"""TaskEventBus 单元测试。

基于 fakeredis 的异步 Pub/Sub 测试，验证：
- 基本发布订阅
- 终态事件关闭订阅流
- 迟到订阅者获取终态缓存
- 多订阅者同时接收事件
- is_active 状态判断
- cleanup 清理资源

技术方案复刻自 finrpa-enterprise 的 approval/pubsub.py，
使用 fakeredis.FakeAsyncRedis 替代真实 Redis 服务进行测试。

@author FinanceRPA
"""

import asyncio

import fakeredis.aioredis
import pytest

from app.agent.event_bus import TaskEventBus


@pytest.fixture
def fake_redis():
    """创建 FakeAsyncRedis 实例（每个测试独立）。"""
    return fakeredis.aioredis.FakeRedis(decode_responses=True)


@pytest.fixture
def event_bus(fake_redis):
    """创建注入 FakeRedis 的事件总线。"""
    return TaskEventBus(redis_client=fake_redis)


@pytest.mark.asyncio
async def test_publish_subscribe_basic(event_bus, fake_redis):
    """基本发布-订阅：订阅者应收到发布的事件。"""
    event_bus.register("task-1")

    # 先启动订阅者，再发布
    async def collector():
        events = []
        async for event in event_bus.subscribe("task-1"):
            events.append(event)
        return events

    task = asyncio.create_task(collector())
    # 等待订阅者订阅完成（fakeredis pubsub 需要时间注册）
    await asyncio.sleep(0.1)

    await event_bus.publish("task-1", "progress", {"message": "步骤 1"})
    await event_bus.publish("task-1", "complete", {"state": "SUCCESS"})

    events = await asyncio.wait_for(task, timeout=2.0)
    assert len(events) == 2
    assert events[0]["event"] == "progress"
    assert events[0]["data"]["message"] == "步骤 1"
    assert events[1]["event"] == "complete"
    assert events[1]["data"]["state"] == "SUCCESS"


@pytest.mark.asyncio
async def test_terminal_event_closes_stream(event_bus):
    """终态事件（complete/error）应关闭订阅流。"""
    event_bus.register("task-2")

    async def collector():
        events = []
        async for event in event_bus.subscribe("task-2"):
            events.append(event)
        return events

    task = asyncio.create_task(collector())
    await asyncio.sleep(0.1)

    await event_bus.publish("task-2", "error", {"error": "执行失败"})

    events = await asyncio.wait_for(task, timeout=2.0)
    assert len(events) == 1
    assert events[0]["event"] == "error"
    assert events[0]["data"]["error"] == "执行失败"


@pytest.mark.asyncio
async def test_late_subscriber_gets_terminal(event_bus):
    """迟到的订阅者应立即获取缓存的终态事件。"""
    event_bus.register("task-3")

    # 先发布终态事件（会缓存到 Redis）
    await event_bus.publish("task-3", "complete", {"state": "SUCCESS"})

    # 再订阅
    events = []
    async for event in event_bus.subscribe("task-3"):
        events.append(event)

    assert len(events) == 1
    assert events[0]["event"] == "complete"
    assert events[0]["data"]["state"] == "SUCCESS"


@pytest.mark.asyncio
async def test_multiple_subscribers(event_bus):
    """多个订阅者应同时收到事件。"""
    event_bus.register("task-4")

    async def collector():
        events = []
        async for event in event_bus.subscribe("task-4"):
            events.append(event)
        return events

    task1 = asyncio.create_task(collector())
    task2 = asyncio.create_task(collector())
    await asyncio.sleep(0.15)  # 等待两个订阅者都就绪

    await event_bus.publish("task-4", "progress", {"step": 1})
    await event_bus.publish("task-4", "complete", {"state": "SUCCESS"})

    events1 = await asyncio.wait_for(task1, timeout=2.0)
    events2 = await asyncio.wait_for(task2, timeout=2.0)

    assert len(events1) == 2
    assert len(events2) == 2
    assert events1[0]["data"]["step"] == 1
    assert events2[0]["data"]["step"] == 1
    assert events1[1]["event"] == "complete"
    assert events2[1]["event"] == "complete"


@pytest.mark.asyncio
async def test_is_active(event_bus):
    """is_active 反映任务是否未到达终态（基于 Redis 终态缓存是否存在）。

    语义说明：Redis pub/sub 无法区分"任务不存在"与"任务执行中"（两者都无终态缓存），
    因此 is_active 只负责判断"是否已到达终态"。任务存在性由调用方（_active_tasks 集合）保证。
    """
    event_bus.register("task-5")

    # 未发布终态事件：任务活跃
    assert await event_bus.is_active("task-5") is True

    # 发布终态事件后：任务不活跃
    await event_bus.publish("task-5", "complete", {"state": "SUCCESS"})

    assert await event_bus.is_active("task-5") is False


@pytest.mark.asyncio
async def test_cleanup(event_bus):
    """cleanup 应删除终态缓存。"""
    event_bus.register("task-6")

    # 发布终态事件 → 缓存到 Redis
    await event_bus.publish("task-6", "complete", {"state": "SUCCESS"})
    assert await event_bus.is_active("task-6") is False

    # cleanup → 删除缓存
    await event_bus.cleanup("task-6")

    # cleanup 后 is_active 返回 True（因为终态缓存已被删除）
    assert await event_bus.is_active("task-6") is True


@pytest.mark.asyncio
async def test_terminal_event_cached_in_redis(event_bus, fake_redis):
    """终态事件应缓存到 Redis（key=task:terminal:{task_id}）。"""
    event_bus.register("task-7")

    await event_bus.publish("task-7", "complete", {"state": "SUCCESS", "message": "done"})

    # 验证 Redis 中存在终态缓存
    cached = await fake_redis.get("task:terminal:task-7")
    assert cached is not None
    import json
    data = json.loads(cached)
    assert data["event"] == "complete"
    assert data["data"]["state"] == "SUCCESS"


@pytest.mark.asyncio
async def test_non_terminal_event_not_cached(event_bus, fake_redis):
    """非终态事件（如 progress）不应缓存到 Redis。"""
    event_bus.register("task-8")

    await event_bus.publish("task-8", "progress", {"step": 1})

    # 验证 Redis 中不存在终态缓存
    cached = await fake_redis.get("task:terminal:task-8")
    assert cached is None


@pytest.mark.asyncio
async def test_register_logs_only(event_bus):
    """register 仅记录日志（Redis pub/sub 无需预注册）。"""
    # register 不应抛异常，且不依赖 Redis 连接
    event_bus.register("task-9")
    event_bus.register("task-10")
    # 多次注册同一任务也不应报错
    event_bus.register("task-9")

"""基于 Redis Pub/Sub 的任务事件总线。

使用 redis.asyncio 实现发布-订阅模型，供 Executor 发布执行事件、
SSE 端点订阅推送。每个任务拥有独立的 Redis 频道，支持多订阅者。

技术方案复刻自 finrpa-enterprise 的 approval/pubsub.py：
- 频道命名：task:events:{task_id}
- 终态缓存：task:terminal:{task_id}（TTL 300s，供迟到订阅者获取）
- 发布：redis_client.publish(channel, json_message)
- 订阅：pubsub = redis_client.pubsub() → subscribe(channel) → get_message()

事件类型：
- step_start：子任务开始执行
- step_end：子任务执行结束
- progress：进度更新
- replan：重新规划
- screenshot：截图已上传
- error：执行错误（终态）
- complete：任务完成（终态）

@author FinanceRPA
"""

import asyncio
import json
import logging
from collections.abc import AsyncIterator
from datetime import datetime
from typing import Callable

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

# 终态事件类型，收到后订阅者应关闭流
_TERMINAL_EVENTS = frozenset({"complete", "error"})

# 频道前缀
_CHANNEL_PREFIX = "task:events:"
# 终态缓存 key 前缀
_TERMINAL_PREFIX = "task:terminal:"
# 终态缓存 TTL（秒）
_TERMINAL_TTL = 300


def _channel_name(task_id: str) -> str:
    """构造 Redis 频道名。"""
    return f"{_CHANNEL_PREFIX}{task_id}"


def _terminal_key(task_id: str) -> str:
    """构造终态缓存 key。"""
    return f"{_TERMINAL_PREFIX}{task_id}"


class TaskEventBus:
    """基于 Redis Pub/Sub 的任务事件总线。

    职责：
    - 为每个任务使用独立 Redis 频道
    - Executor 通过 publish() 发布事件
    - SSE 端点通过 subscribe() 订阅事件流
    - 终态事件缓存到 Redis（TTL 300s），供迟到订阅者获取
    """

    def __init__(self, redis_client: aioredis.Redis | None = None):
        """
        @param redis_client: redis.asyncio.Redis 实例（可选，延迟创建）
        """
        self._redis = redis_client
        self._owns_redis = False  # 标记是否自行创建的 Redis 客户端（需自行关闭）

    async def _get_redis(self) -> aioredis.Redis:
        """获取或创建 Redis 客户端。"""
        if self._redis is None:
            from app.config import get_settings
            settings = get_settings()
            self._redis = aioredis.from_url(
                settings.redis_url,
                decode_responses=True,
            )
            self._owns_redis = True
            logger.info("事件总线: 创建 Redis 客户端, url=%s", settings.redis_url)
        return self._redis

    async def close(self) -> None:
        """关闭自行创建的 Redis 客户端。"""
        if self._redis and self._owns_redis:
            await self._redis.aclose()
            self._redis = None
            self._owns_redis = False
            logger.info("事件总线: 关闭 Redis 客户端")

    def register(self, task_id: str) -> None:
        """注册任务（Redis pub/sub 无需预注册，此方法仅记录日志）。"""
        logger.info("事件总线: 注册任务 %s", task_id)

    async def publish(self, task_id: str, event_type: str, data: dict) -> None:
        """发布事件到 Redis 频道。

        @param task_id: 任务 ID
        @param event_type: 事件类型（step_start/step_end/progress/replan/error/complete）
        @param data: 事件数据
        """
        redis = await self._get_redis()
        event = {
            "event": event_type,
            "data": {
                "taskId": task_id,
                "eventType": event_type,
                "timestamp": datetime.utcnow().isoformat(),
                **data,
            },
        }
        message = json.dumps(event, default=str)
        channel = _channel_name(task_id)

        # 终态事件：缓存到 Redis（TTL 300s），供迟到订阅者
        if event_type in _TERMINAL_EVENTS:
            await redis.set(_terminal_key(task_id), message, ex=_TERMINAL_TTL)
            logger.info(
                "事件总线: 任务 %s 发布终态事件 %s, data=%s",
                task_id, event_type, data,
            )
        else:
            logger.info(
                "事件总线: 任务 %s 发布事件 %s, data=%s",
                task_id, event_type, data,
            )

        # 发布到 Redis 频道
        count = await redis.publish(channel, message)
        logger.info(
            "事件总线: 任务 %s 事件 %s 已推送到 %d 个订阅者",
            task_id, event_type, count,
        )

    async def subscribe(
        self,
        task_id: str,
        on_ready: Callable[[], None] | None = None,
    ) -> AsyncIterator[dict]:
        """订阅任务事件流。

        如果任务已结束，从 Redis 终态缓存获取并立即返回。
        如果任务不存在且无缓存，返回 error 事件。

        @param task_id: 任务 ID
        @param on_ready: 订阅完成后的回调（供测试同步，避免发布者抢跑导致事件丢失）
        @return: 事件异步迭代器
        """
        redis = await self._get_redis()

        # 1. 检查终态缓存：迟到订阅者直接获取终态事件
        terminal = await redis.get(_terminal_key(task_id))
        if terminal:
            event = json.loads(terminal)
            logger.info(
                "事件总线: 迟到订阅者获取终态缓存 [task=%s, event=%s]",
                task_id, event["event"],
            )
            if on_ready is not None:
                on_ready()
            yield event
            return

        # 2. 订阅 Redis 频道
        channel = _channel_name(task_id)
        pubsub = redis.pubsub()
        await pubsub.subscribe(channel)
        logger.info(
            "事件总线: 新订阅者连接 [task=%s, channel=%s]", task_id, channel,
        )

        # 通知订阅就绪（供测试同步，防止发布者在订阅完成前发布事件）
        if on_ready is not None:
            on_ready()

        try:
            # 双重检查：订阅后再次检查终态缓存（防止订阅前刚发布的终态事件丢失）
            terminal = await redis.get(_terminal_key(task_id))
            if terminal:
                event = json.loads(terminal)
                logger.info(
                    "事件总线: 订阅后检测到终态缓存 [task=%s, event=%s]",
                    task_id, event["event"],
                )
                yield event
                return

            while True:
                message = await pubsub.get_message(
                    ignore_subscribe_messages=True,
                    timeout=1.0,
                )
                if message is None:
                    continue
                if message["type"] == "message":
                    event = json.loads(message["data"])
                    yield event
                    if event["event"] in _TERMINAL_EVENTS:
                        logger.info(
                            "事件总线: 订阅者收到终态事件 %s，关闭流 [task=%s]",
                            event["event"], task_id,
                        )
                        break
        finally:
            await pubsub.unsubscribe(channel)
            await pubsub.aclose()
            logger.info(
                "事件总线: 订阅者断开 [task=%s]", task_id,
            )

    async def is_active(self, task_id: str) -> bool:
        """检查任务是否活跃（未结束）。

        @param task_id: 任务 ID
        @return: 是否活跃（无终态缓存则为活跃）
        """
        redis = await self._get_redis()
        terminal = await redis.exists(_terminal_key(task_id))
        return not terminal

    async def cleanup(self, task_id: str) -> None:
        """清理任务事件资源（删除终态缓存）。

        @param task_id: 任务 ID
        """
        redis = await self._get_redis()
        await redis.delete(_terminal_key(task_id))
        logger.info("事件总线: 清理任务 %s 资源", task_id)


# 全局事件总线单例
_event_bus: TaskEventBus | None = None


def get_event_bus() -> TaskEventBus:
    """获取全局事件总线单例。

    @return: TaskEventBus 实例
    """
    global _event_bus
    if _event_bus is None:
        _event_bus = TaskEventBus()
    return _event_bus

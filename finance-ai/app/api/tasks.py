"""任务触发/状态查询/终止 API。

M2.4：状态持久化由 Java 后端负责，Python 侧不再维护内存状态。
- 触发任务：注册事件总线 → 后台执行 Coordinator → 全程回调 Java
- 查询状态：代理到 Java 后端查询
- 终止任务：回调 Java 更新为 ABORTED

@author FinanceRPA
"""

import asyncio
import logging

from fastapi import APIRouter, HTTPException

from app.agent.coordinator import AgentCoordinator
from app.agent.event_bus import get_event_bus
from app.agent.executor import ExecutorAgent
from app.agent.planner import PlannerAgent
from app.clients.java_backend import JavaBackendClient
from app.config import get_settings
from app.schemas import (
    TaskAbortResponse,
    TaskStateResponse,
    TaskTriggerRequest,
    TaskTriggerResponse,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/ai/tasks", tags=["tasks"])

# 并发控制
_semaphore: asyncio.Semaphore | None = None


def _get_semaphore() -> asyncio.Semaphore:
    """获取并发信号量（延迟初始化，从配置读取最大并发数）。"""
    global _semaphore
    if _semaphore is None:
        settings = get_settings()
        _semaphore = asyncio.Semaphore(settings.executor_max_concurrent)
    return _semaphore


# 活跃任务集合（仅追踪正在执行的任务 ID，不存储状态）
_active_tasks: set[str] = set()


async def _execute_task_background(
    task_id: str,
    org_id: str,
    goal: str,
    java_client: JavaBackendClient,
) -> None:
    """后台执行任务（通过 Coordinator 编排，全程回调 Java）。"""
    logger.info(
        "后台任务启动 [task=%s, org=%s, goal=%s], 等待信号量...",
        task_id, org_id, goal,
    )
    async with _get_semaphore():
        logger.info("后台任务获取信号量，开始执行 [task=%s]", task_id)

        event_bus = get_event_bus()
        planner = PlannerAgent()  # fallback 版
        executor = ExecutorAgent(
            java_client=java_client,
            event_bus=event_bus,
            task_id=task_id,
            org_id=org_id,
        )
        coordinator = AgentCoordinator(
            planner=planner,
            executor=executor,
            java_client=java_client,
            event_bus=event_bus,
        )
        logger.info(
            "后台任务组件初始化完成 [task=%s, planner=%s, executor=%s, coordinator=%s]",
            task_id, type(planner).__name__, type(executor).__name__,
            type(coordinator).__name__,
        )

        try:
            await coordinator.run(
                task_id=task_id,
                org_id=org_id,
                navigation_goal=goal,
            )
            logger.info("后台任务 coordinator.run 完成 [task=%s]", task_id)
        except Exception as e:
            logger.error(
                "后台任务执行异常 [task=%s]: %s", task_id, e, exc_info=True,
            )
            # 异常时回调 Java 终态 + 发布 SSE 错误事件
            await java_client.update_task_state(
                task_id=task_id,
                state="FAILED",
                message=f"执行异常: {e}",
                error_message=str(e),
            )
            await event_bus.publish(
                task_id,
                "error",
                {"state": "FAILED", "message": f"执行异常: {e}"},
            )
        finally:
            _active_tasks.discard(task_id)
            logger.info(
                "后台任务结束，从活跃集合移除 [task=%s, 剩余活跃=%d]",
                task_id, len(_active_tasks),
            )
            # 延迟清理事件总线（保留终态事件供迟到订阅者）
            asyncio.get_event_loop().call_later(
                300, lambda: event_bus.cleanup(task_id),
            )


@router.post("", response_model=TaskTriggerResponse)
async def trigger_task(request: TaskTriggerRequest) -> TaskTriggerResponse:
    """触发任务执行。"""
    logger.info(
        "API 触发任务: task_id=%s, org_id=%s, goal=%s, workflow_id=%s",
        request.task_id, request.org_id, request.goal, getattr(request, "workflow_id", None),
    )

    # 1. 注册事件总线
    event_bus = get_event_bus()
    event_bus.register(request.task_id)

    # 2. 标记为活跃任务
    _active_tasks.add(request.task_id)
    logger.info(
        "任务已注册并标记活跃 [task=%s, 当前活跃数=%d]",
        request.task_id, len(_active_tasks),
    )

    # 3. 创建 Java 回调客户端并后台异步执行
    java_client = JavaBackendClient()
    asyncio.create_task(
        _execute_task_background(request.task_id, request.org_id, request.goal, java_client)
    )
    logger.info("后台任务已创建 [task=%s]", request.task_id)

    return TaskTriggerResponse(
        task_id=request.task_id,
        status="running",
        message="Task triggered successfully",
    )


@router.get("/{task_id}/state", response_model=TaskStateResponse)
async def get_task_state(task_id: str) -> TaskStateResponse:
    """查询任务状态（代理到 Java 后端查询）。"""
    # 活跃任务：从事件总线快速判断（is_active 为 async 方法，需 await）
    event_bus = get_event_bus()
    is_active = await event_bus.is_active(task_id) if task_id in _active_tasks else False
    if task_id in _active_tasks and is_active:
        logger.info(
            "查询任务状态: 任务活跃中，返回 EXECUTING [task=%s]", task_id,
        )
        return TaskStateResponse(
            task_id=task_id,
            state="executing",
            message="任务执行中",
        )

    # 非活跃任务：代理到 Java 后端查询权威状态
    logger.info(
        "查询任务状态: 任务非活跃，代理到 Java 查询 [task=%s]", task_id,
    )
    java_client = JavaBackendClient()
    client = await java_client._get_client()
    try:
        resp = await client.get(f"/api/ai/tasks/{task_id}")
        if resp.status_code == 404:
            logger.warning("查询任务状态: Java 返回 404 [task=%s]", task_id)
            raise HTTPException(status_code=404, detail=f"Task not found: {task_id}")
        resp.raise_for_status()
        data = resp.json().get("data", resp.json())
        logger.info(
            "查询任务状态: Java 返回 [task=%s, status=%s, step=%s/%s]",
            task_id, data.get("status"), data.get("currentStep"), data.get("totalSteps"),
        )
        return TaskStateResponse(
            task_id=task_id,
            state=data.get("status", "unknown").lower(),
            current_step=data.get("currentStep", 0),
            total_steps=data.get("totalSteps", 0),
            message=data.get("message", ""),
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error("查询任务状态失败 [task=%s]: %s", task_id, e, exc_info=True)
        raise HTTPException(
            status_code=502, detail=f"Failed to query task state: {e}",
        ) from e
    finally:
        await java_client.close()


@router.post("/{task_id}/abort", response_model=TaskAbortResponse)
async def abort_task(task_id: str) -> TaskAbortResponse:
    """终止任务（回调 Java 更新为 ABORTED）。"""
    logger.info("API 终止任务 [task=%s]", task_id)

    if task_id not in _active_tasks:
        logger.warning(
            "终止任务失败: 任务不存在或已结束 [task=%s, 活跃任务=%s]",
            task_id, _active_tasks,
        )
        raise HTTPException(
            status_code=404,
            detail=f"Task not found or already completed: {task_id}",
        )

    # 回调 Java：ABORTED
    logger.info("终止任务: 回调 Java ABORTED [task=%s]", task_id)
    java_client = JavaBackendClient()
    await java_client.update_task_state(
        task_id=task_id,
        state="ABORTED",
        message="Task aborted by user",
    )

    # 发布 SSE 终态事件
    logger.info("终止任务: 发布 SSE error 事件 [task=%s]", task_id)
    event_bus = get_event_bus()
    await event_bus.publish(
        task_id,
        "error",
        {"state": "ABORTED", "message": "Task aborted by user"},
    )

    _active_tasks.discard(task_id)
    logger.info("终止任务完成 [task=%s]", task_id)

    return TaskAbortResponse(
        task_id=task_id,
        aborted=True,
        message="Task aborted",
    )

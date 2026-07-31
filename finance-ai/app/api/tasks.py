"""任务触发/状态查询/终止 API。

M3.8：触发任务改为内部调 Skyvern API 创建 Skyvern 原生任务，
获取 skyvern_task_id 后返回给 Java 后端。
- 触发任务：调 Skyvern POST /api/v1/tasks → 返回 skyvern_task_id
- 查询状态：调 Skyvern GET /api/v1/tasks/{skyvern_task_id} → 映射为 RPA 状态
- 终止任务：回调 Java 更新为 ABORTED

M4 预留：Planner/Coordinator 后台编排逻辑保留（注释），待 M4 双 Agent 协作时启用。

@author FinanceRPA
"""

import asyncio
import logging

from fastapi import APIRouter, HTTPException

from app.agent.event_bus import get_event_bus
from app.clients.java_backend import JavaBackendClient
from app.clients.skyvern_client import SkyvernClient, map_skyvern_status
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
    """触发任务执行（M3.8：调 Skyvern API 创建 Skyvern 原生任务）。

    流程：
      1. 注册事件总线 + 标记活跃
      2. 调 Skyvern POST /api/v1/tasks 创建任务（LLM 视觉决策 + 浏览器自动化）
      3. 返回 skyvern_task_id 给 Java 后端
    """
    logger.info(
        "API 触发任务: task_id=%s, org_id=%s, goal=%s, workflow_id=%s",
        request.task_id, request.org_id, request.goal, getattr(request, "workflow_id", None),
    )

    # 1. 注册事件总线 + 标记活跃
    event_bus = get_event_bus()
    event_bus.register(request.task_id)
    _active_tasks.add(request.task_id)
    logger.info(
        "任务已注册并标记活跃 [task=%s, 当前活跃数=%d]",
        request.task_id, len(_active_tasks),
    )

    # 2. 调 Skyvern API 创建任务
    # 从 params 中提取 URL（工作流模板参数），默认用 about:blank 让 Skyvern 至少能启动浏览器
    target_url = request.params.get("url", "about:blank") if request.params else "about:blank"

    skyvern_client = SkyvernClient()
    try:
        skyvern_task = await skyvern_client.create_task(
            title=f"FinanceRPA-{request.task_id}",
            url=target_url,
            navigation_goal=request.goal,
            navigation_payload=request.params if request.params else None,
        )
    finally:
        await skyvern_client.close()

    if skyvern_task is None:
        # Skyvern 创建失败，回调 Java 标记失败
        logger.error("Skyvern 任务创建失败: task_id=%s", request.task_id)
        java_client = JavaBackendClient()
        try:
            await java_client.update_task_state(
                task_id=request.task_id,
                state="FAILED",
                message="Skyvern 任务创建失败",
                error_message="Skyvern API 调用失败",
            )
        finally:
            await java_client.close()
        _active_tasks.discard(request.task_id)
        raise HTTPException(
            status_code=502,
            detail="Skyvern 任务创建失败，请检查 Skyvern 服务状态",
        )

    skyvern_task_id = skyvern_task.get("task_id")
    logger.info(
        "Skyvern 任务创建成功: task_id=%s, skyvern_task_id=%s, url=%s",
        request.task_id, skyvern_task_id, target_url,
    )

    # 3. 返回含 skyvern_task_id 的响应（Java 侧保存到 rpa_agent_task.skyvern_task_id）
    return TaskTriggerResponse(
        task_id=request.task_id,
        skyvern_task_id=skyvern_task_id,
        status="running",
        message="Skyvern 任务已触发",
    )


@router.get("/{skyvern_task_id}/state", response_model=TaskStateResponse)
async def get_task_state(skyvern_task_id: str) -> TaskStateResponse:
    """查询 Skyvern 任务状态并映射为 RPA 状态（M3.8）。

    Java 侧先查数据库获取 skyvern_task_id，再调此接口查询 Skyvern 实时状态。
    状态映射：Skyvern status → RPA state（PENDING / EXECUTING / SUCCESS / FAILED / ABORTED）
    """
    logger.info("查询 Skyvern 任务状态: skyvern_task_id=%s", skyvern_task_id)

    skyvern_client = SkyvernClient()
    try:
        task = await skyvern_client.get_task(skyvern_task_id)
    finally:
        await skyvern_client.close()

    if task is None:
        logger.warning("Skyvern 任务不存在: skyvern_task_id=%s", skyvern_task_id)
        raise HTTPException(
            status_code=404,
            detail=f"Skyvern task not found: {skyvern_task_id}",
        )

    # 映射 Skyvern 状态为 RPA 状态
    skyvern_status = task.get("status", "running")
    rpa_state = map_skyvern_status(skyvern_status)
    failure_reason = task.get("failure_reason") or ""

    logger.info(
        "Skyvern 任务状态: skyvern_task_id=%s, skyvern_status=%s, rpa_state=%s",
        skyvern_task_id, skyvern_status, rpa_state,
    )

    return TaskStateResponse(
        task_id=skyvern_task_id,
        state=rpa_state.lower(),
        message=failure_reason or "任务执行中",
    )


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

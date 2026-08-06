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
import json
import logging
from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException
from pydantic import ValidationError

from app.agent.coordinator import AgentCoordinator
from app.agent.event_bus import get_event_bus
from app.agent.executor import ExecutorAgent
from app.agent.planner import PlannerAgent
from app.agent.schemas import TaskPlan
from app.audit.reporter import AuditReporter
from app.clients.java_backend import JavaBackendClient
from app.clients.skyvern_client import SkyvernClient, map_skyvern_status
from app.config import get_settings
from app.schemas import (
    TaskAbortResponse,
    TaskResumeRequest,
    TaskResumeResponse,
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

# 后台监控任务引用集合（防止 asyncio.Task 被垃圾回收，M9.1 修复）
# 官方推荐做法：https://docs.python.org/3/library/asyncio-task.html#asyncio.create_task
_background_tasks: set[asyncio.Task] = set()


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


# Skyvern 任务监控超时（秒）：最长轮询 10 分钟
_SKYVERN_MONITOR_TIMEOUT = 600
# Skyvern 轮询间隔（秒）
_SKYVERN_POLL_INTERVAL = 5


async def _monitor_skyvern_task(
    task_id: str,
    org_id: str,
    user_id: str,
    skyvern_task_id: str,
    target_url: str,
    goal: str,
    params: dict,
) -> None:
    """后台监控 Skyvern 任务，终态时回调 Java 状态 + 上报审计 + 发布 SSE（M9.1）。

    M3.8 默认路径下 Skyvern 原生 API 不会主动回调 Java，也不会产生审计日志。
    本函数补齐这个缺口：
      1. 轮询 Skyvern 任务状态（每 5s，最长 10 分钟）
      2. Skyvern 进入终态（SUCCESS/FAILED/ABORTED）时：
         - 回调 Java update_task_state 更新任务终态
         - 上报一条任务级审计日志（AuditReporter，action_type=skyvern_task_execution）
         - 发布 SSE 终态事件（complete/error）
      3. 异常或超时也回调 Java FAILED

    @param task_id: RPA 任务 ID（雪花算法）
    @param org_id: 组织 ID
    @param user_id: 触发用户 ID（审计需要）
    @param skyvern_task_id: Skyvern 任务 ID
    @param target_url: Skyvern 浏览器访问的 URL（审计记录用）
    @param goal: 任务目标
    @param params: 任务参数（审计记录用）
    """
    logger.info(
        "Skyvern 监控启动 [task=%s, skyvern=%s, url=%s]",
        task_id, skyvern_task_id, target_url,
    )

    skyvern_client = SkyvernClient()
    java_client = JavaBackendClient()
    reporter = AuditReporter(java_client=java_client)
    event_bus = get_event_bus()
    started_at = datetime.now(timezone.utc)

    rpa_state = "EXECUTING"
    failure_reason = ""

    try:
        # 0. 先将任务从 PENDING → EXECUTING（Java 状态机要求：PENDING 不能直接跳 SUCCESS）
        # M9.1 修复：此前监控直接回调 SUCCESS 被 TaskStateMachine 拒绝（非法流转 PENDING→SUCCESS）
        await java_client.update_task_state(
            task_id=task_id,
            state="EXECUTING",
            message="Skyvern 任务开始执行",
        )

        # 1. 轮询 Skyvern 状态
        poll_count = _SKYVERN_MONITOR_TIMEOUT // _SKYVERN_POLL_INTERVAL
        for _ in range(poll_count):
            await asyncio.sleep(_SKYVERN_POLL_INTERVAL)
            task = await skyvern_client.get_task(skyvern_task_id)
            if task is None:
                logger.warning(
                    "Skyvern 监控: 查询返回空 [task=%s, skyvern=%s]",
                    task_id, skyvern_task_id,
                )
                continue
            skyvern_status = task.get("status", "running")
            rpa_state = map_skyvern_status(skyvern_status)
            failure_reason = task.get("failure_reason") or ""
            logger.info(
                "Skyvern 监控轮询 [task=%s, skyvern_status=%s, rpa_state=%s]",
                task_id, skyvern_status, rpa_state,
            )
            if rpa_state in ("SUCCESS", "FAILED", "ABORTED"):
                break
        else:
            # 轮询超时，标记失败
            rpa_state = "FAILED"
            failure_reason = f"Skyvern 任务监控超时（{_SKYVERN_MONITOR_TIMEOUT}s）"

        completed_at = datetime.now(timezone.utc)
        duration_ms = int((completed_at - started_at).total_seconds() * 1000)
        execution_result = "success" if rpa_state == "SUCCESS" else "failed"
        terminal_message = failure_reason or f"Skyvern 任务 {rpa_state}"

        # 2. 回调 Java 更新任务终态
        logger.info("Skyvern 监控: 回调 Java 终态 [task=%s, state=%s]", task_id, rpa_state)
        await java_client.update_task_state(
            task_id=task_id,
            state=rpa_state,
            message=terminal_message,
            error_message=failure_reason if rpa_state != "SUCCESS" else None,
        )

        # 3. 上报任务级审计日志（M9.1：补 Skyvern 原生路径的审计缺口）
        logger.info("Skyvern 监控: 上报审计 [task=%s, result=%s]", task_id, execution_result)
        await reporter.report_step(
            org_id=org_id,
            task_id=task_id,
            step_index=0,
            action_type="skyvern_task_execution",
            started_at=started_at,
            completed_at=completed_at,
            duration_ms=duration_ms,
            page_url=target_url,
            action_params=json.dumps(params, ensure_ascii=False) if params else None,
            execution_result=execution_result,
            error_message=failure_reason if rpa_state != "SUCCESS" else None,
            user_id=user_id,
        )

        # 4. 发布 SSE 终态事件
        sse_event_type = "complete" if rpa_state == "SUCCESS" else "error"
        await event_bus.publish(
            task_id,
            sse_event_type,
            {"state": rpa_state, "message": terminal_message},
        )

        logger.info("Skyvern 监控完成 [task=%s, state=%s, duration=%dms]", task_id, rpa_state, duration_ms)
    except Exception as e:
        logger.error("Skyvern 监控异常 [task=%s]: %s", task_id, e, exc_info=True)
        # 异常时回调 Java 失败
        try:
            await java_client.update_task_state(
                task_id=task_id,
                state="FAILED",
                message=f"监控异常: {e}",
                error_message=str(e),
            )
            await event_bus.publish(
                task_id, "error", {"state": "FAILED", "message": f"监控异常: {e}"},
            )
        except Exception as inner:
            logger.error("Skyvern 监控异常回调失败 [task=%s]: %s", task_id, inner, exc_info=True)
    finally:
        _active_tasks.discard(task_id)
        await skyvern_client.close()
        await java_client.close()
        logger.info(
            "Skyvern 监控结束，从活跃集合移除 [task=%s, 剩余活跃=%d]",
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

    # 3. 启动后台监控（M9.1：轮询 Skyvern 状态，终态回调 Java + 上报审计 + 发布 SSE）
    # M3.8 默认路径下 Skyvern 不主动回调，需本监控补齐任务终态回传与审计日志
    # 关键：必须持有 Task 引用，否则事件循环可能将其垃圾回收，导致监控中断、任务永远卡在 PENDING
    monitor_task = asyncio.create_task(_monitor_skyvern_task(
        task_id=request.task_id,
        org_id=request.org_id,
        user_id=request.user_id,
        skyvern_task_id=skyvern_task_id,
        target_url=target_url,
        goal=request.goal,
        params=request.params if request.params else {},
    ))
    _background_tasks.add(monitor_task)
    monitor_task.add_done_callback(_background_tasks.discard)
    logger.info(
        "Skyvern 监控任务已启动并加入引用集合 [task=%s, 后台任务数=%d]",
        request.task_id, len(_background_tasks),
    )

    # 4. 返回含 skyvern_task_id 的响应（Java 侧保存到 rpa_agent_task.skyvern_task_id）
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


async def _resume_task_background(
    request: TaskResumeRequest,
) -> None:
    """后台续跑任务（通过 Coordinator 从断点继续执行，M4.3）。

    与 _execute_task_background 不同：
    - 传入 initial_plan（从 coordination_state 读取的已存计划），跳过 Planner.create_plan
    - 传入 resume_from=completed_subtasks，跳过已完成子任务
    """
    task_id = request.task_id
    logger.info(
        "后台续跑任务启动 [task=%s, org=%s, completed=%d], 等待信号量...",
        task_id, request.org_id, len(request.completed_subtasks),
    )

    # 反序列化已存计划
    try:
        plan = TaskPlan.model_validate_json(request.current_plan)
    except (ValidationError, json.JSONDecodeError) as e:
        logger.error("续跑任务: 已存计划反序列化失败 [task=%s]: %s", task_id, e, exc_info=True)
        java_client = JavaBackendClient()
        try:
            await java_client.update_task_state(
                task_id=task_id,
                state="FAILED",
                message=f"续跑失败：已存计划反序列化失败: {e}",
                error_message=str(e),
            )
        finally:
            await java_client.close()
        return

    async with _get_semaphore():
        logger.info("后台续跑任务获取信号量，开始执行 [task=%s]", task_id)

        event_bus = get_event_bus()
        event_bus.register(task_id)
        _active_tasks.add(task_id)

        java_client = JavaBackendClient()
        planner = PlannerAgent()
        executor = ExecutorAgent(
            java_client=java_client,
            event_bus=event_bus,
            task_id=task_id,
            org_id=request.org_id,
        )
        coordinator = AgentCoordinator(
            planner=planner,
            executor=executor,
            java_client=java_client,
            event_bus=event_bus,
        )

        try:
            await coordinator.run(
                task_id=task_id,
                org_id=request.org_id,
                navigation_goal=request.navigation_goal,
                context=request.params,
                resume_from=request.completed_subtasks,
                initial_plan=plan,
            )
            logger.info("后台续跑任务 coordinator.run 完成 [task=%s]", task_id)
        except Exception as e:
            logger.error(
                "后台续跑任务执行异常 [task=%s]: %s", task_id, e, exc_info=True,
            )
            await java_client.update_task_state(
                task_id=task_id,
                state="FAILED",
                message=f"续跑异常: {e}",
                error_message=str(e),
            )
            await event_bus.publish(
                task_id,
                "error",
                {"state": "FAILED", "message": f"续跑异常: {e}"},
            )
        finally:
            _active_tasks.discard(task_id)
            await java_client.close()
            logger.info(
                "后台续跑任务结束，从活跃集合移除 [task=%s, 剩余活跃=%d]",
                task_id, len(_active_tasks),
            )
            asyncio.get_event_loop().call_later(
                300, lambda: event_bus.cleanup(task_id),
            )


@router.post("/{task_id}/resume", response_model=TaskResumeResponse)
async def resume_task(task_id: str, request: TaskResumeRequest) -> TaskResumeResponse:
    """任务续跑（M4.3：从断点继续执行，不重做已完成子任务）。

    Java 侧从 rpa_agent_coordination_state 读取已存计划 + completed_subtasks，
    传入此接口让 Python Coordinator 从断点继续执行。

    流程：
      1. 反序列化 current_plan JSON → TaskPlan
      2. 后台启动 Coordinator（initial_plan + resume_from）
      3. 立即返回响应（异步执行）
    """
    logger.info(
        "API 任务续跑: task_id=%s, org_id=%s, completed_subtasks=%d",
        task_id, request.org_id, len(request.completed_subtasks),
    )

    # 校验 task_id 一致性
    if request.task_id != task_id:
        raise HTTPException(
            status_code=400,
            detail=f"task_id 不匹配: path={task_id}, body={request.task_id}",
        )

    # 后台异步执行续跑
    asyncio.create_task(_resume_task_background(request))

    return TaskResumeResponse(
        task_id=task_id,
        status="running",
        message="任务续跑已触发",
    )

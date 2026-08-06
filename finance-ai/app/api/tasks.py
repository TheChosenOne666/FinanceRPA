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
from app.clients.java_backend import JavaBackendClient
from app.clients.skyvern_client import SkyvernClient, map_skyvern_status
from app.llm.resilient_caller import ResilientCaller
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
    url: str = "about:blank",
    params: dict | None = None,
) -> None:
    """后台执行任务（通过 Coordinator 编排，全程回调 Java）。

    M9.7：接入 Skyvern action_handler，ExecutorAgent 真实调 Skyvern API 执行子任务。
    Planner 拆任务 → Executor 逐个调 action_handler → action_handler 调 SkyvernClient。
    """
    logger.info(
        "后台任务启动 [task=%s, org=%s, goal=%s, url=%s], 等待信号量...",
        task_id, org_id, goal, url,
    )
    async with _get_semaphore():
        logger.info("后台任务获取信号量，开始执行 [task=%s]", task_id)

        event_bus = get_event_bus()

        # M9.7: 注入 ResilientCaller（三层容错 + rpa_llm_call_log 上报）
        # Planner 是纯文本 LLM 调用（不传 screenshots），用 GPT-4o-mini 即可
        async def _planner_llm_callable(prompt: str) -> str:
            """Planner LLM 调用（纯文本，GPT-4o-mini）。"""
            import litellm
            response = await litellm.acompletion(
                model="gpt-4o-mini",
                messages=[{"role": "user", "content": prompt}],
                max_tokens=2000,
                temperature=0.1,
            )
            return response.choices[0].message.content

        resilient_caller = ResilientCaller(
            llm_callable=_planner_llm_callable,
            java_client=java_client,
        )
        planner = PlannerAgent(resilient_caller=resilient_caller)

        # M9.7: action_handler 封装 SkyvernClient（创建任务 + 轮询终态）
        # 闭包捕获 url/params/task_id/event_bus，供 ExecutorAgent 逐子任务调用
        _task_url = url
        _task_params = params or {}
        _task_id = task_id
        _event_bus = event_bus

        async def _skyvern_action_handler(goal: str, context: dict) -> dict:
            """ExecutorAgent 的 action_handler：调 Skyvern API 执行子任务。"""
            # 1. 创建 Skyvern 任务
            skyvern_client = SkyvernClient()
            try:
                skyvern_task = await skyvern_client.create_task(
                    title=f"FinanceRPA-{_task_id}",
                    url=_task_url,
                    navigation_goal=goal,
                    navigation_payload=_task_params if _task_params else None,
                )
            finally:
                await skyvern_client.close()

            if skyvern_task is None:
                return {"success": False, "error": "Skyvern 任务创建失败", "data": None}

            skyvern_task_id = skyvern_task.get("task_id")
            logger.info(
                "action_handler: Skyvern 任务创建成功 [task=%s, skyvern=%s, goal=%s]",
                _task_id, skyvern_task_id, goal[:50],
            )

            # 2. 轮询 Skyvern 任务状态（每 5s，最长 10 分钟）
            skyvern_client = SkyvernClient()
            try:
                for _ in range(_SKYVERN_MONITOR_TIMEOUT // _SKYVERN_POLL_INTERVAL):
                    await asyncio.sleep(_SKYVERN_POLL_INTERVAL)
                    task_status = await skyvern_client.get_task(skyvern_task_id)
                    if task_status is None:
                        continue

                    status = map_skyvern_status(task_status.get("status", ""))

                    # 发布进度事件
                    await _event_bus.publish(
                        _task_id,
                        "progress",
                        {
                            "skyvernTaskId": skyvern_task_id,
                            "status": status,
                            "message": f"Skyvern 任务状态: {status}",
                        },
                    )

                    if status in ("SUCCESS", "FAILED", "ABORTED"):
                        if status == "SUCCESS":
                            return {
                                "success": True,
                                "data": task_status,
                                "error": None,
                                "page_url": task_status.get("url"),
                            }
                        else:
                            return {
                                "success": False,
                                "data": task_status,
                                "error": task_status.get("failure_reason", f"Skyvern 任务 {status}"),
                                "page_url": task_status.get("url"),
                            }
            finally:
                await skyvern_client.close()

            return {"success": False, "error": "Skyvern 任务超时", "data": None}

        executor = ExecutorAgent(
            action_handler=_skyvern_action_handler,
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
            # M9.7: 关闭 JavaBackendClient
            try:
                await java_client.close()
            except Exception as e:
                logger.warning("关闭 JavaBackendClient 失败: %s", e)
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


@router.post("", response_model=TaskTriggerResponse)
async def trigger_task(request: TaskTriggerRequest) -> TaskTriggerResponse:
    """触发任务执行（M9.7：改走 Coordinator 编排 + Skyvern action_handler）。

    流程：
      1. 注册事件总线 + 标记活跃
      2. 启动后台 Coordinator：Planner 拆任务 → Executor 逐个调 action_handler → SkyvernClient
      3. 返回 task_id（skyvern_task_id 在 action_handler 内部创建，异步回调 Java）
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

    # 2. 从 params 中提取 URL（工作流模板参数）
    target_url = request.params.get("url", "about:blank") if request.params else "about:blank"

    # M9.7: 改走 Coordinator → Planner → Executor → action_handler（SkyvernClient）
    # Planner 拆任务 + 失败策略 + 断点续跑 + 子任务级审计，action_handler 内部调 Skyvern
    java_client = JavaBackendClient()
    bg_task = asyncio.create_task(_execute_task_background(
        task_id=request.task_id,
        org_id=request.org_id,
        goal=request.goal,
        java_client=java_client,
        url=target_url,
        params=request.params if request.params else {},
    ))
    _background_tasks.add(bg_task)
    bg_task.add_done_callback(_background_tasks.discard)
    logger.info(
        "Coordinator 后台任务已启动 [task=%s, url=%s, 后台任务数=%d]",
        request.task_id, target_url, len(_background_tasks),
    )

    # 3. 返回响应（skyvern_task_id 在 action_handler 内部创建，此处返回 None）
    return TaskTriggerResponse(
        task_id=request.task_id,
        skyvern_task_id=None,
        status="running",
        message="任务已触发，Coordinator 编排中",
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

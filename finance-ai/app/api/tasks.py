"""任务触发/状态查询/终止 API。

@author FinanceRPA
"""

import asyncio
import logging

from fastapi import APIRouter, HTTPException

from app.agent.coordinator import AgentCoordinator
from app.agent.executor import ExecutorAgent
from app.agent.planner import PlannerAgent
from app.schemas import (
    TaskAbortResponse,
    TaskStateResponse,
    TaskTriggerRequest,
    TaskTriggerResponse,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/ai/tasks", tags=["tasks"])

# 模块级状态存储（M2.1 内存版，M2.3 由 Java 持久化）
_task_states: dict[str, dict] = {}

# 并发控制
_semaphore = asyncio.Semaphore(5)


async def _execute_task_background(
    task_id: str,
    org_id: str,
    goal: str,
) -> None:
    """后台执行任务（通过 Coordinator 编排）。"""
    async with _semaphore:
        planner = PlannerAgent()  # fallback 版
        executor = ExecutorAgent()  # 模拟执行
        coordinator = AgentCoordinator(planner, executor)

        _task_states[task_id]["state"] = "executing"
        try:
            state = await coordinator.run(
                task_id=task_id,
                org_id=org_id,
                navigation_goal=goal,
            )
            _task_states[task_id]["state"] = state.status
            _task_states[task_id]["message"] = state.error_message or ""
            _task_states[task_id]["current_step"] = len(state.completed_subtasks)
            if state.current_plan:
                _task_states[task_id]["total_steps"] = len(state.current_plan.subtasks)
        except Exception as e:
            logger.error("任务执行异常 [task=%s]: %s", task_id, e)
            _task_states[task_id]["state"] = "failed"
            _task_states[task_id]["message"] = str(e)


@router.post("", response_model=TaskTriggerResponse)
async def trigger_task(request: TaskTriggerRequest) -> TaskTriggerResponse:
    """触发任务执行。"""
    logger.info(
        "触发任务: task_id=%s, org_id=%s, goal=%s",
        request.task_id, request.org_id, request.goal,
    )

    # 初始化任务状态
    _task_states[request.task_id] = {
        "state": "pending",
        "current_step": 0,
        "total_steps": 0,
        "message": "Task triggered",
        "org_id": request.org_id,
        "goal": request.goal,
    }

    # 后台异步执行
    asyncio.create_task(
        _execute_task_background(request.task_id, request.org_id, request.goal)
    )

    return TaskTriggerResponse(
        task_id=request.task_id,
        status="running",
        message="Task triggered successfully",
    )


@router.get("/{task_id}/state", response_model=TaskStateResponse)
async def get_task_state(task_id: str) -> TaskStateResponse:
    """查询任务状态。"""
    state = _task_states.get(task_id)
    if state is None:
        raise HTTPException(status_code=404, detail=f"Task not found: {task_id}")

    return TaskStateResponse(
        task_id=task_id,
        state=state["state"],
        current_step=state["current_step"],
        total_steps=state["total_steps"],
        message=state["message"],
    )


@router.post("/{task_id}/abort", response_model=TaskAbortResponse)
async def abort_task(task_id: str) -> TaskAbortResponse:
    """终止任务。"""
    state = _task_states.get(task_id)
    if state is None:
        raise HTTPException(status_code=404, detail=f"Task not found: {task_id}")

    state["state"] = "failed"
    state["message"] = "Task aborted by user"

    return TaskAbortResponse(
        task_id=task_id,
        aborted=True,
        message="Task aborted",
    )

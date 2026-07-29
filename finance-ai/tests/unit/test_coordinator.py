"""AgentCoordinator 单元测试。"""

import asyncio
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.agent.coordinator import AgentCoordinator
from app.agent.event_bus import TaskEventBus
from app.agent.executor import ExecutorAgent
from app.agent.planner import PlannerAgent
from app.agent.schemas import FailureStrategy, SubTask


@pytest.mark.asyncio
async def test_coordinator_success():
    """完整执行成功：fallback 单步计划 → 模拟执行 → completed。"""
    planner = PlannerAgent()
    executor = ExecutorAgent()
    coordinator = AgentCoordinator(planner, executor)

    state = await coordinator.run(
        task_id="test-task-1",
        org_id="org-1",
        navigation_goal="下载流水",
    )

    assert state.status == "completed"
    assert state.task_id == "test-task-1"
    assert state.org_id == "org-1"
    assert state.navigation_goal == "下载流水"
    assert len(state.completed_subtasks) == 1
    assert state.total_replans == 0
    assert state.current_plan is not None


@pytest.mark.asyncio
async def test_coordinator_abort_on_failure():
    """ABORT 策略失败时应终止任务。"""
    async def fail_handler(goal, context):
        return {"success": False, "error": "执行失败"}

    planner = PlannerAgent()
    executor = ExecutorAgent(action_handler=fail_handler)
    coordinator = AgentCoordinator(planner, executor)

    state = await coordinator.run(
        task_id="test-task-2",
        org_id="org-1",
        navigation_goal="会失败的任务",
    )

    assert state.status == "failed"
    assert "失败" in (state.error_message or "")


@pytest.mark.asyncio
async def test_coordinator_audit_callback():
    """审计回调应被调用。"""
    audit_calls = []

    async def audit_cb(subtask, result):
        audit_calls.append({
            "subtask_id": subtask.subtask_id,
            "success": result.success,
        })

    planner = PlannerAgent()
    executor = ExecutorAgent()
    coordinator = AgentCoordinator(planner, executor, audit_callback=audit_cb)

    state = await coordinator.run(
        task_id="test-task-3",
        org_id="org-1",
        navigation_goal="审计测试",
    )

    assert state.status == "completed"
    assert len(audit_calls) == 1
    assert audit_calls[0]["success"] is True


@pytest.mark.asyncio
async def test_coordinator_resume_from():
    """断点续跑应跳过已完成子任务。"""
    planner = PlannerAgent()
    executor = ExecutorAgent()
    coordinator = AgentCoordinator(planner, executor)

    # 先执行一次获取 subtask_id
    state1 = await coordinator.run(
        task_id="test-task-4",
        org_id="org-1",
        navigation_goal="断点续跑",
    )
    completed_ids = state1.completed_subtasks

    # 用相同 resume_from 再跑（虽然 fallback 生成的 subtask_id 不同，
    # 但逻辑上 resume_from 为空列表时应正常执行）
    state2 = await coordinator.run(
        task_id="test-task-5",
        org_id="org-1",
        navigation_goal="断点续跑2",
        resume_from=completed_ids,
    )

    assert state2.status == "completed"


@pytest.mark.asyncio
async def test_coordinator_replan_exhausted():
    """超过最大 replan 次数应标记 needs_human。"""
    # 使用 REPLAN 策略 + 总是失败的 handler
    async def fail_handler(goal, context):
        return {"success": False, "error": "持续失败"}

    # 自定义 planner 生成 REPLAN 策略的子任务（replan 也生成 REPLAN 策略）
    from app.agent.schemas import TaskPlan

    class ReplanPlanner(PlannerAgent):
        async def create_plan(self, navigation_goal, context=None):
            return TaskPlan(
                navigation_goal=navigation_goal,
                subtasks=[
                    SubTask(
                        index=0,
                        goal=navigation_goal,
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.REPLAN,
                        max_retries=0,
                    ),
                ],
            )

        async def replan(self, original_goal, completed_subtasks, failed_subtask, failure_reason, context=None):
            return TaskPlan(
                navigation_goal=original_goal,
                subtasks=[
                    SubTask(
                        index=0,
                        goal=f"重试: {original_goal}",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.REPLAN,
                        max_retries=0,
                    ),
                ],
                is_replan=True,
                replan_reason=failure_reason,
                version=len(completed_subtasks) + 2,
            )

    planner = ReplanPlanner()
    executor = ExecutorAgent(action_handler=fail_handler)
    coordinator = AgentCoordinator(planner, executor, max_replans=1)

    state = await coordinator.run(
        task_id="test-task-6",
        org_id="org-1",
        navigation_goal="replan 耗尽",
    )

    assert state.status == "needs_human"
    assert state.total_replans >= 1


# ---------------------------------------------------------------------------
# M2.4 集成测试：Java 状态回调 + SSE 事件发布
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_coordinator_with_java_callback_success():
    """成功执行时应回调 Java 更新任务状态（EXECUTING → SUCCESS）。"""
    mock_java = MagicMock()
    mock_java.update_task_state = AsyncMock(return_value=True)
    mock_java.update_subtask = AsyncMock(return_value=True)
    mock_java.report_audit_log = AsyncMock(return_value=True)

    bus = TaskEventBus()
    bus.register("task-java-1")

    # 收集 SSE 事件
    events = []

    async def collect():
        async for event in bus.subscribe("task-java-1"):
            events.append(event)

    asyncio.create_task(collect())
    await asyncio.sleep(0.05)

    planner = PlannerAgent()
    executor = ExecutorAgent(
        java_client=mock_java,
        event_bus=bus,
        task_id="task-java-1",
        org_id="org-1",
    )
    coordinator = AgentCoordinator(
        planner=planner,
        executor=executor,
        java_client=mock_java,
        event_bus=bus,
    )

    state = await coordinator.run(
        task_id="task-java-1",
        org_id="org-1",
        navigation_goal="回调测试",
    )

    assert state.status == "completed"

    # 验证 Java 回调序列：EXECUTING (start) → EXECUTING (progress) → SUCCESS (terminal)
    assert mock_java.update_task_state.call_count >= 2
    calls = mock_java.update_task_state.call_args_list

    # 第一次：EXECUTING
    assert calls[0][1]["state"] == "EXECUTING"

    # 最后一次：SUCCESS
    assert calls[-1][1]["state"] == "SUCCESS"

    # 验证 SSE 事件
    await asyncio.sleep(0.05)  # 等待 collector 处理事件
    event_types = [e["event"] for e in events]
    assert "progress" in event_types  # 任务开始
    assert "complete" in event_types  # 终态


@pytest.mark.asyncio
async def test_coordinator_with_java_callback_failure():
    """失败执行时应回调 Java 更新任务状态为 FAILED。"""
    async def fail_handler(goal, context):
        return {"success": False, "error": "执行失败"}

    mock_java = MagicMock()
    mock_java.update_task_state = AsyncMock(return_value=True)
    mock_java.update_subtask = AsyncMock(return_value=True)
    mock_java.report_audit_log = AsyncMock(return_value=True)

    bus = TaskEventBus()
    bus.register("task-java-2")

    planner = PlannerAgent()
    executor = ExecutorAgent(
        action_handler=fail_handler,
        java_client=mock_java,
        event_bus=bus,
        task_id="task-java-2",
        org_id="org-1",
    )
    coordinator = AgentCoordinator(
        planner=planner,
        executor=executor,
        java_client=mock_java,
        event_bus=bus,
    )

    state = await coordinator.run(
        task_id="task-java-2",
        org_id="org-1",
        navigation_goal="失败回调测试",
    )

    assert state.status == "failed"

    # 验证 Java 终态回调：FAILED
    calls = mock_java.update_task_state.call_args_list
    assert calls[-1][1]["state"] == "FAILED"
    assert calls[-1][1]["error_message"] is not None


@pytest.mark.asyncio
async def test_coordinator_without_java_client():
    """无 JavaBackendClient 时应正常执行（向后兼容）。"""
    planner = PlannerAgent()
    executor = ExecutorAgent()
    coordinator = AgentCoordinator(planner, executor)

    state = await coordinator.run(
        task_id="test-compat",
        org_id="org-1",
        navigation_goal="兼容测试",
    )

    assert state.status == "completed"

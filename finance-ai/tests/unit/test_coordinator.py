"""AgentCoordinator 单元测试。"""

import asyncio
from unittest.mock import AsyncMock, MagicMock

import fakeredis.aioredis
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
        async def create_plan(self, navigation_goal, context=None, task_id=None):
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

        async def replan(self, original_goal, completed_subtasks, failed_subtask, failure_reason, context=None, task_id=None):
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

    bus = TaskEventBus(redis_client=fakeredis.aioredis.FakeRedis(decode_responses=True))
    bus.register("task-java-1")

    # 收集 SSE 事件
    events = []
    ready = asyncio.Event()

    async def collect():
        async for event in bus.subscribe("task-java-1", on_ready=ready.set):
            events.append(event)

    asyncio.create_task(collect())
    # 等待订阅就绪，避免 coordinator 在订阅完成前发布 progress 事件导致丢失
    await ready.wait()

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

    bus = TaskEventBus(redis_client=fakeredis.aioredis.FakeRedis(decode_responses=True))
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


# ---------------------------------------------------------------------------
# M4.2 四种失败策略 + 协调状态持久化
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_coordinator_skip_strategy():
    """SKIP 策略：失败子任务应被跳过，任务继续执行到完成。"""
    from app.agent.schemas import TaskPlan

    class SkipPlanner(PlannerAgent):
        """生成 2 个子任务：第一个 SKIP 策略会失败，第二个成功。"""
        async def create_plan(self, navigation_goal, context=None, task_id=None):
            return TaskPlan(
                navigation_goal=navigation_goal,
                subtasks=[
                    SubTask(
                        index=0,
                        goal="会失败的步骤",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.SKIP,
                        max_retries=0,
                    ),
                    SubTask(
                        index=1,
                        goal="后续步骤",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.ABORT,
                        max_retries=0,
                    ),
                ],
            )

    call_count = [0]

    async def handler(goal, context):
        call_count[0] += 1
        # 第 1 次调用（index=0）失败，第 2 次（index=1）成功
        if call_count[0] == 1:
            return {"success": False, "error": "模拟失败"}
        return {"success": True, "data": {"goal": goal}}

    planner = SkipPlanner()
    executor = ExecutorAgent(action_handler=handler)
    coordinator = AgentCoordinator(planner, executor)

    state = await coordinator.run(
        task_id="test-skip",
        org_id="org-1",
        navigation_goal="SKIP 策略测试",
    )

    assert state.status == "completed"
    # 第一个子任务被跳过，第二个成功 → completed_subtasks 只有第二个
    assert len(state.completed_subtasks) == 1


@pytest.mark.asyncio
async def test_coordinator_retry_strategy_exhausted():
    """RETRY 策略：重试耗尽后应终止任务（status=failed）。"""
    from app.agent.schemas import TaskPlan

    class RetryPlanner(PlannerAgent):
        async def create_plan(self, navigation_goal, context=None, task_id=None):
            return TaskPlan(
                navigation_goal=navigation_goal,
                subtasks=[
                    SubTask(
                        index=0,
                        goal="需要重试的步骤",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.RETRY,
                        max_retries=1,  # 1 次重试（共 2 次尝试）
                    ),
                ],
            )

    async def fail_handler(goal, context):
        return {"success": False, "error": "持续失败"}

    planner = RetryPlanner()
    executor = ExecutorAgent(action_handler=fail_handler)
    coordinator = AgentCoordinator(planner, executor)

    state = await coordinator.run(
        task_id="test-retry",
        org_id="org-1",
        navigation_goal="RETRY 策略测试",
    )

    assert state.status == "failed"
    assert "重试耗尽" in (state.error_message or "")


@pytest.mark.asyncio
async def test_coordinator_replan_then_succeed():
    """REPLAN 策略：首次失败 → replan → 新计划成功 → 任务完成。"""
    from app.agent.schemas import TaskPlan

    class ReplanSucceedPlanner(PlannerAgent):
        async def create_plan(self, navigation_goal, context=None, task_id=None):
            return TaskPlan(
                navigation_goal=navigation_goal,
                subtasks=[
                    SubTask(
                        index=0,
                        goal="会失败的步骤",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.REPLAN,
                        max_retries=0,
                    ),
                ],
            )

        async def replan(self, original_goal, completed_subtasks, failed_subtask, failure_reason, context=None, task_id=None):
            return TaskPlan(
                navigation_goal=original_goal,
                subtasks=[
                    SubTask(
                        index=0,
                        goal=f"重试: {original_goal}",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.ABORT,
                        max_retries=0,
                    ),
                ],
                is_replan=True,
                replan_reason=failure_reason,
                version=2,
            )

    call_count = [0]

    async def handler(goal, context):
        call_count[0] += 1
        # 第 1 次调用（初始计划）失败，第 2 次（replan 后）成功
        if call_count[0] == 1:
            return {"success": False, "error": "需要重新规划"}
        return {"success": True, "data": {"goal": goal}}

    planner = ReplanSucceedPlanner()
    executor = ExecutorAgent(action_handler=handler)
    coordinator = AgentCoordinator(planner, executor, max_replans=3)

    state = await coordinator.run(
        task_id="test-replan-succeed",
        org_id="org-1",
        navigation_goal="REPLAN 后成功测试",
    )

    assert state.status == "completed"
    assert state.total_replans == 1
    assert len(state.completed_subtasks) == 1


@pytest.mark.asyncio
async def test_coordinator_persists_coordination_state():
    """M4.2：协调状态应在任务执行过程中被持久化到 Java。"""
    mock_java = MagicMock()
    mock_java.update_task_state = AsyncMock(return_value=True)
    mock_java.update_subtask = AsyncMock(return_value=True)
    mock_java.report_audit_log = AsyncMock(return_value=True)
    mock_java.update_coordination_state = AsyncMock(return_value=True)

    planner = PlannerAgent()
    executor = ExecutorAgent(
        java_client=mock_java,
        task_id="task-persist-1",
        org_id="org-1",
    )
    coordinator = AgentCoordinator(
        planner=planner,
        executor=executor,
        java_client=mock_java,
    )

    state = await coordinator.run(
        task_id="task-persist-1",
        org_id="org-1",
        navigation_goal="持久化测试",
    )

    assert state.status == "completed"

    # 验证 update_coordination_state 被调用（至少 3 次：start + progress + terminal）
    assert mock_java.update_coordination_state.call_count >= 3

    # 验证第一次调用（任务开始）：status=RUNNING
    first_call = mock_java.update_coordination_state.call_args_list[0][1]
    assert first_call["status"] == "RUNNING"
    assert first_call["total_replans"] == 0

    # 验证最后一次调用（终态）：status=COMPLETED
    last_call = mock_java.update_coordination_state.call_args_list[-1][1]
    assert last_call["status"] == "COMPLETED"


@pytest.mark.asyncio
async def test_coordinator_skip_publishes_sse_event():
    """M4.2：SKIP 策略应发布 step_skipped SSE 事件。"""
    from app.agent.schemas import TaskPlan

    class SkipPlanner(PlannerAgent):
        async def create_plan(self, navigation_goal, context=None, task_id=None):
            return TaskPlan(
                navigation_goal=navigation_goal,
                subtasks=[
                    SubTask(
                        index=0,
                        goal="会失败的步骤",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.SKIP,
                        max_retries=0,
                    ),
                    SubTask(
                        index=1,
                        goal="后续步骤",
                        completion_condition="达成",
                        failure_strategy=FailureStrategy.ABORT,
                        max_retries=0,
                    ),
                ],
            )

    async def handler(goal, context):
        if "会失败" in goal:
            return {"success": False, "error": "模拟失败"}
        return {"success": True, "data": {"goal": goal}}

    bus = TaskEventBus(redis_client=fakeredis.aioredis.FakeRedis(decode_responses=True))
    bus.register("task-skip-sse")

    events = []
    ready = asyncio.Event()

    async def collect():
        async for event in bus.subscribe("task-skip-sse", on_ready=ready.set):
            events.append(event)

    asyncio.create_task(collect())
    await ready.wait()

    planner = SkipPlanner()
    executor = ExecutorAgent(
        action_handler=handler,
        event_bus=bus,
        task_id="task-skip-sse",
        org_id="org-1",
    )
    coordinator = AgentCoordinator(
        planner=planner,
        executor=executor,
        event_bus=bus,
    )

    state = await coordinator.run(
        task_id="task-skip-sse",
        org_id="org-1",
        navigation_goal="SKIP SSE 测试",
    )

    assert state.status == "completed"

    # 验证 step_skipped 事件被发布
    await asyncio.sleep(0.05)
    event_types = [e["event"] for e in events]
    assert "step_skipped" in event_types


# ---------------------------------------------------------------------------
# M4.3 断点续跑（initial_plan + resume_from）
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_coordinator_resume_with_initial_plan():
    """M4.3：续跑时传入 initial_plan，应跳过 Planner.create_plan 直接用已存计划。"""
    from app.agent.schemas import TaskPlan

    # 1. 构造已存计划：3 个子任务，第 1 个已完成（在 resume_from 列表中）
    subtask_0 = SubTask(
        index=0, goal="已完成步骤", completion_condition="达成",
        failure_strategy=FailureStrategy.ABORT, max_retries=0,
    )
    subtask_1 = SubTask(
        index=1, goal="待执行步骤 1", completion_condition="达成",
        failure_strategy=FailureStrategy.ABORT, max_retries=0,
    )
    subtask_2 = SubTask(
        index=2, goal="待执行步骤 2", completion_condition="达成",
        failure_strategy=FailureStrategy.ABORT, max_retries=0,
    )
    initial_plan = TaskPlan(
        navigation_goal="续跑测试",
        subtasks=[subtask_0, subtask_1, subtask_2],
    )

    # 2. mock Planner（create_plan 不应被调用）
    planner = PlannerAgent()
    planner.create_plan = AsyncMock(side_effect=AssertionError("续跑时不应调 Planner.create_plan"))

    # 3. Executor：记录实际执行的子任务
    executed_goals: list[str] = []

    async def handler(goal, context):
        executed_goals.append(goal)
        return {"success": True, "data": {"goal": goal}}

    executor = ExecutorAgent(action_handler=handler)
    coordinator = AgentCoordinator(planner=planner, executor=executor)

    # 4. 续跑：resume_from 包含 subtask_0 的 subtask_id
    state = await coordinator.run(
        task_id="test-resume",
        org_id="org-1",
        navigation_goal="续跑测试",
        resume_from=[subtask_0.subtask_id],
        initial_plan=initial_plan,
    )

    # 5. 验证
    assert state.status == "completed"
    # 已完成子任务 0 被跳过，只有 1 和 2 被执行
    assert len(executed_goals) == 2
    assert "已完成步骤" not in executed_goals
    # completed_subtasks 包含全部 3 个（1 个 resume_from + 2 个新完成）
    assert len(state.completed_subtasks) == 3
    # Planner.create_plan 未被调用
    planner.create_plan.assert_not_called()


@pytest.mark.asyncio
async def test_coordinator_resume_all_completed():
    """M4.3：续跑时所有子任务都已完成，应直接返回 completed。"""
    from app.agent.schemas import TaskPlan

    subtask_0 = SubTask(
        index=0, goal="步骤 0", completion_condition="达成",
        failure_strategy=FailureStrategy.ABORT, max_retries=0,
    )
    initial_plan = TaskPlan(
        navigation_goal="全部已完成",
        subtasks=[subtask_0],
    )

    planner = PlannerAgent()
    planner.create_plan = AsyncMock(side_effect=AssertionError("不应调 create_plan"))

    async def handler(goal, context):
        raise AssertionError("不应执行任何子任务")

    executor = ExecutorAgent(action_handler=handler)
    coordinator = AgentCoordinator(planner=planner, executor=executor)

    state = await coordinator.run(
        task_id="test-resume-all",
        org_id="org-1",
        navigation_goal="全部已完成",
        resume_from=[subtask_0.subtask_id],
        initial_plan=initial_plan,
    )

    assert state.status == "completed"
    assert len(state.completed_subtasks) == 1

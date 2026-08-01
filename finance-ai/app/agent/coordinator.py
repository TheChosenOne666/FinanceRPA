"""AgentCoordinator：编排 Planner + Executor 通信。

管理多步骤任务的完整生命周期：
1. Planner 创建初始计划
2. Executor 顺序执行子任务
3. 失败时 Coordinator 请求 Planner 重新规划
4. 子任务状态持久化以支持断点续跑
5. 子任务粒度的审计日志

M2.4 增强：集成 JavaBackendClient 回调 + 事件总线 SSE 推送。
任务开始/进度/终态均回调 Java 更新任务状态，发布 SSE 事件。

@from enterprise/agent/coordinator.py
@author FinanceRPA
"""

import logging
from typing import Any

from app.clients.java_backend import JavaBackendClient
from app.llm.resilient_caller import NeedsHumanError

from .event_bus import TaskEventBus
from .executor import ExecutorAgent
from .planner import PlannerAgent
from .schemas import (
    CoordinationState,
    ExecutionResult,
    FailureStrategy,
    SubTask,
    SubTaskStatus,
    TaskPlan,
)

logger = logging.getLogger(__name__)

# Coordinator 内部状态 → Java TaskStateEnum 大写映射
_STATE_MAP = {
    "running": "EXECUTING",
    "completed": "SUCCESS",
    "failed": "FAILED",
    "needs_human": "NEEDS_HUMAN",
    "aborted": "ABORTED",
}

# CoordinationState.status → Java rpa_agent_coordination_state.status 大写映射（M4.2）
_COORD_STATUS_MAP = {
    "running": "RUNNING",
    "completed": "COMPLETED",
    "failed": "FAILED",
    "needs_human": "NEEDS_HUMAN",
}


class AgentCoordinator:
    """编排 Planner 和 Executor 代理。

    职责：
    - 初始计划创建
    - 顺序子任务执行
    - 失败检测和重新规划
    - 断点续跑（跳过已完成子任务）
    - 审计回调集成
    - M2.4：Java 状态回调 + SSE 事件发布
    """

    def __init__(
        self,
        planner: PlannerAgent,
        executor: ExecutorAgent,
        audit_callback=None,
        max_replans: int = 3,
        java_client: JavaBackendClient | None = None,
        event_bus: TaskEventBus | None = None,
    ):
        """
        @param planner: PlannerAgent 实例
        @param executor: ExecutorAgent 实例
        @param audit_callback: 可选异步回调(subtask, result) 用于审计日志
        @param max_replans: 最大重新规划次数
        @param java_client: Java 后端回调客户端（M2.4 状态回调）
        @param event_bus: 事件总线（M2.4 SSE 推送）
        """
        self.planner = planner
        self.executor = executor
        self.audit_callback = audit_callback
        self.max_replans = max_replans
        self.java_client = java_client
        self.event_bus = event_bus

    async def run(
        self,
        task_id: str,
        org_id: str,
        navigation_goal: str,
        context: dict[str, Any] | None = None,
        resume_from: list[str] | None = None,
        initial_plan: TaskPlan | None = None,
    ) -> CoordinationState:
        """通过 Planner → Executor 协调执行完整任务。

        @param task_id: 唯一任务标识符
        @param org_id: 组织 ID（租户隔离）
        @param navigation_goal: 高级用户目标
        @param context: 共享执行上下文
        @param resume_from: 已完成的子任务 ID 列表（用于断点续跑）
        @param initial_plan: 续跑时传入的已存计划（M4.3）。传入时跳过 Planner.create_plan，
                             直接用此计划执行，确保 subtask_id 与 completed_subtasks 匹配
        @return: 包含最终状态和结果的 CoordinationState
        """
        state = CoordinationState(
            task_id=task_id,
            org_id=org_id,
            navigation_goal=navigation_goal,
            completed_subtasks=resume_from or [],
        )

        # 1. 创建初始计划（续跑时使用传入的已存计划，跳过 Planner）
        if initial_plan is not None:
            logger.info(
                "Coordinator: 断点续跑 → 使用已存计划 [task=%s, subtasks=%d, completed=%d]",
                task_id, len(initial_plan.subtasks), len(state.completed_subtasks),
            )
            plan = initial_plan
        else:
            try:
                logger.info("Coordinator: 开始创建任务计划 [task=%s, goal=%s]", task_id, navigation_goal)
                plan = await self.planner.create_plan(navigation_goal, context, task_id=task_id)
            except NeedsHumanError as e:
                # M5.1：ResilientCaller 重试耗尽 → NEEDS_HUMAN
                logger.error(
                    "Coordinator: 任务 %s 规划转 NEEDS_HUMAN: %s", task_id, e, exc_info=True,
                )
                state.status = "needs_human"
                state.error_message = f"LLM 规划重试耗尽: {e}"
                await self._on_task_terminal(state)
                return state
            except Exception as e:
                logger.error("Coordinator: 任务 %s 规划失败: %s", task_id, e, exc_info=True)
                state.status = "failed"
                state.error_message = f"规划失败: {e}"
                await self._on_task_terminal(state)
                return state

        state.current_plan = plan
        logger.info(
            "Coordinator: 任务 %s 已规划 %d 个子任务: %s",
            task_id, len(plan.subtasks),
            [st.goal for st in plan.subtasks],
        )

        # 2. 回调 Java + SSE：任务开始执行
        await self._on_task_start(state, len(plan.subtasks))

        # 3. 执行子任务
        completed_subtasks: list[SubTask] = []
        return await self._execute_plan(
            state, plan, completed_subtasks, context,
        )

    async def _execute_plan(
        self,
        state: CoordinationState,
        plan: TaskPlan,
        completed_subtasks: list[SubTask],
        context: dict[str, Any] | None,
    ) -> CoordinationState:
        """执行计划中的所有子任务。"""

        for subtask in plan.subtasks:
            # 跳过已完成的子任务（断点续跑）
            if subtask.subtask_id in state.completed_subtasks:
                logger.info(
                    "Coordinator: 跳过已完成的子任务 %s（断点续跑）[task=%s]",
                    subtask.subtask_id, state.task_id,
                )
                completed_subtasks.append(subtask)
                continue

            logger.info(
                "Coordinator: 开始执行子任务 %d/%d [task=%s, subtask=%s, goal=%s]",
                subtask.index + 1, len(plan.subtasks), state.task_id,
                subtask.subtask_id, subtask.goal,
            )

            # 执行子任务
            result = await self.executor.execute_subtask(subtask, context)

            # 审计回调
            if self.audit_callback:
                try:
                    await self.audit_callback(subtask, result)
                except Exception as e:
                    logger.warning(
                        "Coordinator: 子任务 %s 审计回调失败: %s",
                        subtask.subtask_id, e,
                    )

            if result.success:
                state.completed_subtasks.append(subtask.subtask_id)
                completed_subtasks.append(subtask)
                logger.info(
                    "Coordinator: 子任务 %s 成功，已完成 %d/%d [task=%s]",
                    subtask.subtask_id, len(state.completed_subtasks),
                    len(plan.subtasks), state.task_id,
                )
                # 回调 Java：进度更新
                await self._on_task_progress(
                    state,
                    current_step=len(state.completed_subtasks),
                    total_steps=len(plan.subtasks),
                    message=f"子任务 {subtask.index + 1} 完成",
                )
                continue

            # 根据策略处理失败
            logger.info(
                "Coordinator: 子任务 %s 失败，策略=%s [task=%s]",
                subtask.subtask_id, subtask.failure_strategy, state.task_id,
            )
            outcome = await self._handle_failure(
                state, plan, subtask, result, completed_subtasks, context,
            )
            if outcome == "aborted":
                logger.info(
                    "Coordinator: 任务因失败终止 [task=%s, outcome=aborted]", state.task_id,
                )
                await self._on_task_terminal(state)
                return state
            if outcome == "replanned":
                logger.info(
                    "Coordinator: 任务已重新规划 [task=%s, outcome=replanned]", state.task_id,
                )
                return state  # _handle_failure 已递归执行新计划

        # 所有子任务完成
        state.status = "completed"
        logger.info(
            "Coordinator: 任务 %s 全部子任务完成，共 %d 个 [task=%s]",
            state.task_id, len(state.completed_subtasks), state.task_id,
        )
        await self._on_task_terminal(state)
        return state

    async def _handle_failure(
        self,
        state: CoordinationState,
        plan: TaskPlan,
        failed_subtask: SubTask,
        result: ExecutionResult,
        completed_subtasks: list[SubTask],
        context: dict[str, Any] | None,
    ) -> str:
        """根据失败策略处理子任务失败。

        @return: "continued"（跳过继续）/ "aborted"（终止）/ "replanned"（已重新规划）
        """
        strategy = failed_subtask.failure_strategy

        # SKIP：跳过失败步骤
        if strategy == FailureStrategy.SKIP:
            logger.info(
                "Coordinator: 策略=SKIP，跳过失败子任务 %s [task=%s]",
                failed_subtask.subtask_id, state.task_id,
            )
            failed_subtask.status = SubTaskStatus.SKIPPED
            # 发布 SSE 事件：子任务跳过
            if self.event_bus and state.task_id:
                await self.event_bus.publish(
                    state.task_id,
                    "step_skipped",
                    {
                        "subtaskIndex": failed_subtask.index,
                        "message": f"子任务 {failed_subtask.index + 1} 已跳过（策略=SKIP）",
                    },
                )
            # 持久化协调状态（M4.2）
            await self._persist_coordination_state(state)
            return "continued"

        # ABORT：终止任务
        if strategy == FailureStrategy.ABORT:
            logger.error(
                "Coordinator: 策略=ABORT，终止任务 %s 于子任务 %s, error=%s",
                state.task_id, failed_subtask.subtask_id, result.error_message,
            )
            state.status = "failed"
            state.error_message = (
                f"子任务 {failed_subtask.index} 失败: {result.error_message}"
            )
            return "aborted"

        # REPLAN：重新规划
        if strategy == FailureStrategy.REPLAN:
            if state.total_replans >= self.max_replans:
                logger.error(
                    "Coordinator: 策略=REPLAN 但已达最大重新规划次数(%d/%d) [task=%s]",
                    state.total_replans, self.max_replans, state.task_id,
                )
                state.status = "needs_human"
                state.error_message = (
                    f"超过最大重新规划次数。最后失败: {result.error_message}"
                )
                return "aborted"

            state.total_replans += 1
            logger.info(
                "Coordinator: 策略=REPLAN，开始重新规划（第 %d/%d 次）[task=%s, 失败子任务=%s]",
                state.total_replans, self.max_replans, state.task_id, failed_subtask.subtask_id,
            )

            # SSE 事件：重新规划
            if self.event_bus and state.task_id:
                await self.event_bus.publish(
                    state.task_id,
                    "replan",
                    {
                        "totalReplans": state.total_replans,
                        "maxReplans": self.max_replans,
                        "failedSubtaskIndex": failed_subtask.index,
                        "message": f"重新规划（第 {state.total_replans} 次）",
                    },
                )

            try:
                new_plan = await self.planner.replan(
                    original_goal=state.navigation_goal,
                    completed_subtasks=completed_subtasks,
                    failed_subtask=failed_subtask,
                    failure_reason=result.error_message or "未知错误",
                    context=context,
                    task_id=state.task_id,
                )
            except NeedsHumanError as e:
                # M5.1：ResilientCaller 重试耗尽 → NEEDS_HUMAN
                logger.error(
                    "Coordinator: 重新规划转 NEEDS_HUMAN: %s [task=%s]", e, state.task_id, exc_info=True,
                )
                state.status = "needs_human"
                state.error_message = f"LLM 重规划重试耗尽: {e}"
                return "aborted"
            except Exception as e:
                logger.error(
                    "Coordinator: 重新规划失败: %s [task=%s]", e, state.task_id, exc_info=True,
                )
                state.status = "needs_human"
                state.error_message = f"重新规划失败: {e}"
                return "aborted"

            state.current_plan = new_plan
            logger.info(
                "Coordinator: 重新规划成功，新计划 %d 个子任务 [task=%s]",
                len(new_plan.subtasks), state.task_id,
            )

            # 持久化协调状态（M4.2：replan 后更新 total_replans + new_plan）
            await self._persist_coordination_state(state)

            # 递归执行新计划
            await self._execute_plan(state, new_plan, completed_subtasks, context)
            return "replanned"

        # RETRY：由 Executor 内部处理，到此处说明重试已耗尽
        logger.error(
            "Coordinator: 策略=RETRY 但重试已耗尽，终止任务 [task=%s, subtask=%s]",
            state.task_id, failed_subtask.subtask_id,
        )
        state.status = "failed"
        state.error_message = (
            f"子任务 {failed_subtask.index} 重试耗尽: {result.error_message}"
        )
        return "aborted"

    async def _on_task_start(self, state: CoordinationState, total_steps: int) -> None:
        """任务开始执行时的回调：更新 Java 状态 + 发布 SSE 事件 + 持久化协调状态。"""
        logger.info(
            "Coordinator: 任务开始 → 回调 Java EXECUTING [task=%s, total_steps=%d]",
            state.task_id, total_steps,
        )
        # 回调 Java：EXECUTING
        if self.java_client and state.task_id:
            await self.java_client.update_task_state(
                task_id=state.task_id,
                state="EXECUTING",
                current_step=0,
                total_steps=total_steps,
                message="任务开始执行",
            )

        # 发布 SSE 事件
        if self.event_bus and state.task_id:
            logger.info(
                "Coordinator: 发布 SSE 事件 progress(任务开始) [task=%s]", state.task_id,
            )
            await self.event_bus.publish(
                state.task_id,
                "progress",
                {
                    "state": "EXECUTING",
                    "currentStep": 0,
                    "totalSteps": total_steps,
                    "message": "任务开始执行",
                },
            )

        # 持久化协调状态（M4.2：初始计划创建后）
        await self._persist_coordination_state(state)

    async def _on_task_progress(
        self,
        state: CoordinationState,
        current_step: int,
        total_steps: int,
        message: str,
    ) -> None:
        """任务进度更新时的回调：更新 Java 状态 + 发布 SSE 事件 + 持久化协调状态。"""
        logger.info(
            "Coordinator: 进度更新 → 回调 Java EXECUTING [task=%s, step=%d/%d, msg=%s]",
            state.task_id, current_step, total_steps, message,
        )
        # 回调 Java：进度更新
        if self.java_client and state.task_id:
            await self.java_client.update_task_state(
                task_id=state.task_id,
                state="EXECUTING",
                current_step=current_step,
                total_steps=total_steps,
                message=message,
            )

        # 发布 SSE 事件
        if self.event_bus and state.task_id:
            await self.event_bus.publish(
                state.task_id,
                "progress",
                {
                    "state": "EXECUTING",
                    "currentStep": current_step,
                    "totalSteps": total_steps,
                    "message": message,
                },
            )

        # 持久化协调状态（M4.2：每步完成后更新 completed_subtasks）
        await self._persist_coordination_state(state)

    async def _on_task_terminal(self, state: CoordinationState) -> None:
        """任务到达终态时的回调：更新 Java 状态 + 发布 SSE 终态事件 + 持久化协调状态。"""
        java_state = _STATE_MAP.get(state.status, "FAILED")
        message = state.error_message or ("任务完成" if state.status == "completed" else "任务结束")

        logger.info(
            "Coordinator: 任务终态 → %s [task=%s, completed=%d, total_replans=%d, error=%s]",
            java_state, state.task_id, len(state.completed_subtasks),
            state.total_replans, state.error_message,
        )

        # 回调 Java：终态
        if self.java_client and state.task_id:
            logger.info(
                "Coordinator: 回调 Java 终态 %s [task=%s]", java_state, state.task_id,
            )
            await self.java_client.update_task_state(
                task_id=state.task_id,
                state=java_state,
                current_step=len(state.completed_subtasks),
                total_steps=len(state.current_plan.subtasks) if state.current_plan else 0,
                message=message,
                error_message=state.error_message,
            )

        # 发布 SSE 终态事件
        if self.event_bus and state.task_id:
            event_type = "complete" if state.status == "completed" else "error"
            logger.info(
                "Coordinator: 发布 SSE 终态事件 %s [task=%s, state=%s]",
                event_type, state.task_id, java_state,
            )
            await self.event_bus.publish(
                state.task_id,
                event_type,
                {
                    "state": java_state,
                    "message": message,
                    "currentStep": len(state.completed_subtasks),
                    "totalSteps": len(state.current_plan.subtasks) if state.current_plan else 0,
                },
            )

        # 持久化协调状态（M4.2：终态时更新 status + error_message）
        await self._persist_coordination_state(state)

    async def _persist_coordination_state(self, state: CoordinationState) -> None:
        """持久化 CoordinationState 到 Java（M4.2 引入）。

        将当前协调状态（导航目标、当前计划、已完成子任务列表、replan 次数、状态）
        回调 Java 持久化到 rpa_agent_coordination_state 表，用于断点续跑和 replan 追踪。
        失败不阻断主流程（仅 warning 日志）。
        """
        if not self.java_client or not state.task_id:
            return

        # 序列化 current_plan 为 JSON 字符串
        current_plan_json = None
        if state.current_plan:
            try:
                current_plan_json = state.current_plan.model_dump_json()
            except Exception as e:
                logger.warning(
                    "Coordinator: 序列化 current_plan 失败: %s [task=%s]", e, state.task_id,
                )

        # 状态映射（小写 → 大写）
        status_upper = _COORD_STATUS_MAP.get(state.status, state.status.upper())

        try:
            await self.java_client.update_coordination_state(
                task_id=state.task_id,
                navigation_goal=state.navigation_goal,
                current_plan=current_plan_json,
                completed_subtasks=state.completed_subtasks,
                total_replans=state.total_replans,
                max_replans=self.max_replans,
                status=status_upper,
                error_message=state.error_message,
            )
        except Exception as e:
            logger.warning(
                "Coordinator: 持久化协调状态失败: %s [task=%s]", e, state.task_id,
            )

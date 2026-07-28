"""AgentCoordinator：编排 Planner + Executor 通信。

管理多步骤任务的完整生命周期：
1. Planner 创建初始计划
2. Executor 顺序执行子任务
3. 失败时 Coordinator 请求 Planner 重新规划
4. 子任务状态持久化以支持断点续跑
5. 子任务粒度的审计日志

@from enterprise/agent/coordinator.py
@author FinanceRPA
"""

import logging
from typing import Any

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


class AgentCoordinator:
    """编排 Planner 和 Executor 代理。

    职责：
    - 初始计划创建
    - 顺序子任务执行
    - 失败检测和重新规划
    - 断点续跑（跳过已完成子任务）
    - 审计回调集成
    """

    def __init__(
        self,
        planner: PlannerAgent,
        executor: ExecutorAgent,
        audit_callback=None,
        max_replans: int = 3,
    ):
        """
        @param planner: PlannerAgent 实例
        @param executor: ExecutorAgent 实例
        @param audit_callback: 可选异步回调(subtask, result) 用于审计日志
        @param max_replans: 最大重新规划次数
        """
        self.planner = planner
        self.executor = executor
        self.audit_callback = audit_callback
        self.max_replans = max_replans

    async def run(
        self,
        task_id: str,
        org_id: str,
        navigation_goal: str,
        context: dict[str, Any] | None = None,
        resume_from: list[str] | None = None,
    ) -> CoordinationState:
        """通过 Planner → Executor 协调执行完整任务。

        @param task_id: 唯一任务标识符
        @param org_id: 组织 ID（租户隔离）
        @param navigation_goal: 高级用户目标
        @param context: 共享执行上下文
        @param resume_from: 已完成的子任务 ID 列表（用于断点续跑）
        @return: 包含最终状态和结果的 CoordinationState
        """
        state = CoordinationState(
            task_id=task_id,
            org_id=org_id,
            navigation_goal=navigation_goal,
            completed_subtasks=resume_from or [],
        )

        # 1. 创建初始计划
        try:
            plan = await self.planner.create_plan(navigation_goal, context)
        except Exception as e:
            logger.error("Coordinator: 任务 %s 规划失败: %s", task_id, e)
            state.status = "failed"
            state.error_message = f"规划失败: {e}"
            return state

        state.current_plan = plan
        logger.info(
            "Coordinator: 任务 %s 已规划 %d 个子任务",
            task_id, len(plan.subtasks),
        )

        # 2. 执行子任务
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
                    "Coordinator: 跳过已完成的子任务 %s",
                    subtask.subtask_id,
                )
                completed_subtasks.append(subtask)
                continue

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
                continue

            # 根据策略处理失败
            outcome = await self._handle_failure(
                state, plan, subtask, result, completed_subtasks, context,
            )
            if outcome == "aborted":
                return state
            if outcome == "replanned":
                return state  # _handle_failure 已递归执行新计划

        # 所有子任务完成
        state.status = "completed"
        logger.info("Coordinator: 任务 %s 成功完成", state.task_id)
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
                "Coordinator: 跳过失败子任务 %s",
                failed_subtask.subtask_id,
            )
            failed_subtask.status = SubTaskStatus.SKIPPED
            return "continued"

        # ABORT：终止任务
        if strategy == FailureStrategy.ABORT:
            logger.error(
                "Coordinator: 终止任务 %s 于子任务 %s",
                state.task_id, failed_subtask.subtask_id,
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
                    "Coordinator: 任务 %s 已达最大重新规划次数(%d)",
                    state.task_id, self.max_replans,
                )
                state.status = "needs_human"
                state.error_message = (
                    f"超过最大重新规划次数。最后失败: {result.error_message}"
                )
                return "aborted"

            state.total_replans += 1
            logger.info(
                "Coordinator: 重新规划任务 %s（第 %d/%d 次）",
                state.task_id, state.total_replans, self.max_replans,
            )

            try:
                new_plan = await self.planner.replan(
                    original_goal=state.navigation_goal,
                    completed_subtasks=completed_subtasks,
                    failed_subtask=failed_subtask,
                    failure_reason=result.error_message or "未知错误",
                    context=context,
                )
            except Exception as e:
                logger.error("Coordinator: 重新规划失败: %s", e)
                state.status = "needs_human"
                state.error_message = f"重新规划失败: {e}"
                return "aborted"

            state.current_plan = new_plan

            # 递归执行新计划
            await self._execute_plan(state, new_plan, completed_subtasks, context)
            return "replanned"

        # RETRY：由 Executor 内部处理，到此处说明重试已耗尽
        state.status = "failed"
        state.error_message = (
            f"子任务 {failed_subtask.index} 重试耗尽: {result.error_message}"
        )
        return "aborted"

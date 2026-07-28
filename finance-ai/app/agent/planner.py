"""PlannerAgent：将导航目标拆解为有序子任务计划。

接收高级导航目标和当前任务上下文，产出包含有序 SubTask 的结构化 TaskPlan。
每个子任务有明确的目标、完成条件和失败策略。

M2.1 实现 fallback 版（单步计划，不依赖 LLM）。
M4.1 接入 LLM 版（_plan_with_llm / _replan_with_llm）。

@from enterprise/agent/planner.py
@author FinanceRPA
"""

import logging
from typing import Any

from .schemas import FailureStrategy, SubTask, TaskPlan

logger = logging.getLogger(__name__)


class PlannerAgent:
    """将导航目标拆解为子任务计划。

    M2.1 使用 fallback 模式（单步计划），不依赖 LLM。
    M4.1 注入 llm_callable 后启用 LLM 拆解。
    """

    def __init__(self, llm_callable=None):
        """
        @param llm_callable: 异步函数 prompt -> str（M4.1 注入，M2.1 传 None）
        """
        self.llm_callable = llm_callable

    async def create_plan(
        self,
        navigation_goal: str,
        context: dict[str, Any] | None = None,
    ) -> TaskPlan:
        """生成初始任务计划。

        @param navigation_goal: 高级目标（如 "下载 Q1 银行流水"）
        @param context: 可选上下文（当前 URL、页面状态等）
        @return: 包含有序 SubTask 的 TaskPlan
        """
        if self.llm_callable:
            # M4.1: LLM 拆解（暂未实现）
            return await self._plan_with_llm(navigation_goal, context)
        return self._create_fallback_plan(navigation_goal)

    async def replan(
        self,
        original_goal: str,
        completed_subtasks: list[SubTask],
        failed_subtask: SubTask,
        failure_reason: str,
        context: dict[str, Any] | None = None,
    ) -> TaskPlan:
        """失败后重新规划剩余步骤。

        M2.1 fallback：跳过失败步骤，创建继续计划。
        M4.1：接入 LLM 重新规划。

        @param original_goal: 原始导航目标
        @param completed_subtasks: 已成功完成的子任务
        @param failed_subtask: 失败的子任务
        @param failure_reason: 失败原因
        @param context: 当前页面状态上下文
        @return: 剩余步骤的修订 TaskPlan
        """
        if self.llm_callable:
            # M4.1: LLM 重新规划（暂未实现）
            return await self._replan_with_llm(
                original_goal, completed_subtasks, failed_subtask, failure_reason, context,
            )

        # fallback：创建继续计划
        return TaskPlan(
            navigation_goal=original_goal,
            subtasks=[
                SubTask(
                    index=0,
                    goal=f"失败后继续: {original_goal}",
                    completion_condition="任务目标达成",
                    failure_strategy=FailureStrategy.ABORT,
                ),
            ],
            is_replan=True,
            replan_reason=failure_reason,
            version=len(completed_subtasks) + 2,
        )

    async def _plan_with_llm(
        self,
        navigation_goal: str,
        context: dict[str, Any] | None,
    ) -> TaskPlan:
        """M4.1 实现：使用 LLM 生成计划。"""
        raise NotImplementedError("LLM 规划在 M4.1 实现")

    async def _replan_with_llm(
        self,
        original_goal: str,
        completed_subtasks: list[SubTask],
        failed_subtask: SubTask,
        failure_reason: str,
        context: dict[str, Any] | None,
    ) -> TaskPlan:
        """M4.1 实现：使用 LLM 重新规划。"""
        raise NotImplementedError("LLM 重新规划在 M4.1 实现")

    def _create_fallback_plan(self, navigation_goal: str) -> TaskPlan:
        """创建简单的单步计划（不需要 LLM）。"""
        return TaskPlan(
            navigation_goal=navigation_goal,
            subtasks=[
                SubTask(
                    index=0,
                    goal=navigation_goal,
                    completion_condition="导航目标达成",
                    failure_strategy=FailureStrategy.ABORT,
                    max_retries=3,
                ),
            ],
        )

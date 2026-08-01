"""PlannerAgent：将导航目标拆解为有序子任务计划。

接收高级导航目标和当前任务上下文，产出包含有序 SubTask 的结构化 TaskPlan。
每个子任务有明确的目标、完成条件和失败策略。

M2.1 实现 fallback 版（单步计划，不依赖 LLM）。
M4.1 接入 LLM 版（_plan_with_llm / _replan_with_llm）：
  - 构造 system prompt + 用户目标 + 上下文 → 调 llm_callable
  - 清理 ``` 代码块包裹 → 解析 JSON → 构建 SubTask 列表
  - 任意环节失败（LLM 异常 / JSON 解析失败 / 空步骤）自动回退到 fallback 计划
  - replan 上限由 Coordinator 的 max_replans 控制（默认 3 次，超限转 NEEDS_HUMAN）

M5.1 集成 ResilientCaller（三层容错）：
  - 注入 resilient_caller 后，_plan_with_llm / _replan_with_llm 改用 ResilientCaller.call()
  - 层 1：Prompt 注入 JSON Schema + 格式约束
  - 层 2：Pydantic 校验 + 失败反馈重试（默认 2 次）
  - 层 3：重试耗尽抛 NeedsHumanError → 传播给 Coordinator 转 NEEDS_HUMAN 状态
  - NeedsHumanError 不被 fallback 捕获，确保 NEEDS_HUMAN 状态正确传播

@from enterprise/agent/planner.py
@author FinanceRPA
"""

import json
import logging
from typing import Any

from pydantic import BaseModel, Field

from app.llm.resilient_caller import NeedsHumanError, ResilientCaller

from .schemas import FailureStrategy, SubTask, TaskPlan

logger = logging.getLogger(__name__)


# region LLM Prompt 常量

PLANNER_SYSTEM_PROMPT = """\
You are a financial RPA planning agent. Your job is to decompose a navigation \
goal into a sequence of concrete sub-tasks that a browser automation executor \
can perform step by step.

Each sub-task must have:
- "goal": a clear, actionable description of what to do
- "completion_condition": how to verify success (e.g. "page URL contains /dashboard")
- "failure_strategy": one of "retry", "skip", "abort", "replan"
- "max_retries": integer (default 2)

Output ONLY a JSON object with a "steps" array. No other text.

Example:
{
  "steps": [
    {"goal": "Login to the system", "completion_condition": "URL contains /home", "failure_strategy": "abort", "max_retries": 3},
    {"goal": "Navigate to account page", "completion_condition": "Page title contains Account", "failure_strategy": "replan", "max_retries": 2}
  ]
}
"""

REPLAN_SYSTEM_PROMPT = """\
You are a financial RPA planning agent. A previous plan failed at a specific \
step. You are given the original goal, the steps completed so far, and the \
failure details. Generate a REVISED plan for the remaining steps only. \
Do NOT repeat already-completed steps.

Output ONLY a JSON object with a "steps" array.
"""

# endregion


class PlannerOutput(BaseModel):
    """LLM 产出的计划 JSON 结构。"""

    steps: list[dict[str, Any]] = Field(
        description="有序子任务列表，每项含 goal/completion_condition/failure_strategy/max_retries",
    )


class PlannerAgent:
    """将导航目标拆解为子任务计划。

    M2.1 使用 fallback 模式（单步计划），不依赖 LLM。
    M4.1 注入 llm_callable 后启用 LLM 拆解。
    M5.1 注入 resilient_caller 后启用三层容错（Pydantic 校验 + 重试 + NEEDS_HUMAN）。
    """

    def __init__(self, llm_callable=None, resilient_caller: ResilientCaller | None = None):
        """
        @param llm_callable: 异步函数 prompt -> str（M4.1 注入，M2.1 传 None）
        @param resilient_caller: ResilientCaller 实例（M5.1 注入，优先于 llm_callable）
        """
        self.llm_callable = llm_callable
        self.resilient_caller = resilient_caller

    async def create_plan(
        self,
        navigation_goal: str,
        context: dict[str, Any] | None = None,
        task_id: str | None = None,
    ) -> TaskPlan:
        """生成初始任务计划。

        @param navigation_goal: 高级目标（如 "下载 Q1 银行流水"）
        @param context: 可选上下文（当前 URL、页面状态等）
        @param task_id: 任务 ID（M5.1 ResilientCaller 上报用）
        @return: 包含有序 SubTask 的 TaskPlan
        """
        if self.resilient_caller:
            # M5.1: 三层容错调用（NeedsHumanError 传播给 Coordinator）
            return await self._plan_with_llm(navigation_goal, context, task_id)
        if self.llm_callable:
            # M4.1: 直接 LLM 调用（带 fallback）
            return await self._plan_with_llm_legacy(navigation_goal, context)
        return self._create_fallback_plan(navigation_goal)

    async def replan(
        self,
        original_goal: str,
        completed_subtasks: list[SubTask],
        failed_subtask: SubTask,
        failure_reason: str,
        context: dict[str, Any] | None = None,
        task_id: str | None = None,
    ) -> TaskPlan:
        """失败后重新规划剩余步骤。

        M2.1 fallback：跳过失败步骤，创建继续计划。
        M4.1：接入 LLM 重新规划。
        M5.1：ResilientCaller 三层容错重规划。

        @param original_goal: 原始导航目标
        @param completed_subtasks: 已成功完成的子任务
        @param failed_subtask: 失败的子任务
        @param failure_reason: 失败原因
        @param context: 当前页面状态上下文
        @param task_id: 任务 ID（M5.1 ResilientCaller 上报用）
        @return: 剩余步骤的修订 TaskPlan
        """
        if self.resilient_caller:
            # M5.1: 三层容错重规划（NeedsHumanError 传播给 Coordinator）
            return await self._replan_with_llm(
                original_goal, completed_subtasks, failed_subtask,
                failure_reason, context, task_id,
            )
        if self.llm_callable:
            # M4.1: 直接 LLM 重规划（带 fallback）
            return await self._replan_with_llm_legacy(
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
        task_id: str | None,
    ) -> TaskPlan:
        """M5.1：使用 ResilientCaller 生成初始计划（三层容错）。

        流程：构造 prompt → ResilientCaller.call()（层 1 Schema 约束 + 层 2 Pydantic 校验重试）→ 构建 SubTask。
        NeedsHumanError 不被捕获，传播给 Coordinator 转 NEEDS_HUMAN 状态。
        其他异常（网络错误等）回退到 fallback 单步计划。

        @param navigation_goal: 高级导航目标
        @param context: 可选上下文（当前 URL、页面状态等）
        @param task_id: 任务 ID（ResilientCaller 上报用）
        @return: LLM 产出的 TaskPlan，或网络异常时的 fallback 计划
        @raises NeedsHumanError: 重试耗尽（层 3），由 Coordinator 捕获
        """
        ctx_str = json.dumps(context, ensure_ascii=False) if context else "无附加上下文"
        prompt = (
            f"{PLANNER_SYSTEM_PROMPT}\n\n"
            f"## Navigation Goal\n{navigation_goal}\n\n"
            f"## Context\n{ctx_str}\n"
        )

        try:
            result = await self.resilient_caller.call(
                prompt=prompt,
                output_model=PlannerOutput,
                task_id=task_id,
                context_name="planner",
            )
        except NeedsHumanError:
            # 层 3：重试耗尽 → 传播给 Coordinator
            raise
        except Exception as e:
            # 网络异常等非校验错误 → fallback
            logger.warning(
                "PlannerAgent: ResilientCaller 调用异常（%s），回退到 fallback 计划 [goal=%s]",
                e, navigation_goal,
            )
            return self._create_fallback_plan(navigation_goal)

        subtasks = self._build_subtasks(result.steps, start_index=0)
        plan = TaskPlan(
            navigation_goal=navigation_goal,
            subtasks=subtasks,
        )
        logger.info(
            "PlannerAgent: LLM 拆解完成（ResilientCaller），生成 %d 个子任务 [goal=%s]",
            len(subtasks), navigation_goal,
        )
        return plan

    async def _replan_with_llm(
        self,
        original_goal: str,
        completed_subtasks: list[SubTask],
        failed_subtask: SubTask,
        failure_reason: str,
        context: dict[str, Any] | None,
        task_id: str | None,
    ) -> TaskPlan:
        """M5.1：使用 ResilientCaller 重新规划剩余步骤（三层容错）。

        NeedsHumanError 不被捕获，传播给 Coordinator 转 NEEDS_HUMAN 状态。
        其他异常（网络错误等）回退到单步继续计划。

        @param original_goal: 原始导航目标
        @param completed_subtasks: 已成功完成的子任务
        @param failed_subtask: 失败的子任务
        @param failure_reason: 失败原因
        @param context: 当前页面状态上下文
        @param task_id: 任务 ID（ResilientCaller 上报用）
        @return: 修订后的 TaskPlan
        @raises NeedsHumanError: 重试耗尽（层 3），由 Coordinator 捕获
        """
        completed_summary = "\n".join(
            f"- Step {s.index}: {s.goal} [COMPLETED]"
            for s in completed_subtasks
        )
        prompt = (
            f"{REPLAN_SYSTEM_PROMPT}\n\n"
            f"## Original Goal\n{original_goal}\n\n"
            f"## Completed Steps\n{completed_summary or 'None'}\n\n"
            f"## Failed Step\nStep {failed_subtask.index}: {failed_subtask.goal}\n"
            f"Failure reason: {failure_reason}\n\n"
            f"## Context\n{json.dumps(context, ensure_ascii=False) if context else 'None'}\n"
        )

        try:
            result = await self.resilient_caller.call(
                prompt=prompt,
                output_model=PlannerOutput,
                task_id=task_id,
                context_name="replan",
            )
        except NeedsHumanError:
            # 层 3：重试耗尽 → 传播给 Coordinator
            raise
        except Exception as e:
            # 网络异常等非校验错误 → fallback 继续计划
            logger.warning(
                "PlannerAgent: ResilientCaller 重规划异常（%s），回退到 fallback 计划 [goal=%s]",
                e, original_goal,
            )
            return TaskPlan(
                navigation_goal=original_goal,
                subtasks=[
                    SubTask(
                        index=len(completed_subtasks),
                        goal=f"失败后继续: {original_goal}",
                        completion_condition="任务目标达成",
                        failure_strategy=FailureStrategy.ABORT,
                    ),
                ],
                is_replan=True,
                replan_reason=failure_reason,
                version=len(completed_subtasks) + 2,
            )

        subtasks = self._build_subtasks(result.steps, start_index=len(completed_subtasks))
        plan = TaskPlan(
            navigation_goal=original_goal,
            subtasks=subtasks,
            is_replan=True,
            replan_reason=failure_reason,
            version=len(completed_subtasks) + 2,
        )
        logger.info(
            "PlannerAgent: LLM 重新规划完成（ResilientCaller），生成 %d 个新子任务 [reason=%s]",
            len(subtasks), (failure_reason[:80] if failure_reason else "未知"),
        )
        return plan

    async def _plan_with_llm_legacy(
        self,
        navigation_goal: str,
        context: dict[str, Any] | None,
    ) -> TaskPlan:
        """M4.1：直接调 llm_callable 生成初始计划（带 fallback，无 Pydantic 校验重试）。

        @param navigation_goal: 高级导航目标
        @param context: 可选上下文（当前 URL、页面状态等）
        @return: LLM 产出的 TaskPlan，或失败时的 fallback 计划
        """
        ctx_str = json.dumps(context, ensure_ascii=False) if context else "无附加上下文"
        prompt = (
            f"{PLANNER_SYSTEM_PROMPT}\n\n"
            f"## Navigation Goal\n{navigation_goal}\n\n"
            f"## Context\n{ctx_str}\n"
        )

        try:
            raw = await self.llm_callable(prompt)
            subtasks = self._parse_llm_steps(raw, start_index=0)

            plan = TaskPlan(
                navigation_goal=navigation_goal,
                subtasks=subtasks,
            )
            logger.info(
                "PlannerAgent: LLM 拆解完成，生成 %d 个子任务 [goal=%s]",
                len(subtasks), navigation_goal,
            )
            return plan

        except Exception as e:
            # LLM 异常 / JSON 解析失败 / 空步骤 → fallback
            logger.warning(
                "PlannerAgent: LLM 规划失败（%s），回退到 fallback 计划 [goal=%s]",
                e, navigation_goal,
            )
            return self._create_fallback_plan(navigation_goal)

    async def _replan_with_llm_legacy(
        self,
        original_goal: str,
        completed_subtasks: list[SubTask],
        failed_subtask: SubTask,
        failure_reason: str,
        context: dict[str, Any] | None,
    ) -> TaskPlan:
        """M4.1：直接调 llm_callable 重新规划剩余步骤（带 fallback，无 Pydantic 校验重试）。

        @param original_goal: 原始导航目标
        @param completed_subtasks: 已成功完成的子任务
        @param failed_subtask: 失败的子任务
        @param failure_reason: 失败原因
        @param context: 当前页面状态上下文
        @return: 修订后的 TaskPlan
        """
        completed_summary = "\n".join(
            f"- Step {s.index}: {s.goal} [COMPLETED]"
            for s in completed_subtasks
        )
        prompt = (
            f"{REPLAN_SYSTEM_PROMPT}\n\n"
            f"## Original Goal\n{original_goal}\n\n"
            f"## Completed Steps\n{completed_summary or 'None'}\n\n"
            f"## Failed Step\nStep {failed_subtask.index}: {failed_subtask.goal}\n"
            f"Failure reason: {failure_reason}\n\n"
            f"## Context\n{json.dumps(context, ensure_ascii=False) if context else 'None'}\n"
        )

        try:
            raw = await self.llm_callable(prompt)
            # 新步骤 index 从已完成数量开始递增，保持全局有序
            subtasks = self._parse_llm_steps(raw, start_index=len(completed_subtasks))

            plan = TaskPlan(
                navigation_goal=original_goal,
                subtasks=subtasks,
                is_replan=True,
                replan_reason=failure_reason,
                version=len(completed_subtasks) + 2,
            )
            logger.info(
                "PlannerAgent: LLM 重新规划完成，生成 %d 个新子任务 [reason=%s]",
                len(subtasks), (failure_reason[:80] if failure_reason else "未知"),
            )
            return plan

        except Exception as e:
            # LLM 异常 / JSON 解析失败 → fallback 继续计划
            logger.warning(
                "PlannerAgent: LLM 重新规划失败（%s），回退到 fallback 计划 [goal=%s]",
                e, original_goal,
            )
            return TaskPlan(
                navigation_goal=original_goal,
                subtasks=[
                    SubTask(
                        index=len(completed_subtasks),
                        goal=f"失败后继续: {original_goal}",
                        completion_condition="任务目标达成",
                        failure_strategy=FailureStrategy.ABORT,
                    ),
                ],
                is_replan=True,
                replan_reason=failure_reason,
                version=len(completed_subtasks) + 2,
            )

    def _parse_llm_steps(self, raw: str, start_index: int) -> list[SubTask]:
        """解析 LLM 返回的 JSON 为 SubTask 列表（M4.1 legacy 版）。

        @param raw: LLM 原始返回（可能含 ```json 代码块包裹）
        @param start_index: 子任务 index 起始值（replan 时从已完成数量开始）
        @return: 有序 SubTask 列表
        @raises ValueError: JSON 解析失败或 steps 为空
        """
        # 1. 清理 ``` 代码块包裹
        cleaned = raw.strip()
        if cleaned.startswith("```"):
            lines = cleaned.split("\n")
            # 去掉首行 ``` 和末行 ```
            cleaned = "\n".join(lines[1:-1])

        # 2. 解析 JSON
        data = json.loads(cleaned)
        raw_steps = data.get("steps", [])

        if not raw_steps:
            raise ValueError("LLM 返回的 steps 为空")

        # 3. 构建 SubTask 列表
        return self._build_subtasks(raw_steps, start_index)

    def _build_subtasks(
        self,
        raw_steps: list[dict[str, Any]],
        start_index: int,
    ) -> list[SubTask]:
        """将 step 字典列表转换为 SubTask 列表（M5.1 ResilientCaller 版共用）。

        @param raw_steps: step 字典列表（每项含 goal/completion_condition/failure_strategy/max_retries）
        @param start_index: 子任务 index 起始值
        @return: 有序 SubTask 列表
        @raises ValueError: steps 为空
        """
        if not raw_steps:
            raise ValueError("LLM 返回的 steps 为空")

        subtasks: list[SubTask] = []
        for i, step in enumerate(raw_steps):
            subtasks.append(SubTask(
                index=start_index + i,
                goal=step.get("goal", f"步骤 {start_index + i + 1}"),
                completion_condition=step.get("completion_condition", ""),
                max_retries=step.get("max_retries", 2),
                failure_strategy=FailureStrategy(
                    step.get("failure_strategy", "replan")
                ),
            ))
        return subtasks

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

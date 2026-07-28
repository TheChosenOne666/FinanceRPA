"""PlannerAgent 单元测试（fallback 版）。"""

import pytest

from app.agent.planner import PlannerAgent
from app.agent.schemas import FailureStrategy


@pytest.mark.asyncio
async def test_fallback_plan_single_step():
    """fallback 模式应生成单步计划。"""
    planner = PlannerAgent()  # 无 llm_callable
    plan = await planner.create_plan("下载银行流水")

    assert plan.navigation_goal == "下载银行流水"
    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "下载银行流水"
    assert plan.subtasks[0].index == 0
    assert plan.subtasks[0].failure_strategy == FailureStrategy.ABORT
    assert plan.subtasks[0].max_retries == 3
    assert plan.is_replan is False
    assert plan.version == 1


@pytest.mark.asyncio
async def test_fallback_replan():
    """fallback replan 应生成继续计划。"""
    from app.agent.schemas import SubTask

    planner = PlannerAgent()
    completed = [
        SubTask(index=0, goal="步骤1", completion_condition="完成"),
    ]
    failed = SubTask(index=1, goal="步骤2", completion_condition="完成")

    plan = await planner.replan(
        original_goal="原始目标",
        completed_subtasks=completed,
        failed_subtask=failed,
        failure_reason="元素未找到",
    )

    assert plan.is_replan is True
    assert plan.replan_reason == "元素未找到"
    assert plan.version == len(completed) + 2
    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].failure_strategy == FailureStrategy.ABORT


@pytest.mark.asyncio
async def test_plan_with_context():
    """带上下文应正常生成 fallback 计划。"""
    planner = PlannerAgent()
    plan = await planner.create_plan(
        "登录系统",
        context={"current_url": "https://example.com/login"},
    )
    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "登录系统"

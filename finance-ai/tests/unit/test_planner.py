"""PlannerAgent 单元测试。

覆盖：
- M2.1 fallback 模式（无 llm_callable）
- M4.1 LLM 模式：拆解成功 / 代码块清理 / replan / 异常 fallback / 空 steps fallback
"""

import json

import pytest

from app.agent.planner import PlannerAgent
from app.agent.schemas import FailureStrategy, SubTask


# region 辅助函数

def _make_llm(response: str):
    """构造 mock llm_callable，返回固定响应。"""
    async def _callable(prompt: str) -> str:
        return response
    return _callable


def _make_failing_llm(error: Exception):
    """构造抛异常的 mock llm_callable。"""
    async def _callable(prompt: str) -> str:
        raise error
    return _callable


_VALID_STEPS_JSON = json.dumps({
    "steps": [
        {"goal": "登录系统", "completion_condition": "URL 包含 /home", "failure_strategy": "abort", "max_retries": 3},
        {"goal": "导航到账户页", "completion_condition": "页面标题包含 账户", "failure_strategy": "replan", "max_retries": 2},
        {"goal": "下载流水", "completion_condition": "下载文件触发", "failure_strategy": "retry", "max_retries": 2},
    ]
}, ensure_ascii=False)


_VALID_REPLAN_JSON = json.dumps({
    "steps": [
        {"goal": "切换登录方式", "completion_condition": "出现验证码输入框", "failure_strategy": "replan", "max_retries": 2},
        {"goal": "重新下载流水", "completion_condition": "下载文件触发", "failure_strategy": "abort", "max_retries": 3},
    ]
}, ensure_ascii=False)

# endregion


# region M2.1 fallback 模式测试

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
async def test_plan_with_context_fallback():
    """带上下文应正常生成 fallback 计划。"""
    planner = PlannerAgent()
    plan = await planner.create_plan(
        "登录系统",
        context={"current_url": "https://example.com/login"},
    )
    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "登录系统"

# endregion


# region M4.1 LLM 拆解测试

@pytest.mark.asyncio
async def test_llm_plan_success():
    """LLM 返回合法 JSON 应正确拆解为多子任务计划。"""
    planner = PlannerAgent(llm_callable=_make_llm(_VALID_STEPS_JSON))
    plan = await planner.create_plan("下载 Q1 银行流水")

    assert plan.navigation_goal == "下载 Q1 银行流水"
    assert plan.is_replan is False
    assert plan.version == 1
    assert len(plan.subtasks) == 3

    # 验证 index 从 0 递增
    assert [s.index for s in plan.subtasks] == [0, 1, 2]

    # 验证字段映射
    s0 = plan.subtasks[0]
    assert s0.goal == "登录系统"
    assert s0.completion_condition == "URL 包含 /home"
    assert s0.failure_strategy == FailureStrategy.ABORT
    assert s0.max_retries == 3

    s1 = plan.subtasks[1]
    assert s1.failure_strategy == FailureStrategy.REPLAN
    assert s1.max_retries == 2

    s2 = plan.subtasks[2]
    assert s2.failure_strategy == FailureStrategy.RETRY


@pytest.mark.asyncio
async def test_llm_plan_with_codeblock_wrapper():
    """LLM 返回含 ```json 代码块包裹时应正确解析。"""
    wrapped = f"```json\n{_VALID_STEPS_JSON}\n```"
    planner = PlannerAgent(llm_callable=_make_llm(wrapped))
    plan = await planner.create_plan("下载流水")

    assert len(plan.subtasks) == 3
    assert plan.subtasks[0].goal == "登录系统"


@pytest.mark.asyncio
async def test_llm_plan_with_context():
    """带上下文应将上下文序列化进 prompt 并正常拆解。"""
    planner = PlannerAgent(llm_callable=_make_llm(_VALID_STEPS_JSON))
    plan = await planner.create_plan(
        "下载流水",
        context={"current_url": "https://bank.example.com/home", "user": "test"},
    )
    assert len(plan.subtasks) == 3


@pytest.mark.asyncio
async def test_llm_plan_default_values_for_missing_fields():
    """LLM 返回缺失字段时应使用默认值。"""
    partial_json = json.dumps({
        "steps": [
            {"goal": "仅目标"},
            {},
        ]
    })
    planner = PlannerAgent(llm_callable=_make_llm(partial_json))
    plan = await planner.create_plan("目标")

    assert len(plan.subtasks) == 2
    s0 = plan.subtasks[0]
    assert s0.goal == "仅目标"
    assert s0.completion_condition == ""
    assert s0.max_retries == 2  # 默认
    assert s0.failure_strategy == FailureStrategy.REPLAN  # 默认

    s1 = plan.subtasks[1]
    assert s1.goal == "步骤 2"  # 默认 goal
    assert s1.index == 1


@pytest.mark.asyncio
async def test_llm_plan_invalid_json_fallback():
    """LLM 返回非法 JSON 时应回退到 fallback 单步计划。"""
    planner = PlannerAgent(llm_callable=_make_llm("这不是 JSON"))
    plan = await planner.create_plan("下载流水")

    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "下载流水"
    assert plan.subtasks[0].failure_strategy == FailureStrategy.ABORT
    assert plan.subtasks[0].max_retries == 3


@pytest.mark.asyncio
async def test_llm_plan_empty_steps_fallback():
    """LLM 返回空 steps 数组时应回退到 fallback 单步计划。"""
    empty_json = json.dumps({"steps": []})
    planner = PlannerAgent(llm_callable=_make_llm(empty_json))
    plan = await planner.create_plan("下载流水")

    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "下载流水"


@pytest.mark.asyncio
async def test_llm_plan_exception_fallback():
    """llm_callable 抛异常时应回退到 fallback 单步计划。"""
    planner = PlannerAgent(
        llm_callable=_make_failing_llm(RuntimeError("LLM 服务不可用")),
    )
    plan = await planner.create_plan("下载流水")

    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "下载流水"
    assert plan.subtasks[0].failure_strategy == FailureStrategy.ABORT


@pytest.mark.asyncio
async def test_llm_plan_invalid_failure_strategy_fallback():
    """LLM 返回非法 failure_strategy 值时应回退到 fallback。"""
    bad_strategy_json = json.dumps({
        "steps": [
            {"goal": "步骤1", "failure_strategy": "unknown_strategy"},
        ]
    })
    planner = PlannerAgent(llm_callable=_make_llm(bad_strategy_json))
    plan = await planner.create_plan("目标")

    # 非法枚举值触发 ValueError → fallback
    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "目标"

# endregion


# region M4.1 LLM replan 测试

@pytest.mark.asyncio
async def test_llm_replan_success():
    """LLM replan 应正确生成剩余步骤计划。"""
    planner = PlannerAgent(llm_callable=_make_llm(_VALID_REPLAN_JSON))
    completed = [
        SubTask(index=0, goal="登录系统", completion_condition="URL 包含 /home"),
        SubTask(index=1, goal="导航账户页", completion_condition="标题包含 账户"),
    ]
    failed = SubTask(index=2, goal="下载流水", completion_condition="下载触发")

    plan = await planner.replan(
        original_goal="下载 Q1 银行流水",
        completed_subtasks=completed,
        failed_subtask=failed,
        failure_reason="下载按钮未找到",
    )

    assert plan.is_replan is True
    assert plan.replan_reason == "下载按钮未找到"
    assert plan.navigation_goal == "下载 Q1 银行流水"
    # version = len(completed) + 2 = 4
    assert plan.version == 4
    assert len(plan.subtasks) == 2

    # index 从已完成数量（2）开始递增，保持全局有序
    assert [s.index for s in plan.subtasks] == [2, 3]

    assert plan.subtasks[0].goal == "切换登录方式"
    assert plan.subtasks[0].failure_strategy == FailureStrategy.REPLAN
    assert plan.subtasks[1].goal == "重新下载流水"
    assert plan.subtasks[1].failure_strategy == FailureStrategy.ABORT


@pytest.mark.asyncio
async def test_llm_replan_exception_fallback():
    """LLM replan 异常时应回退到单步继续计划。"""
    planner = PlannerAgent(
        llm_callable=_make_failing_llm(ConnectionError("网络中断")),
    )
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
    assert plan.version == len(completed) + 2  # = 3
    assert len(plan.subtasks) == 1
    # fallback 子任务 index = len(completed) = 1
    assert plan.subtasks[0].index == 1
    assert plan.subtasks[0].failure_strategy == FailureStrategy.ABORT


@pytest.mark.asyncio
async def test_llm_replan_invalid_json_fallback():
    """LLM replan 返回非法 JSON 时应回退到单步继续计划。"""
    planner = PlannerAgent(llm_callable=_make_llm("invalid json"))
    completed: list[SubTask] = []
    failed = SubTask(index=0, goal="失败步骤", completion_condition="完成")

    plan = await planner.replan(
        original_goal="目标",
        completed_subtasks=completed,
        failed_subtask=failed,
        failure_reason="失败",
    )

    assert plan.is_replan is True
    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].failure_strategy == FailureStrategy.ABORT
    # 无已完成时 fallback index = 0
    assert plan.subtasks[0].index == 0


@pytest.mark.asyncio
async def test_llm_replan_empty_steps_fallback():
    """LLM replan 返回空 steps 时应回退到单步继续计划。"""
    empty_json = json.dumps({"steps": []})
    planner = PlannerAgent(llm_callable=_make_llm(empty_json))
    completed = [SubTask(index=0, goal="步骤1", completion_condition="完成")]
    failed = SubTask(index=1, goal="步骤2", completion_condition="完成")

    plan = await planner.replan(
        original_goal="目标",
        completed_subtasks=completed,
        failed_subtask=failed,
        failure_reason="失败",
    )

    assert plan.is_replan is True
    assert len(plan.subtasks) == 1
    # fallback 子任务 index = len(completed) = 1
    assert plan.subtasks[0].index == 1

# endregion

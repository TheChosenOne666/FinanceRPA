"""ResilientCaller 单元测试（M5.1 三层容错）。

覆盖：
- 层 1：Prompt 注入 JSON Schema 约束
- 层 2：正常返回 / JSON 格式错误重试 / Pydantic 校验失败重试 / 代码块包裹兼容
- 层 3：重试耗尽转 NeedsHumanError + 上报 Java NEEDS_HUMAN 事件
- Java 调用记录上报：成功/失败/Java 端点不可用
- 集成测试：PlannerAgent + ResilientCaller 端到端
"""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from pydantic import BaseModel, Field

from app.llm.resilient_caller import LlmCallRecord, NeedsHumanError, ResilientCaller


# region 测试用 Pydantic 模型

class SampleOutput(BaseModel):
    """测试用输出模型。"""
    name: str = Field(description="名称")
    count: int = Field(description="数量")


# endregion


# region 辅助函数

def _make_llm(*responses: str):
    """构造 mock llm_callable，按顺序返回多个响应（模拟重试场景）。"""
    iterator = iter(responses)

    async def _callable(prompt: str) -> str:
        try:
            return next(iterator)
        except StopIteration:
            return responses[-1]  # 超出范围时返回最后一个响应

    return _callable


def _make_failing_llm(error: Exception):
    """构造始终抛异常的 mock llm_callable。"""
    async def _callable(prompt: str) -> str:
        raise error

    return _callable


def _make_mock_java_client():
    """构造 mock JavaBackendClient。"""
    client = AsyncMock()
    client.report_llm_call = AsyncMock(return_value=True)
    client.update_task_state = AsyncMock(return_value=True)
    return client


_VALID_JSON = json.dumps({"name": "测试", "count": 42})
_INVALID_JSON = "这不是JSON"
_WRONG_SCHEMA_JSON = json.dumps({"name": "测试"})  # 缺少 count
_CODE_BLOCK_JSON = '```json\n{"name": "代码块", "count": 7}\n```'

# endregion


# region 层 1：Prompt 约束测试

def test_enhance_prompt_injects_json_schema():
    """层 1：_enhance_prompt 应注入 JSON Schema + 格式约束。"""
    llm = _make_llm(_VALID_JSON)
    caller = ResilientCaller(llm)

    enhanced = caller._enhance_prompt("分解任务", SampleOutput)

    assert "分解任务" in enhanced
    assert "JSON" in enhanced
    assert "schema" in enhanced.lower()
    assert "properties" in enhanced  # JSON Schema 内容


def test_enhance_prompt_includes_model_fields():
    """层 1：增强 prompt 应包含模型字段定义。"""
    llm = _make_llm(_VALID_JSON)
    caller = ResilientCaller(llm)

    enhanced = caller._enhance_prompt("test", SampleOutput)

    assert "name" in enhanced
    assert "count" in enhanced


# endregion


# region 层 2：正常调用 + 重试测试

@pytest.mark.asyncio
async def test_call_success_first_attempt():
    """层 2：首次调用即返回合法 JSON → 直接成功，不重试。"""
    llm = _make_llm(_VALID_JSON)
    caller = ResilientCaller(llm, max_retries=2)

    result = await caller.call("test prompt", SampleOutput, context_name="test")

    assert isinstance(result, SampleOutput)
    assert result.name == "测试"
    assert result.count == 42


@pytest.mark.asyncio
async def test_call_success_with_code_block():
    """层 2：LLM 返回 ```json 代码块包裹 → 清理后解析成功。"""
    llm = _make_llm(_CODE_BLOCK_JSON)
    caller = ResilientCaller(llm, max_retries=0)

    result = await caller.call("test", SampleOutput)

    assert result.name == "代码块"
    assert result.count == 7


@pytest.mark.asyncio
async def test_call_retry_on_invalid_json():
    """层 2：首次返回非法 JSON → 重试 → 第二次返回合法 JSON → 成功。"""
    llm = _make_llm(_INVALID_JSON, _VALID_JSON)
    caller = ResilientCaller(llm, max_retries=2)

    result = await caller.call("test", SampleOutput, context_name="retry_test")

    assert result.name == "测试"
    assert result.count == 42


@pytest.mark.asyncio
async def test_call_retry_on_validation_error():
    """层 2：首次返回缺字段 JSON → Pydantic 校验失败 → 重试 → 成功。"""
    llm = _make_llm(_WRONG_SCHEMA_JSON, _VALID_JSON)
    caller = ResilientCaller(llm, max_retries=2)

    result = await caller.call("test", SampleOutput)

    assert result.name == "测试"
    assert result.count == 42


@pytest.mark.asyncio
async def test_call_retry_adds_error_feedback():
    """层 2：重试时应在 prompt 中追加错误反馈信息。"""
    call_count = 0
    prompts_received = []

    async def _track_llm(prompt: str) -> str:
        nonlocal call_count
        prompts_received.append(prompt)
        call_count += 1
        if call_count == 1:
            return _INVALID_JSON
        return _VALID_JSON

    caller = ResilientCaller(_track_llm, max_retries=2)
    await caller.call("test", SampleOutput)

    # 第一次 prompt 不含错误反馈
    assert "PREVIOUS ATTEMPT FAILED" not in prompts_received[0]
    # 第二次 prompt 含错误反馈
    assert "PREVIOUS ATTEMPT FAILED" in prompts_received[1]
    assert "Validation error" in prompts_received[1] or "validation error" in prompts_received[1].lower()


# endregion


# region 层 3：NEEDS_HUMAN 兜底测试

@pytest.mark.asyncio
async def test_call_needs_human_on_all_retries_exhausted():
    """层 3：所有重试均返回非法 JSON → 抛出 NeedsHumanError。"""
    llm = _make_llm(_INVALID_JSON, _INVALID_JSON, _INVALID_JSON)
    caller = ResilientCaller(llm, max_retries=2)

    with pytest.raises(NeedsHumanError) as exc_info:
        await caller.call("test", SampleOutput, task_id="task_001", context_name="test")

    assert exc_info.value.attempts == 3  # 首次 + 2 次重试
    assert exc_info.value.last_raw == _INVALID_JSON
    assert exc_info.value.last_error is not None


@pytest.mark.asyncio
async def test_call_needs_human_on_validation_exhausted():
    """层 3：所有重试均返回缺字段 JSON → Pydantic 校验失败 → NeedsHumanError。"""
    llm = _make_llm(_WRONG_SCHEMA_JSON, _WRONG_SCHEMA_JSON, _WRONG_SCHEMA_JSON)
    caller = ResilientCaller(llm, max_retries=2)

    with pytest.raises(NeedsHumanError):
        await caller.call("test", SampleOutput)


@pytest.mark.asyncio
async def test_call_needs_human_reports_to_java():
    """层 3：重试耗尽时应上报 Java NEEDS_HUMAN 事件。"""
    llm = _make_llm(_INVALID_JSON, _INVALID_JSON)
    mock_java = _make_mock_java_client()
    caller = ResilientCaller(llm, max_retries=1, java_client=mock_java)

    with pytest.raises(NeedsHumanError):
        await caller.call("test", SampleOutput, task_id="task_002", context_name="test")

    # 验证上报了 NEEDS_HUMAN 状态
    mock_java.update_task_state.assert_called()
    call_args = mock_java.update_task_state.call_args
    assert call_args.kwargs["state"] == "NEEDS_HUMAN"
    assert call_args.kwargs["task_id"] == "task_002"


@pytest.mark.asyncio
async def test_call_network_error_propagates():
    """层 2/3：LLM 网络异常（非 JSON/校验错误）不重试，直接传播。"""
    llm = _make_failing_llm(ConnectionError("网络断开"))
    caller = ResilientCaller(llm, max_retries=2)

    with pytest.raises(ConnectionError):
        await caller.call("test", SampleOutput)


# endregion


# region Java 调用记录上报测试

@pytest.mark.asyncio
async def test_report_call_on_success():
    """Java 上报：成功调用应上报 success=True 的记录。"""
    llm = _make_llm(_VALID_JSON)
    mock_java = _make_mock_java_client()
    caller = ResilientCaller(llm, max_retries=2, java_client=mock_java)

    await caller.call("test", SampleOutput, task_id="task_003", context_name="planner")

    mock_java.report_llm_call.assert_called_once()
    record = mock_java.report_llm_call.call_args.args[0]
    assert record["success"] is True
    assert record["retry_attempt"] == 0
    assert record["task_id"] == "task_003"
    assert record["context_name"] == "planner"


@pytest.mark.asyncio
async def test_report_call_on_each_retry():
    """Java 上报：每次重试都应上报一条记录（含失败记录）。"""
    llm = _make_llm(_INVALID_JSON, _VALID_JSON)
    mock_java = _make_mock_java_client()
    caller = ResilientCaller(llm, max_retries=2, java_client=mock_java)

    await caller.call("test", SampleOutput, task_id="task_004")

    # 第一次失败 + 第二次成功 = 2 条记录
    assert mock_java.report_llm_call.call_count == 2
    first_record = mock_java.report_llm_call.call_args_list[0].args[0]
    second_record = mock_java.report_llm_call.call_args_list[1].args[0]
    assert first_record["success"] is False
    assert first_record["retry_attempt"] == 0
    assert second_record["success"] is True
    assert second_record["retry_attempt"] == 1


@pytest.mark.asyncio
async def test_java_client_failure_does_not_block():
    """Java 上报：Java 端点不可用时不阻断主流程。"""
    llm = _make_llm(_VALID_JSON)
    mock_java = _make_mock_java_client()
    mock_java.report_llm_call = AsyncMock(side_effect=Exception("Java 不可用"))
    caller = ResilientCaller(llm, max_retries=0, java_client=mock_java)

    # 即使 Java 上报失败，调用仍应成功
    result = await caller.call("test", SampleOutput)
    assert result.name == "测试"


@pytest.mark.asyncio
async def test_no_java_client_skips_reporting():
    """Java 上报：无 java_client 时跳过上报，不影响调用。"""
    llm = _make_llm(_VALID_JSON)
    caller = ResilientCaller(llm, max_retries=0, java_client=None)

    result = await caller.call("test", SampleOutput)
    assert result.count == 42


# endregion


# region LlmCallRecord 模型测试

def test_llm_call_record_defaults():
    """LlmCallRecord 默认值应正确。"""
    record = LlmCallRecord()
    assert record.model == "gpt-4o-mini"
    assert record.context_name == "unknown"
    assert record.success is False
    assert record.cache_hit is False
    assert record.retry_attempt == 0


# endregion


# region 集成测试：PlannerAgent + ResilientCaller

@pytest.mark.asyncio
async def test_planner_with_resilient_caller_success():
    """集成测试：PlannerAgent 注入 ResilientCaller → LLM 拆解成功。"""
    from app.agent.planner import PlannerAgent, PlannerOutput

    valid_steps = json.dumps({
        "steps": [
            {"goal": "登录系统", "completion_condition": "URL 包含 /home", "failure_strategy": "abort", "max_retries": 3},
            {"goal": "下载流水", "completion_condition": "文件下载", "failure_strategy": "retry", "max_retries": 2},
        ]
    }, ensure_ascii=False)

    llm = _make_llm(valid_steps)
    caller = ResilientCaller(llm, max_retries=2)
    planner = PlannerAgent(resilient_caller=caller)

    plan = await planner.create_plan("下载银行流水", task_id="task_005")

    assert plan.navigation_goal == "下载银行流水"
    assert len(plan.subtasks) == 2
    assert plan.subtasks[0].goal == "登录系统"
    assert plan.subtasks[1].goal == "下载流水"


@pytest.mark.asyncio
async def test_planner_with_resilient_caller_needs_human():
    """集成测试：PlannerAgent + ResilientCaller 重试耗尽 → NeedsHumanError 传播。"""
    from app.agent.planner import PlannerAgent

    llm = _make_llm(_INVALID_JSON, _INVALID_JSON, _INVALID_JSON)
    caller = ResilientCaller(llm, max_retries=2)
    planner = PlannerAgent(resilient_caller=caller)

    with pytest.raises(NeedsHumanError):
        await planner.create_plan("下载银行流水", task_id="task_006")


@pytest.mark.asyncio
async def test_planner_resilient_caller_retry_then_success():
    """集成测试：PlannerAgent + ResilientCaller 首次失败 → 重试成功。"""
    from app.agent.planner import PlannerAgent

    invalid_steps = "这不是JSON"
    valid_steps = json.dumps({
        "steps": [
            {"goal": "登录", "completion_condition": "成功", "failure_strategy": "retry", "max_retries": 2},
        ]
    }, ensure_ascii=False)

    llm = _make_llm(invalid_steps, valid_steps)
    caller = ResilientCaller(llm, max_retries=2)
    planner = PlannerAgent(resilient_caller=caller)

    plan = await planner.create_plan("任务", task_id="task_007")

    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "登录"


@pytest.mark.asyncio
async def test_planner_legacy_mode_still_works():
    """回归测试：PlannerAgent 使用 legacy 模式（llm_callable）仍正常工作。"""
    from app.agent.planner import PlannerAgent

    valid_steps = json.dumps({
        "steps": [
            {"goal": "步骤1", "completion_condition": "完成", "failure_strategy": "abort", "max_retries": 2},
        ]
    }, ensure_ascii=False)

    llm = _make_llm(valid_steps)
    planner = PlannerAgent(llm_callable=llm)  # legacy 模式

    plan = await planner.create_plan("任务")

    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "步骤1"


@pytest.mark.asyncio
async def test_planner_fallback_mode_still_works():
    """回归测试：PlannerAgent 无 LLM 时 fallback 模式仍正常工作。"""
    from app.agent.planner import PlannerAgent

    planner = PlannerAgent()  # 无 llm_callable / resilient_caller

    plan = await planner.create_plan("下载流水")

    assert len(plan.subtasks) == 1
    assert plan.subtasks[0].goal == "下载流水"


# endregion


# region 集成测试：Coordinator + NeedsHumanError

@pytest.mark.asyncio
async def test_coordinator_catches_needs_human_on_plan():
    """集成测试：Coordinator 捕获 Planner 的 NeedsHumanError → 转 needs_human 状态。"""
    from app.agent.coordinator import AgentCoordinator
    from app.agent.planner import PlannerAgent

    llm = _make_llm(_INVALID_JSON, _INVALID_JSON, _INVALID_JSON)
    caller = ResilientCaller(llm, max_retries=2)
    planner = PlannerAgent(resilient_caller=caller)

    # mock executor（不会被调用到）
    executor = AsyncMock()

    coordinator = AgentCoordinator(planner=planner, executor=executor)
    state = await coordinator.run(
        task_id="task_008",
        org_id="org_001",
        navigation_goal="测试目标",
    )

    assert state.status == "needs_human"
    assert "LLM 规划重试耗尽" in (state.error_message or "")


# endregion

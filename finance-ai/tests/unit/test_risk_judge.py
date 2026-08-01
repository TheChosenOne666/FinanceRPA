"""RiskJudgeService 单元测试（M6.2 LLM 风险二次判断）。

覆盖：
- 正常 LLM 判断成功
- NeedsHumanError 回退（重试耗尽）
- 其他异常回退（网络错误等）
- LLM 输出非法风险等级回退
- LLM 输出非法审批路由修正
- prompt 构造（goal + params + 关键词 + 金额）
- Pydantic 模型序列化/反序列化

@author FinanceRPA
"""

import json
from unittest.mock import AsyncMock

import pytest

from app.approval.risk_judge import RiskJudgeService
from app.approval.schemas import (
    AmountMatchItem,
    HitKeywordItem,
    RiskJudgeOutput,
    RiskJudgeRequest,
    RiskJudgeResponse,
)
from app.llm.resilient_caller import NeedsHumanError


# region 辅助函数

def _make_request(
    goal: str = "执行转账操作",
    industry: str = "banking",
    pre_screen_risk_level: str = "high",
    hit_keywords: list[HitKeywordItem] | None = None,
    amount_matches: list[AmountMatchItem] | None = None,
    max_amount: float | None = None,
    task_id: str = "700000000000000001",
    org_id: str = "100",
    params: dict | None = None,
) -> RiskJudgeRequest:
    """构造测试用 RiskJudgeRequest。"""
    if hit_keywords is None:
        hit_keywords = [
            HitKeywordItem(
                keyword="转账",
                industry="banking",
                category="high_risk_operation",
                risk_type="high",
                description="资金转出操作",
            ),
        ]
    if amount_matches is None:
        amount_matches = []
    if params is None:
        params = {"url": "https://bank.example.com/transfer"}

    return RiskJudgeRequest(
        task_id=task_id,
        org_id=org_id,
        goal=goal,
        params=params,
        industry=industry,
        pre_screen_risk_level=pre_screen_risk_level,
        hit_keywords=hit_keywords,
        amount_matches=amount_matches,
        max_amount=max_amount,
    )


def _make_mock_caller(response: RiskJudgeOutput | None = None, exception: Exception | None = None) -> AsyncMock:
    """构造 mock ResilientCaller。

    @param response: call() 返回的 RiskJudgeOutput（与 exception 二选一）
    @param exception: call() 抛出的异常
    @return: AsyncMock 实例
    """
    caller = AsyncMock()
    if exception:
        caller.call = AsyncMock(side_effect=exception)
    else:
        caller.call = AsyncMock(return_value=response or RiskJudgeOutput(
            final_risk_level="high",
            reasoning="任务涉及转账操作",
            approval_route="department",
        ))
    return caller


# endregion


# region 正常 LLM 判断成功

@pytest.mark.asyncio
async def test_judge_success_returns_llm_result():
    """正常 LLM 判断成功：返回 LLM 输出的风险等级。"""
    request = _make_request(pre_screen_risk_level="high")
    llm_output = RiskJudgeOutput(
        final_risk_level="critical",
        reasoning="任务同时命中转账操作和银行卡号，风险极高",
        approval_route="compliance",
    )
    caller = _make_mock_caller(response=llm_output)
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.task_id == "700000000000000001"
    assert response.final_risk_level == "critical"
    assert response.approval_route == "compliance"
    assert "风险极高" in response.reasoning
    caller.call.assert_awaited_once()


@pytest.mark.asyncio
async def test_judge_success_low_risk():
    """LLM 判断为低风险：返回 auto 审批路由。"""
    request = _make_request(
        goal="查询账户余额",
        pre_screen_risk_level="medium",
        hit_keywords=[],
    )
    llm_output = RiskJudgeOutput(
        final_risk_level="low",
        reasoning="余额查询为只读操作，无资金流动风险",
        approval_route="auto",
    )
    caller = _make_mock_caller(response=llm_output)
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.final_risk_level == "low"
    assert response.approval_route == "auto"


# endregion


# region NeedsHumanError 回退

@pytest.mark.asyncio
async def test_judge_needs_human_error_falls_back_to_prescreen():
    """NeedsHumanError（重试耗尽）：回退使用预筛风险等级。"""
    request = _make_request(pre_screen_risk_level="high")
    caller = _make_mock_caller(exception=NeedsHumanError(
        message="LLM 调用重试 2 次后仍失败",
        last_raw="invalid json",
        last_error="JSONDecodeError",
        attempts=3,
    ))
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.final_risk_level == "high"  # 回退使用预筛结果
    assert response.approval_route == "department"  # high → department
    assert "回退" in response.reasoning or "预筛" in response.reasoning
    assert "LLM" in response.message


@pytest.mark.asyncio
async def test_judge_needs_human_error_critical_prescreen():
    """NeedsHumanError + 预筛 critical：回退为 compliance 路由。"""
    request = _make_request(pre_screen_risk_level="critical")
    caller = _make_mock_caller(exception=NeedsHumanError("重试耗尽"))
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.final_risk_level == "critical"
    assert response.approval_route == "compliance"


# endregion


# region 其他异常回退

@pytest.mark.asyncio
async def test_judge_network_error_falls_back_to_prescreen():
    """网络异常：回退使用预筛风险等级。"""
    request = _make_request(pre_screen_risk_level="medium")
    caller = _make_mock_caller(exception=ConnectionError("网络不可用"))
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.final_risk_level == "medium"  # 回退使用预筛结果
    assert response.approval_route == "auto"  # medium → auto
    assert "异常" in response.message


@pytest.mark.asyncio
async def test_judge_generic_exception_falls_back():
    """通用异常：回退使用预筛风险等级。"""
    request = _make_request(pre_screen_risk_level="low")
    caller = _make_mock_caller(exception=RuntimeError("意外错误"))
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.final_risk_level == "low"
    assert response.approval_route == "auto"


# endregion


# region LLM 输出非法值校验

@pytest.mark.asyncio
async def test_judge_invalid_risk_level_falls_back_to_prescreen():
    """LLM 输出非法风险等级：回退使用预筛结果。"""
    request = _make_request(pre_screen_risk_level="high")
    llm_output = RiskJudgeOutput(
        final_risk_level="extreme",  # 非法值
        reasoning="测试非法值",
        approval_route="department",
    )
    caller = _make_mock_caller(response=llm_output)
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.final_risk_level == "high"  # 回退到预筛值
    assert response.approval_route == "department"


@pytest.mark.asyncio
async def test_judge_invalid_approval_route_corrected():
    """LLM 输出非法审批路由：根据风险等级自动修正。"""
    request = _make_request(pre_screen_risk_level="high")
    llm_output = RiskJudgeOutput(
        final_risk_level="high",
        reasoning="高风险操作",
        approval_route="unknown",  # 非法值
    )
    caller = _make_mock_caller(response=llm_output)
    service = RiskJudgeService(resilient_caller=caller)

    response = await service.judge(request)

    assert response.final_risk_level == "high"
    assert response.approval_route == "department"  # high → department 自动修正


# endregion


# region prompt 构造

def test_build_prompt_contains_goal():
    """prompt 应包含任务目标。"""
    request = _make_request(goal="下载银行流水")
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "下载银行流水" in prompt
    assert "Task Goal" in prompt


def test_build_prompt_contains_industry():
    """prompt 应包含行业信息。"""
    request = _make_request(industry="securities")
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "securities" in prompt


def test_build_prompt_contains_pre_screen_risk_level():
    """prompt 应包含预筛风险等级。"""
    request = _make_request(pre_screen_risk_level="critical")
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "critical" in prompt
    assert "Pre-Screen Risk Level" in prompt


def test_build_prompt_contains_hit_keywords():
    """prompt 应包含命中关键词。"""
    request = _make_request(hit_keywords=[
        HitKeywordItem(keyword="转账", industry="banking", category="high_risk_operation", risk_type="high"),
        HitKeywordItem(keyword="银行卡号", industry="banking", category="sensitive_data", risk_type="high"),
    ])
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "转账" in prompt
    assert "银行卡号" in prompt
    assert "high_risk_operation" in prompt
    assert "sensitive_data" in prompt


def test_build_prompt_contains_amounts():
    """prompt 应包含金额匹配。"""
    request = _make_request(
        amount_matches=[
            AmountMatchItem(raw_text="￥50,000.00", amount=50000.0, currency="CNY"),
        ],
        max_amount=50000.0,
    )
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "50,000.00" in prompt
    assert "CNY" in prompt
    assert "最大金额" in prompt


def test_build_prompt_contains_params():
    """prompt 应包含任务参数。"""
    request = _make_request(params={"account": "6222000110020034567", "amount": "50000"})
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "6222000110020034567" in prompt
    assert "50000" in prompt


def test_build_prompt_no_keywords():
    """无命中关键词时 prompt 显示"无命中关键词"。"""
    request = _make_request(hit_keywords=[])
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "无命中关键词" in prompt


def test_build_prompt_no_amounts():
    """无金额匹配时 prompt 显示"未检测到金额"。"""
    request = _make_request(amount_matches=[], max_amount=None)
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    prompt = service._build_prompt(request)

    assert "未检测到金额" in prompt


# endregion


# region 格式化辅助方法

def test_format_hit_keywords_with_dict_input():
    """_format_hit_keywords 支持 dict 输入（Java 传过来的格式）。"""
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    keywords = [
        {"keyword": "转账", "category": "high_risk_operation", "riskType": "high", "description": "资金转出"},
        {"keyword": "密码", "category": "sensitive_data", "risk_type": "high", "description": ""},
    ]
    text = service._format_hit_keywords(keywords)

    assert "转账" in text
    assert "密码" in text
    assert "high_risk_operation" in text
    assert "sensitive_data" in text


def test_format_amounts_with_dict_input():
    """_format_amounts 支持 dict 输入。"""
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    amounts = [
        {"rawText": "￥50,000", "amount": 50000.0, "currency": "CNY"},
        {"raw_text": "$1,000", "amount": 7200.0, "currency": "USD"},
    ]
    text = service._format_amounts(amounts, max_amount=50000.0)

    assert "50,000.00" in text
    assert "7,200.00" in text
    assert "CNY" in text
    assert "USD" in text
    assert "最大金额" in text


# endregion


# region 回退响应构造

def test_fallback_response_low_risk():
    """回退响应：low 风险 → auto 路由。"""
    request = _make_request(pre_screen_risk_level="low")
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    response = service._fallback_response(request, "测试回退")

    assert response.final_risk_level == "low"
    assert response.approval_route == "auto"


def test_fallback_response_high_risk():
    """回退响应：high 风险 → department 路由。"""
    request = _make_request(pre_screen_risk_level="high")
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    response = service._fallback_response(request, "测试回退")

    assert response.final_risk_level == "high"
    assert response.approval_route == "department"


def test_fallback_response_critical_risk():
    """回退响应：critical 风险 → compliance 路由。"""
    request = _make_request(pre_screen_risk_level="critical")
    caller = _make_mock_caller()
    service = RiskJudgeService(resilient_caller=caller)

    response = service._fallback_response(request, "测试回退")

    assert response.final_risk_level == "critical"
    assert response.approval_route == "compliance"


# endregion


# region Pydantic 模型序列化

def test_risk_judge_request_camel_case_deserialization():
    """RiskJudgeRequest 支持驼峰字段反序列化（Java 侧传过来的格式）。"""
    java_json = json.dumps({
        "taskId": "700000000000000001",
        "orgId": "100",
        "goal": "执行转账",
        "params": {"url": "https://bank.example.com"},
        "industry": "banking",
        "preScreenRiskLevel": "high",
        "hitKeywords": [
            {"keyword": "转账", "industry": "banking", "category": "high_risk_operation", "riskType": "high"},
        ],
        "amountMatches": [
            {"rawText": "￥50,000", "amount": 50000.0, "currency": "CNY"},
        ],
        "maxAmount": 50000.0,
    })

    request = RiskJudgeRequest.model_validate_json(java_json)

    assert request.task_id == "700000000000000001"
    assert request.org_id == "100"
    assert request.pre_screen_risk_level == "high"
    assert len(request.hit_keywords) == 1
    assert request.hit_keywords[0].keyword == "转账"
    assert len(request.amount_matches) == 1
    assert request.amount_matches[0].amount == 50000.0
    assert request.max_amount == 50000.0


def test_risk_judge_response_camel_case_serialization():
    """RiskJudgeResponse 序列化为驼峰字段（Java 侧解析的格式）。"""
    response = RiskJudgeResponse(
        task_id="700000000000000001",
        final_risk_level="high",
        reasoning="涉及大额转账",
        approval_route="department",
        message="判断完成",
    )

    json_str = response.model_dump_json(by_alias=True)
    data = json.loads(json_str)

    assert data["taskId"] == "700000000000000001"
    assert data["finalRiskLevel"] == "high"
    assert data["approvalRoute"] == "department"
    assert data["reasoning"] == "涉及大额转账"


def test_risk_judge_output_json_schema():
    """RiskJudgeOutput 的 JSON Schema 应包含三个必填字段。"""
    schema = RiskJudgeOutput.model_json_schema()

    assert "final_risk_level" in schema["properties"]
    assert "reasoning" in schema["properties"]
    assert "approval_route" in schema["properties"]
    assert "final_risk_level" in schema.get("required", [])
    assert "reasoning" in schema.get("required", [])
    assert "approval_route" in schema.get("required", [])


# endregion

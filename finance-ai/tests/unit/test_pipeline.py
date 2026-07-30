"""Pipeline 执行器集成测试（M3.2）。

覆盖 M3.2 新增功能：
1. 参数映射：param_mapping 引用 workflow_params
2. 上下文传递：前一个 Skill 输出作为后一个 Skill 输入
3. get_failure_strategy() 动态失败策略
4. 审计回调增强
5. 向后兼容：直接 params 方式仍正常工作
"""

import pytest
from pydantic import BaseModel

from app.skills.base import (
    BaseSkill,
    ErrorStrategy,
    SkillResult,
    SkillStatus,
    register_skill,
)
from app.skills.executor import (
    SkillStep,
    execute_pipeline,
)

# ---------------------------------------------------------------------------
# 测试用 Skill
# ---------------------------------------------------------------------------

class EchoParams(BaseModel):
    """回显参数。"""
    value: str


@register_skill
class EchoSkill(BaseSkill):
    """回显 Skill：将参数值放入 data 返回。"""
    skill_name = "pipeline_echo"
    description = "回显测试"
    params_model = EchoParams
    error_strategy = ErrorStrategy.ABORT
    max_retries = 0

    async def execute(self, params, context=None):
        return SkillResult(
            status=SkillStatus.COMPLETED,
            data={"echoed": params.value},
            duration_ms=10,
        )


class CollectParams(BaseModel):
    """收集参数。"""
    source: str = ""
    format: str = "json"


@register_skill
class CollectSkill(BaseSkill):
    """收集 Skill：从上下文引用前序步骤数据。"""
    skill_name = "pipeline_collect"
    description = "收集测试"
    params_model = CollectParams
    error_strategy = ErrorStrategy.ABORT
    max_retries = 0

    async def execute(self, params, context=None):
        return SkillResult(
            status=SkillStatus.COMPLETED,
            data={"source": params.source, "format": params.format},
            duration_ms=5,
        )


class FailWithStrategyParams(BaseModel):
    """动态策略失败参数。"""
    error_msg: str


@register_skill
class DynamicStrategySkill(BaseSkill):
    """动态策略 Skill：覆写 get_failure_strategy()。"""
    skill_name = "pipeline_dynamic_strategy"
    description = "动态策略测试"
    params_model = FailWithStrategyParams
    error_strategy = ErrorStrategy.ABORT  # 默认 ABORT
    max_retries = 0

    async def execute(self, params, context=None):
        return SkillResult(
            status=SkillStatus.FAILED,
            error_message=params.error_msg,
            duration_ms=1,
        )

    def get_failure_strategy(self, error=None):
        """覆写：timeout 错误 → SKIP，其他 → ABORT。"""
        if error and "timeout" in error.lower():
            return ErrorStrategy.SKIP
        return ErrorStrategy.ABORT


class RetryableParams(BaseModel):
    """可重试参数。"""
    fail_times: int = 0


# 用类变量记录执行次数
@register_skill
class RetryableSkill(BaseSkill):
    """可重试 Skill：前 N 次失败，第 N+1 次成功。"""
    skill_name = "pipeline_retryable"
    description = "重试测试"
    params_model = RetryableParams
    error_strategy = ErrorStrategy.RETRY
    max_retries = 3

    _call_count: int = 0  # 类级计数器

    async def execute(self, params, context=None):
        type(self)._call_count += 1
        if type(self)._call_count <= params.fail_times:
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=f"模拟失败第 {type(self)._call_count} 次",
                duration_ms=1,
            )
        return SkillResult(
            status=SkillStatus.COMPLETED,
            data={"attempts": type(self)._call_count},
            duration_ms=2,
        )


# ---------------------------------------------------------------------------
# 参数映射测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pipeline_param_mapping_reference():
    """param_mapping 引用模式应从 workflow_params 取值。"""
    # 重置计数器
    RetryableSkill._call_count = 0

    workflow_params = {"url": "https://example.com", "name": "test"}
    steps = [
        SkillStep(
            skill_name="pipeline_echo",
            params={},
            param_mapping={"value": "name"},
        ),
    ]

    result = await execute_pipeline(steps, workflow_params=workflow_params)

    assert result.success is True
    assert result.step_results[0]["data"]["echoed"] == "test"


@pytest.mark.asyncio
async def test_pipeline_param_mapping_literal():
    """param_mapping 字面量模式应解析为字面量值。"""
    steps = [
        SkillStep(
            skill_name="pipeline_collect",
            params={},
            param_mapping={"format": "=csv"},
        ),
    ]

    result = await execute_pipeline(steps)

    assert result.success is True
    assert result.step_results[0]["data"]["format"] == "csv"


@pytest.mark.asyncio
async def test_pipeline_param_mapping_mixed():
    """param_mapping 混合引用和字面量。"""
    workflow_params = {"source_url": "https://data.example.com"}
    steps = [
        SkillStep(
            skill_name="pipeline_collect",
            params={},
            param_mapping={
                "source": "source_url",
                "format": "=json",
            },
        ),
    ]

    result = await execute_pipeline(steps, workflow_params=workflow_params)

    assert result.success is True
    assert result.step_results[0]["data"]["source"] == "https://data.example.com"
    assert result.step_results[0]["data"]["format"] == "json"


@pytest.mark.asyncio
async def test_pipeline_param_mapping_merges_with_params():
    """param_mapping 应覆盖 params 中的同名键。"""
    workflow_params = {"name": "from_mapping"}
    steps = [
        SkillStep(
            skill_name="pipeline_echo",
            params={"value": "from_params"},
            param_mapping={"value": "name"},
        ),
    ]

    result = await execute_pipeline(steps, workflow_params=workflow_params)

    assert result.success is True
    # param_mapping 覆盖 params
    assert result.step_results[0]["data"]["echoed"] == "from_mapping"


# ---------------------------------------------------------------------------
# 上下文传递测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pipeline_context_passing():
    """前一个 Skill 的输出应通过 context["step_results"] 传递给后续步骤。"""
    workflow_params = {"name": "hello"}
    steps = [
        SkillStep(
            skill_name="pipeline_echo",
            params={},
            param_mapping={"value": "name"},
        ),
        SkillStep(
            skill_name="pipeline_collect",
            params={},
            param_mapping={
                "source": "{{steps.0.data.echoed}}",
                "format": "=json",
            },
        ),
    ]

    result = await execute_pipeline(steps, workflow_params=workflow_params)

    assert result.success is True
    assert result.steps_completed == 2
    # 第二步引用了第一步的输出
    assert result.step_results[1]["data"]["source"] == "hello"


@pytest.mark.asyncio
async def test_pipeline_context_step_results_populated():
    """执行后 context["step_results"] 应包含所有步骤结果。"""
    steps = [
        SkillStep(skill_name="pipeline_echo", params={"value": "a"}),
        SkillStep(skill_name="pipeline_echo", params={"value": "b"}),
    ]

    context = {}
    result = await execute_pipeline(steps, context=context)

    assert result.success is True
    assert len(context["step_results"]) == 2
    assert context["step_results"][0]["data"]["echoed"] == "a"
    assert context["step_results"][1]["data"]["echoed"] == "b"


@pytest.mark.asyncio
async def test_pipeline_context_none_initialization():
    """context 为 None 时应自动初始化 step_results。"""
    steps = [SkillStep(skill_name="pipeline_echo", params={"value": "test"})]

    result = await execute_pipeline(steps, context=None)

    assert result.success is True
    assert len(result.step_results) == 1


# ---------------------------------------------------------------------------
# get_failure_strategy() 动态策略测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pipeline_dynamic_strategy_skip():
    """动态策略：timeout 错误 → SKIP，管线应继续执行。"""
    steps = [
        SkillStep(
            skill_name="pipeline_dynamic_strategy",
            params={"error_msg": "Connection timeout"},
        ),
        SkillStep(skill_name="pipeline_echo", params={"value": "after_skip"}),
    ]

    result = await execute_pipeline(steps)

    # SKIP 策略：管线不终止
    assert result.success is True
    assert result.steps_completed == 1  # 只有第二步完成
    assert len(result.step_results) == 2


@pytest.mark.asyncio
async def test_pipeline_dynamic_strategy_abort():
    """动态策略：非 timeout 错误 → ABORT，管线应终止。"""
    steps = [
        SkillStep(
            skill_name="pipeline_dynamic_strategy",
            params={"error_msg": "Network error"},
        ),
        SkillStep(skill_name="pipeline_echo", params={"value": "should_not_run"}),
    ]

    result = await execute_pipeline(steps)

    # ABORT 策略：管线终止
    assert result.success is False
    assert result.aborted_at_step == 0
    assert len(result.step_results) == 1  # 第二步不执行


@pytest.mark.asyncio
async def test_pipeline_error_strategy_override():
    """error_strategy_override 应覆盖 Skill 的 get_failure_strategy()。"""
    # DynamicStrategySkill 对 timeout 错误返回 SKIP
    # 但 error_strategy_override 强制设为 ABORT
    steps = [
        SkillStep(
            skill_name="pipeline_dynamic_strategy",
            params={"error_msg": "Connection timeout"},
            error_strategy_override="abort",
        ),
        SkillStep(skill_name="pipeline_echo", params={"value": "should_not_run"}),
    ]

    result = await execute_pipeline(steps)

    # override 强制 ABORT
    assert result.success is False
    assert result.aborted_at_step == 0


# ---------------------------------------------------------------------------
# 重试策略测试（验证 get_failure_strategy 在重试场景的行为）
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pipeline_retry_then_success():
    """RETRY 策略：前 2 次失败，第 3 次成功。"""
    RetryableSkill._call_count = 0
    steps = [
        SkillStep(
            skill_name="pipeline_retryable",
            params={"fail_times": 2},
        ),
    ]

    result = await execute_pipeline(steps)

    assert result.success is True
    assert result.steps_completed == 1
    assert result.step_results[0]["data"]["attempts"] == 3


@pytest.mark.asyncio
async def test_pipeline_retry_exhausted():
    """RETRY 策略：重试耗尽后终止管线。"""
    RetryableSkill._call_count = 0
    steps = [
        SkillStep(
            skill_name="pipeline_retryable",
            params={"fail_times": 10},  # 超过 max_retries
        ),
        SkillStep(skill_name="pipeline_echo", params={"value": "should_not_run"}),
    ]

    result = await execute_pipeline(steps)

    assert result.success is False
    assert result.aborted_at_step == 0
    assert "重试耗尽" in (result.error_message or "")


# ---------------------------------------------------------------------------
# 审计回调测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pipeline_audit_callback_with_context():
    """审计回调应在每步执行后被调用。"""
    audit_calls = []

    async def audit_cb(step_idx, skill_name, audit_dict, skill_result):
        audit_calls.append({
            "step": step_idx,
            "skill": skill_name,
            "status": skill_result.status.value,
            "has_data": skill_result.data is not None,
        })

    steps = [
        SkillStep(skill_name="pipeline_echo", params={"value": "a"}),
        SkillStep(skill_name="pipeline_echo", params={"value": "b"}),
    ]

    result = await execute_pipeline(steps, audit_callback=audit_cb)

    assert result.success is True
    assert len(audit_calls) == 2
    assert audit_calls[0]["skill"] == "pipeline_echo"
    assert audit_calls[0]["status"] == "completed"
    assert audit_calls[0]["has_data"] is True
    assert audit_calls[1]["step"] == 1


@pytest.mark.asyncio
async def test_pipeline_audit_callback_on_failure():
    """失败的步骤也应触发审计回调。"""
    audit_calls = []

    async def audit_cb(step_idx, skill_name, audit_dict, skill_result):
        audit_calls.append({
            "step": step_idx,
            "status": skill_result.status.value,
            "error": skill_result.error_message,
        })

    steps = [
        SkillStep(
            skill_name="pipeline_dynamic_strategy",
            params={"error_msg": "Network error"},
        ),
    ]

    result = await execute_pipeline(steps, audit_callback=audit_cb)

    assert result.success is False
    assert len(audit_calls) == 1
    assert audit_calls[0]["status"] == "failed"
    assert audit_calls[0]["error"] == "Network error"


# ---------------------------------------------------------------------------
# 向后兼容测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pipeline_backward_compatible_direct_params():
    """不使用 param_mapping 时，直接 params 方式仍正常工作。"""
    steps = [
        SkillStep(skill_name="pipeline_echo", params={"value": "direct"}),
    ]

    result = await execute_pipeline(steps)

    assert result.success is True
    assert result.step_results[0]["data"]["echoed"] == "direct"


@pytest.mark.asyncio
async def test_pipeline_backward_compatible_no_workflow_params():
    """不传 workflow_params 时，管线应正常执行（param_mapping 为 None 的场景）。"""
    steps = [
        SkillStep(skill_name="pipeline_echo", params={"value": "test"}),
        SkillStep(skill_name="pipeline_collect", params={"source": "direct", "format": "csv"}),
    ]

    result = await execute_pipeline(steps)

    assert result.success is True
    assert result.steps_completed == 2

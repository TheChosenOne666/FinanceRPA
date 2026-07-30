"""Skills 系统单元测试。"""

import pytest
from pydantic import BaseModel

from app.skills.base import (
    SKILL_REGISTRY,
    BaseSkill,
    ErrorStrategy,
    SkillResult,
    SkillStatus,
    get_skill,
    list_skills,
    register_skill,
)
from app.skills.executor import (
    SkillStep,
    execute_pipeline,
)

# ---------------------------------------------------------------------------
# 测试用 Skill
# ---------------------------------------------------------------------------

class DummyParams(BaseModel):
    """测试参数。"""
    value: str


@register_skill
class DummySkill(BaseSkill):
    """测试用 Skill。"""
    skill_name = "dummy_skill"
    description = "测试 Skill"
    params_model = DummyParams
    error_strategy = ErrorStrategy.RETRY
    max_retries = 2

    async def execute(self, params, context=None):
        return SkillResult(
            status=SkillStatus.COMPLETED,
            data={"echo": params.value},
            duration_ms=10,
        )


class FailParams(BaseModel):
    """失败测试参数。"""
    msg: str


@register_skill
class FailSkill(BaseSkill):
    """总是失败的测试 Skill。"""
    skill_name = "fail_skill"
    description = "总是失败"
    params_model = FailParams
    error_strategy = ErrorStrategy.ABORT
    max_retries = 1

    async def execute(self, params, context=None):
        return SkillResult(
            status=SkillStatus.FAILED,
            error_message=params.msg,
            duration_ms=5,
        )


# ---------------------------------------------------------------------------
# 注册表测试
# ---------------------------------------------------------------------------

def test_skill_registry():
    """Skill 应正确注册到全局注册表。"""
    assert "dummy_skill" in SKILL_REGISTRY
    assert "fail_skill" in SKILL_REGISTRY

    cls = get_skill("dummy_skill")
    assert cls is not None
    assert cls.skill_name == "dummy_skill"

    skills = list_skills()
    names = [s["name"] for s in skills]
    assert "dummy_skill" in names
    assert "fail_skill" in names


def test_validate_params():
    """参数校验应正常工作。"""
    skill = DummySkill()
    params = skill.validate_params({"value": "hello"})
    assert params.value == "hello"


def test_audit_dict_masking():
    """审计字典应脱敏敏感字段。"""
    class SensitiveParams(BaseModel):
        password: str
        token: str
        name: str

    class SensitiveSkill(BaseSkill):
        skill_name = "sensitive_skill"
        description = "脱敏测试"
        params_model = SensitiveParams
        error_strategy = ErrorStrategy.ABORT

        async def execute(self, params, context=None):
            return SkillResult()

    skill = SensitiveSkill()
    params = SensitiveParams(password="secret123", token="tok_abc", name="张三")
    audit = skill.to_audit_dict(params)

    assert audit["params"]["password"] != "secret123"
    assert "*" in audit["params"]["password"]
    assert audit["params"]["token"] != "tok_abc"
    assert "*" in audit["params"]["token"]
    assert audit["params"]["name"] == "张三"  # 非敏感字段不脱敏


# ---------------------------------------------------------------------------
# Pipeline 测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pipeline_success():
    """成功的 pipeline 应完成所有步骤。"""
    steps = [
        SkillStep(skill_name="dummy_skill", params={"value": "step1"}),
        SkillStep(skill_name="dummy_skill", params={"value": "step2"}),
    ]

    result = await execute_pipeline(steps)

    assert result.success is True
    assert result.steps_completed == 2
    assert result.steps_total == 2
    assert result.aborted_at_step is None
    assert len(result.step_results) == 2


@pytest.mark.asyncio
async def test_pipeline_unknown_skill():
    """未知 Skill 应导致 pipeline 失败。"""
    steps = [SkillStep(skill_name="nonexistent", params={})]

    result = await execute_pipeline(steps)

    assert result.success is False
    assert result.aborted_at_step == 0
    assert "nonexistent" in (result.error_message or "")


@pytest.mark.asyncio
async def test_pipeline_abort_on_failure():
    """ABORT 策略失败时应终止 pipeline。"""
    steps = [
        SkillStep(skill_name="dummy_skill", params={"value": "ok"}),
        SkillStep(skill_name="fail_skill", params={"msg": "出错了"}),
        SkillStep(skill_name="dummy_skill", params={"value": "不会执行"}),
    ]

    result = await execute_pipeline(steps)

    assert result.success is False
    assert result.aborted_at_step == 1
    assert result.steps_completed == 1
    assert result.steps_total == 3


@pytest.mark.asyncio
async def test_pipeline_audit_callback():
    """审计回调应被调用。"""
    audit_calls = []

    async def audit_cb(step_idx, skill_name, audit_dict, result):
        audit_calls.append({
            "step": step_idx,
            "skill": skill_name,
            "success": result.status == SkillStatus.COMPLETED,
        })

    steps = [SkillStep(skill_name="dummy_skill", params={"value": "test"})]

    result = await execute_pipeline(steps, audit_callback=audit_cb)

    assert result.success is True
    assert len(audit_calls) == 1
    assert audit_calls[0]["skill"] == "dummy_skill"
    assert audit_calls[0]["success"] is True


# ---------------------------------------------------------------------------
# get_failure_strategy() 默认行为测试（M3.1 决策 1）
# ---------------------------------------------------------------------------

def test_get_failure_strategy_default_returns_classvar():
    """BaseSkill.get_failure_strategy 默认应返回 error_strategy ClassVar。"""
    # DummySkill.error_strategy = RETRY
    skill = DummySkill()
    assert skill.get_failure_strategy() == ErrorStrategy.RETRY
    # FailSkill.error_strategy = ABORT
    fail_skill = FailSkill()
    assert fail_skill.get_failure_strategy() == ErrorStrategy.ABORT


def test_get_failure_strategy_with_error_arg():
    """传 error 参数时，默认仍返回 ClassVar（无动态逻辑）。"""
    skill = DummySkill()
    assert skill.get_failure_strategy("any error") == ErrorStrategy.RETRY
    assert skill.get_failure_strategy(None) == ErrorStrategy.RETRY


def test_get_failure_strategy_override():
    """子类覆写 get_failure_strategy 时应使用覆写逻辑。"""
    class CustomSkill(BaseSkill):
        skill_name = "custom_strategy_skill"
        description = "覆写策略测试"
        params_model = DummyParams
        error_strategy = ErrorStrategy.RETRY  # 默认 RETRY

        async def execute(self, params, context=None):
            return SkillResult()

        def get_failure_strategy(self, error=None):
            # 覆写：含 timeout 字样 → SKIP，否则 ABORT
            if error and "timeout" in error.lower():
                return ErrorStrategy.SKIP
            return ErrorStrategy.ABORT

    skill = CustomSkill()
    # 默认（无 error）应走覆写逻辑返回 ABORT，而不是 ClassVar RETRY
    assert skill.get_failure_strategy() == ErrorStrategy.ABORT
    # timeout 错误 → SKIP
    assert skill.get_failure_strategy("Connection timeout") == ErrorStrategy.SKIP
    # 其他错误 → ABORT
    assert skill.get_failure_strategy("Network error") == ErrorStrategy.ABORT


# ---------------------------------------------------------------------------
# 7 个 Skill 注册完整性测试（M3.1）
# ---------------------------------------------------------------------------

def test_seven_skills_registered():
    """导入 app.skills 包后应自动注册 7 个 Skill。"""
    # 触发 app.skills 包导入（如未导入）
    import app.skills  # noqa: F401

    expected_skills = {
        "login",
        "session_keep_alive",
        "form_fill",
        "search_and_select",
        "pagination",
        "table_extract",
        "file_download",
    }
    registered = set(SKILL_REGISTRY.keys())
    # 7 个 Skill 都应在注册表中（注册表还包含测试 DummySkill/FailSkill）
    assert expected_skills.issubset(registered), (
        f"缺失 Skill: {expected_skills - registered}"
    )

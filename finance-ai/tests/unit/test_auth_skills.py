"""auth_skills 单元测试。

覆盖 LoginSkill 与 SessionKeepAliveSkill 的成功/失败/边界场景。
所有测试用 AsyncMock 模拟 Playwright Page，不依赖真实浏览器。

@author FinanceRPA
"""

from unittest.mock import AsyncMock

import pytest

from app.skills.auth_skills import (
    LoginParams,
    LoginSkill,
    SessionKeepAliveParams,
    SessionKeepAliveSkill,
)
from app.skills.base import ErrorStrategy, SkillStatus

# ---------------------------------------------------------------------------
# LoginSkill 测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_login_skill_success():
    """LoginSkill Fallback 模式登录成功。"""
    page = AsyncMock()
    # success_indicator 为空，跳过 URL 校验
    params = LoginParams(
        url="https://example.com/login",
        username="admin",
        password="secret123",
    )
    skill = LoginSkill()
    result = await skill.execute(params, {"page": page})

    # 验证导航与填充调用
    page.goto.assert_awaited_once_with(
        "https://example.com/login", wait_until="domcontentloaded"
    )
    page.fill.assert_any_call("input[type='text'], input[name*='user']", "admin")
    page.fill.assert_any_call("input[type='password']", "secret123")
    page.click.assert_awaited_once()  # 点击 submit

    assert result.status == SkillStatus.COMPLETED
    assert result.data == {"logged_in": True, "url": "https://example.com/login"}


@pytest.mark.asyncio
async def test_login_skill_no_page():
    """context 无 page 时应立即 FAILED。"""
    params = LoginParams(url="https://x.com", username="u", password="p")
    skill = LoginSkill()
    result = await skill.execute(params, {})

    assert result.status == SkillStatus.FAILED
    assert "No browser page" in (result.error_message or "")


@pytest.mark.asyncio
async def test_login_skill_manual_captcha():
    """captcha_strategy=manual 应返回 PENDING 等待人工。"""
    page = AsyncMock()
    params = LoginParams(
        url="https://example.com/login",
        username="admin",
        password="secret",
        captcha_strategy="manual",
    )
    skill = LoginSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.PENDING
    assert result.data == {"needs_captcha": True, "strategy": "manual"}


@pytest.mark.asyncio
async def test_login_skill_success_indicator_url_match():
    """success_indicator 通过 URL 匹配校验登录成功。"""
    page = AsyncMock()
    # wait_for_url 不抛异常即视为成功
    params = LoginParams(
        url="https://example.com/login",
        username="u",
        password="p",
        success_indicator="dashboard",
    )
    skill = LoginSkill()
    result = await skill.execute(params, {"page": page})

    page.wait_for_url.assert_awaited_once_with("**dashboard**", timeout=10000)
    assert result.status == SkillStatus.COMPLETED


@pytest.mark.asyncio
async def test_login_skill_success_indicator_content_fallback():
    """URL 匹配失败时，回退到页面内容校验。"""
    page = AsyncMock()
    # URL 等待超时
    page.wait_for_url.side_effect = Exception("timeout")
    # 页面内容包含成功标志
    page.content.return_value = "<html>Welcome dashboard home</html>"

    params = LoginParams(
        url="https://example.com/login",
        username="u",
        password="p",
        success_indicator="dashboard",
    )
    skill = LoginSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED


@pytest.mark.asyncio
async def test_login_skill_success_indicator_not_found():
    """success_indicator 在 URL 与内容中均未找到 → FAILED。"""
    page = AsyncMock()
    page.wait_for_url.side_effect = Exception("timeout")
    page.content.return_value = "<html>still on login page</html>"

    params = LoginParams(
        url="https://example.com/login",
        username="u",
        password="p",
        success_indicator="dashboard",
    )
    skill = LoginSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.FAILED
    assert "dashboard" in (result.error_message or "")


@pytest.mark.asyncio
async def test_login_skill_llm_handler_mode():
    """配置 llm_handler 时应调用 LLM 而非 fill/click。"""
    page = AsyncMock()
    llm_handler = AsyncMock()
    params = LoginParams(
        url="https://example.com/login",
        username="admin",
        password="secret",
    )
    skill = LoginSkill()
    result = await skill.execute(params, {"page": page, "llm_handler": llm_handler})

    # LLM 模式：不应调用 page.fill
    page.fill.assert_not_awaited()
    # LLM 应被调用
    llm_handler.assert_awaited_once()
    assert result.status == SkillStatus.COMPLETED


@pytest.mark.asyncio
async def test_login_skill_navigation_exception():
    """page.goto 抛异常 → FAILED 并捕获异常。"""
    page = AsyncMock()
    page.goto.side_effect = Exception("network error")

    params = LoginParams(url="https://x.com", username="u", password="p")
    skill = LoginSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.FAILED
    assert "network error" in (result.error_message or "")


def test_login_skill_class_vars():
    """LoginSkill 的 ClassVar 应符合决策 1 规格。"""
    assert LoginSkill.skill_name == "login"
    assert LoginSkill.error_strategy == ErrorStrategy.ABORT
    assert LoginSkill.max_retries == 3


def test_login_skill_get_failure_strategy_default():
    """默认 get_failure_strategy 应返回 ClassVar ABORT。"""
    skill = LoginSkill()
    assert skill.get_failure_strategy() == ErrorStrategy.ABORT


def test_login_skill_get_failure_strategy_captcha_error():
    """错误信息含 captcha 时仍返回 ABORT（保持向后兼容）。"""
    skill = LoginSkill()
    assert skill.get_failure_strategy("captcha required") == ErrorStrategy.ABORT


# ---------------------------------------------------------------------------
# SessionKeepAliveSkill 测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_session_keep_alive_active_no_heartbeat():
    """无 heartbeat_url 且无 timeout_indicator → 会话活跃。"""
    page = AsyncMock()
    params = SessionKeepAliveParams()
    skill = SessionKeepAliveSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data == {"session_active": True}


@pytest.mark.asyncio
async def test_session_keep_alive_heartbeat_ok():
    """心跳 URL 返回 200 → 会话活跃。"""
    page = AsyncMock()
    page.evaluate.return_value = 200
    params = SessionKeepAliveParams(heartbeat_url="https://x.com/heartbeat")
    skill = SessionKeepAliveSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data == {"session_active": True}


@pytest.mark.asyncio
async def test_session_keep_alive_heartbeat_failed_with_relogin():
    """心跳失败且配置 relogin → 调用 LoginSkill 重新登录。"""
    page = AsyncMock()
    page.evaluate.return_value = 401
    login_params = LoginParams(
        url="https://x.com/login",
        username="u",
        password="p",
    )
    params = SessionKeepAliveParams(
        heartbeat_url="https://x.com/heartbeat",
        relogin_on_expire=True,
        login_params=login_params,
    )
    skill = SessionKeepAliveSkill()
    result = await skill.execute(params, {"page": page})

    # LoginSkill 应被调用：page.goto 至少被调用一次
    page.goto.assert_awaited_once_with(
        "https://x.com/login", wait_until="domcontentloaded"
    )
    assert result.status == SkillStatus.COMPLETED


@pytest.mark.asyncio
async def test_session_keep_alive_timeout_indicator_no_relogin():
    """检测到 timeout 标志但未配置 relogin → FAILED。"""
    page = AsyncMock()
    page.content.return_value = "<html>Session expired, please login again</html>"
    params = SessionKeepAliveParams(
        session_timeout_indicator="session expired",
        relogin_on_expire=False,
    )
    skill = SessionKeepAliveSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.FAILED
    assert "Session expired" in (result.error_message or "")


@pytest.mark.asyncio
async def test_session_keep_alive_timeout_indicator_with_relogin():
    """检测到 timeout 标志且配置 relogin → 调用 LoginSkill。"""
    page = AsyncMock()
    page.content.return_value = "<html>Session expired</html>"
    login_params = LoginParams(
        url="https://x.com/login",
        username="u",
        password="p",
    )
    params = SessionKeepAliveParams(
        session_timeout_indicator="session expired",
        relogin_on_expire=True,
        login_params=login_params,
    )
    skill = SessionKeepAliveSkill()
    result = await skill.execute(params, {"page": page})

    # LoginSkill 应被调用
    page.goto.assert_awaited_once()
    assert result.status == SkillStatus.COMPLETED


@pytest.mark.asyncio
async def test_session_keep_alive_no_page():
    """context 无 page 时应立即 FAILED。"""
    params = SessionKeepAliveParams()
    skill = SessionKeepAliveSkill()
    result = await skill.execute(params, {})

    assert result.status == SkillStatus.FAILED
    assert "No browser page" in (result.error_message or "")


def test_session_keep_alive_class_vars():
    """SessionKeepAliveSkill 的 ClassVar 应符合规格。"""
    assert SessionKeepAliveSkill.skill_name == "session_keep_alive"
    assert SessionKeepAliveSkill.error_strategy == ErrorStrategy.RETRY
    assert SessionKeepAliveSkill.max_retries == 2

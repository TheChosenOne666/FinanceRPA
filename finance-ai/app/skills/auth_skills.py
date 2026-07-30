"""认证类 Skill：LoginSkill 与 SessionKeepAliveSkill。

处理金融系统通用的登录流程与会话保活，覆盖银行、保险、证券门户等场景。

@from enterprise/skills/auth_skills.py
@author FinanceRPA
"""

import logging
import time
from typing import Any, ClassVar

from pydantic import BaseModel, Field

from .base import (
    BaseSkill,
    ErrorStrategy,
    SkillResult,
    SkillStatus,
    register_skill,
)

logger = logging.getLogger(__name__)


# ------------------------------------------------------------------
# LoginSkill 登录技能
# ------------------------------------------------------------------

class LoginParams(BaseModel):
    """LoginSkill 的输入参数。"""

    url: str = Field(description="登录页 URL")
    username: str = Field(description="登录用户名")
    password: str = Field(description="登录密码")
    captcha_strategy: str = Field(
        default="skip",
        description="验证码处理策略：skip | manual | ocr",
    )
    submit_selector: str | None = Field(
        default=None,
        description="登录按钮 CSS 选择器（省略时自动检测）",
    )
    success_indicator: str = Field(
        default="",
        description="登录成功标志：URL 片段或页面文本",
    )


@register_skill
class LoginSkill(BaseSkill):
    """通用登录技能。

    流程：导航到登录页 → 填账号密码 → 处理验证码 → 校验登录成功。
    支持 LLM 视觉决策（context["llm_handler"]）与 Fallback 选择器两种模式。
    """

    skill_name: ClassVar[str] = "login"
    description: ClassVar[str] = "通用登录流程，含验证码处理"
    params_model: ClassVar[type[BaseModel]] = LoginParams
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.ABORT
    max_retries: ClassVar[int] = 3

    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行登录流程。"""
        start = time.monotonic()
        p: LoginParams = params  # type: ignore[assignment]
        ctx = context or {}

        try:
            # 1. 取浏览器 Page，缺失立即失败
            page = ctx.get("page")
            if page is None:
                return SkillResult(
                    status=SkillStatus.FAILED,
                    error_message="No browser page in context",
                    duration_ms=int((time.monotonic() - start) * 1000),
                )

            # 2. 导航到登录页
            await page.goto(p.url, wait_until="domcontentloaded")
            logger.info("LoginSkill: navigated to %s", p.url)

            # 3. 填充账号密码（优先 LLM 模式，否则 Fallback 选择器）
            navigation_goal = (
                f"Fill username '{p.username}' and password into login form fields, "
                f"then click submit"
            )
            llm_handler = ctx.get("llm_handler")
            if llm_handler:
                # LLM 模式：视觉决策填充并提交
                await llm_handler(page, navigation_goal)
            else:
                # Fallback 模式：通用选择器填充
                await page.fill("input[type='text'], input[name*='user']", p.username)
                await page.fill("input[type='password']", p.password)
                # 点击提交按钮
                if p.submit_selector:
                    await page.click(p.submit_selector)
                else:
                    await page.click("button[type='submit'], input[type='submit']")

            # 4. 处理验证码（manual 策略需要人工介入）
            if p.captcha_strategy == "manual":
                return SkillResult(
                    status=SkillStatus.PENDING,
                    data={"needs_captcha": True, "strategy": "manual"},
                    duration_ms=int((time.monotonic() - start) * 1000),
                )

            # 5. 校验登录成功（先尝试 URL 匹配，失败再检查页面内容）
            if p.success_indicator:
                try:
                    await page.wait_for_url(f"**{p.success_indicator}**", timeout=10000)
                except Exception:
                    content = await page.content()
                    if p.success_indicator not in content:
                        return SkillResult(
                            status=SkillStatus.FAILED,
                            error_message=(
                                f"Login success indicator "
                                f"'{p.success_indicator}' not found"
                            ),
                            duration_ms=int((time.monotonic() - start) * 1000),
                        )

            elapsed = int((time.monotonic() - start) * 1000)
            logger.info("LoginSkill: login succeeded in %dms", elapsed)
            return SkillResult(
                status=SkillStatus.COMPLETED,
                data={"logged_in": True, "url": p.url},
                duration_ms=elapsed,
            )

        except Exception as e:
            elapsed = int((time.monotonic() - start) * 1000)
            logger.error("LoginSkill failed: %s", e)
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=str(e),
                duration_ms=elapsed,
            )

    def get_failure_strategy(self, error: str | None = None) -> ErrorStrategy:
        """根据错误信息动态决策失败策略。

        - 验证码相关错误 → 返回 PENDING 等待人工（暂返回 ABORT 保持向后兼容）
        - 其他错误 → 返回默认 ABORT
        """
        if error and "captcha" in error.lower():
            # 验证码错误：登录页通常需要人工介入，仍走 ABORT 让上层决策
            return ErrorStrategy.ABORT
        return self.error_strategy


# ------------------------------------------------------------------
# SessionKeepAliveSkill 会话保活技能
# ------------------------------------------------------------------

class SessionKeepAliveParams(BaseModel):
    """SessionKeepAliveSkill 的输入参数。"""

    check_interval_seconds: int = Field(
        default=300,
        description="保活检查间隔秒数",
    )
    heartbeat_url: str | None = Field(
        default=None,
        description="心跳 URL（如有）",
    )
    session_timeout_indicator: str = Field(
        default="",
        description="会话超时页面文本（如 'session expired'）",
    )
    relogin_on_expire: bool = Field(
        default=True,
        description="会话过期时是否自动重新登录",
    )
    login_params: LoginParams | None = Field(
        default=None,
        description="自动重新登录所需的 LoginParams",
    )


@register_skill
class SessionKeepAliveSkill(BaseSkill):
    """会话保活技能。

    周期性 ping 心跳 URL 或检查页面会话超时标志，必要时自动重新登录。
    """

    skill_name: ClassVar[str] = "session_keep_alive"
    description: ClassVar[str] = "会话监控，超时自动重登"
    params_model: ClassVar[type[BaseModel]] = SessionKeepAliveParams
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.RETRY
    max_retries: ClassVar[int] = 2

    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行会话保活检查。"""
        start = time.monotonic()
        p: SessionKeepAliveParams = params  # type: ignore[assignment]
        ctx = context or {}

        try:
            # 1. 取浏览器 Page
            page = ctx.get("page")
            if page is None:
                return SkillResult(
                    status=SkillStatus.FAILED,
                    error_message="No browser page in context",
                    duration_ms=int((time.monotonic() - start) * 1000),
                )

            # 2. 心跳 URL 检查
            if p.heartbeat_url:
                response = await page.evaluate(
                    f"fetch('{p.heartbeat_url}').then(r => r.status)"
                )
                if response != 200:
                    logger.warning("Session heartbeat failed: status=%s", response)
                    # 心跳失败且配置了自动重登：调用 LoginSkill
                    if p.relogin_on_expire and p.login_params:
                        login_skill = LoginSkill()
                        result = await login_skill.execute(p.login_params, context)
                        return result

            # 3. 页面内容会话超时检查
            if p.session_timeout_indicator:
                content = await page.content()
                if p.session_timeout_indicator.lower() in content.lower():
                    logger.warning("Session expired (indicator found in page)")
                    # 会话过期且配置了自动重登：调用 LoginSkill
                    if p.relogin_on_expire and p.login_params:
                        login_skill = LoginSkill()
                        result = await login_skill.execute(p.login_params, context)
                        return result
                    # 未配置自动重登：返回失败
                    return SkillResult(
                        status=SkillStatus.FAILED,
                        error_message="Session expired and no re-login configured",
                        duration_ms=int((time.monotonic() - start) * 1000),
                    )

            # 4. 会话活跃
            elapsed = int((time.monotonic() - start) * 1000)
            return SkillResult(
                status=SkillStatus.COMPLETED,
                data={"session_active": True},
                duration_ms=elapsed,
            )

        except Exception as e:
            elapsed = int((time.monotonic() - start) * 1000)
            logger.error("SessionKeepAliveSkill failed: %s", e)
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=str(e),
                duration_ms=elapsed,
            )

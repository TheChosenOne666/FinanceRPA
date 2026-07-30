"""交互类 Skill：FormFillSkill、SearchAndSelectSkill、PaginationSkill。

处理金融系统常见的 UI 交互模式：表单填充、搜索选择、分页遍历。

@from enterprise/skills/interaction_skills.py
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
# FormFillSkill 表单填充技能
# ------------------------------------------------------------------

class FormFillParams(BaseModel):
    """FormFillSkill 的输入参数。"""

    field_mapping: dict[str, str] = Field(
        description="字段标签/名称 → 填充值 的映射",
    )
    submit_after_fill: bool = Field(
        default=True,
        description="填充完成后是否点击提交按钮",
    )
    submit_selector: str | None = Field(
        default=None,
        description="提交按钮 CSS 选择器（省略时自动检测）",
    )
    date_format: str = Field(
        default="YYYY-MM-DD",
        description="日期选择器字段格式",
    )


@register_skill
class FormFillSkill(BaseSkill):
    """智能表单填充技能。

    支持下拉框、日期选择器等金融系统常见的非标准表单控件。
    优先用 LLM 视觉决策，缺失时用通用选择器 Fallback。
    """

    skill_name: ClassVar[str] = "form_fill"
    description: ClassVar[str] = "智能表单填充，支持下拉框与日期选择器"
    params_model: ClassVar[type[BaseModel]] = FormFillParams
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.RETRY
    max_retries: ClassVar[int] = 2

    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行表单填充。"""
        start = time.monotonic()
        p: FormFillParams = params  # type: ignore[assignment]
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

            filled_fields: list[str] = []
            failed_fields: list[str] = []
            llm_handler = ctx.get("llm_handler")

            # 2. 遍历字段映射，逐个填充
            for field_label, value in p.field_mapping.items():
                try:
                    if llm_handler:
                        # LLM 模式：视觉决策填充单字段
                        goal = f"Fill the form field labeled '{field_label}' with value '{value}'"
                        await llm_handler(page, goal)
                    else:
                        # Fallback 模式：依次尝试常见选择器
                        selectors = [
                            f"input[name='{field_label}']",
                            f"input[placeholder*='{field_label}']",
                            f"textarea[name='{field_label}']",
                            f"select[name='{field_label}']",
                        ]
                        filled = False
                        for sel in selectors:
                            try:
                                element = await page.query_selector(sel)
                                if element:
                                    # select 元素用 select_option，其他用 fill
                                    tag = await element.evaluate("el => el.tagName.toLowerCase()")
                                    if tag == "select":
                                        await page.select_option(sel, value)
                                    else:
                                        await page.fill(sel, value)
                                    filled = True
                                    break
                            except Exception:
                                # 选择器不匹配，继续尝试下一个
                                continue
                        if not filled:
                            failed_fields.append(field_label)
                            continue

                    filled_fields.append(field_label)
                except Exception as e:
                    logger.warning("FormFill: failed to fill '%s': %s", field_label, e)
                    failed_fields.append(field_label)

            # 3. 全部字段填充成功且配置了 submit → 点击提交
            if p.submit_after_fill and not failed_fields:
                try:
                    if p.submit_selector:
                        await page.click(p.submit_selector)
                    elif llm_handler:
                        await llm_handler(page, "Click the submit or confirm button")
                    else:
                        await page.click("button[type='submit'], input[type='submit']")
                except Exception as e:
                    logger.warning("FormFill: submit click failed: %s", e)

            # 4. 有失败字段 → FAILED，否则 COMPLETED
            elapsed = int((time.monotonic() - start) * 1000)
            status = SkillStatus.COMPLETED if not failed_fields else SkillStatus.FAILED
            return SkillResult(
                status=status,
                data={
                    "filled_fields": filled_fields,
                    "failed_fields": failed_fields,
                    "total": len(p.field_mapping),
                },
                error_message=f"Failed to fill: {failed_fields}" if failed_fields else None,
                duration_ms=elapsed,
            )

        except Exception as e:
            elapsed = int((time.monotonic() - start) * 1000)
            logger.error("FormFillSkill failed: %s", e)
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=str(e),
                duration_ms=elapsed,
            )


# ------------------------------------------------------------------
# SearchAndSelectSkill 搜索选择技能
# ------------------------------------------------------------------

class SearchAndSelectParams(BaseModel):
    """SearchAndSelectParams 的输入参数。"""

    search_text: str = Field(description="搜索框输入的文本")
    target_text: str = Field(
        description="要点击的目标结果项文本",
    )
    search_selector: str | None = Field(
        default=None,
        description="搜索输入框 CSS 选择器（省略时自动检测）",
    )
    result_container_selector: str | None = Field(
        default=None,
        description="结果容器 CSS 选择器",
    )
    wait_for_results_ms: int = Field(
        default=3000,
        description="等待搜索结果出现的毫秒数",
    )


@register_skill
class SearchAndSelectSkill(BaseSkill):
    """搜索并选择结果项技能。

    金融系统常见的客户查找、产品搜索、账户选择等场景。
    """

    skill_name: ClassVar[str] = "search_and_select"
    description: ClassVar[str] = "搜索并从结果列表中选择项"
    params_model: ClassVar[type[BaseModel]] = SearchAndSelectParams
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.RETRY
    max_retries: ClassVar[int] = 2

    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行搜索并选择目标。"""
        start = time.monotonic()
        p: SearchAndSelectParams = params  # type: ignore[assignment]
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

            llm_handler = ctx.get("llm_handler")

            # 2. 输入搜索文本并触发搜索
            if llm_handler:
                # LLM 模式：视觉决策输入并搜索
                await llm_handler(
                    page,
                    f"Find the search box, type '{p.search_text}', and trigger search",
                )
            elif p.search_selector:
                # 指定搜索框选择器
                await page.fill(p.search_selector, p.search_text)
                await page.keyboard.press("Enter")
            else:
                # Fallback：通用搜索框选择器
                await page.fill("input[type='search'], input[type='text']", p.search_text)
                await page.keyboard.press("Enter")

            # 3. 等待搜索结果加载
            await page.wait_for_timeout(p.wait_for_results_ms)

            # 4. 点击目标结果
            if llm_handler:
                await llm_handler(
                    page,
                    f"Click on the search result that contains '{p.target_text}'",
                )
            else:
                target = await page.query_selector(f"text={p.target_text}")
                if target:
                    await target.click()
                else:
                    # 目标未找到 → 失败
                    return SkillResult(
                        status=SkillStatus.FAILED,
                        error_message=f"Target '{p.target_text}' not found in results",
                        duration_ms=int((time.monotonic() - start) * 1000),
                    )

            elapsed = int((time.monotonic() - start) * 1000)
            return SkillResult(
                status=SkillStatus.COMPLETED,
                data={"search_text": p.search_text, "selected": p.target_text},
                duration_ms=elapsed,
            )

        except Exception as e:
            elapsed = int((time.monotonic() - start) * 1000)
            logger.error("SearchAndSelectSkill failed: %s", e)
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=str(e),
                duration_ms=elapsed,
            )


# ------------------------------------------------------------------
# PaginationSkill 分页遍历技能
# ------------------------------------------------------------------

class PaginationParams(BaseModel):
    """PaginationSkill 的输入参数。"""

    max_pages: int = Field(
        default=10,
        description="最大遍历页数",
    )
    next_button_selector: str | None = Field(
        default=None,
        description="「下一页」按钮 CSS 选择器",
    )
    next_button_text: str = Field(
        default="下一页",
        description="下一页按钮文本（未指定 selector 时用）",
    )
    page_data_selector: str | None = Field(
        default=None,
        description="每页数据容器的 CSS 选择器",
    )
    wait_between_pages_ms: int = Field(
        default=2000,
        description="翻页之间的等待毫秒数",
    )
    stop_on_empty: bool = Field(
        default=True,
        description="当前页无数据时是否停止翻页",
    )


@register_skill
class PaginationSkill(BaseSkill):
    """分页遍历技能。

    收集多页数据，支持编号分页、下一页按钮、加载更多等金融系统常见分页样式。
    失败策略为 SKIP（翻页失败跳过，工作流可继续）。
    """

    skill_name: ClassVar[str] = "pagination"
    description: ClassVar[str] = "多页遍历并收集数据"
    params_model: ClassVar[type[BaseModel]] = PaginationParams
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.SKIP
    max_retries: ClassVar[int] = 1

    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行分页遍历。"""
        start = time.monotonic()
        p: PaginationParams = params  # type: ignore[assignment]
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

            pages_traversed = 0
            page_data_collection: list[str] = []
            llm_handler = ctx.get("llm_handler")

            # 2. 循环遍历最多 max_pages 页
            for i in range(p.max_pages):
                pages_traversed += 1

                # 2.1 收集当前页数据
                if p.page_data_selector:
                    elements = await page.query_selector_all(p.page_data_selector)
                    page_text = [await el.inner_text() for el in elements]
                    # 空数据且配置了 stop_on_empty → 停止翻页
                    if p.stop_on_empty and not page_text:
                        logger.info("PaginationSkill: empty page at %d, stopping", i + 1)
                        break
                    page_data_collection.extend(page_text)

                # 2.2 翻到下一页（最后一页不翻）
                if i < p.max_pages - 1:
                    try:
                        if llm_handler:
                            # LLM 模式：视觉决策点击下一页
                            await llm_handler(
                                page,
                                f"Click '{p.next_button_text}' to go to next page",
                            )
                        elif p.next_button_selector:
                            # 指定下一页选择器
                            btn = await page.query_selector(p.next_button_selector)
                            if btn:
                                # 检查按钮是否禁用
                                is_disabled = await btn.evaluate(
                                    "el => el.disabled || el.classList.contains('disabled')"
                                )
                                if is_disabled:
                                    break
                                await btn.click()
                            else:
                                break
                        else:
                            # Fallback：用按钮文本查找
                            btn = await page.query_selector(f"text={p.next_button_text}")
                            if btn:
                                await btn.click()
                            else:
                                break

                        # 等待下一页加载
                        await page.wait_for_timeout(p.wait_between_pages_ms)
                    except Exception as e:
                        # 翻页失败：记录日志并停止
                        logger.info("PaginationSkill: pagination ended at page %d: %s", i + 1, e)
                        break

            elapsed = int((time.monotonic() - start) * 1000)
            return SkillResult(
                status=SkillStatus.COMPLETED,
                data={
                    "pages_traversed": pages_traversed,
                    "items_collected": len(page_data_collection),
                    "data": page_data_collection,
                },
                duration_ms=elapsed,
            )

        except Exception as e:
            elapsed = int((time.monotonic() - start) * 1000)
            logger.error("PaginationSkill failed: %s", e)
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=str(e),
                duration_ms=elapsed,
            )

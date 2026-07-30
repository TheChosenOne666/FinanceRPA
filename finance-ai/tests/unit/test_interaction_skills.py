"""interaction_skills 单元测试。

覆盖 FormFillSkill、SearchAndSelectSkill、PaginationSkill 的成功/失败/边界场景。

@author FinanceRPA
"""

from unittest.mock import AsyncMock

import pytest

from app.skills.base import ErrorStrategy, SkillStatus
from app.skills.interaction_skills import (
    FormFillParams,
    FormFillSkill,
    PaginationParams,
    PaginationSkill,
    SearchAndSelectParams,
    SearchAndSelectSkill,
)

# ---------------------------------------------------------------------------
# 辅助：构造 mock 元素
# ---------------------------------------------------------------------------

def _make_element(tag: str = "input", inner_text: str = "") -> AsyncMock:
    """构造一个 mock 的 Playwright 元素对象。"""
    el = AsyncMock()
    el.evaluate = AsyncMock(return_value=tag.lower())
    el.inner_text = AsyncMock(return_value=inner_text)
    el.click = AsyncMock()
    return el


# ---------------------------------------------------------------------------
# FormFillSkill 测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_form_fill_success_all_fields():
    """所有字段填充成功 + submit_after_fill=True → COMPLETED。"""
    page = AsyncMock()
    # 让前两个 selector 命中 input
    page.query_selector.side_effect = [
        _make_element("input"),  # field1 命中
        _make_element("input"),  # field2 命中
    ]

    params = FormFillParams(
        field_mapping={"username": "admin", "remark": "hello"},
        submit_after_fill=True,
    )
    skill = FormFillSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["filled_fields"] == ["username", "remark"]
    assert result.data["failed_fields"] == []
    # 提交按钮应被点击
    page.click.assert_awaited_once()


@pytest.mark.asyncio
async def test_form_fill_partial_failure():
    """部分字段填充失败 → FAILED，failed_fields 非空。"""
    page = AsyncMock()
    # 第一个字段命中，第二个所有 selector 都返回 None
    page.query_selector.side_effect = [
        _make_element("input"),  # field1 命中
        None, None, None, None,  # field2 的 4 个 selector 都未命中
    ]
    # fill 失败抛异常不应影响后续
    page.fill = AsyncMock()

    params = FormFillParams(
        field_mapping={"username": "admin", "missing": "value"},
        submit_after_fill=True,
    )
    skill = FormFillSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.FAILED
    assert "missing" in result.data["failed_fields"]
    assert "username" in result.data["filled_fields"]
    # 有失败字段 → 不应点击 submit
    page.click.assert_not_awaited()


@pytest.mark.asyncio
async def test_form_fill_select_field():
    """select 元素应调用 select_option 而非 fill。"""
    page = AsyncMock()
    select_el = _make_element("select")
    page.query_selector.return_value = select_el

    params = FormFillParams(
        field_mapping={"country": "CN"},
        submit_after_fill=False,
    )
    skill = FormFillSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    page.select_option.assert_awaited_once()


@pytest.mark.asyncio
async def test_form_fill_no_submit():
    """submit_after_fill=False → 不点击 submit。"""
    page = AsyncMock()
    page.query_selector.return_value = _make_element("input")

    params = FormFillParams(
        field_mapping={"f": "v"},
        submit_after_fill=False,
    )
    skill = FormFillSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    page.click.assert_not_awaited()


@pytest.mark.asyncio
async def test_form_fill_llm_handler_mode():
    """配置 llm_handler → 用 LLM 填充，不调用 query_selector。"""
    page = AsyncMock()
    llm_handler = AsyncMock()

    params = FormFillParams(
        field_mapping={"f1": "v1", "f2": "v2"},
        submit_after_fill=True,
    )
    skill = FormFillSkill()
    result = await skill.execute(params, {"page": page, "llm_handler": llm_handler})

    assert result.status == SkillStatus.COMPLETED
    # LLM 应被调用 2 次（每字段 1 次）+ 1 次 submit
    assert llm_handler.await_count == 3
    page.query_selector.assert_not_awaited()


@pytest.mark.asyncio
async def test_form_fill_no_page():
    """context 无 page → FAILED。"""
    params = FormFillParams(field_mapping={"f": "v"})
    skill = FormFillSkill()
    result = await skill.execute(params, {})

    assert result.status == SkillStatus.FAILED
    assert "No browser page" in (result.error_message or "")


def test_form_fill_class_vars():
    """FormFillSkill 的 ClassVar 应符合规格。"""
    assert FormFillSkill.skill_name == "form_fill"
    assert FormFillSkill.error_strategy == ErrorStrategy.RETRY
    assert FormFillSkill.max_retries == 2


# ---------------------------------------------------------------------------
# SearchAndSelectSkill 测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_search_and_select_success_with_selector():
    """指定 search_selector + 目标结果存在 → COMPLETED。"""
    page = AsyncMock()
    target_el = _make_element("a")
    page.query_selector.return_value = target_el

    params = SearchAndSelectParams(
        search_text="ACCT-001",
        target_text="Account 001",
        search_selector="#search-box",
    )
    skill = SearchAndSelectSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    page.fill.assert_awaited_once_with("#search-box", "ACCT-001")
    page.keyboard.press.assert_awaited_once_with("Enter")
    target_el.click.assert_awaited_once()


@pytest.mark.asyncio
async def test_search_and_select_fallback_selector():
    """未指定 search_selector → 使用通用选择器。"""
    page = AsyncMock()
    target_el = _make_element("a")
    page.query_selector.return_value = target_el

    params = SearchAndSelectParams(
        search_text="ACCT-001",
        target_text="Account 001",
    )
    skill = SearchAndSelectSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    page.fill.assert_awaited_once_with(
        "input[type='search'], input[type='text']", "ACCT-001"
    )


@pytest.mark.asyncio
async def test_search_and_select_target_not_found():
    """目标结果未找到 → FAILED。"""
    page = AsyncMock()
    page.query_selector.return_value = None  # 目标未找到

    params = SearchAndSelectParams(
        search_text="ACCT-001",
        target_text="NonExistent",
    )
    skill = SearchAndSelectSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.FAILED
    assert "NonExistent" in (result.error_message or "")


@pytest.mark.asyncio
async def test_search_and_select_llm_handler_mode():
    """配置 llm_handler → 全程走 LLM 模式。"""
    page = AsyncMock()
    llm_handler = AsyncMock()

    params = SearchAndSelectParams(
        search_text="ACCT-001",
        target_text="Account 001",
    )
    skill = SearchAndSelectSkill()
    result = await skill.execute(params, {"page": page, "llm_handler": llm_handler})

    assert result.status == SkillStatus.COMPLETED
    # LLM 应被调用 2 次：搜索 + 选择
    assert llm_handler.await_count == 2
    page.fill.assert_not_awaited()


@pytest.mark.asyncio
async def test_search_and_select_no_page():
    """context 无 page → FAILED。"""
    params = SearchAndSelectParams(
        search_text="x",
        target_text="y",
    )
    skill = SearchAndSelectSkill()
    result = await skill.execute(params, {})

    assert result.status == SkillStatus.FAILED


def test_search_and_select_class_vars():
    assert SearchAndSelectSkill.skill_name == "search_and_select"
    assert SearchAndSelectSkill.error_strategy == ErrorStrategy.RETRY
    assert SearchAndSelectSkill.max_retries == 2


# ---------------------------------------------------------------------------
# PaginationSkill 测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pagination_single_page():
    """max_pages=1 → 不翻页，仅收集当前页。"""
    page = AsyncMock()
    el1 = _make_element(inner_text="row1")
    el2 = _make_element(inner_text="row2")
    page.query_selector_all.return_value = [el1, el2]

    params = PaginationParams(max_pages=1, page_data_selector=".data-item")
    skill = PaginationSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["pages_traversed"] == 1
    assert result.data["items_collected"] == 2
    assert result.data["data"] == ["row1", "row2"]


@pytest.mark.asyncio
async def test_pagination_multi_page_with_selector():
    """指定 next_button_selector → 翻 3 页。"""
    page = AsyncMock()
    next_btn = _make_element()
    next_btn.evaluate = AsyncMock(return_value=False)  # 非禁用
    # query_selector 用于 next button 查找
    page.query_selector.return_value = next_btn
    # query_selector_all 用于数据收集，每页 2 条
    page.query_selector_all.return_value = [
        _make_element(inner_text="r1"),
        _make_element(inner_text="r2"),
    ]

    params = PaginationParams(
        max_pages=3,
        next_button_selector=".next-btn",
        page_data_selector=".data-item",
        wait_between_pages_ms=0,  # 测试不等待
    )
    skill = PaginationSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["pages_traversed"] == 3
    assert result.data["items_collected"] == 6


@pytest.mark.asyncio
async def test_pagination_empty_page_stops():
    """stop_on_empty=True + 空页 → 停止翻页。"""
    page = AsyncMock()
    page.query_selector_all.return_value = []  # 空页

    params = PaginationParams(
        max_pages=5,
        page_data_selector=".data-item",
        stop_on_empty=True,
    )
    skill = PaginationSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["pages_traversed"] == 1
    assert result.data["items_collected"] == 0


@pytest.mark.asyncio
async def test_pagination_next_button_disabled():
    """下一页按钮禁用 → 停止翻页。"""
    page = AsyncMock()
    next_btn = _make_element()
    next_btn.evaluate = AsyncMock(return_value=True)  # 禁用
    page.query_selector.return_value = next_btn
    page.query_selector_all.return_value = [_make_element(inner_text="r1")]

    params = PaginationParams(
        max_pages=5,
        next_button_selector=".next",
        page_data_selector=".data",
    )
    skill = PaginationSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["pages_traversed"] == 1


@pytest.mark.asyncio
async def test_pagination_next_button_text_fallback():
    """未指定 selector → 用 next_button_text 查找按钮。"""
    page = AsyncMock()
    next_btn = _make_element()
    page.query_selector.return_value = next_btn
    page.query_selector_all.return_value = [_make_element(inner_text="r1")]

    params = PaginationParams(
        max_pages=2,
        next_button_text="下一页",
        page_data_selector=".data",
        wait_between_pages_ms=0,
    )
    skill = PaginationSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["pages_traversed"] == 2
    # 应使用 text= 选择器
    page.query_selector.assert_awaited_with("text=下一页")


@pytest.mark.asyncio
async def test_pagination_no_data_selector():
    """未指定 page_data_selector → 不收集数据，仅翻页。"""
    page = AsyncMock()
    next_btn = AsyncMock()
    # evaluate 返回 False（非禁用），否则会被当作 disabled 跳出
    next_btn.evaluate = AsyncMock(return_value=False)
    page.query_selector.return_value = next_btn

    params = PaginationParams(
        max_pages=2,
        next_button_selector=".next",
        wait_between_pages_ms=0,
    )
    skill = PaginationSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["pages_traversed"] == 2
    assert result.data["items_collected"] == 0


@pytest.mark.asyncio
async def test_pagination_no_page():
    """context 无 page → FAILED。"""
    params = PaginationParams(max_pages=3)
    skill = PaginationSkill()
    result = await skill.execute(params, {})

    assert result.status == SkillStatus.FAILED


def test_pagination_class_vars():
    """PaginationSkill 失败策略应为 SKIP（翻页失败跳过）。"""
    assert PaginationSkill.skill_name == "pagination"
    assert PaginationSkill.error_strategy == ErrorStrategy.SKIP
    assert PaginationSkill.max_retries == 1

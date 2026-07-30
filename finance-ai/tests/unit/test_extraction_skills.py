"""extraction_skills 单元测试。

覆盖 TableExtractSkill 与 FileDownloadSkill 的成功/失败/边界场景。
FileDownloadSkill 重点测试 expect_download async context manager 与 upload_callback 流程。

@author FinanceRPA
"""

import csv
import io
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.skills.base import ErrorStrategy, SkillStatus
from app.skills.extraction_skills import (
    FileDownloadParams,
    FileDownloadSkill,
    TableExtractParams,
    TableExtractSkill,
)

# ---------------------------------------------------------------------------
# TableExtractSkill 测试
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_table_extract_json_output():
    """JSON 输出格式：list[dict]，表头作 key。"""
    page = AsyncMock()
    # query_selector 用于定位 table
    page.query_selector.return_value = AsyncMock()
    # evaluate 返回表头与数据行
    page.evaluate.return_value = {
        "headers": ["账户", "金额"],
        "rows": [["001", "100"], ["002", "200"]],
    }

    params = TableExtractParams(output_format="json")
    skill = TableExtractSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["headers"] == ["账户", "金额"]
    assert result.data["row_count"] == 2
    assert result.data["output"] == [
        {"账户": "001", "金额": "100"},
        {"账户": "002", "金额": "200"},
    ]
    assert result.data["header_match"] is True


@pytest.mark.asyncio
async def test_table_extract_csv_output():
    """CSV 输出格式：字符串。"""
    page = AsyncMock()
    page.query_selector.return_value = AsyncMock()
    page.evaluate.return_value = {
        "headers": ["a", "b"],
        "rows": [["1", "2"], ["3", "4"]],
    }

    params = TableExtractParams(output_format="csv")
    skill = TableExtractSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    csv_output = result.data["output"]
    # 解析 CSV 验证
    reader = csv.reader(io.StringIO(csv_output))
    rows = list(reader)
    assert rows[0] == ["a", "b"]
    assert rows[1] == ["1", "2"]
    assert rows[2] == ["3", "4"]


@pytest.mark.asyncio
async def test_table_extract_no_table_found():
    """表格元素不存在 → FAILED。"""
    page = AsyncMock()
    page.query_selector.return_value = None

    params = TableExtractParams()
    skill = TableExtractSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.FAILED
    assert "No table found" in (result.error_message or "")


@pytest.mark.asyncio
async def test_table_extract_header_match_validation():
    """提供期望 headers，校验表头匹配。"""
    page = AsyncMock()
    page.query_selector.return_value = AsyncMock()
    page.evaluate.return_value = {
        "headers": ["Account ID", "Amount"],
        "rows": [["001", "100"]],
    }

    params = TableExtractParams(headers=["account", "amount"])
    skill = TableExtractSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["header_match"] is True


@pytest.mark.asyncio
async def test_table_extract_header_mismatch_warning():
    """表头不匹配 → header_match=False，但 status 仍为 COMPLETED。"""
    page = AsyncMock()
    page.query_selector.return_value = AsyncMock()
    page.evaluate.return_value = {
        "headers": ["Name", "Date"],
        "rows": [["foo", "2026-01-01"]],
    }

    params = TableExtractParams(headers=["account"])
    skill = TableExtractSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["header_match"] is False


@pytest.mark.asyncio
async def test_table_extract_empty_rows():
    """空表（无数据行）→ COMPLETED，row_count=0。"""
    page = AsyncMock()
    page.query_selector.return_value = AsyncMock()
    page.evaluate.return_value = {"headers": ["a"], "rows": []}

    params = TableExtractParams()
    skill = TableExtractSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["row_count"] == 0


@pytest.mark.asyncio
async def test_table_extract_no_page():
    """context 无 page → FAILED。"""
    params = TableExtractParams()
    skill = TableExtractSkill()
    result = await skill.execute(params, {})

    assert result.status == SkillStatus.FAILED


@pytest.mark.asyncio
async def test_table_extract_custom_selector():
    """自定义 table_selector 应传给 query_selector。"""
    page = AsyncMock()
    page.query_selector.return_value = AsyncMock()
    page.evaluate.return_value = {"headers": [], "rows": []}

    params = TableExtractParams(table_selector="#my-table")
    skill = TableExtractSkill()
    await skill.execute(params, {"page": page})

    page.query_selector.assert_awaited_once_with("#my-table")


def test_table_extract_class_vars():
    assert TableExtractSkill.skill_name == "table_extract"
    assert TableExtractSkill.error_strategy == ErrorStrategy.RETRY
    assert TableExtractSkill.max_retries == 2


# ---------------------------------------------------------------------------
# FileDownloadSkill 测试
# ---------------------------------------------------------------------------

def _make_download_mock(filename: str = "report.csv") -> MagicMock:
    """构造 mock 的 download 对象。"""
    d = MagicMock()
    d.suggested_filename = filename
    d.save_as = AsyncMock()
    return d


def _attach_expect_download(page: AsyncMock, download_mock: MagicMock) -> None:
    """将 mock 的 expect_download async context manager 挂到 page 上。

    page.expect_download(timeout=...) 返回 ctx，ctx.__aenter__ 返回 ctx 自身，
    ctx.value 是 download 对象。模拟 Playwright 的
    `async with page.expect_download() as dl_info` 行为。
    """
    ctx = MagicMock()
    ctx.__aenter__ = AsyncMock(return_value=ctx)
    ctx.__aexit__ = AsyncMock(return_value=False)
    ctx.value = download_mock
    # expect_download 必须可调用，调用后返回 ctx
    page.expect_download = MagicMock(return_value=ctx)


@pytest.mark.asyncio
async def test_file_download_no_upload_callback(tmp_path):
    """无 upload_callback → 仅返回 save_path。"""
    page = AsyncMock()
    download_mock = _make_download_mock("data.csv")
    _attach_expect_download(page, download_mock)

    # 用 tmp_path 隔离测试
    params = FileDownloadParams(
        trigger_selector="#download-btn",
        download_path=str(tmp_path) + "/",
    )
    skill = FileDownloadSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    assert result.data["filename"] == "data.csv"
    assert "data.csv" in result.data["save_path"]
    assert "minio_key" not in result.data
    download_mock.save_as.assert_awaited_once()


@pytest.mark.asyncio
async def test_file_download_with_upload_callback(tmp_path):
    """配置 upload_callback → 上传 MinIO 并清理本地文件。"""
    page = AsyncMock()
    download_mock = _make_download_mock("data.csv")
    _attach_expect_download(page, download_mock)

    upload_callback = AsyncMock(return_value="tasks/123/screenshots/data.csv")

    params = FileDownloadParams(
        trigger_selector="#download-btn",
        download_path=str(tmp_path) + "/",
    )
    skill = FileDownloadSkill()
    result = await skill.execute(
        params, {"page": page, "upload_callback": upload_callback}
    )

    assert result.status == SkillStatus.COMPLETED
    assert result.data["minio_key"] == "tasks/123/screenshots/data.csv"
    assert "save_path" not in result.data
    upload_callback.assert_awaited_once()


@pytest.mark.asyncio
async def test_file_download_upload_callback_failure(tmp_path):
    """upload_callback 抛异常 → FAILED，本地文件保留。"""
    page = AsyncMock()
    download_mock = _make_download_mock("data.csv")
    _attach_expect_download(page, download_mock)

    upload_callback = AsyncMock(side_effect=Exception("MinIO unavailable"))

    params = FileDownloadParams(
        trigger_selector="#dl",
        download_path=str(tmp_path) + "/",
    )
    skill = FileDownloadSkill()
    result = await skill.execute(
        params, {"page": page, "upload_callback": upload_callback}
    )

    assert result.status == SkillStatus.FAILED
    assert "Upload failed" in (result.error_message or "")


@pytest.mark.asyncio
async def test_file_download_trigger_text():
    """未指定 selector → 用 trigger_text 查找按钮。"""
    page = AsyncMock()
    btn_el = AsyncMock()
    page.query_selector.return_value = btn_el
    download_mock = _make_download_mock("file.pdf")
    _attach_expect_download(page, download_mock)

    params = FileDownloadParams(
        trigger_text="下载报表",
        download_path="/tmp/finrpa/downloads/",
    )
    skill = FileDownloadSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.COMPLETED
    page.query_selector.assert_awaited_once_with("text=下载报表")
    btn_el.click.assert_awaited_once()


@pytest.mark.asyncio
async def test_file_download_trigger_text_not_found():
    """trigger_text 对应按钮未找到 → FAILED。"""
    page = AsyncMock()
    page.query_selector.return_value = None
    download_mock = _make_download_mock()
    _attach_expect_download(page, download_mock)

    params = FileDownloadParams(trigger_text="不存在")
    skill = FileDownloadSkill()
    result = await skill.execute(params, {"page": page})

    assert result.status == SkillStatus.FAILED
    assert "不存在" in (result.error_message or "")


@pytest.mark.asyncio
async def test_file_download_extension_warning(tmp_path):
    """扩展名不匹配 → 仅 warning，仍 COMPLETED。"""
    page = AsyncMock()
    download_mock = _make_download_mock("data.txt")
    _attach_expect_download(page, download_mock)

    params = FileDownloadParams(
        trigger_selector="#dl",
        expected_extension=".csv",
        download_path=str(tmp_path) + "/",
    )
    skill = FileDownloadSkill()
    result = await skill.execute(params, {"page": page})

    # 扩展名不匹配不应导致失败
    assert result.status == SkillStatus.COMPLETED


@pytest.mark.asyncio
async def test_file_download_no_page():
    """context 无 page → FAILED。"""
    params = FileDownloadParams(trigger_selector="#dl")
    skill = FileDownloadSkill()
    result = await skill.execute(params, {})

    assert result.status == SkillStatus.FAILED


@pytest.mark.asyncio
async def test_file_download_llm_handler_mode(tmp_path):
    """配置 llm_handler 且未指定 trigger_selector → 走 LLM 模式。"""
    page = AsyncMock()
    llm_handler = AsyncMock()
    download_mock = _make_download_mock("data.csv")
    _attach_expect_download(page, download_mock)

    params = FileDownloadParams(
        trigger_text="下载",
        download_path=str(tmp_path) + "/",
    )
    skill = FileDownloadSkill()
    result = await skill.execute(
        params, {"page": page, "llm_handler": llm_handler}
    )

    assert result.status == SkillStatus.COMPLETED
    llm_handler.assert_awaited_once()
    # LLM 模式不应调用 page.click
    page.click.assert_not_awaited()


def test_file_download_default_path():
    """默认 download_path 应为 /tmp/finrpa/downloads/（决策 5）。"""
    params = FileDownloadParams()
    assert params.download_path == "/tmp/finrpa/downloads/"


def test_file_download_class_vars():
    assert FileDownloadSkill.skill_name == "file_download"
    assert FileDownloadSkill.error_strategy == ErrorStrategy.RETRY
    assert FileDownloadSkill.max_retries == 2

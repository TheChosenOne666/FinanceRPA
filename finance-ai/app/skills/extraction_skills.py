"""数据提取类 Skill：TableExtractSkill 与 FileDownloadSkill。

处理金融系统的表格数据提取与文件下载场景。

@from enterprise/skills/extraction_skills.py
@author FinanceRPA
"""

import logging
import os
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
# TableExtractSkill 表格提取技能
# ------------------------------------------------------------------

class TableExtractParams(BaseModel):
    """TableExtractSkill 的输入参数。"""

    table_selector: str | None = Field(
        default=None,
        description="目标表格 CSS 选择器（省略时自动检测）",
    )
    headers: list[str] | None = Field(
        default=None,
        description="期望列头列表（用于校验）",
    )
    output_format: str = Field(
        default="json",
        description="输出格式：json | csv",
    )
    max_rows: int = Field(
        default=1000,
        description="最大提取行数（安全限制）",
    )
    include_pagination: bool = Field(
        default=False,
        description="是否跨多页提取",
    )
    skip_empty_rows: bool = Field(
        default=True,
        description="是否跳过空行",
    )


@register_skill
class TableExtractSkill(BaseSkill):
    """从 HTML 表格提取结构化数据。

    支持标准 <table> 元素与现代金融系统的 CSS grid 表格。
    输出 JSON（list[dict]）或 CSV 字符串。
    """

    skill_name: ClassVar[str] = "table_extract"
    description: ClassVar[str] = "从页面表格提取结构化数据"
    params_model: ClassVar[type[BaseModel]] = TableExtractParams
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.RETRY
    max_retries: ClassVar[int] = 2

    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行表格数据提取。"""
        start = time.monotonic()
        p: TableExtractParams = params  # type: ignore[assignment]
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

            # 2. 定位表格元素
            selector = p.table_selector or "table"
            table = await page.query_selector(selector)
            if table is None:
                # 表格不存在 → 失败
                return SkillResult(
                    status=SkillStatus.FAILED,
                    error_message=f"No table found with selector '{selector}'",
                    duration_ms=int((time.monotonic() - start) * 1000),
                )

            # 3. 通过 JS 批量提取表头与数据行（性能优于多次 DOM 查询）
            raw_data = await page.evaluate("""(args) => {
                const [selector, maxRows, skipEmpty] = args;
                const table = document.querySelector(selector);
                if (!table) return { headers: [], rows: [] };

                const headerCells = table.querySelectorAll('thead th, thead td, tr:first-child th');
                const headers = Array.from(headerCells).map(c => c.innerText.trim());

                const bodyRows = table.querySelectorAll('tbody tr');
                const rows = [];
                for (let i = 0; i < Math.min(bodyRows.length, maxRows); i++) {
                    const cells = bodyRows[i].querySelectorAll('td, th');
                    const row = Array.from(cells).map(c => c.innerText.trim());
                    if (skipEmpty && row.every(c => c === '')) continue;
                    rows.push(row);
                }

                return { headers, rows };
            }""", [selector, p.max_rows, p.skip_empty_rows])

            extracted_headers = raw_data.get("headers", [])
            extracted_rows = raw_data.get("rows", [])

            # 4. 校验表头（如提供了期望 headers）
            header_match = True
            if p.headers:
                header_match = all(
                    any(exp.lower() in h.lower() for h in extracted_headers)
                    for exp in p.headers
                )
                if not header_match:
                    logger.warning(
                        "TableExtract: header mismatch. Expected %s, got %s",
                        p.headers, extracted_headers,
                    )

            # 5. 按输出格式序列化
            if p.output_format == "csv":
                # CSV：用 csv 模块写入 StringIO
                import csv
                import io
                buf = io.StringIO()
                writer = csv.writer(buf)
                if extracted_headers:
                    writer.writerow(extracted_headers)
                writer.writerows(extracted_rows)
                output_data = buf.getvalue()
            else:
                # JSON：list[dict]，表头作 key
                if extracted_headers:
                    # zip headers 与 row；表头数与单元格数应相等，用 strict=False 容错
                    output_data = [
                        dict(zip(extracted_headers, row, strict=False))
                        for row in extracted_rows
                    ]
                else:
                    # 无表头：返回 list[list[str]]
                    output_data = extracted_rows

            elapsed = int((time.monotonic() - start) * 1000)
            return SkillResult(
                status=SkillStatus.COMPLETED,
                data={
                    "headers": extracted_headers,
                    "row_count": len(extracted_rows),
                    "output_format": p.output_format,
                    "output": output_data,
                    "header_match": header_match,
                },
                duration_ms=elapsed,
            )

        except Exception as e:
            elapsed = int((time.monotonic() - start) * 1000)
            logger.error("TableExtractSkill failed: %s", e)
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=str(e),
                duration_ms=elapsed,
            )


# ------------------------------------------------------------------
# FileDownloadSkill 文件下载技能
# ------------------------------------------------------------------

class FileDownloadParams(BaseModel):
    """FileDownloadSkill 的输入参数。"""

    trigger_selector: str | None = Field(
        default=None,
        description="下载触发元素的 CSS 选择器",
    )
    trigger_text: str | None = Field(
        default=None,
        description="下载按钮/链接的文本（未指定 selector 时用）",
    )
    download_path: str = Field(
        default="/tmp/finrpa/downloads/",
        description="下载文件保存目录（容器内临时路径）",
    )
    expected_extension: str | None = Field(
        default=None,
        description="期望文件扩展名（如 '.csv', '.pdf'）",
    )
    wait_timeout_ms: int = Field(
        default=30000,
        description="等待下载完成的最大毫秒数",
    )


@register_skill
class FileDownloadSkill(BaseSkill):
    """触发文件下载并等待完成。

    下载流程：
    1. 通过 page.expect_download() 捕获下载事件
    2. 触发下载（LLM / selector / text）
    3. save_as 保存到本地临时目录
    4. 若 context["upload_callback"] 存在：上传 MinIO 后清理本地文件，返回 minio_key
       否则仅返回 save_path

    符合项目硬约束「截图/下载文件直接上传 MinIO，不落盘」。
    """

    skill_name: ClassVar[str] = "file_download"
    description: ClassVar[str] = "触发下载并等待文件保存"
    params_model: ClassVar[type[BaseModel]] = FileDownloadParams
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.RETRY
    max_retries: ClassVar[int] = 2

    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行文件下载。"""
        start = time.monotonic()
        p: FileDownloadParams = params  # type: ignore[assignment]
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
            upload_callback = ctx.get("upload_callback")

            # 2. 确保下载目录存在
            os.makedirs(p.download_path, exist_ok=True)

            # 3. 在点击前开始监听 download 事件（避免错过事件）
            async with page.expect_download(timeout=p.wait_timeout_ms) as download_info:
                if llm_handler and not p.trigger_selector:
                    # LLM 模式：视觉决策点击下载按钮
                    text = p.trigger_text or "download"
                    await llm_handler(page, f"Click the download button: '{text}'")
                elif p.trigger_selector:
                    # 指定选择器
                    await page.click(p.trigger_selector)
                elif p.trigger_text:
                    # 用文本查找下载按钮
                    btn = await page.query_selector(f"text={p.trigger_text}")
                    if btn:
                        await btn.click()
                    else:
                        return SkillResult(
                            status=SkillStatus.FAILED,
                            error_message=f"Download trigger '{p.trigger_text}' not found",
                            duration_ms=int((time.monotonic() - start) * 1000),
                        )

            # 4. 获取 download 对象与建议文件名
            download = download_info.value
            filename = download.suggested_filename

            # 5. 校验文件扩展名（仅 warning，不 fail）
            if p.expected_extension and not filename.endswith(p.expected_extension):
                logger.warning(
                    "FileDownload: expected %s but got %s",
                    p.expected_extension, filename,
                )

            # 6. 保存到本地临时目录
            save_path = f"{p.download_path.rstrip('/')}/{filename}"
            await download.save_as(save_path)

            elapsed = int((time.monotonic() - start) * 1000)

            # 7. 若配置了 upload_callback：上传 MinIO 并清理本地文件
            if upload_callback:
                try:
                    # upload_callback 签名：async def upload_callback(local_path, filename) -> str
                    minio_key = await upload_callback(save_path, filename)
                    # 上传成功后删除本地临时文件
                    try:
                        os.remove(save_path)
                        logger.info("FileDownloadSkill: uploaded %s, local cleaned", minio_key)
                    except OSError as clean_err:
                        logger.warning("FileDownloadSkill: local cleanup failed: %s", clean_err)

                    return SkillResult(
                        status=SkillStatus.COMPLETED,
                        data={
                            "filename": filename,
                            "minio_key": minio_key,
                            "suggested_filename": download.suggested_filename,
                        },
                        duration_ms=elapsed,
                    )
                except Exception as upload_err:
                    # 上传失败：返回 FAILED，本地文件保留以便重试
                    logger.error("FileDownloadSkill: upload_callback failed: %s", upload_err)
                    return SkillResult(
                        status=SkillStatus.FAILED,
                        error_message=f"Upload failed: {upload_err}",
                        duration_ms=elapsed,
                    )

            # 8. 无 upload_callback：仅返回本地 save_path（M3.1 单元测试场景）
            logger.info("FileDownloadSkill: saved %s in %dms", save_path, elapsed)
            return SkillResult(
                status=SkillStatus.COMPLETED,
                data={
                    "filename": filename,
                    "save_path": save_path,
                    "suggested_filename": download.suggested_filename,
                },
                duration_ms=elapsed,
            )

        except Exception as e:
            elapsed = int((time.monotonic() - start) * 1000)
            logger.error("FileDownloadSkill failed: %s", e)
            return SkillResult(
                status=SkillStatus.FAILED,
                error_message=str(e),
                duration_ms=elapsed,
            )

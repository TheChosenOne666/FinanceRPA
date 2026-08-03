"""审计回调客户端（M7.3）。

AuditReporter 封装"每步操作前后截图 → 上传 Java → 上报审计元数据"完整流程，
Java 不可用时本地缓存，恢复后批量上报。

数据流（系统设计 6.4.2）：
  1. 操作前截图 → 上传 Java → Java 转发 MinIO → 返回预签名 URL
  2. 执行操作
  3. 操作后截图 → 上传 Java → Java 转发 MinIO → 返回预签名 URL
  4. 上报审计元数据（含 before/after URL）
  5. Java SanitizeService 脱敏 action_params
  6. Java AuditService 持久化

@from enterprise/audit/reporter.py
@author FinanceRPA
"""

import logging
from datetime import datetime, timezone

from app.audit.cache import AuditCache
from app.audit.schemas import AuditLogPayload
from app.clients.java_backend import JavaBackendClient

logger = logging.getLogger(__name__)


class AuditReporter:
    """审计回调客户端。

    封装截图上传 + 元数据上报 + 失败本地缓存 + 恢复批量上报。
    ExecutorAgent 每步执行时调用 report_step 完成审计闭环。
    """

    def __init__(
        self,
        java_client: JavaBackendClient | None = None,
        cache: AuditCache | None = None,
    ):
        """
        @param java_client: Java 后端回调客户端
        @param cache: 本地缓存（默认新建）
        """
        self.java_client = java_client or JavaBackendClient()
        self.cache = cache or AuditCache()

    async def upload_screenshot(
        self,
        org_id: str,
        task_id: str,
        step_index: int,
        phase: str,
        image_data: bytes,
    ) -> str | None:
        """上传单张截图到 Java（转发 MinIO）。

        @param org_id: 组织 ID
        @param task_id: 任务 ID
        @param step_index: 步骤序号
        @param phase: 阶段（before / after）
        @param image_data: PNG 二进制数据
        @return: 预签名 URL，失败时缓存到本地并返回 None
        """
        try:
            url = await self.java_client.upload_screenshot(
                org_id=org_id,
                task_id=task_id,
                step_index=step_index,
                phase=phase,
                image_data=image_data,
            )
            if url:
                logger.info(
                    "AuditReporter: 截图上传成功 [task=%s, step=%d, phase=%s]",
                    task_id, step_index, phase,
                )
                return url
            # Java 返回 None，缓存待重试
            logger.warning(
                "AuditReporter: 截图上传返回空，缓存待重试 [task=%s, step=%d, phase=%s]",
                task_id, step_index, phase,
            )
            self.cache.save("screenshot", {
                "org_id": org_id,
                "task_id": task_id,
                "step_index": step_index,
                "phase": phase,
                "image_b64": _encode_bytes(image_data),
            })
            return None
        except Exception as e:
            logger.error(
                "AuditReporter: 截图上传异常，缓存待重试 [task=%s, step=%d, phase=%s, error=%s]",
                task_id, step_index, phase, e, exc_info=True,
            )
            self.cache.save("screenshot", {
                "org_id": org_id,
                "task_id": task_id,
                "step_index": step_index,
                "phase": phase,
                "image_b64": _encode_bytes(image_data),
            })
            return None

    async def report_audit_log(self, payload: AuditLogPayload) -> bool:
        """上报审计元数据到 Java。

        @param payload: 审计日志载荷
        @return: 是否成功；失败时缓存到本地返回 False
        """
        try:
            success = await self.java_client.report_audit_log(payload)
            if success:
                logger.info(
                    "AuditReporter: 审计日志上报成功 [task=%s, action=%s]",
                    payload.task_id, payload.action_type,
                )
                return True
            logger.warning(
                "AuditReporter: 审计日志上报失败，缓存待重试 [task=%s, action=%s]",
                payload.task_id, payload.action_type,
            )
            self.cache.save("audit_log", payload.model_dump(by_alias=True, exclude_none=True))
            return False
        except Exception as e:
            logger.error(
                "AuditReporter: 审计日志上报异常，缓存待重试 [task=%s, action=%s, error=%s]",
                payload.task_id, payload.action_type, e, exc_info=True,
            )
            self.cache.save("audit_log", payload.model_dump(by_alias=True, exclude_none=True))
            return False

    async def report_step(
        self,
        org_id: str,
        task_id: str,
        step_index: int,
        action_type: str,
        before_screenshot: bytes | None = None,
        after_screenshot: bytes | None = None,
        started_at: datetime | None = None,
        completed_at: datetime | None = None,
        duration_ms: int | None = None,
        page_url: str | None = None,
        action_params: str | None = None,
        execution_result: str = "success",
        error_message: str | None = None,
        risk_level: str | None = None,
        approval_id: str | None = None,
        department_id: str | None = None,
        business_line_id: str | None = None,
        user_id: str | None = None,
        llm_model: str | None = None,
        llm_tokens_used: int | None = None,
        llm_cost: float | None = None,
    ) -> bool:
        """一步操作的完整审计闭环：上传前后截图 + 上报元数据。

        顺序（系统设计 6.4.2）：
        1. before 截图上传 → 拿到 before_url
        2. after 截图上传 → 拿到 after_url
        3. 上报审计元数据（含 before/after URL）

        截图上传失败时仍继续上报元数据（URL 为 None），不阻断主流程。

        @return: 元数据是否上报成功（截图失败不影响返回值）
        """
        # 1. 上传 before 截图
        before_url: str | None = None
        if before_screenshot:
            before_url = await self.upload_screenshot(
                org_id, task_id, step_index, "before", before_screenshot,
            )

        # 2. 上传 after 截图
        after_url: str | None = None
        if after_screenshot:
            after_url = await self.upload_screenshot(
                org_id, task_id, step_index, "after", after_screenshot,
            )

        # 3. 上报审计元数据
        payload = AuditLogPayload(
            taskId=task_id,
            orgId=org_id,
            departmentId=department_id,
            businessLineId=business_line_id,
            userId=user_id,
            actionType=action_type,
            pageUrl=page_url,
            actionParams=action_params,
            executionResult=execution_result,
            errorMessage=error_message,
            riskLevel=risk_level,
            approvalId=approval_id,
            startedAt=started_at,
            completedAt=completed_at,
            durationMs=duration_ms,
            beforeScreenshotUrl=before_url,
            afterScreenshotUrl=after_url,
            llmModel=llm_model,
            llmTokensUsed=llm_tokens_used,
            llmCost=llm_cost,
        )
        return await self.report_audit_log(payload)

    async def flush_cache(self) -> int:
        """恢复后批量上报本地缓存的待发送项。

        遍历缓存目录，逐项重试上报：
        - screenshot 类型：解码 base64 后调用 upload_screenshot
        - audit_log 类型：构造 AuditLogPayload 后调用 report_audit_log
        成功则删除缓存文件，失败保留待下次 flush。

        @return: 成功上报的数量
        """
        items = self.cache.load_all()
        if not items:
            return 0

        logger.info("AuditReporter: 开始批量上报缓存项 %d 个", len(items))
        success_count = 0

        for item in items:
            item_type = item.get("type")
            payload = item.get("payload", {})
            filepath = item.get("filepath")

            try:
                if item_type == "screenshot":
                    image_data = _decode_bytes(payload.get("image_b64", ""))
                    url = await self.java_client.upload_screenshot(
                        org_id=payload.get("org_id", ""),
                        task_id=payload.get("task_id", ""),
                        step_index=int(payload.get("step_index", 0)),
                        phase=payload.get("phase", "before"),
                        image_data=image_data,
                    )
                    if url:
                        success_count += 1
                        if filepath:
                            self.cache.remove(filepath)
                    # 注意：截图缓存重试不再次缓存，避免无限增长

                elif item_type == "audit_log":
                    payload_obj = AuditLogPayload.model_validate(payload)
                    success = await self.java_client.report_audit_log(payload_obj)
                    if success:
                        success_count += 1
                        if filepath:
                            self.cache.remove(filepath)

            except Exception as e:
                logger.warning(
                    "AuditReporter: 缓存项重试失败 [type=%s, file=%s, error=%s]",
                    item_type, filepath, e,
                )

        logger.info(
            "AuditReporter: 缓存批量上报完成 [success=%d/%d]",
            success_count, len(items),
        )
        return success_count


def _encode_bytes(data: bytes) -> str:
    """bytes 编码为 base64 字符串（用于 JSON 序列化）。"""
    import base64
    return base64.b64encode(data).decode("ascii")


def _decode_bytes(encoded: str) -> bytes:
    """base64 字符串解码为 bytes。"""
    import base64
    return base64.b64decode(encoded)

"""ExecutorAgent：执行计划中的单个子任务。

通过可配置的 action_handler 执行子任务（生产环境封装 Skyvern 感知-动作循环）。
结果报告给 Coordinator 用于状态跟踪和可能的重新规划。

M2.4 增强：集成 JavaBackendClient 回调 + 事件总线 SSE 推送。
每步执行后上报 Java 更新任务/子任务状态，发布 SSE 事件。

@from enterprise/agent/executor.py
@author FinanceRPA
"""

import logging
import time
from datetime import datetime
from typing import Any

from app.clients.java_backend import JavaBackendClient

from .event_bus import TaskEventBus
from .schemas import ExecutionResult, SubTask, SubTaskStatus

logger = logging.getLogger(__name__)

# SubTaskStatus → Java 大写枚举映射
_SUBTASK_STATUS_MAP = {
    SubTaskStatus.PENDING: "PENDING",
    SubTaskStatus.RUNNING: "RUNNING",
    SubTaskStatus.COMPLETED: "COMPLETED",
    SubTaskStatus.FAILED: "FAILED",
    SubTaskStatus.SKIPPED: "SKIPPED",
    SubTaskStatus.REPLANNED: "REPLANNED",
}


class ExecutorAgent:
    """执行 PlannerAgent 计划中的子任务。

    每个子任务通过可配置的 action_handler 执行（生产环境封装 Skyvern）。
    结果报告给 Coordinator 用于状态跟踪和可能的重新规划。

    M2.4：每步执行后回调 Java 更新子任务状态，发布 SSE 事件。
    """

    def __init__(
        self,
        action_handler=None,
        java_client: JavaBackendClient | None = None,
        event_bus: TaskEventBus | None = None,
        task_id: str | None = None,
        org_id: str | None = None,
    ):
        """
        @param action_handler: 异步函数(goal: str, context: dict) -> dict
                               执行实际浏览器交互，返回:
                               {"success": bool, "data": dict, "error": str | None,
                                "screenshot_key": str | None, "page_url": str | None}
        @param java_client: Java 后端回调客户端（可选，M2.4 状态回调）
        @param event_bus: 事件总线（可选，M2.4 SSE 推送）
        @param task_id: 任务 ID（回调 Java 时需要）
        @param org_id: 组织 ID（审计日志需要）
        """
        self.action_handler = action_handler
        self.java_client = java_client
        self.event_bus = event_bus
        self.task_id = task_id
        self.org_id = org_id

    async def execute_subtask(
        self,
        subtask: SubTask,
        context: dict[str, Any] | None = None,
    ) -> ExecutionResult:
        """执行单个子任务（带重试逻辑 + 状态回调）。

        @param subtask: 要执行的子任务
        @param context: 执行上下文（浏览器页面、会话数据等）
        @return: 包含成功状态和详情的 ExecutionResult
        """
        # 1. 标记为运行中
        subtask.status = SubTaskStatus.RUNNING
        subtask.started_at = datetime.utcnow()

        logger.info(
            "ExecutorAgent: 开始执行子任务 [task=%s, subtask=%s, index=%d, goal=%s, max_retries=%d]",
            self.task_id, subtask.subtask_id, subtask.index, subtask.goal, subtask.max_retries,
        )

        # 2. 回调 Java + 发布 SSE 事件：子任务开始
        await self._on_step_start(subtask)

        start = time.monotonic()
        last_error = None

        # 3. 带重试执行
        for attempt in range(subtask.max_retries + 1):
            try:
                if self.action_handler:
                    logger.info(
                        "ExecutorAgent: 调用 action_handler 执行子任务 %s（第 %d/%d 次尝试）",
                        subtask.subtask_id, attempt + 1, subtask.max_retries + 1,
                    )
                    handler_result = await self.action_handler(
                        subtask.goal, context or {},
                    )
                else:
                    # fallback：模拟执行（测试/开发用）
                    logger.info(
                        "ExecutorAgent: 使用模拟执行子任务 %s（无 action_handler）",
                        subtask.subtask_id,
                    )
                    handler_result = self._simulate_execution(subtask)

                elapsed = int((time.monotonic() - start) * 1000)

                # 4. 成功
                if handler_result.get("success", False):
                    subtask.status = SubTaskStatus.COMPLETED
                    subtask.completed_at = datetime.utcnow()
                    subtask.result_data = handler_result.get("data")

                    logger.info(
                        "ExecutorAgent: 子任务 %s 完成于 %dms（第 %d 次尝试）, result_data=%s",
                        subtask.subtask_id, elapsed, attempt + 1, subtask.result_data,
                    )

                    result = ExecutionResult(
                        subtask_id=subtask.subtask_id,
                        success=True,
                        result_data=handler_result.get("data"),
                        screenshot_key=handler_result.get("screenshot_key"),
                        page_url=handler_result.get("page_url"),
                        duration_ms=elapsed,
                    )

                    # 5. 回调 Java + 发布 SSE 事件：子任务成功
                    await self._on_step_end(subtask, result)
                    return result

                # 6. 失败，记录错误
                last_error = handler_result.get("error", "未知错误")
                logger.warning(
                    "ExecutorAgent: 子任务 %s 第 %d/%d 次尝试失败: %s",
                    subtask.subtask_id, attempt + 1, subtask.max_retries + 1, last_error,
                )

            except Exception as e:
                last_error = str(e)
                logger.warning(
                    "ExecutorAgent: 子任务 %s 第 %d/%d 次尝试异常: %s",
                    subtask.subtask_id, attempt + 1, subtask.max_retries + 1, e,
                    exc_info=True,
                )

            # 7. 准备重试
            if attempt < subtask.max_retries:
                logger.info(
                    "ExecutorAgent: 准备重试子任务 %s（%d/%d）",
                    subtask.subtask_id, attempt + 2, subtask.max_retries + 1,
                )

        # 8. 所有重试耗尽
        elapsed = int((time.monotonic() - start) * 1000)
        subtask.status = SubTaskStatus.FAILED
        subtask.completed_at = datetime.utcnow()
        subtask.error_message = last_error

        logger.error(
            "ExecutorAgent: 子任务 %s 在 %d 次尝试后失败: %s",
            subtask.subtask_id, subtask.max_retries + 1, last_error,
        )

        result = ExecutionResult(
            subtask_id=subtask.subtask_id,
            success=False,
            error_message=last_error,
            duration_ms=elapsed,
        )

        # 9. 回调 Java + 发布 SSE 事件：子任务失败
        await self._on_step_end(subtask, result)
        return result

    async def _on_step_start(self, subtask: SubTask) -> None:
        """子任务开始时的回调：更新 Java 子任务状态 + 发布 SSE 事件。"""
        # 回调 Java：子任务 RUNNING
        if self.java_client and self.task_id:
            logger.info(
                "ExecutorAgent: 回调 Java 更新子任务状态 → RUNNING [task=%s, index=%d]",
                self.task_id, subtask.index,
            )
            await self.java_client.update_subtask(
                task_id=self.task_id,
                subtask_index=subtask.index,
                status=_SUBTASK_STATUS_MAP[SubTaskStatus.RUNNING],
            )

        # 发布 SSE 事件
        if self.event_bus and self.task_id:
            logger.info(
                "ExecutorAgent: 发布 SSE 事件 step_start [task=%s, index=%d]",
                self.task_id, subtask.index,
            )
            await self.event_bus.publish(
                self.task_id,
                "step_start",
                {
                    "subtaskIndex": subtask.index,
                    "goal": subtask.goal,
                    "message": f"开始执行: {subtask.goal}",
                },
            )

    async def _on_step_end(self, subtask: SubTask, result: ExecutionResult) -> None:
        """子任务结束时的回调：更新 Java 子任务状态 + 发布 SSE 事件 + 审计日志。"""
        final_status = _SUBTASK_STATUS_MAP.get(subtask.status, "FAILED")
        logger.info(
            "ExecutorAgent: 子任务 %s 结束, success=%s, status=%s, duration=%dms",
            subtask.subtask_id, result.success, final_status, result.duration_ms,
        )

        # 回调 Java：子任务终态
        if self.java_client and self.task_id:
            logger.info(
                "ExecutorAgent: 回调 Java 更新子任务终态 → %s [task=%s, index=%d, error=%s]",
                final_status, self.task_id, subtask.index, result.error_message,
            )
            await self.java_client.update_subtask(
                task_id=self.task_id,
                subtask_index=subtask.index,
                status=final_status,
                error_message=result.error_message,
                result_data=result.result_data,
            )

            # 上报审计日志
            if self.org_id:
                logger.info(
                    "ExecutorAgent: 上报审计日志 [task=%s, org=%s, action=EXECUTE, result=%s]",
                    self.task_id, self.org_id, "success" if result.success else "failed",
                )
                # M7.3：改用 AuditLogPayload 完整字段上报
                from app.audit.schemas import AuditLogPayload
                audit_payload = AuditLogPayload(
                    taskId=self.task_id,
                    orgId=self.org_id,
                    actionType="EXECUTE",
                    pageUrl=result.page_url,
                    executionResult="success" if result.success else "failed",
                    errorMessage=result.error_message,
                    durationMs=result.duration_ms,
                    completedAt=subtask.completed_at,
                )
                await self.java_client.report_audit_log(audit_payload)

        # 发布 SSE 事件
        if self.event_bus and self.task_id:
            logger.info(
                "ExecutorAgent: 发布 SSE 事件 step_end [task=%s, index=%d, success=%s]",
                self.task_id, subtask.index, result.success,
            )
            event_data = {
                "subtaskIndex": subtask.index,
                "success": result.success,
                "durationMs": result.duration_ms,
            }
            if result.success:
                event_data["message"] = f"子任务 {subtask.index + 1} 完成"
            else:
                event_data["message"] = f"子任务 {subtask.index + 1} 失败: {result.error_message}"
                event_data["error"] = result.error_message

            # 截图元数据
            if result.screenshot_key:
                event_data["screenshotKey"] = result.screenshot_key

            await self.event_bus.publish(self.task_id, "step_end", event_data)

    def _simulate_execution(self, subtask: SubTask) -> dict:
        """无 action_handler 时的 fallback 模拟执行（测试/开发用）。"""
        return {
            "success": True,
            "data": {"goal": subtask.goal, "simulated": True},
        }

"""ExecutorAgent：执行计划中的单个子任务。

通过可配置的 action_handler 执行子任务（生产环境封装 Skyvern 感知-动作循环）。
结果报告给 Coordinator 用于状态跟踪和可能的重新规划。

@from enterprise/agent/executor.py
@author FinanceRPA
"""

import logging
import time
from datetime import datetime
from typing import Any

from .schemas import ExecutionResult, SubTask, SubTaskStatus

logger = logging.getLogger(__name__)


class ExecutorAgent:
    """执行 PlannerAgent 计划中的子任务。

    每个子任务通过可配置的 action_handler 执行（生产环境封装 Skyvern）。
    结果报告给 Coordinator 用于状态跟踪和可能的重新规划。
    """

    def __init__(self, action_handler=None):
        """
        @param action_handler: 异步函数(goal: str, context: dict) -> dict
                               执行实际浏览器交互，返回:
                               {"success": bool, "data": dict, "error": str | None,
                                "screenshot_key": str | None, "page_url": str | None}
        """
        self.action_handler = action_handler

    async def execute_subtask(
        self,
        subtask: SubTask,
        context: dict[str, Any] | None = None,
    ) -> ExecutionResult:
        """执行单个子任务（带重试逻辑）。

        @param subtask: 要执行的子任务
        @param context: 执行上下文（浏览器页面、会话数据等）
        @return: 包含成功状态和详情的 ExecutionResult
        """
        # 1. 标记为运行中
        subtask.status = SubTaskStatus.RUNNING
        subtask.started_at = datetime.utcnow()

        start = time.monotonic()
        last_error = None

        # 2. 带重试执行
        for attempt in range(subtask.max_retries + 1):
            try:
                if self.action_handler:
                    handler_result = await self.action_handler(
                        subtask.goal, context or {},
                    )
                else:
                    # fallback：模拟执行（测试/开发用）
                    handler_result = self._simulate_execution(subtask)

                elapsed = int((time.monotonic() - start) * 1000)

                # 3. 成功
                if handler_result.get("success", False):
                    subtask.status = SubTaskStatus.COMPLETED
                    subtask.completed_at = datetime.utcnow()
                    subtask.result_data = handler_result.get("data")

                    logger.info(
                        "ExecutorAgent: 子任务 %s 完成于 %dms（第 %d 次尝试）",
                        subtask.subtask_id, elapsed, attempt + 1,
                    )
                    return ExecutionResult(
                        subtask_id=subtask.subtask_id,
                        success=True,
                        result_data=handler_result.get("data"),
                        screenshot_key=handler_result.get("screenshot_key"),
                        page_url=handler_result.get("page_url"),
                        duration_ms=elapsed,
                    )

                # 4. 失败，记录错误
                last_error = handler_result.get("error", "未知错误")
                logger.warning(
                    "ExecutorAgent: 子任务 %s 第 %d 次尝试失败: %s",
                    subtask.subtask_id, attempt + 1, last_error,
                )

            except Exception as e:
                last_error = str(e)
                logger.warning(
                    "ExecutorAgent: 子任务 %s 第 %d 次尝试异常: %s",
                    subtask.subtask_id, attempt + 1, e,
                )

            # 5. 准备重试
            if attempt < subtask.max_retries:
                logger.info(
                    "ExecutorAgent: 重试子任务 %s（%d/%d）",
                    subtask.subtask_id, attempt + 2, subtask.max_retries + 1,
                )

        # 6. 所有重试耗尽
        elapsed = int((time.monotonic() - start) * 1000)
        subtask.status = SubTaskStatus.FAILED
        subtask.completed_at = datetime.utcnow()
        subtask.error_message = last_error

        logger.error(
            "ExecutorAgent: 子任务 %s 在 %d 次尝试后失败: %s",
            subtask.subtask_id, subtask.max_retries + 1, last_error,
        )
        return ExecutionResult(
            subtask_id=subtask.subtask_id,
            success=False,
            error_message=last_error,
            duration_ms=elapsed,
        )

    def _simulate_execution(self, subtask: SubTask) -> dict:
        """无 action_handler 时的 fallback 模拟执行（测试/开发用）。"""
        return {
            "success": True,
            "data": {"goal": subtask.goal, "simulated": True},
        }

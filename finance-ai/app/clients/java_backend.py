"""Java 后端回调客户端。

Python AI 服务执行任务后，通过此客户端回调 Java 后端的内部 API，
上报任务状态、子任务状态、截图和审计日志。

URL 约定：Java 后端 context-path 为 /api，内部控制器前缀 /internal，
完整路径如 /api/internal/tasks/{taskId}/state。

鉴权：所有请求携带 X-Internal-Token Header。

@author FinanceRPA
"""

import asyncio
import logging

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)

# 重试配置
_MAX_RETRIES = 3
_RETRY_DELAYS = [1.0, 2.0, 4.0]  # 指数退避（秒）


class JavaBackendClient:
    """回调 Java 后端的 HTTP 客户端。

    职责：
    - 上报任务状态变更 → POST /api/internal/tasks/{taskId}/state
    - 上报子任务状态 → POST /api/internal/tasks/{taskId}/subtasks
    - 上传截图 → POST /api/internal/screenshots
    - 上报审计日志 → POST /api/internal/audit/logs
    - 鉴权：X-Internal-Token Header 校验
    - 重试：指数退避（最多 3 次，1s→2s→4s）
    """

    def __init__(self, base_url: str | None = None, internal_token: str | None = None):
        """
        @param base_url: Java 后端地址（默认从配置读取）
        @param internal_token: 服务间鉴权 token（默认从配置读取）
        """
        settings = get_settings()
        self.base_url = (base_url or settings.backend_base_url).rstrip("/")
        self.internal_token = internal_token or settings.internal_api_token
        self._client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        """获取或创建 httpx 异步客户端。"""
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                headers={"X-Internal-Token": self.internal_token},
                timeout=httpx.Timeout(connect=5.0, read=30.0, write=10.0, pool=5.0),
            )
        return self._client

    async def close(self) -> None:
        """关闭客户端连接。"""
        if self._client and not self._client.is_closed:
            await self._client.aclose()
            self._client = None

    async def _request_with_retry(
        self,
        method: str,
        url: str,
        **kwargs,
    ) -> httpx.Response | None:
        """带重试的 HTTP 请求。

        @param method: HTTP 方法（POST/GET）
        @param url: 请求路径
        @param kwargs: 传递给 httpx 的额外参数
        @return: 响应对象，失败返回 None
        """
        client = await self._get_client()
        last_exc = None
        for attempt in range(_MAX_RETRIES):
            try:
                logger.info(
                    "JavaBackendClient: HTTP %s %s（第 %d/%d 次尝试）",
                    method, url, attempt + 1, _MAX_RETRIES,
                )
                resp = await client.request(method, url, **kwargs)
                resp.raise_for_status()
                logger.info(
                    "JavaBackendClient: HTTP %s %s 成功（status=%d）",
                    method, url, resp.status_code,
                )
                return resp
            except (httpx.HTTPStatusError, httpx.RequestError) as e:
                last_exc = e
                if attempt < _MAX_RETRIES - 1:
                    delay = _RETRY_DELAYS[min(attempt, len(_RETRY_DELAYS) - 1)]
                    logger.warning(
                        "JavaBackendClient: HTTP %s %s 第 %d 次失败: %s，%ds 后重试",
                        method, url, attempt + 1, e, delay,
                    )
                    await asyncio.sleep(delay)
                else:
                    logger.error(
                        "JavaBackendClient: HTTP %s %s 重试 %d 次后全部失败: %s",
                        method, url, _MAX_RETRIES, e,
                    )
        return None

    async def update_task_state(
        self,
        task_id: str,
        state: str,
        current_step: int = 0,
        total_steps: int = 0,
        message: str = "",
        error_message: str | None = None,
    ) -> bool:
        """更新任务状态。

        @param task_id: 任务 ID
        @param state: 新状态（大写枚举：PENDING/EXECUTING/SUCCESS/FAILED/NEEDS_HUMAN/ABORTED）
        @param current_step: 当前步骤
        @param total_steps: 总步骤数
        @param message: 附加消息
        @param error_message: 错误信息（失败时填写）
        @return: 是否成功
        """
        payload = {
            "state": state,
            "currentStep": current_step,
            "totalSteps": total_steps,
            "message": message,
        }
        if error_message:
            payload["errorMessage"] = error_message

        logger.info(
            "JavaBackendClient: 更新任务状态 [task=%s, state=%s, step=%d/%d, msg=%s]",
            task_id, state, current_step, total_steps, message,
        )
        resp = await self._request_with_retry(
            "POST", f"/api/internal/tasks/{task_id}/state", json=payload,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 更新任务状态失败 [task=%s, state=%s]", task_id, state,
            )
            return False
        logger.info("JavaBackendClient: 更新任务状态成功 [task=%s, state=%s]", task_id, state)
        return True

    async def update_subtask(
        self,
        task_id: str,
        subtask_index: int,
        status: str,
        error_message: str | None = None,
        result_data: dict | None = None,
    ) -> bool:
        """更新子任务状态。

        @param task_id: 任务 ID
        @param subtask_index: 子任务序号
        @param status: 子任务状态（大写：PENDING/RUNNING/COMPLETED/FAILED/SKIPPED/REPLANNED）
        @param error_message: 错误信息（失败时填写）
        @param result_data: 执行结果数据
        @return: 是否成功
        """
        payload = {
            "subtaskIndex": subtask_index,
            "status": status,
        }
        if error_message:
            payload["errorMessage"] = error_message
        if result_data:
            payload["resultData"] = result_data

        logger.info(
            "JavaBackendClient: 更新子任务状态 [task=%s, index=%d, status=%s]",
            task_id, subtask_index, status,
        )
        resp = await self._request_with_retry(
            "POST", f"/api/internal/tasks/{task_id}/subtasks", json=payload,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 更新子任务状态失败 [task=%s, index=%d, status=%s]",
                task_id, subtask_index, status,
            )
            return False
        logger.info(
            "JavaBackendClient: 更新子任务状态成功 [task=%s, index=%d, status=%s]",
            task_id, subtask_index, status,
        )
        return True

    async def update_coordination_state(
        self,
        task_id: str,
        navigation_goal: str | None = None,
        current_plan: str | None = None,
        completed_subtasks: list[str] | None = None,
        total_replans: int | None = None,
        max_replans: int | None = None,
        status: str | None = None,
        error_message: str | None = None,
    ) -> bool:
        """更新协调状态（M4.2 引入，持久化 CoordinationState 到 Java）。

        Python Coordinator 每步执行后回调此接口，将 CoordinationState 持久化到
        Java 侧 rpa_agent_coordination_state 表，用于断点续跑和 replan 追踪。

        @param task_id: 任务 ID
        @param navigation_goal: 导航目标
        @param current_plan: 当前计划 JSON 字符串
        @param completed_subtasks: 已完成子任务 ID 列表
        @param total_replans: 总重规划次数
        @param max_replans: 最大重规划次数
        @param status: 协调状态（RUNNING/COMPLETED/FAILED/NEEDS_HUMAN）
        @param error_message: 错误信息
        @return: 是否成功
        """
        payload: dict = {}
        if navigation_goal is not None:
            payload["navigationGoal"] = navigation_goal
        if current_plan is not None:
            payload["currentPlan"] = current_plan
        if completed_subtasks is not None:
            payload["completedSubtasks"] = completed_subtasks
        if total_replans is not None:
            payload["totalReplans"] = total_replans
        if max_replans is not None:
            payload["maxReplans"] = max_replans
        if status is not None:
            payload["status"] = status
        if error_message is not None:
            payload["errorMessage"] = error_message

        logger.info(
            "JavaBackendClient: 更新协调状态 [task=%s, status=%s, replans=%s, completed=%d]",
            task_id, status, total_replans,
            len(completed_subtasks) if completed_subtasks else 0,
        )
        resp = await self._request_with_retry(
            "POST", f"/api/internal/tasks/{task_id}/coordination-state", json=payload,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 更新协调状态失败 [task=%s, status=%s]", task_id, status,
            )
            return False
        logger.info(
            "JavaBackendClient: 更新协调状态成功 [task=%s, status=%s]", task_id, status,
        )
        return True

    async def upload_screenshot(
        self,
        task_id: str,
        step: int,
        image_data: bytes,
    ) -> str | None:
        """上传截图到 Java 后端。

        @param task_id: 任务 ID
        @param step: 步骤序号
        @param image_data: 图片二进制数据
        @return: 截图 URL，失败返回 None
        """
        logger.info(
            "JavaBackendClient: 上传截图 [task=%s, step=%d, size=%d bytes]",
            task_id, step, len(image_data),
        )
        resp = await self._request_with_retry(
            "POST",
            "/api/internal/screenshots",
            files={"file": (f"{task_id}_step{step}.png", image_data, "image/png")},
            data={"task_id": task_id, "step": str(step)},
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 上传截图失败 [task=%s, step=%d]", task_id, step,
            )
            return None
        data = resp.json()
        url = data.get("url") or data.get("data", {}).get("url")
        logger.info(
            "JavaBackendClient: 上传截图成功 [task=%s, step=%d, url=%s]",
            task_id, step, url,
        )
        return url

    async def report_audit_log(
        self,
        task_id: str,
        org_id: str,
        action_type: str,
        target_element: str | None = None,
        page_url: str | None = None,
        execution_result: str = "success",
        error_message: str | None = None,
    ) -> bool:
        """上报审计日志。

        @param task_id: 任务 ID
        @param org_id: 组织 ID
        @param action_type: 动作类型（NAVIGATE/CLICK/INPUT_TEXT 等）
        @param target_element: 目标元素
        @param page_url: 页面 URL
        @param execution_result: 执行结果（success/failed）
        @param error_message: 错误信息
        @return: 是否成功
        """
        payload = {
            "taskId": task_id,
            "orgId": org_id,
            "actionType": action_type,
            "executionResult": execution_result,
        }
        if target_element:
            payload["targetElement"] = target_element
        if page_url:
            payload["pageUrl"] = page_url
        if error_message:
            payload["errorMessage"] = error_message

        logger.info(
            "JavaBackendClient: 上报审计日志 [task=%s, org=%s, action=%s, result=%s]",
            task_id, org_id, action_type, execution_result,
        )
        resp = await self._request_with_retry(
            "POST", "/api/internal/audit/logs", json=payload,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 上报审计日志失败 [task=%s]", task_id,
            )
            return False
        logger.info("JavaBackendClient: 上报审计日志成功 [task=%s]", task_id)
        return True

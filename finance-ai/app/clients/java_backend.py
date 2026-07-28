"""Java 后端回调客户端。

Python AI 服务执行任务后，通过此客户端回调 Java 后端的内部 API，
上报任务状态、子任务状态、截图和审计日志。

@author FinanceRPA
"""

import logging

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)


class JavaBackendClient:
    """回调 Java 后端的 HTTP 客户端。

    职责：
    - 上报任务状态变更 → POST /api/v1/internal/tasks/{id}/state
    - 上报子任务状态 → POST /api/v1/internal/tasks/{id}/subtasks
    - 上传截图 → POST /api/v1/internal/screenshots
    - 上报审计日志 → POST /api/v1/internal/audit/logs
    - 鉴权：X-Internal-Token Header 校验
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

    async def update_task_state(
        self,
        task_id: str,
        state: str,
        current_step: int = 0,
        total_steps: int = 0,
        message: str = "",
    ) -> bool:
        """更新任务状态。

        @param task_id: 任务 ID
        @param state: 新状态（pending/executing/success/failed/needs_human）
        @param current_step: 当前步骤
        @param total_steps: 总步骤数
        @param message: 附加消息
        @return: 是否成功
        """
        client = await self._get_client()
        try:
            resp = await client.post(
                f"/api/v1/internal/tasks/{task_id}/state",
                json={
                    "state": state,
                    "current_step": current_step,
                    "total_steps": total_steps,
                    "message": message,
                },
            )
            resp.raise_for_status()
            return True
        except Exception as e:
            logger.error("更新任务状态失败 [task=%s]: %s", task_id, e)
            return False

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
        client = await self._get_client()
        try:
            resp = await client.post(
                "/api/v1/internal/screenshots",
                files={
                    "file": (f"{task_id}_step{step}.png", image_data, "image/png"),
                },
                data={"task_id": task_id, "step": str(step)},
            )
            resp.raise_for_status()
            data = resp.json()
            return data.get("url")
        except Exception as e:
            logger.error("上传截图失败 [task=%s, step=%d]: %s", task_id, step, e)
            return None

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
        client = await self._get_client()
        try:
            resp = await client.post(
                "/api/v1/internal/audit/logs",
                json={
                    "task_id": task_id,
                    "org_id": org_id,
                    "action_type": action_type,
                    "target_element": target_element,
                    "page_url": page_url,
                    "execution_result": execution_result,
                    "error_message": error_message,
                },
            )
            resp.raise_for_status()
            return True
        except Exception as e:
            logger.error("上报审计日志失败 [task=%s]: %s", task_id, e)
            return False

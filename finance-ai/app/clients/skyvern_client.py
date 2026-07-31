"""Skyvern API 客户端。

M3.8：封装 Skyvern 原生 API 调用，供 finance-ai 的 /api/v1/ai/tasks 内部使用。
职责：
- token 管理：启动时自动创建 organization + auth token，全局缓存
- 创建 Skyvern 任务 → POST /api/v1/tasks
- 查询 Skyvern 任务状态 → GET /api/v1/tasks/{skyvern_task_id}
- 状态映射：Skyvern 状态 → RPA 任务状态

鉴权：Skyvern API 使用 x-api-key Header（JWT token，由 SKYVERN_SECRET_KEY 签名）。

@author FinanceRPA
"""

import asyncio
import logging
import uuid
from typing import Any

import httpx

logger = logging.getLogger(__name__)

# Skyvern API base URL（Skyvern 路由挂在 finance-ai 本机上）
_SKYVERN_BASE_URL = "http://127.0.0.1:8000"

# 全局缓存的 Skyvern auth token（lifespan 启动时初始化）
_skyvern_token: str | None = None
_skyvern_org_id: str | None = None


async def init_skyvern_token() -> tuple[str, str] | None:
    """初始化 Skyvern organization 和 auth token（应用启动时调用）。

    创建一个 organization 和对应的 API token，全局缓存。
    如果已存在缓存则直接返回。

    @return (org_id, token) 或 None（初始化失败）
    """
    global _skyvern_token, _skyvern_org_id

    if _skyvern_token and _skyvern_org_id:
        return _skyvern_org_id, _skyvern_token

    try:
        from skyvern.forge import app as forge_app
        from skyvern.forge.sdk.services.org_auth_token_service import (
            create_org_api_token,
        )

        org = await forge_app.DATABASE.create_organization(
            organization_name="FinanceRPA-M3.8",
        )
        token = await create_org_api_token(org.organization_id)

        _skyvern_org_id = org.organization_id
        _skyvern_token = token.token

        logger.info(
            "Skyvern token 初始化成功: org_id=%s, token=%s...",
            _skyvern_org_id, _skyvern_token[:20],
        )
        return _skyvern_org_id, _skyvern_token
    except Exception:
        logger.exception("Skyvern token 初始化失败")
        return None


def get_skyvern_token() -> str | None:
    """获取全局缓存的 Skyvern auth token。"""
    return _skyvern_token


# Skyvern 任务状态 → RPA 任务状态映射
# Skyvern 状态：created / queued / running / completed / failed / terminated / canceled
# RPA 状态：PENDING / EXECUTING / SUCCESS / FAILED / NEEDS_HUMAN / ABORTED
_SKYVERN_STATUS_MAP: dict[str, str] = {
    "created": "PENDING",
    "queued": "PENDING",
    "running": "EXECUTING",
    "completed": "SUCCESS",
    "failed": "FAILED",
    "terminated": "FAILED",
    "canceled": "ABORTED",
}


def map_skyvern_status(skyvern_status: str) -> str:
    """将 Skyvern 任务状态映射为 RPA 任务状态。

    @param skyvern_status: Skyvern 任务状态
    @return RPA 任务状态（PENDING / EXECUTING / SUCCESS / FAILED / ABORTED）
    """
    return _SKYVERN_STATUS_MAP.get(skyvern_status.lower(), "EXECUTING")


class SkyvernClient:
    """Skyvern API 客户端，封装任务创建与状态查询。"""

    def __init__(self, base_url: str | None = None, api_key: str | None = None):
        """初始化 Skyvern 客户端。

        @param base_url: Skyvern API 地址（默认本机）
        @param api_key: Skyvern auth token（默认从全局缓存读取）
        """
        self.base_url = (base_url or _SKYVERN_BASE_URL).rstrip("/")
        self.api_key = api_key or get_skyvern_token()
        self._client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        """获取 httpx 异步客户端（延迟创建）。"""
        if self._client is None or self._client.is_closed:
            headers = {"Content-Type": "application/json"}
            if self.api_key:
                headers["x-api-key"] = self.api_key
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                headers=headers,
                timeout=httpx.Timeout(30.0, connect=10.0),
            )
        return self._client

    def _generate_trace_id(self) -> str:
        """生成 trace_id（X-Request-ID），用于日志串联。"""
        return str(uuid.uuid4())

    async def create_task(
        self,
        title: str,
        url: str,
        navigation_goal: str,
        navigation_payload: dict[str, Any] | None = None,
    ) -> dict[str, Any] | None:
        """创建 Skyvern 任务。

        @param title: 任务标题
        @param url: 目标页面 URL
        @param navigation_goal: 导航目标（LLM 决策依据）
        @param navigation_payload: 附加参数（可选）
        @return Skyvern 任务信息（含 task_id），失败返回 None
        """
        client = await self._get_client()

        body: dict[str, Any] = {
            "title": title,
            "url": url,
            "navigation_goal": navigation_goal,
        }
        if navigation_payload:
            body["navigation_payload"] = str(navigation_payload)

        try:
            # M3.8：trace_id 透传，便于日志串联
            trace_id = self._generate_trace_id()
            resp = await client.post(
                "/api/v1/tasks", json=body, headers={"X-Request-ID": trace_id},
            )
            resp.raise_for_status()
            data = resp.json()
            logger.info(
                "Skyvern 任务创建成功: skyvern_task_id=%s, url=%s, trace_id=%s",
                data.get("task_id"), url, trace_id,
            )
            return data
        except httpx.HTTPStatusError as e:
            logger.error(
                "Skyvern 创建任务 HTTP 错误: status=%s, body=%s",
                e.response.status_code, e.response.text[:200],
            )
            return None
        except Exception as e:
            logger.error("Skyvern 创建任务异常: %s", e, exc_info=True)
            return None

    async def get_task(self, skyvern_task_id: str) -> dict[str, Any] | None:
        """查询 Skyvern 任务状态。

        @param skyvern_task_id: Skyvern 任务 ID
        @return Skyvern 任务信息（含 status / failure_reason 等），失败返回 None
        """
        client = await self._get_client()

        try:
            trace_id = self._generate_trace_id()
            resp = await client.get(
                f"/api/v1/tasks/{skyvern_task_id}",
                headers={"X-Request-ID": trace_id},
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPStatusError as e:
            logger.error(
                "Skyvern 查询任务 HTTP 错误: skyvern_task_id=%s, status=%s, body=%s",
                skyvern_task_id, e.response.status_code, e.response.text[:200],
            )
            return None
        except Exception as e:
            logger.error(
                "Skyvern 查询任务异常: skyvern_task_id=%s, error=%s",
                skyvern_task_id, e, exc_info=True,
            )
            return None

    async def close(self) -> None:
        """关闭 HTTP 客户端。"""
        if self._client and not self._client.is_closed:
            await self._client.aclose()

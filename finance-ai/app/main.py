"""FastAPI 应用入口。

M3.7：集成 Skyvern ForgeApp，挂载 Skyvern 原生 API 路由。
通过 start_forge_app() 初始化 ForgeApp（Database/Storage/LLM/BROWSER_MANAGER），
挂载后即可通过 /api/v1/tasks 等 Skyvern 原生 API 触发浏览器自动化与 LLM 视觉决策。

@from enterprise/skyvern/forge/api_app.py:create_api_app
@author FinanceRPA
"""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import redis.asyncio as aioredis
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app import __version__
from app.agent.event_bus import get_event_bus
from app.api import health, risk, skills, sse, tasks
from app.config import get_settings

logger = logging.getLogger(__name__)

# 不需要鉴权的公开路径（健康检查、Skyvern 原生 API 由 Skyvern 自身鉴权）
PUBLIC_PATH_PREFIXES = (
    "/api/v1/ai/health",
    "/docs",
    "/redoc",
    "/openapi.json",
)


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    """应用生命周期。

    启动顺序：
      1. 初始化 finance-ai 自有配置、Redis 客户端、事件总线
      2. 初始化 Skyvern ForgeApp（M3.7：启动 ForgeAgent + BROWSER_MANAGER + LLM_API_HANDLER）
    关闭顺序：
      1. 关闭 Skyvern persistent browser sessions
      2. 关闭 JavaBackendClient 连接
      3. 关闭 Redis 连接
    """
    settings = get_settings()
    _app.state.settings = settings
    logger.info("FinanceRPA AI 服务启动: version=%s", __version__)

    # 1. 初始化 Redis 客户端（finance-ai 事件总线用）
    redis_client = aioredis.from_url(
        settings.redis_url,
        decode_responses=True,
    )
    _app.state.redis = redis_client
    logger.info("Redis 客户端已创建: url=%s", settings.redis_url)

    # 2. 注入事件总线单例（复用全局 Redis 客户端，由 lifespan 统一关闭）
    event_bus = get_event_bus()
    event_bus._redis = redis_client  # noqa: SLF001 — 注入由 lifespan 管理的 Redis 客户端
    event_bus._owns_redis = False  # noqa: SLF001 — 不由事件总线负责关闭
    logger.info("事件总线已注入 Redis 客户端")

    # 3. 初始化 Skyvern ForgeApp（M3.7）
    # 调用 start_forge_app() 创建并初始化 ForgeApp 实例：
    #   - DATABASE（AgentDB，共用 finrpa 库）
    #   - STORAGE（local 存储，不使用 S3/Azure）
    #   - LLM_API_HANDLER（LLM 视觉决策，需配置 OPENAI_API_KEY）
    #   - BROWSER_MANAGER（RealBrowserManager，Playwright + Chromium）
    #   - AGENT（ForgeAgent，execute_step 感知-动作循环）
    # 初始化后即可通过 Skyvern 原生 API 触发浏览器自动化任务
    try:
        from skyvern.forge.forge_app_initializer import start_forge_app

        start_forge_app()
        logger.info("Skyvern ForgeApp 已初始化（M3.7：LLM 视觉决策 + 浏览器自动化就绪）")

        # M3.8：初始化 Skyvern organization + auth token（供 finance-ai 内部调 Skyvern API）
        from app.clients.skyvern_client import init_skyvern_token

        await init_skyvern_token()
    except Exception:
        # 不阻断 finance-ai 启动，让 health/sse/skills 等自有路由仍可用
        # Skyvern 原生 API 路由会因 forge_app 未初始化而在调用时报错
        logger.exception("Skyvern ForgeApp 初始化失败，Skyvern 原生 API 将不可用")

    yield

    # 4. 关闭 Skyvern persistent browser sessions（M3.7）
    try:
        from skyvern.webeye.default_persistent_sessions_manager import (
            DefaultPersistentSessionsManager,
        )

        await DefaultPersistentSessionsManager.close()
        logger.info("Skyvern persistent browser sessions 已关闭")
    except Exception:
        logger.exception("关闭 Skyvern persistent browser sessions 失败")

    # 5. 关闭 JavaBackendClient 连接
    from app.clients.java_backend import JavaBackendClient

    java_client = JavaBackendClient()
    await java_client.close()

    # 6. 关闭 Redis 连接（事件总线不再自行关闭，由 lifespan 统一管理）
    await redis_client.aclose()
    logger.info("Redis 客户端已关闭")

    logger.info("FinanceRPA AI 服务关闭")


class InternalTokenMiddleware(BaseHTTPMiddleware):
    """内部 API 鉴权中间件（对齐 Java InternalTokenInterceptor）。

    拦截 finance-ai 自有路由 /api/v1/ai/*（排除 /health），
    校验 X-Internal-Token Header 是否匹配配置的共享密钥。

    说明：
      - 仅 Docker 内网可达，不对外暴露
      - Skyvern 原生路由 /v1、/api/v1/tasks、/api/v2/* 由 Skyvern 自身鉴权，本中间件不拦截
      - 健康检查 /api/v1/ai/health 不需鉴权（供 Docker healthcheck 调用）
    """

    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        # 1. 仅拦截 finance-ai 自有路由 /api/v1/ai/*
        if not path.startswith("/api/v1/ai/"):
            return await call_next(request)

        # 2. 公开路径放行（health 等）
        if path.startswith(PUBLIC_PATH_PREFIXES):
            return await call_next(request)

        # 3. 校验 X-Internal-Token Header
        settings = get_settings()
        token = request.headers.get("X-Internal-Token")
        expected = settings.internal_api_token
        if not token or token != expected:
            logger.warning(
                "内部 API 鉴权失败: path=%s, remote=%s",
                path, request.client.host if request.client else "unknown",
            )
            return JSONResponse(
                status_code=401,
                content={"code": 40100, "message": "内部 API 鉴权失败", "data": None},
            )

        # 4. 鉴权通过
        return await call_next(request)


def create_app() -> FastAPI:
    """构造 FastAPI 应用实例。

    M3.7：在 finance-ai 自有路由基础上，挂载 Skyvern 原生 API 路由：
      - base_router → /v1（Skyvern 新版 API）
      - legacy_base_router → /api/v1（Skyvern 旧版 API，含 /api/v1/tasks 等）
      - legacy_v2_router → /api/v2（Skyvern v2 API，含 /api/v2/tasks 等）

    路由共存说明：
      - finance-ai 自有路由：/api/v1/ai/*（health、tasks、sse、skills）—— 由 InternalTokenMiddleware 鉴权
      - Skyvern 原生路由：/api/v1/tasks、/api/v1/workflows 等 —— 由 Skyvern 自身鉴权
      - 两者路径不冲突（/api/v1/ai/* vs /api/v1/tasks），可共存
    """
    settings = get_settings()
    app = FastAPI(
        title="FinanceRPA AI Service",
        description="浏览器自动化与 LLM 编排服务（集成 Skyvern）",
        version=__version__,
        debug=settings.debug,
        lifespan=lifespan,
    )
    # CORS（开发环境放行所有来源）
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    # 内部 API 鉴权中间件（拦截 /api/v1/ai/*，校验 X-Internal-Token）
    app.add_middleware(InternalTokenMiddleware)

    # M3.7：Skyvern context 中间件（每个请求设置 skyvern_context，任务执行必需）
    # @from skyvern/forge/api_app.py:request_middleware
    import uuid

    from skyvern.forge.sdk.core import skyvern_context
    from skyvern.forge.sdk.core.skyvern_context import SkyvernContext

    @app.middleware("http")
    async def skyvern_context_middleware(request: Request, call_next):
        """为每个请求设置 SkyvernContext（Skyvern 任务执行链路依赖此 context）。"""
        curr_ctx = skyvern_context.current()
        if not curr_ctx:
            request_id = str(uuid.uuid4())
            skyvern_context.set(SkyvernContext(request_id=request_id))
        elif not curr_ctx.request_id:
            curr_ctx.request_id = str(uuid.uuid4())
        try:
            return await call_next(request)
        finally:
            skyvern_context.reset()

    # 注册 finance-ai 自有路由
    app.include_router(health.router)
    app.include_router(tasks.router)
    app.include_router(sse.router)
    app.include_router(skills.router)
    app.include_router(risk.router)

    # 挂载 Skyvern 原生 API 路由（M3.7）
    # @from enterprise/skyvern/forge/api_app.py:create_api_app
    try:
        from skyvern.forge.sdk.routes.routers import (
            base_router,
            legacy_base_router,
            legacy_v2_router,
        )

        app.include_router(base_router, prefix="/v1")
        app.include_router(legacy_base_router, prefix="/api/v1")
        app.include_router(legacy_v2_router, prefix="/api/v2")
        logger.info("Skyvern 原生 API 路由已挂载：/v1, /api/v1, /api/v2")
    except ImportError:
        logger.exception("无法导入 Skyvern 路由，Skyvern 原生 API 不可用")

    return app


app = create_app()

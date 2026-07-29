"""FastAPI 应用入口。

M2.4：在 lifespan 中初始化 Redis 客户端并注入事件总线，
应用关闭时统一关闭 Redis 连接，避免资源泄漏。

@author FinanceRPA
"""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import redis.asyncio as aioredis
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import __version__
from app.agent.event_bus import get_event_bus
from app.api import health, sse, tasks
from app.config import get_settings

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    """应用生命周期。

    启动：初始化配置、Redis 客户端、事件总线。
    关闭：关闭 Redis 连接和 JavaBackendClient 连接。
    """
    settings = get_settings()
    _app.state.settings = settings
    logger.info("FinanceRPA AI 服务启动: version=%s", __version__)

    # 1. 初始化 Redis 客户端（用于事件总线 pub/sub）
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

    yield

    # 3. 关闭资源
    from app.clients.java_backend import JavaBackendClient

    java_client = JavaBackendClient()
    await java_client.close()

    # 关闭 Redis 连接（事件总线不再自行关闭，由 lifespan 统一管理）
    await redis_client.aclose()
    logger.info("Redis 客户端已关闭")

    logger.info("FinanceRPA AI 服务关闭")


def create_app() -> FastAPI:
    """构造 FastAPI 应用实例。"""
    settings = get_settings()
    app = FastAPI(
        title="FinanceRPA AI Service",
        description="浏览器自动化与 LLM 编排服务",
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
    # 注册路由
    app.include_router(health.router)
    app.include_router(tasks.router)
    app.include_router(sse.router)
    return app


app = create_app()

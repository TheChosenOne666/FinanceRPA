"""FastAPI 应用入口。"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import __version__
from app.api import health
from app.config import get_settings


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    """应用生命周期。

    M0.1 骨架阶段仅打印启动日志。
    后续模块在此初始化：数据库连接池、Redis 客户端、Playwright 浏览器池、LLM 客户端等。
    """
    settings = get_settings()
    _app.state.settings = settings
    yield
    # 关闭资源（骨架阶段无）


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
    return app


app = create_app()

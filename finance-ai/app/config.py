"""应用配置。

从环境变量或 .env 文件读取配置项。骨架阶段仅包含启动必需项，
后续模块按需扩展（LLM API Key、Playwright 配置、Java 后端地址等）。
"""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """应用配置。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # 服务配置
    app_name: str = "finance-ai"
    app_version: str = "0.0.1"
    debug: bool = False

    # HTTP 服务
    host: str = "0.0.0.0"
    port: int = 8000

    # Java 后端地址（AI 服务回调后端 API）
    backend_base_url: str = "http://localhost:8080"

    # 数据库（M0.2 启用）
    database_url: str = "postgresql+asyncpg://finrpa:finrpa@localhost:5432/finrpa"

    # Redis（M0.2 启用）
    redis_url: str = "redis://localhost:6379/1"

    # LLM（M2 启用）
    llm_provider: str = "openai"
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_model: str = "gpt-4o-mini"


@lru_cache
def get_settings() -> Settings:
    """获取配置单例。"""
    return Settings()

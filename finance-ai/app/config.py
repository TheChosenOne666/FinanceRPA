"""应用配置。

从环境变量或 .env 文件读取配置项。骨架阶段仅包含启动必需项，
后续模块按需扩展（LLM API Key、Playwright 配置、Java 后端地址等）。
"""

from functools import lru_cache

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

    # LLM（M4.1 启用，Planner fallback 版不需要）
    llm_provider: str = "openai"
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_model: str = "gpt-4o-mini"

    # 执行器配置
    executor_max_concurrent: int = 5       # 最大并发任务数
    executor_step_timeout: int = 60        # 单步执行超时（秒）

    # Demo seed
    demo_seed_enabled: bool = True

    # 内部鉴权（服务间通信）
    internal_api_token: str = "finrpa-internal-secret"

    # 审计模块配置（M7.3）
    # 失败请求本地缓存目录（Java 不可用时持久化，恢复后批量上报）
    audit_cache_dir: str = "/tmp/finrpa-audit-cache"


@lru_cache
def get_settings() -> Settings:
    """获取配置单例。"""
    return Settings()

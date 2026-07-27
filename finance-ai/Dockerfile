# finance-ai/Dockerfile
# 基于 uv 官方镜像（Python 3.11 + uv）
# M0.2 骨架阶段不安装 Playwright 浏览器，M2 Skyvern 集成时切换到 mcr.microsoft.com/playwright 镜像

FROM ghcr.io/astral-sh/uv:python3.11-bookworm-slim

WORKDIR /app

# 系统依赖（curl 用于健康检查）
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 先复制依赖文件，利用 Docker 缓存
COPY pyproject.toml uv.lock ./

# 安装依赖（生产模式，不安装 dev 依赖）
RUN uv sync --frozen --no-dev

# 复制源码
COPY app/ ./app/

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

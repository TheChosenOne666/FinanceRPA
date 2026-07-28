# finance-ai/Dockerfile
# M2.1：使用 Playwright 预装镜像（Skyvern 集成时需要，M3 引入 Skyvern 源码后生效）
# 当前 M2.1 fallback 模式不需要浏览器，但提前使用此镜像为后续做准备

FROM mcr.microsoft.com/playwright:v1.49.0-jammy-full

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

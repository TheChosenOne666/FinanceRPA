# finance-ai/Dockerfile
# M3.7：引入 Skyvern 源码，启动 Skyvern ForgeApp 服务
# 参考 finrpa-enterprise Dockerfile：python:3.11-slim-bookworm + playwright install

FROM python:3.11-slim-bookworm

WORKDIR /app

# 安装 uv（从官方镜像复制二进制）
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

# 系统依赖（curl 用于健康检查；Playwright install-deps 会安装 Chromium 运行所需的系统库）
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 先复制依赖文件，利用 Docker 缓存
COPY pyproject.toml ./

# 安装依赖（生产模式，不安装 dev 依赖）
RUN uv sync --no-dev

# 安装 Playwright 浏览器（Chromium）+ 系统依赖
# playwright install-deps 安装 Chromium 运行所需的系统库（libnss3、libatk 等）
# playwright install chromium 下载 Chromium 二进制
RUN uv run playwright install-deps && uv run playwright install chromium

# 复制源码（app + skyvern + alembic 迁移配置）
COPY app/ ./app/
COPY skyvern/ ./skyvern/
COPY alembic/ ./alembic/
COPY alembic.ini ./

# Skyvern 需要的数据目录（视频、HAR、日志、截图等产物）
ENV VIDEO_PATH=/data/videos
ENV HAR_PATH=/data/har
ENV LOG_PATH=/data/log
ENV ARTIFACT_STORAGE_PATH=/data/artifacts
RUN mkdir -p /data/videos /data/har /data/log /data/artifacts

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

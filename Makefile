# FinanceRPA 运维 Makefile
#
# 用法：
#   make help            查看全部命令
#   make dev             一键启动开发环境（docker-compose up -d 全服务）
#   make dev-down        停止开发环境
#   make build           构建全部服务（Java 打包 + 前端打包 + AI 依赖）
#   make test            运行全部测试（后端 / AI / 前端 lint）
#   make seed            导入演示数据（SQL 方式，目标库由 .env 决定）
#   make backup          全量备份（PG dump + MinIO 同步，脚本自动读 .env）
#   make logs            跟踪查看全部服务日志（Ctrl+C 退出）
#   make health          全链路健康检查（依赖脚本 scripts/healthcheck.sh）
#   make clean           清理构建产物
#
# 说明：
#   - 默认使用 docker-compose.yml（开发环境）。生产环境用 PROFILE=prod 切换：
#       make PROFILE=prod dev / make PROFILE=prod backup
#   - 生产 compose 文件为 docker-compose.prod.yml，备份落到 pg-backup / minio-backup 卷。

COMPOSE_DEV  := docker-compose.yml
COMPOSE_PROD := docker-compose.prod.yml
PROFILE      ?= dev
COMPOSE_FILE := $(if $(filter prod,$(PROFILE)),$(COMPOSE_PROD),$(COMPOSE_DEV))

.PHONY: help install dev dev-down build test clean seed backup logs health

help: ## 显示帮助
	@echo "FinanceRPA 运维命令（当前 profile: $(PROFILE)）"
	@echo ""
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

install: ## 安装所有依赖（后端 / AI / 前端）
	cd finance-backend && ./mvnw.cmd -N dependency:resolve
	cd finance-ai && uv sync
	cd finance-frontend && npm install

dev: ## 一键启动 $(PROFILE) 环境全部服务
	docker-compose -f $(COMPOSE_FILE) up -d
	@echo ">> 等待服务就绪后可用 'make health' 校验全链路"

dev-down: ## 停止 $(PROFILE) 环境全部服务
	docker-compose -f $(COMPOSE_FILE) down

build: ## 构建所有服务
	cd finance-backend && ./mvnw.cmd clean package -DskipTests
	cd finance-frontend && npm run build
	cd finance-ai && uv sync

test: ## 运行所有测试
	cd finance-backend && ./mvnw.cmd test
	cd finance-ai && uv run pytest
	cd finance-frontend && npm run lint

clean: ## 清理构建产物
	cd finance-backend && ./mvnw.cmd clean
	cd finance-frontend && rm -rf dist node_modules
	cd finance-ai && rm -rf .venv

seed: ## 导入演示数据（sys_user / sys_user_role 等）
	./scripts/seed_demo_data.py

backup: ## 全量备份（PG dump + MinIO 同步）
	./scripts/backup.sh

logs: ## 跟踪查看全部服务日志
	docker-compose -f $(COMPOSE_FILE) logs -f

health: ## 全链路健康检查
	./scripts/healthcheck.sh

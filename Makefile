.PHONY: help install dev-backend dev-ai dev-frontend build test clean seed

help: ## 显示帮助
	@echo "FinanceRPA 开发命令"
	@echo ""
	@echo "  make install        安装所有依赖"
	@echo "  make dev-backend    启动 Java 后端 (8080)"
	@echo "  make dev-ai         启动 Python AI 服务 (8000)"
	@echo "  make dev-frontend   启动前端 (8081)"
	@echo "  make build          构建所有服务"
	@echo "  make test           运行所有测试"
	@echo "  make clean          清理构建产物"
	@echo "  make seed           导入演示数据"

install: ## 安装所有依赖
	cd finance-backend && ./mvnw.cmd -N dependency:resolve
	cd finance-ai && uv sync
	cd finance-frontend && npm install

dev-backend: ## 启动 Java 后端
	cd finance-backend && ./mvnw.cmd spring-boot:run

dev-ai: ## 启动 Python AI 服务
	cd finance-ai && uv run uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

dev-frontend: ## 启动前端
	cd finance-frontend && npm run dev

build: ## 构建所有服务
	cd finance-backend && ./mvnw.cmd clean package -DskipTests
	cd finance-frontend && npm run build

test: ## 运行所有测试
	cd finance-backend && ./mvnw.cmd test
	cd finance-ai && uv run pytest
	cd finance-frontend && npm run lint

clean: ## 清理构建产物
	cd finance-backend && ./mvnw.cmd clean
	cd finance-frontend && rm -rf dist node_modules
	cd finance-ai && rm -rf .venv

seed: ## 导入演示数据
	docker cp finance-backend/src/main/resources/db/seed/demo_data.sql finrpa-postgres:/tmp/demo_data.sql
	docker-compose exec postgres psql -U finrpa -d finrpa -f /tmp/demo_data.sql

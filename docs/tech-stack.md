# FinanceRPA 技术选型文档

> 基于 Skyvern 二次开发的金融级 AI 浏览器自动化平台
> 后端 Java + AI 服务 Python 的跨语言架构

| 项 | 内容 |
|----|------|
| 文档版本 | v1.1 |
| 创建日期 | 2026-07-25 |
| 文档状态 | 初稿，待评审 |
| 关联文档 | [requirements.md](./requirements.md) |
| 参考项目 | finrpa-enterprise (D:\lingou-projects\tempgithub\finrpa-enterprise) |

> **文档范围说明**：本文档仅覆盖技术栈选型与关键决策依据，不展开架构详设、数据模型详设、模块交互序列图等设计阶段内容，将在系统设计文档中单独阐述。

---

## 1. 选型原则

| 原则 | 说明 |
|------|------|
| **金融合规优先** | 满足私有化部署、全链路审计、职责分离、数据不出内网 |
| **对齐原项目能力** | 功能 100% 对齐 finrpa-enterprise，技术栈替换不损失能力 |
| **跨语言解耦** | Java 后端与 Python AI 服务通过网络 API 解耦，规避 AGPL-3.0 传染 |
| **生态成熟稳定** | 优先选择 LTS 版本与主流社区方案，规避小众框架风险 |
| **国产化友好** | 选型需兼容未来信创适配（麒麟/UOS、达梦/人大金仓） |
| **单节点可运维** | Docker Compose 一键部署，运维成本可控 |

---

## 2. 技术栈总览

> 非 Java 部分（AI Agent 底座、ORM & 数据库、缓存 & 消息、对象存储、认证授权模型、前端、容器化、反向代理、数据库迁移、测试）与原项目完全对齐；Java 部分独立选型。

| 层次 | 技术 | 版本/备注 |
|------|------|-----------|
| **AI Agent 底座** | Skyvern + Playwright | 视觉理解 + 浏览器自动化（沿用原项目） |
| **Java 后端** | Spring Boot | 3.2.x |
| **JDK** | OpenJDK / Temurin | 21 LTS |
| **Java ORM** | MyBatis-Plus | 3.5.x |
| **Java 迁移** | Flyway | 10.x（管 `enterprise_*`） |
| **后端框架（Python AI 服务）** | FastAPI + Python 3.11 | 沿用原项目 |
| **Python ORM** | SQLAlchemy + Alembic | 2.0 + 1.13（管 `skyvern_*`） |
| **LLM 网关** | LiteLLM + OpenAI SDK + Anthropic SDK | 多 LLM 提供商（沿用原项目） |
| **ORM & 数据库** | MyBatis-Plus + SQLAlchemy + PostgreSQL 14 | 共享实例，分表归属 |
| **缓存 & 消息** | Redis 7.x（Pub/Sub + 结果缓存） | Java 用 Redisson，Python 用 redis-py |
| **对象存储** | MinIO（私有化截图存储） | latest |
| **认证授权** | JWT + 三维度 RBAC（Java 侧 Spring Security 6 + jjwt + BCrypt + Fernet） | 与原项目算法兼容 |
| **前端** | React 18 + TypeScript + ECharts + 手写 SVG 图标 | Vite + Tailwind + Zustand + React Query |
| **容器化** | Docker Compose（开发 + 生产双配置） | Docker 24+ / Compose v2 |
| **反向代理** | Nginx（gzip + 安全头 + HTTPS 预留） | 1.25+ |
| **数据库迁移** | Flyway（Java）+ Alembic（Python） | 双工具分表归属 |
| **测试** | JUnit 5 + pytest + pytest-asyncio + Vitest + happy-dom | Java + Python + 前端 |
| **构建工具** | Maven（Java）/ uv（Python）/ npm（前端） | - |

---

## 3. 后端 Java 选型详述

### 3.1 框架与运行时

| 项 | 选型 | 理由 |
|----|------|------|
| JDK | **Java 21 LTS** | 最新 LTS，虚拟线程（Virtual Threads）支撑浏览器并发会话的高并发请求；模式匹配、记录类提升代码表达力；Spring Boot 3.x 基线要求 Java 17+ |
| 框架 | **Spring Boot 3.2.x** | 生态最成熟、金融行业事实标准；原生支持 Java 21 虚拟线程；对 Jakarta EE 9+ 命名空间兼容；社区文档与人才储备充分 |
| Web 模型 | **Spring Web MVC + 虚拟线程** | RPA 业务以阻塞 IO 为主（DB、HTTP 调 Python），虚拟线程下阻塞模型吞吐已足够；规避 WebFlux 反应式编程的心智负担与调试复杂度 |
| HTTP 客户端 | **Spring 6 HTTP Interface（声明式） + RestClient** | 替代 RestTemplate，类型安全；调用 Python AI 服务时基于接口注解生成客户端 |
| JSON | **Jackson + jackson-databind** | Spring Boot 默认，性能稳定；配合 JavaTimeModule 处理时间序列化 |

### 3.2 数据访问

| 项 | 选型 | 理由 |
|----|------|------|
| ORM | **MyBatis-Plus 3.5.x** | SQL 可控、复杂查询灵活；动态条件构造器天然适配三维度 RBAC 的部门/业务线/角色过滤；国内金融业主流，团队熟悉度高 |
| 数据库连接池 | **HikariCP** | Spring Boot 默认，性能与稳定性平衡；连接池参数配合虚拟线程调整 |
| 多租户隔离 | **MyBatis-Plus TenantLineInnerInterceptor** | 自动追加 `organization_id` 过滤，与原项目 SQLAlchemy query_filter 对齐 |
| 读写分离 | 暂不引入 | 单节点部署无需；保留扩展空间 |

### 3.3 数据库迁移（Java 侧）

| 项 | 选型 | 理由 |
|----|------|------|
| 迁移工具 | **Flyway 10.x** | Java 生态标准，SQL-first 思路清晰；与 MyBatis-Plus 兼容性好；版本化脚本可审计 |
| 表归属 | Java 侧管理 `enterprise_*` 前缀表 | 包括 auth、tenant、approval、audit、dashboard、llm、agent、skills、workflows、notification 等企业模块表 |
| 命名规范 | `V{date}__{module}__{action}.sql`，例如 `V20260725_001__auth__init_schema.sql` | 可追溯、按模块组织 |
| 禁止项 | Java 侧 Flyway 不得创建/修改 `skyvern_*` 前缀表 | 边界严格隔离 |

### 3.4 安全与认证

| 项 | 选型 | 理由 |
|----|------|------|
| 安全框架 | **Spring Security 6.x** | Spring Boot 3 原生集成，过滤器链清晰；与 JWT、方法级注解无缝 |
| JWT | **jjwt 0.12+** | 活跃维护、API 现代；支持 access + refresh 双 token |
| 密码加密 | **BCryptPasswordEncoder** | 行业标准，与原项目 passlib[bcrypt] 算法兼容，可直接复用演示账号 |
| 敏感参数加密 | **Fernet 对称加密**（Java 侧用 Fernet-java 实现） | 与原项目 Python cryptography 库 Fernet 算法字节级兼容，工作流模板加密参数可跨语言读写 |
| CSRF | **SameSite Cookie + 双提交 Token** | 满足金融合规要求 |
| 跨域 | **Spring CORS 配置** | 仅在开发环境开放，生产环境由 Nginx 同源代理 |

### 3.5 缓存与异步

| 项 | 选型 | 理由 |
|----|------|------|
| Redis 客户端 | **Redisson 3.x** | 内置分布式锁、Pub/Sub、限流、布隆过滤器；与原项目 approval pubsub 能力对齐 |
| 本地缓存 | **Caffeine** | Spring Cache 抽象，二级缓存基础 |
| 任务调度 | **ShedLock + Spring Scheduler** | 单节点部署可用；为未来多节点扩展预留；定时刷新 dashboard 统计、清理过期缓存 |
| 异步执行 | **Spring `@Async` + 虚拟线程 TaskExecutor** | Java 后端轻量异步（如发通知、写审计）走虚拟线程；重计算/浏览器操作由 Python 侧承接 |

### 3.6 API 文档与校验

| 项 | 选型 | 理由 |
|----|------|------|
| API 文档 | **springdoc-openapi 2.x** | Spring Boot 3 原生支持，OpenAPI 3 规范；自动生成 Swagger UI |
| 参数校验 | **Jakarta Bean Validation 3.0（Hibernate Validator）** | 注解式校验，与 Pydantic 等价能力 |
| 全局异常 | `@RestControllerAdvice` 统一封装 | 错误码体系化，对接前端友好 |

---

## 4. AI 服务 Python 选型详述

### 4.1 框架与运行时

| 项 | 选型 | 理由 |
|----|------|------|
| Python 版本 | **CPython 3.11.x** | 与原项目 `requires-python = ">=3.11,<3.14"` 对齐；性能与生态平衡 |
| Web 框架 | **FastAPI + Uvicorn** | 与原项目一致，原生 async、Pydantic 校验、自动 OpenAPI |
| ASGI 服务器 | **Uvicorn（多 worker）** | 单进程多 worker 应对并发浏览器会话 |

### 4.2 核心依赖（对齐原项目）

| 类别 | 依赖 | 用途 |
|------|------|------|
| 浏览器自动化 | playwright 1.46+ | 页面驱动、截图、下载 |
| 数据库 | sqlalchemy 2.0 + psycopg[binary] + asyncpg | ORM + 异步驱动 |
| 迁移 | alembic 1.13+ | skyvern_* 表迁移 |
| LLM 网关 | litellm 1.63+ | 多 LLM 提供商统一接口 |
| LLM SDK | openai 1.66+ / anthropic 0.50+ | 直连供应商兜底 |
| 数据模型 | pydantic 2.6+ / pydantic-settings 2.2+ | 强类型校验 |
| HTTP 客户端 | httpx 0.27+ | 调用 Java 后端 API（回调、状态同步） |
| 异步文件 | aiofiles 23+ / aiohttp 3.9+ | 异步 IO |
| Redis | redis 5.0+（async） | Pub/Sub 订阅 approval |
| MinIO | minio 7.2+ | 截图上传 |
| 日志 | structlog 24+ | 结构化 JSON 日志 |

### 4.3 表归属

- Python 侧 Alembic 仅管理 `skyvern_*` 前缀表（任务、workflow、artifact、持久化 context 等）
- Python 侧对 `enterprise_*` 表只读访问，通过 SQLAlchemy 反射或显式视图模型，**不得创建或修改其 schema**
- 跨侧数据共享优先走 HTTP API（Java 暴露 → Python 调用），避免直接跨侧写表

---

## 5. 前端选型

> 与原项目 skyvern-frontend 完全对齐：React 18 + TypeScript + ECharts + 手写 SVG 图标，复用其毛玻璃 UI 与全部组件。图标采用手写 SVG 组件，不依赖外部图标库（如 lucide-react / heroicons）。

| 项 | 选型 | 版本 |
|----|------|------|
| 框架 | React + TypeScript | 18.x / 5.5.x |
| 构建 | Vite + @vitejs/plugin-react-swc | 5.x |
| 路由 | react-router-dom | 6.30+ |
| **图标** | **手写 SVG 图标组件**（BagIcon / BookIcon / BrainIcon / BugIcon / CartIcon / ClickIcon / DebugIcon / FolderIcon / GraphIcon / InboxIcon / KeyIcon / OutputIcon / PowerIcon / QRCodeIcon / RobotIcon / SaveIcon / ToolIcon） | - |
| 基础组件 | Radix UI primitives + shadcn/ui 风格自实现 | - |
| 样式 | Tailwind CSS + tailwindcss-animate | 3.4.x |
| 状态 | Zustand（全局） + React Query（服务端状态） | 4.5.x / 5.x |
| 表单 | react-hook-form + zod | 7.51+ / 3.22+ |
| 图表 | ECharts + echarts-for-react | 6.x / 3.x |
| 工作流编排 | @xyflow/react | 12.x |
| SSE 客户端 | @microsoft/fetch-event-source | 2.x |
| 浏览器流 | @novnc/novnc | 1.5.x |
| 国际化 | 自实现轻量 i18n（沿用原项目 locales.ts） | - |
| 测试 | Vitest + @testing-library/react + happy-dom | 3.x / 16.x |
| 代码规范 | ESLint + Prettier + prettier-plugin-tailwindcss | 8.x / 3.x |

---

## 6. 数据存储与中间件

| 中间件 | 选型 | 版本 | 用途 |
|--------|------|------|------|
| 关系数据库 | PostgreSQL | 14-alpine | 主数据存储，Java + Python 共享 |
| 缓存 & 消息 | Redis | 7.x（Pub/Sub + 结果缓存） | 审批 Pub/Sub、Action 缓存、Dashboard 统计缓存、Token 黑名单 |
| 对象存储 | MinIO | latest（私有化截图存储） | 审计截图、下载文件、Workflow 产物 |

> **共享 PostgreSQL 实例**：Java 与 Python 共用同一 PostgreSQL，通过表前缀（`enterprise_*` / `skyvern_*`）+ 独立迁移工具实现 schema 隔离。应用层不共用连接池。

---

## 7. 跨语言通信

### 7.1 协议选型

| 模式 | 协议 | 场景 |
|------|------|------|
| 同步调用 | **HTTP REST + JSON** | Java → Python：触发任务、查询状态、调用 Skill；Python → Java：状态回调、审计写入 |
| 流式推送 | **SSE（Server-Sent Events）** | Python → Java → 前端：浏览器实时流、任务执行进度、LLM 推理过程 |
| 消息广播 | **Redis Pub/Sub** | 审批请求/响应跨进程通知，与原项目 approval:requests / approval:responses channel 对齐 |

### 7.2 通信契约

| 项 | 选型 | 理由 |
|----|------|------|
| API 契约来源 | **OpenAPI 3.0** | Java 用 springdoc 生成、Python 用 FastAPI 自动生成；可考虑契约优先模式但本期从简 |
| Java 调 Python | Spring 6 HTTP Interface（声明式接口） | 类型安全、易测试 |
| Python 调 Java | httpx + Pydantic 模型 | 与原项目风格一致 |
| 错误传播 | 标准 HTTP 状态码 + 业务错误码 JSON 体 | 双侧统一错误模型 |
| 超时与重试 | Java 侧 Spring Retry；Python 侧 tenacity | 默认连接 5s / 读取 30s；浏览器长任务用 SSE 旁路 |
| 链路追踪 | W3C Trace Context（traceparent header）透传 | 未来接入 OpenTelemetry 时可贯通 |

### 7.3 流式数据流

```
Python (Playwright + LLM)
  └─ SSE 推送 step / screenshot / reasoning
       └─ Java SSE Gateway（透传 + 鉴权 + 审计）
            └─ Nginx SSE 透传
                 └─ 前端 EventSource 消费
```

---

## 8. 数据库迁移策略（双工具）

### 8.1 边界划分

| 工具 | 管理范围 | 命名前缀 |
|------|----------|----------|
| **Flyway（Java）** | 企业模块表 | `enterprise_*` |
| **Alembic（Python）** | Skyvern 核心表 | `skyvern_*` |

### 8.2 共享规则

1. **不跨界修改**：Java 侧 Flyway 脚本禁止 `CREATE/ALTER` 任何 `skyvern_*` 表；Python 侧 Alembic 同理禁止触碰 `enterprise_*`。
2. **只读访问**：跨侧只读通过 ORM 反射或视图模型，禁止写入对方表。
3. **跨侧数据流动**：通过 HTTP API，例如 Python 调 Java `/audit/logs` 写审计；Java 调 Python `/tasks/{id}/state` 查任务状态。
4. **初始化顺序**：Docker Compose 启动时 PostgreSQL 就绪 → Java 服务启动（Flyway 执行 enterprise_* 迁移） → Python 服务启动（Alembic 执行 skyvern_* 迁移）。
5. **共享枚举与字典**：跨侧枚举值（如 risk_level、role）通过 OpenAPI 契约同步，避免硬编码漂移。

### 8.3 应急修订流程

- 紧急 schema 变更须由对应侧 owner 发起 PR，经评审后合并；另一方通过 ORM 模型同步更新。
- 数据回滚脚本由各自迁移工具的 rollback 命令承担，禁用跨工具手动 SQL。

---

## 9. 构建与测试

### 9.1 构建

| 端 | 工具 | 产物 |
|----|------|------|
| Java | **Maven 3.9+**（多模块 pom） | `finance-backend.jar`（可执行 fat jar） |
| Python | **uv**（推荐）或 pip + hatchling | wheel 包 + Docker 镜像 |
| 前端 | **npm + Vite** | 静态资源 → Nginx 容器 |

### 9.2 测试

| 端 | 框架 | 覆盖率目标 |
|----|------|------------|
| Java | **JUnit 5 + Mockito + AssertJ + TestContainers** | 单元 ≥ 80% / 集成 ≥ 60% |
| Python | **pytest + pytest-asyncio + pytest-cov + fakeredis** | 对齐原项目 ≥ 85% |
| 前端 | **Vitest + @testing-library/react + happy-dom** | 组件覆盖关键路径 |
| 端到端 | Playwright（复用） | 6 个金融场景工作流模板 |

### 9.3 静态检查

| 端 | 工具 |
|----|------|
| Java | **Checkstyle（阿里巴巴 Java 开发手册）+ SpotBugs + JaCoCo** |
| Python | **ruff + mypy + isort**（沿用原项目） |
| 前端 | **ESLint + Prettier + TypeScript strict mode** |

---

## 10. 可观测性

| 维度 | Java 侧 | Python 侧 | 前端 |
|------|---------|-----------|------|
| 日志 | SLF4J + Logback + logstash-logback-encoder（JSON） | structlog（JSON） | console |
| 指标 | Micrometer + Prometheus actuator endpoint | prometheus-client | posthog（用户行为） |
| 链路追踪 | OpenTelemetry SDK（可选接入） | OpenTelemetry SDK | - |
| 健康检查 | Spring Boot Actuator `/actuator/health` | FastAPI `/health` | Nginx `/healthz` |
| 浏览器流 | SSE 透传 | Playwright 截图 / 视频 / HAR | EventSource 消费 |

---

## 11. 部署与运维

| 项 | 选型 |
|----|------|
| 容器 | Docker 24+ |
| 编排 | Docker Compose v2（开发 + 生产双配置：`docker-compose.yml` + `docker-compose.prod.yml`） |
| 反向代理 | Nginx 1.25+（gzip + 安全头 + HTTPS 预留 + SSE 透传 + 静态资源） |
| 镜像源 | Java: eclipse-temurin:21-jre-alpine；Python: python:3.11-slim；前端: nginx:alpine |
| 配置管理 | 环境变量 + `.env` 文件（生产由部署平台注入） |
| 密钥管理 | 环境变量注入；Fernet 主密钥独立配置项 |
| 日志卷 | `/data/log` 挂载到宿主机 |
| 数据卷 | `postgres-data` / `redis-data` / `minio-data` named volume |
| 健康检查 | Docker Compose healthcheck 全链路覆盖 |
| 优雅停机 | Spring Boot graceful shutdown + Uvicorn `--timeout-graceful-shutdown` |

---

## 12. 与原项目技术栈对照

| 维度 | 原项目 (finrpa-enterprise) | 本项目 |
|------|----------------------------|--------|
| 后端语言 | Python 3.11 | **Java 21** |
| 后端框架 | FastAPI | **Spring Boot 3.2** |
| ORM | SQLAlchemy 2.0 | **MyBatis-Plus 3.5** |
| 迁移 | Alembic（全量） | **Flyway（enterprise_*）+ Alembic（skyvern_*）** |
| 鉴权 | python-jose + passlib | **jjwt + Spring Security BCrypt** |
| 安全框架 | FastAPI Depends | **Spring Security 6** |
| API 文档 | FastAPI 自动 OpenAPI | **springdoc-openapi 2** |
| AI 服务 | 与后端同进程 | **独立 Python 服务（FastAPI）** |
| 浏览器 | Playwright | Playwright（不变） |
| LLM | litellm + openai + anthropic | litellm + openai + anthropic（不变） |
| 数据库 | PostgreSQL 14 | PostgreSQL 14（不变） |
| 缓存 | redis-py 5 | **Redisson 3** |
| 对象存储 | minio 7.2 | minio 7.2（不变） |
| 反向代理 | Nginx | Nginx（不变） |
| 前端 | React 18 + Vite + Radix + Tailwind | 同原项目 |
| 测试 | pytest / Vitest | JUnit 5 / pytest / Vitest |
| 容器 | Docker Compose | Docker Compose（不变） |

---

## 13. 关键决策记录（ADR 摘要）

### ADR-001 后端语言切换为 Java

- **背景**：原项目为纯 Python，企业级场景需 Java 生态成熟度与团队储备。
- **决策**：后端企业模块用 Java 21 + Spring Boot 3.2 重写；AI 与浏览器自动化保留 Python。
- **代价**：跨语言通信引入网络开销；通过 HTTP REST + SSE 满足。

### ADR-002 双迁移工具分表归属

- **背景**：Java 与 Python 共用 PostgreSQL，迁移工具不同。
- **决策**：Flyway 管 `enterprise_*`，Alembic 管 `skyvern_*`，互不跨界。
- **代价**：跨侧数据共享需走 HTTP API，禁止直写对方表。

### ADR-003 ORM 选用 MyBatis-Plus

- **背景**：金融场景 SQL 复杂，三维度 RBAC 动态过滤需求强。
- **决策**：采用 MyBatis-Plus 3.5，配合 TenantLineInnerInterceptor 实现自动租户过滤。
- **代价**：相比 JPA 对象关系映射能力弱，但金融行业 SQL 控制需求更高。

### ADR-004 Java ↔ Python 通信采用 HTTP REST + SSE

- **背景**：Skyvern 原生 HTTP API，跨语言解耦需规避 AGPL 传染。
- **决策**：同步走 HTTP REST，流式走 SSE，消息广播走 Redis Pub/Sub。
- **代价**：性能不如 gRPC，但调试简单、与 Skyvern 兼容、跨语言零成本。

### ADR-005 JDK 选用 Java 21 LTS

- **背景**：Spring Boot 3 基线要求 Java 17+，未来并发需求高。
- **决策**：采用 Java 21 LTS，启用虚拟线程处理浏览器会话并发。
- **代价**：部分老旧依赖未适配 Java 21，需选型时验证。

### ADR-006 Java 安全框架选用 Spring Security 6

- **背景**：JWT 鉴权 + 方法级权限 + CSRF 防护需要统一框架。
- **决策**：采用 Spring Security 6 + jjwt + BCrypt；Fernet 跨语言兼容敏感参数加密。
- **代价**：Spring Security 学习曲线较陡，但生态与文档完善。

---

## 14. 待定与未来演进

| 项 | 当前决策 | 演进方向 |
|----|----------|----------|
| 多节点集群 | 单节点 Docker Compose | Kubernetes 编排，Redisson 分布式锁可平滑支撑 |
| 工作流引擎 | 自实现轻量状态机 | 引入 Camunda / Temporal 支持复杂编排 |
| 消息队列 | Redis Pub/Sub | 升级 RocketMQ / Kafka 支持高吞吐与持久化 |
| 信创适配 | 标准 OpenJDK / PostgreSQL | 切换毕昇 JDK / 达梦 / 人大金仓 |
| 多租户 SaaS | 私有化单租户 | 引入库级隔离或 schema 级隔离 |
| 服务网格 | 无 | 引入 Istio 支持熔断、限流、链路追踪 |
| LLM 私有化 | OpenAI / Anthropic / Azure | 接入本地 Ollama / vLLM / 国产大模型 |
| API 契约管理 | 文档驱动 | 引入契约优先（OpenAPI 生成双侧客户端） |

---

## 15. 附录

### 15.1 选型决策一览

> 与原项目对齐的非 Java 部分（AI Agent 底座、后端框架、ORM & 数据库、缓存 & 消息、对象存储、认证授权、前端、容器化、反向代理、数据库迁移、测试）保持一致；Java 部分独立选型。

| 维度 | 选型 |
|------|------|
| **AI Agent 底座** | Skyvern + Playwright |
| **后端框架（Python AI 服务）** | FastAPI + Python 3.11 |
| **ORM & 数据库** | SQLAlchemy 2.0 + PostgreSQL 14（Python） / MyBatis-Plus 3.5（Java） |
| **缓存 & 消息** | Redis 7.x（Pub/Sub + 结果缓存） |
| **对象存储** | MinIO（私有化截图存储） |
| **认证授权** | JWT + 三维度 RBAC |
| **前端** | React 18 + TypeScript + ECharts + 手写 SVG 图标 |
| **容器化** | Docker Compose（开发 + 生产双配置） |
| **反向代理** | Nginx（gzip + 安全头 + HTTPS 预留） |
| **数据库迁移** | Alembic（Python） + Flyway（Java） |
| **测试** | pytest + pytest-asyncio + Vitest + happy-dom + JUnit 5 |
| JDK | Java 21 LTS |
| Java 框架 | Spring Boot 3.2.x |
| Java Web | Spring Web MVC + 虚拟线程 |
| Java ORM | MyBatis-Plus 3.5 |
| Java 迁移 | Flyway 10 |
| Java 安全 | Spring Security 6 + jjwt + BCrypt + Fernet |
| Java 缓存 | Redisson 3 + Caffeine |
| Java 测试 | JUnit 5 + Mockito + AssertJ + TestContainers |
| Java 构建 | Maven 3.9 |
| Python AI 栈 | LiteLLM + OpenAI SDK + Anthropic SDK |
| 跨语言通信 | HTTP REST + SSE + Redis Pub/Sub |

### 15.2 修订记录

| 版本 | 日期 | 修订人 | 说明 |
|------|------|--------|------|
| v1.0 | 2026-07-25 | - | 初稿，覆盖后端 Java、AI 服务 Python、前端、存储、通信、迁移、安全、测试、可观测、部署等维度，附原项目对照与 ADR 摘要 |
| v1.1 | 2026-07-25 | - | 非 Java 部分对齐原项目技术栈表述：新增「AI Agent 底座 Skyvern + Playwright」层次；前端强调「手写 SVG 图标」；缓存 & 消息、对象存储、容器化、反向代理、数据库迁移、测试等描述与原项目保持一致 |

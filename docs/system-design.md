# FinanceRPA 系统设计文档

> 基于 Skyvern 二次开发的金融级 AI 浏览器自动化平台
> Java 后端 + Python AI 服务 的跨语言架构方案设计

| 项 | 内容 |
|----|------|
| 文档版本 | v1.0 |
| 创建日期 | 2026-07-25 |
| 文档状态 | 初稿，待评审 |
| 关联文档 | [requirements.md](./requirements.md) · [tech-stack.md](./tech-stack.md) |
| 参考项目 | finrpa-enterprise (D:\lingou-projects\tempgithub\finrpa-enterprise) |

> **文档范围说明**：本文档覆盖整体架构、模块划分、项目目录结构、跨语言协作、核心模块详设、关键流程、数据模型概要、API 划分与部署架构。数据模型详设（字段级 DDL）、API 字段级契约、序列图等留待详细设计文档。

---

## 1. 设计目标与原则

### 1.1 设计目标

1. **功能对齐**：100% 复刻 finrpa-enterprise 全部 9 大企业模块 + Skyvern 核心 AI 能力。
2. **跨语言解耦**：Java 后端负责企业管理与编排，Python AI 服务负责浏览器与 LLM 执行，通过网络 API 解耦，规避 AGPL-3.0 传染。
3. **边界清晰**：每个模块有明确的归属语言与职责边界，禁止跨侧直写对方表。
4. **可独立演进**：Java 与 Python 服务可独立部署、独立扩展、独立迭代。
5. **演示完备**：5 种角色 × 4 种业务场景演示数据，Docker Compose 一键启动。

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **管理 vs 执行分离** | Java 管"配置 / 状态 / 检索 / 编排"，Python 管"浏览器 / LLM / Skill 执行" |
| **数据权威单一** | 每张表只有一个写入方，跨侧通过 API 同步，禁止双写 |
| **状态外置** | Python AI 服务无状态（除浏览器上下文），任务状态持久化到 PostgreSQL |
| **失败可恢复** | 所有长任务支持断点续跑，LLM 失败转 NEEDS_HUMAN 兜底 |
| **最小改动 Skyvern** | Skyvern 核心代码改动通过 volume 挂载或独立模块隔离，便于升级 |

---

## 2. 整体架构

### 2.1 架构总览

```
┌────────────────────────────────────────────────────────────────────┐
│                         客户端 (Web 浏览器)                          │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ HTTPS
┌──────────────────────────────▼─────────────────────────────────────┐
│              Nginx 反向代理 (gzip + 安全头 + HTTPS 预留)              │
│         /api/v1/*  → Java 后端      /api/v1/ai/*  → Python AI        │
│         /            → 前端静态资源   /sse/*        → SSE 透传        │
└──────────────┬───────────────────────────────┬────────────────────┘
               │                               │
┌──────────────▼──────────────┐  ┌─────────────▼─────────────────────┐
│   Java 后端 (finance-backend) │  │   Python AI 服务 (finance-ai)      │
│   Spring Boot 3.2 + Java 21   │  │   FastAPI + Python 3.11            │
│   ┌────────────────────────┐ │  │   ┌─────────────────────────────┐  │
│   │ auth    tenant  approval│ │  │   │ Skyvern 核心 (Playwright)    │  │
│   │ audit   dashboard llm  │◄┼──┼──┤│ Planner + Executor 双 Agent │  │
│   │ agent   skills workflows│ │  │   │ 7 个 Skill 实现              │  │
│   │ notification  ai-client │ │  │   │ LLM 三层容错 + Action 缓存   │  │
│   └────────────────────────┘ │  │   │ LLM 风险二次判断             │  │
└──────────┬───────────────────┘  └───┬─────────────┬───────────────┘
           │                          │             │
           │   HTTP REST + SSE        │             │
           └──────────────────────────┘             │
                                                     │
┌────────────────────────────────────────────────────▼───────────────┐
│                       数据层 (共享)                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐ │
│  │ PostgreSQL 14    │  │ Redis 7.x        │  │ MinIO            │ │
│  │ enterprise_*     │  │ Pub/Sub + 缓存   │  │ 私有化截图存储    │ │
│  │ skyvern_*        │  │ Action 缓存      │  │ 审计截图 + 下载   │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

### 2.2 服务边界划分

| 服务 | 语言 | 端口 | 职责 |
|------|------|------|------|
| **finance-backend** | Java 21 | 8080 | 企业 API 网关、认证授权、审批流编排、审计检索、Dashboard、配置管理、任务状态权威 |
| **finance-ai** | Python 3.11 | 8000 | Skyvern 浏览器自动化、Planner/Executor 执行、Skill 实现、LLM 调用、Action 缓存、LLM 风险判断 |
| **finance-frontend** | TypeScript | 8081 (dev) / 80 (prod) | React SPA，对接 Java 后端 |
| **PostgreSQL** | - | 5432 | 共享数据库，分表归属 |
| **Redis** | - | 6379 | Pub/Sub + 缓存 |
| **MinIO** | - | 9000/9001 | 对象存储 |

### 2.3 数据流概览

```
任务执行主链路：
  前端 → Java(鉴权+租户+审批编排) → Python(Planner+Executor+Skill+LLM)
                                       │
                                       ├─ SSE 推送进度 → Java 透传 → 前端
                                       ├─ HTTP 回调写审计 → Java → MinIO/PG
                                       └─ HTTP 回调写状态 → Java → PG

审批流旁路：
  Python(检测高风险) → Java(创建审批单) → Redis Pub/Sub → 通知审批员
                                                       ↓
  Python(等待结果) ← Redis Pub/Sub ← Java(审批员批准/拒绝)

LLM 调用链：
  Python(Skill 需要 LLM) → 模型路由 → Action 缓存命中?
                             │                ↓ 是
                             │ 否             直接返回缓存
                             ↓
                          LLM 调用 → Prompt 约束 → Pydantic 校验
                             ↓ 失败
                          重试 2 次 → 仍失败 → NEEDS_HUMAN
```

---

## 3. 模块划分与职责

### 3.1 模块归属矩阵（核心决策）

> 原则：Java 管"管理 / 状态 / 编排 / 检索"，Python 管"浏览器 / LLM / Skill 执行"。

| 模块 | Java 后端 | Python AI 服务 | 职责拆分说明 |
|------|:---------:|:--------------:|--------------|
| **auth** | ✅ 全部 | ❌ | JWT 签发/校验、三维度 RBAC 权限解析、角色互斥约束、与 Skyvern 原生认证桥接 |
| **tenant** | ✅ 全部 | ❌ | 多租户上下文注入、SQL 自动追加 `organization_id` 过滤 |
| **approval** | ✅ 主导 | 🔶 协作 | Java：关键词预筛（规则引擎）+ 审批流路由 + Pub/Sub 协调 + 审批 CRUD；Python：LLM 二次风险判断 |
| **audit** | ✅ 主导 | 🔶 协作 | Java：日志 CRUD + 脱敏 + MinIO 上传 + CSV 导出 + 多维检索；Python：执行时上报操作元数据 + 截图上传回调 |
| **dashboard** | ✅ 全部 | ❌ | 统计 API + Redis 缓存 + 趋势分析 |
| **llm** | ✅ 管理 | 🔶 执行 | Java：Action 缓存统计/清理 + NEEDS_HUMAN 队列管理 + 模型路由策略配置；Python：三层容错调用 + 缓存读写 + 模型路由执行 |
| **agent** | ✅ 状态 | 🔶 执行 | Java：CoordinationState 持久化 + 任务状态机 + 断点续跑状态查询；Python：Planner 规划 + Executor 执行 + replan |
| **skills** | ✅ 元数据 | 🔶 实现 | Java：Skill 注册/CRUD/版本管理；Python：7 个 Skill 实现 + Pipeline 执行器 |
| **workflows** | ✅ 主导 | 🔶 执行 | Java：模板 CRUD + 参数加密 + 校验 + 触发执行；Python：Skill 编排执行 + 参数映射 |
| **notification** | ✅ 全部 | ❌ | 企业微信/钉钉 Webhook + 通知模板 + 通道配置 |

> 图例：✅ 全部归属 / 🔶 协作（Python 侧实现执行逻辑，Java 侧管理状态与配置）

### 3.2 Java 后端模块职责

```
finance-backend/
├── auth/              # 认证授权（JWT + 三维度 RBAC + 互斥约束 + Skyvern 桥接）
├── tenant/            # 多租户隔离（上下文 + 查询过滤拦截器）
├── approval/          # 审批引擎（关键词预筛 + 路由 + Pub/Sub + 审批 CRUD）
├── audit/             # 合规审计（日志 + 脱敏 + MinIO + CSV + 多维检索）
├── dashboard/         # 运营大屏（统计 API + Redis 缓存 + 趋势）
├── llm/               # LLM 管理（缓存统计 + NEEDS_HUMAN 队列 + 模型路由配置）
├── agent/             # Agent 状态（CoordinationState + 任务状态机 + 断点续跑）
├── skills/            # Skill 元数据（注册 + CRUD + 版本）
├── workflows/         # 工作流（模板 CRUD + Fernet 加密 + 校验 + 触发）
├── notification/      # 通知（企业微信/钉钉 + 模板 + 通道配置）
├── ai/                # Python AI 服务客户端（HTTP Interface + SSE 透传）
├── common/            # 通用（异常 + 响应封装 + 枚举 + 工具）
└── config/            # Spring 配置（Security + Redis + MyBatis + OpenAPI）
```

### 3.3 Python AI 服务模块职责

```
finance-ai/
├── skyvern/                  # Skyvern 核心（最小改动，Playwright 浏览器管理）
├── app/
│   ├── agent/                # Planner + Executor + Coordinator 执行逻辑
│   ├── skills/               # 7 个 Skill 实现 + Pipeline 执行器
│   ├── llm/                  # 三层容错调用 + Action 缓存读写 + 模型路由执行
│   ├── approval/             # LLM 风险二次判断（被 Java 调用）
│   ├── audit/                # 审计回调客户端（上报 Java 后端）
│   ├── workflows/            # Skill 编排执行（被 Java 触发）
│   ├── browser/              # Playwright 浏览器会话管理
│   └── api/                  # FastAPI 路由
└── alembic/                  # skyvern_* 表迁移
```

### 3.4 前端模块职责

```
finance-frontend/
├── src/
│   ├── components/
│   │   ├── Icon/             # 21 个手写 SVG 图标组件
│   │   ├── ui/               # 基础组件（Radix + shadcn 风格）
│   │   └── enterprise/       # 企业通用组件（RiskBadge / StatusBadge / Timeline）
│   ├── routes/
│   │   ├── auth/             # 登录页
│   │   ├── enterprise/       # 企业专属页面（审批中心 / 审计日志 / 大屏 / 设置）
│   │   ├── tasks/            # 任务列表与详情
│   │   └── workflows/        # 工作流管理
│   ├── api/                  # Axios + SSE 客户端
│   ├── store/                # Zustand 状态管理
│   └── styles/               # CSS 设计 token + 毛玻璃样式
```

---

## 4. 项目目录结构

### 4.1 顶层结构

```
financeRPA/
├── docs/                         # 文档
│   ├── requirements.md           # 需求分析
│   ├── tech-stack.md             # 技术选型
│   └── system-design.md          # 系统设计（本文档）
├── finance-backend/              # Java 后端（Spring Boot 3.2 + Java 21）
├── finance-ai/                   # Python AI 服务（FastAPI + Skyvern）
├── finance-frontend/             # React 前端
├── nginx/                        # Nginx 反向代理配置
│   ├── nginx.conf
│   └── conf.d/
│       └── default.conf
├── scripts/                      # 辅助脚本
│   ├── seed_demo_data.py         # 演示数据导入
│   └── healthcheck.sh            # 健康检查
├── docker-compose.yml            # 开发环境（6 服务）
├── docker-compose.prod.yml       # 生产环境 overlay
├── Makefile                      # 常用操作入口
├── .env.example                  # 全量配置模板
└── README.md
```

### 4.2 finance-backend 详设

```
finance-backend/
├── pom.xml                                    # Maven 配置
├── src/
│   ├── main/
│   │   ├── java/com/finrpa/
│   │   │   ├── FinRpaApplication.java         # 启动类
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java        # Spring Security 配置
│   │   │   │   ├── RedisConfig.java           # Redisson 配置
│   │   │   │   ├── MybatisPlusConfig.java     # 分页 + 租户拦截器
│   │   │   │   ├── VirtualThreadConfig.java   # 虚拟线程 TaskExecutor
│   │   │   │   ├── OpenApiConfig.java         # springdoc 配置
│   │   │   │   └── WebClientConfig.java       # HTTP Interface 客户端
│   │   │   ├── common/
│   │   │   │   ├── exception/                 # 业务异常 + 全局处理
│   │   │   │   ├── response/                  # 统一响应封装
│   │   │   │   ├── enums/                     # 枚举（RiskLevel / RoleType / TaskState）
│   │   │   │   ├── context/                   # TenantContext / UserContext
│   │   │   │   └── util/                      # FernetUtil / HashUtil
│   │   │   ├── auth/
│   │   │   │   ├── controller/                # AuthController
│   │   │   │   ├── service/                   # JwtService / PermissionService
│   │   │   │   ├── mapper/                    # UserMapper / RoleMapper
│   │   │   │   ├── entity/                    # UserEO / RoleEO
│   │   │   │   └── dto/                       # LoginDTO / PermissionDTO
│   │   │   ├── tenant/
│   │   │   │   ├── interceptor/               # TenantInterceptor
│   │   │   │   └── handler/                   # TenantLineHandler
│   │   │   ├── approval/
│   │   │   │   ├── controller/                # ApprovalController
│   │   │   │   ├── service/                   # RiskDetectService / ApprovalRouteService
│   │   │   │   ├── pubsub/                    # ApprovalPublisher / ApprovalSubscriber
│   │   │   │   ├── keywords/                  # RiskKeywordRegistry（3 大行业）
│   │   │   │   ├── mapper/                    # ApprovalMapper
│   │   │   │   └── entity/                    # ApprovalEO
│   │   │   ├── audit/
│   │   │   │   ├── controller/                # AuditController
│   │   │   │   ├── service/                   # AuditService / SanitizeService
│   │   │   │   ├── storage/                   # MinioStorageService
│   │   │   │   ├── export/                    # CsvExporter
│   │   │   │   └── mapper/                    # AuditLogMapper
│   │   │   ├── dashboard/
│   │   │   │   ├── controller/                # DashboardController
│   │   │   │   ├── service/                   # StatsService
│   │   │   │   └── cache/                     # DashboardCache
│   │   │   ├── llm/
│   │   │   │   ├── controller/                # LlmCacheController / NeedsHumanController
│   │   │   │   ├── service/                   # CacheStatsService / NeedsHumanService
│   │   │   │   ├── router/                    # ModelRouterConfig（策略配置）
│   │   │   │   └── mapper/                    # LlmCacheStatsMapper
│   │   │   ├── agent/
│   │   │   │   ├── controller/                # TaskController
│   │   │   │   ├── service/                   # CoordinationStateService / TaskStateMachine
│   │   │   │   ├── mapper/                    # TaskMapper / SubTaskMapper
│   │   │   │   └── entity/                    # TaskEO / SubTaskEO / CoordinationStateEO
│   │   │   ├── skills/
│   │   │   │   ├── controller/                # SkillController
│   │   │   │   ├── service/                   # SkillRegistryService
│   │   │   │   └── mapper/                    # SkillMetaMapper
│   │   │   ├── workflows/
│   │   │   │   ├── controller/                # WorkflowController
│   │   │   │   ├── service/                   # WorkflowService / WorkflowTriggerService
│   │   │   │   ├── crypto/                    # FernetCryptoService
│   │   │   │   ├── validator/                 # WorkflowValidator
│   │   │   │   └── mapper/                    # WorkflowMapper
│   │   │   ├── notification/
│   │   │   │   ├── controller/                # NotificationController
│   │   │   │   ├── service/                   # NotificationService
│   │   │   │   ├── channels/                  # WeComChannel / DingTalkChannel
│   │   │   │   └── templates/                 # 通知模板
│   │   │   └── ai/                            # Python AI 服务客户端
│   │   │       ├── client/                    # AiServiceClient (HTTP Interface)
│   │   │       ├── dto/                       # AiRequestDTO / AiResponseDTO
│   │   │       └── sse/                       # AiSseProxy（SSE 透传）
│   │   └── resources/
│   │       ├── application.yml                # 主配置
│   │       ├── application-dev.yml            # 开发环境
│   │       ├── application-prod.yml           # 生产环境
│   │       ├── db/migration/                  # Flyway 脚本
│   │       │   ├── V20260725_001__auth__init_schema.sql
│   │       │   ├── V20260725_002__tenant__init_schema.sql
│   │       │   ├── V20260725_003__approval__init_schema.sql
│   │       │   ├── V20260725_004__audit__init_schema.sql
│   │       │   ├── V20260725_005__agent__init_schema.sql
│   │       │   ├── V20260725_006__workflows__init_schema.sql
│   │       │   ├── V20260725_007__skills__init_schema.sql
│   │       │   ├── V20260725_008__llm__init_schema.sql
│   │       │   ├── V20260725_009__dashboard__init_schema.sql
│   │       │   └── V20260725_010__notification__init_schema.sql
│   │       └── mapper/                        # MyBatis XML（复杂 SQL）
│   │           ├── AuditLogMapper.xml
│   │           └── DashboardStatsMapper.xml
│   └── test/
│       ├── java/com/finrpa/                   # JUnit 5 单元 + 集成测试
│       └── resources/                         # 测试数据 + TestContainers
└── Dockerfile
```

### 4.3 finance-ai 详设

```
finance-ai/
├── pyproject.toml                             # 依赖管理（uv + hatchling）
├── alembic.ini
├── alembic/                                   # skyvern_* 表迁移
│   ├── env.py
│   └── versions/
├── skyvern/                                   # Skyvern 核心（最小改动）
│   ├── forge/                                 # ForgeAgent + LLM 调度
│   └── cli/                                   # Skyvern CLI
├── app/
│   ├── main.py                                # FastAPI 入口
│   ├── config.py                              # 配置（pydantic-settings）
│   ├── api/
│   │   ├── tasks.py                           # 任务触发 / 状态查询
│   │   ├── sse.py                             # SSE 流推送
│   │   ├── skills.py                          # Skill 元数据查询
│   │   ├── risk.py                            # LLM 风险判断（被 Java 调用）
│   │   └── health.py                          # 健康检查
│   ├── agent/
│   │   ├── planner.py                         # Planner（任务拆解 + replan）
│   │   ├── executor.py                        # Executor（子任务执行）
│   │   ├── coordinator.py                     # Coordinator（编排 + 失败策略）
│   │   └── schemas.py                         # SubTask / TaskPlan / ExecutionResult
│   ├── skills/
│   │   ├── base.py                            # Skill 基类接口
│   │   ├── executor.py                        # Skill Pipeline 执行器
│   │   ├── auth_skills.py                     # LoginSkill + SessionKeepAliveSkill
│   │   ├── interaction_skills.py              # FormFillSkill + SearchAndSelectSkill + PaginationSkill
│   │   └── extraction_skills.py               # TableExtractSkill + FileDownloadSkill
│   ├── llm/
│   │   ├── resilient_caller.py                # 三层容错（Prompt + Pydantic + NEEDS_HUMAN）
│   │   ├── action_cache.py                    # Action 缓存读写（Redis）
│   │   ├── model_router.py                    # 模型路由执行（按页面复杂度）
│   │   └── task_states.py                     # NEEDS_HUMAN 状态机
│   ├── approval/
│   │   └── risk_judge.py                      # LLM 风险二次判断
│   ├── audit/
│   │   └── reporter.py                        # 审计回调客户端（上报 Java）
│   ├── workflows/
│   │   └── runner.py                          # Skill 编排执行
│   ├── browser/
│   │   ├── session_manager.py                 # Playwright 会话管理
│   │   └── browser_ops.py                     # 浏览器操作封装
│   └── clients/
│       ├── java_backend.py                    # Java 后端 HTTP 客户端（httpx）
│       └── sse_proxy.py                       # SSE 流转发
├── tests/
│   ├── unit/                                  # pytest 单元测试
│   ├── integration/                           # 端到端集成测试
│   └── fixtures/                              # 模拟数据
└── Dockerfile
```

### 4.4 finance-frontend 详设

```
finance-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.js
├── index.html
├── src/
│   ├── main.tsx                               # 入口
│   ├── App.tsx                                # 根组件
│   ├── router.tsx                             # 路由配置
│   ├── api/
│   │   ├── AxiosClient.ts                     # Axios 实例 + 拦截器
│   │   ├── QueryClient.ts                     # React Query 配置
│   │   ├── sse.ts                             # SSE 客户端
│   │   └── types.ts                           # API 类型定义
│   ├── store/
│   │   ├── AuthStore.ts                       # 认证状态
│   │   ├── UserContext.ts                     # 用户上下文
│   │   └── ...
│   ├── components/
│   │   ├── Icon/
│   │   │   ├── icons.tsx                      # 21 个手写 SVG 图标
│   │   │   └── index.tsx
│   │   ├── ui/                                # 基础组件（accordion/button/dialog/...）
│   │   ├── enterprise/
│   │   │   ├── RiskBadge.tsx                  # 风险等级徽章
│   │   │   ├── StatusBadge.tsx                # 任务状态徽章
│   │   │   ├── Timeline.tsx                   # 操作时间线
│   │   │   └── GlassCard.tsx                  # 毛玻璃卡片
│   │   ├── BrowserStream.tsx                  # 浏览器实时流
│   │   ├── AuthGuard.tsx                      # 路由守卫
│   │   └── PageLayout.tsx                     # 页面布局
│   ├── routes/
│   │   ├── auth/
│   │   │   └── LoginPage.tsx
│   │   ├── enterprise/
│   │   │   ├── ApprovalCenter.tsx             # 审批中心
│   │   │   ├── AuditLogs.tsx                  # 审计日志
│   │   │   ├── Dashboard.tsx                  # 运营大屏
│   │   │   ├── NeedsHuman.tsx                 # 人工接管队列
│   │   │   ├── LlmMonitor.tsx                 # LLM 监控
│   │   │   └── Settings.tsx                   # 设置
│   │   ├── tasks/
│   │   │   ├── TasksPage.tsx                  # 任务列表
│   │   │   └── TaskDetail.tsx                 # 任务详情（含浏览器流）
│   │   ├── workflows/
│   │   │   ├── Workflows.tsx                  # 工作流管理
│   │   │   └── WorkflowRuns.tsx               # 执行历史
│   │   └── root/
│   │       ├── RootLayout.tsx
│   │       ├── Header.tsx
│   │       └── SideNav.tsx
│   ├── hooks/                                 # 自定义 Hooks
│   ├── util/                                  # 工具函数
│   ├── i18n/
│   │   ├── locales.ts                         # 中英文文案
│   │   └── useI18n.ts
│   └── styles/
│       ├── variables.css                      # 设计 token
│       ├── glass.css                          # 毛玻璃样式
│       └── index.css
└── Dockerfile
```

---

## 5. 跨语言协作设计

### 5.1 调用拓扑

```
┌─────────────────────────────────────────────────────────────────┐
│                    Java 后端 → Python AI 服务                     │
├─────────────────────────────────────────────────────────────────┤
│ POST /api/v1/ai/tasks               触发任务执行                  │
│ GET  /api/v1/ai/tasks/{id}/state    查询任务状态                  │
│ POST /api/v1/ai/tasks/{id}/resume   断点续跑                      │
│ POST /api/v1/ai/tasks/{id}/abort    终止任务                      │
│ POST /api/v1/ai/risk/judge          LLM 风险二次判断              │
│ GET  /api/v1/ai/sse/tasks/{id}      SSE 订阅执行流（透传前端）     │
│ POST /api/v1/ai/skills/validate     Skill 参数校验                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Python AI 服务 → Java 后端                    │
├─────────────────────────────────────────────────────────────────┤
│ POST /api/v1/internal/audit/logs    上报审计日志（含截图元数据）   │
│ POST /api/v1/internal/tasks/{id}/state   更新任务状态            │
│ POST /api/v1/internal/tasks/{id}/subtasks  更新子任务状态        │
│ POST /api/v1/internal/llm/calls     记录 LLM 调用（成本统计）    │
│ POST /api/v1/internal/llm/needs-human  转人工接管               │
│ POST /api/v1/internal/screenshots    上传截图（转发 MinIO）      │
└─────────────────────────────────────────────────────────────────┘
```

> **内部 API 隔离**：Python → Java 的 `/api/v1/internal/*` 路径走服务间鉴权（共享密钥 + 网络隔离），不对外暴露。

### 5.2 通信机制

| 场景 | 机制 | 说明 |
|------|------|------|
| 同步调用 | HTTP REST + JSON | 默认超时：连接 5s / 读取 30s；浏览器长任务用 SSE 旁路 |
| 流式推送 | SSE | Python 推 → Java 透传 → Nginx 透传 → 前端 EventSource 消费 |
| 消息广播 | Redis Pub/Sub | 审批请求/响应跨进程通知；channel: `approval:requests` / `approval:responses` |
| 重试 | Spring Retry（Java）/ tenacity（Python） | 瞬态错误自动重试 3 次，指数退避 |
| 链路追踪 | W3C Trace Context（traceparent header）透传 | 未来接入 OpenTelemetry 可贯通 |

### 5.3 状态同步机制

```
任务状态权威：Java 后端（enterprise_task / enterprise_subtask / enterprise_coordination_state 表）

Python 执行流程：
  1. Java 触发任务 → POST /api/v1/ai/tasks（含 task_id + plan）
  2. Python Executor 执行子任务
  3. 每步执行后 → POST /api/v1/internal/tasks/{id}/subtasks（更新子任务状态）
  4. 截图上传 → POST /api/v1/internal/screenshots → Java 转发 MinIO
  5. 任务终态 → POST /api/v1/internal/tasks/{id}/state（success/failed/needs_human）
  6. SSE 全程推送进度 → 前端实时可见

断点续跑：
  Java 查询 CoordinationState → 找到 last_success_subtask_index
       → POST /api/v1/ai/tasks/{id}/resume（从 index+1 开始）
  Python Planner 重新规划剩余子任务 → Executor 续跑
```

### 5.4 错误传播与降级

| 错误类型 | Java 侧处理 | Python 侧处理 |
|----------|-------------|---------------|
| Python 服务不可用 | 返回 503 + 重试 3 次 + 告警 | - |
| Java 后端不可用 | - | 本地缓存审计日志，恢复后批量上报 |
| LLM 调用失败 | - | 三层容错 → NEEDS_HUMAN |
| 浏览器崩溃 | 标记任务 failed | 重启浏览器会话 + 触发 replan |
| Redis Pub/Sub 断开 | 等待重连 + 持久化审批状态 | 等待重连 + 持久化任务状态 |
| MinIO 上传失败 | 重试 3 次 + 降级本地存储 | 通过 Java 转发，由 Java 兜底 |

---

## 6. 核心模块详设

### 6.1 三维度权限体系（auth）

#### 6.1.1 权限模型

```
维度 1: 部门（department）     维度 2: 业务线（business_line）    维度 3: 角色（role）
┌──────────────────┐          ┌──────────────────┐              ┌──────────────────┐
│ 对公信贷部        │          │ 公司金融          │              │ super_admin      │
│ 个人金融部        │          │ 个人金融          │              │ org_admin        │
│ 资产管理部        │          │ 资产管理          │              │ operator         │
│ 风险管理部        │          │ 同业业务          │              │ approver         │
│ 合规审计部        │          │ ...              │              │ viewer           │
└──────────────────┘          └──────────────────┘              └──────────────────┘

关联：user_department_role（用户 × 部门 × 业务线 × 角色）多对多

互斥约束：同一用户在同一部门内不能同时持有 operator + approver（数据库 CHECK）
```

#### 6.1.2 权限解析算法

```
输入：user_context + resource(organization_id, department_id, business_line_id)
输出：effective_permission (none / read / operate / approve)

1. 跨组织检查：
   - if resource.organization_id != user.organization_id:
       if user.has_special_permission('cross_org_approve'): return APPROVE
       if user.has_special_permission('cross_org_read'): return READ
       return NONE

2. 遍历 user.user_department_roles:
   - if role == super_admin or org_admin: return APPROVE
   - if role.department_id == resource.department_id:
       effective = max(effective, role.permission_level)
   - if role.business_line_id == resource.business_line_id:
       effective = max(effective, role.permission_level)  # 跨部门访问业务线

3. return effective
```

#### 6.1.3 Java 实现要点

| 组件 | 实现 |
|------|------|
| `JwtService` | jjwt 0.12 签发/校验，access 60min + refresh 7d |
| `PermissionService` | 权限解析算法实现，结果缓存到 RequestContext |
| `AuthBridge` | 企业 JWT → Skyvern 原生 API Key 桥接 |
| `RoleMutexValidator` | 用户角色变更时校验互斥约束 |
| `PermissionInterceptor` | Spring AOP，方法级 `@RequirePermission(level=APPROVE)` |

#### 6.1.4 与原项目对照

| 原项目（Python） | 本项目（Java） |
|------------------|----------------|
| `enterprise/auth/jwt_service.py` | `auth/service/JwtService.java` |
| `enterprise/auth/permission.py` | `auth/service/PermissionService.java` |
| `enterprise/auth/bridge.py` | `auth/service/AuthBridge.java` |
| `enterprise/auth/constraints.py` | `auth/service/RoleMutexValidator.java` |
| `enterprise/auth/dependencies.py` | `auth/filter/PermissionInterceptor.java` |

### 6.2 多租户隔离（tenant）

> **实现说明（M1.2 落地偏差）**：
> - 字段名使用 `org_id`（与 M1.1 已存在的 `sys_user.org_id` / `sys_role.org_id` 保持一致），而非设计稿原写的 `organization_id`，避免破坏性迁移。
> - `TenantContext` 采用 `ThreadLocal` 而非 Java 21 `ScopedValue`（后者在 JDK 21 仍为预览特性，启用 `--enable-preview` 改动面大）。如未来 ScopedValue 转正可平滑迁移。
> - JWT 解析与 TenantContext 设置分两层：`JwtAuthenticationFilter`（Filter 层）解析 orgId 暂存到 request attribute，`TenantInterceptor`（WebMVC 层）读取后注入 `TenantContext`，避免重复解析 token 并保持职责分离。

#### 6.2.1 隔离机制

```
请求入口 → JwtAuthenticationFilter 解析 token → 提取 orgId 暂存 request attribute
         → TenantInterceptor.preHandle 读取 → TenantContext.setOrgId(orgId)（ThreadLocal）
         → MyBatis-Plus TenantLineInnerInterceptor 自动追加 WHERE org_id = ?
请求结束 → TenantInterceptor.afterCompletion → TenantContext.clear()
```

#### 6.2.2 Java 实现要点

| 组件 | 实现 |
|------|------|
| `TenantContext` | 基于 `ThreadLocal<String>`，提供 `setOrgId` / `getOrgId` / `clear` 静态方法 |
| `TenantInterceptor` | Spring Web MVC `HandlerInterceptor`，从 request attribute 读取 orgId 注入 `TenantContext` |
| `TenantLineHandlerImpl` | 实现 MyBatis-Plus `TenantLineHandler`，`getTenantIdColumn()` 返回 `org_id` |
| `TenantConstant` | 忽略表清单常量接口：`enterprise_organization`、`sys_user`（登录场景需查）、`sys_role`、`sys_user_role`、`sys_role_permission`、`sys_permission`、`sys_dictionary`、`sys_config`、`sys_audit_log`、RPA 执行/日志/浏览器会话/审批表，及 `skyvern_*` 前缀 |
| `TenantWebMvcConfig` | `WebMvcConfigurer` 实现，注册 `TenantInterceptor` 拦截所有路径 `/**` |
| `MyBatisPlusConfig` | 在拦截器链中按 `TenantLineInnerInterceptor` → `PaginationInnerInterceptor` 顺序添加（租户插件须在分页之前） |

### 6.3 高危操作分级审批（approval）

#### 6.3.1 两阶段风险检测流程

```
                    ┌─────────────────────────┐
                    │  任务触发（含目标+参数）  │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │ 阶段 1：Java 关键词预筛   │
                    │ - 风险关键词库（3 大行业）│
                    │ - 金额正则检测            │
                    │ - 命中 → 标记待 LLM 判断  │
                    │ - 未命中 → risk_level=low │
                    └────────────┬────────────┘
                                 │ 命中
                    ┌────────────▼────────────┐
                    │ 阶段 2：Python LLM 判断   │
                    │ Java → POST /ai/risk/judge│
                    │ LLM 输入：目标+参数+预筛结果│
                    │ LLM 输出：final_risk_level │
                    │ 走三层容错               │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │ 分级路由                 │
                    │ low → 直接执行           │
                    │ medium → 自动通过        │
                    │ high → 部门 approver     │
                    │ critical → 合规审计部    │
                    └─────────────────────────┘
```

#### 6.3.2 审批流编排

```
Java ApprovalService:
  1. 创建 ApprovalRequest（持久化 PostgreSQL）
  2. 发布 Redis Pub/Sub: approval:requests
  3. 调用 NotificationService 推送企业微信/钉钉
  4. 异步等待 approval:responses（不阻塞主线程）
  5. 超时检测（ScheduledExecutor，high=30min / critical=60min）
     → 超时自动拒绝 + 告警
  6. 收到响应 → 更新 ApprovalRequest 状态 → 通知 Python Executor

Python Executor:
  - 提交审批后挂起当前子任务（state=PENDING_APPROVAL）
  - 订阅 approval:responses:{task_id}
  - 收到 approved → 继续执行
  - 收到 rejected → 终止任务
```

#### 6.3.3 Java 实现要点

| 组件 | 实现 |
|------|------|
| `RiskKeywordRegistry` | 3 大行业关键词库（银行/保险/证券），从配置或 DB 加载 |
| `AmountDetector` | 金额正则识别 + 阈值比较 |
| `RiskDetectService` | 阶段 1 入口，命中后调 Python `/ai/risk/judge` |
| `ApprovalRouteService` | 按 risk_level 路由到对应 approver |
| `ApprovalPublisher` | Redisson RTopic 发布 `approval:requests` |
| `ApprovalSubscriber` | Redisson RTopic 订阅 `approval:responses` |
| `ApprovalTimeoutScheduler` | ShedLock + Spring Scheduler 检测超时 |

### 6.4 全链路合规审计（audit）

#### 6.4.1 审计日志结构

```
enterprise_audit_log
├── 基本信息: log_id, task_id, organization_id, department_id, business_line_id, user_id
├── 操作信息: action_type, target_element, action_params(脱敏), execution_result
├── 风险信息: risk_level, approval_id
├── 时间信息: started_at, completed_at, duration_ms
├── 截图: before_screenshot_url, after_screenshot_url（MinIO 预签名）
└── LLM 信息: llm_model, llm_tokens_used, llm_cost
```

#### 6.4.2 数据流

```
Python Executor 执行每步操作：
  1. 操作前截图 → 上传 Java → Java 转发 MinIO
  2. 执行操作
  3. 操作后截图 → 上传 Java → Java 转发 MinIO
  4. 上报审计元数据 → POST /api/v1/internal/audit/logs
  5. Java SanitizeService 脱敏 action_params
  6. Java AuditService 持久化日志（含 MinIO 预签名 URL）
  7. Dashboard 缓存失效
```

#### 6.4.3 脱敏规则

| 类型 | 规则 | 实现 |
|------|------|------|
| 银行卡号 | 前 4 后 4，中间 `*` | `SanitizeService.sanitizeCard()` |
| 身份证号 | 前 6 后 4 | `SanitizeService.sanitizeIdCard()` |
| 密码 | 完全替换 `***` | `SanitizeService.sanitizePassword()` |
| 手机号 | 前 3 后 4 | `SanitizeService.sanitizePhone()` |
| 邮箱 | 首字符 + `***` + 域名 | `SanitizeService.sanitizeEmail()` |

#### 6.4.4 MinIO 存储策略

```
Bucket: finrpa-audit-{organization_id}
路径:   {date}/{task_id}/{step_index}_{before|after}.png
访问:   预签名 URL，有效期 1 小时
保留期: 90 天（可配置）
清理:   定时任务扫描过期对象删除
```

### 6.5 LLM 三层容错 + NEEDS_HUMAN（llm）

#### 6.5.1 三层容错流程（Python 实现）

```
┌──────────────────────────────────────────┐
│ 层 1: Prompt 强制格式约束                 │
│ - System Prompt 明确要求返回 JSON Schema  │
│ - 附 few-shot 示例                       │
└──────────────────┬───────────────────────┘
                   │ LLM 返回
┌──────────────────▼───────────────────────┐
│ 层 2: Pydantic 校验重试                  │
│ - 用结构化模型校验返回结果                │
│ - 失败则将错误反馈给 LLM 重试（默认 2 次）│
└──────────────────┬───────────────────────┘
                   │ 仍失败
┌──────────────────▼───────────────────────┐
│ 层 3: NEEDS_HUMAN 转换                   │
│ - 任务状态转为 needs_human               │
│ - 上报 Java → 进入人工接管队列            │
│ - 等待操作员处置（skip/manual/abort）     │
└──────────────────────────────────────────┘
```

#### 6.5.2 Action 缓存（Python 读写 + Java 统计）

```
缓存 Key: hash(DOM 结构哈希（剥除动态内容）) + hash(导航目标)
缓存 Value: LLM 决策结果（click/input/extract 等）
TTL: 24 小时
存储: Redis

命中流程（Python）:
  1. Skill 需要 LLM 决策
  2. 计算 DOM 哈希 + 导航目标哈希
  3. 查 Redis → 命中直接返回缓存
  4. 未命中 → 调 LLM → 写入缓存
  5. 上报 Java LLM 调用记录（含 cache_hit 标记）

统计（Java）:
  - GET /api/v1/llm/cache/stats → 缓存命中率
  - DELETE /api/v1/llm/cache/task/{task_id} → 清除指定任务缓存
  - DELETE /api/v1/llm/cache/expired → 清除过期缓存
```

#### 6.5.3 模型路由（Python 执行 + Java 配置）

```
页面复杂度评分（Python 计算）:
  - DOM 节点数量
  - 表单字段数量
  - 动态元素数量
  - 截图熵（视觉复杂度）

路由规则（Java 配置，Python 读取）:
  score < 30  → 轻量模型（GPT-4o-mini / Claude Haiku）
  30 ≤ score < 70 → 标准模型（GPT-4o / Claude Sonnet）
  score ≥ 70 → 重型模型（Claude Opus / GPT-4 Turbo）

Java 配置 API:
  - GET /api/v1/llm/router/config → 路由策略
  - PUT /api/v1/llm/router/config → 更新策略
  - GET /api/v1/llm/router/stats → 各模型调用统计
```

#### 6.5.4 NEEDS_HUMAN 人工接管

```
状态流转:
  executing → needs_human → (操作员处置)
                          ├─ skip → 继续下一子任务
                          ├─ manual → 操作员手动执行 → 继续
                          └─ abort → 终止任务

展示信息（前端）:
  - 卡住步骤的截图
  - LLM 原始输出
  - Pydantic 校验错误信息
  - 上下文参数
```

### 6.6 Planner + Executor 双 Agent（agent）

#### 6.6.1 协作架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Java 后端（状态权威）                      │
│  enterprise_task / enterprise_subtask / enterprise_coord_state│
│  TaskStateMachine + CoordinationStateService                │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP REST
┌──────────────────────▼──────────────────────────────────────┐
│                Python AI 服务（执行逻辑）                     │
│                                                              │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │  Planner    │◄──►│ Coordinator  │◄──►│  Executor    │   │
│  │             │    │              │    │              │   │
│  │ - 拆解任务   │    │ - 编排通信    │    │ - 执行子任务  │   │
│  │ - replan    │    │ - 失败策略    │    │ - 调用 Skill  │   │
│  │             │    │ - 断点续跑    │    │ - 审计回调    │   │
│  └─────────────┘    └──────────────┘    └──────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

#### 6.6.2 数据结构

```
SubTask:
  - id, task_id, index, goal, completion_condition
  - max_retries, failure_strategy (retry/skip/abort/replan)
  - state (pending/executing/success/failed/skipped)
  - retry_count, result, error

TaskPlan:
  - id, task_id, navigation_goal, subtasks[]
  - version, created_at

CoordinationState:
  - task_id, current_plan, current_subtask_index
  - replan_count (max 3), state
```

#### 6.6.3 失败策略

| 策略 | 触发条件 | 处理 |
|------|----------|------|
| `retry` | 瞬态错误（网络抖动、超时） | 重试子任务（受 max_retries 限制） |
| `skip` | 非关键步骤失败 | 跳过继续下一子任务 |
| `abort` | 关键前置步骤失败 | 终止整个任务 |
| `replan` | 路径阻塞（页面结构变化） | 请求 Planner 重新规划剩余子任务 |

```
replan 上限:
  - replan_count < 3 → Planner 重新规划
  - replan_count >= 3 → 转 NEEDS_HUMAN
```

#### 6.6.4 断点续跑

```
1. 任务中断（网络断开/服务重启）
2. Java 查询 CoordinationState → last_success_subtask_index = N
3. Java → POST /api/v1/ai/tasks/{id}/resume
4. Python Coordinator 加载状态
5. Planner 重新规划从 N+1 开始的剩余子任务
6. Executor 续跑（不重做 1~N）
```

### 6.7 可组合 Skill 库（skills）

#### 6.7.1 Skill 基类接口（Python）

```python
class BaseSkill(ABC):
    name: str
    description: str
    param_model: Type[BaseModel]

    @abstractmethod
    async def execute(self, params: BaseModel, context: SkillContext) -> ExecutionResult:
        ...

    @abstractmethod
    def get_failure_strategy(self, error: SkillError) -> FailureStrategy:
        ...
```

#### 6.7.2 七个 Skill 实现

| Skill | 类别 | 实现 |
|-------|------|------|
| LoginSkill | 认证 | 用户名/密码 + 2FA + TOTP |
| SessionKeepAliveSkill | 认证 | 维持会话活跃 |
| FormFillSkill | 交互 | 表单填写 + 提交 |
| SearchAndSelectSkill | 交互 | 搜索 + 下拉选择 |
| PaginationSkill | 交互 | 翻页（下一页/页码/无限滚动） |
| TableExtractSkill | 提取 | 表格 → 结构化 JSON |
| FileDownloadSkill | 提取 | 下载 + 校验 + 上传 MinIO |

#### 6.7.3 Skill Pipeline 执行器

```
输入: skills[{skill, params_mapping}] + workflow_params

执行:
  for step in skills:
    params = resolve_params(step.params_mapping, workflow_params)
    result = step.skill.execute(params, context)
    audit_callback(result)  # 上报 Java
    if result.failed:
        strategy = step.skill.get_failure_strategy(result.error)
        handle_strategy(strategy)

参数映射:
  - 引用模式: {{workflow.params.account_id}} → 从 workflow_params 取值
  - 字面量模式: 直接预设值
```

#### 6.7.4 Java 侧 Skill 元数据管理

```
enterprise_skill_meta 表:
  - id, name, description, category
  - param_schema (JSON)
  - version, enabled
  - registered_at

API:
  - GET /api/v1/skills → 列表
  - GET /api/v1/skills/{name} → 详情
  - POST /api/v1/skills → 注册（同步 Python 校验）
  - PUT /api/v1/skills/{name} → 更新
```

### 6.8 工作流模板（workflows）

#### 6.8.1 模板结构

```
WorkflowTemplate:
  - id, name, industry (banking/insurance/securities)
  - risk_level (low/medium/high/critical)
  - params[]: {name, type, required, encrypted}
  - steps[]: {skill, params_mapping}
```

#### 6.8.2 六个金融场景模板

| 模板 | 行业 | 风险 | Skill 组合 |
|------|------|------|------------|
| 银行流水下载 | banking | medium | Login → FormFill → FileDownload |
| 跨行转账核对 | banking | high | Login → TableExtract → Pagination |
| 对公贷款放款 | banking | critical | Login → FormFill → SearchAndSelect |
| 保单申请填写 | insurance | high | Login → FormFill |
| 理赔审核提交 | insurance | high | Login → FileDownload → FormFill |
| 委托下单 | securities | high | Login → FormFill |

#### 6.8.3 参数加密

```
敏感参数（密码、密钥）:
  - 存储前: Fernet 对称加密（Java 侧 FernetUtil，与 Python cryptography 字节级兼容）
  - 运行时: Java 解密 → 传给 Python（内存中，不落盘）
  - 加密密钥: 环境变量 FERNET_KEY 注入
```

#### 6.8.4 触发执行流程

```
1. POST /api/v1/workflows/{id}/run（Java）
2. Java WorkflowTriggerService:
   - 加载模板
   - 解密敏感参数
   - 创建 Task + 初始化 CoordinationState
   - 风险检测（调 approval 模块）
   - 若需审批 → 等待审批结果
   - 审批通过 / 无需审批 → POST /api/v1/ai/tasks（触发 Python）
3. Python 接收任务:
   - Planner 拆解
   - Executor 按 Skill Pipeline 执行
   - 全程 SSE 推送 + 审计回调
4. Java 更新任务终态
```

### 6.9 运营大屏（dashboard）

#### 6.9.1 统计指标

| 类别 | 指标 |
|------|------|
| 任务 | 总数 / 成功数 / 失败数 / 进行中数 / 成功率 |
| 性能 | 平均执行时长 / P95 执行时长 |
| LLM | 调用次数 / 总成本 / Action 缓存命中率 |
| 人工 | 接管队列长度 / 平均处置时长 |
| 业务 | 各业务线任务分布 + 成功率对比 |
| 错误 | 错误类型分布 Top 10 |
| 风险 | 风险等级分布 |
| 审批 | 审批平均响应时长 / 超时数 |

#### 6.9.2 缓存策略

```
Key: dashboard:{organization_id}:{metric}:{date}
TTL: 5 分钟（实时指标） / 1 小时（历史趋势）
失效: 任务完成时主动刷新（Spring ApplicationEvent）
存储: Redis Hash
```

### 6.10 通知（notification）

#### 6.10.1 通道与模板

| 通道 | 实现 |
|------|------|
| 企业微信群机器人 | Webhook URL + JSON 消息体 |
| 钉钉群机器人 | Webhook URL + 加签 |

| 模板 | 触发场景 |
|------|----------|
| 审批待处理 | approval:requests 发布时 |
| 审批超时告警 | 超时检测触发时 |
| 任务失败 | 任务终态为 failed 时 |
| NEEDS_HUMAN 接管 | LLM 三层容错失败时 |
| 风险等级升级 | LLM 判断升级 risk_level 时 |

---

## 7. 关键流程设计

### 7.1 高风险任务执行流程

```
操作员                Java 后端              Python AI            Redis Pub/Sub
  │                      │                       │                     │
  │── POST /workflows/run│                       │                     │
  │─────────────────────►│                       │                     │
  │                      │── 解密参数             │                     │
  │                      │── 创建 Task           │                     │
  │                      │── 关键词预筛           │                     │
  │                      │   命中"大额转账"       │                     │
  │                      │── POST /ai/risk/judge │                     │
  │                      │──────────────────────►│                     │
  │                      │                       │── LLM 判断 critical │
  │                      │◄── risk_level=critical │                     │
  │                      │                       │                     │
  │                      │── 创建 ApprovalRequest│                     │
  │                      │── 发布 approval:requests                    │
  │                      │────────────────────────────────────────────►│
  │                      │── 推送企业微信通知     │                     │
  │                      │                       │                     │
  │                      │   （审批员在审批中心处理）                    │
  │                      │◄── approval:responses │                     │
  │                      │   approved            │                     │
  │                      │                       │                     │
  │                      │── POST /ai/tasks      │                     │
  │                      │──────────────────────►│                     │
  │                      │                       │── Planner 拆解      │
  │                      │                       │── Executor 执行     │
  │◄── SSE 推送进度 ─────┼───────────────────────┤                     │
  │                      │                       │── 每步截图上报      │
  │                      │── 写审计日志           │                     │
  │                      │── 转发 MinIO          │                     │
  │                      │                       │                     │
  │                      │◄── 任务终态 success   │                     │
  │                      │── 更新 Task 状态       │                     │
  │◄── 200 OK ──────────│                       │                     │
```

### 7.2 LLM 失败转 NEEDS_HUMAN 流程

```
Python Executor                Java 后端                前端
     │                            │                       │
     │── LLM 调用（FormFill）      │                       │
     │── Prompt 格式约束失败       │                       │
     │── Pydantic 校验重试 2 次    │                       │
     │── 仍失败                    │                       │
     │                            │                       │
     │── POST /internal/llm/needs-human                     │
     │───────────────────────────►│                       │
     │                            │── 更新 Task 状态       │
     │                            │   needs_human          │
     │                            │── 推送通知             │
     │                            │                       │
     │                            │── SSE 推送状态变更 ────►│
     │                            │                       │── 弹窗提示
     │                            │                       │
     │                            │   （操作员查看截图+LLM 原始输出）
     │                            │                       │
     │                            │◄── POST /llm/needs-human/{id}/resolve
     │                            │   action=manual       │
     │                            │                       │
     │── POST /ai/tasks/{id}/resume                       │
     │◄───────────────────────────│                       │
     │── 继续执行下一子任务        │                       │
```

### 7.3 双 Agent 断点续跑流程

```
场景: 银行日终批处理 10 个子任务，第 7 个失败

时间线:
  T1: Java 触发任务 → Python Planner 拆解 10 个子任务
  T2: Executor 执行 1~6 成功（每步上报 Java 持久化）
  T3: 子任务 7 因网络中断失败
  T4: Coordinator 触发 replan
      → replan_count=1 < 3
      → Planner 重新规划 7~10
      → 重试仍失败
  T5: replan_count=2，再试
  T6: replan_count=3，再试仍失败
  T7: 转 NEEDS_HUMAN（任务挂起）

操作员介入:
  T8: 操作员查看失败原因 → 手动处理后选择"续跑"
  T9: Java 查询 CoordinationState
      → last_success_subtask_index=6
      → replan_count=3（已达上限，重置为 0）
  T10: Java → POST /ai/tasks/{id}/resume?from=7
  T11: Python Planner 重新规划 7~10
  T12: Executor 从 7 续跑（不重做 1~6）
  T13: 全部成功 → 任务完成
```

### 7.4 审批超时处理流程

```
ApprovalTimeoutScheduler（Java, 每分钟扫描）:
  1. 查询所有 pending 状态的 ApprovalRequest
  2. 检查 created_at + timeout 是否 < now
     - high: 30 分钟
     - critical: 60 分钟
  3. 超时 → 自动 reject
     - 更新 ApprovalRequest 状态 = rejected（reason="timeout"）
     - 发布 approval:responses（rejected）
     - 推送企业微信告警通知审批员
  4. Python Executor 收到 rejected → 终止任务
  5. Java 更新 Task 状态 = failed（reason="approval_timeout"）
```

---

## 8. 数据模型概要

### 8.1 表归属与命名

| 模块 | 表前缀 | 迁移工具 | 归属语言 |
|------|--------|----------|----------|
| auth | `enterprise_auth_*` | Flyway | Java |
| tenant | `enterprise_tenant_*` | Flyway | Java |
| approval | `enterprise_approval_*` | Flyway | Java |
| audit | `enterprise_audit_*` | Flyway | Java |
| dashboard | `enterprise_dashboard_*` | Flyway | Java |
| llm | `enterprise_llm_*` | Flyway | Java |
| agent | `enterprise_agent_*` | Flyway | Java |
| skills | `enterprise_skill_*` | Flyway | Java |
| workflows | `enterprise_workflow_*` | Flyway | Java |
| notification | `enterprise_notification_*` | Flyway | Java |
| Skyvern 核心 | `skyvern_*` | Alembic | Python |

### 8.2 核心实体关系

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ organization    │1---*│ department      │     │ business_line   │
│ (组织)          │     │ (部门)          │     │ (业务线)        │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        │                       └───────────┬───────────┘
        │                                   │
        │                       ┌───────────▼───────────┐
        │                       │ user_department_role  │
        │                       │ (用户部门业务线角色关联)│
        │                       └───────────┬───────────┘
        │                                   │
┌───────▼───────┐               ┌───────────▼───────────┐
│ user          │───────────────│ user_special_permission│
│ (用户)        │               │ (跨组织特殊权限)       │
└───────────────┘               └───────────────────────┘
        │
        │
┌───────▼───────┐     ┌─────────────────┐     ┌─────────────────┐
│ task          │1---*│ subtask         │     │ coordination    │
│ (任务)        │     │ (子任务)        │1──1│ state           │
└───────┬───────┘     └─────────────────┘     └─────────────────┘
        │
        │
┌───────▼───────┐     ┌─────────────────┐     ┌─────────────────┐
│ audit_log     │     │ approval_request│     │ llm_call_log    │
│ (审计日志)    │     │ (审批请求)      │     │ (LLM 调用记录)  │
└───────────────┘     └─────────────────┘     └─────────────────┘

┌─────────────────┐     ┌─────────────────┐
│ workflow        │1---*│ workflow_run    │
│ (工作流模板)    │     │ (执行历史)      │
└─────────────────┘     └─────────────────┘

┌─────────────────┐     ┌─────────────────┐
│ skill_meta      │     │ notification    │
│ (Skill 元数据)  │     │ channel (通知通道)│
└─────────────────┘     └─────────────────┘
```

> 字段级 DDL 与索引设计留待数据库详设文档。

---

## 9. API 划分

### 9.1 Java 后端 API（对外）

| 模块 | 前缀 | 主要端点 |
|------|------|----------|
| auth | `/api/v1/auth` | login / refresh / logout / me / organizations / permissions/check |
| tenant | `/api/v1/tenant` | info / departments / business-lines |
| approval | `/api/v1/approvals` | list / detail / approve / reject / history |
| audit | `/api/v1/audit` | logs / logs/{id} / screenshots / export / stats |
| dashboard | `/api/v1/dashboard` | overview / trends / business-lines / errors / costs / approvals / export |
| llm | `/api/v1/llm` | cache/stats / cache/task/{id} / cache/expired / needs-human / needs-human/{id}/resolve / router/config / router/stats |
| agent | `/api/v1/tasks` | list / detail / state / resume / abort |
| skills | `/api/v1/skills` | list / detail / create / update |
| workflows | `/api/v1/workflows` | list / detail / create / update / run / runs / runs/{id} / validate |
| notification | `/api/v1/notification` | channels / channels/{type} / test |

### 9.2 Java 后端 API（内部，Python 调用）

| 端点 | 用途 |
|------|------|
| `POST /api/v1/internal/audit/logs` | 上报审计日志 |
| `POST /api/v1/internal/tasks/{id}/state` | 更新任务状态 |
| `POST /api/v1/internal/tasks/{id}/subtasks` | 更新子任务状态 |
| `POST /api/v1/internal/llm/calls` | 记录 LLM 调用 |
| `POST /api/v1/internal/llm/needs-human` | 转人工接管 |
| `POST /api/v1/internal/screenshots` | 上传截图 |

> 内部 API 走服务间鉴权（共享密钥 Header `X-Internal-Token`），Nginx 仅允许内网访问。

### 9.3 Python AI 服务 API（Java 调用）

| 端点 | 用途 |
|------|------|
| `POST /api/v1/ai/tasks` | 触发任务执行 |
| `GET /api/v1/ai/tasks/{id}/state` | 查询任务状态 |
| `POST /api/v1/ai/tasks/{id}/resume` | 断点续跑 |
| `POST /api/v1/ai/tasks/{id}/abort` | 终止任务 |
| `POST /api/v1/ai/risk/judge` | LLM 风险二次判断 |
| `GET /api/v1/ai/sse/tasks/{id}` | SSE 订阅执行流 |
| `POST /api/v1/ai/skills/validate` | Skill 参数校验 |

---

## 10. 部署架构

### 10.1 Docker Compose 服务编排

```yaml
# docker-compose.yml（开发环境）
services:
  postgres:        # PostgreSQL 14-alpine
  redis:           # Redis 7-alpine
  minio:           # MinIO latest
  finance-backend: # Java 21 (eclipse-temurin:21-jre-alpine)
  finance-ai:      # Python 3.11-slim + Playwright
  finance-frontend:# nginx:alpine (静态资源)
  nginx:           # Nginx 1.25 反向代理

# docker-compose.prod.yml（生产 overlay）
# - 启用 HTTPS
# - 资源限制
# - 日志卷挂载
# - 健康检查强化
```

### 10.2 启动顺序

```
1. postgres + redis + minio（数据层就绪）
2. finance-backend（Flyway 执行 enterprise_* 迁移）
3. finance-ai（Alembic 执行 skyvern_* 迁移，Playwright 安装浏览器）
4. finance-frontend（构建静态资源）
5. nginx（反向代理就绪）
6. 健康检查全链路通过
```

### 10.3 Nginx 路由策略

```nginx
/api/v1/auth/*      → finance-backend:8080
/api/v1/tenant/*    → finance-backend:8080
/api/v1/approvals/* → finance-backend:8080
/api/v1/audit/*     → finance-backend:8080
/api/v1/dashboard/* → finance-backend:8080
/api/v1/llm/*       → finance-backend:8080
/api/v1/tasks/*     → finance-backend:8080
/api/v1/skills/*    → finance-backend:8080
/api/v1/workflows/* → finance-backend:8080
/api/v1/notification/* → finance-backend:8080

/api/v1/ai/sse/*    → finance-ai:8000（SSE 透传，proxy_buffering off）
/api/v1/ai/*        → finance-backend:8080（Java 转发，统一鉴权）

/                   → finance-frontend:80（静态资源）
```

### 10.4 健康检查

| 服务 | 端点 |
|------|------|
| finance-backend | `GET /actuator/health` |
| finance-ai | `GET /api/v1/ai/health` |
| finance-frontend | `GET /` |
| postgres | `pg_isready -U skyvern` |
| redis | `redis-cli ping` |
| minio | `mc ready local` |

---

## 11. 关键设计决策

### ADR-001 管理与执行分离（Java vs Python）

- **决策**：Java 后端负责"管理 / 状态 / 编排 / 检索"，Python AI 服务负责"浏览器 / LLM / Skill 执行"。
- **理由**：Java 生态适合企业 CRUD + 事务 + 权限；Python 生态适合 LLM + 浏览器；分离后可独立扩展。
- **代价**：跨语言通信引入网络开销，通过 HTTP REST + SSE 承载。

### ADR-002 approval 模块拆分（关键词预筛在 Java，LLM 判断在 Python）

- **决策**：阶段 1 关键词预筛放 Java（规则引擎），阶段 2 LLM 判断放 Python。
- **理由**：关键词预筛是纯规则，Java 性能更好且可快速拦截；只有命中时才调 Python 做 LLM 判断，减少跨语言调用。
- **代价**：approval 模块跨语言，需明确接口契约。

### ADR-003 agent 状态权威在 Java，执行在 Python

- **决策**：CoordinationState、Task、SubTask 持久化在 Java（PostgreSQL `enterprise_agent_*`），Python 仅执行。
- **理由**：任务状态是企业数据，需要权限与审计；Java 是数据权威；Python 无状态便于扩展。
- **代价**：Python 每步执行需 HTTP 回调 Java 更新状态，增加延迟（可接受，<10ms）。

### ADR-004 审计截图统一走 Java 转发 MinIO

- **决策**：Python 不直接写 MinIO，而是通过 Java 内部 API 转发。
- **理由**：MinIO 凭证集中管理；Java 可统一脱敏、签名、记录；未来切换存储后端只改 Java。
- **代价**：截图多一跳网络，单张 < 500ms 可接受。

### ADR-005 LLM 缓存读写由 Python 负责，统计与清理由 Java 负责

- **决策**：Action 缓存的读写（执行时）在 Python，缓存统计与清理 API 在 Java。
- **理由**：读写需要低延迟，本地 Redis 操作；统计与清理是管理操作，归 Java。
- **代价**：两侧共享 Redis key 前缀约定，需文档化。

### ADR-006 Skill 元数据归 Java，实现归 Python

- **决策**：Skill 的注册、CRUD、版本管理在 Java（`enterprise_skill_meta` 表），7 个 Skill 实现在 Python。
- **理由**：元数据是配置数据，归 Java 管理；实现是代码，归 Python。
- **代价**：Skill 注册需 Java 同步 Python 校验存在性。

### ADR-007 内部 API 走共享密钥鉴权

- **决策**：Python → Java 的 `/api/v1/internal/*` 路径走 `X-Internal-Token` Header 鉴权。
- **理由**：避免引入 OAuth 复杂性；网络层隔离（Docker 内网）+ 共享密钥足够。
- **代价**：密钥管理需谨慎，通过环境变量注入。

### ADR-008 Skyvern 核心通过 volume 挂载最小改动

- **决策**：Skyvern 核心代码改动通过 Docker volume 挂载方式注入，不直接修改源码。
- **理由**：保留 Skyvern 升级能力；改动可追溯。
- **代价**：volume 挂载路径需与镜像内路径严格对应。

---

## 12. 附录

### 12.1 与原项目模块映射

| 原项目（Python 单体） | 本项目（Java + Python 双服务） |
|------------------------|-------------------------------|
| `enterprise/auth/*` | Java `finance-backend/auth/*` |
| `enterprise/tenant/*` | Java `finance-backend/tenant/*` |
| `enterprise/approval/risk_detector.py` + `risk_keywords.py` | Java `approval/keywords/*` + `RiskDetectService` |
| `enterprise/approval/routing.py` + `pubsub.py` | Java `approval/ApprovalRouteService` + `pubsub/*` |
| `enterprise/audit/*` | Java `audit/*` + Python `app/audit/reporter.py`（回调客户端） |
| `enterprise/dashboard/*` | Java `dashboard/*` |
| `enterprise/llm/resilient_caller.py` + `action_cache.py` + `model_router.py` | Python `app/llm/*`（执行） + Java `llm/*`（统计/配置） |
| `enterprise/llm/human_intervention.py` + `task_states.py` | Java `llm/NeedsHumanService` + Python `llm/task_states.py` |
| `enterprise/agent/*` | Python `app/agent/*`（执行） + Java `agent/*`（状态持久化） |
| `enterprise/skills/*` | Python `app/skills/*`（实现） + Java `skills/*`（元数据） |
| `enterprise/workflows/*` | Java `workflows/*`（模板管理） + Python `app/workflows/runner.py`（执行） |
| `enterprise/notification/*` | Java `notification/*` |
| `enterprise/auth/bridge.py` | Java `auth/AuthBridge` |
| `skyvern/*` | Python `finance-ai/skyvern/*`（最小改动） |
| `skyvern-frontend/*` | `finance-frontend/*`（复刻） |

### 12.2 测试矩阵

| 端 | 类型 | 框架 | 数量目标 | 覆盖率 |
|----|------|------|----------|--------|
| Java | 单元测试 | JUnit 5 + Mockito | ≥ 300 | ≥ 80% |
| Java | 集成测试 | TestContainers + Spring Boot Test | ≥ 40 | ≥ 60% |
| Python | 单元测试 | pytest + pytest-asyncio | ≥ 200 | ≥ 85% |
| Python | 集成测试 | pytest + fakeredis | ≥ 30 | - |
| 前端 | 组件测试 | Vitest + @testing-library/react | ≥ 60 | 关键路径 |
| 端到端 | E2E | Playwright | 6 个金融场景 | - |

### 12.3 修订记录

| 版本 | 日期 | 修订人 | 说明 |
|------|------|--------|------|
| v1.0 | 2026-07-25 | - | 初稿，覆盖整体架构、模块划分、目录结构、跨语言协作、核心模块详设、关键流程、数据模型概要、API 划分、部署架构与 ADR |

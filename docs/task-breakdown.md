# FinanceRPA 任务拆分与规划

> 基于 Skyvern 二次开发的金融级 AI 浏览器自动化平台
> Java 后端 + Python AI 服务 + React 前端 的跨语言项目任务规划

| 项 | 内容 |
|----|------|
| 文档版本 | v1.1 |
| 创建日期 | 2026-07-25 |
| 文档状态 | 初稿，待评审 |
| 关联文档 | [requirements.md](./requirements.md) · [tech-stack.md](./tech-stack.md) · [system-design.md](./system-design.md) |

> **文档范围说明**：本文档覆盖里程碑划分、任务拆分、依赖关系、关键路径与风险缓解。每个任务标注规模（S/M/L/XL）但不含工时估算，工时由后续排期确定。

---

## 1. 拆分原则

| 原则 | 说明 |
|------|------|
| **垂直切片优先** | 优先打通端到端最小闭环（登录 → 触发任务 → 执行 → 看到结果），再横向扩展功能 |
| **依赖驱动排序** | 后续任务依赖前置任务产出，严格按依赖关系排序 |
| **可独立验收** | 每个任务有明确产出物与验收标准，可独立演示 |
| **粒度适中** | 单任务规模控制在 S/M/L/XL，避免过细或过粗 |
| **跨语言并行** | 同一里程碑内 Java / Python / 前端任务尽量并行 |
| **测试同步** | 实现任务与单元测试任务捆绑，不单独拆分测试阶段 |

**规模定义**：
- **S**：单一模块小改动，1 个文件级影响
- **M**：单一模块完整功能，多文件影响
- **L**：跨模块或多服务协作
- **XL**：跨多个里程碑影响，需分阶段

---

## 2. 里程碑总览

```
M0 基础设施            M1 认证与多租户       M2 任务执行闭环（MVP）
    │                       │                       │
    ▼                       ▼                       ▼
[骨架+环境+DB]         [JWT+RBAC+登录]       [触发→执行→SSE→展示]
                                                    │
                                                    ▼
                            M3 Skill 库与工作流 ◄────┘
                                    │
                                    ▼
                            M4 双 Agent 协作
                                    │
                                    ▼
                            M5 LLM 韧性与成本控制
                                    │
                                    ▼
                            M6 审批引擎
                                    │
                                    ▼
                            M7 全链路审计
                                    │
                                    ▼
                            M8 运营大屏
                                    │
                                    ▼
                            M9 集成测试与部署
```

| 里程碑 | 名称 | 核心产出 | 演示能力 |
|--------|------|----------|----------|
| **M0** | 基础设施 | 三大子项目骨架 + Docker Compose 环境 + 数据库迁移脚本 | `docker-compose up` 一键启动空壳 |
| **M1** | 认证与多租户 | Java auth + tenant 模块 + 前端登录页 + 演示数据 | 5 种角色登录，看到不同菜单 |
| **M2** | 任务执行闭环（MVP） | Skyvern 集成 + Java↔Python 通信 + 任务执行 + SSE + 前端任务页 | 触发简单任务，实时看浏览器执行 |
| **M3** | Skill 库与工作流 | 7 个 Skill + 6 个工作流模板 + Pipeline 执行器 | 跑通一个金融场景（如银行流水下载） |
| **M4** | 双 Agent 协作 | Planner + Executor + Coordinator + 断点续跑 | 多步骤任务自动拆解，中断后续跑 |
| **M5** | LLM 韧性 | 三层容错 + Action 缓存 + 模型路由 + NEEDS_HUMAN | LLM 失败转人工接管，缓存命中降低成本 |
| **M6** | 审批引擎 | 关键词预筛 + LLM 判断 + 审批流 + 超时 + 通知 | 高风险任务走审批，超时自动拒绝 |
| **M7** | 全链路审计 | 审计日志 + 脱敏 + MinIO + CSV 导出 + 多维检索 | 每步操作有截图，可检索可导出 |
| **M8** | 运营大屏 | 统计 API + Redis 缓存 + ECharts 大屏 | 看任务成功率/LLM 成本/审批时长等指标 |
| **M9** | 集成测试与部署 | E2E 测试 + 性能测试 + 生产 Docker Compose | 6 个金融场景全链路验收，生产可部署 |

---

## 3. 任务依赖关系图

```
                    ┌──────────┐
                    │   M0.1   │ 三大子项目骨架
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
        ┌─────────┐ ┌─────────┐ ┌─────────┐
        │  M0.2   │ │  M0.3   │ │  M0.4   │
        │ Docker  │ │ DB 迁移 │ │ Nginx   │
        │ Compose │ │ 脚本    │ │ 路由    │
        └────┬────┘ └────┬────┘ └────┬────┘
             │           │           │
             └─────┬─────┴───────────┘
                   │
                   ▼
             ┌───────────┐
             │   M1.1    │ Java auth 模块
             └─────┬─────┘
                   │
             ┌─────┴─────┐
             ▼           ▼
       ┌──────────┐ ┌──────────┐
       │  M1.2    │ │  M1.3    │
       │ Java     │ │ 前端登录 │
       │ tenant   │ │ + 路由   │
       └────┬─────┘ └────┬─────┘
            │            │
            └─────┬──────┘
                  │
                  ▼
            ┌───────────┐
            │   M1.4    │ 演示数据脚本
            └─────┬─────┘
                  │
                  ▼
       ┌──────────────────────┐
       │         M2           │ 任务执行闭环（并行展开）
       └──────────────────────┘
                  │
    ┌──────┬──────┼──────┬──────┐
    ▼      ▼      ▼      ▼      ▼
 M2.1   M2.2   M2.3   M2.4   M2.5
 Python Java   Java   Python 前端
 骨架   客户端 agent  执行   任务页
 Skyvern+SSE  持久化 回调
    │      │      │      │      │
    └──────┴──────┼──────┴──────┘
                  │
                  ▼
            ┌───────────┐
            │   M2.6    │ 端到端联调
            └─────┬─────┘
                  │
                  ▼
            ┌───────────┐
            │    M3     │ Skill 库与工作流
            └─────┬─────┘
                  │
                  ▼
            ┌───────────┐
            │    M4     │ 双 Agent 协作
            └─────┬─────┘
                  │
                  ▼
            ┌───────────┐
            │    M5     │ LLM 韧性
            └─────┬─────┘
                  │
                  ▼
            ┌───────────┐
            │    M6     │ 审批引擎
            └─────┬─────┘
                  │
                  ▼
            ┌───────────┐
            │    M7     │ 全链路审计
            └─────┬─────┘
                  │
                  ▼
            ┌───────────┐
            │    M8     │ 运营大屏
            └─────┬─────┘
                  │
                  ▼
            ┌───────────┐
            │    M9     │ 集成测试与部署
            └───────────┘
```

> M3-M8 之间存在部分并行可能，详见各任务依赖标注。

---

## 4. 详细任务列表

### M0 基础设施

#### M0.1 三大子项目骨架搭建

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | 无 |
| **产出物** | `finance-backend/`（Maven + Spring Boot 3.2 + Java 21）、`finance-ai/`（uv + FastAPI + Python 3.11）、`finance-frontend/`（Vite + React 18 + TS）、顶层 `Makefile` + `.env.example` |
| **描述** | 1. Java：`pom.xml` 配置 Spring Boot 3.2 / MyBatis-Plus / Flyway / Redisson / jjwt / springdoc；启动类 `FinRpaApplication`；`application.yml` 基础配置<br>2. Python：`pyproject.toml` 配置 FastAPI / Uvicorn / SQLAlchemy / Playwright / LiteLLM；`app/main.py` FastAPI 入口<br>3. 前端：`package.json` 配置 React / Vite / Tailwind / Zustand / React Query；`App.tsx` 根组件<br>4. 顶层 Makefile：`make dev` / `make build` / `make test` 入口 |
| **验收标准** | 三个子项目能独立启动（Java 8080、Python 8000、前端 8081）；健康检查端点返回 200 |

#### M0.2 Docker Compose 环境搭建

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.1 |
| **产出物** | `docker-compose.yml`（开发）、`docker-compose.prod.yml`（生产 overlay）、各服务 Dockerfile |
| **描述** | 1. 6 服务编排：postgres / redis / minio / finance-backend / finance-ai / finance-frontend + nginx<br>2. 数据卷：`postgres-data` / `redis-data` / `minio-data`<br>3. 健康检查：全链路 healthcheck<br>4. 启动顺序：depends_on + condition: service_healthy<br>5. Python 镜像装 Playwright 浏览器依赖 |
| **验收标准** | `docker-compose up -d` 一键启动全部服务；所有健康检查通过；服务间网络互通 |

#### M0.3 数据库迁移脚本初始化

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.1 |
| **产出物** | Java `db/migration/` 下 10 个 Flyway 脚本（占位空表）；Python `alembic/` 配置 + skyvern_* 表迁移 |
| **描述** | 1. Java：按 system-design 8.1 节表前缀创建 10 个 Flyway 脚本骨架（`V20260725_001__auth__init_schema.sql` 等），先建空表结构<br>2. Python：Alembic 初始化，复刻 Skyvern 原项目 skyvern_* 表迁移<br>3. 共享枚举字典表（risk_level / role_type / task_state）由 Java 侧 Flyway 创建<br>4. 双方迁移脚本边界严格隔离（前缀校验） |
| **验收标准** | Java 服务启动 Flyway 执行成功；Python 服务启动 Alembic 执行成功；`psql` 能查到全部表 |

#### M0.4 Nginx 反向代理配置

| 项 | 内容 |
|----|------|
| **规模** | S |
| **前置依赖** | M0.1 |
| **产出物** | `nginx/nginx.conf`、`nginx/conf.d/default.conf` |
| **描述** | 1. 路由规则按 system-design 10.3 节<br>2. SSE 透传：`proxy_buffering off` / `proxy_read_timeout 3600s`<br>3. gzip 压缩 + 安全头（X-Frame-Options / X-Content-Type-Options 等）<br>4. HTTPS 预留配置（生产 overlay 启用） |
| **验收标准** | Nginx 启动后，前端访问 `/` 返回静态资源；`/api/v1/*` 转发到 Java；`/api/v1/ai/sse/*` 透传 SSE |

---

### M1 认证与多租户基座

#### M1.1 Java auth 模块实现

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M0.1、M0.3 |
| **产出物** | `auth/` 模块完整代码 + 单元测试 |
| **描述** | 1. 实体：`UserEO` / `RoleEO` / `UserDepartmentRoleEO` / `OrganizationEO` / `DepartmentEO` / `BusinessLineEO`<br>2. JWT 服务：jjwt 0.12 签发 access（60min）+ refresh（7d）<br>3. 权限解析：按 system-design 6.1.2 算法实现 `PermissionService`<br>4. 角色互斥约束：`RoleMutexValidator`（operator + approver 不可共存）<br>5. Spring Security 6 配置：过滤器链 + 方法级 `@RequirePermission`<br>6. Skyvern 桥接：`AuthBridge` 将企业 JWT 转为 Skyvern API Key<br>7. API：`POST /auth/login` / `POST /auth/refresh` / `GET /auth/me` / `POST /auth/permissions/check` |
| **验收标准** | 单元测试覆盖率 ≥ 80%；5 种角色登录返回正确权限；互斥约束拦截非法角色组合 |

#### M1.2 Java tenant 模块实现

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M1.1 |
| **产出物** | `tenant/` 模块完整代码 + 单元测试 |
| **描述** | 1. `TenantContext`：基于 Java 21 ScopedValue（或 ThreadLocal 兜底）<br>2. `TenantInterceptor`：从 JWT 解析 organization_id 注入上下文<br>3. MyBatis-Plus `TenantLineInnerInterceptor` + `TenantLineHandler`：自动追加 `WHERE organization_id = ?`<br>4. 忽略表配置：`skyvern_*` 与 `enterprise_organization` 不参与过滤<br>5. API：`GET /tenant/info` / `GET /tenant/departments` / `GET /tenant/business-lines` |
| **验收标准** | 跨组织查询自动过滤；同一用户切换组织后查询结果隔离；忽略表不被追加条件 |

#### M1.3 前端登录页与路由守卫

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M1.1（API 契约确定） |
| **产出物** | 前端 `routes/auth/LoginPage.tsx`、`components/AuthGuard.tsx`、`store/AuthStore.ts`、`api/AxiosClient.ts` |
| **描述** | 1. 登录页：用户名密码 + 毛玻璃风格<br>2. Axios 拦截器：自动附加 JWT、401 自动 refresh<br>3. AuthGuard：未登录跳转、权限不足提示<br>4. Zustand AuthStore：token / userInfo / permissions<br>5. 路由配置：基于权限的菜单渲染 |
| **验收标准** | 5 种角色登录后看到不同菜单；token 过期自动 refresh；无权限访问路由被拦截 |

#### M1.4 演示数据生成器

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M1.1、M1.2 |
| **产出物** | `scripts/seed_demo_data.py` 演示数据生成器（可重复执行、幂等、支持参数化） |
| **描述** | 1. 1 个组织 + 5 个部门（对公信贷/个人金融/资产管理/风险管理/合规审计）+ 4 条业务线<br>2. 5 种角色用户：super_admin / org_admin / operator / approver / viewer<br>3. 角色与部门、业务线关联（含跨部门只读场景）<br>4. 互斥约束验证数据（operator 与 approver 不同部门）<br>5. 默认密码 BCrypt 哈希（与原项目兼容）<br>6. **生成器特性**：支持 `--reset` 清空重建、`--only=users` 按模块生成、`--count=N` 批量生成任务数据、生成结果报告<br>7. 对应 6 个金融场景的演示任务数据（任务、审批、审计日志样本） |
| **验收标准** | 生成器可重复执行不报错；5 种角色均能登录；权限解析返回预期结果；互斥约束数据正确；`--reset` 后数据干净重建 |

#### M1.5 前端 UI 系统改造（毛玻璃 + SVG 图标）

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M0.1 |
| **产出物** | `finance-frontend/src/styles/`（设计 token + 毛玻璃样式）、`src/components/Icon/`（21 个手写 SVG 图标）、`src/components/ui/`（基础组件库） |
| **描述** | 1. 设计 token：CSS 变量定义颜色 / 间距 / 圆角 / 阴影 / 字体（`styles/variables.css`）<br>2. 毛玻璃样式：`backdrop-filter: blur()` + 渐变背景 + 半透明卡片（`styles/glass.css`）<br>3. **21 个手写 SVG 图标组件**：BagIcon / BookIcon / BrainIcon / BugIcon / CartIcon / ClickIcon / DebugIcon / FolderIcon / GraphIcon / InboxIcon / KeyIcon / OutputIcon / PowerIcon / QRCodeIcon / RobotIcon / SaveIcon / ToolIcon / 等不依赖外部图标库<br>4. 基础组件库：基于 Radix UI primitives + shadcn/ui 风格自实现 Button / Dialog / Input / Select / Table / Toast / Tooltip / Accordion<br>5. 主题切换：暗色 / 亮色模式预留 |
| **验收标准** | 21 个图标可独立导入使用；毛玻璃效果在主流浏览器生效；基础组件覆盖常用交互；设计 token 统一 |

#### M1.6 全站 i18n 国际化

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M1.5 |
| **产出物** | `finance-frontend/src/i18n/locales.ts`（中英文文案）、`useI18n.ts`（Hook）、全站文案替换 |
| **描述** | 1. 文案字典：中文（默认）+ 英文，覆盖菜单 / 表单 / 按钮 / 提示 / 错误信息<br>2. `useI18n` Hook：基于 Zustand 管理语言状态<br>3. 全站文案替换：硬编码字符串迁移到 i18n 字典<br>4. 语言切换：右上角语言切换按钮<br>5. 后端错误码国际化：Java 错误码 → 前端文案映射 |
| **验收标准** | 中英文切换全站生效；无硬编码中文字符串；后端错误信息可国际化 |

---

### M2 任务执行闭环（MVP）

> 本里程碑是端到端最小闭环，验证 Java ↔ Python ↔ 前端 协作链路。

#### M2.1 Python finance-ai 服务骨架 + Skyvern 集成

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M0.1、M0.3 |
| **产出物** | `finance-ai/app/` 骨架 + Skyvern 集成 |
| **描述** | 1. FastAPI 路由：`POST /api/v1/ai/tasks` / `GET /api/v1/ai/tasks/{id}/state` / `GET /api/v1/ai/sse/tasks/{id}` / `GET /api/v1/ai/health`<br>2. Skyvern 集成：volume 挂载 Skyvern 源码，最小改动接入 ForgeAgent<br>3. Playwright 浏览器会话管理：`browser/session_manager.py`<br>4. 简单 Executor：接收任务 → 调用 Skyvern 执行 → SSE 推送进度<br>5. 配置：`config.py` 使用 pydantic-settings，从环境变量读取 |
| **验收标准** | `POST /api/v1/ai/tasks` 能触发 Skyvern 执行一个简单导航任务；SSE 推送 step 事件；`/health` 返回 200 |

#### M2.2 Java↔Python HTTP 客户端 + SSE 透传

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.1 |
| **产出物** | Java `ai/` 模块完整代码 |
| **描述** | 1. `AiServiceClient`：Spring 6 HTTP Interface 声明式客户端，调用 Python API<br>2. `AiSseProxy`：透传 Python SSE 流到前端，附加鉴权与审计<br>3. Spring Retry：瞬态错误重试 3 次，指数退避<br>4. W3C Trace Context：traceparent header 透传<br>5. 配置：Python 服务地址、超时、重试参数 |
| **验收标准** | Java 调 Python 接口成功；SSE 流透传到前端无丢失；Python 服务不可用时返回 503 + 重试 |

#### M2.3 Java agent 模块（任务状态持久化）

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M1.1、M1.2、M0.3 |
| **产出物** | `agent/` 模块完整代码 + 单元测试 |
| **描述** | 1. 实体：`TaskEO` / `SubTaskEO` / `CoordinationStateEO`<br>2. `TaskStateMachine`：状态机（pending → executing → success/failed/needs_human）<br>3. `TaskService`：创建任务、查询状态、更新状态<br>4. 内部 API（Python 回调）：`POST /internal/tasks/{id}/state` / `POST /internal/tasks/{id}/subtasks`<br>5. 对外 API：`GET /api/v1/tasks` / `GET /api/v1/tasks/{id}` / `POST /api/v1/tasks/{id}/abort`<br>6. 共享密钥鉴权：`X-Internal-Token` Header 校验 |
| **验收标准** | 任务创建/查询/状态更新正常；Python 回调能更新状态；内部 API 鉴权生效 |

#### M2.4 Python Executor 基础执行 + 状态回调

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M2.1、M2.3（API 契约确定） |
| **产出物** | `app/agent/executor.py` + `app/clients/java_backend.py` |
| **描述** | 1. Executor：接收任务 → 调 Skyvern → 每步回调 Java 更新状态<br>2. `JavaBackendClient`：httpx 客户端，调用 Java 内部 API<br>3. 状态同步：每步执行后上报 Java（含截图元数据）<br>4. SSE 推送：执行进度实时推送给 Java 透传<br>5. 错误处理：执行失败上报终态 |
| **验收标准** | 任务执行全程状态在 Java 侧可查；截图元数据上报成功；SSE 进度推送无丢失 |

#### M2.5 前端任务列表与详情页

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M1.3、M2.3（API 契约确定） |
| **产出物** | `routes/tasks/TasksPage.tsx`、`routes/tasks/TaskDetail.tsx`、`components/BrowserStream.tsx` |
| **描述** | 1. 任务列表：分页 + 状态筛选 + 搜索<br>2. 任务详情：基本信息 + 子任务时间线 + 操作日志<br>3. 浏览器实时流：基于 @novnc/novnc + SSE 接收 Skyvern 截图流<br>4. 触发任务入口：简易表单（导航目标 URL + 参数）<br>5. 状态徽章：StatusBadge 组件 |
| **验收标准** | 任务列表正确展示；详情页时间线实时更新；浏览器流可见；触发任务后能看到执行过程 |

#### M2.6 端到端联调

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.1、M2.2、M2.3、M2.4、M2.5 |
| **产出物** | 联调问题清单 + 修复 |
| **描述** | 1. 完整链路：前端触发 → Java 鉴权+创建任务 → Python 执行 → SSE 透传 → 前端展示<br>2. 验证：状态一致性、SSE 连通性、截图上传、错误传播<br>3. 性能基线：单任务执行延迟、SSE 推送延迟 |
| **验收标准** | 端到端跑通一个简单导航任务；无状态丢失；SSE 延迟 < 1s |

---

### M3 Skill 库与工作流

#### M3.1 Python 7 个 Skill 实现

| 项 | 内容 |
|----|------|
| **规模** | XL |
| **前置依赖** | M2.1 |
| **产出物** | `app/skills/` 完整代码 + 单元测试 |
| **描述** | 1. `base.py`：BaseSkill 抽象基类 + SkillContext + ExecutionResult<br>2. `auth_skills.py`：LoginSkill（用户名密码 + 2FA + TOTP）、SessionKeepAliveSkill<br>3. `interaction_skills.py`：FormFillSkill、SearchAndSelectSkill、PaginationSkill<br>4. `extraction_skills.py`：TableExtractSkill、FileDownloadSkill<br>5. 每个 Skill 实现 `execute()` + `get_failure_strategy()`<br>6. 参数模型：Pydantic BaseModel 定义每个 Skill 的参数 |
| **验收标准** | 7 个 Skill 单元测试覆盖率 ≥ 85%；每个 Skill 能独立执行；失败策略返回正确 |

#### M3.2 Python Skill Pipeline 执行器

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M3.1 |
| **产出物** | `app/skills/executor.py` |
| **描述** | 1. Pipeline 执行器：按顺序执行 Skill 列表<br>2. 参数映射：引用模式 `{{workflow.params.xxx}}` + 字面量模式<br>3. 上下文传递：前一个 Skill 输出可作为后一个 Skill 输入<br>4. 失败处理：按 Skill 的 `get_failure_strategy()` 处理<br>5. 审计回调：每步执行上报 Java |
| **验收标准** | Pipeline 能串联多个 Skill；参数映射正确；失败策略生效 |

#### M3.3 Java skills 元数据管理

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.3、M3.1（Skill 定义确定） |
| **产出物** | `skills/` 模块完整代码 |
| **描述** | 1. 实体：`SkillMetaEO`（name / description / category / param_schema / version / enabled）<br>2. `SkillRegistryService`：CRUD + 版本管理<br>3. 注册时同步 Python 校验 Skill 存在性<br>4. API：`GET /api/v1/skills` / `GET /api/v1/skills/{name}` / `POST /api/v1/skills` / `PUT /api/v1/skills/{name}`<br>5. 初始化：启动时自动注册 7 个内置 Skill 元数据 |
| **验收标准** | 7 个 Skill 元数据自动注册；CRUD 接口正常；Python 校验存在性生效 |

#### M3.4 Java workflows 模板管理 + Fernet 加密

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M3.3、M1.1（Fernet 加密依赖） |
| **产出物** | `workflows/` 模块完整代码 |
| **描述** | 1. 实体：`WorkflowTemplateEO`（name / industry / risk_level / params[] / steps[]）<br>2. `WorkflowService`：CRUD + 校验<br>3. `FernetCryptoService`：与 Python cryptography 字节级兼容<br>4. `WorkflowValidator`：校验 Skill 引用合法性、参数完整性<br>5. `WorkflowTriggerService`：触发执行 → 解密参数 → 创建任务 → 调 Python<br>6. API：`GET /api/v1/workflows` / `POST /api/v1/workflows` / `POST /api/v1/workflows/{id}/run` / `GET /api/v1/workflows/{id}/runs` |
| **验收标准** | 模板 CRUD 正常；敏感参数加密存储；触发执行创建任务成功；Java 解密结果与 Python 加密一致 |

#### M3.5 6 个金融工作流模板配置

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M3.4、M3.1 |
| **产出物** | 6 个工作流模板数据（seed 脚本） |
| **描述** | 按系统设计 6.8.2 节配置 6 个模板：银行流水下载 / 跨行转账核对 / 对公贷款放款 / 保单申请填写 / 理赔审核提交 / 委托下单 |
| **验收标准** | 6 个模板可通过 API 查询；参数 schema 完整；Skill 引用合法 |

#### M3.6 前端工作流管理页面

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M3.4（API 契约确定） |
| **产出物** | `routes/workflows/Workflows.tsx`、`routes/workflows/WorkflowRuns.tsx` |
| **描述** | 1. 工作流列表：按行业筛选 + 搜索<br>2. 详情页：参数表单 + Skill 步骤可视化<br>3. 触发执行：表单填写参数 → 调 `/run` → 跳转任务详情<br>4. 执行历史：分页 + 状态筛选 |
| **验收标准** | 列表展示 6 个模板；参数表单按 schema 动态生成；触发后跳转任务详情 |

---

### M4 双 Agent 协作

#### M4.1 Python Planner 任务拆解

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M2.4 |
| **产出物** | `app/agent/planner.py` + 单元测试 |
| **描述** | 1. Planner：接收导航目标 → LLM 拆解为子任务列表<br>2. 数据结构：`TaskPlan` / `SubTask`（goal / completion_condition / max_retries / failure_strategy）<br>3. replan：接收失败上下文 → 重新规划剩余子任务<br>4. replan 上限：3 次，超限转 NEEDS_HUMAN |
| **验收标准** | Planner 能拆解复杂目标为合理子任务；replan 能根据失败原因调整；上限拦截生效 |

#### M4.2 Python Coordinator 编排 + 失败策略

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M4.1、M3.2 |
| **产出物** | `app/agent/coordinator.py` + 单元测试 |
| **描述** | 1. Coordinator：编排 Planner 与 Executor 通信<br>2. 失败策略：retry / skip / abort / replan 四种处理<br>3. 状态持久化：每步执行后回调 Java 更新 `CoordinationState`<br>4. replan 触发：路径阻塞时调 Planner 重新规划 |
| **验收标准** | 四种失败策略正确执行；replan 触发后任务继续；状态持久化无丢失 |

#### M4.3 Java 断点续跑状态管理

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.3、M4.2 |
| **产出物** | `agent/CoordinationStateService.java` 增强 + API |
| **描述** | 1. `CoordinationStateService`：查询 `last_success_subtask_index`<br>2. 续跑 API：`POST /api/v1/tasks/{id}/resume`<br>3. 调 Python `POST /api/v1/ai/tasks/{id}/resume`（从 index+1 开始）<br>4. replan_count 重置逻辑 |
| **验收标准** | 中断任务可从断点续跑；不重做已完成子任务；replan_count 重置正确 |

#### M4.4 前端子任务时间线展示

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.5、M4.2 |
| **产出物** | `components/Timeline.tsx` 增强、`routes/tasks/TaskDetail.tsx` 增强 |
| **描述** | 1. 子任务时间线：垂直时间轴展示每个子任务状态<br>2. replan 标记：可视化展示 replan 发生点<br>3. 续跑按钮：任务中断后可点击续跑<br>4. 子任务详情：点击查看子任务参数与结果 |
| **验收标准** | 时间线实时更新；replan 点可见；续跑按钮可用 |

---

### M5 LLM 韧性与成本控制

#### M5.1 Python 三层容错调用

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M2.1 |
| **产出物** | `app/llm/resilient_caller.py` + 单元测试 |
| **描述** | 1. 层 1：Prompt 强制 JSON Schema 约束 + few-shot 示例<br>2. 层 2：Pydantic 校验 + 失败反馈 LLM 重试（默认 2 次）<br>3. 层 3：转 NEEDS_HUMAN 状态<br>4. 上报 Java：调用记录 + NEEDS_HUMAN 事件 |
| **验收标准** | 正常 LLM 返回通过校验；格式错误自动重试；重试耗尽转 NEEDS_HUMAN |

#### M5.2 Python Action 缓存读写

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M5.1 |
| **产出物** | `app/llm/action_cache.py` + 单元测试 |
| **描述** | 1. 缓存 Key：DOM 结构哈希（剥除动态内容）+ 导航目标哈希<br>2. Redis 读写：TTL 24 小时<br>3. 命中流程：查缓存 → 命中直接返回 → 未命中调 LLM → 写缓存<br>4. 上报 Java：调用记录标记 `cache_hit` |
| **验收标准** | 相同页面结构命中缓存；缓存未命中时调 LLM 并写入；TTL 过期自动失效 |

#### M5.3 Python 模型路由执行

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M5.1 |
| **产出物** | `app/llm/model_router.py` + 单元测试 |
| **描述** | 1. 页面复杂度评分：DOM 节点数 / 表单字段数 / 动态元素数 / 截图熵<br>2. 路由规则：score < 30 轻量 / 30-70 标准 / ≥ 70 重型<br>3. 从 Java 读取路由策略配置<br>4. 上报 Java：调用记录标记使用模型 |
| **验收标准** | 不同复杂度页面路由到不同模型；策略可从 Java 配置更新；调用记录正确 |

#### M5.4 Java LLM 调用记录与统计

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.3、M5.1 |
| **产出物** | `llm/` 模块（统计部分） |
| **描述** | 1. 实体：`LlmCallLogEO`（task_id / model / tokens / cost / cache_hit / timestamp）<br>2. 内部 API：`POST /internal/llm/calls`（Python 上报）<br>3. 统计 API：`GET /api/v1/llm/calls/stats`（按时间/模型/任务维度）<br>4. 成本计算：按模型 token 单价计算 |
| **验收标准** | 调用记录持久化；统计 API 返回正确；成本计算准确 |

#### M5.5 Java NEEDS_HUMAN 队列管理

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.3、M5.1 |
| **产出物** | `llm/NeedsHumanService.java` + API |
| **描述** | 1. 实体：`NeedsHumanQueueEO`（task_id / subtask_id / screenshot_url / llm_raw_output / validation_error / status）<br>2. 内部 API：`POST /internal/llm/needs-human`（Python 上报）<br>3. 处置 API：`POST /api/v1/llm/needs-human/{id}/resolve`（action: skip/manual/abort）<br>4. 处置后调 Python resume |
| **验收标准** | NEEDS_HUMAN 事件入队；操作员可查看详情；处置后任务继续或终止 |

#### M5.6 前端 NEEDS_HUMAN 接管页面 + LLM 监控页

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M5.4、M5.5 |
| **产出物** | `routes/enterprise/NeedsHuman.tsx`、`routes/enterprise/LlmMonitor.tsx` |
| **描述** | 1. 接管队列：待处理列表 + 详情（截图 + LLM 原始输出 + 校验错误）<br>2. 处置操作：skip / manual / abort 三按钮<br>3. LLM 监控：调用次数 / 成本 / 缓存命中率 / 模型分布图表<br>4. ECharts 可视化 |
| **验收标准** | 接管队列正确展示；处置按钮可用；LLM 监控图表数据准确 |

---

### M6 审批引擎

#### M6.1 Java 关键词预筛（3 大行业）

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.3 |
| **产出物** | `approval/keywords/*` + `RiskDetectService.java` |
| **描述** | 1. 风险关键词库：银行 / 保险 / 证券 三大行业（参考原项目 `risk_keywords.py`）<br>2. 金额正则检测<br>3. `RiskDetectService`：阶段 1 入口，命中后调 Python 阶段 2<br>4. 配置化：关键词从 DB 或配置文件加载 |
| **验收标准** | 关键词库覆盖 3 大行业；金额检测准确；命中后正确触发 LLM 判断 |

#### M6.2 Python LLM 风险二次判断

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M5.1、M6.1 |
| **产出物** | `app/approval/risk_judge.py` + API |
| **描述** | 1. LLM Prompt：输入目标 + 参数 + 预筛结果 → 输出 final_risk_level<br>2. 走三层容错（复用 M5.1）<br>3. API：`POST /api/v1/ai/risk/judge`（Java 调用）<br>4. 输出：low / medium / high / critical |
| **验收标准** | LLM 判断结果合理；走三层容错；返回正确风险等级 |

#### M6.3 Java 审批流路由 + Pub/Sub

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M6.1、M6.2、M1.1 |
| **产出物** | `approval/` 模块完整代码 |
| **描述** | 1. 实体：`ApprovalRequestEO`（task_id / risk_level / approver_id / status / timeout_at）<br>2. `ApprovalRouteService`：按 risk_level 路由（high → 部门 approver / critical → 合规审计部）<br>3. Redisson Pub/Sub：发布 `approval:requests` / 订阅 `approval:responses`<br>4. API：`GET /api/v1/approvals` / `POST /api/v1/approvals/{id}/approve` / `POST /api/v1/approvals/{id}/reject`<br>5. Python 等待：通过 Pub/Sub 通知 Python Executor |
| **验收标准** | 审批单创建后正确路由；Pub/Sub 消息收发正常；审批结果通知 Python |

#### M6.4 Java 审批超时检测

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M6.3 |
| **产出物** | `approval/ApprovalTimeoutScheduler.java` |
| **描述** | 1. ShedLock + Spring Scheduler：每分钟扫描 pending 审批<br>2. 超时阈值：high 30min / critical 60min<br>3. 超时处理：自动 reject + 推送通知 + 通知 Python |
| **验收标准** | 超时审批自动拒绝；通知推送成功；Python 收到 rejected 终止任务 |

#### M6.5 前端审批中心

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M6.3 |
| **产出物** | `routes/enterprise/ApprovalCenter.tsx` |
| **描述** | 1. 待审批列表：按风险等级排序<br>2. 审批详情：任务信息 + 风险原因 + 截图<br>3. 操作：批准 / 拒绝（含拒绝理由）<br>4. 历史记录：已处理审批 |
| **验收标准** | 列表正确展示；审批操作生效；历史可查 |

#### M6.6 通知模块（企业微信/钉钉）

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M6.3 |
| **产出物** | `notification/` 模块完整代码 |
| **描述** | 1. 通道：企业微信群机器人 + 钉钉群机器人 Webhook<br>2. 模板：审批待处理 / 审批超时 / 任务失败 / NEEDS_HUMAN / 风险升级<br>3. 配置：Webhook URL 从环境变量读取<br>4. API：`GET /api/v1/notification/channels` / `POST /api/v1/notification/test` |
| **验收标准** | 通知发送到指定群；模板渲染正确；测试接口可用 |

---

### M7 全链路审计

#### M7.1 Java 审计日志 CRUD + 脱敏

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M0.3、M1.2 |
| **产出物** | `audit/` 模块（CRUD + 脱敏部分） |
| **描述** | 1. 实体：`AuditLogEO`（按系统设计 6.4.1 结构）<br>2. `SanitizeService`：银行卡 / 身份证 / 密码 / 手机 / 邮箱 脱敏规则<br>3. 内部 API：`POST /internal/audit/logs`（Python 上报）<br>4. 对外 API：`GET /api/v1/audit/logs`（多维检索） / `GET /api/v1/audit/logs/{id}` |
| **验收标准** | 审计日志持久化；脱敏规则正确；多维检索可用 |

#### M7.2 Java MinIO 存储集成

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M7.1、M0.2 |
| **产出物** | `audit/storage/MinioStorageService.java` |
| **描述** | 1. MinIO 客户端：`io.minio:minio`<br>2. Bucket 策略：`finrpa-audit-{organization_id}`<br>3. 路径：`{date}/{task_id}/{step_index}_{before|after}.png`<br>4. 预签名 URL：有效期 1 小时<br>5. 内部 API：`POST /internal/screenshots`（Python 上传） |
| **验收标准** | 截图上传到 MinIO；预签名 URL 可访问；路径规则正确 |

#### M7.3 Python 审计回调客户端

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M7.1、M7.2 |
| **产出物** | `app/audit/reporter.py` |
| **描述** | 1. `AuditReporter`：每步操作前后截图 → 上传 Java → 上报审计元数据<br>2. 截图上传：POST `/internal/screenshots`<br>3. 元数据上报：POST `/internal/audit/logs`<br>4. 失败重试：本地缓存 + 恢复后批量上报 |
| **验收标准** | 每步操作有 before/after 截图；元数据上报成功；Java 失败时本地缓存 |

#### M7.4 Java CSV 导出 + 多维检索增强

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M7.1 |
| **产出物** | `audit/export/CsvExporter.java` + 检索增强 |
| **描述** | 1. CSV 导出：按查询条件导出审计日志<br>2. 多维检索：时间范围 / 任务 / 用户 / 部门 / 业务线 / 风险等级 / 操作类型<br>3. 分页 + 排序<br>4. API：`GET /api/v1/audit/export` / `GET /api/v1/audit/logs`（增强查询参数） |
| **验收标准** | CSV 导出格式正确；多维检索响应 < 500ms；分页准确 |

#### M7.5 前端审计日志页面

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M7.1、M7.4 |
| **产出物** | `routes/enterprise/AuditLogs.tsx` |
| **描述** | 1. 日志列表：多维筛选 + 分页<br>2. 详情：截图对比（before/after）+ 操作参数 + LLM 信息<br>3. 导出按钮：触发 CSV 下载<br>4. 时间线视图：按任务维度展示操作时间线 |
| **验收标准** | 列表筛选正确；详情截图可查看；导出功能可用 |

---

### M8 运营大屏

#### M8.1 Java 统计 API + Redis 缓存

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M2.3、M5.4、M6.3、M7.1 |
| **产出物** | `dashboard/` 模块完整代码 |
| **描述** | 1. 统计指标：按系统设计 6.9.1 节 8 类指标<br>2. Redis 缓存：key `dashboard:{org}:{metric}:{date}`，TTL 5min/1h<br>3. 缓存失效：任务完成时 ApplicationEvent 主动刷新<br>4. API：`GET /api/v1/dashboard/overview` / `trends` / `business-lines` / `errors` / `costs` / `approvals` |
| **验收标准** | 统计数据准确；缓存命中率 > 80%；任务完成后缓存刷新 |

#### M8.2 前端 Dashboard（ECharts）

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M8.1 |
| **产出物** | `routes/enterprise/Dashboard.tsx` |
| **描述** | 1. 概览卡片：任务总数 / 成功率 / LLM 成本 / 接管队列<br>2. 趋势图：任务量趋势 / 成本趋势（ECharts 折线）<br>3. 分布图：业务线分布 / 错误类型分布（ECharts 饼图）<br>4. 实时刷新：定时拉取 + 手动刷新 |
| **验收标准** | 图表数据准确；毛玻璃风格统一；刷新无卡顿 |

---

### M9 集成测试与部署

#### M9.1 端到端测试（6 个金融场景）

| 项 | 内容 |
|----|------|
| **规模** | XL |
| **前置依赖** | M3、M4、M5、M6、M7、M8 全部完成 |
| **产出物** | `tests/e2e/` 6 个场景测试脚本 |
| **描述** | 1. 银行流水下载（medium）<br>2. 跨行转账核对（high，触发审批）<br>3. 对公贷款放款（critical，触发合规审批）<br>4. 保单申请填写（high）<br>5. 理赔审核提交（high）<br>6. 委托下单（high）<br>每个场景验证：触发 → 审批 → 执行 → 审计 → 大屏统计 |
| **验收标准** | 6 个场景全部通过；审计日志完整；大屏数据正确 |

#### M9.2 性能测试

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M9.1 |
| **产出物** | 性能测试报告 |
| **描述** | 1. 单任务执行延迟基线<br>2. 并发任务（10/50/100）吞吐量<br>3. SSE 推送延迟<br>4. 数据库查询性能（审计日志百万级）<br>5. LLM 调用成本基线 |
| **验收标准** | 单任务延迟 < 30s；10 并发稳定运行；审计查询 < 500ms |

#### M9.3 生产 Docker Compose overlay

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.2、M9.1 |
| **产出物** | `docker-compose.prod.yml` 完善 + 部署文档 |
| **描述** | 1. HTTPS 启用<br>2. 资源限制（CPU / 内存）<br>3. 日志卷挂载<br>4. 健康检查强化<br>5. 备份策略（PG dump / MinIO 同步）<br>6. 部署文档：安装步骤 + 配置说明 + 故障排查 |
| **验收标准** | 生产配置可一键启动；HTTPS 生效；资源限制合理；备份脚本可用 |

#### M9.4 Makefile 与运维脚本

| 项 | 内容 |
|----|------|
| **规模** | S |
| **前置依赖** | M9.3 |
| **产出物** | `Makefile` 完善 + `scripts/` 运维脚本 |
| **描述** | 1. Makefile：`make dev` / `make build` / `make test` / `make seed` / `make backup` / `make logs`<br>2. `scripts/healthcheck.sh`：全链路健康检查<br>3. `scripts/seed_demo_data.py`：演示数据导入<br>4. `scripts/backup.sh`：PG + MinIO 备份 |
| **验收标准** | Makefile 命令可用；脚本执行成功 |

#### M9.5 SIT 系统集成测试

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M9.1 |
| **产出物** | `tests/sit/` 系统集成测试套件 + 测试报告 |
| **描述** | 1. **跨服务集成测试**：Java + Python + 前端三服务联调，覆盖模块间交互<br>2. 测试场景：<br>　- 认证 → 触发任务 → 审批 → 执行 → 审计 → 大屏统计 全链路<br>　- LLM 失败 → NEEDS_HUMAN → 人工处置 → 续跑<br>　- 任务中断 → 断点续跑<br>　- 审批超时 → 自动拒绝<br>　- 跨组织数据隔离<br>3. 数据准备：SIT 专用数据集（独立于演示数据）<br>4. 自动化：CI 可执行，输出测试报告<br>5. 与 E2E 区别：SIT 关注模块间接口契约与数据一致性，E2E 关注用户场景 |
| **验收标准** | SIT 测试全部通过；覆盖 5 个跨模块场景；接口契约一致性验证通过；数据一致性无丢失 |

#### M9.6 前后端字段对齐联调

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M9.5 |
| **产出物** | 前后端字段对齐报告 + 修复 |
| **描述** | 1. **字段对齐审计**：逐个 API 比对 Java 后端响应字段与前端 TypeScript 类型定义<br>2. 检查项：<br>　- 字段名一致性（驼峰 / 下划线）<br>　- 字段类型一致（Java Long ↔ TS number / Java LocalDateTime ↔ TS string）<br>　- 枚举值一致（risk_level / task_state / role_type）<br>　- 可空字段标注<br>　- 分页结构统一<br>3. OpenAPI 契约校验：springdoc 生成的 OpenAPI 与前端 API 类型定义比对<br>4. 修复不一致项<br>5. 前端 API 类型自动生成（可选：openapi-typescript） |
| **验收标准** | 前后端字段 100% 对齐；OpenAPI 契约校验通过；无类型不一致导致的运行时错误 |

---

## 5. 关键路径

```
M0.1 → M0.3 → M1.1 → M1.2 → M2.3 → M2.4 → M2.6 → M3.1 → M3.2 → M4.1 → M4.2 → M5.1 → M6.1 → M6.3 → M7.1 → M9.1
```

**关键路径说明**：
- M0-M2 是 MVP 必经路径，不可并行
- M3 Skill 库是 M4 双 Agent 的前置
- M5 LLM 韧性是 M6 审批 LLM 判断的前置
- M7 审计依赖 M2 任务执行（产生审计数据）
- M9 集成测试依赖所有功能模块完成

**并行机会**：
- M0.2 / M0.3 / M0.4 可并行
- M1.3 前端与 M1.1 Java 可并行（API 契约先定）
- M2.1 Python 与 M2.3 Java 可并行
- M3.3 Java skills 元数据 与 M3.1 Python Skill 实现 可并行
- M6.1 关键词预筛 与 M6.2 LLM 判断 可并行
- M7.1 审计 CRUD 与 M7.2 MinIO 可并行
- M8 大屏 与 M7 审计 可并行（统计独立）

---

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **Skyvern 集成复杂度** | M2.1 阻塞 | 先用 Skyvern 原生 API 跑通简单任务，再逐步封装 |
| **跨语言通信稳定性** | M2.6 联调问题多 | M2.2 HTTP 客户端先做充分的超时/重试/熔断测试 |
| **Playwright 浏览器环境** | Docker 镜像大、依赖多 | M0.2 单独验证 Playwright 在 Docker 内运行 |
| **LLM 调用成本** | M5 阶段成本飙升 | M5.2 Action 缓存优先实现，M5.3 模型路由及时启用 |
| **Fernet 跨语言兼容** | M3.4 加密解密失败 | M3.4 先写跨语言单元测试验证字节级兼容 |
| **三维度 RBAC 复杂度** | M1.1 权限解析错误 | M1.1 先写权限解析算法单元测试，覆盖 5 角色 × 4 场景 |
| **双 Agent 状态一致性** | M4 断点续跑数据丢失 | M4.2 每步强制回调 Java 持久化，Python 不本地存状态 |
| **审计截图存储压力** | M7 MinIO 空间不足 | M7.2 配置 90 天保留期 + 定时清理任务 |
| **演示数据真实性** | M1.4 演示效果差 | M1.4 参考 6 个金融场景设计演示数据 |
| **端到端测试环境** | M9.1 测试不稳定 | M9.1 使用 mock 银行页面，避免依赖真实系统 |

---

## 7. 任务统计

| 里程碑 | 任务数 | 规模分布 | 说明 |
|--------|--------|----------|------|
| M0 基础设施 | 4 | S×1 + M×3 | 骨架与环境 |
| M1 认证与多租户 | 6 | M×4 + L×2 | 垂直切片第一步（含 UI 系统 + i18n） |
| M2 任务执行闭环 | 6 | M×2 + L×4 | MVP 核心闭环 |
| M3 Skill 库与工作流 | 6 | M×4 + L×1 + XL×1 | 业务能力构建 |
| M4 双 Agent 协作 | 4 | M×2 + L×2 | 高级 AI 能力 |
| M5 LLM 韧性 | 6 | M×4 + L×2 | 成本与稳定性 |
| M6 审批引擎 | 6 | M×5 + L×1 | 合规能力 |
| M7 全链路审计 | 5 | M×3 + L×2 | 合规审计 |
| M8 运营大屏 | 2 | L×2 | 可视化 |
| M9 集成测试与部署 | 6 | S×1 + M×3 + L×1 + XL×1 | 验收与交付（含 SIT + 字段对齐） |
| **合计** | **51** | S×2 + M×30 + L×17 + XL×3 | - |

---

## 8. 建议开发顺序

### 阶段一：MVP 闭环（M0 → M1 → M2）

**目标**：跑通端到端最小闭环，验证架构可行性。

顺序：M0.1 → (M0.2 / M0.3 / M0.4 并行) → M1.5（UI 系统，前端基建） → M1.1 → (M1.2 / M1.3 并行) → M1.6（i18n）→ M1.4 → (M2.1 / M2.3 并行) → M2.2 → M2.4 → M2.5 → M2.6

**验收**：能触发一个简单导航任务，前端实时看到执行过程，UI 毛玻璃风格统一，中英文可切换。

### 阶段二：业务能力（M3 → M4）

**目标**：构建 Skill 库与双 Agent，支持复杂金融场景。

顺序：M3.1 → M3.2 → (M3.3 / M3.4 并行) → M3.5 → M3.6 → M4.1 → M4.2 → (M4.3 / M4.4 并行)

**验收**：能跑通一个完整金融场景（如银行流水下载），多步骤任务自动拆解。

### 阶段三：韧性与合规（M5 → M6 → M7）

**目标**：LLM 容错、审批流、全链路审计。

顺序：M5.1 → (M5.2 / M5.3 并行) → (M5.4 / M5.5 并行) → M5.6 → (M6.1 / M6.2 并行) → M6.3 → (M6.4 / M6.5 / M6.6 并行) → M7.1 → (M7.2 / M7.3 并行) → M7.4 → M7.5

**验收**：高风险任务走审批，LLM 失败转人工，每步操作有审计。

### 阶段四：可视化与交付（M8 → M9）

**目标**：运营大屏 + 端到端验收 + SIT + 字段对齐 + 生产部署。

顺序：M8.1 → M8.2 → M9.1（E2E） → M9.5（SIT） → M9.6（字段对齐） → M9.2 → M9.3 → M9.4

**验收**：6 个金融场景 E2E + SIT 通过，前后端字段 100% 对齐，生产可部署。

---

## 9. 附录

### 9.1 任务编号规则

```
M{里程碑号}.{任务序号}
例：M2.3 = 里程碑 2 的第 3 个任务
```

### 9.2 任务状态定义

| 状态 | 说明 |
|------|------|
| **待开始** | 前置依赖未完成 |
| **可开始** | 前置依赖已完成 |
| **进行中** | 正在开发 |
| **待评审** | 开发完成，待代码评审 |
| **已完成** | 评审通过 + 验收达标 |
| **阻塞** | 遇到阻碍，需协调 |

### 9.3 与原项目 16 天开发进度对照

> 原项目按 16 天线性推进，本项目按里程碑 + 并行方式拆分。下表对照原项目每日内容与本任务拆分的覆盖关系。

| 原 Day | 原分支 | 原核心内容 | 本项目任务 | 覆盖状态 |
|--------|--------|------------|------------|----------|
| Day 1 | day-1/project-setup | 项目脚手架、Docker 环境 | M0.1 + M0.2 + M0.4 | ✅ |
| Day 2 | day-2/permission-data-model | 三维度权限数据模型 + SQL 模拟数据 | M0.3 + M1.4 | ✅ |
| Day 3 | day-3/auth-and-permission | JWT 认证 + 多维度权限验证 | M1.1 | ✅ |
| Day 4 | day-4/tenant-isolation-middleware | 多维度租户隔离中间件 | M1.2 | ✅ |
| Day 5 | day-5/financial-risk-detector | 金融场景高危操作识别引擎 | M6.1 | ✅ |
| Day 6 | day-6/approval-engine | 分级审批引擎 + Redis Pub/Sub | M6.3 | ✅ |
| Day 7 | day-7/notification | 企业微信/钉钉通知集成 | M6.6 | ✅ |
| Day 8 | day-8/audit-compliance | 全链路审计 + MinIO 合规存储 | M7.1 + M7.2 + M7.3 | ✅ |
| Day 9 | day-9/llm-resilience | LLM 三层容错 + NEEDS_HUMAN 状态机 | M5.1 + M5.5 | ✅ |
| Day 10 | day-10/financial-workflow-templates | 六个金融场景工作流模板 + Skill 库 | M3.1 + M3.4 + M3.5 | ✅ |
| Day 11 | day-11/dashboard-api | 运营统计后端 API + Redis 缓存 | M8.1 | ✅ |
| Day 12 | day-12/ui-redesign | 毛玻璃 UI 改造 + SVG 图标系统 | **M1.5**（v1.1 新增） | ✅ |
| Day 13 | day-13/performance-optimization | Action 缓存 + 模型路由优化 | M5.2 + M5.3 | ✅ |
| Day 14 | day-14/production-ready | 容器化完善 + 端到端验收 | M9.1 + M9.3 | ✅ |
| Day 15 | day-15/enterprise-frontend-integration | 企业认证 + 全站 i18n + LLM 监控 + SIT 测试 | M1.3 + M1.6（i18n，v1.1 新增）+ M5.6 + **M9.5**（SIT，v1.1 新增） | ✅ |
| Day 16 | day-16/demo-data-integration | 演示数据生成器 + 前后端字段对齐 + 运营大屏升级 | M1.4（升级为生成器） + **M9.6**（字段对齐，v1.1 新增）+ M8.2 | ✅ |

**本项目额外增加的任务**（原项目未明确）：
- M2.2 Java↔Python HTTP 客户端 + SSE 透传（跨语言通信）
- M2.3 Java agent 模块（任务状态持久化）
- M4.1-M4.4 双 Agent 协作（Planner + Executor + 断点续跑）
- M5.4 Java LLM 调用记录与统计
- M7.4 CSV 导出 + 多维检索增强
- M9.2 性能测试

### 9.4 修订记录

| 版本 | 日期 | 修订人 | 说明 |
|------|------|--------|------|
| v1.0 | 2026-07-25 | - | 初稿，覆盖 10 个里程碑、47 个任务、依赖关系图、关键路径、风险缓解与开发顺序建议 |
| v1.1 | 2026-07-25 | - | 补充原项目 16 天进度对照缺失任务：新增 M1.5（毛玻璃 UI + SVG 图标）、M1.6（全站 i18n）、M9.5（SIT 系统集成测试）、M9.6（前后端字段对齐）；升级 M1.4 为演示数据生成器；新增 9.3 节原项目对照表；任务总数 47 → 51 |

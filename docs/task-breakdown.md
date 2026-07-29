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

#### M0.1 三大子项目骨架搭建 ✅

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | 无 |
| **产出物** | `finance-backend/`（Maven + Spring Boot 3.2 + Java 21）、`finance-ai/`（uv + FastAPI + Python 3.11）、`finance-frontend/`（Vite + React 18 + TS）、顶层 `Makefile` + `.env.example` |
| **描述** | 1. Java：`pom.xml` 配置 Spring Boot 3.2 / MyBatis-Plus / Flyway / Redisson / jjwt / springdoc；启动类 `FinRpaApplication`；`application.yml` 基础配置<br>2. Python：`pyproject.toml` 配置 FastAPI / Uvicorn / SQLAlchemy / Playwright / LiteLLM；`app/main.py` FastAPI 入口<br>3. 前端：`package.json` 配置 React / Vite / Tailwind / Zustand / React Query；`App.tsx` 根组件<br>4. 顶层 Makefile：`make dev` / `make build` / `make test` 入口 |
| **验收标准** | 三个子项目能独立启动（Java 8080、Python 8000、前端 8081）；健康检查端点返回 200 |
| **状态** | ✅ 已完成（2026-07-27）。三服务均验证启动+健康检查 200：Java `/actuator/health` 返回 UP；Python `/api/v1/ai/health` 返回 `{"status":"up"}`；前端 `localhost:8081` 返回 200 含 #root。骨架阶段 Java 排除中间件自动配置（PG/Redis/Flyway/Security）使可独立启动；通用基础类（BaseResponse/ErrorCode/异常处理/AOP/工具类）从用户模板 `springboot3-java21-backend` 迁移并改包名为 `com.finrpa`；litellm 降级到 1.89.6（1.90+ 含 Rust 扩展，本机 dlltool 被策略阻止无法编译） |

#### M0.2 Docker Compose 环境搭建 ✅

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.1 |
| **产出物** | `docker-compose.yml`（开发）、`docker-compose.prod.yml`（生产 overlay）、各服务 Dockerfile |
| **描述** | 1. 7 服务编排：postgres / redis / minio / finance-backend / finance-ai / finance-frontend + nginx<br>2. 数据卷：`postgres-data` / `redis-data` / `minio-data` / `maven-repo` / `uv-cache` / `frontend-node-modules` / `ai-venv`<br>3. 健康检查：全链路 healthcheck，start_period 适配 uv sync 下载时间<br>4. 启动顺序：depends_on + condition: service_healthy<br>5. 国内镜像加速：Maven 阿里云镜像、PyPI 阿里云镜像（UV_INDEX_URL）<br>6. 端口：5432/6379/9000/9001/8080/8000/8081/80 |
| **验收标准** | ✅ `docker-compose up -d` 一键启动全部服务；✅ 所有健康检查通过；✅ 服务间网络互通；✅ Java 连接 PG+Redis 验证通过 |

#### M0.3 数据库迁移脚本初始化

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.1 |
| **产出物** | Java `db/migration/` 下 10 个 Flyway 脚本（占位空表）；Python `alembic/` 配置 + skyvern_* 表迁移 |
| **描述** | 1. Java：按 system-design 8.1 节表前缀创建 10 个 Flyway 脚本骨架（`V20260725_001__auth__init_schema.sql` 等），先建空表结构<br>2. Python：Alembic 初始化，复刻 Skyvern 原项目 skyvern_* 表迁移<br>3. 共享枚举字典表（risk_level / role_type / task_state）由 Java 侧 Flyway 创建<br>4. 双方迁移脚本边界严格隔离（前缀校验） |
| **验收标准** | Java 服务启动 Flyway 执行成功；Python 服务启动 Alembic 执行成功；`psql` 能查到全部表 |

#### M0.4 Nginx 反向代理配置 ✅

| 项 | 内容 |
|----|------|
| **规模** | S |
| **前置依赖** | M0.1 |
| **产出物** | `nginx/nginx.conf`、`nginx/conf.d/default.conf` |
| **描述** | 1. 路由规则按 system-design 10.3 节<br>2. SSE 透传：`proxy_buffering off` / `proxy_read_timeout 86400s`<br>3. gzip 压缩 + 安全头（X-Frame-Options / X-Content-Type-Options 等）<br>4. HTTPS 预留配置（生产 overlay 启用）<br>5. WebSocket 支持（Vite HMR） |
| **验收标准** | ✅ Nginx 启动后，前端访问 `/` 返回静态资源；✅ `/api/v1/*` 转发到 Java；✅ `/api/v1/ai/sse/*` 透传 SSE；✅ `/actuator/health` 通过 nginx 访问返回 UP |

---

### M1 认证与多租户基座

#### M1.1 Java auth 模块实现 ✅

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M0.1、M0.3 |
| **产出物** | `auth/` 模块完整代码 + 单元测试 + Javadoc 注释 |
| **描述** | 1. 实体：`UserEO` / `RoleEO` / `UserRoleEO`（sys_user / sys_role / sys_user_role 三张表）<br>2. DTO：`LoginRequest` / `RefreshRequest` / `PermissionCheckRequest` / `LoginResponse` / `UserInfoResponse`<br>3. Mapper：`UserMapper`（selectByUsername/selectByUserId，含 PostgreSQL UUID 类型转换）/ `RoleMapper`（selectByUserId/selectByRoleCode）/ `UserRoleMapper`<br>4. JWT 服务：jjwt 0.12.6 签发 access（60min）+ refresh（7d），支持组织 ID/部门名称注入<br>5. 权限解析：`PermissionService` 实现三维度权限算法（super_admin/org_admin/viewer/operator/approver），含角色互斥约束（operator + approver 不可共存）、跨组织读/审批权限判断<br>6. 认证服务：`AuthService` 实现登录校验、token 刷新、用户信息获取、权限检查委托<br>7. Spring Security 6 配置：`SecurityConfig` 无状态会话 + `JwtAuthenticationFilter` + 方法级 `@RequirePermission`<br>8. AOP 切面：`PermissionAspect` 拦截 `@RequirePermission` 注解方法进行权限校验<br>9. API：`POST /auth/login` / `POST /auth/refresh` / `GET /auth/me` / `POST /auth/permissions/check`<br>10. 服务层规范：`AuthService`/`PermissionService` 接口定义在 `service/`，实现类 `AuthServiceImpl`/`PermissionServiceImpl` 放在 `service/impl/`<br>11. 代码规范：全部 40+ Java 文件补全类级 Javadoc（含 `@author`/`@from`）、字段注释、方法注释（含 `@param`/`@return`）、步骤编号注释 |
| **验收标准** | ✅ 4 个 API 全部测试通过（登录/刷新/用户信息/权限检查）；✅ 59 个单元测试覆盖边界情况（角色互斥、跨组织权限、token 校验）；✅ 代码编译通过；✅ 服务接口与实现分离；✅ 全部 Java 文件补全 Javadoc 注释 |
| **状态** | ✅ 已完成（2026-07-28）。提交记录：`9880a6a` refactor: service层接口与实现分离 + 补全Java文件Javadoc注释 |

#### M1.2 Java tenant 模块实现 ✅

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M1.1 |
| **产出物** | `tenant/` 模块完整代码 + 单元测试 + Javadoc 注释 |
| **描述** | 1. `TenantContext`：基于 `ThreadLocal<String>`（实现说明：未采用 Java 21 ScopedValue 预览特性，避免 `--enable-preview` 改动面）<br>2. `TenantInterceptor`：Spring WebMVC `HandlerInterceptor`，从 `JwtAuthenticationFilter` 预存的 request attribute 读取 orgId 注入 `TenantContext`，请求结束时清理 ThreadLocal<br>3. MyBatis-Plus `TenantLineInnerInterceptor` + `TenantLineHandlerImpl`：自动追加 `WHERE org_id = ?`（字段名为 `org_id`，与 M1.1 `sys_user`/`sys_role` 已有字段保持一致）<br>4. `TenantConstant` 忽略表配置：`enterprise_organization` 本身、`sys_user_role`/`sys_role_permission`/`sys_permission`/`sys_dictionary`/`sys_config`/`sys_audit_log`、RPA 执行/日志/浏览器会话/审批表、`skyvern_*` 前缀<br>5. 实体：`OrganizationEO` / `DepartmentEO` / `BusinessLineEO`（对应 `enterprise_organization` / `enterprise_department` / `enterprise_business_line` 三张表，V6 Flyway 脚本）<br>6. Mapper：`OrganizationMapper`（含 `selectByOrgId` 手动按 UUID 查询，因表本身在忽略清单中）/ `DepartmentMapper` / `BusinessLineMapper`<br>7. DTO：`TenantInfoResponse` / `DepartmentVO` / `BusinessLineVO`<br>8. Service：`TenantService` 接口 + `TenantServiceImpl` 实现<br>9. API：`GET /tenant/info` / `GET /tenant/departments` / `GET /tenant/business-lines`（均从 `TenantContext` 读取 orgId 后查询对应表）<br>10. 修改 `JwtAuthenticationFilter`：解析 token 中的 orgId 后存到 request attribute（key 见 `TenantConstant.ORG_ID_REQUEST_ATTR`），由 `TenantInterceptor` 读取，避免重复解析 token<br>11. 修改 `MyBatisPlusConfig`：在拦截器链中按 `TenantLineInnerInterceptor` → `PaginationInnerInterceptor` 顺序添加（租户插件须在分页之前）<br>12. `TenantWebMvcConfig`：`WebMvcConfigurer` 实现，注册 `TenantInterceptor` 拦截 `/**` |
| **验收标准** | ✅ 91 个单元测试全部通过（M1.1 原 59 + M1.2 新增 32）；✅ TenantContext set/get/clear 行为正确；✅ TenantLineHandlerImpl 忽略表清单与 skyvern_* 前缀匹配正确；✅ TenantInterceptor preHandle/afterCompletion 正确注入和清理；✅ TenantServiceImpl 3 个业务方法覆盖组织存在/不存在/无 orgId 三种场景；✅ TenantController 3 个 API MockMvc 验证返回结构与字段一致 |
| **状态** | ✅ 已完成（2026-07-28）。新增代码全部含类级 Javadoc（@author/@from）+ 字段注释 + 方法注释（@param/@return）+ 步骤注释。文档偏差已在 system-design.md 6.2 节"实现说明（M1.2 落地偏差）"中记录 |

#### M1.3 前端登录页与路由守卫 ✅

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M1.1（API 契约确定） |
| **产出物** | 前端 `src/api/AxiosClient.ts`、`src/api/auth.ts`、`src/api/types.ts`、`src/store/AuthStore.ts`、`src/components/AuthGuard.tsx`、`src/routes/auth/LoginPage.tsx`、`src/routes/Forbidden.tsx`、`src/routes/RootLayout.tsx`、`src/router.tsx`、`src/styles/variables.css`、`src/styles/glass.css`，并更新 `vite.config.ts`（proxy 转发 /api）、`tailwind.config.js`（扩展配色）、`App.tsx`、`index.css`；后端修改 `application.yml`（添加 `server.servlet.context-path: /api`）、`auth/config/SecurityConfig.java`（放行路径同步加 /api 前缀）；修改 `docker-compose.yml` / `docker-compose.prod.yml`（healthcheck 路径改为 `/api/actuator/health`） |
| **描述** | 1. 登录页：用户名密码 + 毛玻璃风格（对齐 prototypes/01-auth-and-layout.html），含显示/隐藏密码切换、客户端校验、服务端错误提示、已登录自动跳转、URL expired=1 过期提示<br>2. Axios 拦截器：请求拦截器自动附加 `Authorization: Bearer <accessToken>`；响应拦截器处理业务码非 0 抛 ApiError；HTTP 401 或业务码 40100 自动触发 refresh；refresh 并发合并（pendingQueue 排队）；refresh 失败登出并跳 `/login?expired=1`<br>3. AuthGuard：`RequireAuth` 未登录跳 `/login?redirect=...`；`RequirePermission` 权限不足跳 `/403`；`RequireRole` 角色不足跳 `/403`<br>4. Zustand AuthStore：`accessToken / refreshToken / expiresAt / user / permissions / loading` 状态 + `login / logout / setTokens / fetchCurrentUser / isAuthenticated / hasPermission / hasRole` actions；通过 `persist` 中间件持久化到 `localStorage` key `finrpa-auth`<br>5. 路由配置：`/login` 公开；`/` 受 `RequireAuth` 保护，子路由含 `index`（占位首页展示用户角色与权限）、`/403`、`*` 兜底 |
| **验收标准** | ✅ 登录页按原型图渲染（毛玻璃卡片 + 深海蓝 logo + 表单图标 + 显示密码切换 + 客户端校验 + 错误提示），browser 自动化验证 11 项检查全部 PASS；✅ Axios 拦截器机制完整（请求附加 JWT、401 自动 refresh、并发合并、refresh 失败登出）；✅ AuthGuard 三组件覆盖登录/权限/角色三种守卫；✅ AuthStore 通过 persist 同步 localStorage，刷新页面状态不丢失；✅ 路由配置 `/login` 公开 + `/` 受保护 + `/403` + `*` 兜底；⏳ 5 种角色登录差异、token 过期自动 refresh、权限拦截需前后端联调验证（步骤见下方） |
| **状态** | ✅ 已完成（2026-07-28）。M1.3 范围最小化：仅落地登录页所需样式（`variables.css` 设计 token + `glass.css` 毛玻璃组件最小集），M1.5（21 个 SVG 图标 + 完整基础组件库）留待后续；登录页 i18n 暂不实现（M1.6 全站 i18n 统一处理），原型中的 2FA 输入框因后端 LoginRequest 不支持暂未实现；前后端联调测试步骤见下方"前后端联调测试步骤"小节 |

##### 前后端联调测试步骤

1. **启动后端**：在 `finance-backend` 目录运行 `mvnw spring-boot:run`（或 Docker Compose 启动全栈），确认 `http://localhost:8080/auth/login` 可访问
2. **启动前端**：在 `finance-frontend` 目录运行 `npm run dev`，访问 `http://localhost:8081`（如端口被占用会自动切换，看终端日志）
3. **测试登录流程**：
   - 浏览器访问 `http://localhost:8081/`，应自动重定向到 `/login`
   - 输入 M1.1 测试时使用的账号（如 `zhangsan` / 对应密码），点击登录
   - 登录成功后应跳转到首页，看到欢迎信息、用户名、组织、部门、角色列表、权限列表
4. **测试 5 种角色差异**：用 5 种不同角色账号（M1.1 数据初始化的演示账号）依次登录，确认首页展示的角色和权限列表不同
5. **测试 token 过期自动 refresh**：
   - 登录后打开浏览器 DevTools → Application → Local Storage，找到 `finrpa-auth`，手动修改 `expiresAt` 为过去时间戳
   - 刷新页面，触发任意需要鉴权的请求（如点击"退出"再登录，或直接访问 `/`）
   - 观察 Network 面板：原请求返回 401 → 自动发起 `/auth/refresh` → 原请求被重发成功
6. **测试权限路由拦截**：
   - 在 `router.tsx` 中临时给某个路由加 `<RequirePermission permission="xxx:yyy">` 包裹（用当前账号没有的权限）
   - 访问该路由，应自动跳转到 `/403` 页面
   - `/403` 页面会展示当前账号的角色和拥有的权限列表
7. **测试已登录访问 /login 自动跳转**：登录后手动访问 `http://localhost:8081/login`，应自动跳回 `/`
8. **测试 expired 提示**：手动访问 `http://localhost:8081/login?expired=1`，应看到"登录已过期，请重新登录"红色提示

#### M1.4 演示数据生成器

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M1.1、M1.2 |
| **产出物** | `scripts/seed_demo_data.py` 演示数据生成器（可重复执行、幂等、支持参数化） |
| **描述** | 1. 1 个组织 + 5 个部门（对公信贷/个人金融/资产管理/风险管理/合规审计）+ 4 条业务线<br>2. 5 种角色用户：super_admin / org_admin / operator / approver / viewer<br>3. 角色与部门、业务线关联（含跨部门只读场景）<br>4. 互斥约束验证数据（operator 与 approver 不同部门）<br>5. 默认密码 BCrypt 哈希（与原项目兼容）<br>6. **生成器特性**：支持 `--reset` 清空重建、`--only=users` 按模块生成、`--count=N` 批量生成任务数据、生成结果报告<br>7. 对应 6 个金融场景的演示任务数据（任务、审批、审计日志样本） |
| **验收标准** | 生成器可重复执行不报错；5 种角色均能登录；权限解析返回预期结果；互斥约束数据正确；`--reset` 后数据干净重建 |

> **开发环境账号密码表**（以数据库实际哈希为准，2026-07-29 经 BCryptPasswordEncoder 验证）
>
> 默认管理员（V5 迁移脚本初始化）：
>
> | 账号 | 密码 | 部门 | 角色 | 说明 |
> |------|------|------|------|------|
> | admin | admin123 | 管理部 | org_admin | 默认管理员 |
>
> 演示数据账号（DemoDataInitializer 初始化，组织：银河证券 DEMO_YHSEC / 星辰银行 DEMO_XCBA）：
>
> | 账号 | 密码 | 部门 | 角色 | 说明 |
> |------|------|------|------|------|
> | admin_demo_yhsec | 123456 | 财务部 | org_admin | 银河证券组织管理员 |
> | operator_demo_yhsec | 123456 | 财务结算科 | operator | 操作员 |
> | approver_demo_yhsec | 123456 | 审批部 | approver | 审批员 |
> | viewer_demo_yhsec | 123456 | 业务部 | viewer | 查看员（跨组织只读） |
> | admin_demo_xcba | 123456 | 财务部 | org_admin | 星辰银行组织管理员 |
> | operator_demo_xcba | 123456 | 财务结算科 | operator | 操作员 |
>
> 说明：默认管理员密码由 V5 迁移脚本插入（哈希 `$2b$10$ax2XrMPam32Kv4oL/SPO5eKlCCdCJzrQTBeTtSbNRNRF9tV3WYZlq`，明文 admin123）。演示账号密码由 DemoDataInitializer 统一生成（BCrypt 哈希，明文 123456）。主键已从 UUID 迁移为雪花算法 BIGINT。

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

#### M2.1 Python finance-ai 服务骨架 + Skyvern 集成 ✅

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M0.1、M0.3 |
| **产出物** | `finance-ai/app/` 完整骨架 + Skyvern 集成 + Agent 三层架构（骨架版）+ Skills 基础层 + demo_seed + 单元测试 |
| **验收标准** | `POST /api/v1/ai/tasks` 能触发 Agent 执行简单导航任务；Planner（fallback 版）拆解任务为子任务列表；Executor 逐个执行并产出 SSE 事件流；Coordinator 处理失败与 replan；`/health` 返回 200；启动时自动加载 demo 数据 |
| **状态** | ✅ 已完成（2026-07-29）。新增文件：`app/agent/__init__.py`、`app/agent/schemas.py`、`app/agent/planner.py`、`app/agent/executor.py`、`app/agent/coordinator.py`、`app/skills/__init__.py`、`app/skills/base.py`、`app/skills/executor.py`、`app/clients/__init__.py`、`app/clients/java_backend.py`、`app/schemas.py`、`app/demo_seed.py`、`app/api/tasks.py`、`app/api/sse.py`；更新文件：`app/config.py`、`app/main.py`、`pyproject.toml`、`Dockerfile`；单元测试：`tests/unit/test_planner.py`、`test_executor.py`、`test_coordinator.py`、`test_skills.py`，20 个单元测试全部通过。Skyvern 源码暂未引入（M2.1 fallback 模式不需要，M3 实际浏览器操作时引入） |

##### 技术实现方案

**核心设计原则**：
1. Skyvern 已内置 Playwright，**不再单独引入/管理 Playwright**，直接基于 Skyvern `ForgeAgent` 构建底层执行
2. Agent 采用**三层架构**（骨架版）：Planner（fallback 单步计划，LLM 版在 M4.1）→ Executor（子任务执行 + 重试）→ Coordinator（编排调度 + 失败处理），与参考项目 `enterprise/agent/` 接口对齐
3. Skills 系统采用**注册表模式**：`BaseSkill` 抽象基类 + `SKILL_REGISTRY` 全局注册 + `execute_pipeline()` 流水线执行，与参考项目 `enterprise/skills/` 完全对齐
4. Demo 数据采用**内存 store**：`demo_seed.py` 在启动时填充任务/审批/审计/模型调用缓存，供 Dashboard 和 Approval 路由使用
5. **LLM 模块推迟**：ActionCache 和 ModelRouter 不在 M2.1 实现，推迟到 M5.2/M5.3；PlannerAgent 先用 fallback 模式，M4.1 接入 LLM 版

**目录结构**：

```
finance-ai/
├── skyvern/                    # Skyvern 源码（volume 挂载，最小改动）
│   ├── forge/                  # ForgeAgent 核心
│   └── ...
├── app/
│   ├── main.py                 # FastAPI 入口（注册路由 + 初始化 + demo seed）
│   ├── config.py               # 配置（扩展新增项）
│   ├── schemas.py              # 全局数据模型（TaskTriggerRequest / SseEvent 等）
│   ├── demo_seed.py            # 内存演示数据生成器（新增）
│   ├── api/
│   │   ├── health.py           # 已存在
│   │   ├── tasks.py            # 任务触发/状态查询/终止 API（新增）
│   │   └── sse.py              # SSE 流推送端点（新增）
│   ├── agent/                  # Agent 三层架构（骨架版，接口对齐 enterprise/agent/）
│   │   ├── schemas.py          # SubTask / TaskPlan / ExecutionResult / CoordinationState
│   │   ├── planner.py          # PlannerAgent（fallback 版：单步计划，LLM 版 M4.1 接入）
│   │   ├── executor.py         # ExecutorAgent（子任务执行 + 重试）
│   │   └── coordinator.py      # AgentCoordinator（编排调度 + 失败处理）
│   ├── skills/                 # Skills 基础层（新增，对齐 enterprise/skills/）
│   │   ├── base.py             # BaseSkill + SKILL_REGISTRY + ErrorStrategy + SkillStatus
│   │   └── executor.py         # execute_pipeline() 流水线执行 + PipelineResult
│   └── clients/
│       └── java_backend.py     # Java 后端回调客户端（新增）
└── tests/
    └── unit/
        ├── test_planner.py     # PlannerAgent 单元测试（fallback 版）
        ├── test_executor.py    # ExecutorAgent 单元测试
        ├── test_coordinator.py # AgentCoordinator 单元测试
        └── test_skills.py      # Skills 系统单元测试
```

**新增文件清单**：

| 文件 | 行数估算 | 职责 | 对齐参考 | 说明 |
|------|----------|------|----------|------|
| `app/schemas.py` | ~80 | 全局 Pydantic 模型（TaskTriggerRequest / TaskStateResponse / SseEvent） | — | |
| `app/demo_seed.py` | ~120 | 内存 store + 演示数据生成（任务/审批/审计/模型调用） | `enterprise/demo_seed.py` | |
| `app/api/tasks.py` | ~100 | 任务触发、状态查询、终止、断点续跑 4 个端点 | — | |
| `app/api/sse.py` | ~60 | SSE 订阅端点 | — | |
| `app/agent/schemas.py` | ~100 | SubTask / TaskPlan / ExecutionResult / CoordinationState / FailureStrategy | `enterprise/agent/schemas.py` | |
| `app/agent/planner.py` | ~80 | PlannerAgent **fallback 版**：单步计划 + replan 空实现（LLM 版 M4.1 接入） | `enterprise/agent/planner.py` | 接口对齐，内部用 fallback |
| `app/agent/executor.py` | ~130 | ExecutorAgent：子任务执行 + 重试 + 错误处理 | `enterprise/agent/executor.py` | |
| `app/agent/coordinator.py` | ~250 | AgentCoordinator：编排调度 + 失败策略（SKIP/ABORT/REPLAN）+ 断点续跑 | `enterprise/agent/coordinator.py` | |
| `app/skills/base.py` | ~120 | BaseSkill 抽象基类 + SKILL_REGISTRY + ErrorStrategy + SkillStatus + register_skill | `enterprise/skills/base.py` | |
| `app/skills/executor.py` | ~170 | execute_pipeline() 流水线执行 + PipelineResult + audit_callback | `enterprise/skills/executor.py` | |
| `app/clients/java_backend.py` | ~100 | httpx 客户端，回调 Java 内部 API | — | |
| `tests/unit/test_planner.py` | ~40 | PlannerAgent 测试（fallback 单步计划） | — | |
| `tests/unit/test_executor.py` | ~80 | ExecutorAgent 测试（执行 + 重试 + 失败） | — | |
| `tests/unit/test_coordinator.py` | ~100 | AgentCoordinator 测试（编排 + replan + abort） | — | |
| `tests/unit/test_skills.py` | ~80 | Skills 系统测试（pipeline + 错误策略 + registry） | — | |

**推迟到后续里程碑**：

| 文件 | 推迟到 | 原因 |
|------|--------|------|
| `app/llm/action_cache.py` | M5.2 | 需要真实 LLM 调用数据才有意义，MVP 阶段无实际缓存命中 |
| `app/llm/model_router.py` | M5.3 | 需要 DOM 复杂度分析，M2 阶段无多模型选择需求 |
| `app/llm/resilient_caller.py` | M5.1 | 三层容错依赖 LLM 真实调用 |
| `PlannerAgent` LLM 版（`_plan_with_llm`） | M4.1 | fallback 版先跑通接口，M4 替换内部实现 |

**修改文件清单**：

| 文件 | 改动 |
|------|------|
| `app/config.py` | 新增执行器/LLM/Skill/内部鉴权配置项 |
| `app/main.py` | 注册新路由（tasks.router / sse.router），lifespan 初始化 Agent + demo_seed |
| `pyproject.toml` | 新增 `skyvern`（通过 git/volume 引入）、`sse-starlette` 依赖 |
| `Dockerfile` | 基础镜像切换为 `mcr.microsoft.com/playwright:v1.49.0-jammy-full` |

---

#### Agent 三层架构详解

**架构关系图**：

```
用户请求 → POST /api/v1/ai/tasks
         │
         ▼
┌──────────────────────────────────────────────────┐
│  AgentCoordinator (agent/coordinator.py)          │
│  1. 调 PlannerAgent.create_plan() 生成任务计划    │
│  2. 遍历 SubTask 列表，逐个调 ExecutorAgent       │
│  3. 监听 ExecutionResult，处理失败策略           │
│  4. 产出 CoordinationState 最终状态              │
└──────────┬───────────────────────────────────────┘
           │
    ┌──────┴──────┐
    ▼              ▼
┌────────┐  ┌──────────────┐
│Planner │  │  Executor    │
│ Agent  │  │  Agent       │
└────────┘  └──────┬───────┘
    │              │
    │    ┌─────────┴──────────┐
    │    ▼                     ▼
    │  SkillPipeline    Skyvern ForgeAgent
    │  (skills/exec)    (底层浏览器执行)
    │    │                     │
    │    └────────┬────────────┘
    ▼             ▼
  LLM         Playwright
  (task 执行) (浏览器操作)
  拆解)
```

**`agent/schemas.py`** — 数据模型（对齐参考项目）：

```python
class FailureStrategy(str, enum.Enum):
    """子任务失败策略。"""
    RETRY = "retry"       # 重试（由 Executor 内部处理）
    SKIP = "skip"         # 跳过，继续下一个
    ABORT = "abort"       # 终止整个任务
    REPLAN = "replan"     # 让 Planner 重新规划剩余步骤

class SubTaskStatus(str, enum.Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"
    REPLANNED = "replanned"

class SubTask(BaseModel):
    """Planner 产出的单个子任务。"""
    subtask_id: str
    index: int
    goal: str                              # 该子任务要做什么
    completion_condition: str              # 如何验证成功
    max_retries: int = 2
    failure_strategy: FailureStrategy = FailureStrategy.REPLAN
    status: SubTaskStatus = SubTaskStatus.PENDING
    error_message: str | None = None
    result_data: dict | None = None
    started_at: datetime | None = None
    completed_at: datetime | None = None

class TaskPlan(BaseModel):
    """Planner 产出的完整计划。"""
    plan_id: str
    navigation_goal: str
    subtasks: list[SubTask]
    is_replan: bool = False
    replan_reason: str | None = None
    version: int = 1

class ExecutionResult(BaseModel):
    """Executor 执行子任务的结果。"""
    subtask_id: str
    success: bool
    result_data: dict | None = None
    error_message: str | None = None
    screenshot_key: str | None = None
    page_url: str | None = None
    duration_ms: int | None = None

class CoordinationState(BaseModel):
    """Coordinator 维护的全局状态。"""
    task_id: str
    org_id: str
    navigation_goal: str
    current_plan: TaskPlan | None = None
    completed_subtasks: list[str] = Field(default_factory=list)
    total_replans: int = 0
    max_replans: int = 3
    status: str = "running"       # running / completed / failed / needs_human
    error_message: str | None = None
```

**`agent/planner.py`** — PlannerAgent（**fallback 版**，LLM 版 M4.1 接入）：

```
PlannerAgent.create_plan(navigation_goal, context) → TaskPlan
  │
  ├── llm_callable 已注入？
  │   └── No（M2.1 默认）→ _create_fallback_plan()
  │       └── 单步计划（goal 直接作为唯一 SubTask）
  │
  └── LLM 失败 → 自动 fallback 到单步计划

PlannerAgent.replan(original_goal, completed, failed, reason, context) → TaskPlan
  └── M2.1 返回 replanned=True 空计划（占位实现）
      └── M4.1: 接入 LLM → REPLAN_SYSTEM_PROMPT → 重新规划
```

**`agent/executor.py`** — ExecutorAgent（对齐参考项目）：

```
ExecutorAgent.execute_subtask(subtask, context) → ExecutionResult
  │
  ├── 标记状态为 RUNNING
  │
  ├── for attempt in range(max_retries + 1):
  │   ├── 调 action_handler(goal, context)
  │   │   ├── 有 action_handler → 异步调用（封装 Skyvern/技能管线）
  │   │   └── 无 → _simulate_execution()（测试用模拟）
  │   │
  │   ├── 成功 → 标记 COMPLETED，返回结果
  │   └── 失败 → 记录错误，继续重试
  │
  └── 全部重试耗尽 → 标记 FAILED，返回失败结果
```

**`agent/coordinator.py`** — AgentCoordinator（对齐参考项目）：

```
AgentCoordinator.run(task_id, org_id, navigation_goal, context, resume_from)
  │
  ├── 1. Planner.create_plan() → TaskPlan
  │
  ├── 2. _execute_plan(state, plan, completed, context)
  │      └── for subtask in plan.subtasks:
  │          ├── 跳过已完成（断点续跑）
  │          ├── Executor.execute_subtask()
  │          ├── audit_callback（可选，记录审计日志）
  │          ├── 成功 → 加入 completed_subtasks
  │          └── 失败 → _handle_failure()
  │              ├── SKIP → 标记 SKIPPED，继续
  │              ├── ABORT → 标记 failed，终止
  │              ├── REPLAN（次数内） → Planner.replan() → 递归执行新计划
  │              └── REPLAN（超限）→ 标记 needs_human，终止
  │
  └── 3. 返回 CoordinationState
```

---

#### Skills 系统详解

**`skills/base.py`** — 抽象基类 + 注册表（对齐参考项目）：

```python
class ErrorStrategy(str, enum.Enum):
    RETRY = "retry"      # 重试 max_retries 次
    SKIP = "skip"        # 跳过继续
    ABORT = "abort"      # 终止整个管线

class SkillStatus(str, enum.Enum):
    PENDING / RUNNING / COMPLETED / FAILED / SKIPPED

class SkillResult(BaseModel):
    status: SkillStatus = SkillStatus.COMPLETED
    data: dict | None = None
    error_message: str | None = None
    screenshots: list[str] | None = None    # MinIO keys
    duration_ms: int | None = None

class BaseSkill(ABC):
    skill_name: ClassVar[str]
    description: ClassVar[str]
    params_model: ClassVar[type[BaseModel]]
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.RETRY
    max_retries: ClassVar[int] = 2

    @abstractmethod
    async def execute(self, params: BaseModel, context: dict | None) -> SkillResult: ...

    def validate_params(self, raw_params: dict) -> BaseModel: ...
    def to_audit_dict(self, params: BaseModel) -> dict: ...   # 脱敏审计

SKILL_REGISTRY: dict[str, type[BaseSkill]] = {}
def register_skill(cls): ...   # 装饰器注册
def get_skill(name): ...        # 按名查找
def list_skills(): ...          # 列出全部
```

**`skills/executor.py`** — 流水线执行（对齐参考项目）：

```
execute_pipeline(steps: list[SkillStep], context, audit_callback) → PipelineResult
  │
  └── for step in steps:
      ├── get_skill(step.skill_name) → BaseSkill
      ├── skill.validate_params(step.params) → 校验参数
      ├── for attempt in range(max_attempts):
      │   └── skill.execute(params, context) → SkillResult
      ├── 记录 step_result
      ├── audit_callback(step_index, skill_name, audit_dict, result)
      └── 按 error_strategy 处理失败：
          ├── RETRY → 已在内部完成重试
          ├── SKIP → 跳过继续
          └── ABORT → 终止管线
```

---

#### Demo Seed 详解

**`demo_seed.py`** — 内存演示数据生成器（对齐参考项目）：

```
populate_all_stores()  ← FastAPI lifespan 中调用
  │
  ├── 1. _generate_tasks(rng, now, count=250)
  │      └── 250 条任务记录（30 天分布，状态/时长/错误类型随机）
  │
  ├── 2. _generate_approvals(rng, tasks)
  │      └── 约 50 条审批记录（pending/approved/rejected）
  │
  ├── 3. _generate_audit_logs(rng, tasks, approval_store)
  │      └── 约 120 条审计日志（NAVIGATE/CLICK/INPUT_TEXT 等动作）
  │
  ├── 4. _generate_model_calls(rng, tasks, count=1200)
  │      └── 1200 条 LLM 调用记录（light/standard/heavy 三档 token 分布）
  │
  ├── 5. _seed_cache_stats()
  │      └── 25 条 Action 缓存 + 历史命中统计
  │
  └── 6. configure 各 store（供后续 Approval/Audit/Dashboard 路由使用）
```

---

#### 全局数据模型

**`app/schemas.py`**：

```python
class TaskTriggerRequest(BaseModel):
    """任务触发请求（Java → Python）。"""
    task_id: str
    org_id: str
    user_id: str
    goal: str
    params: dict = {}
    workflow_id: str | None = None

class TaskStateResponse(BaseModel):
    """任务状态响应。"""
    task_id: str
    state: str
    current_step: int = 0
    total_steps: int = 0
    message: str = ""

class SseEvent(BaseModel):
    """SSE 事件。"""
    task_id: str
    event_type: str
    data: dict
    timestamp: datetime
```

#### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/ai/tasks` | 触发任务执行（经 Coordinator 编排） |
| GET | `/api/v1/ai/tasks/{id}/state` | 查询任务当前状态 |
| POST | `/api/v1/ai/tasks/{id}/abort` | 终止任务 |
| POST | `/api/v1/ai/tasks/{id}/resume` | 断点续跑（跳过已完成 SubTask） |
| GET | `/api/v1/ai/sse/tasks/{id}` | SSE 订阅，推送 CoordinationState 变更 |

#### 执行流程

```
POST /api/v1/ai/tasks
  │
  ├── 1. Coordinator.run(task_id, org_id, goal)
  │      └── Planner.create_plan(goal) → TaskPlan (N 个 SubTask)
  │
  ├── 2. Coordinator._execute_plan(state, plan)
  │      └── for subtask in plan.subtasks:
  │          ├── SSE 推送 {event_type: "subtask_start", data: {subtask}}
  │          ├── Executor.execute_subtask(subtask) → ExecutionResult
  │          ├── SSE 推送 {event_type: "subtask_end", data: {result}}
  │          ├── JavaBackendClient.update_task_state()
  │          └── 失败 → _handle_failure()
  │              ├── REPLAN → Planner.replan() → 重新执行
  │              ├── ABORT → SSE 推送 error，回调 failed
  │              └── SKIP → 继续
  │
  └── 3. 结束：SSE 推送 {event_type: "complete", data: {state}}
         └── JavaBackendClient.update_task_state(final)
```

#### 配置扩展

```python
# 执行器配置
executor_max_concurrent: int = 5
executor_step_timeout: int = 60

# LLM 配置（Planner fallback 版不需要；M4.1 接入时启用）
# llm_provider / llm_api_key / llm_model — M4.1 配置

# Demo seed
demo_seed_enabled: bool = True

# 内部鉴权
internal_api_token: str = "finrpa-internal-secret"
```

#### Docker 基础镜像

从 `ghcr.io/astral-sh/uv:python3.11-bookworm-slim` 切换为 `mcr.microsoft.com/playwright:v1.49.0-jammy-full`，该镜像已预装 Chromium 浏览器驱动。

#### 关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| Agent 架构 | Planner（fallback 版）+ Executor + Coordinator 三层 | 接口对齐参考项目，Planner 内部实现 M4.1 替换 |
| 执行器底层 | Skyvern ForgeAgent + Skills Pipeline | Agent 编排 → Skill 执行 → Skyvern 浏览器操作 |
| Skills 系统 | BaseSkill + SKILL_REGISTRY + execute_pipeline | 与参考项目完全对齐，支持错误策略和审计 |
| Demo 数据 | Python 内存 store（dict） | 启动时填充，无需数据库依赖，供 Dashboard 等路由使用 |
| LLM 容错 | ActionCache / ModelRouter 推迟到 M5 | 需要真实 LLM 调用数据才有意义 |
| 浏览器管理 | 完全委托 Skyvern | 避免重复实现，利用已验证的隔离机制 |
| 状态持久化 | Python 不存业务状态，全部回调 Java | 数据权威单一原则 |
| 并发控制 | `asyncio.Semaphore` | MVP 阶段无需复杂调度 |
| 渐进增强 | Planner 接口稳定，内部实现分阶段替换 | M2.1 fallback → M4.1 LLM → M5 容错，外部接口不变 |

---

#### M2.2 Java↔Python HTTP 客户端 + SSE 透传 ✅

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.1 |
| **产出物** | Java `ai/` 模块完整代码 + 单元测试 |
| **验收标准** | Java 调 Python 接口成功；SSE 流透传到前端无丢失；Python 服务不可用时返回 503 + 重试 |
| **状态** | ✅ 已完成（2026-07-29）。新增文件：`ai/client/AiServiceClient.java`、`ai/client/dto/{TaskTriggerRequest,TaskTriggerResponse,TaskStateResponse,TaskAbortResponse,AiException}.java`、`ai/sse/{AiSseProxy,SseEventDto}.java`、`ai/controller/AiProxyController.java`、`ai/config/{AiServiceProperties,AiWebClientConfig}.java`；修改文件：`pom.xml`（新增 webflux 依赖）、`application.yml`（新增 ai 配置块）、`SecurityConfig.java`（放行 `/ai/sse/**`）、`ErrorCode.java`（新增 AI_SERVICE_ERROR/UNAVAILABLE/TIMEOUT 三个错误码）。单元测试：`AiServicePropertiesTest`（3）、`AiProxyControllerTest`（8）、`AiSseProxyTest`（3），14 个单元测试全部通过；全量回归 13 个测试类 0 失败 0 错误。Python 服务不可用时 Controller 抛 `AiException`（继承 BusinessException，错误码 50301），由 GlobalExceptionHandler 统一返回 BaseResponse |

##### 技术实现方案

**核心设计原则**：Java 作为前端与 Python 之间的**中间层**，承担鉴权、路由、SSE 透传三项职责。前端不直接调用 Python，全部经 Java 转发。

**新增文件清单**：

```
finance-backend/src/main/java/com/finrpa/ai/
├── client/
│   ├── AiServiceClient.java       # HTTP Interface 客户端（~80 行）
│   └── dto/
│       ├── TaskTriggerRequest.java  # Java → Python 触发请求 DTO（~40 行）
│       ├── TaskTriggerResponse.java # Python 返回触发响应（~20 行）
│       ├── TaskStateResponse.java   # Python 返回状态响应（~30 行）
│       └── AiException.java         # AI 服务调用异常（~30 行）
├── sse/
│   ├── AiSseProxy.java             # SSE 透传服务（~100 行）
│   └── SseEventDto.java            # SSE 事件 DTO（~30 行）
├── controller/
│   └── AiProxyController.java      # 对外暴露的代理端点（~80 行）
└── config/
    └── WebClientConfig.java        # WebClient + 鉴权 Header 配置（~50 行）
```

**新增文件明细**：

| 文件 | 行数估算 | 职责 |
|------|----------|------|
| `AiServiceClient.java` | ~80 | Spring HTTP Interface 声明式客户端，调用 Python API |
| `TaskTriggerRequest.java` | ~40 | 触发任务请求 DTO（含 taskId/orgId/userId/goal/params） |
| `TaskTriggerResponse.java` | ~20 | 触发响应（含 taskId） |
| `TaskStateResponse.java` | ~30 | 状态响应（含 state/currentStep/totalSteps/message） |
| `AiException.java` | ~30 | AI 服务不可用/超时异常 |
| `AiSseProxy.java` | ~100 | SSE 透传：从 Python 订阅 Flux → 写入 Java SseEmitter → 推给前端 |
| `SseEventDto.java` | ~30 | SSE 事件结构（eventType/data/timestamp） |
| `AiProxyController.java` | ~80 | 对外代理端点：触发任务 + SSE 订阅 |
| `WebClientConfig.java` | ~50 | WebClient 配置（baseUrl/超时/重试/X-Internal-Token Header） |

**核心接口定义**：

`AiServiceClient.java` — Spring 6 HTTP Interface：

```java
@HttpExchange("/api/v1/ai")
public interface AiServiceClient {

    @PostExchange("/tasks")
    TaskTriggerResponse triggerTask(@RequestBody TaskTriggerRequest request);

    @GetExchange("/tasks/{taskId}/state")
    TaskStateResponse getTaskState(@PathVariable String taskId);

    @PostExchange("/tasks/{taskId}/abort")
    void abortTask(@PathVariable String taskId);

    @PostExchange("/tasks/{taskId}/resume")
    void resumeTask(@PathVariable String taskId, @RequestBody ResumeRequest request);
}
```

`AiSseProxy.java` — SSE 透传核心逻辑：

```
AiSseProxy.proxySse(taskId, httpServletResponse)
  │
  ├── 1. 构建 WebClient 请求 GET /api/v1/ai/sse/tasks/{taskId}
  ├── 2. 订阅 Flux<ServerSentEvent>（timeout: 1h）
  ├── 3. 将每个 event 转换为 SSE 格式写入 HttpServletResponse
  ├── 4. 连接断开 / 超时 → 自动清理
  └── 5. 异常处理：Python 不可用时发送 error 事件
```

`AiProxyController.java` — 对外端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/ai/tasks` | 触发任务（前端 → Java → Python） |
| GET | `/api/v1/ai/sse/tasks/{id}` | SSE 订阅（Java 透传 Python 流） |
| GET | `/api/v1/ai/tasks/{id}/state` | 代理查询任务状态 |

**配置项**（`application.yml` 新增）：

```yaml
ai:
  base-url: http://finance-ai:8000      # Python 服务地址
  internal-token: ${AI_INTERNAL_TOKEN:finrpa-internal-secret}
  connect-timeout: 5s                     # 连接超时
  read-timeout: 60s                       # 读取超时
  sse-timeout: 3600s                      # SSE 长连接超时（1 小时）
  retry:
    max-attempts: 3
    backoff: 1000ms                       # 初始退避 1s，指数递增
```

**Docker Compose 配置**：

```yaml
finance-ai:
  environment:
    - BACKEND_BASE_URL=http://finance-backend:8080
    - INTERNAL_API_TOKEN=finrpa-internal-secret
  networks:
    - finrpa-network

finance-backend:
  environment:
    - AI_BASE_URL=http://finance-ai:8000
    - AI_INTERNAL_TOKEN=finrpa-internal-secret
  networks:
    - finrpa-network
```

**服务鉴权机制**：

```
Java → Python 请求：X-Internal-Token: finrpa-internal-secret（WebClient 默认 Header）
Python → Java 请求：X-Internal-Token: finrpa-internal-secret（JavaBackendClient 添加）
```

两端共享同一密钥，通过 Docker 网络隔离 + Header 校验实现服务间安全。

**M2.2 与 M2.3 的接口约定**：

M2.2 需要 Python 回调 Java 的内部 API，M2.3（Java agent 模块）需实现以下端点（接口契约在 M2.2 阶段确定，实现在 M2.3 完成）：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/internal/tasks/{id}/state` | 更新任务状态 |
| POST | `/api/v1/internal/tasks/{id}/subtasks` | 更新子任务状态 |
| POST | `/api/v1/internal/screenshots` | 上传截图（转发 MinIO） |
| POST | `/api/v1/internal/audit/logs` | 上报审计日志 |
| POST | `/api/v1/internal/llm/calls` | 记录 LLM 调用 |
| POST | `/api/v1/internal/llm/needs-human` | 转人工接管 |

这些端点统一通过 `X-Internal-Token` Header 鉴权，仅 Docker 内网可达，不对外暴露。

**关键设计决策**：

| 决策 | 方案 | 理由 |
|------|------|------|
| HTTP 客户端 | Spring HTTP Interface（`@HttpExchange`） | 类型安全、声明式、统一配置 |
| SSE 实现 | Java `SseEmitter` + WebClient `bodyToFlux` | Spring Boot 原生支持，无需额外依赖 |
| 服务鉴权 | `X-Internal-Token` Header + Docker 网络隔离 | 简单有效，后续可升级为 mTLS |
| 超时策略 | 连接 5s / 读取 60s / SSE 1h | 分离长短任务场景 |
| 重试 | Spring Retry，指数退避 | 瞬态错误自动恢复 |
| 任务状态 | Java 为权威源，Python 回调更新 | 保证数据一致性 |
| 异常处理 | Python 不可用时返回 503 + SSE error 事件 | 前端可感知服务状态 |

**实现说明（M2.2 落地偏差）**：
- **HTTP 状态码 vs 业务错误码**：原计划 Python 不可用时返回 HTTP 503，实际落地为返回 HTTP 200 + `BaseResponse{code:50301, message:"AI 服务不可用"}`，与项目现有统一响应风格一致（GlobalExceptionHandler 统一返回 BaseResponse）。前端通过业务错误码 50301 识别 AI 服务不可用场景。
- **SSE 鉴权暂缓**：SSE 端点 `/ai/sse/**` 暂时在 SecurityConfig 放行（permitAll），因 EventSource API 无法携带 Authorization Header。TODO M2.5：改为 query 参数 token 鉴权（如 `?token=xxx`）。
- **重试机制**：M2.2 阶段未启用 Spring Retry 注解（`@Retryable`），仅保留配置项 `ai.retry.*`。重试逻辑推迟到 M2.6 联调阶段，结合真实瞬态错误场景调优（max-attempts/backoff 参数）。原因：M2.2 单元测试无法验证重试行为，需集成测试。
- **HTTP Interface 客户端 Bean**：通过 `HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient)).build()` 创建代理，Spring Boot 3.2.5 原生支持，无需额外 starter。
- **WebClient 单例**：仅配置 `aiWebClient`（同步调用），SSE 透传复用同一 WebClient（Flux 层通过 `.timeout(Duration.ofSeconds(sseTimeout))` 单独控制长连接超时），避免维护两个 WebClient。
- **AiException 设计**：继承 `BusinessException` 复用 `GlobalExceptionHandler.businessExceptionHandler`，无需单独注册 `@ExceptionHandler`。错误码扩展三个：`AI_SERVICE_ERROR(50300)` / `AI_SERVICE_UNAVAILABLE(50301)` / `AI_SERVICE_TIMEOUT(50302)`。
- **内部 API 契约**：M2.2 仅确定 Python 回调 Java 的 6 个内部端点契约（见"M2.2 与 M2.3 的接口约定"），实际实现在 M2.3。
- **接口路径**：对外端点实际访问路径为 `/api/ai/*`（context-path `/api` + `@RequestMapping("/ai")`），与设计文档 9.1 节 `/api/v1/ai/*` 略有差异（少 `/v1`），与项目现有风格一致（auth/tenant 等模块均无 `/v1` 前缀）。
- **驼峰统一命名策略（2026-07-29 调整）**：全链路统一使用 camelCase，消除 Java↔Python 字段映射成本。Python 端 Pydantic 配置 `alias_generator=to_camel, populate_by_name=True`（`schemas.py`），SSE 事件 `data` 字段手动输出驼峰（`taskId`/`currentStep`/`totalSteps`/`message`/`timestamp`）；Java 端 WebClient 移除 SNAKE_CASE 配置，使用默认驼峰。前端无需字段映射，直接接收驼峰。
- **联调测试结果（2026-07-29 第一轮：无认证直连）**：M2.2 全部端点联调通过。① Python 健康检查 200 ✅；② Java 健康检查 200 ✅；③ Docker 内网连通（Java 容器访问 finance-ai:8000）✅；④ Java 代理触发任务 POST /api/ai/tasks → Python 返回 running ✅；⑤ Java 代理查询状态 GET /api/ai/tasks/{id}/state → 返回 completed ✅；⑥ Java 代理终止任务 POST /api/ai/tasks/{id}/abort → 返回 aborted:true ✅；⑦ SSE 透传 GET /api/ai/sse/tasks/{id} → 透传 progress + complete 事件，保留 event name 与 data ✅。17 个单元测试全部通过。
- **联调测试结果（2026-07-29 第二轮：带 JWT 认证 + 前端代理）**：① 后端登录 POST /api/auth/login（admin/admin123）→ 返回 accessToken + user ✅；② 带 Bearer token 触发任务 POST /api/ai/tasks → running ✅；③ 带 token 查询状态 → completed ✅；④ 带 token 终止任务 → aborted:true ✅；⑤ SSE 透传收到 progress + complete 两个事件（data 为驼峰，前端直接接收）✅；⑥ 前端 Vite 代理登录 POST localhost:8081/api/auth/login → 透传后端返回 token ✅。
- **联调测试结果（2026-07-29 第三轮：雪花算法 BIGINT + 驼峰统一）**：① 数据库重建（drop schema + Flyway V1-V6 重新迁移）✅；② 演示数据初始化 2 组织/10 部门/6 业务线/13 用户（雪花 ID 自动生成）✅；③ admin/admin123 登录返回 orgId=2082333077580967938 ✅；④ admin_demo_yhsec/123456 登录返回 userId=2082333078168170497 ✅；⑤ POST /api/ai/tasks 返回 taskId=2082333099000000099（雪花格式）✅；⑥ GET /api/ai/tasks/{id}/state 返回驼峰字段（taskId/state/currentStep/totalSteps/message）✅；⑦ GET /api/ai/sse/tasks/{id} 收到 progress + complete 事件，字段全驼峰（taskId/currentStep/totalSteps/message/timestamp）✅。
- **遗留问题（非 M2.2 引入，已修复）**：
  - ~~`DemoDataInitializer` UUID 类型 bug~~（已解决）：主键从 UUID 迁移为雪花算法 BIGINT，`@TableId(type = IdType.ASSIGN_ID)` 自动生成，`DEMO_DATA_ENABLED=true` 已开启。V2 迁移脚本所有主键改为 BIGINT。
  - **fill 注解问题（2026-07-29 修复）**：UserEO/RoleEO/UserRoleEO 的 `createTime`/`updateTime` 字段标注了 `@TableField(fill = FieldFill.INSERT)` 但未配置 MetaObjectHandler，导致 MyBatis-Plus 显式插入 null 违反 NOT NULL 约束。修复：移除 fill 注解，与 OrganizationEO/DepartmentEO 一致，使用数据库 `DEFAULT CURRENT_TIMESTAMP` 自动填充。
  - **UserRoleEO deleted 字段问题（2026-07-29 修复）**：UserRoleEO 有 `deleted` 字段但 `sys_user_role` 表无此列。修复：移除 deleted 字段及 DemoDataInitializer 中对应的 `setDeleted(0)` 调用。
  - **测试代码 String→Long（2026-07-29 修复）**：UUID→BIGINT 改动后 6 个测试文件（AuthControllerTest/AuthServiceTest/PermissionServiceTest/DemoDataInitializerTest/TenantServiceImplTest/TenantControllerTest）中 ID 字段类型从 String 改为 Long，编译通过。
  - **DemoDataInitializer @Transactional 失效（2026-07-29 修复）**：`generateDemoData()` 通过 `this.` 内部调用，绕过 Spring AOP 代理，事务不生效，部分失败留下脏数据。修复：将全部数据生成逻辑抽离到独立 `DemoDataGenerator` Bean（@Component），`DemoDataInitializer` 仅负责调度（注入 `DemoDataGenerator` 调用），`@Transactional` 通过外部 Bean 调用生效。验证：清理 DEMO 数据后重启，2组织/10部门/6业务线/13用户完整生成。
  - 登录 403 问题（已解决）：根因非 Security 配置，而是密码错误。数据库 `sys_user` 表中所有用户的密码哈希 `$2b$10$JMrmn2lRr...` 对应明文为 `admin123`（经 Spring BCryptPasswordEncoder 验证），与文档记载的 `admin` / `demo123` 不一致。文档已在 M1.4 节"开发环境账号密码表"更正：所有账号密码统一为 `admin123`。
  - Python `main.py` import 缺失（M2.1 遗留）：`from app.api import health` 缺 `sse, tasks`，已修复为 `from app.api import health, sse, tasks`。

#### M2.3 Java agent 模块（任务状态持久化） ✅

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M1.1、M1.2、M0.3 |
| **产出物** | `agent/` 模块完整代码 + 单元测试 |
| **描述** | 1. 实体：`AgentTaskEO` / `AgentSubTaskEO` / `CoordinationStateEO`（对应 `rpa_agent_task` / `rpa_agent_subtask` / `rpa_agent_coordination_state` 三张表，V7 Flyway 脚本）<br>2. `TaskStateMachine`：状态机（PENDING → EXECUTING → SUCCESS/FAILED/NEEDS_HUMAN/ABORTED），定义合法流转规则与终态判定<br>3. `TaskService` 接口 + `TaskServiceImpl`：创建任务、分页查询、详情查询、终止任务、Python 回调状态更新<br>4. 内部 API（Python 回调）：`POST /internal/tasks/{id}/state` / `POST /internal/tasks/{id}/subtasks`，由 `InternalTokenInterceptor` 拦截 `X-Internal-Token` Header 鉴权<br>5. 对外 API：`GET /tasks`（分页列表）/ `GET /tasks/{id}`（详情含子任务）/ `POST /tasks/{id}/abort`（终止）<br>6. 修改 `AiProxyController.triggerTask`：先持久化任务到 DB（生成雪花 taskId），再转发 Python<br>7. 修改 `JwtAuthenticationFilter`：暂存 userId 到 request attribute 供 Controller 读取<br>8. 租户隔离：Agent 表加入 `TenantConstant.IGNORED_TABLES`（内部回调无 JWT 上下文），对外接口在 Service 层手动按 orgId 过滤 |
| **验收标准** | ✅ 任务创建/查询/状态更新正常；✅ Python 回调能更新状态；✅ 内部 API 鉴权生效；✅ 编译通过；✅ 全量 177 个单元测试通过（含 agent 模块 67 个新增测试 + M2.2 回归 + 修复 TenantLineHandlerImplTest 预存 bug） |
| **状态** | ✅ 已完成（2026-07-29）。新增文件 24 个（实体 3 + 枚举 2 + Mapper 3 + DTO 7 + Service 3 + Controller 2 + 拦截器 1 + 常量 1 + 配置 1 + 迁移脚本 1）；修改文件 6 个（SecurityConfig/TenantConstant/JwtAuthenticationFilter/AiProxyController + 2 个测试文件）。修复预存 bug：TenantLineHandlerImplTest 在 UUID→BIGINT 迁移后未同步更新（StringValue→LongValue、"org-001"→雪花 ID）。单元测试补全：新增 agent 模块测试文件 7 个（TaskStateMachineTest 22 + TaskServiceImplTest 28 + TaskStateEnumTest 4 + SubTaskStateEnumTest 4 + InternalTokenInterceptorTest 4 + InternalTaskControllerTest 2 + TaskControllerTest 3），覆盖状态机合法/非法流转、终态判定、任务创建/查询/终止、Python 回调状态更新、内部 API 鉴权拦截器、对外控制器路由 |

#### M2.4 Python Executor 基础执行 + 状态回调

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M2.1、M2.3（API 契约确定） |
| **产出物** | `app/agent/executor.py` + `app/agent/event_bus.py` + `app/agent/coordinator.py` + `app/clients/java_backend.py` + `app/api/sse.py` + `app/api/tasks.py` |
| **描述** | 1. Executor：接收任务 → 调 Skyvern → 每步回调 Java 更新状态<br>2. `JavaBackendClient`：httpx 客户端，调用 Java 内部 API<br>3. 状态同步：每步执行后上报 Java（含截图元数据）<br>4. SSE 推送：执行进度实时推送给 Java 透传<br>5. 错误处理：执行失败上报终态<br>6. **事件总线（复刻 finrpa-enterprise 技术方案）**：基于 Redis Pub/Sub 实现发布-订阅模型，替代 asyncio.Queue 内存队列；每个任务独立 Redis 频道（`task:events:{task_id}`），支持多订阅者；终态事件缓存到 Redis（`task:terminal:{task_id}`，TTL 300s）供迟到订阅者获取 |
| **验收标准** | 任务执行全程状态在 Java 侧可查；截图元数据上报成功；SSE 进度推送无丢失 |
| **状态** | ✅ 已完成（2026-07-29）。事件总线从 asyncio.Queue 重构为 Redis Pub/Sub（复刻 enterprise/approval/pubsub.py 技术方案）：`TaskEventBus` 使用 `redis.asyncio` 实现，`main.py` lifespan 统一管理 Redis 客户端生命周期并注入事件总线单例。新增 `fakeredis>=2.26.0` 测试依赖，`test_event_bus.py` 用 `FakeAsyncRedis` 替代内存队列测试，新增 9 个测试用例（基本发布订阅、终态关闭流、迟到订阅者、多订阅者、is_active、cleanup、终态缓存、非终态不缓存、register 仅日志）。全量 45 个 Python 单元测试通过 |

#### M2.5 前端任务列表与详情页

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M1.3、M2.3（API 契约确定） |
| **产出物** | `routes/tasks/TasksPage.tsx`、`routes/tasks/TaskDetail.tsx`、`routes/tasks/TriggerTaskModal.tsx`、`components/BrowserStream.tsx`、`components/StatusBadge.tsx`、`components/Pagination.tsx`、`components/Icons.tsx`、`api/tasks.ts`、`api/sse.ts` |
| **描述** | 1. 任务列表：分页 + 状态筛选 + 关键词搜索（防抖 400ms）+ 自动轮询（执行中 5s / 空闲 30s）<br>2. 任务详情：基本信息 + 子任务时间线 + 操作日志占位（M3 接入审计 API）+ 终止任务<br>3. 浏览器实时流：基于原生 EventSource 订阅 Java SSE 透传（`/api/ai/sse/tasks/{taskId}`），渲染进度条 + 事件日志 + 截图占位（M3.1 接入 MinIO 后展示真实截图）<br>4. 触发任务入口：弹窗表单（目标 / JSON 参数 / workflowId），客户端校验 + 错误提示<br>5. 状态徽章：StatusBadge 组件，6 种任务状态 + 6 种子任务状态，含脉动小点（执行中类）<br>6. React Query Provider：main.tsx 注入全局 QueryClient（staleTime 5s / retry 1）<br>7. 顶部导航：RootLayout 添加"首页 / 任务"入口<br>8. 路由：/tasks、/tasks/:taskId<br>9. 不引入 @novnc/novnc（参考项目用其接 VNC，当前 M2.x fallback 模式无 VNC 流，避免冗余依赖） |
| **验收标准** | 任务列表正确展示；详情页时间线实时更新；浏览器流可见；触发任务后能看到执行过程 |
| **状态** | ✅ 已完成（2026-07-29）。新增 9 个文件（4 个页面/组件路由 + 4 个通用组件 + 2 个 API 封装），修改 3 个文件（types.ts 扩展任务相关类型 / router.tsx 注册路由 / RootLayout.tsx 顶部导航 / main.tsx 注入 QueryClientProvider / glass.css 追加 M2.5 任务管理样式约 720 行）。TypeScript 严格模式编译通过（167 modules），Vite 生产构建通过（347.80 kB gzipped 113.57 kB）。未引入新依赖（复用 @tanstack/react-query + dayjs + react-router-dom + axios）。SSE 端点 `/ai/sse/**` 已在 SecurityConfig 放行，EventSource 直接连接无需 token。 |

#### M2.6 端到端联调

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.1、M2.2、M2.3、M2.4、M2.5 |
| **产出物** | 联调问题清单 + 修复 |
| **描述** | 1. 完整链路：前端触发 → Java 鉴权+创建任务 → Python 执行 → SSE 透传 → 前端展示<br>2. 验证：状态一致性、SSE 连通性、截图上传、错误传播<br>3. 性能基线：单任务执行延迟、SSE 推送延迟 |
| **验收标准** | 端到端跑通一个简单导航任务；无状态丢失；SSE 延迟 < 1s |
| **状态** | ✅ 已完成（2026-07-30） |

**联调结果**：
- 完整链路跑通：前端触发 → Java 鉴权+创建任务（PostgreSQL 持久化）→ Python 执行 → SSE 透传 → 前端展示
- 状态一致性验证通过：PENDING → EXECUTING → SUCCESS 完整流转，数据库状态与前端展示一致
- SSE 连通性验证通过：EventSource 连接成功，事件实时推送，延迟 < 1s
- 任务执行约 2 秒完成（Python fallback 模式模拟执行）
- Console 无业务错误，Network 无 4xx/5xx 失败请求

**联调过程发现并修复的问题**：
1. **PostgreSQL jsonb 类型错误**：`rpa_agent_task.params` 列为 jsonb 类型，MyBatis-Plus 默认传 varchar 导致插入失败。修复：JDBC URL 添加 `?stringtype=unspecified` 参数，让 PostgreSQL 自动转换。
2. **Vite 配置缓存问题**：`vite.config.js`（TypeScript 编译产物）覆盖了 `vite.config.ts`，导致环境变量 `VITE_USE_MOCK=false` 不生效。修复：删除编译产物，确保 Vite 加载 `.ts` 源文件。
3. **Proxy target 默认值**：`vite.config.ts` 中 proxy target 默认值为 Docker 网络名 `http://finance-backend:8080`，本地运行需改为 `http://localhost:8080`。修复：调整默认值为 localhost，Docker 环境通过环境变量覆盖。

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
| v1.1 | 2026-07-28 | - | 更新 M1.1 任务状态为已完成；补充 M1.1 详细产出物描述（实体/DTO/Mapper/JWT/权限/认证/Security/API/代码规范）；补充验收标准验证结果；补充提交记录 |
| v1.1 | 2026-07-25 | - | 补充原项目 16 天进度对照缺失任务：新增 M1.5（毛玻璃 UI + SVG 图标）、M1.6（全站 i18n）、M9.5（SIT 系统集成测试）、M9.6（前后端字段对齐）；升级 M1.4 为演示数据生成器；新增 9.3 节原项目对照表；任务总数 47 → 51 |
| v1.2 | 2026-07-28 | - | 更新 M1.2 任务状态为已完成；补充 M1.2 详细产出物描述（TenantContext/TenantInterceptor/TenantLineHandlerImpl/TenantConstant/Entity/Mapper/DTO/Service/Controller/Flyway V6/MyBatisPlusConfig 修改/JwtAuthenticationFilter 修改）；补充验收标准验证结果（91 个单元测试通过）；同步 system-design.md 6.2 节实现说明（字段名 org_id 偏差、ThreadLocal 选择、Filter+Interceptor 分层） |
| v1.3 | 2026-07-28 | - | 更新 M1.3 任务状态为已完成；补充 M1.3 详细产出物描述（AxiosClient/auth.ts/types.ts/AuthStore/AuthGuard/LoginPage/Forbidden/RootLayout/router/styles）；补充验收标准验证结果（browser 自动化 11 项 PASS）；新增"前后端联调测试步骤"小节；同步 system-design.md 4.4 节实现说明（后端 controller 路径偏差 /api/v1、vite proxy rewrite、M1.5 范围最小化、i18n 暂不实现、2FA 暂不实现、RootLayout 占位、refresh 失败强制跳转） |
| v1.4 | 2026-07-29 | - | 更新 M2.1 任务状态为已完成；补充 M2.1 实际产出文件清单（agent/skills/clients/api/schemas/demo_seed 14 个新增 + config/main/pyproject/Dockerfile 4 个更新）；记录 20 个单元测试全部通过；记录 Skyvern 源码暂未引入的落地偏差（M2.1 fallback 模式不需要，M3 实际浏览器操作时引入）；同步 system-design.md 4.3 节 M2.1 落地偏差实现说明 |

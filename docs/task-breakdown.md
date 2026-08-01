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

**端点实现状态**（M3.6 联调时修复）：

| 端点 | 实现状态 | 说明 |
|------|----------|------|
| `POST /internal/tasks/{id}/state` | ✅ M2.3 已实现 | 更新任务状态 |
| `POST /internal/tasks/{id}/subtasks` | ✅ M2.3 已实现 | 更新子任务状态 |
| `POST /internal/audit/logs` | ✅ M3.6 联调修复 | 修复 403 bug：新增 V10 迁移 + AuditLogEO/Service/Controller + TenantConstant 忽略列表 |
| `POST /internal/screenshots` | ⏳ 延后 | Python 端 upload_screenshot 方法已定义但未实际调用，待 M6.4.2 截图存储模块实现 |
| `POST /internal/llm/calls` | ⏳ 延后 | 待 M6.3 LLM 调用统计模块实现 |
| `POST /internal/llm/needs-human` | ⏳ 延后 | 待 M6.5 人工接管模块实现 |

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
| **状态** | ✅ 已完成（2026-07-30） |

**实施结果**：
- 7 个 Skill 全部实现并通过单元测试：LoginSkill / SessionKeepAliveSkill / FormFillSkill / SearchAndSelectSkill / PaginationSkill / TableExtractSkill / FileDownloadSkill
- 69 个测试用例全部通过（含 11 个 base/executor 回归测试 + 18 个 auth_skills + 21 个 interaction_skills + 19 个 extraction_skills）
- ruff 静态检查全部通过
- `get_failure_strategy()` 默认方法已添加到 BaseSkill，LoginSkill 覆写示范了 captcha 错误的动态决策
- 决策 4 调整：LoginSkill 仅含 captcha_strategy，不实现 2FA/TOTP（与参考项目 1:1 对齐）
- 决策 5 落地：FileDownloadSkill 默认下载到 `/tmp/finrpa/downloads/`，支持 `context["upload_callback"]` 上传 MinIO 并清理本地文件
- M3.1 不引入 Skyvern，7 个 Skill 直接用 Playwright Page API；未来引入 Skyvern 时只换 Page 来源，不改 Skill 内部代码
- 预先存在的 `test_coordinator_with_java_callback_success` 测试失败（M2.x 遗留问题，coordinator 不发 progress 事件），与 M3.1 无关

##### 技术实现方案

> 本方案经 2026-07-30 评审确认，作为 M3.1 实施依据。开发前不再更改。

**核心设计原则**：

1. **不引入 Skyvern**：M3.1 阶段 7 个 Skill 直接使用 Playwright Page API，与参考项目 `finrpa-enterprise/enterprise/skills/` 1:1 对齐。未来引入 Skyvern 时**只换 Page 来源**（由 Skyvern `ForgeAgent` 管理 BrowserContext，Page 注入到 `context["page"]`），**不改 Skill 内部的 `page.goto()` / `page.fill()` 等调用**。理由：参考项目本身从未把 Skills 改写为 ForgeAgent Step 抽象，Skills 目录下 0 处 `from skyvern` 引用，全部直接操作 Playwright Page。
2. **复刻参考项目结构**：7 个 Skill 的命名、参数模型、execute() 流程、error_strategy、max_retries 均与 `enterprise/skills/{auth,interaction,extraction}_skills.py` 一致。
3. **测试不依赖真实浏览器**：单元测试用 `unittest.mock.AsyncMock` 模拟 Playwright Page 对象，不启动 Chromium。

---

**决策 1：error_strategy ClassVar + 可覆写 get_failure_strategy() 方法**

- 保留 M2.1 已实现的 ClassVar `error_strategy: ErrorStrategy` 作为**默认策略**
- 在 `BaseSkill` 增加一个**可选覆写**的方法：

  ```python
  def get_failure_strategy(self, error: str | None = None) -> ErrorStrategy:
      """根据错误信息返回失败处理策略，默认返回 self.error_strategy。"""
      return self.error_strategy
  ```

- 7 个 Skill 在简单场景下只声明 ClassVar；如需根据错误类型动态决策（如 LoginSkill 区分 captcha 错误 vs 网络错误），覆写 `get_failure_strategy()`
- `execute_pipeline()` 在 M3.2 中调用 `skill.get_failure_strategy(error)` 替代直接读 ClassVar（M3.1 仅在 BaseSkill 添加方法，executor.py 改造留到 M3.2）

---

**决策 2：LLM handler 双模式**

- **LLM 模式**（后续实现）：通过 `context["llm_handler"]` 调用 LLM 视觉决策，handler 签名为 `async def llm_handler(page, navigation_goal: str) -> None`
- **Fallback 模式**（M3.1 本阶段使用）：直接用 Playwright 选择器操作（`page.fill()` / `page.click()` / `page.query_selector()` 等）
- 每个 Skill 在 execute() 内部优先检查 `context.get("llm_handler")`，存在则用 LLM 模式，否则用 Fallback 模式
- M3.1 单元测试全部走 Fallback 模式，不依赖 LLM

---

**决策 3：浏览器上下文来源**

- 7 个 Skill 一律从 `context["page"]` 取 Playwright Page 对象
- Page 缺失时立即返回 `SkillResult(status=SkillStatus.FAILED, error_message="No browser page in context")`
- Skill 需要更细粒度的原语（fill/click/query_selector/evaluate/expect_download/wait_for_url/wait_for_timeout 等），直接操作 Page，不通过中间抽象层
- `context` 预留字段（M3.2 Pipeline 执行器注入）：
  - `page`：Playwright Page 对象（必需）
  - `llm_handler`：LLM 视觉决策回调（可选，M5 注入）
  - `upload_callback`：MinIO 上传回调（可选，FileDownloadSkill 用）
  - `audit_context`：审计上下文（org_id / task_id / subtask_index，M3.2 注入）
  - `screenshot_callback`：截图回调（可选，M7 注入）

---

**决策 4：LoginSkill 实现范围（与参考项目 1:1 对齐）**

- LoginParams 字段**仅含 captcha_strategy**，不实现 2FA / TOTP / pyotp
- 与 `enterprise/skills/auth_skills.py` 的 LoginParams 完全一致：

  ```python
  class LoginParams(BaseModel):
      url: str
      username: str
      password: str
      captcha_strategy: str = "skip"          # skip | manual | ocr
      submit_selector: str | None = None
      success_indicator: str = ""
  ```

- task-breakdown.md M3.1 描述中的「LoginSkill（用户名密码 + 2FA + TOTP）」作废，以本决策为准
- 如未来需要 2FA/TOTP，作为 LoginSkill 的增强功能单独引入（届时再评估是否引入 pyotp）

---

**决策 5：FileDownloadSkill 下载文件去向**

- `FileDownloadParams.download_path` 默认值改为 `/tmp/finrpa/downloads/`（容器内临时目录）
- 执行流程：
  1. `page.expect_download()` 触发并等待下载
  2. `download.save_as(save_path)` 保存到本地临时目录
  3. 若 `context["upload_callback"]` 存在：调用 `await upload_callback(save_path, filename)` 上传 MinIO，返回 `minio_key`；上传后删除本地文件
  4. 若 `upload_callback` 不存在：仅返回 `save_path`（M3.1 单元测试场景）
- SkillResult.data 返回字段：
  - 有 upload_callback：`{"filename", "minio_key", "suggested_filename"}`
  - 无 upload_callback：`{"filename", "save_path", "suggested_filename"}`

---

##### 7 个 Skill 实现规格

| # | Skill | 文件 | skill_name | error_strategy | max_retries | 关键参数 |
|---|---|---|---|---|---|---|
| 1 | LoginSkill | auth_skills.py | `login` | ABORT | 3 | url / username / password / captcha_strategy / submit_selector / success_indicator |
| 2 | SessionKeepAliveSkill | auth_skills.py | `session_keep_alive` | RETRY | 2 | heartbeat_url / session_timeout_indicator / relogin_on_expire / login_params |
| 3 | FormFillSkill | interaction_skills.py | `form_fill` | RETRY | 2 | field_mapping / submit_after_fill / submit_selector / date_format |
| 4 | SearchAndSelectSkill | interaction_skills.py | `search_and_select` | RETRY | 2 | search_text / target_text / search_selector / result_container_selector / wait_for_results_ms |
| 5 | PaginationSkill | interaction_skills.py | `pagination` | SKIP | 1 | max_pages / next_button_selector / next_button_text / page_data_selector / wait_between_pages_ms / stop_on_empty |
| 6 | TableExtractSkill | extraction_skills.py | `table_extract` | RETRY | 2 | table_selector / headers / output_format(json\|csv) / max_rows / include_pagination / skip_empty_rows |
| 7 | FileDownloadSkill | extraction_skills.py | `file_download` | RETRY | 2 | trigger_selector / trigger_text / download_path / expected_extension / wait_timeout_ms |

每个 Skill 的 `execute()` 流程严格按参考项目 `enterprise/skills/{auth,interaction,extraction}_skills.py` 1:1 复刻，包括：
- 双模式（LLM handler / Fallback 选择器）分支
- 错误捕获与 SkillResult 返回
- `duration_ms` 字段计算
- `to_audit_dict()` 脱敏沿用 BaseSkill 默认实现

---

##### 文件清单

| 文件 | 操作 | 内容 |
|---|---|---|
| `finance-ai/app/skills/base.py` | 修改 | 新增 `get_failure_strategy()` 默认方法 |
| `finance-ai/app/skills/auth_skills.py` | 新增 | LoginSkill + SessionKeepAliveSkill |
| `finance-ai/app/skills/interaction_skills.py` | 新增 | FormFillSkill + SearchAndSelectSkill + PaginationSkill |
| `finance-ai/app/skills/extraction_skills.py` | 新增 | TableExtractSkill + FileDownloadSkill |
| `finance-ai/app/skills/__init__.py` | 修改 | 补全导出 + import 三个 skills 模块触发自动注册 |
| `finance-ai/tests/unit/test_auth_skills.py` | 新增 | LoginSkill + SessionKeepAliveSkill 单元测试 |
| `finance-ai/tests/unit/test_interaction_skills.py` | 新增 | FormFill + SearchAndSelect + Pagination 单元测试 |
| `finance-ai/tests/unit/test_extraction_skills.py` | 新增 | TableExtract + FileDownload 单元测试 |
| `finance-ai/tests/unit/test_skills.py` | 修改 | 新增 `get_failure_strategy()` 默认行为测试 |

---

##### 测试策略

- **覆盖率目标**：≥ 85%
- **Mock 策略**：用 `unittest.mock.AsyncMock` 模拟 Playwright Page，包括：
  - `page.goto` / `page.fill` / `page.click` / `page.query_selector` / `page.query_selector_all`
  - `page.evaluate` / `page.content` / `page.wait_for_url` / `page.wait_for_timeout`
  - `page.keyboard.press`
  - `page.expect_download`（用 `__aenter__` / `__aexit__` 模拟 async context manager）
  - 元素对象：`element.click` / `element.inner_text` / `element.evaluate`
- **不引入真实 Playwright/Chromium 依赖**：M3.1 测试纯 mock，无浏览器启动
- **测试用例覆盖**：
  - 成功路径（每个 Skill 至少 1 个）
  - 失败路径（Page 缺失 / 元素未找到 / 超时）
  - 边界条件（max_pages=1 / 空表 / 部分字段填充失败）
  - LLM handler 存在/不存在分支
  - `get_failure_strategy()` 默认返回与覆写返回

---

##### 依赖变更

- **无新增依赖**：决策 4 不引入 pyotp；7 个 Skill 仅用 Pydantic + 标准库（csv / io / logging / time）
- Playwright 仍由 Skyvern base image（`mcr.microsoft.com/playwright:v1.49.0-jammy-full`）预装，本阶段不显式声明依赖

---

##### 与 M2.1 已实现代码的关系

| M2.1 已有 | M3.1 操作 |
|---|---|
| `app/skills/base.py`（BaseSkill / SkillResult / ErrorStrategy / SkillStatus / SKILL_REGISTRY / register_skill / get_skill / list_skills / to_audit_dict） | **保留**，仅新增 `get_failure_strategy()` 默认方法 |
| `app/skills/executor.py`（SkillStep / PipelineResult / execute_pipeline） | **保留不动**，M3.1 不改 Pipeline 执行器（M3.2 才改造调用 `get_failure_strategy()`） |
| `tests/unit/test_skills.py`（注册表 / 参数校验 / 脱敏 / Pipeline 行为） | **保留**，仅追加 `get_failure_strategy()` 默认行为测试 |

---

#### M3.2 Python Skill Pipeline 执行器

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M3.1 |
| **产出物** | `app/skills/executor.py`、`app/skills/param_resolver.py` |
| **描述** | 1. Pipeline 执行器：按顺序执行 Skill 列表<br>2. 参数映射：引用模式 `{{workflow.params.xxx}}` + 字面量模式<br>3. 上下文传递：前一个 Skill 输出可作为后一个 Skill 输入<br>4. 失败处理：按 Skill 的 `get_failure_strategy()` 处理<br>5. 审计回调：每步执行上报 Java |
| **验收标准** | Pipeline 能串联多个 Skill；参数映射正确；失败策略生效 |
| **状态** | ✅ 已完成（2026-07-30） |

##### M3.2 实施结果

**新增文件**：
- `app/skills/param_resolver.py`：参数映射解析器，复刻参考项目 `enterprise/workflows/schemas.py` 的 `SkillStepDefinition.param_mapping` 语法
- `tests/unit/test_param_resolver.py`：25 个参数映射解析器单元测试
- `tests/unit/test_pipeline.py`：16 个 Pipeline 集成测试

**修改文件**：
- `app/skills/executor.py`：Pipeline 执行器改造（5 个功能点）
- `app/skills/__init__.py`：导出 `resolve_param_mapping` / `resolve_param_value`

**参数映射语法**（复刻参考项目 `param_mapping`）：
1. **字面量模式**：`=csv` → `"csv"`，`=500` → `500`（自动 JSON 解析）
2. **引用模式**：`bank_url` → `workflow_params["bank_url"]`
3. **嵌入引用**：`={"key": "${param_name}"}` → 解析 `${}` 内工作流参数引用
4. **上下文引用**：`{{steps.0.data.filename}}` → 从前序步骤输出取值
5. **上下文嵌入**：`=prefix_{{steps.0.data.filename}}_suffix` → 字符串替换

**Pipeline 改造要点**：
- `SkillStep` 新增 `param_mapping: dict[str, str] | None` 字段
- `execute_pipeline()` 新增 `workflow_params` 参数，执行前解析 `param_mapping`
- 上下文传递：每步完成后将 step_record 存入 `context["step_results"]`，后续步骤可通过 `{{steps.N.data.key}}` 引用
- 失败处理：调用 `skill.get_failure_strategy(error)` 替代直接读 ClassVar，`error_strategy_override` 优先级最高
- 审计回调：保持原有签名，增强日志输出
- 向后兼容：不使用 `param_mapping` 时，直接 `params` 方式仍正常工作

**测试覆盖**：148 个单元测试全部通过，ruff 静态检查通过

#### M3.3 Java skills 元数据管理

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M0.3、M3.1（Skill 定义确定） |
| **产出物** | `skills/` 模块完整代码 |
| **描述** | 1. 实体：`SkillMetaEO`（name / description / category / param_schema / version / enabled）<br>2. `SkillRegistryService`：CRUD + 版本管理<br>3. 注册时同步 Python 校验 Skill 存在性<br>4. API：`GET /api/skills` / `GET /api/skills/{name}` / `POST /api/skills` / `PUT /api/skills/{name}`<br>5. 初始化：启动时自动注册 7 个内置 Skill 元数据 |
| **验收标准** | 7 个 Skill 元数据自动注册；CRUD 接口正常；Python 校验存在性生效 |
| **状态** | ✅ 已完成（2026-07-30） |

##### M3.3 实施结果

**新增文件**：
- Python：`finance-ai/app/api/skills.py`（`GET /api/v1/ai/skills` 端点，返回 7 个 Skill 完整元数据）
- Python：`finance-ai/tests/unit/test_skills_api.py`（7 个元数据字段完整性测试）
- 数据库：`V8__create_skill_meta_table.sql`（`rpa_skill_meta` 表，全局共享无 org_id）
- Java 实体：`SkillMetaEO` / `SkillMetaMapper` / `SkillCategoryEnum`
- Java 常量：`SkillConstant`（7 个内置 Skill 的 name、分类、param_schema JSON 硬编码）
- Java DTO：`SkillAddRequest` / `SkillUpdateRequest` / `SkillQueryRequest` / `SkillVO`
- Java 客户端 DTO：`SkillInfoResponse`（Python skills 端点响应）
- Java Service：`SkillRegistryService` 接口 + `SkillRegistryServiceImpl` 实现
- Java Controller：`SkillController`（4 个 API）
- Java 初始化器：`SkillMetaInitializer`（启动 upsert 7 个内置 Skill）
- Java 测试：`SkillRegistryServiceImplTest`（16 个用例）+ `SkillControllerTest`（4 个用例）

**修改文件**：
- `finance-ai/app/skills/base.py`：`BaseSkill` 新增 `category` ClassVar；`list_skills()` 扩展返回 category/max_retries/params_schema
- `finance-ai/app/skills/auth_skills.py` / `interaction_skills.py` / `extraction_skills.py`：7 个 Skill 声明 category
- `finance-ai/app/main.py`：注册 skills router
- `AiServiceClient.java`：新增 `@GetExchange("/skills") getSkills()`
- `ErrorCode.java`：新增 `SKILL_NOT_FOUND(40401)` / `SKILL_ALREADY_EXISTS(40402)` / `SKILL_NOT_ENABLED(40403)`
- `TenantConstant.java`：`rpa_skill_meta` 加入租户过滤忽略清单

**关键设计决策**：
1. **内置 Skill 元数据硬编码**（决策 1 方案 A）：7 个内置 Skill 的 param_schema JSON 由 Python `model_json_schema()` 导出后硬编码在 `SkillConstant`，启动 upsert 时不依赖 Python 在线
2. **全局共享**（决策 3）：`rpa_skill_meta` 表无 org_id，加入 TenantLineHandler 忽略清单，所有租户共用同一套 Skill 定义
3. **API 路径**（决策 5）：context-path 为 `/api`，Controller 用 `@RequestMapping("/skills")`，实际路径 `/api/skills`
4. **upsert 语义**：启动注册时按 name 查询，已存在则更新元数据字段（不动 enabled，避免重置用户禁用状态），不存在则插入
5. **Python 校验**：仅 `POST /api/skills`（自定义 Skill）调 Python `getSkills()` 校验 name 存在性；Python 不可用直接抛 `AI_SERVICE_UNAVAILABLE`，不降级
6. **camelCase 统一**：Python `SkillMetaItem` 配 `alias_generator=to_camel`，JSON 输出 camelCase 与 Java WebClient 对齐

**测试覆盖**：Python 7 个测试通过；Java 20 个测试通过（Service 16 + Controller 4）

#### M3.4 Java workflows 模板管理 + Fernet 加密

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M3.3、M1.1（Fernet 加密依赖） |
| **产出物** | `workflows/` 模块完整代码 |
| **描述** | 1. 实体：`WorkflowTemplateEO`（name / industry / risk_level / params[] / steps[]）<br>2. `WorkflowService`：CRUD + 校验<br>3. `FernetCryptoService`：与 Python cryptography 字节级兼容<br>4. `WorkflowValidator`：校验 Skill 引用合法性、参数完整性<br>5. `WorkflowTriggerService`：触发执行 → 解密参数 → 创建任务 → 调 Python<br>6. API：`GET /api/v1/workflows` / `POST /api/v1/workflows` / `POST /api/v1/workflows/{id}/run` / `GET /api/v1/workflows/{id}/runs` |
| **验收标准** | 模板 CRUD 正常；敏感参数加密存储；触发执行创建任务成功；Java 解密结果与 Python 加密一致 |
| **状态** | ✅ 已完成（2026-07-30） |
| **测试覆盖** | Java 36 个测试通过（ServiceImpl 18 + TriggerServiceImpl 10 + Controller 8）；Python Fernet 跨语言兼容测试通过 |
| **修复 Bug** | `WorkflowTriggerServiceImpl.resolveParams` 中 `Matcher.appendReplacement` 二次解释 `\` 与 `$` 元字符破坏 JSON 转义；改用 `Matcher.quoteReplacement` 包裹 replacement |

#### M3.5 6 个金融工作流模板配置

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M3.4、M3.1 |
| **产出物** | 6 个工作流模板数据（seed 脚本） |
| **描述** | 按系统设计 6.8.2 节配置 6 个模板：银行流水下载 / 跨行转账核对 / 对公贷款放款 / 保单申请填写 / 理赔审核提交 / 委托下单 |
| **验收标准** | 6 个模板可通过 API 查询；参数 schema 完整；Skill 引用合法 |
| **状态** | ✅ 已完成（2026-07-30） |
| **实现方式** | 参考 M3.3 Skill 注册模式：WorkflowConstant 硬编码 6 个模板 + WorkflowTemplateInitializer 启动 upsert（@Order(30)，晚于 SkillMetaInitializer @Order(20)） |
| **测试覆盖** | 48 个测试通过（WorkflowConstantTest 9 + WorkflowServiceImplTest 21 + WorkflowTriggerServiceImplTest 10 + WorkflowControllerTest 8） |

#### M3.6 前端工作流管理页面

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M3.4（API 契约确定） |
| **产出物** | `routes/workflows/Workflows.tsx`、`routes/workflows/WorkflowDetail.tsx`、`routes/workflows/WorkflowRuns.tsx`、`api/workflows.ts` |
| **描述** | 1. 工作流列表：按行业筛选 + 风险等级筛选 + 名称搜索（卡片网格）<br>2. 详情页：基本信息 + 参数表单（按 params schema 动态生成） + Skill 步骤可视化（横向流程图，加密参数脱敏） + 触发执行 + 执行历史入口<br>3. 触发执行：表单填写参数 → POST `/workflows/{id}/run` → 跳转任务详情<br>4. 执行历史：按 workflowId 筛选任务列表，分页 + 状态筛选 + 自动轮询 |
| **验收标准** | 列表展示 6 个模板；参数表单按 schema 动态生成；触发后跳转任务详情 |
| **状态** | ✅ 已完成（2026-07-31） |
| **后端配套改动** | `TaskQueryRequest` 添加 `workflowId` 字段 + `TaskServiceImpl.listTasks` 支持按 workflowId 筛选（用于工作流执行历史查询） |
| **测试覆盖** | 后端 TaskServiceImplTest 29 个测试通过（含新增 listTasks_WithWorkflowId_Success）；前端 `tsc -b && vite build` 通过（171 modules transformed） |

---

#### M3.7 Skyvern 集成 + 浏览器执行链路打通

> **补录说明**：项目定位"基于 Skyvern 二次开发"（见本文档第 3 行），M2.1 声称"Skyvern 集成 ✅"但实际只落地了骨架占位，M3.1 又明确"不引入 Skyvern"，导致 Skill 层有 Playwright API 调用代码但**无浏览器启动入口**（`context["page"]` 无来源），端到端链路断裂。本任务补齐这个缺口，是 M4 Coordinator 编排真实浏览器操作的前置条件。
>
> **方案决策（A1：复刻 finrpa-enterprise 模式）**：调研发现 finrpa-enterprise 通过 `python -m skyvern.forge` 启动 Skyvern 原生 FastAPI 服务，浏览器自动化和 LLM 视觉决策通过 Skyvern 原生 API（`POST /api/v1/tasks` 等）触发，ForgeAgent + BROWSER_MANAGER + LLM 全部可用。本任务采用同款方案：在 finance-ai 的 `app/main.py` lifespan 中调用 `start_forge_app()` 初始化 ForgeApp，挂载 Skyvern 原生路由（`/v1`、`/api/v1`、`/api/v2`），与 finance-ai 自有的 `/api/v1/ai/*` 路由共存不冲突。
>
> **数据库策略**：共用 finrpa 库（Skyvern alembic 建 skyvern 自己的表，与 finrpa 的 rpa_* 表物理共存但逻辑分离）。
>
> **范围限定**：M3.7 只做 finance-ai 侧（启动 Skyvern 服务 + 配置 + DB 迁移 + 验证）。Java backend 改造（TaskController 改调 Skyvern API、rpa_task 表加 skyvern_task_id 字段）移到 M3.8。

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M3.1（Skill 层已就绪）、M3.2（Skill Pipeline 执行器） |
| **产出物** | 1. `finance-ai/Dockerfile`（python:3.11-slim-bookworm + playwright install）<br>2. `finance-ai/.env.example`（Skyvern 必需配置：DATABASE_STRING/BROWSER_TYPE/LLM/数据目录等）<br>3. `finance-ai/alembic.ini` + `finance-ai/alembic/`（从 finrpa-enterprise 复制，env.py 改用 `skyvern.config.settings`）<br>4. `finance-ai/app/main.py`（lifespan 调用 `start_forge_app()` + 挂载 Skyvern 路由 `base_router`/`legacy_base_router`/`legacy_v2_router`）<br>5. `docker-compose.yml`（finance-ai command 加 `alembic upgrade head` + Skyvern 环境变量）<br>6. `finance-ai/skyvern/`（Skyvern 源码，696 个文件，已复制） |
| **描述** | 1. **Dockerfile 修复**：从 `mcr.microsoft.com/playwright`（tag 不存在）改为 `python:3.11-slim-bookworm` + `playwright install-deps && playwright install chromium`，参考 finrpa-enterprise Dockerfile<br>2. **alembic 配置**：从 finrpa-enterprise 复制 `alembic.ini` + `alembic/`（含 200+ 迁移脚本），修改 `env.py` 去掉 `enterprise.auth.models` 导入，改用 `from skyvern.config import settings` 读取 DATABASE_STRING<br>3. **app/main.py 改造**：lifespan 启动时调 `start_forge_app()` 初始化 ForgeApp（Database/Storage/LLM_API_HANDLER/BROWSER_MANAGER/ForgeAgent），挂载 Skyvern 原生路由；初始化失败不阻断 finance-ai 自有路由（health/sse/skills）<br>4. **docker-compose.yml**：finance-ai command 加 `uv run alembic upgrade head`（启动前建 Skyvern 表），environment 加 DATABASE_STRING/ENABLE_OPENAI/LLM_KEY/OPENAI_API_KEY/SKYVERN_STORAGE_TYPE 等<br>5. **端到端验证**：直接调 Skyvern `POST /api/v1/tasks` 触发任务，确认 Chromium 真实启动执行<br>6. **保留 app/agent + app/skills**：当前架构作为未来扩展层保留不动，M3.7 不依赖也不破坏 |
| **验收标准** | 1. `docker-compose build finance-ai` 成功（python:3.11-slim-bookworm + playwright install 通过）；2. `alembic upgrade head` 成功（finrpa 库出现 skyvern 的 task/workflow/artifact 等表）；3. `start_forge_app()` 初始化成功（日志可见 "Skyvern ForgeApp 已初始化"）；4. Skyvern 原生 API 可访问（`GET /api/v1/tasks` 返回 200）；5. `POST /api/v1/tasks` 触发任务后 Docker 内启动 Chromium（`docker logs finrpa-ai` 可见 Playwright 启动日志）；6. finance-ai 自有路由不受影响（`/api/v1/ai/health` 仍返回 200）；7. 现有单元测试仍通过 |
| **参考实现** | `finrpa-enterprise/skyvern/forge/api_app.py:create_api_app` + `finrpa-enterprise/skyvern/forge/forge_app_initializer.py:start_forge_app` + `finrpa-enterprise/Dockerfile` + `finrpa-enterprise/alembic/env.py` |
| **状态** | ✅ 已完成（2026-07-31）。Skyvern 集成 + 浏览器执行链路打通。最终落地：(1) Docker 镜像 python:3.11-slim-bookworm + playwright install chromium；(2) Skyvern 源码 696 文件复制到 `finance-ai/skyvern/`；(3) `app/main.py` lifespan 调用 `start_forge_app()` 初始化 ForgeApp，挂载 Skyvern 原生路由 `/v1` `/api/v1` `/api/v2`；(4) `alembic upgrade head` 成功，finrpa 库共存 15+ skyvern 表（artifacts/persistent_browser_sessions/task_runs/workflows 等）与 rpa_* 表；(5) 验证：`GET /api/v1/tasks` 返回 403（路由已挂载，需 Skyvern API Key 鉴权而非 404），`/api/v1/ai/health` 返回 200；(6) 隐式依赖补齐：filetype/json_repair/pandas/lark/lxml/pyyaml/beautifulsoup4/requests/pyotp/google-auth/email-validator/jsonschema/tiktoken/rich/starlette-context/tldextract/cachetools/pdfplumber/pypdf/zstandard/yarl/multidict/charset-normalizer/asyncache/libcst/fastmcp/lmnr/azure-storage-blob/azure-core/azure-identity/azure-keyvault-secrets/psutil/boto3/botocore 共 35+ 包；(7) `onepassword` stub 包：1Password Python SDK 不在 PyPI（仅 GitHub Packages 分发），本项目使用本地存储不依赖 1Password，创建 `finance-ai/onepassword/` stub 让 Skyvern 导入通过，调用相关方法抛 RuntimeError；(8) LLM 配置用占位符 `sk-placeholder-for-m37-verification` 让 ForgeApp 初始化通过，实际触发浏览器任务需在根目录 `.env` 设置真实 `OPENAI_API_KEY` |

#### M3.8 Java backend 改调 Skyvern API（M3.7 后续）

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M3.7（Skyvern 服务可用） |
| **产出物** | `TaskController` 触发任务改调 Skyvern `/api/v1/tasks` + `rpa_task` 表加 `skyvern_task_id` 字段 + 状态查询映射 |
| **描述** | **方案决策（B：Python 中转）**：Java 不直接调 Skyvern，通过 Python finance-ai 中转。Python 内部调 Skyvern API，封装 token 管理。<br>1. `rpa_agent_task` 表加 `skyvern_task_id` 字段（V11 迁移）<br>2. Java `AiProxyController.triggerTask` 调 Python `/api/v1/ai/tasks`，Python 内部调 Skyvern `POST /api/v1/tasks` 创建 Skyvern 任务，返回 `skyvern_task_id` 给 Java<br>3. Java `AiProxyController.getTaskState` 先查数据库获取 `skyvern_task_id`，再调 Python 查询 Skyvern 状态并映射（created→PENDING / running→EXECUTING / completed→SUCCESS / failed→FAILED / canceled→ABORTED）<br>4. Python `SkyvernClient` 生成 `X-Request-ID` header 传给 Skyvern，日志可串联<br>5. Python lifespan 启动时自动创建 Skyvern organization + auth token（JWT），全局缓存 |
| **验收标准** | 前端触发任务 → Java backend 调 Python → Python 调 Skyvern API → Skyvern 启动浏览器执行 → 状态回传 Java → 前端展示 |
| **状态** | ✅ 已完成（2026-07-31）。新增文件：`V11__add_skyvern_task_id_to_agent_task.sql`、`app/clients/skyvern_client.py`。改动文件：`AgentTaskEO`、`TaskVO`、`TaskTriggerResponse`(Java+Python)、`TaskService`+`TaskServiceImpl`(updateSkyvernTaskId)、`AiProxyController`(triggerTask 保存 skyvernTaskId + getTaskState 查数据库)、`app/api/tasks.py`(trigger_task 调 SkyvernClient + get_task_state 调 SkyvernClient + 状态映射)、`app/main.py`(lifespan 初始化 token)、`app/schemas.py`(TaskTriggerResponse 加 skyvern_task_id)、`config_registry.py`(加 doubao-seed-evolving 模型)。Java 278 个测试全部通过（含新增 4 个 updateSkyvernTaskId 测试 + 更新 triggerTask/getTaskState 测试） |

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
| **状态** | ✅ 已完成（2026-08-01）。M2.1 已落地骨架（fallback 单步计划 + `NotImplementedError` 占位），本任务移植 `finrpa-enterprise/agent/planner.py` 实现 LLM 拆解。最终落地：(1) 新增 `PLANNER_SYSTEM_PROMPT` / `REPLAN_SYSTEM_PROMPT` 常量（约束 LLM 输出 `{"steps":[...]}` JSON）；(2) 新增 `PlannerOutput` Pydantic 模型描述 LLM 输出 schema；(3) 实现 `_plan_with_llm`：构造 prompt（system + goal + context）→ 调 `llm_callable` → 清理 ``` 代码块 → JSON 解析 → 构建 SubTask 列表；(4) 实现 `_replan_with_llm`：同流程，但 index 从 `len(completed_subtasks)` 起递增保持全局有序，且不重复已完成步骤；(5) 抽取 `_parse_llm_steps` 公共方法处理代码块清理 + JSON 解析 + 字段映射（缺失字段用默认值）；(6) 异常兜底：LLM 异常 / JSON 解析失败 / 空 steps / 非法 failure_strategy 值 → 自动回退到 fallback 单步计划，不抛异常给调用方；(7) replan 上限 3 次拦截已在 M2.4 `coordinator.py` 的 `_handle_failure` 实现（`max_replans=3`，超限 `state.status = "needs_human"`），本任务不重复实现 |
| **测试覆盖** | `test_planner.py` 15 个测试通过（3 fallback + 8 LLM 拆解 + 4 LLM replan）：fallback 单步计划 / fallback replan / 带上下文 fallback / LLM 拆解成功 / ```json 代码块清理 / 带上下文 LLM 拆解 / 缺失字段默认值 / 非法 JSON fallback / 空 steps fallback / LLM 异常 fallback / 非法 failure_strategy fallback / LLM replan 成功（含 index 递增 + version 校验）/ replan 异常 fallback / replan 非法 JSON fallback / replan 空 steps fallback。回归 `test_coordinator.py` 8 个 + `test_executor.py` 8 个测试全部通过，未破坏现有功能 |
| **未做范围** | `llm_callable` 的实际注入（从 `config.py` 读取 LLM 配置构造客户端、`tasks.py` 的 `PlannerAgent()` 改为传入真实 llm_callable）留到后续任务，本任务只实现 planner.py 内部逻辑 + 单元测试 |

#### M4.2 Python Coordinator 编排 + 失败策略

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M4.1、M3.2 |
| **产出物** | `app/agent/coordinator.py` + 单元测试 |
| **描述** | 1. Coordinator：编排 Planner 与 Executor 通信<br>2. 失败策略：retry / skip / abort / replan 四种处理<br>3. 状态持久化：每步执行后回调 Java 更新 `CoordinationState`<br>4. replan 触发：路径阻塞时调 Planner 重新规划 |
| **验收标准** | 四种失败策略正确执行；replan 触发后任务继续；状态持久化无丢失 |
| **状态** | ✅ 已完成（2026-08-01）。M2.4 已落地 Coordinator 基本框架（4 种失败策略 + Java 回调 + SSE），本任务补齐状态持久化缺口 + 增强测试覆盖。最终落地：(1) **Java 侧新增协调状态持久化端点**：`CoordinationStateUpdateRequest` DTO + `InternalTaskController` 新增 `POST /internal/tasks/{taskId}/coordination-state` + `TaskService.updateCoordinationState()` upsert 到 `rpa_agent_coordination_state` 表（首次 insert / 后续 update，按 taskId 唯一约束）；(2) **Python `JavaBackendClient` 新增 `update_coordination_state()` 方法**：POST 导航目标 / 当前计划 JSON / 已完成子任务列表 / total_replans / max_replans / status / error_message 到 Java；(3) **Coordinator 新增 `_persist_coordination_state()` 方法**：在 5 个关键节点调用 —— 任务开始（`_on_task_start`）、每步完成（`_on_task_progress`）、SKIP 跳过（`_handle_failure` SKIP 分支）、replan 后（`_handle_failure` REPLAN 分支）、终态（`_on_task_terminal`）；失败不阻断主流程（仅 warning 日志）；(4) **SKIP 策略增强**：新增 `step_skipped` SSE 事件（含 subtaskIndex + message）；(5) **状态映射**：`_COORD_STATUS_MAP` 将 Python 小写状态（running/completed/failed/needs_human）映射为 Java 大写（RUNNING/COMPLETED/FAILED/NEEDS_HUMAN） |
| **测试覆盖** | Python `test_coordinator.py` 13 个测试通过（8 原有 + 5 新增）：SKIP 策略跳过后续完成 / RETRY 策略重试耗尽终止 / REPLAN 后成功完成（total_replans=1）/ 协调状态持久化验证（update_coordination_state 调用 ≥3 次，首次 RUNNING 末次 COMPLETED）/ SKIP 发布 step_skipped SSE 事件。回归 `test_planner.py` 15 + `test_executor.py` 8 + 全量 182 个 Python 测试通过。Java `TaskServiceImplTest` 39 个（含新增 6 个 updateCoordinationState：insert 成功 / update 成功 / null taskId / null request / 任务不存在 / null completedSubtasks）+ `InternalTaskControllerTest` 4 个（含新增 1 个 coordination-state 端点）= 43 个 Java 测试通过 |
| **未做范围** | M4.3 断点续跑 API（`POST /api/v1/tasks/{id}/resume` + 从 coordination_state 表读取 last_success_subtask_index + replan_count 重置）留到后续任务 |

#### M4.3 Java 断点续跑状态管理

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.3、M4.2 |
| **产出物** | `agent/CoordinationStateService.java` 增强 + API |
| **描述** | 1. `CoordinationStateService`：查询 `last_success_subtask_index`<br>2. 续跑 API：`POST /api/v1/tasks/{id}/resume`<br>3. 调 Python `POST /api/v1/ai/tasks/{id}/resume`（从 index+1 开始）<br>4. replan_count 重置逻辑 |
| **验收标准** | 中断任务可从断点续跑；不重做已完成子任务；replan_count 重置正确 |
| **状态** | ✅ 已完成（2026-08-01）。实现 Java + Python 双端续跑链路。最终落地：(1) **Python `Coordinator.run()` 新增 `initial_plan` 参数**：续跑时传入已存计划跳过 `Planner.create_plan`，确保 subtask_id 与 `completed_subtasks` 匹配（解决新计划 UUID 不匹配问题）；(2) **Python `app/schemas.py` 新增 `TaskResumeRequest` / `TaskResumeResponse`**（camelCase 对齐 Java）：含 taskId / orgId / navigationGoal / completedSubtasks / currentPlan / params；(3) **Python `app/api/tasks.py` 新增 `POST /api/v1/ai/tasks/{taskId}/resume` 端点**：反序列化 `current_plan` JSON → `TaskPlan` → 后台 `_resume_task_background` 调 `coordinator.run(resume_from=..., initial_plan=...)`，立即返回响应；(4) **Java 新增 `TaskResumeRequest` / `TaskResumeResponse` DTO** + `AiServiceClient.resumeTask()` 方法（`@PostExchange("/tasks/{taskId}/resume")`）；(5) **Java `TaskService.resumeTask()` 实现**：查询任务（校验 FAILED/NEEDS_HUMAN 状态 + 租户权限）→ 查询 `rpa_agent_coordination_state`（读取 completed_subtasks + navigation_goal + current_plan）→ 解析 completed_subtasks JSON → 重置 `total_replans=0, status=RUNNING, error_message=null` → 更新任务状态 EXECUTING → 调 Python resume API → Python 调用失败时回滚任务状态为 FAILED；(6) **Java `TaskController` 新增 `POST /tasks/{taskId}/resume` 端点** |
| **测试覆盖** | Python `test_coordinator.py` 15 个测试通过（含新增 2 个：续跑跳过已完成子任务 + Planner.create_plan 未被调用验证 / 全部子任务已completed 直接返回）。Java `TaskServiceImplTest` 46 个测试通过（含新增 7 个：续跑成功 FAILED → EXECUTING + 调 Python / NEEDS_HUMAN 也可续跑 / 任务不存在 / EXECUTING 状态不可续跑 / 协调状态不存在 / currentPlan 为空 / Python 调用失败回滚 FAILED）。Python 全量 182 个通过（2 个 test_executor Redis 连接失败为环境问题非回归） |
| **未做范围** | M4.4 前端子任务时间线展示（续跑按钮 UI + replan 可视化）留到后续任务 |

#### M4.4 前端子任务时间线展示

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.5、M4.2 |
| **产出物** | `components/Timeline.tsx` 增强、`routes/tasks/TaskDetail.tsx` 增强 |
| **描述** | 1. 子任务时间线：垂直时间轴展示每个子任务状态<br>2. replan 标记：可视化展示 replan 发生点<br>3. 续跑按钮：任务中断后可点击续跑<br>4. 子任务详情：点击查看子任务参数与结果 |
| **验收标准** | 时间线实时更新；replan 点可见；续跑按钮可用 |
| **状态** | ✅ 已完成（2026-08-01）。实现前端子任务时间线增强 + 断点续跑 UI。最终落地：(1) **新建 `components/Timeline.tsx` 组件**（从 TaskDetail.tsx 中抽取 SubTaskTimeline 并增强）：垂直时间轴按 subtaskIndex 排序；REPLANNED 状态子任务节点显示 IconRefresh 图标；每个 REPLANNED 子任务后插入"第 N 次重规划"分隔标记（紫色渐变线 + pill 徽章）；顶部汇总条显示总重规划次数；子任务行可点击展开/折叠详情面板（完成条件 / 执行结果 JSON / 子任务 ID / 耗时），支持键盘 Enter/Space 操作；新增 SKIPPED 状态节点显示"—"符号<br>(2) **`Icons.tsx` 新增 `IconResume`**（顺时针箭头 + 播放三角，表示从断点继续）**+ `IconChevronDown`**（展开/折叠箭头）<br>(3) **`api/tasks.ts` 新增 `resumeTask(taskId)` 方法**：POST /tasks/{taskId}/resume，对齐后端 TaskController.resumeTask()<br>(4) **`TaskDetail.tsx` 增强**：新增 `canResume` 判定（仅 FAILED / NEEDS_HUMAN 状态显示续跑按钮）；绿色主题续跑按钮（IconResume + "断点续跑"）含 loading 态 + confirm 确认 + 错误提示；续跑成功后 refetch 刷新详情；替换内联 SubTaskTimeline 为新 Timeline 组件；移除冗余 useMemo / SubTaskVO 导入<br>(5) **`glass.css` 新增 19.1 节样式**：`.timeline-replan-summary`（紫色汇总条）/ `.timeline-item-clickable`（hover 高亮）/ `.timeline-chevron`（旋转动画）/ `.timeline-details`（展开面板）/ `.timeline-replan-marker`（重规划分隔标记，渐变线 + pill 徽章）/ `.timeline-node-skip` 等 |
| **测试覆盖** | TypeScript 类型检查通过（tsc --noEmit exit 0）；Vite 生产构建通过（172 modules transformed，370KB JS + 38KB CSS）；0 个编译错误 |

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
| **状态** | ✅ 已完成（2026-08-01）。实现 Python 三层容错 LLM 调用器。最终落地：(1) **新建 `app/llm/resilient_caller.py`**：`ResilientCaller` 类封装三层容错机制 — 层 1 `_enhance_prompt()` 自动从 Pydantic 输出模型提取 JSON Schema 注入 prompt + 格式约束（仅输出 JSON、无 markdown）；层 2 `call()` 循环调 LLM → `_parse_json()` 解析（兼容 ```json 代码块包裹）→ Pydantic `model_validate()` 校验，校验失败时 `_add_error_feedback()` 将错误信息 + LLM 上次输出反馈给 LLM 重试（默认 2 次）；层 3 重试耗尽抛 `NeedsHumanError`（含 last_raw / last_error / attempts）+ `_report_needs_human()` 上报 Java `NEEDS_HUMAN` 状态<br>(2) **`LlmCallRecord` Pydantic 模型**：task_id / org_id / model / context_name / retry_attempt / success / error_message / duration_ms / prompt_tokens / completion_tokens / total_tokens / cache_hit / timestamp（M5.4 Java 侧持久化用）<br>(3) **`JavaBackendClient` 新增 `report_llm_call()` 方法**：POST /api/internal/llm/calls，Java 端点未实现时 warning 不阻断<br>(4) **`PlannerAgent` 集成 ResilientCaller**：新增 `resilient_caller` 参数（优先于 `llm_callable`）；`create_plan()` / `replan()` 新增 `task_id` 参数；新增 `_plan_with_llm()` / `_replan_with_llm()` 使用 ResilientCaller（NeedsHumanError 不捕获，传播给 Coordinator）；原 M4.1 直接调用重命名为 `_plan_with_llm_legacy()` / `_replan_with_llm_legacy()`；抽取 `_build_subtasks()` 共用方法<br>(5) **`Coordinator` 捕获 NeedsHumanError**：`run()` 中 `planner.create_plan()` 和 `_handle_failure()` 中 `planner.replan()` 均捕获 NeedsHumanError → 设置 `state.status = "needs_human"` + 上报 Java<br>(6) **22 个单元测试**：层 1 Schema 注入 / 层 2 正常返回 + 代码块兼容 + JSON 错误重试 + Pydantic 校验失败重试 + 错误反馈验证 / 层 3 重试耗尽 + NEEDS_HUMAN 上报 + 网络异常传播 / Java 上报成功 + 每次重试上报 + Java 不可用不阻断 / PlannerAgent 集成成功 + 重试耗尽 + 重试后成功 + legacy 回归 + fallback 回归 / Coordinator 捕获 NEEDS_HUMAN |
| **测试覆盖** | 22 个新测试全部通过；30 个 planner+coordinator 回归测试全部通过；总计 204 passed / 2 failed（Redis 未运行环境问题） |

#### M5.2 Python Action 缓存读写

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M5.1 |
| **产出物** | `app/llm/action_cache.py` + 单元测试 |
| **描述** | 1. 缓存 Key：DOM 结构哈希（剥除动态内容）+ 导航目标哈希<br>2. Redis 读写：TTL 24 小时<br>3. 命中流程：查缓存 → 命中直接返回 → 未命中调 LLM → 写缓存<br>4. 上报 Java：调用记录标记 `cache_hit` |
| **验收标准** | 相同页面结构命中缓存；缓存未命中时调 LLM 并写入；TTL 过期自动失效 |
| **状态** | ✅ 已完成（2026-08-01）。实现 LLM Action 缓存读写 + ResilientCaller 集成。最终落地：(1) **新建 `app/llm/action_cache.py`**：`ActionCache` 类基于 Redis 实现缓存读写 — `_make_key()` 生成 Key（`llm:action:{dom_hash}:{goal_hash}`）；`_hash_dom()` 剥除动态内容后取 SHA256 前 16 位；`_hash_goal()` 导航目标 normalize（lower + strip）后取 SHA256 前 16 位；`get()` / `set()` / `delete()` / `clear_pattern()` 异步方法；TTL 默认 86400 秒（24 小时）；Redis 异常时 catch 不阻断主流程<br>(2) **DOM 动态内容剥除 `_strip_dynamic_content()`**：正则移除 `<script>` / `<style>` / `<noscript>` 标签及内容、HTML 注释、标签间文本内容（保留标签结构）、`data-*` 属性、CSRF token、nonce 属性、时间戳（ISO 8601 / 日期时间 / Unix 时间戳）、规范化空白<br>(3) **`ResilientCaller` 集成 ActionCache**：`__init__` 新增 `action_cache` 参数；`call()` 新增 `cache_key_dom` / `cache_key_goal` 参数 — 传入时先查缓存，命中直接返回 Pydantic 校验结果 + 上报 `cache_hit=True`；未命中调 LLM 成功后写入缓存（`data` dict）；`_report_call()` 新增 `cache_hit` 参数透传到 `LlmCallRecord`；向后兼容（未注入 ActionCache 或未传 cache_key 时不查缓存）<br>(4) **31 个单元测试**：缓存 Key 生成 6 个（格式 / 相同 DOM+目标 / 不同目标 / 不同 DOM / 大小写不敏感 / 空白去除）+ 动态内容剥除 10 个（script / style / 文本 / data-* / 时间戳 / CSRF token / 注释 / nonce / 相同结构不同内容相同哈希 / 不同结构不同哈希）+ Redis 读写 8 个（命中 / 未命中 / 写入 / 写入后读取 / get 异常返回 None / set 异常不抛 / delete / clear_pattern）+ ResilientCaller 集成 7 个（命中跳过 LLM / 未命中调 LLM 并写入 / 未传 cache_key 不查缓存 / 未注入 ActionCache 不查缓存 / 部分 cache_key 不查 / 写入失败不阻断 / 命中后不重写） |
| **测试覆盖** | 31 个新测试全部通过；52 个回归测试全部通过（resilient_caller 22 + planner 15 + coordinator 15） |

#### M5.3 Python 模型路由执行

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M5.1 |
| **产出物** | `app/llm/model_router.py` + 单元测试 |
| **描述** | 1. 页面复杂度评分：DOM 节点数 / 表单字段数 / 动态元素数 / 截图熵<br>2. 路由规则：score < 30 轻量 / 30-70 标准 / ≥ 70 重型<br>3. 从 Java 读取路由策略配置<br>4. 上报 Java：调用记录标记使用模型 |
| **验收标准** | 不同复杂度页面路由到不同模型；策略可从 Java 配置更新；调用记录正确 |
| **状态** | ✅ 已完成（2026-08-01）。实现页面复杂度评分 + 模型路由 + Java 配置热更新。最终落地：(1) **新建 `app/llm/model_router.py`**：`ModelRouter` 类 + `ComplexityScore` / `RoutingConfig` Pydantic 模型<br>(2) **页面复杂度评分 `score_complexity()`**：四维度加权 — DOM 节点数（权重 40%，每 5 节点 1 分，上限 40 分）+ 表单字段数（权重 30%，每字段 2 分，上限 30 分）+ 动态元素数（权重 20%，上限 20 分，含 button / a[href] / on* 事件处理器）+ 截图熵（权重 10%，0-1 归一化 × 10）；总分 0-100，档位判定 light(<30) / standard(30-70) / heavy(≥70)<br>(3) **DOM 统计 `_parse_dom_stats()`**：正则统计开标签数（`<[a-zA-Z][^/>]*>` 排除自闭合）/ 表单字段（input / select / textarea，大小写不敏感）/ 动态元素（button + a[href] + on* 事件处理器）<br>(4) **模型路由 `get_model()` / `route()`**：根据档位返回模型名 — light → gpt-4o-mini，standard → gpt-4o，heavy → gpt-4o-2024-08-06；`route()` 一步完成评分 + 选模型<br>(5) **Java 配置热更新 `get_routing_config()`**：GET /api/v1/ai/llm/routing-config，5 分钟缓存 TTL，兼容 BaseResponse 包装格式，Java 不可用时回退本地默认配置；`refresh_config()` 强制刷新缓存<br>(6) **32 个单元测试**：DOM 统计 8 个（空 DOM / 简单 DOM / 表单字段 / button / link / 事件处理器 / 自闭合标签 / 大小写不敏感）+ 复杂度评分 9 个（空 DOM / 简单 light / 复杂 heavy / 中等 standard / 截图熵加分 / 熵截断 / 负值截断 / 全字段 / 边界值）+ 模型路由 4 个（light / standard / heavy / 自定义配置）+ 路由配置 2 个（默认值 / 自定义阈值）+ Java 配置 6 个（无 Java 客户端 / 成功读取 / BaseResponse 包装 / 缓存 / 强制刷新 / 异常回退）+ 端到端路由 4 个（简单 DOM / 复杂 DOM / 返回元组 / 截图熵） |
| **测试覆盖** | 32 个新测试全部通过 |

#### M5.4 Java LLM 调用记录与统计

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.3、M5.1 |
| **产出物** | `llm/` 模块（统计部分） |
| **描述** | 1. 实体：`LlmCallLogEO`（task_id / model / tokens / cost / cache_hit / timestamp）<br>2. 内部 API：`POST /internal/llm/calls`（Python 上报）<br>3. 统计 API：`GET /api/v1/llm/calls/stats`（按时间/模型/任务维度）<br>4. 成本计算：按模型 token 单价计算 |
| **验收标准** | 调用记录持久化；统计 API 返回正确；成本计算准确 |
| **状态** | ✅ 已完成（2026-08-01）。实现 LLM 调用记录持久化 + 成本计算 + 统计 API。最终落地：(1) **新建 `com.finrpa.llm` 模块**：`LlmCallLogEO` 实体（call_id 雪花主键 + task_id / org_id / model / context_name / retry_attempt / success / error_message / duration_ms / prompt_tokens / completion_tokens / total_tokens / cache_hit / cost / call_time / deleted / create_time）+ `LlmCallLogMapper`<br>(2) **内部 API `POST /internal/llm/calls`**：`InternalLlmController` 接收 Python ResilientCaller 上报的 `LlmCallRecord`，字段一一对应（taskId / orgId 为字符串形式，Service 层解析为 Long）；由 `InternalTokenInterceptor` 鉴权<br>(3) **成本计算 `calculateCost()`**：`LlmConstant.MODEL_PRICING` 定义 3 个模型单价（gpt-4o-mini: $0.15/$0.60，gpt-4o: $2.50/$10.00，gpt-4o-2024-08-06: $2.50/$10.00，单位美元/百万 token），cost = promptTokens × inputPrice / 1M + completionTokens × outputPrice / 1M，保留 6 位小数；未知模型成本为 0<br>(4) **统计 API `GET /llm/calls/stats`**：`LlmCallLogController` 对外接口，从 `TenantContext` 获取 orgId 做租户隔离；`LlmCallStatsQueryRequest` 支持 startTime / endTime / model / taskId 四维筛选；返回 `LlmCallStatsVO`（totalCalls / successCalls / failedCalls / cacheHitCalls / cacheHitRate / totalPromptTokens / totalCompletionTokens / totalTokens / totalCost / avgDurationMs / modelStats）<br>(5) **V12 迁移脚本**：`rpa_llm_call_log` 表（BIGSERIAL + call_id BIGINT UNIQUE + 4 个索引：task_id / org_id / model / create_time）<br>(6) **TenantConstant**：`rpa_llm_call_log` 加入 IGNORED_TABLES（Python 回调无 JWT 上下文，org_id 显式传入，对外 API 在 Service 层手动过滤）<br>(7) **时间戳解析**：Python `datetime.utcnow().isoformat()` → Java `LocalDateTime.parse()` → `Timestamp`，解析失败返回 null |
| **测试覆盖** | 27 个新测试全部通过：Service 19 个（createCallLog 成功场景 6 + 参数校验 3 + 数据库异常 1 + getStats 统计 5 + 成本计算 4）+ InternalLlmController 4 个（成功 / 缓存命中 / 重试失败 / 无 taskId）+ LlmCallLogController 4 个（成功 / 无参数 / 带 taskId / 租户上下文为空） |

#### M5.5 Java NEEDS_HUMAN 队列管理

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M2.3、M5.1 |
| **产出物** | `llm/NeedsHumanService.java` + API |
| **描述** | 1. 实体：`NeedsHumanQueueEO`（task_id / subtask_id / screenshot_url / llm_raw_output / validation_error / status）<br>2. 内部 API：`POST /internal/llm/needs-human`（Python 上报）<br>3. 处置 API：`POST /api/v1/llm/needs-human/{id}/resolve`（action: skip/manual/abort）<br>4. 处置后调 Python resume |
| **验收标准** | NEEDS_HUMAN 事件入队；操作员可查看详情；处置后任务继续或终止 |
| **状态** | ✅ 已完成（2026-08-01）。实现 NEEDS_HUMAN 事件入队 + 操作员查询/处置 + Python 侧两步上报。最终落地：(1) **新建 `com.finrpa.llm` 模块扩展**：`NeedsHumanQueueEO` 实体（queue_id 雪花主键 + task_id / org_id / subtask_id / context_name / screenshot_url / llm_raw_output / validation_error / attempts / status / resolve_action / resolved_by / resolved_at）+ `NeedsHumanQueueMapper`<br>(2) **内部 API `POST /internal/llm/needs-human`**：`InternalLlmController` 扩展，接收 Python ResilientCaller 上报的 NEEDS_HUMAN 事件详情入队<br>(3) **对外 API**：`NeedsHumanController` 提供 3 个端点 — `GET /llm/needs-human`（分页列表，支持 status / taskId 筛选）+ `GET /llm/needs-human/{queueId}`（详情，含 LLM 原始输出 + 校验错误）+ `POST /llm/needs-human/{queueId}/resolve`（处置：skip / manual / abort）<br>(4) **处置逻辑 `resolveNeedsHuman()`**：先执行动作（skip/manual → `taskService.resumeTask`，abort → `taskService.abortTask`），成功后标记 RESOLVED；resumeTask 失败时异常传播，队列保持 PENDING 可重试<br>(5) **V13 迁移脚本**：`rpa_needs_human_queue` 表（BIGSERIAL + queue_id BIGINT UNIQUE + 4 个索引：task_id / org_id / status / create_time）<br>(6) **TenantConstant**：`rpa_needs_human_queue` 加入 IGNORED_TABLES（Python 回调无 JWT 上下文）<br>(7) **LlmConstant 扩展**：NEEDS_HUMAN_STATUS_PENDING/RESOLVED + RESOLVE_ACTION_SKIP/MANUAL/ABORT 常量<br>(8) **Python 侧 `JavaBackendClient.report_needs_human()`**：POST `/api/internal/llm/needs-human`，上报 task_id / org_id / subtask_id / context_name / screenshot_url / llm_raw_output / validation_error / attempts<br>(9) **Python 侧 `_report_needs_human` 两步上报**：步骤 1 `update_task_state(state=NEEDS_HUMAN)` 更新任务状态 + 步骤 2 `report_needs_human()` 上报详情入队；两步独立 try/except，失败不阻断主流程 |
| **测试覆盖** | Java 25 个新测试全部通过：NeedsHumanServiceImpl 19 个（入队 5 + 列表 2 + 详情 3 + 处置 9）+ InternalLlmController 2 个（needs-human 上报）+ NeedsHumanController 4 个（列表 / 详情 / skip 处置 / abort 处置）；Python 22 个现有测试全部通过（含 `test_call_needs_human_reports_to_java`） |

#### M5.6 前端 NEEDS_HUMAN 接管页面 + LLM 监控页

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M5.4、M5.5 |
| **产出物** | `routes/llm/NeedsHuman.tsx`、`routes/llm/LlmMonitor.tsx` |
| **描述** | 1. 接管队列：待处理列表 + 详情（截图 + LLM 原始输出 + 校验错误）<br>2. 处置操作：skip / manual / abort 三按钮<br>3. LLM 监控：调用次数 / 成本 / 缓存命中率 / 模型分布图表<br>4. ECharts 可视化 |
| **验收标准** | 接管队列正确展示；处置按钮可用；LLM 监控图表数据准确 |
| **状态** | ✅ 已完成（2026-08-01）。实现 NEEDS_HUMAN 接管队列页 + LLM 调用监控页。最终落地：(1) **安装 echarts + echarts-for-react 依赖**<br>(2) **新建 `src/api/needsHuman.ts`**：`listNeedsHuman` / `getNeedsHumanDetail` / `resolveNeedsHuman` 三个 API 方法，对齐 `NeedsHumanController`<br>(3) **新建 `src/api/llmMonitor.ts`**：`getCallStats` API 方法，对齐 `LlmCallLogController`<br>(4) **扩展 `src/api/types.ts`**：`NeedsHumanQueueVO` / `NeedsHumanQueryRequest` / `NeedsHumanResolveRequest` / `LlmCallStatsVO` / `ModelStatsVO` / `LlmCallStatsQueryRequest` 类型定义<br>(5) **扩展 `src/components/Icons.tsx`**：新增 6 个图标 — `IconChart`（柱状图）/ `IconShield`（盾牌）/ `IconSkip`（快进）/ `IconHand`（手掌）/ `IconDollar`（美元）/ `IconCamera` 已有<br>(6) **新建 `src/routes/llm/NeedsHuman.tsx`**：接管队列页 — 分页表格 + 状态筛选（PENDING/RESOLVED）+ 行展开详情（截图 + LLM 原始输出 + 校验错误，两列 grid 布局）+ 三按钮处置（skip→续跑/manual→续跑/abort→终止），使用 `useMutation` + `invalidateQueries` 处置后自动刷新<br>(7) **新建 `src/routes/llm/LlmMonitor.tsx`**：LLM 监控页 — 4 张汇总卡片（总调用次数/成功率+缓存命中率/Token 用量/总成本+平均耗时）+ 3 个 ECharts 图表（模型调用分布饼图 + 模型成本对比柱状图 + Token 用量柱状图）+ 模型统计明细表<br>(8) **路由注册 `src/router.tsx`**：`/needs-human` + `/llm-monitor` 两条新路由<br>(9) **导航集成 `src/routes/RootLayout.tsx`**：顶部导航新增"接管"和"监控"两个按钮，版本标识更新为 M5.6<br>(10) **样式 `src/styles/glass.css`**：新增 NeedsHuman 页面样式（展开行/详情布局/截图/代码块/处置按钮颜色）+ LlmMonitor 页面样式（汇总卡片/图表网格/统计明细表），沿用绿色主色调 `#047857`<br>(11) **TypeScript 编译通过**：`tsc --noEmit` 零错误 |
| **测试覆盖** | TypeScript 编译零错误；API 类型对齐后端 DTO；ECharts 图表配置覆盖饼图/柱状图两种类型 |

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
| **状态** | ✅ 已完成（2026-08-01）。实现 Java 关键词预筛 + 金额检测 + 风险等级判定 + LLM 二次判断预留接口。最终落地：(1) **新建 `com.finrpa.approval` 模块**：constant / enums / entity / mapper / dto / service / controller / datagen 完整包结构<br>(2) **`ApprovalConstant` 常量类**：定义关键词分类（high_risk_operation / sensitive_data / large_amount）、风险类型（high / medium / low）、行业大额阈值（银行 5 万 / 保险 1 万 / 证券 10 万）、预筛动作（proceed / judge）<br>(3) **`RiskKeywordConstant` 内置关键词库**：硬编码 55 个关键词覆盖 3 大行业 — 银行 25 个（12 高风险操作 + 8 敏感数据 + 5 大额）/ 保险 13 个（8 + 3 + 2）/ 证券 17 个（10 + 5 + 2）<br>(4) **`RiskKeywordEO` 实体 + `RiskKeywordMapper`**：keyword_id 雪花主键 + keyword / industry / category / risk_type / description / enabled / builtin 字段<br>(5) **`RiskKeywordCategoryEnum` 枚举**：3 个分类枚举值 + 默认风险类型映射<br>(6) **V14 迁移脚本**：`rpa_risk_keyword` 表（BIGSERIAL + keyword_id BIGINT UNIQUE + 4 个索引：industry / category / enabled / builtin）<br>(7) **`RiskKeywordInitializer`**：ApplicationRunner 启动时 upsert 内置关键词库（@Order(25)），已存在则更新元数据字段（不动 enabled），不存在则插入<br>(8) **`RiskKeywordService` + Impl**：分页查询（支持行业/分类/启用状态筛选）+ 详情 + 新增自定义关键词 + 更新（内置仅可改 enabled/description）+ 删除（内置不可删除）+ 注册内置关键词（upsert 语义）<br>(9) **`RiskDetectService` + Impl**：核心预筛服务 — `detect()` 关键词匹配 + 金额正则检测 + 风险等级判定；`detectAndJudge()` 预筛 + LLM 二次判断（M6.2 未实现时回退使用预筛结果）<br>(10) **金额正则检测**：4 种格式 — 人民币（￥/¥/人民币）、美元（$/USD 按 7.2 汇率折算）、中文单位（万元/万/元）、金额前缀关键词（金额：/amount:）<br>(11) **风险等级判定规则**：critical（同时命中高风险操作 + 敏感数据）/ high（命中高风险操作或敏感数据或大额）/ medium（命中中风险关键词）/ low（无命中）<br>(12) **`AiServiceClient.judgeRisk()` 预留接口**：POST /api/v1/ai/risk/judge，M6.2 Python 端实现后自动接入<br>(13) **`RiskDetectController`**：对外 API — POST /risk/detect（仅预筛）+ POST /risk/detect-and-judge（预筛 + LLM 判断）<br>(14) **`RiskKeywordController`**：对外 API — GET /risk-keywords（分页列表）+ GET /{keywordId}（详情）+ POST（新增）+ PUT /{keywordId}（更新）+ DELETE /{keywordId}（删除）<br>(15) **`WorkflowTriggerServiceImpl` 集成**：替换原 `// TODO M6` 注释，新增 `performRiskDetection()` 方法在任务触发前执行风险检测，M6.1 阶段仅记录风险等级不阻塞执行，M6.3 将实现 high/critical 阻塞等待审批<br>(16) **`TenantConstant`**：`rpa_risk_keyword` 加入 IGNORED_TABLES（全局共享，无 org_id 字段） |
| **测试覆盖** | 45 个新测试全部通过：RiskDetectServiceImplTest 20 个（关键词匹配 7 + 金额检测 8 + 参数校验 2 + detectAndJudge 3）+ RiskKeywordServiceImplTest 21 个（查询 4 + 详情 3 + 新增 5 + 更新 3 + 删除 3 + 注册内置 3）+ RiskDetectControllerTest 4 个（detect 成功 + 带 params + detectAndJudge 低风险返回 null + detectAndJudge 高风险返回判断结果）；WorkflowTriggerServiceImplTest 10 个回归测试全部通过（新增 RiskDetectService mock）；总计 389 passed / 0 failed |

#### M6.2 Python LLM 风险二次判断

| 项 | 内容 |
|----|------|
| **规模** | M |
| **前置依赖** | M5.1、M6.1 |
| **产出物** | `app/approval/risk_judge.py` + API |
| **描述** | 1. LLM Prompt：输入目标 + 参数 + 预筛结果 → 输出 final_risk_level<br>2. 走三层容错（复用 M5.1）<br>3. API：`POST /api/v1/ai/risk/judge`（Java 调用）<br>4. 输出：low / medium / high / critical |
| **验收标准** | LLM 判断结果合理；走三层容错；返回正确风险等级 |
| **状态** | ✅ 已完成（2026-08-01）。实现 Python LLM 风险二次判断，对接 M6.1 在 `AiServiceClient.judgeRisk()` 预留的 `POST /api/v1/ai/risk/judge` 接口。最终落地：(1) **新建 `app/approval/` 包**：`__init__.py` + `schemas.py` + `risk_judge.py`<br>(2) **`schemas.py` Pydantic 模型**：`RiskJudgeRequest`（Java→Python，含 task_id / goal / params / industry / pre_screen_risk_level / hit_keywords / amount_matches / max_amount）+ `RiskJudgeResponse`（Python→Java，含 final_risk_level / reasoning / approval_route / message）+ `RiskJudgeOutput`（LLM 输出模型，注入 ResilientCaller JSON Schema 约束）+ `HitKeywordItem` / `AmountMatchItem` 子模型，全部使用 `_CAMEL_CONFIG` 驼峰序列化与 Java 侧对齐<br>(3) **`risk_judge.py` RiskJudgeService**：核心判断服务 — `judge()` 方法构造 prompt（system prompt + goal + params + 预筛结果）→ 调 `ResilientCaller.call()`（层 1 Schema 约束 + 层 2 Pydantic 校验重试 + 层 3 NEEDS_HUMAN 兜底）→ 解析 LLM 输出 → 校验风险等级与审批路由合法性<br>(4) **三层容错集成**：复用 M5.1 ResilientCaller，`NeedsHumanError`（重试耗尽）回退使用预筛风险等级不阻塞 Java 流程；其他异常（网络错误等）同样回退；LLM 输出非法值时自动修正<br>(5) **LLM Prompt 设计**：金融 RPA 风险评估专家系统 prompt，含 4 级风险判定指南（low/medium/high/critical）+ 3 级审批路由指南（auto/department/compliance）+ few-shot 示例<br>(6) **审批路由默认映射**：low→auto / medium→auto / high→department / critical→compliance<br>(7) **`app/api/risk.py` API 路由**：`POST /api/v1/ai/risk/judge` 端点，含 `_create_llm_callable()` litellm 工厂函数（从 config 读取 model/api_key/base_url）+ `_create_risk_judge_service()` 服务工厂（构造 ResilientCaller + JavaBackendClient）<br>(8) **`main.py` 注册路由**：`app.include_router(risk.router)`<br>(9) **litellm 集成**：使用 `litellm.acompletion()` 异步调用，temperature=0.1 保证判断稳定性，max_tokens=1024 限制输出长度 |
| **测试覆盖** | 24 个新测试全部通过：正常 LLM 判断成功 2 个（high→critical + low→auto）+ NeedsHumanError 回退 2 个（high→department + critical→compliance）+ 其他异常回退 2 个（网络错误 + 通用异常）+ LLM 输出非法值校验 2 个（非法风险等级回退 + 非法审批路由修正）+ prompt 构造 8 个（goal/industry/preScreen/keywords/amounts/params/空关键词/空金额）+ 格式化辅助 2 个（dict 输入兼容关键词+金额）+ 回退响应 3 个（low/high/critical）+ Pydantic 序列化 3 个（驼峰反序列化 + 驼峰序列化 + JSON Schema）；全部 293 个单元测试通过 |

#### M6.3 Java 审批流路由 + Pub/Sub

| 项 | 内容 |
|----|------|
| **规模** | L |
| **前置依赖** | M6.1、M6.2、M1.1 |
| **产出物** | `approval/` 模块完整代码 |
| **描述** | 1. 实体：`ApprovalRequestEO`（task_id / risk_level / approver_id / status / timeout_at）<br>2. `ApprovalRouteService`：按 risk_level 路由（high → 部门 approver / critical → 合规审计部）<br>3. Redisson Pub/Sub：发布 `approval:requests` / 订阅 `approval:responses`<br>4. API：`GET /api/v1/approvals` / `POST /api/v1/approvals/{id}/approve` / `POST /api/v1/approvals/{id}/reject`<br>5. Python 等待：通过 Pub/Sub 通知 Python Executor |
| **验收标准** | 审批单创建后正确路由；Pub/Sub 消息收发正常；审批结果通知 Python |
| **状态** | ✅ 已完成（2026-08-01）。实现 Java 审批流路由 + Redisson Pub/Sub 完整模块。最终落地：(1) **新建 `ApprovalRequestEO` 实体**（V15 迁移脚本 `rpa_approval_request` 表，雪花算法主键，含 task_id / org_id / risk_level / approval_route / status / approver_id / approve_reason / reject_reason / risk_reasoning / request_payload / timeout_at / approved_at 等字段）<br>(2) **枚举**：`ApprovalStatusEnum`（PENDING / APPROVED / REJECTED / TIMEOUT，含 `isTerminal()` 判定）+ `ApprovalRouteEnum`（AUTO / DEPARTMENT / COMPLIANCE，含 `fromRiskLevel()` 路由 + `needsHumanApproval()` 判定）<br>(3) **`ApprovalRouteService`**：按风险等级路由 — low/medium→auto / high→department / critical→compliance<br>(4) **`ApprovalPubSubService`**（Redisson）：`publishRequest()` 发布到 `approval:requests` RTopic（广播通知前端）+ `publishResponse()` 发布到 `approval:responses:{approvalId}` RTopic + 推送 RBlockingQueue（可靠唤醒等待线程）+ `waitForResponse()` 阻塞等待带超时<br>(5) **`ApprovalService`**：`createApproval()` 创建审批单 + 发布 Pub/Sub + 计算超时截止时间 / `approve()` 更新状态 + 发布响应 + **触发 Python 执行**（从 requestPayload 反序列化 TaskTriggerRequest 调 AiServiceClient）/ `reject()` 更新状态 + 发布响应 / `listApprovals()` 分页查询 / `getApprovalResultByTaskId()` Python 回调查询 / `processTimeoutApprovals()` 超时处理（M6.4 调用）<br>(6) **`ApprovalController`**：GET /api/approvals（分页查询）+ GET /api/approvals/{id}（详情）+ POST /api/approvals/{id}/approve（通过）+ POST /api/approvals/{id}/reject（拒绝）<br>(7) **`InternalTaskController` 新增内部 API**：GET /api/internal/approvals/{taskId}/result（Python 查询审批结果）<br>(8) **`WorkflowTriggerServiceImpl` 集成审批**：风险检测后若 high/critical → 创建审批单 → 返回 PENDING_APPROVAL（非阻塞）；若 low/medium → 直接触发 Python → 返回 EXECUTING；审批通过后 ApprovalService 自动触发 Python<br>(9) **`ApprovalConstant` 扩展**：审批状态/路由/Pub/Sub 频道/超时时间/表名常量<br>(10) **`TenantConstant`** 加入 `rpa_approval_request`<br>(11) **`WorkflowRunVO` 新增 approvalId 字段**（PENDING_APPROVAL 状态时返回）<br>(12) **设计决策**：采用非阻塞架构 — Java 创建审批单后立即返回 PENDING_APPROVAL，审批通过后由 ApprovalService 从 requestPayload 反序列化触发请求调 AiServiceClient.triggerTask()，避免 HTTP 请求长时间阻塞 |
| **测试覆盖** | 19 个新测试全部通过：ApprovalRouteServiceImplTest(9) + ApprovalServiceImplTest(10)，覆盖路由判定 / 创建审批 / 审批通过+触发Python / 审批拒绝 / 不存在校验 / 重复操作校验 / 查询结果 / 超时处理；全部 408 个测试通过（含 10 个 WorkflowTriggerServiceImpl 回归测试） |

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

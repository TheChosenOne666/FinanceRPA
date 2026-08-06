# 系统设置页（Settings）功能需求文档

> 金融 RPA 平台「设置」页缺失功能盘点与实施计划
>
> | 项 | 内容 |
> |----|------|
> | 文档版本 | v1.4 |
> | 创建日期 | 2026-08-04 |
> | 文档状态 | P0/P1/P2/P3 全部已实施 |
> | 关联页面 | `finance-frontend/src/routes/enterprise/Settings.tsx` |
> | 关联原型 | `prototypes/08-settings.html`（仅作 UI 参考，本文档不约束 UI） |

---

## 1. 背景与目标

### 1.1 背景

当前 `Settings.tsx` 仅按原型 `08-settings.html` 完成了 UI 对齐：7 项子导航中只有 3 个区块接入了数据（用户管理 / 角色管理 / 通知配置），且全部使用 Mock 数据；其余 4 项（部门管理 / 业务线 / Skill 管理 / 权限矩阵）仅展示「敬请期待」占位。

从金融 RPA 平台的业务视角看，设置页缺失了多项**后端已就绪但未接入**、以及**金融监管/RPA 运维必须但完全未实现**的重要功能。本文档盘点缺口并按价值/成本比制定实施计划。

### 1.2 目标

- 盘点设置页所有缺失的重要功能
- 按 P0 / P1 / P2 / P3 排定优先级
- 明确每项功能的后端接口、前端区块、数据模型对应关系
- 作为后续开发的唯一需求依据

### 1.3 范围

- 仅覆盖「设置」页内部功能
- 不涉及其他页面的功能调整
- 不约束 UI 布局（UI 仍以原型为基线，但允许按功能扩展）

---

## 2. 缺失功能盘点

### 2.1 后端已就绪但前端未接入（最易补齐）

| 功能 | 后端现状 | 设置页现状 | 缺口 |
|------|---------|-----------|------|
| Skill 元数据管理 | `SkillController` 已实现 `GET / POST / PUT` | 占位 | 前端接入 CRUD |
| 风险关键词库 | `RiskKeywordController` 已实现 `GET / POST / PUT / DELETE` | **无此项** | 前端新建区块 + 子导航项 |
| 部门列表 | `TenantController` `GET /tenant/departments` | 占位 | 前端只读列表 |
| 业务线列表 | `TenantController` `GET /tenant/business-lines` | 占位 | 前端只读列表 |
| 通知通道 Webhook 保存 | 后端仅有 `GET /channels` + `POST /test`，**缺保存接口** | Mock 持久化 | 后端补 `PUT /channels/{channel}`，前端切真实 |

### 2.2 金融 RPA 必须但完全缺失的功能

#### A. 安全合规类（金融监管刚需）

| 编号 | 功能 | 描述 | 后端依赖 |
|------|------|------|---------|
| SEC-1 | 密码策略配置 | 最小长度 / 复杂度 / 过期天数 / 历史密码不重复 | 新建 `sys_password_policy` 表 + Service |
| SEC-2 | 登录安全策略 | 失败锁定阈值 / IP 白名单 / 验证码开关 | 新建 `sys_login_policy` 表 |
| SEC-3 | 会话管理 | Token 有效期 / 并发登录限制 / 空闲超时 | 扩展 `JwtUtil` + Redis |
| SEC-4 | 审计策略配置 | 日志保留天数 / 敏感操作清单 / 审计员指定 | 新建 `sys_audit_policy` 表 |

#### B. 风险控制类（RPA 核心）

| 编号 | 功能 | 描述 | 后端依赖 |
|------|------|------|---------|
| RSK-1 | 审批超时阈值配置 | `high / critical` 超时分钟数在线可配（当前写死在 `ApprovalConstant` 30/60 分钟） | 新建 `rpa_approval_timeout_config` 表 + 改 `ApprovalRouteService` 读取 |
| RSK-2 | 风险等级阈值 | 金额阈值（>10万 → high）、关键词→风险等级映射规则 | 扩展 `RiskKeywordEO` + `RiskDetectService` |
| RSK-3 | 审批人映射 | 风险等级 × 业务线 → 审批人/审批部门（当前只按风险等级路由） | 新建 `rpa_approval_route_config` 表 + 改 `ApprovalRouteService` |

#### C. 系统集成类

| 编号 | 功能 | 描述 | 后端依赖 |
|------|------|------|---------|
| INT-1 | AI 服务配置 | `baseUrl / internalToken / timeout / retry` 在线编辑（当前只能改 yml） | 新建 `sys_ai_config` 表 + 改 `AiServiceProperties` 动态读取 |
| INT-2 | 通知通道 Webhook 持久化 | 见 2.1 | 补 `PUT /channels/{channel}` |
| INT-3 | MinIO / 对象存储配置 | 桶名 / 访问域名 / 保留期 | 新建 `sys_storage_config` 表 |

#### D. 用户与权限类

| 编号 | 功能 | 描述 | 后端依赖 |
|------|------|------|---------|
| USR-1 | 用户 CRUD | 新增 / 编辑 / 禁用 / 重置密码 / 分配角色 | 补 `UserController` + Service（Mapper 已有） |
| USR-2 | 角色 CRUD | 新增 / 编辑 / 删除 / 分配权限 | 补 `RoleController` + Service（Mapper 已有） |
| USR-3 | 权限矩阵 | 角色 × 资源 × 操作可视化勾选（三维度 RBAC） | 新建 `sys_permission` 表 + 矩阵查询接口 |

#### E. 运维监控类

| 编号 | 功能 | 描述 | 后端依赖 |
|------|------|------|---------|
| OPS-1 | 系统健康检查 | 一键检测 DB / Redis / Python AI / MinIO 连通性 | 新建 `GET /system/health` |
| OPS-2 | 定时任务配置 | 审批超时扫描 cron / 通知重试间隔（当前写死在常量） | 新建 `sys_scheduler_config` 表 |
| OPS-3 | 系统参数开关 | 全局维护模式 / 注册开关 / 文件上传限制 | 新建 `sys_global_config` 表 |

---

## 3. 实施计划

### 3.1 优先级划分

| 优先级 | 范围 | 理由 |
|--------|------|------|
| **P0** | Skill 管理 + 风险关键词库 + 部门/业务线只读 + 通知 Webhook 保存 | 后端已就绪（或仅需补 1 个接口），投入产出比最高 |
| **P1** | 审批超时配置 + 审批人映射 + 用户/角色 CRUD | 金融 RPA 核心风控配置，后端需新建表与改 Service |
| **P2** | 密码策略 + 登录安全 + 会话管理 + 系统健康检查 | 金融合规刚需，需新建后端模块 |
| **P3** | 权限矩阵可视化 + AI 服务在线配置 + MinIO 配置 + 定时任务配置 + 系统参数开关 | 工作量大，可后置 |

### 3.2 P0 详细任务

#### P0-1 风险关键词库管理

- **后端**：复用已有 `RiskKeywordController` / `RiskKeywordService`（无改动）
- **前端**：
  - 新增子导航项「风险关键词」（位于「通知配置」前）
  - 新建 `RiskKeywordsSection` 区块：
    - 分页表格：关键词 / 行业 / 分类 / 风险类型 / 启用状态 / 内置标识 / 操作
    - 筛选栏：行业（银行/保险/证券）+ 分类 + 启用状态 + 关键词搜索
    - 操作：新增（弹窗） / 编辑（弹窗） / 删除（确认） / 启用-禁用切换
    - 内置关键词仅可改 enabled/description（前端禁用其他字段）
- **Mock**：移除（直接调真实后端）

#### P0-2 Skill 元数据管理

- **后端**：复用已有 `SkillController` / `SkillRegistryService`（无改动）
- **前端**：
  - 替换当前「Skill 管理」占位区块为 `SkillsSection`
  - 分页表格：name / description / category / version / enabled / 操作
  - 筛选栏：分类 + 启用状态 + 关键词搜索
  - 操作：注册自定义 Skill（弹窗，调用 POST 前调 Python 校验） / 更新（弹窗） / 启用-禁用切换
  - 不允许修改 `name`（前端禁用该字段）

#### P0-3 部门 / 业务线只读列表

- **后端**：复用已有 `TenantController`（无改动）
- **前端**：
  - 「部门管理」占位区块替换为 `DepartmentsSection`：只读表格展示 `deptId / deptName / deptCode / parentId / sortOrder / status`
  - 「业务线」占位区块替换为 `BusinessLinesSection`：只读表格展示 `businessLineId / businessLineName / businessLineCode / description / sortOrder / status`
  - 表头加「只读」徽章，CRUD 待 P1 实现

#### P0-4 通知通道 Webhook 保存

- **后端**：
  - 新增 `PUT /api/notification/channels/{channel}` 接口
    - 请求体：`{ "webhookUrl": "..." , "secret": "..."（仅 dingtalk）, "enabled": true }`
    - 持久化到数据库（新建 `rpa_notification_channel_config` 表）
    - 更新 `NotificationProperties` 运行时配置（支持热生效）
  - 修改 `ChannelVO`：增加 `webhookUrl`（脱敏显示）+ `enabled` 字段
  - 单元测试：保存 / 查询 / 无效 channel 异常
- **前端**：
  - `NotificationSection` 通道列表每行新增「编辑 Webhook」按钮
  - 弹窗：Webhook URL 输入框（dingtalk 额外显示 secret）
  - 保存后调用 `PUT /channels/{channel}`，刷新通道列表
  - 移除 Mock 端的 `PUT /notification/config` 持久化逻辑

### 3.3 P1 详细任务

P1 范围：审批超时阈值配置（RSK-1）/ 审批人映射（RSK-3）/ 用户 CRUD（USR-1）/ 角色 CRUD（USR-2）。本节为已实施记录。

#### P1-1 审批超时阈值配置（RSK-1）

- **后端**：
  - 新建 `rpa_approval_timeout_config` 表（V22 迁移脚本）：`config_id / risk_level / timeout_minutes / description / enabled`
  - 新建 `ApprovalTimeoutConfigController`（`@RequestMapping("/approval-timeout")`）：
    - `GET /approval-timeout` 返回 high / critical 两条配置
    - `PUT /approval-timeout/{riskLevel}` 更新超时分钟数（1-1440）/ 描述 / 启停
  - `ApprovalService` 改为从配置表读取超时阈值（替代 `ApprovalConstant` 硬编码 30/60 分钟）
  - 加入 `TenantConstant.IGNORED_TABLES`（全局共享，无 org_id）
  - 单元测试：`ApprovalTimeoutConfigServiceImplTest` PASS
- **前端**：
  - `RiskControlSection` 上半区行内编辑：每条配置一行，含数字输入框（1-1440 分钟）/ 描述输入框 / 启停开关 / 保存按钮
  - 高风险默认 30 分钟，严重风险默认 60 分钟
- **Mock**：`MOCK_APPROVAL_TIMEOUT_CONFIGS` 两条数据 + `handleListApprovalTimeoutConfigs` / `handleUpdateApprovalTimeoutConfig`

#### P1-2 审批人映射配置（RSK-3）

- **后端**：
  - 新建 `rpa_approval_route_config` 表（V23 迁移脚本）：`config_id / org_id / risk_level / business_line_id / approver_user_id / department_id / description / enabled`
  - 新建 `ApprovalRouteConfigController`（`@RequestMapping("/approval-routes")`）：
    - `GET /approval-routes`（分页，按风险等级 / 业务线 / 启用状态筛选）
    - `POST /approval-routes` 新增（同 riskLevel + businessLineId 唯一性校验）
    - `PUT /approval-routes/{configId}` 更新（approverUserId / departmentId / description / enabled）
    - `DELETE /approval-routes/{configId}` 删除
  - `ApprovalService.createApproval` 改为先精确匹配 (riskLevel × businessLineId)，未命中回退默认路由（businessLineId = null）
  - 加入 `TenantConstant.IGNORED_TABLES`
  - 单元测试：`ApprovalRouteConfigServiceImplTest` PASS
- **前端**：
  - `RiskControlSection` 下半区：分页表格（风险等级 / 业务线 / 审批人 / 说明 / 状态 / 操作）
  - 筛选栏：风险等级 + 业务线 + 启用状态 + 重置
  - 新增 / 编辑弹窗：风险等级（high / critical）/ 业务线（默认路由 + 6 条业务线）/ 审批人（启用用户下拉）/ 部门 ID / 描述 / 状态
  - 编辑时风险等级 + 业务线禁用（业务键不可改）
- **Mock**：`MOCK_APPROVAL_ROUTE_CONFIGS` 三条数据 + 4 个 handler

#### P1-3 用户管理 CRUD（USR-1）

- **后端**：
  - `UserController` 实现 `@RequestMapping("/users")` 全套接口：
    - `GET /users`（分页，按 keyword / status 筛选）
    - `GET /users/{userId}`
    - `POST /users` 新增（用户名 + 真实姓名必填，密码可省略走默认 Finrpa@2026，BCrypt 加密）
    - `PUT /users` 编辑（用户名不可改）
    - `PUT /users/{userId}/status` 启停
    - `PUT /users/reset-password` 重置密码（默认 Finrpa@2026）
    - `DELETE /users/{userId}` 逻辑删除（同时清理用户-角色关联）
    - `POST /users/roles` 分配角色（三维度 RBAC，全量替换语义）
  - 单元测试：`UserServiceImplTest` PASS
- **前端**：
  - `UsersSection` 完整 CRUD：筛选栏（关键词 + 状态）/ 分页表格（用户名+真实姓名 / 部门 / 角色 badges / 状态 / 操作）
  - 操作：编辑 / 分配角色 / 重置密码 / 启用-禁用 / 删除
  - 新增 / 编辑弹窗：用户名（编辑时禁用）/ 真实姓名 / 密码（仅新增）/ 部门名称 / 邮箱 / 手机号 / 状态
  - 分配角色弹窗：全量角色多选，提交后全量替换
- **Mock**：`MOCK_USERS` + 8 个 handler

#### P1-4 角色管理 CRUD（USR-2）

- **后端**：
  - `RoleController` 实现 `@RequestMapping("/roles")` 全套接口：
    - `GET /roles`（分页，按 keyword / status 筛选）
    - `GET /roles/all` 查全部启用角色（分配角色下拉用）
    - `GET /roles/{roleId}`
    - `POST /roles` 新增（内置编码 super_admin / org_admin / operator / approver / viewer 受保护）
    - `PUT /roles` 编辑（roleCode 不可改；内置角色仅可改状态 / 描述）
    - `PUT /roles/{roleId}/status` 启停（super_admin / org_admin 禁止禁用）
    - `DELETE /roles/{roleId}` 逻辑删除（内置角色 + 有用户关联的角色禁止删除）
  - 单元测试：`RoleServiceImplTest` PASS
- **前端**：
  - `RolesSection` 完整 CRUD：筛选栏 / 分页表格（角色编码 / 名称 / 描述 / 跨组织读/批 / 状态 / 内置标识 / 操作）
  - 操作：编辑 / 启用-禁用 / 删除
  - 新增 / 编辑弹窗：角色编码（编辑时禁用）/ 角色名称 / 描述 / 跨组织读 / 跨组织批 / 状态
- **Mock**：`MOCK_ROLES` + 7 个 handler

### 3.4 P2 / P3

#### P2-1 密码策略配置（SEC-1，已实施）

- **后端**：`PasswordPolicyController` + `PasswordPolicyService(Impl)` + `PasswordPolicyEO` + V21 迁移脚本
  - 端点：`GET /password-policy` / `PUT /password-policy`
  - 字段：最小长度（8-128）/ 大写 / 小写 / 数字 / 特殊字符 / 特殊字符集合 / 过期天数（1-365）/ 历史密码检查数（0-20）/ 启用
  - 登录流程接入：`AuthServiceImpl.login` 调用 `passwordPolicyService.isPasswordExpired(user)` 校验密码过期
- **前端**：`SecurityPolicySection`（含 `PasswordPolicyForm` 子组件）+ `getPasswordPolicy` / `updatePasswordPolicy` API + Mock 端点
- **测试**：`PasswordPolicyServiceImplTest` PASS
- **状态**：已完成

#### P2-2 登录安全策略（SEC-2，已实施）

- **后端**：`LoginPolicyController` + `LoginPolicyService(Impl)` + `LoginPolicyEO` + V21 迁移脚本
  - 端点：`GET /login-policy` / `PUT /login-policy`
  - 字段：最大连续登录失败次数（1-20）/ 账号锁定时长（1-1440 分钟）/ IP 白名单 / IP 黑名单 / 会话空闲超时（1-1440 分钟）/ 允许多端并发登录 / 启用
  - 登录流程接入：
    - `AuthServiceImpl.login` 调用 `checkIpAllowed` + `checkAccountLocked` + 登录失败 `recordLoginFailure` + 登录成功 `resetLoginFailure`
    - Redis 数据结构：`finrpa:auth:fail:count:{username}` (RAtomicLong) + `finrpa:auth:lock:until:{username}` (RBucket<Long>)
- **前端**：`LoginPolicySection` + `getLoginPolicy` / `updateLoginPolicy` API + Mock 端点
- **测试**：`LoginPolicyServiceImplTest` PASS（13 用例）
- **状态**：已完成

#### P2-3 在线会话管理（SEC-3，已实施）

- **后端**：`SessionController` + `SessionService(Impl)` + `SessionVO` / `SessionQueryRequest`
  - 端点：
    - `GET /sessions` —— 分页查询在线会话列表（按 userId / username 筛选）
    - `DELETE /sessions/{sessionId}` —— 踢人下线（拉黑 token + 从用户会话集合移除）
  - JWT 过滤器接入：`JwtAuthenticationFilter.doFilterInternal` 每次请求调用 `sessionService.touchSession(token)` 校验会话状态
  - Redis 数据结构：
    - 黑名单：`finrpa:session:blacklist:{sessionId}` (String, TTL = token 剩余有效期)
    - 用户会话集合：`finrpa:session:user:{userId}` (RMap<sessionId, SessionInfo>, TTL = access token 最大有效期)
  - SessionId 生成：SHA-256(token) 前 32 位 hex，避免在 Redis 中存储原始 token
  - 并发登录控制：策略 `allowMultiLogin=0` 时新登录会踢掉同账号旧会话（加入黑名单）
  - 空闲超时：`touchSession` 校验 `lastAccessTime`，超阈值自动销毁会话并加黑名单
  - 权限：仅 `org_admin` / `super_admin` 可访问（`SessionController.checkAdminPermission`）
- **前端**：`SessionManagementSection` + `listSessions` / `killSession` API + Mock 端点（4 条 Mock 会话）
- **测试**：`SessionServiceImplTest` PASS（11 用例）
- **状态**：已完成

#### P2-4 系统健康检查（OPS-1，已实施）

- **后端**：`SystemHealthController` + `SystemHealthService(Impl)` + `SystemHealthVO` + `SystemHealthMapper`
  - 端点：`GET /system-health` —— 一键检测 DB / Redis / Python AI / MinIO 连通性
  - 检查方式：
    - DB：`SystemHealthMapper.ping()`（`SELECT 1`，PostgreSQL 连通性）
    - Redis：`redissonClient.getKeys().count()`（Redisson 轻量调用，兼容 Single / Cluster 模式）
    - Python AI：`aiServiceClient.getSkills()`（HTTP Interface GET `/api/v1/ai/skills`）
    - MinIO：`minioClient.listBuckets()`（列举 bucket）
  - 设计原则：每组件独立 try-catch，单组件 DOWN 不影响其他组件检查；不抛异常，聚合到 VO 返回
  - 整体状态：全 UP → UP；部分 DOWN → DEGRADED；全 DOWN → DOWN
  - 权限：仅 `org_admin` / `super_admin` 可访问
- **前端**：`SystemHealthSection` + `checkSystemHealth` API + Mock 端点
  - UI：一键检测按钮 + 整体状态徽章 + 检查时间 / 耗时 + 组件卡片网格（DB / Redis / Python AI / MinIO）
- **测试**：`SystemHealthServiceImplTest` PASS（11 用例，含 4 组件 UP/DOWN + 整体状态计算）
- **状态**：已完成

---

## 4. 验收标准

### 4.1 P0 验收

- [x] 后端 `PUT /notification/channels/{channel}` 接口通过单元测试（`NotificationChannelConfigServiceImplTest` PASS）
- [x] 前端「风险关键词」区块：列表 / 筛选 / 新增 / 编辑 / 删除 / 启用切换全部联调通过（Mock 端模拟）
- [x] 前端「Skill 管理」区块：列表 / 筛选 / 注册 / 更新 / 启用切换全部联调通过（Mock 端模拟）
- [x] 前端「部门管理」「业务线」只读列表渲染真实数据（Mock 端模拟，复用 TenantController 端点）
- [x] 前端「通知配置」保存 Webhook 后，刷新页面仍生效（Mock 端内存持久化 + 后端 DB 持久化已就绪）
- [x] `tsc --noEmit` 通过
- [x] 浏览器手动验证全部功能（4 项子导航 UI 全部 PASS）

### 4.2 P1 验收

- [x] 后端 `ApprovalTimeoutConfigServiceImplTest` PASS（V22 迁移脚本 + GET/PUT 接口）
- [x] 后端 `ApprovalRouteConfigServiceImplTest` PASS（V23 迁移脚本 + GET/POST/PUT/DELETE 接口 + 唯一性校验）
- [x] 后端 `UserServiceImplTest` PASS（CRUD + 启停 + 重置密码 + 分配角色 + 逻辑删除）
- [x] 后端 `RoleServiceImplTest` PASS（CRUD + 启停 + 内置角色保护 + 关联用户校验）
- [x] 前端「风控配置 → 审批超时阈值」：高风险 30 分钟 / 严重风险 60 分钟行内编辑 + 保存联调通过
- [x] 前端「风控配置 → 审批人映射」：列表 / 筛选 / 新增 / 编辑 / 启停 / 删除联调通过；编辑弹窗风险等级 + 业务线正确禁用
- [x] 前端「用户管理」：列表 / 筛选 / 新增 / 编辑 / 分配角色 / 重置密码 / 启停 / 删除联调通过
- [x] 前端「角色管理」：列表 / 筛选 / 新增 / 编辑 / 启停 / 删除联调通过；内置编码受保护
- [x] `tsc --noEmit` 通过
- [x] 内置浏览器验证 4 项功能 UI 全部 PASS（无 JS 错误）

### 4.3 P2 验收

- [x] 后端 `PasswordPolicyServiceImplTest` PASS（SEC-1 密码策略 CRUD + 登录接入密码过期校验）
- [x] 后端 `LoginPolicyServiceImplTest` PASS（SEC-2 登录策略 13 用例，含 IP 白/黑名单 + 账号锁定 + 失败计数）
- [x] 后端 `SessionServiceImplTest` PASS（SEC-3 在线会话 11 用例，含黑名单 + 并发登录 + 空闲超时 + 踢人下线）
- [x] 后端 `SystemHealthServiceImplTest` PASS（OPS-1 系统健康检查 11 用例，含 DB / Redis / Python AI / MinIO 四组件 UP/DOWN + 整体状态计算）
- [x] 前端「安全策略 → 密码策略」：表单加载 / 字段编辑 / 保存 / 重置全部联调通过
- [x] 前端「安全策略 → 登录安全策略」：表单加载 / 字段编辑 / 保存 / 重置全部联调通过
- [x] 前端「安全策略 → 在线会话」：列表 / 筛选 / 分页 / 踢人下线联调通过
- [x] 前端「安全策略 → 系统健康」：一键检测 / 整体状态徽章 / 组件卡片网格联调通过
- [x] `tsc --noEmit` 通过
- [x] 内置浏览器验证 4 项功能 UI 全部 PASS（无 JS 错误）

### 4.4 P3 验收

- [x] 后端 `SystemConfigController`（P3 INT-1/INT-2/INT-3 + OPS-2/OPS-3）已实现：`GET /system-config` / `PUT /system-config/{key}` / `POST /system-config/refresh`
- [x] 后端 `PermissionController`（P3 USR-3 权限矩阵可视化）已实现：`GET /permissions` / `GET /permissions/matrix` / `PUT /permissions/roles/{roleId}`
- [x] 后端 `MaintenanceInterceptor`（P3 OPS-3 全局维护模式开关）已接入 WebMvc 拦截链
- [x] 后端 `MinioStorageServiceImpl` 已接入 `upload.max_file_size_mb` 运行时校验（P3 OPS-3）
- [x] 后端 `ApprovalTimeoutScheduler` / `NotificationRetryScheduler` 已接入 `scheduler.*.enabled` 启停开关（P3 OPS-2）
- [x] 后端 `AiServiceProperties` / `MinioProperties` 已实现 `refreshFromConfig` 热刷新方法
- [x] 数据库 V26 迁移脚本初始化 24 条 `sys_config` 种子数据（AI / MinIO / 定时任务 / 维护 / 注册 / 上传 / RPA / Security / System 九组）
- [x] 数据库 V27 迁移脚本初始化 12 个权限点 + 8 个角色的权限关联（`sys_permission` + `sys_role_permission`）
- [x] 前端新增 4 个子导航项（AI 配置 / 存储配置 / 定时任务 / 系统参数），全部复用 `SystemConfigSection` 组件按 `config_key` 前缀过滤
- [x] 前端「权限矩阵」占位替换为 `PermissionMatrixSection` 组件：行=角色、列=权限点、按行保存（全量替换语义），内置角色标记「内置」徽章
- [x] 前端 `types.ts` 新增 `SystemConfigVO` / `SystemConfigUpdateRequest` / `PermissionVO` / `RolePermissionMatrixVO` / `RolePermissionSaveRequest` 5 个类型
- [x] 前端 `settings.ts` 新增 6 个 API 函数：`listSystemConfigs` / `updateSystemConfig` / `refreshSystemConfig` / `listAllPermissions` / `getPermissionMatrix` / `saveRolePermissions`
- [x] 前端 `glass.css` 新增 `settings-config-*` 与 `permission-matrix-*` 样式类
- [x] 修正 `.env.local` Mock 变量名：`VITE_ENABLE_MOCK` → `VITE_USE_MOCK`（vite.config.ts 实际读取的变量名）
- [x] `tsc --noEmit` 通过
- [x] 后端 API 联调验证：`GET /api/system-config` 返回 24 条、`GET /api/permissions` 返回 12 条、`GET /api/permissions/matrix` 返回 8 行

---

## 5. 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|---------|------|
| 2026-08-04 | v1.0 | 创建文档，盘点缺失功能并制定 P0-P3 计划 | 小楼 |
| 2026-08-04 | v1.1 | P0 全部实施完成（P0-1 风险关键词库 / P0-2 Skill 管理 / P0-3 部门业务线只读 / P0-4 通知通道 Webhook 保存），4.1 验收清单全部通过 | 小楼 |
| 2026-08-04 | v1.2 | P1 全部实施完成（P1-1 审批超时阈值 / P1-2 审批人映射 / P1-3 用户 CRUD / P1-4 角色 CRUD），4.2 验收清单全部通过 | 小楼 |
| 2026-08-04 | v1.3 | P2 全部实施完成（P2-1 密码策略 SEC-1 / P2-2 登录安全策略 SEC-2 / P2-3 在线会话管理 SEC-3 / P2-4 系统健康检查 OPS-1），4.3 验收清单全部通过 | 小楼 |
| 2026-08-06 | v1.4 | P3 全部实施完成（P3 INT-1/INT-2/INT-3 统一配置中心 + OPS-2 定时任务开关 + OPS-3 维护模式/上传限制 + USR-3 权限矩阵可视化），4.4 验收清单全部通过 | 小楼 |

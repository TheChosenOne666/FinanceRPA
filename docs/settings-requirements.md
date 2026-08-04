# 系统设置页（Settings）功能需求文档

> 金融 RPA 平台「设置」页缺失功能盘点与实施计划
>
> | 项 | 内容 |
> |----|------|
> | 文档版本 | v1.0 |
> | 创建日期 | 2026-08-04 |
> | 文档状态 | 评审通过，待实施 |
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

### 3.3 P1 详细任务（仅规划，不在本次实施）

待 P0 完成后由用户确认是否进入 P1。P1 范围：

- 审批超时阈值配置（RSK-1）
- 审批人映射（RSK-3）
- 用户 CRUD（USR-1）
- 角色 CRUD（USR-2）

### 3.4 P2 / P3

仅作记录，待 P1 完成后再规划。

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

---

## 5. 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|---------|------|
| 2026-08-04 | v1.0 | 创建文档，盘点缺失功能并制定 P0-P3 计划 | 小楼 |
| 2026-08-04 | v1.1 | P0 全部实施完成（P0-1 风险关键词库 / P0-2 Skill 管理 / P0-3 部门业务线只读 / P0-4 通知通道 Webhook 保存），4.1 验收清单全部通过 | 小楼 |

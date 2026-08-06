# FinanceRPA 需求分析文档

> 基于 Skyvern 二次开发的金融级 AI 浏览器自动化平台
> 复刻 finrpa-enterprise 开源项目的全部功能

| 项 | 内容 |
|----|------|
| 文档版本 | v1.1 |
| 创建日期 | 2026-07-25 |
| 文档状态 | 初稿，待评审 |
| 参考项目 | finrpa-enterprise (MIT, Xuelin Xu / Musenn) |
| 参考项目地址 | D:\lingou-projects\tempgithub\finrpa-enterprise |
| 本项目仓库 | https://github.com/TheChosenOne666/FinanceRPA.git |
| 部署形态 | 纯私有化部署 |

> **文档范围说明**：本文档仅覆盖市场分析与功能需求。技术选型、架构设计、数据模型详设、部署架构等不在本文档范围内，将在设计文档中单独阐述。

---

## 1. 项目背景

### 1.1 行业现状

金融机构（银行、保险、证券）后台存在大量重复性、规则明确的浏览器操作：下载银行流水、填报保单申请、提交合规报告、对公信贷资料核验、监管报表生成等。这些操作的痛点在于：

- **人工成本高**：单家大型银行后台日均 3 万条跨行流水核对需 12 人，月度关账周期长达 7.2 天。
- **传统 RPA 脆弱**：基于 XPath/CSS 选择器的脚本一旦页面改版即失效，维护成本高。
- **合规要求严**：所有自动化操作必须可追溯，高风险操作（转账、下单、核保）必须人工审批，数据不得出内网。
- **组织复杂**：多业务线、多部门、操作员/审批员职责分离，传统单租户工具无法满足。

### 1.2 Skyvern 的价值与不足

Skyvern 是基于 LLM + 计算机视觉的浏览器自动化开源框架，用视觉理解替代 XPath，从根本上解决页面改版失效问题。但 Skyvern 原版存在以下不足：

| 维度 | Skyvern 原版 | 金融企业真实需求 |
|------|--------------|------------------|
| 部署形态 | 单机单用户 | 多业务线、多部门、多角色并发 |
| 权限体系 | 无 | 三维度 RBAC + 跨部门审计 |
| 操作审计 | 无 | 全链路截图 + 元数据 + 脱敏 + 检索 |
| 风险控制 | 无 | 高危操作识别 + 分级审批 + 超时拒绝 |
| LLM 容错 | 单次调用 | 三层容错 + NEEDS_HUMAN 人工接管 |
| Agent 架构 | 单 Agent 循环 | Planner + Executor 双 Agent 协同 + 断点续跑 |
| 通用操作 | 自然语言驱动 | 可组合 Skill 库 + 工作流模板 |
| 成本控制 | 无 | Action 缓存 + 模型路由 |
| 通知能力 | 无 | 企业微信/钉钉实时通知 |

### 1.3 本项目定位

本项目对 finrpa-enterprise 开源项目进行 **完全功能对齐的复刻实现**，目标是在功能 100% 对齐原项目的前提下，覆盖金融企业对权限、合规、风控、审计、多 Agent 协同等企业级能力的需求。

---

## 2. 市场分析

### 2.1 市场规模

| 指标 | 数据 | 来源 |
|------|------|------|
| 2025 中国金融 RPA 市场规模 | 36.1 亿元（占整体 RPA 53.2%） | 博研咨询 |
| 2026 中国金融 RPA 市场预测 | 44.2 亿元，YoY +30.1% | 博研咨询 |
| 2025 全球金融 RPA 市场规模 | 1279.7 亿美元 | Global Growth Insights |
| 2026-2035 全球 CAGR | 31.04% | Global Growth Insights |
| 2024 大型金融机构 RPA 部署率 | 90% | Gartner |
| 平均 ROI | 380% | Gartner |
| 5 年渗透率变化 | 5% → 68% | IDC 2025 |

**银行业**是核心引擎，2025 年规模 12.4 亿元，占金融 RPA 34.3%；证券与保险合计 28.9%。城商行与农信系统采购金额占比 32.1%，已超过股份制银行，成为增量主战场。

### 2.2 竞品格局

#### 国际厂商

| 厂商 | 优势 | 中国市场短板 |
|------|------|--------------|
| **UiPath** | 全球市占率第一，生态最成熟，KYC/AML 加速器 | 中文支持弱，信创适配差，本土化依赖代理商 |
| **Automation Anywhere** | 云原生先行者，AI 集成灵活 | 无本地数据中心，数据合规风险高 |
| **Blue Prism (SS&C)** | 审计 ready，Tier-1 银行渗透深 | 价格高昂（$15K-$50K/数字员工/年） |
| **Power Automate** | Microsoft 生态绑定，成本低 | 文档密集型场景易触达能力天花板 |
| **Pega** | 流程编排 + 案例管理强 | RPA 非设计重心 |

#### 国内厂商

| 厂商 | 定位 | 标杆案例 |
|------|------|----------|
| **金智维** | 金融、央国企标杆，等保三级，全栈信创 | 工行、国泰海安证券（资金核查 1h→8min） |
| **弘玑 Cyclone** | 国内金融大型转型项目，云原生 | 国有大行财务共享中心流程节点 47,200+ |
| **来也科技** | 政务、中小企业，低代码 | 连续四年 Gartner 魔力象限 |
| **实在智能** | 第三代 AI Agent，多模态文档理解 | 银行非结构化票据识别准确率 94.6% |
| **第四范式** | AI 决策与数据智能平台 | 量化策略回测、风控模型迭代 |

### 2.3 国内市场核心痛点（本项目机会）

1. **信创适配**：国产 OS（麒麟/UOS）、国产数据库（达梦/人大金仓）兼容性。
2. **数据合规**：数据不出内网，需私有化部署 + 全链路审计。
3. **AI 增强 RPA**：传统 RPA 处理不了非结构化数据，需 LLM + 视觉理解。
4. **高风险操作审批**：转账、下单、核保必须人工二次确认。
5. **多业务线权限**：单租户工具无法满足多部门、多业务线、操作员/审批员职责分离。
6. **LLM 成本与可靠性**：纯 LLM 驱动成本高且不稳定，需缓存、模型路由、容错。

### 2.4 目标用户画像

| 画像 | 典型客户 | 核心诉求 |
|------|----------|----------|
| 主目标 | 城商行、农信系统、中型保险公司、区域券商 | 私有化部署、合规审计、低成本快速上线 |
| 次目标 | 大型金融机构科技创新子公司 | 可二次开发的底座、信创适配 |
| 三级目标 | 金融科技公司、监管科技公司 | 工作流模板可复用、Skill 库可扩展 |

### 2.5 差异化定位

| 维度 | 传统国际 RPA | 国内传统 RPA | 本项目 |
|------|--------------|--------------|--------|
| 技术底座 | XPath 脚本 | XPath + OCR | LLM + 视觉理解（Skyvern） |
| 部署形态 | SaaS/混合 | 私有化 | 纯私有化 |
| 权限模型 | 单维度角色 | 二维（部门+角色） | 三维（部门+业务线+角色） |
| 风险控制 | 规则引擎 | 规则 + 关键词 | 两阶段（关键词+LLM）+ 分级审批 |
| 审计能力 | 操作日志 | 操作日志 + 截图 | 全链路 + 截图 + 脱敏 + 检索 + CSV |
| LLM 容错 | 无 | 无 | 三层容错 + NEEDS_HUMAN |
| 成本控制 | 按机器人计费 | 按机器人计费 | Action 缓存 + 模型路由（降 60%） |

---

## 3. 项目目标与范围

### 3.1 业务目标

1. **功能完全对齐** finrpa-enterprise 原项目的全部 9 大企业模块 + Skyvern 核心 AI 能力。
2. **私有化可交付**：单机 Docker Compose 一键部署，生产环境 Nginx + 多容器编排。
3. **演示数据完备**：5 个演示账号覆盖 5 种角色 × 4 种业务场景。
4. **测试覆盖**：单元测试 + 集成测试覆盖率 ≥ 85%（对齐原项目 601 测试 / 85% 覆盖率）。

### 3.2 项目约束

| 约束类型 | 约束内容 |
|----------|----------|
| 复刻对象 | 完全对齐 finrpa-enterprise (D:\lingou-projects\tempgithub\finrpa-enterprise) 的全部功能 |
| 部署形态 | 纯私有化部署，数据不出内网 |
| 不在范围 | 真实银行/保险/证券系统对接、信创认证、多节点集群、移动端 |

> 技术栈选型（后端语言、AI 实现、ORM、JDK 版本、服务间通信协议、数据库迁移策略等）属于设计决策，记录在设计文档中，不在本需求文档展开。

### 3.3 项目范围

#### In Scope（本项目交付）

- 9 大企业模块：auth / tenant / approval / audit / dashboard / llm / agent / skills / workflows / notification
- Skyvern 核心 AI 能力：浏览器自动化、视觉理解、LLM 调度
- 前端：复刻全部企业页面与毛玻璃 UI + 国际化
- 基础设施：PostgreSQL + Redis + MinIO + Nginx + Docker Compose
- 6 个金融场景工作流模板 + 7 个 Skill
- 演示数据脚本 + 健康检查脚本

#### Out of Scope（本项目不交付）

- 真实银行/保险/证券系统对接（仅模拟站点验证）
- 信创认证（等保三级、信通院认证）—— 留作商业化阶段
- 多节点集群部署（仅单节点 Docker Compose）
- 真实企业微信/钉钉账号配置（仅 Webhook URL 占位）
- 移动端 App
- 工作流可视化拖拽编辑器增强（保留原项目能力即可）

### 3.4 里程碑分期

| 阶段 | 内容 | 对应原项目 Day |
|------|------|----------------|
| Phase 1 | 项目脚手架 + 数据库设计 + 三维度权限 + JWT 认证 | Day 1-3 |
| Phase 2 | 租户隔离 + 风险检测 + 审批引擎 + 通知 | Day 4-7 |
| Phase 3 | 审计 + LLM 容错 + NEEDS_HUMAN + 工作流模板 + Skill 库 | Day 8-10 |
| Phase 4 | 运营大屏 API + 双 Agent 协同 + Action 缓存 + 模型路由 | Day 11-13 |
| Phase 5 | UI 改造 + 容器化 + 生产就绪 + 企业认证 + i18n + 演示数据 | Day 14-16 |

---

## 4. 干系人分析

| 干系人 | 关注点 | 期望产出 |
|--------|--------|----------|
| 业务操作员 | 任务能跑通、能看执行过程 | 任务列表 + 实时浏览器流 |
| 业务审批员 | 高风险操作能拦截、能批/拒 | 审批中心 + 通知 |
| 部门管理员 | 看本部门任务、管本部门用户 | 权限管理 + 部门视图 |
| 风险管理部 | 跨部门只读、风险趋势 | 运营大屏 + 跨组织只读 |
| 合规审计部 | 全组织审批权、可查任何记录 | 审计日志 + 全组织审批 |
| 平台管理员 | 全局可管、配置 LLM/通知 | 设置页 + LLM 监控 |
| 运维 | 部署简单、可观测 | Docker Compose + 健康检查 + 日志 |

---

## 5. 业务场景与用例

### 5.1 银行场景

| 场景 | 描述 | 风险等级 | 涉及 Skill |
|------|------|----------|------------|
| 银行流水下载 | 登录企业网银，按日期下载对公账户流水 PDF | medium | LoginSkill + FileDownloadSkill |
| 跨行转账核对 | 自动比对跨行流水与内部账务系统记录 | high | TableExtractSkill + PaginationSkill |
| 大额转账审批 | 检测转账金额 ≥ 100万，触发审批 | critical | FormFillSkill + 审批引擎 |
| 对公贷款放款 | 填写放款申请表单 + 提交核验 | critical | FormFillSkill + SearchAndSelectSkill |
| 账户冻结/解冻 | 修改账户状态 | high | FormFillSkill |
| 月度监管报表 | 自动汇总 + 填报 EAST 报表 | high | TableExtractSkill + FormFillSkill |

### 5.2 保险场景

| 场景 | 描述 | 风险等级 | 涉及 Skill |
|------|------|----------|------------|
| 保单申请填写 | 客户信息录入 + 保额计算 + 提交 | high | FormFillSkill |
| 理赔审核 | 上传理赔材料 + 核对 + 提交审核 | high | FileDownloadSkill + FormFillSkill |
| 大额理赔支付 | 赔付金额 ≥ 阈值，触发审批 | critical | FormFillSkill + 审批引擎 |
| 保单退保 | 退保申请 + 受益人变更 | critical | FormFillSkill |
| 批单签发 | 批单信息录入 + 出单 | medium | FormFillSkill + SessionKeepAliveSkill |

### 5.3 证券场景

| 场景 | 描述 | 风险等级 | 涉及 Skill |
|------|------|----------|------------|
| 委托下单 | 委托单录入 + 风险揭示确认 | high | FormFillSkill |
| 大宗交易 | 大宗交易申报 + 双边确认 | critical | FormFillSkill + 审批引擎 |
| 融资融券担保品划转 | 担保品划转 + 余额核对 | critical | FormFillSkill + TableExtractSkill |
| 银证转账 | 资金划拨 + 双边对账 | high | FormFillSkill + TableExtractSkill |
| 强制平仓 | 触发条件识别 + 平仓执行 | critical | FormFillSkill + 审批引擎 |
| 客户权限变更 | 客户权限调整 + 留痕 | high | FormFillSkill |

### 5.4 核心用例

#### UC-01 操作员执行高风险任务

1. 操作员登录，选择「银行流水下载」工作流模板
2. 填入参数（账户号、日期范围）
3. 系统识别到「下载」+ 涉及账户操作 → 风险等级 high
4. 启动 Planner + Executor
5. Executor 执行 LoginSkill → FormFillSkill → FileDownloadSkill
6. 全程截图 + 元数据写入审计日志
7. 任务完成，操作员查看结果与审计回放

#### UC-02 审批员处理 critical 级请求

1. 操作员触发「大额转账」（金额 500 万）
2. 关键词预筛命中「大额转账」+「critical」+ 金额 ≥ 100万
3. LLM 二次确认风险等级为 critical
4. 创建审批请求，路由到合规审计部
5. Redis Pub/Sub 推送 + 企业微信通知合规审批员
6. 合规审批员在审批中心查看截图、LLM 推理过程、参数
7. 批准 → 任务继续执行；拒绝 → 任务终止
8. 超时 60 分钟未审批 → 自动拒绝 + 告警

#### UC-03 风险管理部跨部门审计

1. 风险管理部用户登录（持有 cross_org_read 权限）
2. 进入审计日志页面，按业务线筛选
3. 查看对公信贷部 + 个人金融部 + 资产管理部全部任务
4. 点击某条记录查看截图（预签名 URL 临时访问）
5. 导出 CSV 用于内审报告

#### UC-04 LLM 失败转 NEEDS_HUMAN

1. Executor 执行 FormFillSkill 时 LLM 返回格式异常
2. Prompt 强制格式约束 → 失败
3. Pydantic 校验重试（默认 2 次） → 仍失败
4. 任务状态转为 NEEDS_HUMAN
5. 操作员在「人工接管队列」看到任务
6. 查看卡住步骤的截图 + LLM 原始输出
7. 选择「跳过」/「手动执行」/「终止」

#### UC-05 双 Agent 断点续跑

1. 银行日终批处理包含 10 个子任务
2. Planner 拆解后 Executor 逐步执行
3. 第 7 个子任务因网络中断失败
4. Coordinator 触发 replan，Planner 重新规划剩余 4 个子任务
5. 重新规划次数已达上限（3 次） → 转 NEEDS_HUMAN
6. 操作员介入处理 → 任务从第 7 个子任务续跑（不需重做 1-6）

---

## 6. 功能性需求

### 6.1 模块总览

| 模块 | 职责 |
|------|------|
| 认证授权 (auth) | JWT 登录、三维度 RBAC、权限解析 |
| 租户隔离 (tenant) | 多租户上下文、查询过滤、中间件 |
| 审批引擎 (approval) | 两阶段风险检测、审批流、Redis Pub/Sub |
| 合规审计 (audit) | 全链路日志、脱敏、MinIO 存储、CSV 导出 |
| 运营大屏 (dashboard) | 统计 API、Redis 缓存、趋势分析 |
| LLM 韧性 (llm) | 三层容错、Action 缓存、模型路由、NEEDS_HUMAN |
| 多 Agent (agent) | Planner + Executor + Coordinator、断点续跑 |
| Skill 库 (skills) | 7 个可组合 Skill |
| 工作流 (workflows) | 模板管理、参数加密、Skill 编排 |
| 通知 (notification) | 企业微信/钉钉 Webhook |
| Skyvern 核心 | 浏览器自动化、视觉理解、LLM 调度 |

### 6.2 认证授权模块（auth）

#### 6.2.1 角色与权限

**5 种角色**：
- `super_admin`：平台管理员，全组织 APPROVE 权限
- `org_admin`：组织管理员，本组织 APPROVE 权限
- `operator`：操作员，OPERATE 权限（可执行任务）
- `approver`：审批员，APPROVE 权限（可审批任务）
- `viewer`：查看者，READ 权限

**约束**：同一用户在同一部门内 operator 与 approver 角色互斥（数据库 CHECK 约束强制）

**4 种权限等级**：
- `none`：无权限
- `read`：可查看
- `operate`：可执行
- `approve`：可审批（含 operate）

**2 种跨组织特殊权限**：
- `cross_org_read`：风险管理部跨组织只读
- `cross_org_approve`：合规审计部跨组织审批

#### 6.2.2 权限解析算法

输入：用户上下文 + 资源所属组织、部门、业务线
输出：有效权限等级

逻辑（按优先级，最高权限生效）：
1. 跨组织 → 默认 NONE（除非有特殊权限）
2. 遍历用户的所有 (部门, 角色) 关联：
   - 角色是 super_admin/org_admin → 直接返回 APPROVE
   - 部门匹配 → 取该角色对应权限
   - 业务线匹配 → 取该角色对应权限（跨部门访问）
3. 检查跨组织特殊权限：
   - cross_org_approve → APPROVE
   - cross_org_read → READ
4. 返回累计的最高权限

#### 6.2.3 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/api/v1/auth/login` | 登录，返回 JWT + Refresh Token |
| POST | `/api/v1/auth/refresh` | 刷新 Token |
| POST | `/api/v1/auth/logout` | 登出，吊销 Token |
| GET | `/api/v1/auth/me` | 获取当前用户信息（含部门、业务线、角色、特殊权限） |
| GET | `/api/v1/auth/organizations` | 获取组织列表（登录页下拉） |
| GET | `/api/v1/auth/permissions/check` | 校验当前用户对某资源的权限 |

#### 6.2.4 与 Skyvern 原生认证桥接

- 企业 JWT 与 Skyvern 原生 API Key 通过桥接函数转换
- 实现企业登录后无感调用 Skyvern 能力

### 6.3 租户隔离模块（tenant）

#### 6.3.1 多租户上下文

- 每个请求解析 JWT → 提取 organization_id
- 注入租户上下文（请求级隔离）
- 请求结束清理

#### 6.3.2 查询过滤

- 所有企业表 SQL 自动追加 `WHERE organization_id = ?`
- 防止跨组织数据泄露

#### 6.3.3 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/v1/tenant/info` | 当前租户信息 |
| GET | `/api/v1/tenant/departments` | 当前组织部门树 |
| GET | `/api/v1/tenant/business-lines` | 当前组织业务线列表 |

### 6.4 审批引擎模块（approval）

#### 6.4.1 两阶段风险检测

**阶段 1：关键词预筛**

关键词库覆盖 3 大行业：
- 银行（banking）：转账、汇款、销户、放款、核销、授信额度调整等
- 保险（insurance）：理赔、承保、退保、受益人变更、保额调整等
- 证券（securities）：委托下单、大宗交易、融资买入、融券卖出、强制平仓等

每个关键词附带：
- `risk_level`：medium / high / critical
- `category`：fund_transfer / account_ops / credit_ops / approval / claims / underwriting / policy_change / trading / margin / fund_ops
- `description`：中英文说明

金额检测正则识别 `¥/$/€/£/万/亿/million/billion`，超过阈值（100 万 / 1 million）视为高风险。

**阶段 2：LLM 精准判断**

当阶段 1 命中关键词或金额超阈值时，调用 LLM 二次判断：
- 输入：任务目标 + 参数 + 阶段 1 命中结果
- 输出：最终风险等级 + 判断依据
- LLM 调用走 LLM 韧性模块（三层容错）

#### 6.4.2 分级审批路由

| 风险等级 | 路由目标 | 超时时间 | 超时处理 |
|----------|----------|----------|----------|
| low | 无需审批 | - | - |
| medium | 自动通过 | - | - |
| high | 对应部门的 approver | 30 分钟 | 自动拒绝 + 告警 |
| critical | 合规审计部的 approver | 60 分钟 | 自动拒绝 + 告警 |

#### 6.4.3 审批流通信

- 基于 Redis Pub/Sub（channel：`approval:requests`、`approval:responses`）
- 不阻塞主任务线程，Executor 异步等待审批结果
- 审批状态持久化到 PostgreSQL

#### 6.4.4 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/v1/approvals` | 待审批列表（按当前用户角色过滤） |
| GET | `/api/v1/approvals/{id}` | 审批详情（含截图、LLM 推理） |
| POST | `/api/v1/approvals/{id}/approve` | 批准 |
| POST | `/api/v1/approvals/{id}/reject` | 拒绝（含拒绝理由） |
| GET | `/api/v1/approvals/history` | 历史审批记录 |

### 6.5 合规审计模块（audit）

#### 6.5.1 审计日志结构

每条审计日志包含：
- 基本信息：log_id, task_id, organization_id, department_id, business_line_id, user_id
- 操作信息：action_type, target_element, action_params（脱敏后）, execution_result
- 风险信息：risk_level, approval_id（关联审批）
- 时间信息：started_at, completed_at, duration_ms
- 截图：before_screenshot_url, after_screenshot_url（MinIO 预签名 URL）
- LLM 信息：llm_model, llm_tokens_used, llm_cost

#### 6.5.2 脱敏规则

- 银行卡号：保留前 4 后 4，中间用 `*` 替换
- 身份证号：保留前 6 后 4
- 密码：完全替换为 `***`
- 手机号：保留前 3 后 4
- 邮箱：保留首字符 + `***` + 域名

#### 6.5.3 MinIO 存储策略

- Bucket：`finrpa-audit-{organization_id}`
- 路径：`{date}/{task_id}/{step_index}_{before|after}.png`
- 访问：预签名 URL，有效期 1 小时
- 保留期：默认 90 天，可配置

#### 6.5.4 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/v1/audit/logs` | 多维度检索（时间、组织、部门、业务线、用户、风险等级、操作类型） |
| GET | `/api/v1/audit/logs/{id}` | 单条详情 |
| GET | `/api/v1/audit/logs/{id}/screenshots` | 截图预签名 URL |
| GET | `/api/v1/audit/logs/export` | 导出 CSV |
| GET | `/api/v1/audit/stats` | 审计统计（按维度聚合） |

### 6.6 运营大屏模块（dashboard）

#### 6.6.1 统计指标

- 任务总数 / 成功数 / 失败数 / 进行中数
- 成功率（按天/周/月）
- 平均执行时长
- LLM 调用次数 + 总成本
- Action 缓存命中率
- 人工接管队列长度
- 各业务线任务分布 + 成功率对比
- 错误类型分布（Top 10）
- 风险等级分布
- 审批平均响应时长

#### 6.6.2 缓存策略

- Redis Hash 缓存统计结果
- Key：`dashboard:{organization_id}:{metric}:{date}`
- TTL：5 分钟（实时指标）/ 1 小时（历史趋势）
- 缓存失效：任务完成时主动刷新

#### 6.6.3 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/v1/dashboard/overview` | 概览（KPI 卡片） |
| GET | `/api/v1/dashboard/trends` | 趋势图（成功率、成本、任务量） |
| GET | `/api/v1/dashboard/business-lines` | 业务线对比 |
| GET | `/api/v1/dashboard/errors` | 错误分布 |
| GET | `/api/v1/dashboard/costs` | LLM 成本分析 |
| GET | `/api/v1/dashboard/approvals` | 审批响应时长分析 |
| GET | `/api/v1/dashboard/export` | 导出 CSV |

### 6.7 LLM 韧性模块（llm）

#### 6.7.1 三层容错

1. **Prompt 强制格式约束**：System Prompt 明确要求返回 JSON Schema，附 few-shot 示例
2. **校验重试**：LLM 返回结果用结构化模型校验，失败则将错误反馈给 LLM 重试（默认 2 次）
3. **NEEDS_HUMAN 转换**：重试超限后任务状态转为 `needs_human`，进入人工接管队列

#### 6.7.2 Action 缓存

- Key：`hash(DOM 结构哈希（剥除动态内容）) + hash(导航目标)`
- Value：LLM 决策结果（点击、输入、提取等动作）
- TTL：24 小时
- 命中条件：相同页面结构 + 相同导航目标
- 存储后端：Redis

#### 6.7.3 模型路由

页面复杂度评分维度：
- DOM 节点数量
- 表单字段数量
- 动态元素数量
- 截图熵（视觉复杂度）

路由规则：
- 简单页面 → 轻量模型（如 GPT-4o-mini / Claude Haiku）
- 标准页面 → 标准模型（如 GPT-4o / Claude Sonnet）
- 复杂页面 → 重型模型（如 Claude Opus / GPT-4 Turbo）

#### 6.7.4 NEEDS_HUMAN 人工接管

- 状态：`needs_human`
- 处置选项：跳过 / 手动执行 / 终止
- 展示信息：卡住步骤截图、LLM 原始输出、校验错误、上下文参数
- 处置后任务状态流转

#### 6.7.5 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/v1/llm/cache/stats` | 缓存命中率统计 |
| DELETE | `/api/v1/llm/cache/task/{task_id}` | 清除指定任务缓存 |
| DELETE | `/api/v1/llm/cache/expired` | 清除过期缓存 |
| POST | `/api/v1/llm/cache/reset` | 重置缓存统计 |
| GET | `/api/v1/llm/needs-human` | 人工接管队列 |
| POST | `/api/v1/llm/needs-human/{id}/resolve` | 处置（skip/manual/abort） |

### 6.8 多 Agent 模块（agent）

#### 6.8.1 数据结构

- **SubTask**：子任务（目标、完成条件、最大重试次数、失败策略、状态、重试计数、结果）
- **TaskPlan**：任务计划（导航目标、子任务列表、版本号）
- **ExecutionResult**：执行结果（子任务 ID、是否成功、输出、错误、耗时）
- **CoordinationState**：协调状态（任务 ID、计划、当前子任务索引、重规划次数、状态）

#### 6.8.2 Planner 职责

- 输入：导航目标（自然语言）+ 上下文（当前页面 URL、DOM 摘要）
- 输出：TaskPlan（子任务列表）
- 调用 LLM 拆解，每个子任务包含目标、完成条件、最大重试次数、失败策略
- 支持重新规划（replan）：根据失败的子任务和错误信息，重新生成剩余子任务列表

#### 6.8.3 Executor 职责

- 逐步执行 TaskPlan 中的子任务
- 调用 Skill 库或 Skyvern 原生能力
- 每步返回 ExecutionResult
- 触发审计回调（截图 + 元数据）

#### 6.8.4 Coordinator 职责

- 编排 Planner 与 Executor 的通信
- 维护 CoordinationState
- 处理失败策略：
  - `retry`：瞬态错误，重试（受 max_retries 限制）
  - `skip`：非关键步骤，跳过继续
  - `abort`：关键前置步骤失败，终止整个任务
  - `replan`：路径阻塞，请求 Planner 重新规划
- 重规划次数超上限（默认 3 次）→ 转 NEEDS_HUMAN
- 子任务状态持久化到 PostgreSQL，支持断点续跑

### 6.9 Skill 库模块（skills）

#### 6.9.1 Skill 基类接口

每个 Skill 必须实现：
- 元数据：名称、描述、参数模型
- 执行方法：异步执行，接收参数和上下文，返回执行结果
- 失败策略：根据错误类型返回处理策略

#### 6.9.2 7 个 Skill

| Skill | 类别 | 职责 |
|-------|------|------|
| LoginSkill | 认证类 | 登录目标系统（用户名/密码 + 2FA + TOTP） |
| SessionKeepAliveSkill | 认证类 | 维持会话活跃，防过期 |
| FormFillSkill | 交互类 | 表单字段填写 + 提交 |
| SearchAndSelectSkill | 交互类 | 搜索 + 下拉选择 |
| PaginationSkill | 交互类 | 翻页处理（下一页/页码跳转/无限滚动） |
| TableExtractSkill | 提取类 | 表格数据提取为结构化 JSON |
| FileDownloadSkill | 提取类 | 文件下载 + 校验 + 上传到 MinIO |

#### 6.9.3 Skill Pipeline 执行器

- 按序执行 Skill 列表
- 每步独立处理错误策略
- 参数映射支持：
  - 引用模式：`{{workflow.params.account_id}}` 从工作流参数取值
  - 字面量模式：直接预设值
- 审计回调自动记录脱敏后的参数和执行结果

### 6.10 工作流模块（workflows）

#### 6.10.1 工作流模板结构

工作流模板包含：
- 元数据：名称、所属行业、风险等级
- 参数定义：名称、类型、是否必填、是否加密
- 步骤列表：每步指定 Skill + 参数映射

#### 6.10.2 6 个金融场景工作流模板

1. 银行流水下载（banking）
2. 跨行转账核对（banking）
3. 对公贷款放款申请（banking）
4. 保单申请填写（insurance）
5. 理赔审核提交（insurance）
6. 委托下单（securities）

#### 6.10.3 参数加密

- 敏感参数（密码、密钥）使用 Fernet 对称加密存储
- 加密密钥通过环境变量注入
- 运行时解密后传给执行器

#### 6.10.4 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/v1/workflows` | 工作流模板列表 |
| GET | `/api/v1/workflows/{id}` | 工作流详情 |
| POST | `/api/v1/workflows` | 创建工作流 |
| PUT | `/api/v1/workflows/{id}` | 更新工作流 |
| POST | `/api/v1/workflows/{id}/run` | 触发执行 |
| GET | `/api/v1/workflows/runs` | 执行历史 |
| GET | `/api/v1/workflows/runs/{run_id}` | 执行详情 |
| POST | `/api/v1/workflows/{id}/validate` | 校验工作流配置 |

### 6.11 通知模块（notification）

#### 6.11.1 通知通道

- 企业微信群机器人 Webhook
- 钉钉群机器人 Webhook（支持加签）

#### 6.11.2 通知模板

- 审批待处理通知
- 审批超时告警
- 任务失败通知
- NEEDS_HUMAN 接管通知
- 风险等级升级通知

#### 6.11.3 HTTP API

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/v1/notification/channels` | 通道列表 |
| PUT | `/api/v1/notification/channels/{type}` | 配置 Webhook |
| POST | `/api/v1/notification/test` | 发送测试通知 |

### 6.12 Skyvern 核心模块

#### 6.12.1 能力范围

- ForgeAgent：核心 AI Agent，负责任务规划、推理与动作执行
- 浏览器管理：Playwright 浏览器进程生命周期管理
- 页面抓取：DOM 解析、元素识别
- 动作执行：点击、输入、提取等
- LLM 调度：多 LLM 提供商适配（OpenAI、Anthropic、Azure、Ollama 等）

#### 6.12.2 与企业模块的协作

- 企业模块通过桥接调用 Skyvern 核心
- Skyvern 原生 HTTP API 保留，但对外不再直接暴露
- 前端统一通过企业后端访问

---

#### 6.13 批量数据驱动任务（M10，2026-08-06 新增）

**背景**：RPA 自动填表的核心价值是「同一流程模板 + 不同用户数据批量执行」。原系统仅支持单任务手动录入参数，每次换用户都要重填，违背「替代重复操作」初衷。本需求补齐数据驱动能力。

**功能概述**：
- **批量文件导入**：上传 CSV / TSV / Excel(.xlsx/.xls) 或粘贴多行（逗号/制表符分隔，首行表头），前端解析为 N 行数据。Excel 经 SheetJS 解析首个工作表并转为行数据，与 CSV/粘贴共用同一映射与提交链路。
- **字段映射**：将源列名映射到工作流模板的参数名（param name），支持用户逐列配置。
- **外部业务系统对接**：配置只读外部数据源（JDBC URL + 驱动 + 账号），按表名 + 可选 WHERE 条件拉取客户清单，映射后批量生成任务。
- **批量拆分执行**：后端将 N 份参数逐条调用现有 `WorkflowTriggerService.triggerWorkflow` 生成独立任务，汇总每条成功/失败结果（批次号 + 逐行明细）。
- **容量限制**：单次批量上限 500 行（防执行器过载）；外部数据源查询上限 1000 行。

**接口契约**：
- `POST /api/batch-tasks`：请求体 `{ workflowId, columnMapping, rows | externalQuery }`，响应 `BatchTaskResultVO { batchId, total, successCount, failedCount, results[] }`。

**验收标准**：
- 上传含 3 行用户的 CSV 或 Excel(.xlsx)，映射到模板参数后，系统生成 3 个独立任务且参数正确。
- 外部表查询返回数据并成功映射生成任务（外部数据源配置启用时）。
- 单批超过 500 行或空数据被拒绝并返回明确错误。
- 部分行失败时，整体返回汇总结果（成功数 + 失败数 + 失败原因），不中断其余行。

---

## 7. 非功能性需求

### 7.1 性能

| 指标 | 要求 |
|------|------|
| 后端 API P95 响应时间 | ≤ 200ms（不含 LLM 调用） |
| 后端单节点并发 | ≥ 200 QPS |
| 浏览器并发会话 | 单节点 ≥ 10 个并行浏览器上下文 |
| 数据库查询 P95 | ≤ 50ms（带索引） |
| Redis 命中率 | ≥ 95% |
| MinIO 截图上传延迟 | ≤ 500ms（单张） |

### 7.2 安全

- 全站 HTTPS（生产环境强制）
- JWT 鉴权，access token 60min，refresh token 7d
- 密码 BCrypt 加密
- 敏感参数 Fernet 对称加密
- SQL 注入防护（参数化查询）
- XSS 防护（前端转义 + CSP 头）
- CSRF 防护（SameSite Cookie + Token）
- 文件上传白名单 + 大小限制
- 审计日志不可篡改（仅追加，禁止修改）

### 7.3 合规

- 数据不出内网（私有化部署 + MinIO 内网存储）
- 全链路操作可追溯（审计日志 + 截图）
- 高风险操作人工审批（金融监管要求）
- 职责分离（operator/approver 互斥）
- 跨部门审计（cross_org_read / cross_org_approve）
- 敏感信息脱敏（卡号、身份证、密码等）
- 审计日志保留期 ≥ 90 天

### 7.4 可用性

- 单节点部署可用性 ≥ 99%
- 服务健康检查
- 优雅停机
- 任务断点续跑（CoordinationState 持久化）
- LLM 失败不影响任务终态（NEEDS_HUMAN 兜底）

### 7.5 可观测性

- 结构化 JSON 日志
- 应用指标暴露（Prometheus 兼容）
- 链路追踪（可选接入 OpenTelemetry）
- 浏览器实时流（SSE 推送到前端）
- 健康检查端点

### 7.6 可维护性

- 代码规范：Java 遵循阿里巴巴 Java 开发手册，Python 遵循 PEP 8
- 静态检查：Java 用 Checkstyle + SpotBugs，Python 用 ruff + mypy
- 单元测试覆盖率：≥ 80%
- 集成测试覆盖率：≥ 60%
- 文档：每个公开类/函数必须有 Javadoc / docstring

### 7.7 兼容性

- 浏览器：Chrome / Edge / Firefox 最新版本
- 操作系统：Linux（CentOS 7+ / Ubuntu 20.04+）
- 数据库：PostgreSQL 14+
- 缓存：Redis 7+
- 对象存储：MinIO RELEASE.2024+

---

## 8. 风险与假设

### 8.1 业务风险

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 真实金融系统对接失败 | 高 | 本项目不直接对接真实系统，仅模拟站点验证 |
| 信创认证缺失 | 中 | 商业化阶段补齐等保三级 + 信通院认证 |
| 演示数据与真实场景偏差 | 低 | 参考真实业务流程设计 6 个工作流模板 |

### 8.2 假设

1. 部署环境为金融机构内网，可访问 LLM API（OpenAI/Anthropic/Azure 或本地 Ollama）
2. 浏览器可访问目标金融系统的 Web 端
3. 演示账号仅用于功能验证，不代表真实组织结构
4. 6 个工作流模板基于公开的业务流程设计，不涉及具体客户数据

---

## 9. 验收标准

### 9.1 功能验收

- [ ] 6 种角色登录与权限校验全部通过
- [ ] 7 个 Skill 单元测试全部通过
- [ ] 6 个工作流模板可执行（在模拟站点上）
- [ ] 两阶段风险检测准确率 ≥ 90%
- [ ] 审批流端到端跑通（创建 → 通知 → 批准/拒绝 → 状态流转）
- [ ] 审计日志多维度检索 + CSV 导出
- [ ] 运营大屏所有 KPI 指标正确
- [ ] LLM 三层容错全部触发验证
- [ ] NEEDS_HUMAN 人工接管三种处置全部可用
- [ ] 双 Agent 断点续跑验证（中断后续跑成功）
- [ ] Action 缓存命中率统计准确
- [ ] 模型路由按复杂度评分正确选模型
- [ ] 通知通道（企业微信 + 钉钉）测试发送成功
- [ ] 国际化中英文切换无遗漏

### 9.2 非功能验收

- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试覆盖率 ≥ 60%
- [ ] 后端 API P95 ≤ 200ms
- [ ] 单节点支持 ≥ 200 QPS
- [ ] 浏览器并发会话 ≥ 10
- [ ] Docker Compose 一键启动 + 健康检查通过
- [ ] 生产模式（含 Nginx）部署通过
- [ ] HTTPS 配置可用
- [ ] 演示数据脚本一键导入

### 9.3 文档验收

- [ ] 需求分析文档（本文档）
- [ ] 系统设计文档（架构 + 模块设计 + 数据模型详设）
- [ ] API 文档（OpenAPI 3.0）
- [ ] 部署文档（开发 + 生产）
- [ ] 开发者文档（本地启动 + 调试 + 测试）
- [ ] 用户手册（演示账号 + 5 个核心场景操作指南）

---

## 10. 附录

### 10.1 术语表

| 术语 | 含义 |
|------|------|
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| 三维度权限 | 部门 × 业务线 × 角色 的复合权限模型 |
| 职责分离 | operator 与 approver 角色互斥，防止一人同时操作与审批 |
| NEEDS_HUMAN | LLM 失败后任务转入人工接管状态 |
| Action 缓存 | 相同页面结构 + 相同导航目标时复用 LLM 决策结果 |
| 模型路由 | 根据页面复杂度自动选择轻量/标准/重型 LLM |
| 两阶段风险检测 | 关键词预筛 + LLM 精准判断 的双重风险识别 |
| 断点续跑 | 任务中断后从最近成功的子任务继续执行 |
| 信创 | 信息技术应用创新，指国产化软硬件适配 |
| 等保 | 网络安全等级保护，金融行业通常要求三级 |

### 10.2 参考文档

- Skyvern 官方文档：https://www.skyvern.com/docs
- Skyvern GitHub：https://github.com/Skyvern-AI/skyvern
- finrpa-enterprise 原项目：D:\lingou-projects\tempgithub\finrpa-enterprise

### 10.3 修订记录

| 版本 | 日期 | 修订人 | 说明 |
|------|------|--------|------|
| v1.0 | 2026-07-25 | - | 初稿，包含技术栈映射 |
| v1.1 | 2026-07-25 | - | 移除技术栈映射、数据模型详设、部署架构等设计阶段内容，聚焦市场分析与功能需求 |

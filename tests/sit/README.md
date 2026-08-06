# M9.5 SIT 系统集成测试

FinanceRPA 跨服务接口契约一致性 + 数据一致性 + 异常分支覆盖测试套件。

## 与 E2E 的区别

| 维度 | SIT（本目录） | E2E（`tests/e2e/`） |
|------|---------------|----------------------|
| **目标** | 验证模块间接口契约、数据一致性、异常分支 | 验证用户场景端到端正确性 |
| **驱动方式** | API 调用 + 内部回调模拟（不驱动前端 UI） | Playwright 驱动前端 UI |
| **Skyvern 依赖** | 仅场景1 驱动真实 Skyvern 执行 | 6 个场景均驱动真实 Skyvern |
| **内部回调** | 用 `X-Internal-Token` 模拟 Python → Java 回调 | 不涉及 |
| **执行速度** | 8 个用例约 6.5 分钟（场景1 占 4 分钟） | 6 个场景约 30 分钟 |

## 测试场景

共 5 个场景、8 个测试用例：

| 场景 | 文件 | 用例数 | 耗时 | 验证点 |
|------|------|--------|------|--------|
| **1. 全链路接口契约** | `01-contract.spec.ts` | 1 | ~4min | 真实 Skyvern 执行"银行流水下载"，验证 `WorkflowRunVO`/`TaskDetailVO`/`SubTaskVO`/`AuditLogVO`/`OverviewVO` 字段对齐 + 状态流转（PENDING → EXECUTING → SUCCESS）+ 数据一致性 |
| **2. LLM 失败 → NEEDS_HUMAN** | `02-needs-human-flow.spec.ts` | 2 | ~4s | 模拟 LLM 失败 → NEEDS_HUMAN 入队 → 队列查询 → skip 续跑 SUCCESS + abort 终止 ABORTED |
| **3. 任务中断 → 断点续跑** | `03-task-resume.spec.ts` | 2 | ~2s | coordination-state 持久化 → 中断 → resume 续跑（跳过已完成子任务）+ resume 前置校验（非 FAILED/NEEDS_HUMAN 不可续跑） |
| **4. 审批超时 → 自动拒绝** | `04-approval-timeout.spec.ts` | 1 | ~2.2min | 动态改 high 风险超时阈值 1 分钟 → 等超时 → 审批单 TIMEOUT + 任务 ABORTED + 阈值还原 |
| **5. 跨组织数据隔离** | `05-cross-org-isolation.spec.ts` | 2 | ~2s | admin（银河证券）与 admin_demo_xcba（星辰银行）双向隔离：任务列表不可见、直接查询被拒绝、审批单/审计日志不可见 |

## 运行前置条件

1. **docker-compose 全栈已启动**（Java 后端 + Python finance-ai + mock-bank + Postgres + Redis + Skyvern）
2. **演示数据已初始化**（`DemoDataGenerator` 已执行，包含 admin / admin_demo_xcba 账号 + 6 个工作流模板）
3. **ShedLock 审批超时检测定时任务正常运行**（场景4 依赖，每分钟扫描）
4. **Node.js 依赖已安装**：`npm install`

## 运行命令

```bash
cd tests/sit

# 安装依赖（首次）
npm install

# 运行全部 5 个场景（串行执行，约 7 分钟）
npm test

# 运行单个场景
npm run test:contract      # 场景1：全链路契约（~4min，真实 Skyvern）
npm run test:needs-human   # 场景2：NEEDS_HUMAN 流程（~4s）
npm run test:resume        # 场景3：断点续跑（~2s）
npm run test:timeout       # 场景4：审批超时（~2.2min）
npm run test:isolation     # 场景5：跨组织隔离（~2s）

# CI 模式（失败重试 1 次）
$env:CI="true"; npm test

# 查看 HTML 测试报告
npm run report
```

## 环境变量

所有环境变量均有默认值，适配本地 docker-compose 全栈，无需额外配置即可运行。

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `BACKEND_URL` | `http://localhost:8080` | Java 后端地址 |
| `MOCK_BANK_BASE` | `http://mock-bank` | mock 银行页基址（场景1 用，Skyvern 容器内访问） |
| `AI_INTERNAL_TOKEN` | `finrpa-internal-secret` | 内部回调 token（模拟 Python 回调用） |
| `SIT_USERNAME_PRIMARY` | `admin` | 主组织账号（银河证券 org_admin） |
| `SIT_PASSWORD_PRIMARY` | `admin123` | 主组织账号密码 |
| `SIT_USERNAME_CROSS` | `admin_demo_xcba` | 跨组织账号（星辰银行 org_admin） |
| `SIT_PASSWORD_CROSS` | `123456` | 跨组织账号密码 |
| `TASK_WAIT_TIMEOUT` | `600000`（10 分钟） | 等待任务终态最大毫秒数（场景1 用） |
| `TASK_POLL_INTERVAL` | `3000` | 任务终态轮询间隔（毫秒） |
| `APPROVAL_TIMEOUT_WAIT` | `180000`（3 分钟） | 审批超时等待最大毫秒数（场景4 用） |
| `APPROVAL_TIMEOUT_POLL_INTERVAL` | `5000` | 审批超时轮询间隔（毫秒） |

## 测试架构

```
tests/sit/
├── scenarios/                    # 5 个测试场景
│   ├── 01-contract.spec.ts       # 全链路接口契约（真实 Skyvern）
│   ├── 02-needs-human-flow.spec.ts  # LLM 失败 → NEEDS_HUMAN → 处置
│   ├── 03-task-resume.spec.ts    # 断点续跑
│   ├── 04-approval-timeout.spec.ts  # 审批超时
│   └── 05-cross-org-isolation.spec.ts  # 跨组织隔离
├── lib/
│   ├── api.ts                    # API 客户端（对外 Bearer JWT + 内部 X-Internal-Token）
│   ├── types.ts                  # TypeScript 类型定义（对齐 Java VO/DTO）
│   ├── fixtures.ts               # test fixture + 辅助函数（waitForTaskTerminal 等）
│   └── env.ts                    # 环境变量读取
├── playwright.config.ts          # Playwright 配置（串行、15min 超时）
├── package.json
└── tsconfig.json
```

### 鉴权方式

- **对外接口**（`/api/tasks`、`/api/approvals` 等）：Bearer JWT（登录 admin 账号获取 token）
- **内部回调接口**（`/api/internal/*`）：`X-Internal-Token` Header（模拟 Python → Java 回调）

### 接口契约对齐

`lib/types.ts` 中的 TypeScript 接口与 Java 后端 VO/DTO 一一对齐：

| TypeScript 接口 | Java 类 | 说明 |
|-----------------|---------|------|
| `WorkflowVO` | `WorkflowVO` | 工作流模板 |
| `WorkflowRunVO` | `WorkflowRunVO` | 触发工作流响应 |
| `TaskVO` / `TaskDetailVO` | `TaskVO` / `TaskDetailVO` | 任务（含子任务） |
| `SubTaskVO` | `SubTaskVO` | 子任务 |
| `ApprovalRequestVO` | `ApprovalRequestVO` | 审批单 |
| `ApprovalTimeoutConfigVO` | `ApprovalTimeoutConfigVO` | 审批超时配置 |
| `NeedsHumanQueueVO` | `NeedsHumanQueueVO` | NEEDS_HUMAN 队列 |
| `CoordinationStateVO` | `CoordinationStateVO` | 协调状态（断点续跑用） |
| `AuditLogVO` | `AuditLogVO` | 审计日志 |
| `OverviewVO` | `OverviewVO` | 大屏概览 |

> **注意**：Java 后端雪花 ID（`Long`）通过 Jackson 序列化为 JSON 字符串，TypeScript 中对应字段类型为 `string`（非 `number`），避免大整数精度丢失。

## 已知契约差异

| 差异 | 说明 | 处理方式 |
|------|------|----------|
| `WorkflowRunVO.approvalId` null vs undefined | Java Jackson 默认序列化 null 字段，medium 风险时返回 `{"approvalId": null}`；TypeScript 可选字段期望 `undefined` | 测试断言用 `toBeFalsy()` 兼容 null/undefined；前端 `if (run.approvalId)` 判断两者等价 |

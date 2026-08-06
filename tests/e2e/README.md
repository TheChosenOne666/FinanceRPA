# FinanceRPA M9.1 端到端测试

6 个金融场景全链路验收：触发 → 审批 → Skyvern 执行 mock 银行页 → 审计 → 大屏统计。

## 目录结构

```
tests/e2e/
├── package.json              # Playwright + TS 依赖
├── playwright.config.ts      # 测试配置
├── tsconfig.json
├── lib/
│   ├── types.ts              # API 类型定义（对齐 Java VO/DTO）
│   ├── api.ts                # API 客户端（登录/工作流/审批/任务/审计/大屏）
│   └── env.ts                # 环境变量读取
├── fixtures/
│   └── workflow.ts           # 工作流 fixtures（按 name 查模板、触发、审批、等待终态）
├── scenarios/                # 6 个场景测试
│   ├── 01-bank-statement.spec.ts       # 银行流水下载（medium，不审批）
│   ├── 02-cross-bank-reconcile.spec.ts # 跨行转账核对（high，部门审批）
│   ├── 03-corporate-loan.spec.ts       # 对公贷款放款（critical，合规审批）
│   ├── 04-policy-application.spec.ts   # 保单申请填写（high）
│   ├── 05-claim-review.spec.ts         # 理赔审核提交（high）
│   └── 06-securities-order.spec.ts     # 委托下单（high）
└── mock-bank/                # mock 银行页（供 Skyvern 操作）
    ├── nginx.conf
    └── ...                   # 6 个场景静态 HTML
```

## 运行前置

1. 全栈已启动（docker-compose up -d，含 Java/Python/前端/PG/Redis/MinIO/Skyvern）
2. mock 银行页服务已启动（docker-compose -f docker-compose.e2e.yml up -d mock-bank）
3. 火山方舟 API key 已配置（.env 的 VOLCENGINE_API_KEY，Skyvern 视觉决策用）

## 运行

```bash
cd tests/e2e
npm install
npm test                 # 跑全部 6 个场景
npm run test:scenario1   # 只跑场景 1
npm run report           # 查看测试报告
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| BACKEND_URL | http://localhost:8080 | Java 后端地址 |
| MOCK_BANK_BASE | http://mock-bank | mock 银行页基址（Skyvern 容器内访问） |
| E2E_USERNAME | admin | 演示账号 |
| E2E_PASSWORD | admin123 | 演示密码 |
| TASK_WAIT_TIMEOUT | 240000 | 等待任务终态最大毫秒数 |

## 验收标准

- 6 个场景全部通过
- 每个场景审计日志完整（至少 1 条 skyvern_task_execution 记录）
- 大屏统计任务数正确增加

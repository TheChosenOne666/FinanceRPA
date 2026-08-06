# M9.2 性能测试

FinanceRPA 性能测试套件，覆盖 5 个维度：单任务延迟、并发吞吐、SSE 推送延迟、审计百万查询、LLM 成本基线。

## 与 SIT / E2E 的区别

| 维度 | 性能测试（本目录） | SIT（`tests/sit/`） | E2E（`tests/e2e/`） |
|------|-------------------|---------------------|---------------------|
| **目标** | 测量延迟/吞吐/成本基线，验证验收标准 | 验证模块间接口契约、数据一致性 | 验证用户场景端到端正确性 |
| **驱动方式** | API 调用 + 内部回调模拟 + EventSource + 直连 PG | API 调用 + 内部回调模拟 | Playwright 驱动前端 UI |
| **数据规模** | 百万级审计日志（SQL 造数） | 演示数据 | 演示数据 |
| **真实 LLM** | 场景5 真实调用火山方舟豆包 | 不涉及 | 不涉及 |
| **执行速度** | 5 个场景约 30-60 分钟 | 8 个用例约 6.5 分钟 | 6 个场景约 30 分钟 |

## 测试场景

共 5 个场景：

| 场景 | 文件 | 耗时 | 验证点 |
|------|------|------|--------|
| **1. 单任务延迟基线** | `01-single-task-latency.spec.ts` | ~4min | 真实 Skyvern 银行流水下载，测量触发延迟 / 端到端延迟 / 任务详情查询 / 审计查询延迟 |
| **2. 并发吞吐量** | `02-concurrent-throughput.spec.ts` + `jmeter/concurrent-mock.jmx` | ~15-30min | 5 并发真实 Skyvern（成功率 ≥ 90%）+ 50/100 并发 JMeter 模拟内部回调（吞吐量基线） |
| **3. SSE 推送延迟** | `03-sse-latency.spec.ts` | ~5min | EventSource 连接 Java SSE 端点，采集事件推送延迟 P95 < 500ms |
| **4. 审计查询性能** | `04-audit-query-perf.spec.ts` | ~5min | 百万级审计日志分页/多维检索/深度翻页/COUNT 查询 P95 < 500ms |
| **5. LLM 成本基线** | `05-llm-cost-baseline.spec.ts` | ~2min | 5 次真实火山方舟豆包风险判断，统计 token 用量与成本 |

## 验收标准

| 指标 | 标准 | 场景 |
|------|------|------|
| 单任务延迟 | 触发 < 5s，详情查询 < 1s，审计查询 < 500ms（端到端记录基线，不强制 < 30s，因含 LLM 视觉决策） | 场景1 |
| 5 并发稳定运行 | 成功率 ≥ 90% | 场景2 |
| SSE 推送延迟 | P95 < 500ms | 场景3 |
| 审计查询 | 分页/多维/COUNT P95 < 500ms，深度翻页 P95 < 2000ms | 场景4 |
| LLM 调用 | P95 < 30s，至少成功 1 次 | 场景5 |

## 运行前置条件

1. **docker-compose 全栈已启动**（Java 后端 + Python finance-ai + mock-bank + Postgres + Redis + Skyvern）
2. **演示数据已初始化**（`DemoDataGenerator` 已执行）
3. **`.env` 已配置 `VOLCENGINE_API_KEY`**（场景5 真实调用火山方舟豆包，会产生约 1-2 元费用）
4. **Node.js 依赖已安装**：`npm install`
5. **场景4 前置造数**：`npm run seed:audit`（造 100 万条审计日志，约 1-2 分钟）

## 运行命令

```bash
cd tests/perf

# 安装依赖（首次）
npm install

# 场景4 前置：造百万级审计日志
npm run seed:audit

# 运行全部 5 个场景（串行执行，约 30-60 分钟）
npm test

# 运行单个场景
npm run test:single       # 场景1：单任务延迟（~4min，真实 Skyvern）
npm run test:concurrent   # 场景2 第1组：5 并发真实 Skyvern（~15-25min）
npm run test:concurrent:jmx:50   # 场景2 第2组：50 并发 JMeter 模拟回调（~1min）
npm run test:concurrent:jmx:100  # 场景2 第3组：100 并发 JMeter 模拟回调（~2min）
npm run test:sse          # 场景3：SSE 延迟（~5min）
npm run test:audit        # 场景4：审计百万查询（~5min，需先 seed:audit）
npm run test:llm          # 场景5：LLM 成本基线（~2min，真实调用豆包）

# 查看 HTML 测试报告
npm run report

# 类型检查
npm run typecheck
```

## 环境变量

所有环境变量均有默认值，适配本地 docker-compose 全栈。

### 基础配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `BACKEND_URL` | `http://localhost:8080` | Java 后端地址 |
| `AI_URL` | `http://localhost:8000` | Python finance-ai 地址（场景5 LLM 调用） |
| `MOCK_BANK_BASE` | `http://mock-bank` | mock 银行页基址（Skyvern 容器内访问） |
| `AI_INTERNAL_TOKEN` | `finrpa-internal-secret` | 内部回调 token |
| `PERF_USERNAME` | `admin` | 主组织账号 |
| `PERF_PASSWORD` | `admin123` | 主组织账号密码 |

### 场景1 单任务延迟

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `TASK_WAIT_TIMEOUT` | `600000` | 等待任务终态最大毫秒数 |
| `TASK_POLL_INTERVAL` | `3000` | 任务终态轮询间隔 |

### 场景2 并发吞吐

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `REAL_CONCURRENT` | `5` | 真实 Skyvern 并发数（Playwright） |
| `MOCK_CONCURRENT_LEVELS` | `50,100` | JMeter 模拟并发档位（命令行 `-Jconcurrent.level` 传入） |
| `MOCK_TASK_DURATION_MS` | `2000` | 模拟单任务执行时长（JMeter Constant Timer） |
| `MOCK_CONCURRENT_TIMEOUT` | `300000` | 模拟并发全部完成超时（保留配置，JMeter 不使用） |

**JMeter 配置**（命令行 `-J` 传入，见 `jmeter/concurrent-mock.jmx`）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `concurrent.level` | `50` | JMeter 线程数（并发数） |
| `backend.host` | `localhost` | Java 后端主机 |
| `backend.port` | `8080` | Java 后端端口 |
| `internal.token` | `finrpa-internal-secret` | 内部回调 token |
| `mock.duration.ms` | `2000` | 两次回调间隔（模拟任务执行时长） |

**JMeter 安装路径**：默认调用 `jmeter` 命令（需加入 PATH）。本地安装路径 `D:\jmeter\apache-jmeter-5.6.3`，可将 `D:\jmeter\apache-jmeter-5.6.3\bin` 加入系统 PATH，或直接修改 `package.json` 脚本为完整路径。

### 场景3 SSE 延迟

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SSE_TEST_DURATION` | `30000` | SSE 测试订阅持续时长 |
| `SSE_EVENT_TIMEOUT` | `60000` | SSE 单次等待事件超时 |

### 场景4 审计造数

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PG_HOST` | `localhost` | PostgreSQL 主机 |
| `PG_PORT` | `5432` | PostgreSQL 端口 |
| `PG_DB` | `finrpa` | 数据库名 |
| `PG_USER` | `finrpa` | 用户名 |
| `PG_PASSWORD` | `finrpa` | 密码 |
| `AUDIT_SEED_COUNT` | `1000000` | 造数条数 |
| `AUDIT_SEED_BATCH_SIZE` | `10000` | 造数批次大小 |
| `AUDIT_QUERY_SAMPLES` | `100` | 审计查询采样次数 |

### 场景5 LLM 成本基线

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `LLM_SAMPLE_SIZE` | `5` | LLM 采样次数 |
| `LLM_CALL_TIMEOUT` | `30000` | LLM 单次调用超时 |

## 测试架构

```
tests/perf/
├── scenarios/                              # 5 个测试场景
│   ├── 01-single-task-latency.spec.ts      # 单任务延迟基线（真实 Skyvern）
│   ├── 02-concurrent-throughput.spec.ts    # 并发吞吐第1组（5 真实 Skyvern）
│   ├── 03-sse-latency.spec.ts              # SSE 推送延迟
│   ├── 04-audit-query-perf.spec.ts         # 审计百万查询
│   └── 05-llm-cost-baseline.spec.ts        # LLM 成本基线（真实豆包）
├── jmeter/                                 # JMeter 压测（场景2 第2/3组）
│   └── concurrent-mock.jmx                 # 50/100 并发模拟内部回调
├── lib/
│   ├── api.ts                              # API 客户端（Java + Python）
│   ├── types.ts                            # TypeScript 类型定义
│   ├── metrics.ts                          # 性能统计工具（P50/P95/P99/throughput）
│   ├── fixtures.ts                         # test fixture + 辅助函数
│   └── env.ts                              # 环境变量读取
├── scripts/
│   └── seed-audit-logs.mjs                 # 百万级审计日志造数脚本
├── playwright.config.ts                    # Playwright 配置（串行、60min 超时）
├── package.json
└── tsconfig.json
```

### 鉴权方式

- **对外接口**（`/api/tasks`、`/api/audit/logs` 等）：Bearer JWT（登录 admin 账号）
- **内部回调接口**（`/api/internal/*`）：`X-Internal-Token` Header（模拟 Python → Java 回调）
- **Python AI 接口**（`/api/v1/ai/risk/judge`）：直连 Python，无鉴权（开发环境）
- **PostgreSQL 直连**：用户名密码（造数与 COUNT 查询用）

### 性能指标计算

`lib/metrics.ts` 提供：

- `computeStats(samples)`：从样本列表计算 P50/P95/P99/avg/min/max/throughput
- `formatStats(title, stats)`：格式化为可读字符串
- `saveStats(title, stats, filename)`：保存为 JSON 文件（供报告引用）
- `measure(fn)`：测量异步函数执行耗时

分位数计算采用 nearest-rank 方法：`index = ceil(p/100 * N) - 1`。

### 审计造数策略

`scripts/seed-audit-logs.mjs` 直连 PostgreSQL，用 `generate_series` 批量 INSERT：

- `audit_id` 起始值 `8000000000000000000`（8×10^18，低于 bigint 上限 9.22×10^18，避开雪花算法真实 ID 范围）
- 数据分布：6 个 org_id × 4 个 risk_level × 10 个 action_type
- 时间范围：过去 90 天（按 `random()` 分布）
- 测试后自动清理（`DELETE WHERE audit_id >= 8000000000000000000`）
- 造数后执行 `ANALYZE` 更新统计信息

## 产出物

运行测试后产出以下文件（已加入 `.gitignore`）：

- `perf-single-task.perf.json`：场景1 单任务延迟基线
- `perf-concurrent-real-10.perf.json`：场景2 10 并发真实 Skyvern
- `perf-concurrent-mock-50.perf.json`：场景2 50 并发模拟回调
- `perf-concurrent-mock-100.perf.json`：场景2 100 并发模拟回调
- `perf-sse-latency.perf.json`：场景3 SSE 推送延迟
- `perf-audit-page.perf.json`：场景4 审计分页查询
- `perf-audit-multi.perf.json`：场景4 审计多维检索
- `perf-audit-deeppage.perf.json`：场景4 审计深度翻页
- `perf-audit-count.perf.json`：场景4 审计 COUNT 查询
- `perf-llm-latency.perf.json`：场景5 LLM 调用延迟
- `perf-llm-cost.perf.json`：场景5 LLM 成本基线

测试结果汇总见 `docs/perf-test-report.md`。

# M9.2 性能测试报告

> 测试时间：2026-08-06
> 测试环境：docker-compose 全栈（Java 后端 + Python finance-ai + mock-bank + Postgres + Redis + Skyvern）
> 测试套件：`tests/perf/`

## 1. 测试概览

| 场景 | 文件 | 验收标准 | 状态 |
|------|------|----------|------|
| 1. 单任务延迟基线 | `01-single-task-latency.spec.ts` | 触发 < 5s，详情 < 1s，审计 < 500ms | ✅ 通过 |
| 2. 并发吞吐量 | `02-concurrent-throughput.spec.ts` + `jmeter/concurrent-mock.jmx` | 5 并发成功率 ≥ 90% | ✅ 通过 |
| 3. SSE 推送延迟 | `03-sse-latency.spec.ts` | P95 < 500ms | ✅ 通过 |
| 4. 审计查询性能 | `04-audit-query-perf.spec.ts` | 分页/多维/COUNT P95 < 500ms | ⏳ 待执行 |
| 5. LLM 成本基线 | `05-llm-cost-baseline.spec.ts` | P95 < 30s，至少成功 1 次 | ⏳ 待执行 |

> 状态说明：⏳ 待执行 / ✅ 通过 / ❌ 失败 / ⚠️ 部分通过

## 2. 测试策略

### 2.1 并发压测混合策略（方案B）

真实 Skyvern 单任务耗时约 4 分钟（含 LLM 视觉决策 + 浏览器自动化），100 并发会瞬间打爆浏览器/LLM/内存资源。采用混合策略：

- **5 并发（Playwright）**：用真实 Skyvern 验证端到端并发能力（场景2 第 1 组）
- **50/100 并发（JMeter）**：用 JMeter 压测内部回调接口 `POST /api/internal/tasks/{id}/state`，绕过 Skyvern 视觉决策，专注测 Java 后端 + PostgreSQL 的并发处理能力（场景2 第 2、3 组）

JMeter 测试计划见 `tests/perf/jmeter/concurrent-mock.jmx`，通过命令行参数 `-Jconcurrent.level=50/100` 控制并发数，生成 HTML 报告（TPS、响应时间分布、错误率图表）。

### 2.2 审计百万造数

直连 PostgreSQL 用 `generate_series` 批量 INSERT 100 万条审计日志：

- `audit_id` 起始值 `9900000000000000000`，避开雪花算法 ID 范围
- 数据分布：6 个 org_id × 4 个 risk_level × 10 个 action_type
- 时间范围：过去 90 天（按 `random()` 分布）
- 测试后自动清理（`DELETE WHERE audit_id >= 9900000000000000000`）
- 造数后执行 `ANALYZE` 更新统计信息

### 2.3 LLM 成本基线

触发 5 次真实 LLM 风险判断（`POST Python /api/v1/ai/risk/judge`），从 `rpa_llm_call_log` 表统计：

- prompt_tokens / completion_tokens / total_tokens
- cost（美元）
- 缓存命中率
- 风险判断准确率

预算约 1-2 元（5 次 × doubao-seed 单次约 0.2 元）。

## 3. 测试结果（待填充）

> 执行 `npm test` 后，将各场景的 `.perf.json` 结果汇总到此处。

### 3.1 场景1 单任务延迟基线

> 测试时间：2026-08-06　　任务终态：SUCCESS（真实 Skyvern 银行流水下载）

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| 触发延迟 | 1502 ms | < 5000ms | ✅ |
| 任务详情查询延迟 | 88 ms | < 1000ms | ✅ |
| 端到端延迟 | 321246 ms (5.4min) | 记录基线 | - |
| 审计日志查询延迟 | 32 ms | < 500ms | ✅ |

**结论**：触发、详情查询、审计查询均远低于验收标准。端到端延迟 5.4min 为基线值（含 Skyvern LLM 视觉决策 + 浏览器自动化，不强制 < 30s）。

### 3.2 场景2 并发吞吐量

> 测试时间：2026-08-06　　测试耗时：约 8min（第1组 6min + 第2组 0.2min + 第3组 0.2min）

#### 第1组 · 5 并发真实 Skyvern（Playwright）

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| 样本数 | 5 | 5 | ✅ |
| 成功率 | 100% | ≥ 90% | ✅ |
| P50 端到端延迟 | 299309ms (~5.0min) | - | - |
| P95 端到端延迟 | 357159ms (~5.95min) | - | - |
| P99 端到端延迟 | 357159ms | - | - |
| 吞吐量 | 0.01 req/s | - | - |
| 耗时 | 6.0min | - | - |

#### 第2组 · 50 并发模拟回调（JMeter）

> 执行命令：`npm run test:concurrent:jmx:50`
> 报告产出：`jmeter/report-50/index.html` + `jmeter/results-50.jtl`

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| 样本数 | 100（50 线程 × 2 回调） | 50 | ✅ |
| 错误率 | 0% | - | ✅ |
| P50 回调延迟 | 9ms | - | - |
| P90 回调延迟 | 67ms | - | - |
| P95 回调延迟 | 67ms | - | - |
| P99 回调延迟 | 67ms | - | - |
| 吞吐量 | 17.0 req/s | - | - |
| 耗时 | 9s | - | - |

#### 第3组 · 100 并发模拟回调（JMeter）

> 执行命令：`npm run test:concurrent:jmx:100`
> 报告产出：`jmeter/report-100/index.html` + `jmeter/results-100.jtl`

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| 样本数 | 200（100 线程 × 2 回调） | 100 | ✅ |
| 错误率 | 0% | - | ✅ |
| P50 回调延迟 | 8ms | - | - |
| P90 回调延迟 | 70ms | - | - |
| P95 回调延迟 | 70ms | - | - |
| P99 回调延迟 | 70ms | - | - |
| 吞吐量 | 33.74 req/s | - | - |
| 耗时 | 9s | - | - |

#### 瓶颈分析

1. **第1组端到端延迟高（~5min/任务）**：瓶颈在 Skyvern LLM 视觉决策 + 浏览器自动化，不是 Java 后端。5 个任务并发时总耗时 6min（接近单任务 5.4min），说明 Skyvern 并发执行能力良好，无严重排队
2. **第2/3组回调延迟极低（P95 < 100ms）**：Java 后端 + PostgreSQL 并发处理能力强。50→100 并发，P95 从 67ms→70ms（仅 +3ms），吞吐量从 17→33.74 req/s（近线性增长），说明系统还有很大余量
3. **100 并发 0 错误**：Java 后端 Tomcat + HikariCP 连接池配置合理，能承受 100 并发回调无超时无拒绝

### 3.3 场景3 SSE 推送延迟

> 测试时间：2026-08-06　　测试耗时：3.8min　　任务终态：SUCCESS
> 事件分布：42 个 progress + 1 个 complete（Skyvern 银行流水下载，走 M3.8 原生路径）

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| 首个事件延迟 | 5996 ms | < 60000ms | ✅ |
| 事件总数 | 43 | > 0 | ✅ |
| 推送延迟 min | 0 ms | - | - |
| 推送延迟 avg | 2 ms | - | - |
| 推送延迟 P50 | 2 ms | - | - |
| 推送延迟 P95 | 3 ms | < 500ms | ✅ |
| 推送延迟 P99 | 16 ms | - | - |
| 推送延迟 max | 16 ms | - | - |
| 事件间隔 P50 | 5028 ms | - | 符合 5s 轮询间隔 |
| 事件间隔 P95 | 5372 ms | - | - |

**结论**：SSE 端到端推送延迟极低（P95=3ms），Java 透传 Python SSE 流无性能损耗。

### 3.4 场景4 审计查询性能

#### 分页查询（命中 org_id 索引）

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| P50 | - ms | - | - |
| P95 | - ms | < 500ms | - |
| P99 | - ms | - | - |

#### 多维检索（时间 + risk_level + action_type）

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| P50 | - ms | - | - |
| P95 | - ms | < 500ms | - |
| P99 | - ms | - | - |

#### 深度翻页（第 1000 页，LIMIT OFFSET 20000）

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| P50 | - ms | - | - |
| P95 | - ms | < 2000ms | - |
| P99 | - ms | - | - |

#### COUNT 查询（org_id 索引）

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| P50 | - ms | - | - |
| P95 | - ms | < 500ms | - |
| P99 | - ms | - | - |

### 3.5 场景5 LLM 成本基线

#### 调用延迟

| 指标 | 测量值 | 验收标准 | 结果 |
|------|--------|----------|------|
| 样本数 | - | 5 | - |
| 成功数 | - | ≥ 1 | - |
| P50 延迟 | - ms | - | - |
| P95 延迟 | - ms | < 30000ms | - |

#### Token 与成本

| 指标 | 测量值 |
|------|--------|
| 模型 | - |
| 单次平均 prompt_tokens | - |
| 单次平均 completion_tokens | - |
| 单次平均 total_tokens | - |
| 单次平均成本 | $- |
| 总成本 | $- |
| 缓存命中率 | - % |

#### 风险判断准确率

| 测试用例 | 期望风险 | 实际风险 | 结果 |
|----------|----------|----------|------|
| 银行流水下载 | medium | - | - |
| 1000万市价单 | high | - | - |
| 5万理赔审核 | medium | - | - |
| 查询余额 | low | - | - |
| 500万跨行转账 | critical | - | - |

## 4. 性能瓶颈与优化建议

### 4.1 场景3 发现并修复的问题

场景3 执行过程中发现两个真实 bug（非测试脚本问题），已修复：

#### 问题1：Python SSE timestamp 时区偏移（8 小时）

**现象**：首次跑场景3 时，SSE 推送延迟 P95 = 28800003ms（≈8 小时），恰好对应 GMT-8 时区偏移。

**根因**：[event_bus.py](file:///d:/lingou-projects/financeRPA/finance-ai/app/agent/event_bus.py) 等 3 处使用 `datetime.utcnow().isoformat()` 生成无时区信息的 naive datetime 字符串（形如 `2026-08-06T12:00:00.123456`）。前端/测试端 `new Date(ts)` 按本地时区 GMT-8 解析，把"UTC 12:00"当成"北京时间 12:00"，产生 8 小时负偏移。

**修复**：3 处 `datetime.utcnow()` → `datetime.now(timezone.utc)`，isoformat 输出带 `+00:00` 时区标识，`new Date()` 正确按 UTC 解析：
- [event_bus.py#L111](file:///d:/lingou-projects/financeRPA/finance-ai/app/agent/event_bus.py#L111) — SSE 事件 timestamp（核心）
- [schemas.py#L101](file:///d:/lingou-projects/financeRPA/finance-ai/app/schemas.py#L101) — SseEvent.timestamp 字段
- [resilient_caller.py#L336](file:///d:/lingou-projects/financeRPA/finance-ai/app/llm/resilient_caller.py#L336) — LLM 调用记录 timestamp

修复后 P95 从 28800003ms 降至 3ms。

#### 问题2：Skyvern 监控路径不发布中间 SSE 事件

**现象**：首次跑场景3 时，首个事件延迟 = 234711ms（≈3.9 分钟），恰好等于整个任务执行时长。只收到 1 个 `complete` 事件，无中间进度事件。`firstEventLatency < 60000ms` 断言失败。

**根因**：[tasks.py](file:///d:/lingou-projects/financeRPA/finance-ai/app/api/tasks.py) 的 `_monitor_skyvern_task`（M3.8 Skyvern 原生执行路径）在轮询 Skyvern 状态时，只在终态（SUCCESS/FAILED/ABORTED）发布一次 SSE 事件，中间轮询过程不发布任何事件。前端 SSE 订阅者在任务执行的 3-4 分钟内完全无进度反馈。

**修复**：在轮询循环中，非终态时发布 `progress` 事件（每 5 秒一次），包含 `skyvernStatus` 和执行中提示。修复后首个事件延迟降至 5996ms（首次轮询），共收到 43 个事件（42 progress + 1 complete）。

**产品影响**：此修复同时改善了前端用户体验——任务详情页在 Skyvern 执行期间能实时看到进度事件，而非干等终态。

## 5. 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|----------|
| v1.0 | 2026-08-06 | 初始版本，创建性能测试套件与报告模板 |
| v1.1 | 2026-08-06 | 场景3 SSE 推送延迟测试通过（P95=3ms）；修复 2 个 bug：① Python SSE timestamp 时区偏移（8小时）② Skyvern 监控路径不发中间 progress 事件 |
| v1.2 | 2026-08-06 | 补记场景1 单任务延迟基线测试结果（触发 1502ms / 详情 88ms / 端到端 5.4min / 审计 32ms，全部通过） |
| v1.3 | 2026-08-06 | 场景2 改为方案B混合策略：第1组 5 并发真实 Skyvern（Playwright，从 10 降为 5）+ 第2/3组 50/100 并发模拟回调改用 JMeter（jmeter/concurrent-mock.jmx，参数化 concurrent.level）；新增 package.json 脚本 test:concurrent:jmx:50/100 |
| v1.4 | 2026-08-06 | 场景2 并发吞吐量测试通过：第1组 5 并发真实 Skyvern 成功率 100%（P50=299s/P95=357s，6min）；第2组 50 并发 JMeter 0 错误（P50=9ms/P95=67ms，17 req/s）；第3组 100 并发 JMeter 0 错误（P50=8ms/P95=70ms，33.74 req/s）；瓶颈分析：端到端延迟瓶颈在 Skyvern LLM，Java 后端 100 并发 P95<100ms 余量充足 |
| v1.5 | 2026-08-06 | 重跑 100 并发 JMeter 压测确认（引号包裹 -J 参数）：200 请求 0 错误，P50=8ms/P95=70ms/TPS=33.74 req/s，与首次结果一致（P50=7.5ms/P95=70ms/TPS=33.35），数据稳定可信 |

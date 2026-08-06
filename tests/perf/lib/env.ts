/**
 * 性能测试环境变量读取（M9.2）。
 *
 * 所有配置走环境变量，默认值适配本地 docker-compose 全栈。
 */

/** Java 后端地址。 */
export const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

/** Python finance-ai 地址（场景3 SSE 直连 Python + 场景5 LLM 风险判断用）。 */
export const AI_URL = process.env.AI_URL || 'http://localhost:8000';

/** mock 银行页基址（供 Skyvern 容器内访问）。 */
export const MOCK_BANK_BASE = process.env.MOCK_BANK_BASE || 'http://mock-bank';

/** 内部回调 token（模拟 Python 回调时用）。 */
export const INTERNAL_TOKEN = process.env.AI_INTERNAL_TOKEN || 'finrpa-internal-secret';

/** 主组织账号（admin 关联银河证券演示组织，org_admin 角色）。 */
export const PERF_USERNAME = process.env.PERF_USERNAME || 'admin';
export const PERF_PASSWORD = process.env.PERF_PASSWORD || 'admin123';

// region 任务执行
/** 单任务等待终态最大毫秒数（场景1 真实 Skyvern，默认 10 分钟）。 */
export const TASK_WAIT_TIMEOUT = Number(process.env.TASK_WAIT_TIMEOUT || 600_000);

/** 任务终态轮询间隔（毫秒）。 */
export const TASK_POLL_INTERVAL = Number(process.env.TASK_POLL_INTERVAL || 3_000);
// endregion

// region 并发吞吐
/** 真实 Skyvern 并发数（默认 5，避免浏览器内存爆炸）。 */
export const REAL_CONCURRENT = Number(process.env.REAL_CONCURRENT || 5);

/**
 * 模拟并发档位（默认 50/100，用 JMeter 压测，不再走 Playwright）。
 * 保留配置供脚本引用，实际执行见 package.json 的 test:concurrent:jmx:* 脚本。
 */
export const MOCK_CONCURRENT_LEVELS = (process.env.MOCK_CONCURRENT_LEVELS || '50,100')
  .split(',')
  .map((n) => Number(n.trim()))
  .filter((n) => !Number.isNaN(n) && n > 0);

/** 模拟并发单任务执行毫秒数（JMeter Constant Timer，默认 2000ms）。 */
export const MOCK_TASK_DURATION_MS = Number(process.env.MOCK_TASK_DURATION_MS || 2_000);

/** 模拟并发全部任务完成的超时毫秒数（默认 5 分钟）。 */
export const MOCK_CONCURRENT_TIMEOUT = Number(process.env.MOCK_CONCURRENT_TIMEOUT || 300_000);
// endregion

// region SSE 延迟
/** SSE 测试订阅持续时长（毫秒，默认 30 秒）。 */
export const SSE_TEST_DURATION = Number(process.env.SSE_TEST_DURATION || 30_000);

/** SSE 单次最大等待事件超时（毫秒，默认 60 秒）。 */
export const SSE_EVENT_TIMEOUT = Number(process.env.SSE_EVENT_TIMEOUT || 60_000);
// endregion

// region 审计造数
/** PostgreSQL 连接配置（直连造审计数据用）。 */
export const PG_HOST = process.env.PG_HOST || 'localhost';
export const PG_PORT = Number(process.env.PG_PORT || 5432);
export const PG_DB = process.env.PG_DB || 'finrpa';
export const PG_USER = process.env.PG_USER || 'finrpa';
export const PG_PASSWORD = process.env.PG_PASSWORD || 'finrpa';

/** 审计日志造数规模（默认 100 万条）。 */
export const AUDIT_SEED_COUNT = Number(process.env.AUDIT_SEED_COUNT || 1_000_000);

/** 审计造数批次大小（默认 10000 条/批）。 */
export const AUDIT_SEED_BATCH_SIZE = Number(process.env.AUDIT_SEED_BATCH_SIZE || 10_000);

/** 审计查询性能采样次数（默认 100 次取 P50/P95/P99）。 */
export const AUDIT_QUERY_SAMPLES = Number(process.env.AUDIT_QUERY_SAMPLES || 100);
// endregion

// region LLM 成本基线
/** LLM 采样次数（默认 5 次真实调用）。 */
export const LLM_SAMPLE_SIZE = Number(process.env.LLM_SAMPLE_SIZE || 5);

/** LLM 单次调用最大等待毫秒数（默认 30 秒）。 */
export const LLM_CALL_TIMEOUT = Number(process.env.LLM_CALL_TIMEOUT || 30_000);
// endregion

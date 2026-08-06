import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright 配置（M9.2 性能测试）。
 *
 * 测试形态说明：
 * - 性能测试关注延迟、吞吐量、并发稳定性，不是契约正确性。
 * - 测试方式：
 *   - 单任务延迟 / 并发吞吐：10 并发用真实 Skyvern，50/100 用内部回调模拟
 *   - SSE 推送延迟：用 EventSource 连接 Java 透传的 SSE 端点
 *   - 审计查询性能：先 SQL 批量造 100 万条数据，再测分页查询 P50/P95/P99
 *   - LLM 成本基线：触发 5-10 次真实 LLM 风险判断，统计 tokens/cost
 * - 串行执行，避免并发测试相互干扰性能指标。
 *
 * 环境变量：
 * - BACKEND_URL：Java 后端地址（默认 http://localhost:8080）
 * - AI_URL：Python finance-ai 地址（默认 http://localhost:8000）
 * - MOCK_BANK_BASE：mock 银行页基址（默认 http://mock-bank）
 * - AI_INTERNAL_TOKEN：内部回调 token（默认 finrpa-internal-secret）
 * - PERF_USERNAME：主组织账号（默认 admin）
 * - PERF_PASSWORD：主组织账号密码（默认 admin123）
 * - PG_HOST / PG_PORT / PG_DB / PG_USER / PG_PASSWORD：直连 PG 造审计数据用
 * - LLM_SAMPLE_SIZE：LLM 成本基线采样次数（默认 5）
 */
export default defineConfig({
  testDir: './scenarios',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60 * 60 * 1000, // 单用例 60 分钟（场景2 100 并发可能耗时较长）
  expect: { timeout: 60_000 },
  use: {
    baseURL: process.env.BACKEND_URL || 'http://localhost:8080',
    trace: 'retain-on-failure',
    extraHTTPHeaders: { 'Content-Type': 'application/json' },
  },
  projects: [
    {
      name: 'perf',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});

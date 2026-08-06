import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright 配置（M9.1 端到端测试）。
 *
 * 测试形态说明：
 * - 本测试为 API 驱动的全链路验收，不驱动前端 UI（浏览器自动化由 Skyvern 完成）。
 * - 全链路：登录 → 触发工作流 →（审批）→ Skyvern 操作 mock 银行页 → 轮询任务终态 → 验证审计日志 + 大屏统计。
 * - 串行执行，避免并发任务相互干扰审计/大屏统计断言。
 *
 * 环境变量：
 * - BACKEND_URL：Java 后端地址（默认 http://localhost:8080，走 nginx 用 http://localhost）
 * - MOCK_BANK_BASE：mock 银行页基址，供 Skyvern 容器访问（默认 http://mock-bank）
 * - E2E_USERNAME / E2E_PASSWORD：演示账号（默认 admin / admin123）
 * - TASK_WAIT_TIMEOUT：等待单个任务终态的最大毫秒数（默认 600000 = 10 分钟）
 */
export default defineConfig({
  testDir: './scenarios',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 15 * 60 * 1000, // 单用例 15 分钟（Skyvern 执行 + 审批等待，留足余量）
  expect: { timeout: 30_000 },
  use: {
    baseURL: process.env.BACKEND_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    extraHTTPHeaders: { 'Content-Type': 'application/json' },
  },
  projects: [
    {
      name: 'e2e',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});

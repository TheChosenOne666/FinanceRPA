import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright 配置（M9.5 SIT 系统集成测试）。
 *
 * 测试形态说明：
 * - SIT 关注跨服务接口契约一致性 + 数据一致性 + 异常分支覆盖，不驱动前端 UI。
 * - 测试方式：
 *   - 对外 API（/api/tasks、/api/approvals 等）：用真实账号登录调用
 *   - 内部回调 API（/api/internal/*）：用 X-Internal-Token 模拟 Python 回调
 *   - 不依赖 Skyvern 浏览器执行（场景1 除外，复用 E2E 银行流水场景验证契约）
 * - 串行执行，避免并发任务相互干扰数据一致性断言。
 *
 * 环境变量：
 * - BACKEND_URL：Java 后端地址（默认 http://localhost:8080）
 * - MOCK_BANK_BASE：mock 银行页基址（场景1 用，默认 http://mock-bank）
 * - AI_INTERNAL_TOKEN：内部回调 token（默认 finrpa-internal-secret）
 * - SIT_USERNAME_PRIMARY：主组织账号（默认 admin，DemoDataGenerator 关联到银河证券）
 * - SIT_PASSWORD_PRIMARY：主组织账号密码（默认 admin123）
 * - SIT_USERNAME_CROSS：跨组织账号（默认 admin_demo_xcba，星辰银行 org_admin）
 * - SIT_PASSWORD_CROSS：跨组织账号密码（默认 123456）
 * - TASK_WAIT_TIMEOUT：等待任务终态的最大毫秒数（默认 600000 = 10 分钟，仅场景1 用）
 */
export default defineConfig({
  testDir: './scenarios',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 15 * 60 * 1000, // 单用例 15 分钟（场景1 真实执行 Skyvern + 场景4 等审批超时 1 分钟）
  expect: { timeout: 30_000 },
  use: {
    baseURL: process.env.BACKEND_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    extraHTTPHeaders: { 'Content-Type': 'application/json' },
  },
  projects: [
    {
      name: 'sit',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});

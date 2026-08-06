/**
 * 场景2 · 并发任务吞吐量（M9.2）。
 *
 * 混合策略（方案B）：
 * - 第1组：5 并发真实 Skyvern，用 Playwright 验证端到端并发能力（本文件）
 * - 第2/3组：50/100 并发模拟回调，用 JMeter 压测（见 jmeter/concurrent-mock.jmx）
 *
 * 验收标准：5 并发稳定运行（成功率 ≥ 90%）。
 *
 * 测量维度（本文件）：
 * 1. 5 并发真实 Skyvern：成功率、P50/P95/P99 端到端延迟、吞吐量
 *
 * JMeter 压测（第2/3组）：
 * 2. 50 并发模拟回调：成功率、P50/P95/P99 单次回调延迟、吞吐量
 * 3. 100 并发模拟回调：成功率、P50/P95/P99 单次回调延迟、吞吐量
 *
 * 前置条件：docker-compose 全栈已启动。
 */
import { computeStats, formatStats, saveStats } from '../lib/metrics';
import { REAL_CONCURRENT, TASK_WAIT_TIMEOUT } from '../lib/env';
import { expect, measureConcurrent, mockBankUrl, test, waitForTaskTerminal } from '../lib/fixtures';

test.describe('场景2 · 并发任务吞吐量', () => {
  test.describe.configure({ timeout: 60 * 60 * 1000 }); // 60 分钟（5 并发真实 Skyvern 可能较慢）

  // region 5 并发真实 Skyvern
  test('5 并发真实 Skyvern 银行流水下载 - 端到端并发稳定性', async ({ api }) => {
    const concurrency = REAL_CONCURRENT;
    console.log(`[并发-真实] 启动 ${concurrency} 并发真实 Skyvern 任务...`);

    // 1. 查找工作流模板
    const workflow = await api.findWorkflowByName('银行流水下载');
    console.log(`[并发-真实] 工作流模板: id=${workflow.workflowId}`);

    // 2. 并发触发 + 等待终态
    const batchStart = Date.now();
    const samples = await measureConcurrent(concurrency, async (index) => {
      const params = {
        login_url: mockBankUrl(1),
        login_username: 'testuser',
        login_password: 'testpass',
        account_number: `622848001234567${index}`,
        date_start: '2026-07-01',
        date_end: '2026-07-31',
      };
      // 触发 + 等待终态（每个任务独立计时）
      const run = await api.runWorkflow(workflow.workflowId, params);
      const task = await waitForTaskTerminal(api, run.taskId, TASK_WAIT_TIMEOUT);
      if (task.status !== 'SUCCESS') {
        throw new Error(`任务 ${run.taskId} 终态非 SUCCESS: ${task.status} msg=${task.message}`);
      }
      return { taskId: run.taskId, status: task.status };
    });
    const batchTotalMs = Date.now() - batchStart;

    // 3. 统计
    const stats = computeStats(samples, batchTotalMs);
    console.log('\n' + formatStats(`场景2-${concurrency}并发真实Skyvern`, stats));

    // 4. 保存结果
    saveStats(`场景2-${concurrency}并发真实Skyvern`, stats, `perf-concurrent-real-${concurrency}.perf.json`);

    // 5. 断言验收标准：5 并发稳定运行（成功率 ≥ 90%）
    expect(stats.total, '样本数应等于并发数').toBe(concurrency);
    expect(stats.successRate, `${concurrency} 并发成功率应 ≥ 90%（实际 ${(stats.successRate * 100).toFixed(1)}%）`).toBeGreaterThanOrEqual(0.9);

    console.log(`\n[并发-真实] 测试完成 ✓ ${concurrency} 并发，成功率 ${(stats.successRate * 100).toFixed(1)}%，吞吐量 ${stats.throughputPerSec?.toFixed(2)} req/s`);
  });
  // endregion

  // 注：50/100 并发模拟回调已迁移至 JMeter，见 jmeter/concurrent-mock.jmx
  // 执行命令：npm run test:concurrent:jmx:50 / npm run test:concurrent:jmx:100
});

/**
 * 场景2 · 跨行转账核对（high 风险，触发部门审批）。
 *
 * 全链路：触发工作流 → PENDING_APPROVAL → 审批通过 → Skyvern 登录 mock 网银
 *         → 查询转账记录 → 翻页提取 → 审计日志 → 任务 SUCCESS → 大屏统计。
 *
 * 验证点：
 * - high 风险触发审批（approval !== null，路由 department）
 * - 审批通过后任务进入执行
 * - 任务终态 SUCCESS
 * - 审计日志 >= 1 条
 */
import { assertAuditLogs, expect, mockBankUrl, runWorkflowToEnd, test } from '../lib/fixtures';

test.describe('场景2 · 跨行转账核对（high + 审批）', () => {
  test('触发 → 审批 → Skyvern 翻页提取 → 审计', async ({ api }) => {
    // 工作流参数（对齐 WorkflowConstant CROSS_BANK_RECONCILE_TEMPLATE）
    const params = {
      login_url: mockBankUrl(2),
      login_username: 'testuser',
      login_password: 'testpass',
      query_date: '2026-07-15',
    };

    // 触发 + 自动审批 + 等待终态
    const { run, task, approval } = await runWorkflowToEnd(api, '跨行转账核对', params);

    // 断言1：high 风险触发审批
    expect(approval, 'high 风险任务应产生审批单').not.toBeNull();
    expect(approval!.approvalRoute, '审批路由应为 department').toBe('department');
    expect(approval!.status, '审批状态应为 APPROVED').toBe('APPROVED');

    // 断言2：任务终态 SUCCESS
    expect(task.status, `任务终态应为 SUCCESS，实际 ${task.status}，错误：${task.errorMessage ?? '无'}`).toBe('SUCCESS');

    // 断言3：审计日志完整
    const logs = await assertAuditLogs(api, run.taskId, 1);
    console.log(`场景2 审计日志 ${logs.length} 条，首条 actionType=${logs[0].actionType}`);

    // 断言4：大屏概览
    const overview = await api.getOverview();
    expect(Number(overview.totalTasks), '大屏 totalTasks 应 > 0').toBeGreaterThan(0);
    console.log(`场景2 大屏概览：total=${overview.totalTasks} success=${overview.successTasks}`);
  });
});

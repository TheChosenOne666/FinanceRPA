/**
 * 场景5 · 理赔审核提交（insurance / high 风险，触发部门审批）。
 *
 * 全链路：触发工作流 → 审批通过 → Skyvern 登录 mock 保险系统
 *         → 下载理赔材料 → 填写理赔编号/金额/审核意见 → 审计日志 → 任务 SUCCESS → 大屏统计。
 *
 * 验证点：
 * - high 风险触发部门审批
 * - 任务终态 SUCCESS
 * - 审计日志 >= 1 条
 */
import { assertAuditLogs, expect, mockBankUrl, runWorkflowToEnd, test } from '../lib/fixtures';

test.describe('场景5 · 理赔审核提交（high + 审批）', () => {
  test('触发 → 审批 → Skyvern 下载材料+填写 → 审计', async ({ api }) => {
    // 工作流参数（对齐 WorkflowConstant CLAIM_REVIEW_TEMPLATE）
    const params = {
      login_url: mockBankUrl(5),
      login_username: 'testuser',
      login_password: 'testpass',
      claim_number: 'CL20260700001',
      claim_amount: '50000',
      reviewer_comment: '材料齐全，同意理赔',
    };

    // 触发 + 自动审批 + 等待终态
    const { run, task, approval } = await runWorkflowToEnd(api, '理赔审核提交', params);

    // 断言1：high 风险触发部门审批
    expect(approval, 'high 风险任务应产生审批单').not.toBeNull();
    expect(approval!.approvalRoute, '审批路由应为 department').toBe('department');

    // 断言2：任务终态 SUCCESS
    expect(task.status, `任务终态应为 SUCCESS，实际 ${task.status}，错误：${task.errorMessage ?? '无'}`).toBe('SUCCESS');

    // 断言3：审计日志完整
    const logs = await assertAuditLogs(api, run.taskId, 1);
    console.log(`场景5 审计日志 ${logs.length} 条，首条 actionType=${logs[0].actionType}`);

    // 断言4：大屏概览
    const overview = await api.getOverview();
    expect(Number(overview.totalTasks), '大屏 totalTasks 应 > 0').toBeGreaterThan(0);
    console.log(`场景5 大屏概览：total=${overview.totalTasks} success=${overview.successTasks}`);
  });
});

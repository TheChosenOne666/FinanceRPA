/**
 * 场景3 · 对公贷款放款（critical 风险，触发合规审批）。
 *
 * 全链路：触发工作流 → PENDING_APPROVAL → 合规审批通过 → Skyvern 登录 mock 贷款系统
 *         → 填写放款表单 → 搜索借款人并选择 → 审计日志 → 任务 SUCCESS → 大屏统计。
 *
 * 验证点：
 * - critical 风险触发合规审批（approval !== null，路由 compliance）
 * - 审批通过后任务进入执行
 * - 任务终态 SUCCESS
 * - 审计日志 >= 1 条
 */
import { assertAuditLogs, expect, mockBankUrl, runWorkflowToEnd, test } from '../lib/fixtures';

test.describe('场景3 · 对公贷款放款（critical + 合规审批）', () => {
  test('触发 → 合规审批 → Skyvern 搜索选择 → 审计', async ({ api }) => {
    // 工作流参数（对齐 WorkflowConstant CORPORATE_LOAN_TEMPLATE）
    // search_target 必须匹配 mock-bank/scenario3.html 搜索结果中的目标项
    // 风险关键词：loan_account 同时含"冻结"(high_risk_operation) 和"身份证号"(sensitive_data)
    // 两者同时命中 → critical 风险 → 触发合规审批
    // 注意：borrower_name 保持"测试科技"以保证搜索能匹配 mock 结果
    const params = {
      login_url: mockBankUrl(3),
      login_username: 'testuser',
      login_password: 'testpass',
      loan_amount: '5000000',
      loan_account: '冻结身份证号LD2026070001',
      borrower_name: '测试科技',
      search_target: '测试科技有限公司',
    };

    // 触发 + 自动审批 + 等待终态
    const { run, task, approval } = await runWorkflowToEnd(api, '对公贷款放款', params);

    // 断言1：critical 风险触发合规审批
    expect(approval, 'critical 风险任务应产生审批单').not.toBeNull();
    expect(approval!.approvalRoute, '审批路由应为 compliance').toBe('compliance');
    expect(approval!.status, '审批状态应为 APPROVED').toBe('APPROVED');

    // 断言2：任务终态 SUCCESS
    expect(task.status, `任务终态应为 SUCCESS，实际 ${task.status}，错误：${task.errorMessage ?? '无'}`).toBe('SUCCESS');

    // 断言3：审计日志完整
    const logs = await assertAuditLogs(api, run.taskId, 1);
    console.log(`场景3 审计日志 ${logs.length} 条，首条 actionType=${logs[0].actionType}`);

    // 断言4：大屏概览
    const overview = await api.getOverview();
    expect(Number(overview.totalTasks), '大屏 totalTasks 应 > 0').toBeGreaterThan(0);
    console.log(`场景3 大屏概览：total=${overview.totalTasks} success=${overview.successTasks}`);
  });
});

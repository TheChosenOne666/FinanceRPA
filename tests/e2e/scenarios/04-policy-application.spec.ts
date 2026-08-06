/**
 * 场景4 · 保单申请填写（insurance / high 风险，触发部门审批）。
 *
 * 全链路：触发工作流 → 审批通过 → Skyvern 登录 mock 保险系统
 *         → 填写投保人/被保人/产品/保额 → 审计日志 → 任务 SUCCESS → 大屏统计。
 *
 * 验证点：
 * - high 风险触发部门审批
 * - 任务终态 SUCCESS
 * - 审计日志 >= 1 条
 */
import { assertAuditLogs, expect, mockBankUrl, runWorkflowToEnd, test } from '../lib/fixtures';

test.describe('场景4 · 保单申请填写（high + 审批）', () => {
  test('触发 → 审批 → Skyvern 填写保单 → 审计', async ({ api }) => {
    // 工作流参数（对齐 WorkflowConstant POLICY_APPLICATION_TEMPLATE）
    // 风险关键词：insured_name 含"退保"(insurance high_risk_operation) → 预筛 high → 触发部门审批
    // 注意：LLM 二次判断时已排除 steps 字段（含 field_mapping key "身份证号"），
    // 避免误判为 critical。仅命中 high_risk_operation → high → department 路由
    const params = {
      login_url: mockBankUrl(4),
      login_username: 'testuser',
      login_password: 'testpass',
      applicant_name: '张三',
      applicant_id: '110101199001011234',
      insured_name: '退保李四',
      product_name: '安心保2026',
      insured_amount: '1000000',
    };

    // 触发 + 自动审批 + 等待终态
    const { run, task, approval } = await runWorkflowToEnd(api, '保单申请填写', params);

    // 断言1：high 风险触发部门审批
    expect(approval, 'high 风险任务应产生审批单').not.toBeNull();
    expect(approval!.approvalRoute, '审批路由应为 department').toBe('department');

    // 断言2：任务终态 SUCCESS
    expect(task.status, `任务终态应为 SUCCESS，实际 ${task.status}，错误：${task.errorMessage ?? '无'}`).toBe('SUCCESS');

    // 断言3：审计日志完整
    const logs = await assertAuditLogs(api, run.taskId, 1);
    console.log(`场景4 审计日志 ${logs.length} 条，首条 actionType=${logs[0].actionType}`);

    // 断言4：大屏概览
    const overview = await api.getOverview();
    expect(Number(overview.totalTasks), '大屏 totalTasks 应 > 0').toBeGreaterThan(0);
    console.log(`场景4 大屏概览：total=${overview.totalTasks} success=${overview.successTasks}`);
  });
});

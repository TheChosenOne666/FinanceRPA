/**
 * 场景6 · 委托下单（securities / high 风险，触发部门审批）。
 *
 * 全链路：触发工作流 → 审批通过 → Skyvern 登录 mock 证券系统
 *         → 填写股票代码/交易类型/数量/价格 → 风险揭示确认 → 审计日志 → 任务 SUCCESS → 大屏统计。
 *
 * 验证点：
 * - high 风险触发部门审批
 * - 任务终态 SUCCESS
 * - 审计日志 >= 1 条
 * - 6 个场景全部执行后，大屏 successTasks > 0
 */
import { assertAuditLogs, expect, mockBankUrl, runWorkflowToEnd, test } from '../lib/fixtures';

test.describe('场景6 · 委托下单（high + 审批）', () => {
  test('触发 → 审批 → Skyvern 填写委托单 → 审计 → 大屏统计', async ({ api }) => {
    // 工作流参数（对齐 WorkflowConstant SECURITIES_ORDER_TEMPLATE）
    // trade_type 取值 "买入" 或 "卖出"，对齐 mock-bank/scenario6.html 下拉选项
    // 风险关键词："买入"(securities high_risk_operation) → 预筛 high → 触发部门审批
    const params = {
      login_url: mockBankUrl(6),
      login_username: 'testuser',
      login_password: 'testpass',
      stock_code: '600000',
      trade_type: '买入',
      quantity: '1000',
      price: '12.50',
    };

    // 触发 + 自动审批 + 等待终态
    const { run, task, approval } = await runWorkflowToEnd(api, '委托下单', params);

    // 断言1：high 风险触发部门审批
    expect(approval, 'high 风险任务应产生审批单').not.toBeNull();
    expect(approval!.approvalRoute, '审批路由应为 department').toBe('department');

    // 断言2：任务终态 SUCCESS
    expect(task.status, `任务终态应为 SUCCESS，实际 ${task.status}，错误：${task.errorMessage ?? '无'}`).toBe('SUCCESS');

    // 断言3：审计日志完整
    const logs = await assertAuditLogs(api, run.taskId, 1);
    console.log(`场景6 审计日志 ${logs.length} 条，首条 actionType=${logs[0].actionType}`);

    // 断言4：6 个场景全部执行后，大屏统计应反映成功任务
    const overview = await api.getOverview();
    expect(Number(overview.totalTasks), '大屏 totalTasks 应 >= 1').toBeGreaterThanOrEqual(1);
    expect(Number(overview.successTasks), '大屏 successTasks 应 >= 1').toBeGreaterThanOrEqual(1);
    console.log(`场景6 大屏概览：total=${overview.totalTasks} success=${overview.successTasks} failed=${overview.failedTasks} successRate=${overview.successRate}`);
  });
});

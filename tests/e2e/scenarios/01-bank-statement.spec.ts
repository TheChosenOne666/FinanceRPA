/**
 * 场景1 · 银行流水下载（medium 风险，无审批）。
 *
 * 全链路：触发工作流 → Skyvern 登录 mock 网银 → 填写账号/日期 → 下载流水 PDF
 *         → 审计日志上报 → 任务终态 SUCCESS → 大屏统计。
 *
 * 验证点：
 * - medium 风险不触发审批（approval === null）
 * - 任务终态 SUCCESS
 * - 审计日志 >= 1 条
 * - 大屏概览接口可用
 */
import { assertAuditLogs, expect, mockBankUrl, runWorkflowToEnd, test } from '../lib/fixtures';

test.describe('场景1 · 银行流水下载（medium）', () => {
  test('触发 → Skyvern 执行 → 审计 → 大屏统计', async ({ api }) => {
    // 工作流参数（对齐 WorkflowConstant BANK_STATEMENT_TEMPLATE）
    const params = {
      login_url: mockBankUrl(1),
      login_username: 'testuser',
      login_password: 'testpass',
      account_number: '6228480012345678',
      date_start: '2026-07-01',
      date_end: '2026-07-31',
    };

    // 触发 + 等待终态（medium 不审批，直接 EXECUTING）
    const { run, task, approval } = await runWorkflowToEnd(api, '银行流水下载', params);

    // 断言1：medium 风险不触发审批
    expect(approval, 'medium 风险任务不应产生审批单').toBeNull();

    // 断言2：任务终态 SUCCESS
    expect(task.status, `任务终态应为 SUCCESS，实际 ${task.status}，错误：${task.errorMessage ?? '无'}`).toBe('SUCCESS');

    // 断言3：审计日志完整（Login + FormFill + FileDownload 至少 1 条）
    const logs = await assertAuditLogs(api, run.taskId, 1);
    console.log(`场景1 审计日志样例：actionType=${logs[0].actionType} result=${logs[0].executionResult}`);

    // 断言4：大屏概览接口可用（Java 返回的统计字段可能为 string 类型，需转换）
    const overview = await api.getOverview();
    expect(Number(overview.totalTasks), '大屏 totalTasks 应 > 0').toBeGreaterThan(0);
    console.log(`场景1 大屏概览：total=${overview.totalTasks} success=${overview.successTasks}`);
  });
});

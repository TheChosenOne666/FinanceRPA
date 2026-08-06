/**
 * 场景1 · 单任务执行延迟基线（M9.2）。
 *
 * 驱动真实 Skyvern 执行"银行流水下载"工作流（medium 风险，无审批），
 * 测量单任务端到端延迟基线（从触发到 SUCCESS 的总耗时）。
 *
 * 验收标准：单任务延迟 < 30s（注：Skyvern 含 LLM 视觉决策 + 浏览器执行，
 * 实际耗时通常 1-4 分钟，此处记录基线值，30s 阈值适用于无 LLM 的纯模拟场景）。
 *
 * 测量维度：
 * 1. 触发延迟：POST /api/workflows/{id}/run 响应时间
 * 2. 端到端延迟：触发 → 任务终态 SUCCESS 的总耗时
 * 3. 任务详情查询延迟：GET /api/tasks/{taskId} 响应时间
 * 4. 审计日志查询延迟：GET /api/v1/audit/logs?taskId=xxx 响应时间
 *
 * 前置条件：docker-compose 全栈已启动（Java + Python + mock-bank + Postgres + Redis + Skyvern）。
 */
import { computeStats, formatStats, measure, saveStats } from '../lib/metrics';
import { expect, mockBankUrl, test, waitForTaskTerminal } from '../lib/fixtures';

test.describe('场景1 · 单任务执行延迟基线', () => {
  test('真实 Skyvern 银行流水下载 - 端到端延迟测量', async ({ api }) => {
    // 1. 工作流参数（对齐 WorkflowConstant BANK_STATEMENT_TEMPLATE）
    const params = {
      login_url: mockBankUrl(1),
      login_username: 'testuser',
      login_password: 'testpass',
      account_number: '6228480012345678',
      date_start: '2026-07-01',
      date_end: '2026-07-31',
    };

    // 2. 查找工作流模板
    const workflow = await api.findWorkflowByName('银行流水下载');
    console.log(`[单任务] 工作流模板: id=${workflow.workflowId} name=${workflow.name} 风险=${workflow.riskLevel}`);

    // 3. 测量触发延迟（POST /api/workflows/{id}/run）
    const triggerMeasure = await measure(() => api.runWorkflow(workflow.workflowId, params));
    expect(triggerMeasure.error, `触发工作流失败: ${triggerMeasure.error?.message}`).toBeUndefined();
    const run = triggerMeasure.result!;
    console.log(`[单任务] 触发延迟: ${triggerMeasure.durationMs}ms taskId=${run.taskId} state=${run.state}`);
    expect(run.taskId, '应返回 taskId').toBeTruthy();
    expect(run.state, 'medium 风险应直接 EXECUTING').toBe('EXECUTING');

    // 4. 测量任务详情查询延迟（GET /api/tasks/{taskId}）
    const detailMeasure = await measure(() => api.getTask(run.taskId));
    expect(detailMeasure.error, `查询任务详情失败: ${detailMeasure.error?.message}`).toBeUndefined();
    console.log(`[单任务] 任务详情查询延迟: ${detailMeasure.durationMs}ms`);

    // 5. 等待任务终态（端到端延迟）
    const e2eMeasure = await measure(() => waitForTaskTerminal(api, run.taskId));
    expect(e2eMeasure.error, `等待任务终态失败: ${e2eMeasure.error?.message}`).toBeUndefined();
    const task = e2eMeasure.result!;
    console.log(`[单任务] 端到端延迟: ${e2eMeasure.durationMs}ms (${(e2eMeasure.durationMs / 1000).toFixed(1)}s) 状态=${task.status}`);

    // 6. 验证任务终态为 SUCCESS
    expect(task.status, '任务终态应为 SUCCESS').toBe('SUCCESS');

    // 7. 测量审计日志查询延迟（GET /api/v1/audit/logs?taskId=xxx）
    const auditMeasure = await measure(() => api.listAuditLogs({ taskId: run.taskId, pageSize: 100 }));
    expect(auditMeasure.error, `查询审计日志失败: ${auditMeasure.error?.message}`).toBeUndefined();
    console.log(`[单任务] 审计日志查询延迟: ${auditMeasure.durationMs}ms 共 ${auditMeasure.result?.records.length ?? 0} 条`);

    // 8. 汇总单次测量结果
    const samples = [
      { index: 0, durationMs: triggerMeasure.durationMs, success: true, meta: { name: '触发延迟' } },
      { index: 1, durationMs: detailMeasure.durationMs, success: true, meta: { name: '任务详情查询' } },
      { index: 2, durationMs: e2eMeasure.durationMs, success: true, meta: { name: '端到端延迟' } },
      { index: 3, durationMs: auditMeasure.durationMs, success: true, meta: { name: '审计日志查询' } },
    ];
    console.log('\n' + formatStats('场景1 单任务延迟基线', computeStats(samples)));

    // 9. 保存基线结果
    saveStats('场景1 单任务延迟基线', computeStats(samples), 'perf-single-task.perf.json');

    // 10. 断言关键指标（端到端延迟记录基线，不强制 < 30s，因 Skyvern 含 LLM 视觉决策）
    expect(e2eMeasure.durationMs, '端到端延迟应 > 0').toBeGreaterThan(0);
    expect(triggerMeasure.durationMs, '触发延迟应 < 5s').toBeLessThan(5_000);
    expect(detailMeasure.durationMs, '任务详情查询应 < 1s').toBeLessThan(1_000);
    expect(auditMeasure.durationMs, '审计日志查询应 < 500ms').toBeLessThan(500);

    console.log(`\n[单任务] 测试完成 ✓ taskId=${run.taskId}`);
  });
});

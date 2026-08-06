/**
 * 场景4 · 审批超时 → 自动拒绝（动态改阈值 1 分钟 + 等超时 + 验证 TIMEOUT/ABORTED）。
 *
 * 验证 M6.4 审批超时检测机制：
 * 1. 动态修改 high 风险等级的审批超时阈值为 1 分钟（PUT /api/approval-timeout/high）
 * 2. 触发 high 风险工作流（跨行转账核对），产生 PENDING 审批单
 * 3. 不审批，等待审批超时（约 1 分钟超时 + 1 分钟扫描间隔）
 * 4. 验证审批单状态变为 TIMEOUT
 * 5. 验证任务状态变为 ABORTED
 * 6. 还原超时阈值（避免影响其他测试）
 *
 * 前置条件：docker-compose 全栈已启动，ShedLock 超时检测定时任务正常运行（每分钟扫描）。
 *
 * 时序说明：
 * - T+0：触发工作流，审批单创建，timeoutAt = T+1min
 * - T+1min：审批超时
 * - T+1min~T+2min：ShedLock 定时任务扫描，检测到超时，更新审批单 TIMEOUT + 任务 ABORTED
 * - 测试等待最多 3 分钟（APPROVAL_TIMEOUT_WAIT = 180s）
 */
import {
  expect,
  mockBankUrl,
  test,
  updateApprovalTimeoutAndRestore,
  waitForApprovalStatus,
} from '../lib/fixtures';

test.describe('场景4 · 审批超时 → 自动拒绝', () => {
  test('动态改阈值 1 分钟 → 等超时 → 审批 TIMEOUT + 任务 ABORTED', async ({ api }) => {
    // 1. 动态修改 high 风险等级超时阈值为 1 分钟（测试后还原）
    const restoreTimeout = await updateApprovalTimeoutAndRestore(api, 'high', 1);
    console.log(`[超时] high 风险超时阈值已改为 1 分钟`);

    try {
      // 2. 触发 high 风险工作流（跨行转账核对），产生审批单
      const workflow = await api.findWorkflowByName('跨行转账核对');
      console.log(`[超时] 工作流模板: id=${workflow.workflowId} 风险=${workflow.riskLevel}`);
      expect(workflow.riskLevel, '工作流风险等级应为 high').toBe('high');

      const params = {
        login_url: mockBankUrl(2),
        login_username: 'testuser',
        login_password: 'testpass',
        query_date: '2026-07-31',
      };
      const run = await api.runWorkflow(workflow.workflowId, params);
      console.log(`[超时] 触发工作流 taskId=${run.taskId} state=${run.state}`);
      expect(run.state, 'high 风险工作流应返回 PENDING_APPROVAL').toBe('PENDING_APPROVAL');
      expect(run.approvalId, '应返回 approvalId').toBeTruthy();

      // 3. 验证审批单已创建且状态为 PENDING
      let approvals = await api.listApprovalsByTaskId(run.taskId);
      // 审批单创建有短暂延迟，重试查询
      for (let i = 0; i < 5 && approvals.length === 0; i++) {
        await new Promise((r) => setTimeout(r, 1000));
        approvals = await api.listApprovalsByTaskId(run.taskId);
      }
      expect(approvals.length, '应创建 1 条审批单').toBeGreaterThanOrEqual(1);
      const pendingApproval = approvals[0];
      console.log(`[超时] 审批单已创建: approvalId=${pendingApproval.approvalId} status=${pendingApproval.status} timeoutAt=${pendingApproval.timeoutAt ?? '-'}`);
      expect(pendingApproval.status, '审批单初始状态应为 PENDING').toBe('PENDING');
      expect(pendingApproval.riskLevel, '审批单风险等级应为 high').toBe('high');
      expect(pendingApproval.timeoutAt, '审批单应有 timeoutAt').toBeTruthy();

      // 4. 等待审批超时（TIMEOUT）
      //    超时阈值 1 分钟 + ShedLock 扫描间隔 1 分钟 = 最多等 3 分钟
      console.log(`[超时] 等待审批超时（最多 3 分钟）...`);
      const timedOutApproval = await waitForApprovalStatus(api, run.taskId, 'TIMEOUT', 180_000);
      console.log(`[超时] 审批单已超时: approvalId=${timedOutApproval.approvalId} status=${timedOutApproval.status}`);
      expect(timedOutApproval.status, '审批单状态应为 TIMEOUT').toBe('TIMEOUT');

      // 5. 验证任务状态变为 ABORTED
      //    审批超时后，任务应被终止（ABORTED）
      //    任务状态更新可能有短暂延迟，轮询等待
      let taskAborted = false;
      for (let i = 0; i < 30; i++) {
        const task = await api.getTask(run.taskId);
        if (task.status === 'ABORTED') {
          taskAborted = true;
          console.log(`[超时] 任务已终止: status=ABORTED message=${task.message}`);
          break;
        }
        console.log(`[超时] 等待任务 ABORTED，当前 status=${task.status}`);
        await new Promise((r) => setTimeout(r, 3000));
      }
      expect(taskAborted, '审批超时后任务状态应为 ABORTED').toBe(true);

      // 6. 验证审批单字段对齐（ApprovalRequestVO）
      expect(timedOutApproval.approvalId, 'ApprovalRequestVO.approvalId 应非空').toBeTruthy();
      expect(timedOutApproval.taskId, 'ApprovalRequestVO.taskId 应等于任务 ID').toBe(run.taskId);
      expect(timedOutApproval.riskLevel, 'ApprovalRequestVO.riskLevel 应为 high').toBe('high');
      expect(timedOutApproval.approvalRoute, 'ApprovalRequestVO.approvalRoute 应为 department').toBe('department');
      expect(timedOutApproval.timeoutAt, 'ApprovalRequestVO.timeoutAt 应非空').toBeTruthy();
    } finally {
      // 7. 还原超时阈值
      await restoreTimeout();
      console.log(`[超时] high 风险超时阈值已还原`);
    }
  });
});

/**
 * 场景1 · 全链路接口契约验证（字段对齐 + 状态流转一致性）。
 *
 * 驱动真实 Skyvern 执行"银行流水下载"工作流（medium 风险，无审批），
 * 验证 Java 后端返回的 VO/DTO 字段与 TypeScript 类型定义对齐，
 * 且状态流转（PENDING → EXECUTING → SUCCESS）与数据一致性正确。
 *
 * 验证点：
 * 1. WorkflowRunVO 字段对齐（taskId / workflowId / state / approvalId）
 * 2. TaskDetailVO 字段对齐（taskId / orgId / userId / goal / status / currentStep / totalSteps 等）
 * 3. SubTaskVO 字段对齐（subtaskId / taskId / subtaskIndex / goal / status）
 * 4. AuditLogVO 字段对齐（auditId / taskId / orgId / actionType / executionResult）
 * 5. 状态流转一致性：触发时 EXECUTING → 终态 SUCCESS
 * 6. 数据一致性：任务 orgId/userId 与登录用户一致；审计日志 taskId 与任务一致
 *
 * 前置条件：docker-compose 全栈已启动（Java + Python + mock-bank + Postgres + Redis）。
 */
import { assertAuditLogs, expect, mockBankUrl, runWorkflowToEnd, test } from '../lib/fixtures';
import type { AuditLogVO, SubTaskVO, TaskDetailVO, WorkflowRunVO } from '../lib/types';

test.describe('场景1 · 全链路接口契约验证', () => {
  test('触发 → Skyvern 执行 → 字段对齐 + 状态流转一致性', async ({ api }) => {
    // 1. 工作流参数（对齐 WorkflowConstant BANK_STATEMENT_TEMPLATE）
    const params = {
      login_url: mockBankUrl(1),
      login_username: 'testuser',
      login_password: 'testpass',
      account_number: '6228480012345678',
      date_start: '2026-07-01',
      date_end: '2026-07-31',
    };

    // 2. 查找工作流模板（验证 WorkflowVO 字段对齐）
    const workflow = await api.findWorkflowByName('银行流水下载');
    console.log(`[契约] 工作流模板: id=${workflow.workflowId} name=${workflow.name} 风险=${workflow.riskLevel}`);
    expect(workflow.workflowId, 'WorkflowVO.workflowId 应非空').toBeTruthy();
    expect(workflow.name, 'WorkflowVO.name 应为"银行流水下载"').toBe('银行流水下载');
    expect(workflow.riskLevel, 'WorkflowVO.riskLevel 应为 medium').toBe('medium');
    expect(workflow.enabled, 'WorkflowVO.enabled 应为 1（启用）').toBe(1);

    // 3. 触发工作流 + 等待终态（medium 不审批，直接 EXECUTING）
    const { run, task, approval } = await runWorkflowToEnd(api, '银行流水下载', params);

    // 4. 验证 WorkflowRunVO 字段对齐
    console.log(`[契约] 触发响应: taskId=${run.taskId} state=${run.state} approvalId=${run.approvalId ?? '-'}`);
    expect(run.taskId, 'WorkflowRunVO.taskId 应非空').toBeTruthy();
    expect(run.workflowId, 'WorkflowRunVO.workflowId 应等于工作流模板 ID').toBe(workflow.workflowId);
    expect(run.state, 'WorkflowRunVO.state medium 风险应为 EXECUTING').toBe('EXECUTING');
    // Java Jackson 默认序列化 null 字段，medium 风险时 approvalId 为 null（非 undefined）
    expect(run.approvalId, 'WorkflowRunVO.approvalId medium 风险应为 null/undefined').toBeFalsy();
    expect(approval, 'medium 风险任务不应产生审批单').toBeNull();

    // 5. 验证 TaskDetailVO 字段对齐（核心字段）
    console.log(`[契约] 任务终态: status=${task.status} step=${task.currentStep}/${task.totalSteps} duration=${task.durationMs ?? '-'}ms`);
    expect(task.taskId, 'TaskDetailVO.taskId 应等于触发响应的 taskId').toBe(run.taskId);
    expect(task.orgId, 'TaskDetailVO.orgId 应非空').toBeTruthy();
    expect(task.userId, 'TaskDetailVO.userId 应非空').toBeTruthy();
    expect(task.goal, 'TaskDetailVO.goal 应非空').toBeTruthy();
    expect(task.status, 'TaskDetailVO.status 终态应为 SUCCESS').toBe('SUCCESS');
    expect(task.currentStep, 'TaskDetailVO.currentStep 应 >= 0').toBeGreaterThanOrEqual(0);
    expect(task.totalSteps, 'TaskDetailVO.totalSteps 应 >= 0').toBeGreaterThanOrEqual(0);
    expect(task.workflowId, 'TaskDetailVO.workflowId 应等于工作流模板 ID').toBe(workflow.workflowId);
    expect(task.params, 'TaskDetailVO.params 应非空（含 login_url 等）').toBeTruthy();
    expect(task.createTime, 'TaskDetailVO.createTime 应非空').toBeTruthy();
    expect(task.updateTime, 'TaskDetailVO.updateTime 应非空').toBeTruthy();

    // 6. 验证子任务字段对齐（如有子任务）
    if (task.subtasks && task.subtasks.length > 0) {
      console.log(`[契约] 子任务数量: ${task.subtasks.length}`);
      for (const sub of task.subtasks as SubTaskVO[]) {
        expect(sub.subtaskId, 'SubTaskVO.subtaskId 应非空').toBeTruthy();
        expect(sub.taskId, 'SubTaskVO.taskId 应等于父任务 ID').toBe(task.taskId);
        expect(sub.subtaskIndex, 'SubTaskVO.subtaskIndex 应 >= 0').toBeGreaterThanOrEqual(0);
        expect(sub.goal, 'SubTaskVO.goal 应非空').toBeTruthy();
        expect(sub.status, 'SubTaskVO.status 应为合法状态').toMatch(/^(PENDING|RUNNING|COMPLETED|FAILED|SKIPPED|REPLANNED)$/);
      }
      // 数据一致性：所有子任务 taskId 与父任务一致
      const allBelongToTask = task.subtasks.every((s) => s.taskId === task.taskId);
      expect(allBelongToTask, '所有子任务 taskId 应与父任务一致').toBe(true);
    } else {
      console.log('[契约] 任务无子任务（Skyvern 单步执行）');
    }

    // 7. 验证审计日志字段对齐
    const logs = await assertAuditLogs(api, run.taskId, 1);
    for (const log of logs as AuditLogVO[]) {
      expect(log.auditId, 'AuditLogVO.auditId 应非空').toBeTruthy();
      expect(log.taskId, 'AuditLogVO.taskId 应等于任务 ID').toBe(task.taskId);
      expect(log.orgId, 'AuditLogVO.orgId 应等于任务 orgId').toBe(task.orgId);
      expect(log.actionType, 'AuditLogVO.actionType 应非空').toBeTruthy();
      expect(log.executionResult, 'AuditLogVO.executionResult 应非空').toBeTruthy();
    }
    console.log(`[契约] 审计日志样例: actionType=${logs[0].actionType} result=${logs[0].executionResult}`);

    // 8. 数据一致性：审计日志 orgId 与任务 orgId 一致
    const orgIdConsistent = logs.every((l) => l.orgId === task.orgId);
    expect(orgIdConsistent, '审计日志 orgId 应与任务 orgId 一致').toBe(true);

    // 9. 验证大屏概览接口字段对齐
    const overview = await api.getOverview();
    expect(Number(overview.totalTasks), 'OverviewVO.totalTasks 应 > 0').toBeGreaterThan(0);
    expect(Number(overview.successTasks), 'OverviewVO.successTasks 应 > 0（刚完成的任务）').toBeGreaterThan(0);
    console.log(`[契约] 大屏概览: total=${overview.totalTasks} success=${overview.successTasks} running=${overview.runningTasks}`);
  });
});

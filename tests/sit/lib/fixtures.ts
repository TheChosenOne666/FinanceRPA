/**
 * SIT 测试 fixture 与辅助函数（M9.5）。
 *
 * 提供：
 * - test fixture：已认证的 ApiClient（默认登录 admin 账号）
 * - mockBankUrl：构建 mock 银行页 login_url（场景1 用）
 * - waitForTaskTerminal：轮询任务直到终态
 * - approveTask：按 taskId 审批通过
 * - runWorkflowToEnd：触发工作流 + 自动审批 + 等待终态
 * - waitForApprovalTimeout：轮询审批单直到 TIMEOUT 或超时（场景4 用）
 * - updateApprovalTimeoutAndRestore：动态修改审批超时阈值，测试后还原
 * - triggerTaskViaInternalCallback：通过内部回调模拟 Python 触发任务（不依赖 Skyvern）
 */
import { test as base, expect } from '@playwright/test';
import { ApiClient } from './api';
import {
  APPROVAL_TIMEOUT_POLL_INTERVAL,
  APPROVAL_TIMEOUT_WAIT,
  MOCK_BANK_BASE,
  SIT_PASSWORD_PRIMARY,
  SIT_USERNAME_PRIMARY,
  TASK_POLL_INTERVAL,
  TASK_WAIT_TIMEOUT,
} from './env';
import type {
  ApprovalRequestVO,
  ApprovalTimeoutConfigVO,
  AuditLogVO,
  TaskDetailVO,
  TaskStatus,
  WorkflowRunVO,
} from './types';

/** 任务终态集合（到达后不再变化）。 */
const TERMINAL_STATES: TaskStatus[] = ['SUCCESS', 'FAILED', 'NEEDS_HUMAN', 'ABORTED'];

// region test fixture

/**
 * SIT test fixture：提供已登录的 ApiClient。
 *
 * 默认登录 admin 账号（关联银河证券演示组织，org_admin 角色）。
 * 场景5 跨组织隔离测试可调用 client.login(crossUsername, crossPassword) 切换。
 *
 * 用法：
 * ```ts
 * import { test, expect } from '../lib/fixtures';
 * test('场景1', async ({ api }) => { ... });
 * ```
 */
export const test = base.extend<{ api: ApiClient }>({
  api: async ({}, use) => {
    const client = new ApiClient();
    const resp = await client.login(SIT_USERNAME_PRIMARY, SIT_PASSWORD_PRIMARY);
    client.setToken(resp.accessToken);
    await use(client);
  },
});

export { expect };

// endregion

// region 辅助函数

/**
 * 构建 mock 银行页 login_url（供 Skyvern 容器内访问，场景1 用）。
 *
 * @param scenario 场景序号 1-6
 * @returns 如 http://mock-bank/scenario1.html
 */
export function mockBankUrl(scenario: number): string {
  return `${MOCK_BANK_BASE}/scenario${scenario}.html`;
}

/**
 * 等待任务到达终态（SUCCESS / FAILED / NEEDS_HUMAN / ABORTED）。
 *
 * 轮询 GET /api/tasks/{taskId}，直到终态或超时。
 * Skyvern 视觉决策 + 浏览器执行较慢，默认超时 10 分钟（TASK_WAIT_TIMEOUT）。
 *
 * @param api 已认证的 ApiClient
 * @param taskId 任务 ID
 * @param timeoutMs 超时毫秒（默认 TASK_WAIT_TIMEOUT）
 * @returns 终态任务详情（含子任务）
 */
export async function waitForTaskTerminal(
  api: ApiClient,
  taskId: string,
  timeoutMs: number = TASK_WAIT_TIMEOUT,
): Promise<TaskDetailVO> {
  const start = Date.now();
  let lastStatus: TaskStatus = 'PENDING';
  while (Date.now() - start < timeoutMs) {
    const task = await api.getTask(taskId);
    lastStatus = task.status;
    if (TERMINAL_STATES.includes(task.status)) {
      return task;
    }
    console.log(`[waitForTaskTerminal] task=${taskId} status=${task.status} step=${task.currentStep}/${task.totalSteps} msg=${task.message}`);
    await new Promise((r) => setTimeout(r, TASK_POLL_INTERVAL));
  }
  throw new Error(`任务 ${taskId} 等待终态超时（${timeoutMs}ms），最后状态：${lastStatus}`);
}

/**
 * 审批通过任务：按 taskId 查审批单，对第一个 PENDING 审批单调用 approve。
 *
 * @param api 已认证的 ApiClient
 * @param taskId 任务 ID
 * @param reason 审批理由（默认 "SIT 自动审批通过"）
 * @returns 审批后的审批单，无 PENDING 审批单返回 null
 */
export async function approveTask(
  api: ApiClient,
  taskId: string,
  reason: string = 'SIT 自动审批通过',
): Promise<ApprovalRequestVO | null> {
  const approvals = await api.listApprovalsByTaskId(taskId);
  const pending = approvals.find((a) => a.status === 'PENDING');
  if (!pending) {
    console.log(`[approveTask] task=${taskId} 无 PENDING 审批单（共 ${approvals.length} 条）`);
    return null;
  }
  console.log(`[approveTask] task=${taskId} 审批单=${pending.approvalId} 路由=${pending.approvalRoute} 风险=${pending.riskLevel}`);
  return api.approve(pending.approvalId, reason);
}

/**
 * 全链路封装：触发工作流 → 自动审批 → 等待终态。
 *
 * 1. 按名称查找工作流模板
 * 2. 触发执行（medium 直接 EXECUTING；high/critical 返回 PENDING_APPROVAL + approvalId）
 * 3. 若需审批，自动 approveTask
 * 4. 等待任务终态
 *
 * @param api 已认证的 ApiClient
 * @param workflowName 工作流模板名称
 * @param params 工作流参数（含 login_url 等）
 * @returns 触发响应、终态任务、审批单（无审批则 null）
 */
export async function runWorkflowToEnd(
  api: ApiClient,
  workflowName: string,
  params: Record<string, string>,
): Promise<{
  run: WorkflowRunVO;
  task: TaskDetailVO;
  approval: ApprovalRequestVO | null;
}> {
  // 1. 查找工作流模板
  const workflow = await api.findWorkflowByName(workflowName);
  console.log(`[runWorkflowToEnd] 工作流=${workflowName} id=${workflow.workflowId} 风险=${workflow.riskLevel}`);

  // 2. 触发执行
  const run = await api.runWorkflow(workflow.workflowId, params);
  console.log(`[runWorkflowToEnd] 触发成功 taskId=${run.taskId} state=${run.state} approvalId=${run.approvalId ?? '-'}`);

  // 3. 自动审批（high/critical 需审批）
  let approval: ApprovalRequestVO | null = null;
  if (run.state === 'PENDING_APPROVAL') {
    // 审批单创建有短暂延迟，重试查询
    for (let i = 0; i < 5; i++) {
      approval = await approveTask(api, run.taskId);
      if (approval) break;
      await new Promise((r) => setTimeout(r, 1000));
    }
    if (!approval) {
      throw new Error(`任务 ${run.taskId} 处于 PENDING_APPROVAL 但未找到 PENDING 审批单`);
    }
  }

  // 4. 等待终态
  const task = await waitForTaskTerminal(api, run.taskId);
  console.log(`[runWorkflowToEnd] 任务终态 taskId=${run.taskId} status=${task.status} duration=${task.durationMs ?? '-'}ms`);

  return { run, task, approval };
}

/**
 * 等待审批单到达指定状态（场景4 超时检测用）。
 *
 * 轮询 GET /api/approvals?taskId=xxx，直到审批单状态匹配或超时。
 *
 * @param api 已认证的 ApiClient
 * @param taskId 任务 ID
 * @param expectedStatus 期望状态（'TIMEOUT' / 'APPROVED' / 'REJECTED'）
 * @param timeoutMs 超时毫秒（默认 APPROVAL_TIMEOUT_WAIT = 3 分钟）
 * @returns 终态审批单
 */
export async function waitForApprovalStatus(
  api: ApiClient,
  taskId: string,
  expectedStatus: 'TIMEOUT' | 'APPROVED' | 'REJECTED',
  timeoutMs: number = APPROVAL_TIMEOUT_WAIT,
): Promise<ApprovalRequestVO> {
  const start = Date.now();
  let lastApproval: ApprovalRequestVO | null = null;
  while (Date.now() - start < timeoutMs) {
    const approvals = await api.listApprovalsByTaskId(taskId);
    const matching = approvals.find((a) => a.status === expectedStatus);
    if (matching) {
      console.log(`[waitForApprovalStatus] task=${taskId} 审批单=${matching.approvalId} 状态=${matching.status}`);
      return matching;
    }
    if (approvals.length > 0) {
      lastApproval = approvals[0];
      console.log(`[waitForApprovalStatus] task=${taskId} 审批单=${lastApproval.approvalId} 当前状态=${lastApproval.status}，等待 ${expectedStatus}`);
    }
    await new Promise((r) => setTimeout(r, APPROVAL_TIMEOUT_POLL_INTERVAL));
  }
  throw new Error(`审批单等待 ${expectedStatus} 超时（${timeoutMs}ms），最后状态：${lastApproval?.status ?? '无审批单'}`);
}

/**
 * 动态修改审批超时阈值并返回还原函数（场景4 用）。
 *
 * 调用 PUT /api/approval-timeout/{riskLevel} 修改阈值，返回一个还原函数，
 * 测试结束后调用还原函数恢复原值，避免影响其他测试。
 *
 * @param api 已认证的 ApiClient
 * @param riskLevel 风险等级（'high' / 'critical'）
 * @param newMinutes 新的超时分钟数
 * @returns 还原函数（调用恢复原值）
 */
export async function updateApprovalTimeoutAndRestore(
  api: ApiClient,
  riskLevel: 'high' | 'critical',
  newMinutes: number,
): Promise<() => Promise<void>> {
  // 1. 查询原值
  const configs = await api.listApprovalTimeoutConfigs();
  const original = configs.find((c) => c.riskLevel === riskLevel);
  const originalMinutes = original?.timeoutMinutes ?? 30;
  console.log(`[updateApprovalTimeoutAndRestore] ${riskLevel} 原值=${originalMinutes}分钟，临时改为 ${newMinutes}分钟`);

  // 2. 修改为新值
  await api.updateApprovalTimeoutConfig(riskLevel, { timeoutMinutes: newMinutes });

  // 3. 返回还原函数
  return async () => {
    try {
      await api.updateApprovalTimeoutConfig(riskLevel, { timeoutMinutes: originalMinutes });
      console.log(`[updateApprovalTimeoutAndRestore] ${riskLevel} 已还原为 ${originalMinutes}分钟`);
    } catch (e) {
      console.error(`[updateApprovalTimeoutAndRestore] 还原 ${riskLevel} 失败:`, e);
    }
  };
}

/**
 * 按 taskId 查询审计日志并断言数量不少于 minCount。
 *
 * @param api 已认证的 ApiClient
 * @param taskId 任务 ID
 * @param minCount 最少审计日志条数
 * @returns 审计日志列表
 */
export async function assertAuditLogs(
  api: ApiClient,
  taskId: string,
  minCount: number = 1,
): Promise<AuditLogVO[]> {
  const logs = await api.listAuditLogsByTaskId(taskId);
  console.log(`[assertAuditLogs] task=${taskId} 审计日志 ${logs.length} 条（要求 >= ${minCount}）`);
  if (logs.length < minCount) {
    throw new Error(`审计日志数量不足：期望 >= ${minCount}，实际 ${logs.length}`);
  }
  return logs;
}

// endregion

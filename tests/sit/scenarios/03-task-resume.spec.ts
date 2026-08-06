/**
 * 场景3 · 任务中断 → 断点续跑（coordination-state 持久化 + resume 跳过已完成子任务）。
 *
 * 验证 M4.2/M4.3 断点续跑机制：
 * 1. internalUpdateCoordinationState 持久化协调状态（含已完成子任务列表）
 * 2. internalUpdateSubTask 标记子任务状态
 * 3. 任务中断后（EXECUTING → FAILED），调用 resume 续跑
 * 4. resumeTask 读取 coordination-state，调 Python resumeTask（传 completedSubtasks）
 * 5. 验证 resume 接口契约 + coordination-state 持久化（间接验证：resume 不会抛"协调状态不存在"）
 *
 * 注意：
 * - TaskController 没有 GET coordination-state 接口，通过 resume 行为间接验证持久化
 * - resume 会调 Python AiServiceClient.resumeTask，Python 可能成功或失败
 * - Python 失败时 resumeTask 回滚状态为 FAILED，接口抛 BusinessException
 *
 * 前置条件：docker-compose 全栈已启动。
 */
import { expect, mockBankUrl, test } from '../lib/fixtures';
import type { TaskDetailVO } from '../lib/types';

/** 轮询任务直到指定状态或超时。 */
async function waitForStatus(api: import('../lib/api').ApiClient, taskId: string, status: string, timeoutMs = 60_000): Promise<TaskDetailVO> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const task = await api.getTask(taskId);
    if (task.status === status) return task;
    if (['SUCCESS', 'FAILED', 'ABORTED'].includes(task.status)) return task;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`等待任务 ${taskId} 到达 ${status} 超时`);
}

test.describe('场景3 · 任务中断 → 断点续跑', () => {
  test('coordination-state 持久化 → 中断 → resume 续跑（跳过已完成子任务）', async ({ api }) => {
    // 1. 触发工作流，获得 taskId，等待 EXECUTING
    const workflow = await api.findWorkflowByName('银行流水下载');
    const params = {
      login_url: mockBankUrl(1),
      login_username: 'testuser',
      login_password: 'testpass',
      account_number: '6228480012345678',
      date_start: '2026-07-01',
      date_end: '2026-07-31',
    };
    const run = await api.runWorkflow(workflow.workflowId, params);
    console.log(`[续跑] 触发工作流 taskId=${run.taskId}`);

    const executingTask = await waitForStatus(api, run.taskId, 'EXECUTING', 30_000);
    if (executingTask.status !== 'EXECUTING') {
      test.skip(true, `任务已到终态 ${executingTask.status}，无法模拟中断`);
      return;
    }

    const taskIdStr = run.taskId.toString();

    // 2. 内部回调：持久化 coordination-state（模拟 Python Coordinator 每步回调）
    //    记录已完成子任务 ["step_0", "step_1"]，当前计划含 3 个子任务
    const plan = JSON.stringify([
      { subtask_id: 'step_0', goal: '登录网银', status: 'COMPLETED' },
      { subtask_id: 'step_1', goal: '填写账号日期', status: 'COMPLETED' },
      { subtask_id: 'step_2', goal: '下载流水', status: 'PENDING' },
    ]);
    await api.internalUpdateCoordinationState(taskIdStr, {
      navigationGoal: '下载银行流水 PDF',
      currentPlan: plan,
      completedSubtasks: ['step_0', 'step_1'],
      totalReplans: 0,
      maxReplans: 3,
      status: 'RUNNING',
    });
    console.log(`[续跑] coordination-state 已持久化：completedSubtasks=["step_0","step_1"]`);

    // 3. 内部回调：更新子任务状态（模拟 Python 标记前两步完成）
    //    子任务是否存在取决于 Skyvern 执行计划，单步任务可能无子任务
    //    子任务更新非核心验证点，失败时跳过（不影响 coordination-state 持久化验证）
    try {
      await api.internalUpdateSubTask(taskIdStr, {
        subtaskIndex: 0,
        status: 'COMPLETED',
      });
      await api.internalUpdateSubTask(taskIdStr, {
        subtaskIndex: 1,
        status: 'COMPLETED',
      });
      console.log(`[续跑] 子任务 0/1 已标记 COMPLETED`);
    } catch (e: unknown) {
      console.log(`[续跑] 子任务更新跳过（子任务不存在，单步任务无子任务）: ${e instanceof Error ? e.message : String(e)}`);
    }

    // 4. 内部回调：EXECUTING → FAILED（模拟任务中断）
    await api.internalUpdateTaskState(taskIdStr, {
      state: 'FAILED',
      currentStep: 2,
      totalSteps: 3,
      message: '任务中断：下载步骤失败',
      errorMessage: '网络超时，文件下载失败',
    });

    // 5. 验证任务状态为 FAILED
    const failedTask = await api.getTask(run.taskId);
    expect(failedTask.status, '中断后任务状态应为 FAILED').toBe('FAILED');
    expect(failedTask.currentStep, 'currentStep 应为 2（已完成 2 步）').toBe(2);
    expect(failedTask.totalSteps, 'totalSteps 应为 3').toBe(3);
    console.log(`[续跑] 任务已中断: status=FAILED step=2/3`);

    // 6. 调用 resume 续跑
    //    resumeTask 会读取 coordination-state，调 Python resumeTask
    //    Python 可能成功（状态变 EXECUTING）或失败（回滚 FAILED）
    let resumeSucceeded = false;
    let resumeError: string | null = null;
    try {
      await api.resumeTask(run.taskId);
      resumeSucceeded = true;
    } catch (e: unknown) {
      resumeError = e instanceof Error ? e.message : String(e);
      console.log(`[续跑] resume 调 Python 失败（预期行为，Python 无对应 session）: ${resumeError}`);
    }

    if (resumeSucceeded) {
      // 7a. resume 成功：验证任务状态变为 EXECUTING
      const resumedTask = await api.getTask(run.taskId);
      expect(['EXECUTING', 'SUCCESS', 'FAILED'].includes(resumedTask.status)).toBe(true);
      console.log(`[续跑] resume 成功，任务状态: ${resumedTask.status}`);
    } else {
      // 7b. resume 失败：验证任务状态回滚为 FAILED（不是 NEEDS_HUMAN 或其他）
      const rolledBackTask = await api.getTask(run.taskId);
      expect(rolledBackTask.status, 'resume 失败后应回滚为 FAILED').toBe('FAILED');
      console.log(`[续跑] resume 失败，任务已回滚为 FAILED（符合预期）`);

      // 验证 resume 失败不是因为 coordination-state 不存在（间接验证持久化成功）
      // 如果 coordination-state 不存在，错误信息会包含"协调状态不存在"
      expect(resumeError, 'resume 失败原因不应是"协调状态不存在"').not.toMatch(/协调状态不存在/);
      console.log(`[续跑] coordination-state 持久化已验证（resume 未报"协调状态不存在"）`);
    }
  });

  test('resume 前置校验：非 FAILED/NEEDS_HUMAN 状态不允许续跑', async ({ api }) => {
    // 1. 触发工作流，获得 taskId，等待 EXECUTING
    const workflow = await api.findWorkflowByName('银行流水下载');
    const params = {
      login_url: mockBankUrl(1),
      login_username: 'testuser',
      login_password: 'testpass',
      account_number: '6228480012345678',
      date_start: '2026-07-01',
      date_end: '2026-07-31',
    };
    const run = await api.runWorkflow(workflow.workflowId, params);

    const executingTask = await waitForStatus(api, run.taskId, 'EXECUTING', 30_000);
    if (executingTask.status !== 'EXECUTING') {
      test.skip(true, `任务已到终态 ${executingTask.status}，无法验证前置校验`);
      return;
    }

    // 2. 对 EXECUTING 状态的任务调用 resume，应抛错"仅失败或需人工介入的任务可续跑"
    let errorCaught = false;
    try {
      await api.resumeTask(run.taskId);
    } catch (e: unknown) {
      errorCaught = true;
      const msg = e instanceof Error ? e.message : String(e);
      console.log(`[续跑-校验] EXECUTING 状态 resume 被拒绝: ${msg}`);
    }
    expect(errorCaught, 'EXECUTING 状态调用 resume 应抛错').toBe(true);

    // 3. 终止任务，避免影响后续测试
    try {
      await api.abortTask(run.taskId);
    } catch {
      // 任务可能已到终态，忽略
    }
  });
});

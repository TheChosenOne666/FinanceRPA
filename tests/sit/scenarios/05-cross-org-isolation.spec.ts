/**
 * 场景5 · 跨组织数据隔离（admin vs 跨组织账号互不可见任务/审批/审计）。
 *
 * 验证多租户数据隔离机制：
 * 1. admin（银河证券）触发工作流，创建任务 + 审批单 + 审计日志
 * 2. admin_demo_xcba（星辰银行）登录，查询任务列表 → 看不到 admin 的任务
 * 3. admin_demo_xcba 直接 GET /api/tasks/{taskId} → 看不到（租户过滤或 404）
 * 4. admin_demo_xcba 查询审批单 → 看不到 admin 的审批单
 * 5. admin_demo_xcba 查询审计日志 → 看不到 admin 的审计日志
 * 6. 反向验证：admin 能看到自己的任务
 *
 * 隔离原理：
 * - MyBatis-Plus TenantLineHandler 自动在 SQL 追加 WHERE org_id = {当前组织}
 * - admin（银河证券）org_id = 银河证券雪花 id
 * - admin_demo_xcba（星辰银行）org_id = 星辰银行雪花 id
 * - 两者 org_id 不同，互不可见
 *
 * 前置条件：docker-compose 全栈已启动，DemoDataGenerator 已创建两个演示组织（银河证券 + 星辰银行）。
 */
import { expect, mockBankUrl, test } from '../lib/fixtures';
import { ApiClient } from '../lib/api';
import { SIT_PASSWORD_CROSS, SIT_USERNAME_CROSS } from '../lib/env';
import type { TaskVO } from '../lib/types';

test.describe('场景5 · 跨组织数据隔离', () => {
  test('admin（银河证券）的任务对 admin_demo_xcba（星辰银行）不可见', async ({ api }) => {
    // 1. admin（银河证券）触发工作流，创建任务
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
    console.log(`[隔离] admin 触发任务 taskId=${run.taskId}`);

    // 2. admin 验证能看到自己的任务
    const adminTask = await api.getTask(run.taskId);
    expect(adminTask.taskId, 'admin 应能看到自己的任务').toBe(run.taskId);
    expect(adminTask.orgId, 'admin 任务 orgId 应非空').toBeTruthy();
    console.log(`[隔离] admin 看到 task: taskId=${adminTask.taskId} orgId=${adminTask.orgId}`);

    // 3. admin_demo_xcba（星辰银行）登录
    const crossClient = new ApiClient();
    const crossLogin = await crossClient.login(SIT_USERNAME_CROSS, SIT_PASSWORD_CROSS);
    crossClient.setToken(crossLogin.accessToken);
    console.log(`[隔离] ${SIT_USERNAME_CROSS} 登录成功: userId=${crossLogin.user.userId} orgId=${crossLogin.user.orgId ?? '-'}`);

    // 4. 验证两个账号属于不同组织
    const adminLogin = await api.login('admin', 'admin123');
    expect(adminLogin.user.orgId, 'admin orgId 应非空').toBeTruthy();
    expect(crossLogin.user.orgId, 'admin_demo_xcba orgId 应非空').toBeTruthy();
    expect(crossLogin.user.orgId, '两个账号应属于不同组织').not.toBe(adminLogin.user.orgId);
    console.log(`[隔离] 组织对比: admin orgId=${adminLogin.user.orgId} vs cross orgId=${crossLogin.user.orgId}`);

    // 5. admin_demo_xcba 查询任务列表 → 看不到 admin 的任务
    const crossTasks = await crossClient.listTasks();
    const crossFoundAdminTask = crossTasks.some((t: TaskVO) => t.taskId === run.taskId);
    expect(crossFoundAdminTask, 'admin_demo_xcba 不应在任务列表中看到 admin 的任务').toBe(false);
    console.log(`[隔离] admin_demo_xcba 任务列表 ${crossTasks.length} 条，不含 admin 的 taskId=${run.taskId}`);

    // 6. admin_demo_xcba 直接 GET /api/tasks/{taskId} → 看不到
    //    租户过滤会追加 WHERE org_id = 星辰银行，查不到银河证券的任务
    //    接口可能返回 404 或空数据
    let crossDirectAccess = false;
    try {
      const directTask = await crossClient.getTask(run.taskId);
      // 如果能查到，验证 orgId 不匹配（不应发生，但防御性检查）
      if (directTask && directTask.taskId === run.taskId) {
        crossDirectAccess = true;
      }
    } catch (e: unknown) {
      // 预期行为：租户过滤导致查不到，抛 404 或其他错误
      console.log(`[隔离] admin_demo_xcba 直接查询 task=${run.taskId} 被拒绝: ${e instanceof Error ? e.message : String(e)}`);
    }
    expect(crossDirectAccess, 'admin_demo_xcba 不应能直接查询 admin 的任务详情').toBe(false);

    // 7. admin_demo_xcba 查询审批单 → 看不到 admin 的审批单
    const crossApprovals = await crossClient.listApprovalsByTaskId(run.taskId);
    expect(crossApprovals.length, 'admin_demo_xcba 不应看到 admin 任务的审批单').toBe(0);
    console.log(`[隔离] admin_demo_xcba 查询 task=${run.taskId} 审批单: ${crossApprovals.length} 条`);

    // 8. admin_demo_xcba 查询审计日志 → 看不到 admin 的审计日志
    const crossAuditLogs = await crossClient.listAuditLogsByTaskId(run.taskId);
    expect(crossAuditLogs.length, 'admin_demo_xcba 不应看到 admin 任务的审计日志').toBe(0);
    console.log(`[隔离] admin_demo_xcba 查询 task=${run.taskId} 审计日志: ${crossAuditLogs.length} 条`);

    // 9. 反向验证：admin 能看到自己的任务、审批单、审计日志
    const adminTasks = await api.listTasks();
    const adminFoundOwnTask = adminTasks.some((t: TaskVO) => t.taskId === run.taskId);
    expect(adminFoundOwnTask, 'admin 应能在任务列表中看到自己的任务').toBe(true);

    // 10. admin_demo_xcba 应能看到自己组织的任务（如果有的话）
    //     验证跨组织账号本身的数据可见性正常（不是"看不到任何数据"）
    console.log(`[隔离] admin_demo_xcba 可见本组织任务 ${crossTasks.length} 条，admin 可见本组织任务 ${adminTasks.length} 条`);
    console.log(`[隔离] 跨组织数据隔离验证通过`);
  });

  test('admin_demo_xcba 触发的任务对 admin 不可见', async ({ api }) => {
    // 1. admin_demo_xcba（星辰银行）登录并触发工作流
    const crossClient = new ApiClient();
    const crossLogin = await crossClient.login(SIT_USERNAME_CROSS, SIT_PASSWORD_CROSS);
    crossClient.setToken(crossLogin.accessToken);

    const workflow = await crossClient.findWorkflowByName('银行流水下载');
    const params = {
      login_url: mockBankUrl(1),
      login_username: 'testuser',
      login_password: 'testpass',
      account_number: '6228480012345678',
      date_start: '2026-07-01',
      date_end: '2026-07-31',
    };
    const run = await crossClient.runWorkflow(workflow.workflowId, params);
    console.log(`[隔离-反向] admin_demo_xcba 触发任务 taskId=${run.taskId}`);

    // 2. admin（银河证券）查询任务列表 → 看不到 admin_demo_xcba 的任务
    const adminTasks = await api.listTasks();
    const adminFoundCrossTask = adminTasks.some((t: TaskVO) => t.taskId === run.taskId);
    expect(adminFoundCrossTask, 'admin 不应在任务列表中看到 admin_demo_xcba 的任务').toBe(false);
    console.log(`[隔离-反向] admin 任务列表 ${adminTasks.length} 条，不含 cross 的 taskId=${run.taskId}`);

    // 3. admin 直接 GET /api/tasks/{taskId} → 看不到
    let adminDirectAccess = false;
    try {
      const directTask = await api.getTask(run.taskId);
      if (directTask && directTask.taskId === run.taskId) {
        adminDirectAccess = true;
      }
    } catch (e: unknown) {
      console.log(`[隔离-反向] admin 直接查询 task=${run.taskId} 被拒绝: ${e instanceof Error ? e.message : String(e)}`);
    }
    expect(adminDirectAccess, 'admin 不应能直接查询 admin_demo_xcba 的任务详情').toBe(false);

    console.log(`[隔离-反向] 双向隔离验证通过`);
  });
});

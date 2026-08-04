/**
 * Vite Dev Server Mock 插件
 *
 * 仅在 dev 模式启用（npm run dev），生产构建不会包含。
 * 用于本地测试前端 SSE 接收与展示效果，无需启动 Java/Python 后端。
 *
 * 覆盖端点：
 *   POST /api/auth/login                登录（任意账号密码均可）
 *   GET  /api/auth/me                   当前用户信息
 *   POST /api/auth/logout               登出
 *   GET  /api/tasks                     任务列表（分页 + 筛选 + 搜索）
 *   GET  /api/tasks/:taskId             任务详情（含子任务）
 *   POST /api/ai/tasks                  触发任务（返回新任务 ID）
 *   POST /api/tasks/:taskId/abort       终止任务
 *   POST /api/tasks/:taskId/resume      任务续跑（M4.4）
 *   GET  /api/ai/sse/tasks/:taskId      SSE 实时事件流
 *   GET  /api/llm/needs-human           NEEDS_HUMAN 队列列表（M5.6）
 *   GET  /api/llm/needs-human/:queueId  NEEDS_HUMAN 详情（M5.6）
 *   POST /api/llm/needs-human/:queueId/resolve 处置（M5.6）
 *   GET  /api/llm/calls/stats           LLM 调用统计（M5.6，含环比趋势）
 *   GET  /api/llm/calls                 LLM 调用记录分页（P3 ai-monitoring 原型对齐）
 *   GET  /api/llm/calls/daily-trend     LLM 调用按日趋势（P3 ai-monitoring 原型对齐）
 *   GET  /api/users                     用户列表（P4 settings 原型对齐，Mock 数据）
 *   GET  /api/roles                     角色列表（P4 settings 原型对齐，Mock 数据）
 *   GET  /api/notification/channels     通知通道列表（P4 settings 原型对齐，含 webhookUrl/enabled）
 *   GET  /api/notification/templates    通知模板配置列表（P4 settings 原型对齐，Mock 数据）
 *   PUT  /api/notification/config       保存通知配置（P4 settings 原型对齐，Mock 接受返回成功）
 *
 * SSE 场景（按 taskId 切换）：
 *   mock-success      全部子任务成功
 *   mock-failed       中途子任务失败 → 任务失败
 *   mock-needs-human  中途触发 NEEDS_HUMAN
 *   mock-replan       触发重新规划后成功
 *   mock-aborted      模拟被终止
 *   其他              默认成功流程
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import type { Connect, Plugin } from 'vite'
import type { IncomingMessage, ServerResponse } from 'node:http'

/** Mock 用户信息 */
const MOCK_USER = {
  userId: '100001',
  username: 'admin_demo_yhsec',
  realName: '张三',
  orgId: '900000000000000001',
  orgName: '银河证券',
  deptName: '财务部',
  roles: ['org_admin'],
  permissions: ['task:trigger', 'task:abort', 'task:view'],
}

/** Mock 任务列表（内存存储，触发新任务会追加） */
interface MockTask {
  taskId: string
  orgId: string
  userId: string
  goal: string
  status: 'PENDING' | 'EXECUTING' | 'SUCCESS' | 'FAILED' | 'NEEDS_HUMAN' | 'ABORTED'
  currentStep: number
  totalSteps: number
  message?: string
  errorMessage?: string
  params?: string
  workflowId?: string
  userName?: string
  durationMs?: number
  riskLevel?: 'low' | 'medium' | 'high' | 'critical'
  // M7.6 三维度 RBAC：部门/业务线（可选，未指定时 listTasks 返回默认值）
  departmentId?: string
  departmentName?: string
  businessLineId?: string
  businessLineName?: string
  createTime: string
  updateTime: string
  subtasks: Array<{
    subtaskId: string
    taskId: string
    subtaskIndex: number
    goal: string
    completionCondition?: string
    maxRetries?: number
    failureStrategy?: string
    status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED' | 'REPLANNED'
    errorMessage?: string
    resultData?: string
    startedAt?: string
    completedAt?: string
    createTime: string
    updateTime: string
  }>
}

/** 内存任务存储 */
const mockTasks: MockTask[] = [
  {
    taskId: '700000000000000001',
    orgId: MOCK_USER.orgId,
    userId: MOCK_USER.userId,
    goal: '下载工商银行 2026 年 6 月银行流水',
    workflowId: '300000000000000001',
    status: 'SUCCESS',
    currentStep: 3,
    totalSteps: 3,
    message: '任务已完成',
    createTime: '2026-07-28T09:12:35.000Z',
    updateTime: '2026-07-28T09:15:42.000Z',
    subtasks: [
      {
        subtaskId: '710000000000000001',
        taskId: '700000000000000001',
        subtaskIndex: 0,
        goal: '登录工商银行企业网银',
        completionCondition: '页面显示账户总览',
        maxRetries: 2,
        failureStrategy: 'RETRY',
        status: 'COMPLETED',
        startedAt: '2026-07-28T09:12:40.000Z',
        completedAt: '2026-07-28T09:13:25.000Z',
        createTime: '2026-07-28T09:12:35.000Z',
        updateTime: '2026-07-28T09:13:25.000Z',
      },
      {
        subtaskId: '710000000000000002',
        taskId: '700000000000000001',
        subtaskIndex: 1,
        goal: '导航到账户明细页面',
        completionCondition: '页面显示交易明细表格',
        maxRetries: 1,
        failureStrategy: 'RETRY',
        status: 'COMPLETED',
        startedAt: '2026-07-28T09:13:30.000Z',
        completedAt: '2026-07-28T09:14:15.000Z',
        createTime: '2026-07-28T09:12:35.000Z',
        updateTime: '2026-07-28T09:14:15.000Z',
      },
      {
        subtaskId: '710000000000000003',
        taskId: '700000000000000001',
        subtaskIndex: 2,
        goal: '下载 2026 年 6 月流水 PDF',
        completionCondition: '文件下载完成',
        maxRetries: 0,
        failureStrategy: 'ABORT',
        status: 'COMPLETED',
        resultData: '{"fileKey":"statements/2026-06-icbc.pdf","size":245678}',
        startedAt: '2026-07-28T09:14:20.000Z',
        completedAt: '2026-07-28T09:15:42.000Z',
        createTime: '2026-07-28T09:12:35.000Z',
        updateTime: '2026-07-28T09:15:42.000Z',
      },
    ],
  },
  {
    taskId: '700000000000000002',
    orgId: MOCK_USER.orgId,
    userId: MOCK_USER.userId,
    goal: '登录招商银行网银并查询账户余额',
    status: 'FAILED',
    currentStep: 1,
    totalSteps: 3,
    errorMessage: '密码错误连续 3 次，账户被临时锁定',
    createTime: '2026-07-28T14:22:10.000Z',
    updateTime: '2026-07-28T14:25:30.000Z',
    subtasks: [
      {
        subtaskId: '710000000000000010',
        taskId: '700000000000000002',
        subtaskIndex: 0,
        goal: '登录招商银行企业网银',
        maxRetries: 2,
        failureStrategy: 'RETRY',
        status: 'FAILED',
        errorMessage: '密码错误连续 3 次，账户被临时锁定',
        startedAt: '2026-07-28T14:22:15.000Z',
        completedAt: '2026-07-28T14:25:30.000Z',
        createTime: '2026-07-28T14:22:10.000Z',
        updateTime: '2026-07-28T14:25:30.000Z',
      },
    ],
  },
  {
    taskId: '700000000000000003',
    orgId: MOCK_USER.orgId,
    userId: MOCK_USER.userId,
    goal: '下载建设银行 2026 年 Q2 季度对账单',
    workflowId: '300000000000000001',
    status: 'EXECUTING',
    currentStep: 2,
    totalSteps: 4,
    message: '正在下载对账单文件…',
    createTime: '2026-07-29T08:30:00.000Z',
    updateTime: '2026-07-29T08:33:20.000Z',
    subtasks: [
      {
        subtaskId: '710000000000000020',
        taskId: '700000000000000003',
        subtaskIndex: 0,
        goal: '登录建设银行企业网银',
        status: 'COMPLETED',
        startedAt: '2026-07-29T08:30:05.000Z',
        completedAt: '2026-07-29T08:30:50.000Z',
        createTime: '2026-07-29T08:30:00.000Z',
        updateTime: '2026-07-29T08:30:50.000Z',
      },
      {
        subtaskId: '710000000000000021',
        taskId: '700000000000000003',
        subtaskIndex: 1,
        goal: '导航到季度对账单页面',
        status: 'COMPLETED',
        startedAt: '2026-07-29T08:30:55.000Z',
        completedAt: '2026-07-29T08:32:10.000Z',
        createTime: '2026-07-29T08:30:00.000Z',
        updateTime: '2026-07-29T08:32:10.000Z',
      },
      {
        subtaskId: '710000000000000022',
        taskId: '700000000000000003',
        subtaskIndex: 2,
        goal: '选择 Q2 季度并下载',
        status: 'RUNNING',
        startedAt: '2026-07-29T08:32:15.000Z',
        createTime: '2026-07-29T08:30:00.000Z',
        updateTime: '2026-07-29T08:33:20.000Z',
      },
    ],
  },
  {
    // M4.4 测试：REPLANNED 子任务（验证 replan 标记 + SKIPPED 节点）
    taskId: '700000000000000004',
    orgId: MOCK_USER.orgId,
    userId: MOCK_USER.userId,
    goal: '下载农业银行 2026 年 7 月工资代发表',
    status: 'SUCCESS',
    currentStep: 4,
    totalSteps: 4,
    message: '任务已完成（含 1 次重规划）',
    createTime: '2026-07-30T10:00:00.000Z',
    updateTime: '2026-07-30T10:12:30.000Z',
    subtasks: [
      {
        subtaskId: '710000000000000030',
        taskId: '700000000000000004',
        subtaskIndex: 0,
        goal: '登录农业银行企业网银',
        completionCondition: '页面显示账户总览',
        maxRetries: 2,
        failureStrategy: 'RETRY',
        status: 'COMPLETED',
        startedAt: '2026-07-30T10:00:05.000Z',
        completedAt: '2026-07-30T10:01:20.000Z',
        createTime: '2026-07-30T10:00:00.000Z',
        updateTime: '2026-07-30T10:01:20.000Z',
      },
      {
        subtaskId: '710000000000000031',
        taskId: '700000000000000004',
        subtaskIndex: 1,
        goal: '点击"代发工资"菜单',
        completionCondition: '页面显示代发工资列表',
        maxRetries: 1,
        failureStrategy: 'REPLAN',
        status: 'REPLANNED',
        errorMessage: '页面结构变化，菜单未找到',
        startedAt: '2026-07-30T10:01:25.000Z',
        completedAt: '2026-07-30T10:02:30.000Z',
        createTime: '2026-07-30T10:00:00.000Z',
        updateTime: '2026-07-30T10:02:30.000Z',
      },
      {
        subtaskId: '710000000000000032',
        taskId: '700000000000000004',
        subtaskIndex: 2,
        goal: '通过快捷入口进入代发工资页面',
        completionCondition: '页面显示代发工资列表',
        maxRetries: 1,
        failureStrategy: 'RETRY',
        status: 'COMPLETED',
        startedAt: '2026-07-30T10:02:35.000Z',
        completedAt: '2026-07-30T10:04:10.000Z',
        resultData: '{"page":"salary_list","entries":42}',
        createTime: '2026-07-30T10:00:00.000Z',
        updateTime: '2026-07-30T10:04:10.000Z',
      },
      {
        subtaskId: '710000000000000033',
        taskId: '700000000000000004',
        subtaskIndex: 3,
        goal: '下载 7 月工资代发表 Excel',
        completionCondition: '文件下载完成',
        maxRetries: 0,
        failureStrategy: 'SKIP',
        status: 'SKIPPED',
        errorMessage: '7 月数据尚未生成，跳过下载',
        startedAt: '2026-07-30T10:04:15.000Z',
        completedAt: '2026-07-30T10:04:20.000Z',
        createTime: '2026-07-30T10:00:00.000Z',
        updateTime: '2026-07-30T10:04:20.000Z',
      },
    ],
  },
  {
    // M4.4 测试：NEEDS_HUMAN 状态（验证续跑按钮）
    taskId: '700000000000000005',
    orgId: MOCK_USER.orgId,
    userId: MOCK_USER.userId,
    goal: '登录中国银行网银并下载回单',
    status: 'NEEDS_HUMAN',
    currentStep: 1,
    totalSteps: 3,
    message: '等待人工介入识别短信验证码',
    errorMessage: '触发 NEEDS_HUMAN：需要人工识别短信验证码',
    createTime: '2026-07-31T14:00:00.000Z',
    updateTime: '2026-07-31T14:05:30.000Z',
    subtasks: [
      {
        subtaskId: '710000000000000040',
        taskId: '700000000000000005',
        subtaskIndex: 0,
        goal: '登录中国银行企业网银',
        status: 'COMPLETED',
        startedAt: '2026-07-31T14:00:05.000Z',
        completedAt: '2026-07-31T14:01:30.000Z',
        createTime: '2026-07-31T14:00:00.000Z',
        updateTime: '2026-07-31T14:01:30.000Z',
      },
      {
        subtaskId: '710000000000000041',
        taskId: '700000000000000005',
        subtaskIndex: 1,
        goal: '输入短信验证码',
        status: 'FAILED',
        errorMessage: '需要人工识别短信验证码',
        startedAt: '2026-07-31T14:01:35.000Z',
        completedAt: '2026-07-31T14:05:30.000Z',
        createTime: '2026-07-31T14:00:00.000Z',
        updateTime: '2026-07-31T14:05:30.000Z',
      },
    ],
  },
]

/**
 * 创建 Mock Server Vite 插件
 *
 * @returns Vite 插件对象
 */
export function mockServerPlugin(): Plugin {
  return {
    name: 'finrpa-mock-server',
    configureServer(server) {
      // 1. 在内置 middleware 之后、proxy 之前注入
      server.middlewares.use(
        (req: Connect.IncomingMessage, res: ServerResponse, next: Connect.NextFunction) => {
          // 1.1 仅处理 /api/ 开头的请求
          const url = req.url || ''
          if (!url.startsWith('/api/')) {
            return next()
          }

          // 1.2 去除 query string
          const pathname = url.split('?')[0]
          const method = (req.method || 'GET').toUpperCase()

          try {
            // 2. 路由分发
            if (pathname === '/api/auth/login' && method === 'POST') {
              return handleLogin(req, res)
            }
            if (pathname === '/api/auth/me' && method === 'GET') {
              return handleGetCurrentUser(res)
            }
            if (pathname === '/api/auth/logout' && method === 'POST') {
              return handleLogout(res)
            }
            if (pathname === '/api/auth/refresh' && method === 'POST') {
              return handleRefresh(req, res)
            }
            if (pathname === '/api/tasks' && method === 'GET') {
              return handleListTasks(req, res)
            }
            if (pathname === '/api/ai/tasks' && method === 'POST') {
              return handleTriggerTask(req, res)
            }
            if (pathname === '/api/ai/sse/tasks' && method === 'GET') {
              // 兼容旧路径 /api/ai/sse/tasks（无具体 taskId，返回 400）
              return sendJson(res, 400, { code: 40400, data: null, message: '缺少 taskId' })
            }
            // SSE：/api/ai/sse/tasks/:taskId
            const sseMatch = pathname.match(/^\/api\/ai\/sse\/tasks\/([^/]+)$/)
            if (sseMatch && method === 'GET') {
              return handleSseStream(req, res, decodeURIComponent(sseMatch[1]))
            }
            // 任务详情：/api/tasks/:taskId
            const detailMatch = pathname.match(/^\/api\/tasks\/([^/]+)$/)
            if (detailMatch && method === 'GET') {
              return handleGetTaskDetail(res, decodeURIComponent(detailMatch[1]))
            }
            // 终止任务：/api/tasks/:taskId/abort
            const abortMatch = pathname.match(/^\/api\/tasks\/([^/]+)\/abort$/)
            if (abortMatch && method === 'POST') {
              return handleAbortTask(res, decodeURIComponent(abortMatch[1]))
            }
            // 任务续跑：/api/tasks/:taskId/resume（M4.4）
            const resumeMatch = pathname.match(/^\/api\/tasks\/([^/]+)\/resume$/)
            if (resumeMatch && method === 'POST') {
              return handleResumeTask(res, decodeURIComponent(resumeMatch[1]))
            }
            // NEEDS_HUMAN 队列列表：/api/llm/needs-human（M5.6）
            if (pathname === '/api/llm/needs-human' && method === 'GET') {
              return handleListNeedsHuman(req, res)
            }
            // NEEDS_HUMAN 处置：/api/llm/needs-human/:queueId/resolve（M5.6）
            const resolveMatch = pathname.match(
              /^\/api\/llm\/needs-human\/([^/]+)\/resolve$/,
            )
            if (resolveMatch && method === 'POST') {
              return handleResolveNeedsHuman(req, res, decodeURIComponent(resolveMatch[1]))
            }
            // NEEDS_HUMAN 详情：/api/llm/needs-human/:queueId（M5.6）
            const needsHumanDetailMatch = pathname.match(
              /^\/api\/llm\/needs-human\/([^/]+)$/,
            )
            if (needsHumanDetailMatch && method === 'GET') {
              return handleGetNeedsHumanDetail(
                res,
                decodeURIComponent(needsHumanDetailMatch[1]),
              )
            }
            // LLM 调用统计：/api/llm/calls/stats（M5.6）
            if (pathname === '/api/llm/calls/stats' && method === 'GET') {
              return handleGetLlmStats(req, res)
            }
            // LLM 调用按日趋势：/api/llm/calls/daily-trend（P3 ai-monitoring 原型对齐）
            if (pathname === '/api/llm/calls/daily-trend' && method === 'GET') {
              return handleGetLlmDailyTrend(req, res)
            }
            // LLM 调用记录分页：/api/llm/calls（P3 ai-monitoring 原型对齐，必须在 /stats 等子路径之后匹配）
            if (pathname === '/api/llm/calls' && method === 'GET') {
              return handleListLlmCalls(req, res)
            }
            // 运营大屏：/api/v1/dashboard/*（M8.2）
            if (pathname === '/api/v1/dashboard/overview' && method === 'GET') {
              return handleGetDashboardOverview(res)
            }
            if (pathname === '/api/v1/dashboard/trends' && method === 'GET') {
              return handleGetDashboardTrends(req, res)
            }
            if (pathname === '/api/v1/dashboard/business-lines' && method === 'GET') {
              return handleGetDashboardBusinessLines(res)
            }
            if (pathname === '/api/v1/dashboard/errors' && method === 'GET') {
              return handleGetDashboardErrors(res)
            }
            if (pathname === '/api/v1/dashboard/costs' && method === 'GET') {
              return handleGetDashboardCosts(res)
            }
            if (pathname === '/api/v1/dashboard/approvals' && method === 'GET') {
              return handleGetDashboardApprovals(res)
            }
            // 审批列表：/api/approvals（M6.5，Dashboard 最近审批表格用）
            if (pathname === '/api/approvals' && method === 'GET') {
              return handleListApprovals(req, res)
            }
            // 审计日志导出：/api/v1/audit/logs/export（M7.5，必须在 :auditId 之前匹配）
            if (pathname === '/api/v1/audit/logs/export' && method === 'GET') {
              return handleExportAuditLogs(req, res)
            }
            // 审计日志列表：/api/v1/audit/logs（M7.5）
            if (pathname === '/api/v1/audit/logs' && method === 'GET') {
              return handleListAuditLogs(req, res)
            }
            // 审计日志详情：/api/v1/audit/logs/:auditId（M7.5）
            const auditDetailMatch = pathname.match(
              /^\/api\/v1\/audit\/logs\/([^/]+)$/,
            )
            if (auditDetailMatch && method === 'GET') {
              return handleGetAuditLogDetail(
                res,
                decodeURIComponent(auditDetailMatch[1]),
              )
            }
            // 工作流列表：/api/workflows（M3.6）
            if (pathname === '/api/workflows' && method === 'GET') {
              return handleListWorkflows(req, res)
            }
            // 工作流触发执行：/api/workflows/:workflowId/run（必须在 :workflowId 之前匹配）
            const workflowRunMatch = pathname.match(
              /^\/api\/workflows\/([^/]+)\/run$/,
            )
            if (workflowRunMatch && method === 'POST') {
              return handleRunWorkflow(
                req,
                res,
                decodeURIComponent(workflowRunMatch[1]),
              )
            }
            // 工作流详情：/api/workflows/:workflowId（M3.6）
            const workflowDetailMatch = pathname.match(
              /^\/api\/workflows\/([^/]+)$/,
            )
            if (workflowDetailMatch && method === 'GET') {
              return handleGetWorkflowDetail(
                res,
                decodeURIComponent(workflowDetailMatch[1]),
              )
            }
            // 部门列表：/api/tenant/departments（P0-3 对齐后端 TenantController，去掉 v1 前缀）
            if (pathname === '/api/tenant/departments' && method === 'GET') {
              return handleListDepartments(res)
            }
            // 业务线列表：/api/tenant/business-lines（P0-3 对齐后端 TenantController，去掉 v1 前缀）
            if (pathname === '/api/tenant/business-lines' && method === 'GET') {
              return handleListBusinessLines(res)
            }
            // 用户列表：/api/users（P1 USR-1，分页查询）
            if (pathname === '/api/users' && method === 'GET') {
              return handleListUsers(req, res)
            }
            // 新增用户：/api/users（P1 USR-1）
            if (pathname === '/api/users' && method === 'POST') {
              return handleAddUser(req, res)
            }
            // 编辑用户：/api/users（P1 USR-1，PUT 无 :id）
            if (pathname === '/api/users' && method === 'PUT') {
              return handleUpdateUser(req, res)
            }
            // 重置密码：/api/users/reset-password（P1 USR-1，必须先于 /users/:userId 匹配）
            if (pathname === '/api/users/reset-password' && method === 'PUT') {
              return handleResetPassword(req, res)
            }
            // 分配角色：/api/users/roles（P1 USR-1，必须先于 /users/:userId 匹配）
            if (pathname === '/api/users/roles' && method === 'POST') {
              return handleAssignUserRoles(req, res)
            }
            // 用户详情 / 启停 / 删除：/api/users/{userId}（P1 USR-1）
            const userMatch = pathname.match(/^\/api\/users\/([^/]+)$/)
            if (userMatch && method === 'GET') {
              return handleGetUser(res, userMatch[1])
            }
            if (userMatch && method === 'DELETE') {
              return handleDeleteUser(res, userMatch[1])
            }
            // 用户启停：/api/users/{userId}/status（P1 USR-1）
            const userStatusMatch = pathname.match(
              /^\/api\/users\/([^/]+)\/status$/,
            )
            if (userStatusMatch && method === 'PUT') {
              return handleToggleUserStatus(req, res, userStatusMatch[1])
            }

            // 角色列表：/api/roles（P1 USR-2，分页查询）
            if (pathname === '/api/roles' && method === 'GET') {
              return handleListRoles(req, res)
            }
            // 全部角色：/api/roles/all（P1 USR-2，不分页，必须先于 /roles/:roleId 匹配）
            if (pathname === '/api/roles/all' && method === 'GET') {
              return handleListAllRoles(res)
            }
            // 新增角色：/api/roles（P1 USR-2）
            if (pathname === '/api/roles' && method === 'POST') {
              return handleAddRole(req, res)
            }
            // 编辑角色：/api/roles（P1 USR-2，PUT 无 :id）
            if (pathname === '/api/roles' && method === 'PUT') {
              return handleUpdateRole(req, res)
            }
            // 角色详情 / 删除：/api/roles/{roleId}（P1 USR-2）
            const roleMatch = pathname.match(/^\/api\/roles\/([^/]+)$/)
            if (roleMatch && method === 'GET') {
              return handleGetRole(res, roleMatch[1])
            }
            if (roleMatch && method === 'DELETE') {
              return handleDeleteRole(res, roleMatch[1])
            }
            // 角色启停：/api/roles/{roleId}/status（P1 USR-2）
            const roleStatusMatch = pathname.match(
              /^\/api\/roles\/([^/]+)\/status$/,
            )
            if (roleStatusMatch && method === 'PUT') {
              return handleToggleRoleStatus(req, res, roleStatusMatch[1])
            }

            // 审批超时配置列表：/api/approval-timeout（P1 RSK-1）
            if (pathname === '/api/approval-timeout' && method === 'GET') {
              return handleListApprovalTimeoutConfigs(res)
            }
            // 更新审批超时配置：/api/approval-timeout/{riskLevel}（P1 RSK-1）
            const timeoutMatch = pathname.match(
              /^\/api\/approval-timeout\/([^/]+)$/,
            )
            if (timeoutMatch && method === 'PUT') {
              return handleUpdateApprovalTimeoutConfig(
                req,
                res,
                timeoutMatch[1],
              )
            }

            // 审批人映射列表：/api/approval-routes（P1 RSK-3，分页查询）
            if (pathname === '/api/approval-routes' && method === 'GET') {
              return handleListApprovalRouteConfigs(req, res)
            }
            // 新增审批人映射：/api/approval-routes（P1 RSK-3）
            if (pathname === '/api/approval-routes' && method === 'POST') {
              return handleAddApprovalRouteConfig(req, res)
            }
            // 更新 / 删除审批人映射：/api/approval-routes/{configId}（P1 RSK-3）
            const routeMatch = pathname.match(
              /^\/api\/approval-routes\/([^/]+)$/,
            )
            if (routeMatch && method === 'PUT') {
              return handleUpdateApprovalRouteConfig(req, res, routeMatch[1])
            }
            if (routeMatch && method === 'DELETE') {
              return handleDeleteApprovalRouteConfig(res, routeMatch[1])
            }

            // 密码策略查询：/api/password-policy（P2 SEC-1）
            if (pathname === '/api/password-policy' && method === 'GET') {
              return handleGetPasswordPolicy(res)
            }
            // 密码策略更新：/api/password-policy（P2 SEC-1）
            if (pathname === '/api/password-policy' && method === 'PUT') {
              return handleUpdatePasswordPolicy(req, res)
            }

            // 登录安全策略查询：/api/login-policy（P2 SEC-2）
            if (pathname === '/api/login-policy' && method === 'GET') {
              return handleGetLoginPolicy(res)
            }
            // 登录安全策略更新：/api/login-policy（P2 SEC-2）
            if (pathname === '/api/login-policy' && method === 'PUT') {
              return handleUpdateLoginPolicy(req, res)
            }

            // 在线会话列表：/api/sessions（P2 SEC-3，分页查询）
            if (pathname === '/api/sessions' && method === 'GET') {
              return handleListSessions(req, res)
            }
            // 踢人下线：/api/sessions/{sessionId}（P2 SEC-3）
            const sessionMatch = pathname.match(/^\/api\/sessions\/([^/]+)$/)
            if (sessionMatch && method === 'DELETE') {
              return handleKillSession(res, sessionMatch[1])
            }

            // 系统健康检查：/api/system-health（P2 OPS-1，一键检测）
            if (pathname === '/api/system-health' && method === 'GET') {
              return handleCheckSystemHealth(res)
            }

            // 通知通道列表：/api/notification/channels（P4 settings 原型对齐，含 webhookUrl/enabled）
            if (pathname === '/api/notification/channels' && method === 'GET') {
              return handleListNotificationChannels(res)
            }
            // 通知模板配置列表：/api/notification/templates（P4 settings 原型对齐，Mock 数据）
            if (pathname === '/api/notification/templates' && method === 'GET') {
              return handleListNotificationTemplates(res)
            }
            // 保存通知配置：/api/notification/config（P4 settings 原型对齐，Mock 接受返回成功）
            if (pathname === '/api/notification/config' && method === 'PUT') {
              return handleSaveNotificationConfig(req, res)
            }
            // 保存通道 Webhook 配置：/api/notification/channels/{channel}（P0-4）
            const channelSaveMatch = pathname.match(
              /^\/api\/notification\/channels\/([^/]+)$/,
            )
            if (channelSaveMatch && method === 'PUT') {
              return handleSaveChannelConfig(req, res, channelSaveMatch[1])
            }

            // 风险关键词列表：/api/risk-keywords（P0-1，分页查询）
            if (pathname === '/api/risk-keywords' && method === 'GET') {
              return handleListRiskKeywords(req, res)
            }
            // 新增风险关键词：/api/risk-keywords（P0-1）
            if (pathname === '/api/risk-keywords' && method === 'POST') {
              return handleAddRiskKeyword(req, res)
            }
            // 更新 / 删除风险关键词：/api/risk-keywords/{keywordId}（P0-1）
            const riskKeywordMatch = pathname.match(
              /^\/api\/risk-keywords\/([^/]+)$/,
            )
            if (riskKeywordMatch && method === 'PUT') {
              return handleUpdateRiskKeyword(req, res, riskKeywordMatch[1])
            }
            if (riskKeywordMatch && method === 'DELETE') {
              return handleDeleteRiskKeyword(res, riskKeywordMatch[1])
            }

            // Skill 列表：/api/skills（P0-2，分页查询）
            if (pathname === '/api/skills' && method === 'GET') {
              return handleListSkills(req, res)
            }
            // 注册 Skill：/api/skills（P0-2）
            if (pathname === '/api/skills' && method === 'POST') {
              return handleRegisterSkill(req, res)
            }
            // 更新 Skill：/api/skills/{name}（P0-2）
            const skillMatch = pathname.match(/^\/api\/skills\/([^/]+)$/)
            if (skillMatch && method === 'PUT') {
              return handleUpdateSkill(req, res, skillMatch[1])
            }

            // 3. 未匹配的 /api/ 请求 → 放行到 proxy（实际会失败，但便于发现遗漏）
            return next()
          } catch (err) {
            console.error('[mock] 处理请求出错:', err)
            return sendJson(res, 500, {
              code: 50000,
              data: null,
              message: 'Mock server 内部错误',
            })
          }
        },
      )
    },
  }
}

// ============================================================
// 工具函数
// ============================================================

/** 发送 JSON 响应（对齐 BaseResponse 结构） */
function sendJson(
  res: ServerResponse,
  status: number,
  body: { code: number; data: unknown; message: string },
): void {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify(body))
}

/** 读取请求 body（解析为 JSON） */
function readBody(req: IncomingMessage): Promise<Record<string, unknown>> {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (chunk: Buffer) => {
      raw += chunk.toString()
    })
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
    req.on('error', () => resolve({}))
  })
}

/** 解析 query string */
function parseQuery(url: string): Record<string, string> {
  const q = url.split('?')[1]
  if (!q) return {}
  const result: Record<string, string> = {}
  for (const pair of q.split('&')) {
    const [k, v] = pair.split('=')
    if (k) result[decodeURIComponent(k)] = v ? decodeURIComponent(v) : ''
  }
  return result
}

/** 生成简化雪花 ID（时间戳 + 随机数，长度 18 位） */
function genId(): string {
  const ts = Date.now().toString()
  const rand = Math.floor(Math.random() * 1_000_000)
    .toString()
    .padStart(6, '0')
  return ts + rand
}

/** 当前时间 ISO 字符串 */
function nowIso(): string {
  return new Date().toISOString()
}

// ============================================================
// Auth 接口
// ============================================================

/** POST /api/auth/login */
async function handleLogin(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const body = await readBody(req)
  console.log('[mock] 登录请求:', body)
  // 任意账号密码均可登录
  sendJson(res, 200, {
    code: 0,
    data: {
      accessToken: 'mock-access-token-' + Date.now(),
      refreshToken: 'mock-refresh-token-' + Date.now(),
      expiresIn: 7200,
      user: {
        userId: MOCK_USER.userId,
        username: (body.username as string) || 'admin_demo_yhsec',
        realName: MOCK_USER.realName,
        orgId: MOCK_USER.orgId,
        orgName: MOCK_USER.orgName,
        deptName: MOCK_USER.deptName,
        roles: MOCK_USER.roles,
      },
    },
    message: 'ok',
  })
}

/** GET /api/auth/me */
function handleGetCurrentUser(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: {
      ...MOCK_USER,
      avatar: undefined,
      email: 'admin@yhsec.com',
      phone: '138****8888',
    },
    message: 'ok',
  })
}

/** POST /api/auth/logout */
function handleLogout(res: ServerResponse): void {
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** POST /api/auth/refresh */
async function handleRefresh(req: IncomingMessage, res: ServerResponse): Promise<void> {
  console.log('[mock] 刷新 token')
  sendJson(res, 200, {
    code: 0,
    data: {
      accessToken: 'mock-access-token-refreshed-' + Date.now(),
      refreshToken: 'mock-refresh-token-refreshed-' + Date.now(),
      expiresIn: 7200,
      user: {
        userId: MOCK_USER.userId,
        username: 'admin_demo_yhsec',
        realName: MOCK_USER.realName,
        orgId: MOCK_USER.orgId,
        orgName: MOCK_USER.orgName,
        deptName: MOCK_USER.deptName,
        roles: MOCK_USER.roles,
      },
    },
    message: 'ok',
  })
}

// ============================================================
// Task 接口
// ============================================================

/** GET /api/tasks */
function handleListTasks(req: IncomingMessage, res: ServerResponse): void {
  const q = parseQuery(req.url || '')
  const current = Number(q.current) || 1
  const pageSize = Number(q.pageSize) || 10
  const status = q.status || ''
  const searchText = (q.searchText || '').toLowerCase()
  const workflowId = q.workflowId || ''
  // M7.6 三维度 RBAC：业务线 / 部门筛选参数
  const businessLineId = q.businessLineId || ''
  const departmentId = q.departmentId || ''

  // 1. 过滤
  let filtered = mockTasks.filter((t) => t.orgId === MOCK_USER.orgId)
  if (status) {
    filtered = filtered.filter((t) => t.status === status)
  }
  if (searchText) {
    filtered = filtered.filter((t) => t.goal.toLowerCase().includes(searchText))
  }
  if (workflowId) {
    // 工作流执行历史：按 workflowId 筛选（mock task 无 workflowId 则被过滤）
    filtered = filtered.filter((t) => t.workflowId === workflowId)
  }
  if (businessLineId) {
    // M7.6：业务线筛选（mock 数据未指定时默认返回 2001-证券交易，按默认值匹配）
    filtered = filtered.filter(
      (t) => (t.businessLineId ?? '2001') === businessLineId,
    )
  }
  if (departmentId) {
    // M7.6：部门筛选
    filtered = filtered.filter((t) => (t.departmentId ?? '1001') === departmentId)
  }

  // 2. 排序（按创建时间倒序）
  filtered.sort((a, b) => b.createTime.localeCompare(a.createTime))

  // 3. 分页
  const total = filtered.length
  const start = (current - 1) * pageSize
  const records = filtered.slice(start, start + pageSize)

  // 4. 返回 IPage 结构（对齐 MyBatis-Plus）
  sendJson(res, 200, {
    code: 0,
    data: {
      records: records.map((t) => {
        // 4.1 关联工作流模板获取风险等级
        const workflow = t.workflowId
          ? mockWorkflows.find((w) => w.workflowId === t.workflowId)
          : undefined
        // 4.2 计算耗时（仅终态任务）
        const isTerminal = ['SUCCESS', 'FAILED', 'ABORTED', 'NEEDS_HUMAN'].includes(t.status)
        const durationMs = isTerminal
          ? new Date(t.updateTime).getTime() - new Date(t.createTime).getTime()
          : undefined
        return {
          taskId: t.taskId,
          orgId: t.orgId,
          userId: t.userId,
          goal: t.goal,
          status: t.status,
          currentStep: t.currentStep,
          totalSteps: t.totalSteps,
          message: t.message,
          errorMessage: t.errorMessage,
          userName: MOCK_USER.realName,
          durationMs,
          riskLevel: workflow?.riskLevel,
          // M7.6 三维度 RBAC：部门/业务线（mock 数据：未指定时统一返回"财务部 / 证券交易"）
          departmentId: t.departmentId ?? '1001',
          departmentName: t.departmentName ?? '财务部',
          businessLineId: t.businessLineId ?? '2001',
          businessLineName: t.businessLineName ?? '证券交易',
          createTime: t.createTime,
          updateTime: t.updateTime,
        }
      }),
      current,
      size: pageSize,
      total,
      pages: Math.ceil(total / pageSize),
    },
    message: 'ok',
  })
}

/** GET /api/tasks/:taskId */
function handleGetTaskDetail(res: ServerResponse, taskId: string): void {
  const task = mockTasks.find((t) => t.taskId === taskId)
  if (!task) {
    return sendJson(res, 200, {
      code: 40400,
      data: null,
      message: `任务 ${taskId} 不存在`,
    })
  }
  sendJson(res, 200, { code: 0, data: task, message: 'ok' })
}

/** POST /api/ai/tasks */
async function handleTriggerTask(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const body = await readBody(req)
  console.log('[mock] 触发任务:', body)

  // 1. 创建新任务
  const taskId = genId()
  const newTask: MockTask = {
    taskId,
    orgId: MOCK_USER.orgId,
    userId: MOCK_USER.userId,
    goal: (body.goal as string) || '未命名任务',
    status: 'PENDING',
    currentStep: 0,
    totalSteps: 0,
    params: body.params ? JSON.stringify(body.params) : undefined,
    workflowId: body.workflowId as string | undefined,
    createTime: nowIso(),
    updateTime: nowIso(),
    subtasks: [],
  }
  mockTasks.unshift(newTask)

  // 2. 异步模拟任务执行（不阻塞响应）
  setTimeout(() => simulateTaskExecution(taskId), 500)

  sendJson(res, 200, {
    code: 0,
    data: {
      taskId,
      status: 'PENDING',
      message: '任务已触发',
    },
    message: 'ok',
  })
}

/** POST /api/tasks/:taskId/abort */
function handleAbortTask(res: ServerResponse, taskId: string): void {
  const task = mockTasks.find((t) => t.taskId === taskId)
  if (task && (task.status === 'PENDING' || task.status === 'EXECUTING')) {
    task.status = 'ABORTED'
    task.updateTime = nowIso()
    task.errorMessage = '用户主动终止'
    // 通知 SSE 流终止（通过 activeStreams 注册的控制器）
    const ctrl = activeStreams.get(taskId)
    if (ctrl) {
      ctrl.aborted = true
    }
  }
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** POST /api/tasks/:taskId/resume（M4.4：任务续跑） */
function handleResumeTask(res: ServerResponse, taskId: string): void {
  const task = mockTasks.find((t) => t.taskId === taskId)
  if (!task) {
    return sendJson(res, 200, { code: 40400, data: null, message: `任务 ${taskId} 不存在` })
  }
  // 校验状态：仅 FAILED / NEEDS_HUMAN 可续跑
  if (task.status !== 'FAILED' && task.status !== 'NEEDS_HUMAN') {
    return sendJson(res, 200, {
      code: 50001,
      data: null,
      message: `仅失败或需人工介入的任务可续跑，当前状态: ${task.status}`,
    })
  }
  // 模拟续跑：状态 → EXECUTING，清除错误信息
  task.status = 'EXECUTING'
  task.errorMessage = undefined
  task.message = '任务续跑中（从断点继续）'
  task.updateTime = nowIso()
  console.log(`[mock] 任务续跑: task=${taskId}`)
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

// ============================================================
// SSE 实时事件流
// ============================================================

/** 活跃 SSE 流控制器（用于 abort 时中断） */
interface StreamCtrl {
  aborted: boolean
  closed: boolean
}
const activeStreams = new Map<string, StreamCtrl>()

/** GET /api/ai/sse/tasks/:taskId */
function handleSseStream(
  req: IncomingMessage,
  res: ServerResponse,
  taskId: string,
): void {
  console.log(`[mock] SSE 连接: task=${taskId}`)

  // 1. 设置 SSE 响应头
  res.statusCode = 200
  res.setHeader('Content-Type', 'text/event-stream; charset=utf-8')
  res.setHeader('Cache-Control', 'no-cache, no-transform')
  res.setHeader('Connection', 'keep-alive')
  res.setHeader('X-Accel-Buffering', 'no')
  res.flushHeaders?.()

  // 2. 注册流控制器
  const ctrl: StreamCtrl = { aborted: false, closed: false }
  activeStreams.set(taskId, ctrl)

  // 3. 心跳（每 15s 发送注释行，防止连接超时）
  const heartbeat = setInterval(() => {
    if (ctrl.closed) return
    res.write(': heartbeat\n\n')
  }, 15000)

  // 4. 客户端关闭连接时清理
  req.on('close', () => {
    ctrl.closed = true
    clearInterval(heartbeat)
    activeStreams.delete(taskId)
    console.log(`[mock] SSE 连接关闭: task=${taskId}`)
  })

  // 5. 选择场景
  const scenario = detectScenario(taskId)
  console.log(`[mock] SSE 场景: ${scenario}`)

  // 6. 推送事件序列
  runScenario(scenario, taskId, res, ctrl)
    .then(() => {
      if (!ctrl.closed) {
        ctrl.closed = true
        clearInterval(heartbeat)
        activeStreams.delete(taskId)
        res.end()
        console.log(`[mock] SSE 流结束: task=${taskId}`)
      }
    })
    .catch((err) => {
      console.error(`[mock] SSE 流异常: task=${taskId}`, err)
      if (!ctrl.closed) {
        ctrl.closed = true
        clearInterval(heartbeat)
        activeStreams.delete(taskId)
        res.end()
      }
    })
}

/** 根据 taskId 推断场景 */
function detectScenario(taskId: string): string {
  const lower = taskId.toLowerCase()
  if (lower.includes('fail')) return 'failed'
  if (lower.includes('human')) return 'needs-human'
  if (lower.includes('replan')) return 'replan'
  if (lower.includes('abort')) return 'aborted'
  if (lower.includes('pending')) return 'pending'
  return 'success'
}

/** 发送单个 SSE 事件 */
function sendSseEvent(
  res: ServerResponse,
  event: string,
  data: Record<string, unknown>,
): void {
  const payload = JSON.stringify({
    taskId: data.taskId,
    eventType: event,
    timestamp: new Date().toISOString(),
    ...data,
  })
  res.write(`event: ${event}\n`)
  res.write(`data: ${payload}\n\n`)
}

/** 等待指定毫秒（可被 abort 中断） */
function delay(ms: number, ctrl: StreamCtrl): Promise<void> {
  return new Promise((resolve) => {
    const start = Date.now()
    const timer = setInterval(() => {
      if (ctrl.aborted || ctrl.closed || Date.now() - start >= ms) {
        clearInterval(timer)
        resolve()
      }
    }, 50)
  })
}

/**
 * 按场景推送事件序列
 *
 * @param scenario 场景名
 * @param taskId 任务 ID
 * @param res 响应对象
 * @param ctrl 流控制器
 */
async function runScenario(
  scenario: string,
  taskId: string,
  res: ServerResponse,
  ctrl: StreamCtrl,
): Promise<void> {
  const task = mockTasks.find((t) => t.taskId === taskId)
  // 更新任务状态为执行中
  if (task) {
    task.status = 'EXECUTING'
    task.updateTime = nowIso()
  }

  // 1. 任务开始
  sendSseEvent(res, 'progress', {
    taskId,
    message: 'Planner 已规划 3 个子任务，开始执行',
    totalSteps: 3,
    currentStep: 0,
  })
  await delay(800, ctrl)
  if (ctrl.aborted) return sendAborted(res, taskId)

  // 2. 子任务 #1（所有场景都成功）
  await runSubtask(res, ctrl, taskId, 0, '登录企业网银系统', {
    success: true,
    screenshot: true,
    durationMs: 1200,
  })
  if (ctrl.aborted) return sendAborted(res, taskId)

  // 3. 子任务 #2（按场景分流）
  switch (scenario) {
    case 'failed':
      await runSubtask(res, ctrl, taskId, 1, '输入账号密码登录', {
        success: false,
        screenshot: true,
        durationMs: 1800,
        errorMsg: '密码错误连续 3 次，账户被临时锁定',
      })
      // 任务失败
      sendSseEvent(res, 'error', {
        taskId,
        state: 'FAILED',
        message: '任务执行失败：子任务 #2 失败且不可恢复',
        error: '密码错误连续 3 次，账户被临时锁定',
      })
      if (task) {
        task.status = 'FAILED'
        task.currentStep = 1
        task.errorMessage = '密码错误连续 3 次，账户被临时锁定'
        task.updateTime = nowIso()
      }
      return

    case 'needs-human':
      // 子任务 #2 触发验证码，需要人工介入
      sendSseEvent(res, 'step_start', {
        taskId,
        subtaskIndex: 1,
        goal: '处理图形验证码',
        totalSteps: 3,
      })
      await delay(600, ctrl)
      sendSseEvent(res, 'screenshot', {
        taskId,
        subtaskIndex: 1,
        screenshotKey: `screenshots/${taskId}/captcha.png`,
        message: '检测到图形验证码，需要人工识别',
      })
      await delay(800, ctrl)
      sendSseEvent(res, 'progress', {
        taskId,
        message: '触发 NEEDS_HUMAN：等待人工介入识别验证码',
        currentStep: 1,
        totalSteps: 3,
      })
      sendSseEvent(res, 'complete', {
        taskId,
        state: 'NEEDS_HUMAN',
        message: '任务挂起，等待人工介入',
      })
      if (task) {
        task.status = 'NEEDS_HUMAN'
        task.currentStep = 1
        task.message = '等待人工介入识别验证码'
        task.updateTime = nowIso()
      }
      return

    case 'replan':
      // 子任务 #2 第一次失败，触发重新规划
      sendSseEvent(res, 'step_start', {
        taskId,
        subtaskIndex: 1,
        goal: '点击"账户明细"菜单',
        totalSteps: 3,
      })
      await delay(500, ctrl)
      sendSseEvent(res, 'screenshot', {
        taskId,
        subtaskIndex: 1,
        screenshotKey: `screenshots/${taskId}/step2-before.png`,
      })
      await delay(400, ctrl)
      sendSseEvent(res, 'replan', {
        taskId,
        message: '页面结构变化，触发重新规划',
        totalReplans: 1,
        maxReplans: 3,
        failedSubtaskIndex: 1,
      })
      await delay(600, ctrl)
      // 重新规划后继续
      sendSseEvent(res, 'step_start', {
        taskId,
        subtaskIndex: 1,
        goal: '通过快捷入口进入账户明细',
        totalSteps: 3,
      })
      await delay(500, ctrl)
      sendSseEvent(res, 'screenshot', {
        taskId,
        subtaskIndex: 1,
        screenshotKey: `screenshots/${taskId}/step2-replan.png`,
      })
      await delay(400, ctrl)
      sendSseEvent(res, 'step_end', {
        taskId,
        subtaskIndex: 1,
        success: true,
        durationMs: 2000,
        message: '重新规划后成功进入账户明细页面',
      })
      if (task) task.currentStep = 2
      break

    case 'pending':
      // 仅发送 progress 后挂起（模拟长时间规划）
      sendSseEvent(res, 'progress', {
        taskId,
        message: 'Planner 正在分析任务复杂度…',
        currentStep: 0,
        totalSteps: 0,
      })
      return

    case 'aborted':
      // 模拟用户立即终止
      await delay(200, ctrl)
      return sendAborted(res, taskId)

    case 'success':
    default:
      await runSubtask(res, ctrl, taskId, 1, '导航到账户明细页面', {
        success: true,
        screenshot: true,
        durationMs: 1500,
      })
      if (ctrl.aborted) return sendAborted(res, taskId)
      break
  }

  // 4. 子任务 #3（成功场景）
  await runSubtask(res, ctrl, taskId, 2, '下载 2026 年 6 月流水文件', {
    success: true,
    screenshot: true,
    durationMs: 1300,
    resultData: { fileKey: 'statements/2026-06.pdf', size: 245678 },
  })
  if (ctrl.aborted) return sendAborted(res, taskId)

  // 5. 任务完成
  sendSseEvent(res, 'complete', {
    taskId,
    state: 'SUCCESS',
    message: '任务执行完成，已生成 3 个子任务结果',
  })
  if (task) {
    task.status = 'SUCCESS'
    task.currentStep = 3
    task.message = '任务执行完成'
    task.updateTime = nowIso()
  }
}

/**
 * 执行单个子任务（推送 step_start → screenshot → step_end）
 *
 * @param res 响应对象
 * @param ctrl 流控制器
 * @param taskId 任务 ID
 * @param index 子任务序号
 * @param goal 子任务目标
 * @param opts 选项（success / screenshot / durationMs / errorMsg / resultData）
 */
async function runSubtask(
  res: ServerResponse,
  ctrl: StreamCtrl,
  taskId: string,
  index: number,
  goal: string,
  opts: {
    success: boolean
    screenshot?: boolean
    durationMs: number
    errorMsg?: string
    resultData?: Record<string, unknown>
  },
): Promise<void> {
  // 1. 开始
  sendSseEvent(res, 'step_start', {
    taskId,
    subtaskIndex: index,
    goal,
    totalSteps: 3,
  })
  await delay(400, ctrl)

  // 2. 截图（中途）
  if (opts.screenshot) {
    sendSseEvent(res, 'screenshot', {
      taskId,
      subtaskIndex: index,
      screenshotKey: `screenshots/${taskId}/step${index + 1}.png`,
      message: `已截图：${goal}`,
    })
    await delay(300, ctrl)
  }

  // 3. 进度更新
  sendSseEvent(res, 'progress', {
    taskId,
    currentStep: index + 1,
    totalSteps: 3,
    message: `子任务 #${index + 1} 执行中`,
  })

  // 4. 等待执行时长
  await delay(opts.durationMs, ctrl)

  // 5. 结束
  sendSseEvent(res, 'step_end', {
    taskId,
    subtaskIndex: index,
    success: opts.success,
    durationMs: opts.durationMs,
    message: opts.success
      ? `子任务 #${index + 1} 完成`
      : `子任务 #${index + 1} 失败：${opts.errorMsg || ''}`,
    ...(opts.resultData ? { resultData: JSON.stringify(opts.resultData) } : {}),
  })

  // 6. 同步更新任务对象
  const task = mockTasks.find((t) => t.taskId === taskId)
  if (task) {
    task.currentStep = index + 1
    task.updateTime = nowIso()
    if (task.subtasks.length <= index) {
      task.subtasks.push({
        subtaskId: genId(),
        taskId,
        subtaskIndex: index,
        goal,
        status: opts.success ? 'COMPLETED' : 'FAILED',
        errorMessage: opts.errorMsg,
        resultData: opts.resultData ? JSON.stringify(opts.resultData) : undefined,
        startedAt: nowIso(),
        completedAt: nowIso(),
        createTime: task.createTime,
        updateTime: nowIso(),
      })
    } else {
      const st = task.subtasks[index]
      st.status = opts.success ? 'COMPLETED' : 'FAILED'
      st.errorMessage = opts.errorMsg
      st.resultData = opts.resultData ? JSON.stringify(opts.resultData) : undefined
      st.completedAt = nowIso()
      st.updateTime = nowIso()
    }
  }
}

/** 发送终止事件 */
function sendAborted(res: ServerResponse, taskId: string): void {
  sendSseEvent(res, 'complete', {
    taskId,
    state: 'ABORTED',
    message: '任务已被用户终止',
  })
  const task = mockTasks.find((t) => t.taskId === taskId)
  if (task) {
    task.status = 'ABORTED'
    task.errorMessage = '用户主动终止'
    task.updateTime = nowIso()
  }
}

/**
 * 异步模拟任务执行（用于触发任务后立即返回的场景）
 *
 * @param taskId 任务 ID
 */
async function simulateTaskExecution(taskId: string): Promise<void> {
  // 触发任务后不主动推送（前端会通过 SSE 接收）
  // 这里仅用于在内存中标记任务状态变化
  const task = mockTasks.find((t) => t.taskId === taskId)
  if (task) {
    task.status = 'EXECUTING'
    task.updateTime = nowIso()
  }
}

// ============================================================
// LLM NEEDS_HUMAN 队列 + 调用统计 Mock 数据（M5.6）
// ============================================================

/** Mock NEEDS_HUMAN 队列条目 */
interface MockNeedsHumanQueue {
  queueId: string
  taskId: string
  orgId: string
  /** 业务线 ID（P3 ai-monitoring 原型对齐） */
  businessLineId?: string
  /** 业务线名称 */
  businessLineName?: string
  subtaskId?: string
  contextName: string
  screenshotUrl?: string
  llmRawOutput?: string
  validationError?: string
  attempts: number
  status: 'PENDING' | 'RESOLVED'
  resolveAction?: 'skip' | 'manual' | 'abort'
  resolvedBy?: string
  resolvedAt?: string
  createTime: string
}

/** Mock NEEDS_HUMAN 队列（内存存储） */
const mockNeedsHumanQueue: MockNeedsHumanQueue[] = [
  {
    queueId: '800000000000000001',
    taskId: '700000000000000005',
    orgId: MOCK_USER.orgId,
    businessLineId: '2002',
    businessLineName: '对公信贷',
    subtaskId: '710000000000000041',
    contextName: 'executor.step',
    screenshotUrl:
      'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=bank%20login%20page%20with%20sms%20verification%20code%20input%20field%20highlighted&image_size=landscape_4_3',
    llmRawOutput:
      '{"action": "click", "selector": "#verify-btn", "reasoning": "需要点击获取验证码按钮"}',
    validationError:
      'Pydantic ValidationError: action "click" 无法完成，需要人工输入短信验证码（field: sms_code required）',
    attempts: 3,
    status: 'PENDING',
    createTime: '2026-07-31T14:05:30.000Z',
  },
  {
    queueId: '800000000000000002',
    taskId: '700000000000000003',
    orgId: MOCK_USER.orgId,
    businessLineId: '2003',
    businessLineName: '个人金融',
    subtaskId: '710000000000000022',
    contextName: 'planner.create_plan',
    screenshotUrl:
      'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=bank%20statement%20download%20page%20with%20complex%20form%20and%20date%20range%20selector&image_size=landscape_4_3',
    llmRawOutput:
      '{"steps": [{"goal": "选择日期范围"}, {"goal": "点击下载"}], "reasoning": "页面结构较简单"}',
    validationError:
      'JSON Schema 校验失败：steps[0] 缺少必填字段 completion_condition；steps[1] 缺少必填字段 failure_strategy',
    attempts: 3,
    status: 'PENDING',
    createTime: '2026-07-29T08:33:10.000Z',
  },
  {
    queueId: '800000000000000003',
    taskId: '700000000000000002',
    orgId: MOCK_USER.orgId,
    businessLineId: '2004',
    businessLineName: '保险业务',
    subtaskId: '710000000000000010',
    contextName: 'executor.step',
    screenshotUrl:
      'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=bank%20login%20page%20with%20password%20error%20message%20account%20locked&image_size=landscape_4_3',
    llmRawOutput:
      '{"action": "input", "selector": "#password", "value": "retry-password"}',
    validationError:
      '连续 3 次密码错误，账户已被临时锁定，LLM 仍尝试输入密码',
    attempts: 3,
    status: 'PENDING',
    createTime: '2026-07-28T14:25:20.000Z',
  },
  {
    queueId: '800000000000000004',
    taskId: '700000000000000001',
    orgId: MOCK_USER.orgId,
    businessLineId: '2002',
    businessLineName: '对公信贷',
    subtaskId: '710000000000000001',
    contextName: 'planner.create_plan',
    llmRawOutput:
      '{"steps": [{"goal": "登录网银", "completion_condition": "账户总览页面"}]}',
    validationError: 'JSON 解析成功但步骤数不足（最少 2 步，实际 1 步）',
    attempts: 3,
    status: 'RESOLVED',
    resolveAction: 'skip',
    resolvedBy: MOCK_USER.userId,
    resolvedAt: '2026-07-28T09:13:00.000Z',
    createTime: '2026-07-28T09:12:50.000Z',
  },
  {
    queueId: '800000000000000005',
    taskId: '700000000000000004',
    orgId: MOCK_USER.orgId,
    businessLineId: '2003',
    businessLineName: '个人金融',
    subtaskId: '710000000000000031',
    contextName: 'executor.step',
    llmRawOutput:
      '{"action": "click", "selector": "#salary-menu", "reasoning": "点击代发工资菜单"}',
    validationError: '元素 #salary-menu 不存在（页面结构已变化）',
    attempts: 3,
    status: 'RESOLVED',
    resolveAction: 'manual',
    resolvedBy: MOCK_USER.userId,
    resolvedAt: '2026-07-30T10:02:25.000Z',
    createTime: '2026-07-30T10:02:00.000Z',
  },
]

/** GET /api/llm/needs-human */
function handleListNeedsHuman(req: IncomingMessage, res: ServerResponse): void {
  const q = parseQuery(req.url || '')
  const status = q.status || ''
  const taskId = q.taskId || ''
  const businessLineId = q.businessLineId || ''

  // 1. 过滤
  let filtered = mockNeedsHumanQueue.filter((i) => i.orgId === MOCK_USER.orgId)
  if (status) {
    filtered = filtered.filter((i) => i.status === status)
  }
  if (taskId) {
    filtered = filtered.filter((i) => i.taskId === taskId)
  }
  if (businessLineId) {
    filtered = filtered.filter((i) => i.businessLineId === businessLineId)
  }

  // 2. 排序（按创建时间倒序）
  filtered.sort((a, b) => b.createTime.localeCompare(a.createTime))

  // 3. 关联任务目标（taskTitle）用于前端展示"子任务：xxx"
  const enriched = filtered.map((i) => {
    const task = mockTasks.find((t) => t.taskId === i.taskId)
    const subtask = task?.subtasks.find((s) => s.subtaskId === i.subtaskId)
    return {
      ...i,
      taskTitle: task?.goal,
      subtaskGoal: subtask?.goal,
    }
  })

  sendJson(res, 200, {
    code: 0,
    data: enriched,
    message: 'ok',
  })
}

/** GET /api/llm/needs-human/:queueId */
function handleGetNeedsHumanDetail(res: ServerResponse, queueId: string): void {
  const item = mockNeedsHumanQueue.find((i) => i.queueId === queueId)
  if (!item) {
    return sendJson(res, 200, {
      code: 40400,
      data: null,
      message: `队列 ${queueId} 不存在`,
    })
  }
  sendJson(res, 200, { code: 0, data: item, message: 'ok' })
}

/** POST /api/llm/needs-human/:queueId/resolve */
async function handleResolveNeedsHuman(
  req: IncomingMessage,
  res: ServerResponse,
  queueId: string,
): Promise<void> {
  const body = await readBody(req)
  const action = (body.action as string) || 'skip'
  const item = mockNeedsHumanQueue.find((i) => i.queueId === queueId)
  if (!item) {
    return sendJson(res, 200, {
      code: 40400,
      data: null,
      message: `队列 ${queueId} 不存在`,
    })
  }
  if (item.status !== 'PENDING') {
    return sendJson(res, 200, {
      code: 50001,
      data: null,
      message: `队列 ${queueId} 已处置，不可重复操作`,
    })
  }
  // 模拟处置
  item.status = 'RESOLVED'
  item.resolveAction = action as 'skip' | 'manual' | 'abort'
  item.resolvedBy = MOCK_USER.userId
  item.resolvedAt = nowIso()
  console.log(`[mock] NEEDS_HUMAN 处置: queue=${queueId}, action=${action}`)

  // 如果是 skip/manual，模拟任务续跑；如果是 abort，模拟任务终止
  const task = mockTasks.find((t) => t.taskId === item.taskId)
  if (task) {
    if (action === 'abort') {
      task.status = 'ABORTED'
      task.errorMessage = '操作员终止任务'
    } else {
      task.status = 'EXECUTING'
      task.errorMessage = undefined
      task.message = '人工处置后续跑中'
    }
    task.updateTime = nowIso()
  }

  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** GET /api/llm/calls/stats */
function handleGetLlmStats(req: IncomingMessage, res: ServerResponse): void {
  // 1. 时间筛选（今日/本周/本月通过 startTime/endTime 传入，Mock 简化处理）
  const q = parseQuery(req.url || '')
  const period = (q.startTime || '').slice(0, 10)
  // 2. 根据是否传入 businessLineId 略微调整数字（模拟筛选效果）
  const hasBizLine = !!q.businessLineId
  void period
  void hasBizLine
  sendJson(res, 200, {
    code: 0,
    data: {
      totalCalls: 3421,
      successCalls: 3198,
      failedCalls: 223,
      cacheHitCalls: 2131,
      cacheHitRate: 0.623,
      totalPromptTokens: 1850000,
      totalCompletionTokens: 620000,
      totalTokens: 2470000,
      totalCost: 1256.48,
      avgDurationMs: 1400,
      modelStats: [
        {
          model: 'gpt-4o-mini',
          calls: 1984,
          successCalls: 1880,
          totalTokens: 980000,
          cost: 196.2,
        },
        {
          model: 'gpt-4o',
          calls: 924,
          successCalls: 870,
          totalTokens: 1080000,
          cost: 812.6,
        },
        {
          model: 'claude-3.5',
          calls: 513,
          successCalls: 448,
          totalTokens: 410000,
          cost: 247.68,
        },
      ],
      // ===== 趋势字段（P3 ai-monitoring 原型对齐：对比上一周期） =====
      totalCallsTrendPct: 18.0,
      totalCostTrendPct: -8.0,
      cacheHitRateTrendPct: 4.2,
      avgDurationTrendPct: -12.5,
    },
    message: 'ok',
  })
}

/** Mock LLM 调用记录条目 */
interface MockLlmCallRecord {
  callId: string
  taskId?: string
  taskTitle?: string
  model: string
  contextName: string
  success: boolean
  cacheHit: boolean
  cost: number
  durationMs: number
  callTime: string
  /** 业务线 ID（筛选用） */
  businessLineId?: string
}

/** Mock LLM 调用记录（按 callTime 倒序） */
const mockLlmCallRecords: MockLlmCallRecord[] = [
  {
    callId: '900000000000000001',
    taskId: '700000000000000002',
    taskTitle: '填写转账表单',
    model: 'gpt-4o-mini',
    contextName: 'executor.step',
    success: true,
    cacheHit: false,
    cost: 0.04,
    durationMs: 980,
    callTime: '2026-07-26T14:32:08.000Z',
    businessLineId: '2002',
  },
  {
    callId: '900000000000000002',
    taskId: '700000000000000002',
    taskTitle: '点击转账按钮',
    model: 'gpt-4o-mini',
    contextName: 'executor.step',
    success: true,
    cacheHit: true,
    cost: 0.02,
    durationMs: 120,
    callTime: '2026-07-26T14:31:42.000Z',
    businessLineId: '2002',
  },
  {
    callId: '900000000000000003',
    taskId: '700000000000000002',
    taskTitle: '页面导航决策',
    model: 'gpt-4o',
    contextName: 'planner.create_plan',
    success: true,
    cacheHit: false,
    cost: 0.18,
    durationMs: 2100,
    callTime: '2026-07-26T14:30:15.000Z',
    businessLineId: '2002',
  },
  {
    callId: '900000000000000004',
    taskId: '700000000000000001',
    taskTitle: '登录网银',
    model: 'gpt-4o-mini',
    contextName: 'executor.step',
    success: true,
    cacheHit: true,
    cost: 0.03,
    durationMs: 150,
    callTime: '2026-07-26T14:28:33.000Z',
    businessLineId: '2003',
  },
  {
    callId: '900000000000000005',
    taskId: '700000000000000004',
    taskTitle: '保单字段识别',
    model: 'claude-3.5',
    contextName: 'extractor.parse',
    success: true,
    cacheHit: false,
    cost: 0.22,
    durationMs: 3200,
    callTime: '2026-07-26T14:25:09.000Z',
    businessLineId: '2004',
  },
  {
    callId: '900000000000000006',
    taskId: '700000000000000003',
    taskTitle: '导航到季度对账单页面',
    model: 'gpt-4o',
    contextName: 'planner.create_plan',
    success: true,
    cacheHit: false,
    cost: 0.16,
    durationMs: 1980,
    callTime: '2026-07-26T14:20:55.000Z',
    businessLineId: '2002',
  },
  {
    callId: '900000000000000007',
    taskId: '700000000000000003',
    taskTitle: '登录建设银行企业网银',
    model: 'gpt-4o-mini',
    contextName: 'executor.step',
    success: true,
    cacheHit: true,
    cost: 0.02,
    durationMs: 110,
    callTime: '2026-07-26T14:18:20.000Z',
    businessLineId: '2002',
  },
  {
    callId: '900000000000000008',
    taskId: '700000000000000005',
    taskTitle: '输入短信验证码',
    model: 'gpt-4o',
    contextName: 'executor.step',
    success: false,
    cacheHit: false,
    cost: 0.14,
    durationMs: 1450,
    callTime: '2026-07-26T14:15:30.000Z',
    businessLineId: '2003',
  },
  {
    callId: '900000000000000009',
    taskId: '700000000000000004',
    taskTitle: '点击代发工资菜单',
    model: 'gpt-4o-mini',
    contextName: 'executor.step',
    success: false,
    cacheHit: false,
    cost: 0.05,
    durationMs: 820,
    callTime: '2026-07-26T14:10:12.000Z',
    businessLineId: '2004',
  },
  {
    callId: '900000000000000010',
    taskId: '700000000000000001',
    taskTitle: '下载 6 月流水 PDF',
    model: 'gpt-4o-mini',
    contextName: 'executor.step',
    success: true,
    cacheHit: false,
    cost: 0.04,
    durationMs: 1050,
    callTime: '2026-07-26T14:05:48.000Z',
    businessLineId: '2003',
  },
]

/** GET /api/llm/calls（分页查询 LLM 调用记录） */
function handleListLlmCalls(req: IncomingMessage, res: ServerResponse): void {
  const q = parseQuery(req.url || '')
  const current = Math.max(1, parseInt(q.current || '1', 10) || 1)
  const pageSize = Math.max(1, parseInt(q.pageSize || '10', 10) || 10)
  const model = q.model || ''
  const taskId = q.taskId || ''
  const businessLineId = q.businessLineId || ''
  const cacheHit = q.cacheHit

  // 1. 过滤
  let filtered = mockLlmCallRecords.slice()
  if (model) {
    filtered = filtered.filter((r) => r.model === model)
  }
  if (taskId) {
    filtered = filtered.filter((r) => r.taskId === taskId)
  }
  if (businessLineId) {
    filtered = filtered.filter((r) => r.businessLineId === businessLineId)
  }
  if (cacheHit === 'true') {
    filtered = filtered.filter((r) => r.cacheHit)
  } else if (cacheHit === 'false') {
    filtered = filtered.filter((r) => !r.cacheHit)
  }

  // 2. 排序（按 callTime 倒序）
  filtered.sort((a, b) => b.callTime.localeCompare(a.callTime))

  // 3. 分页
  const total = filtered.length
  const pages = Math.max(1, Math.ceil(total / pageSize))
  const start = (current - 1) * pageSize
  const records = filtered.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: {
      records,
      current,
      size: pageSize,
      total,
      pages,
    },
    message: 'ok',
  })
}

/** GET /api/llm/calls/daily-trend（按日聚合趋势，默认近 7 日） */
function handleGetLlmDailyTrend(req: IncomingMessage, res: ServerResponse): void {
  const q = parseQuery(req.url || '')
  const businessLineId = q.businessLineId || ''
  void businessLineId
  // 近 7 日固定 Mock 数据（成本呈下降趋势，对齐原型）
  const trend = [
    { date: '2026-07-20', calls: 521, cost: 218.5, avgDurationMs: 1620 },
    { date: '2026-07-21', calls: 498, cost: 235.2, avgDurationMs: 1580 },
    { date: '2026-07-22', calls: 532, cost: 198.7, avgDurationMs: 1510 },
    { date: '2026-07-23', calls: 467, cost: 182.4, avgDurationMs: 1470 },
    { date: '2026-07-24', calls: 510, cost: 165.9, avgDurationMs: 1430 },
    { date: '2026-07-25', calls: 489, cost: 142.6, avgDurationMs: 1390 },
    { date: '2026-07-26', calls: 404, cost: 113.2, avgDurationMs: 1340 },
  ]
  sendJson(res, 200, { code: 0, data: trend, message: 'ok' })
}

// ============================================================
// 审计日志接口（M7.5）
// ============================================================

/** Mock 审计日志记录 */
interface MockAuditLog {
  auditId: string
  taskId: string
  orgId: string
  departmentId?: string
  businessLineId?: string
  userId?: string
  /** 触发用户姓名（联表 sys_user.real_name 填充，对齐原型 06-audit-logs.html 列表显示） */
  userName?: string
  /** 部门名称（联表 sys_department.dept_name 填充） */
  departmentName?: string
  /** 业务线名称（联表 sys_business_line.business_line_name 填充） */
  businessLineName?: string
  actionType: string
  targetElement?: string
  pageUrl?: string
  actionParams?: string
  executionResult: 'success' | 'failed'
  errorMessage?: string
  riskLevel?: 'low' | 'medium' | 'high' | 'critical'
  approvalId?: string
  startedAt?: string
  completedAt?: string
  durationMs?: number
  beforeScreenshotUrl?: string
  afterScreenshotUrl?: string
  llmModel?: string
  llmTokensUsed?: number
  llmCost?: number
  createTime: string
}

/** Mock 截图 URL（使用 picsum.photos 占位图） */
const MOCK_SCREENSHOT_BEFORE =
  'https://picsum.photos/seed/finrpa-before/640/400'
const MOCK_SCREENSHOT_AFTER =
  'https://picsum.photos/seed/finrpa-after/640/400'

/** Mock 审计日志数据：覆盖不同 actionType / riskLevel / executionResult / 截图 / LLM
 *
 * 关联 mock 用户：
 *   - 100001 张三 · 对公信贷部(1002) · 对公信贷(2002)
 *   - 100002 李四 · 个人金融部(1003) · 个人金融(2003)
 *   - 100003 王五 · 保险业务部(1004) · 保险业务(2004)
 */
const mockAuditLogs: MockAuditLog[] = [
  {
    auditId: '800000000000000001',
    taskId: '700000000000000001',
    orgId: MOCK_USER.orgId,
    departmentId: '1002',
    businessLineId: '2002',
    userId: '100001',
    userName: '张三',
    departmentName: '对公信贷部',
    businessLineName: '对公信贷',
    actionType: 'NAVIGATE',
    targetElement: 'https://corporate.icbc.com.cn/',
    pageUrl: 'https://corporate.icbc.com.cn/ICBCINES/financialmarketstable.jsp',
    actionParams:
      '{"url":"https://corporate.icbc.com.cn/ICBCINES/financialmarketstable.jsp"}',
    executionResult: 'success',
    riskLevel: 'low',
    startedAt: '2026-07-28T09:12:40.000Z',
    completedAt: '2026-07-28T09:12:52.000Z',
    durationMs: 12350,
    beforeScreenshotUrl: MOCK_SCREENSHOT_BEFORE,
    afterScreenshotUrl: MOCK_SCREENSHOT_AFTER,
    llmModel: 'gpt-4o-mini',
    llmTokensUsed: 320,
    llmCost: 0.00048,
    createTime: '2026-07-28T09:12:40.000Z',
  },
  {
    auditId: '800000000000000002',
    taskId: '700000000000000001',
    orgId: MOCK_USER.orgId,
    departmentId: '1002',
    businessLineId: '2002',
    userId: '100001',
    userName: '张三',
    departmentName: '对公信贷部',
    businessLineName: '对公信贷',
    actionType: 'LOGIN',
    targetElement: 'input[name="username"]',
    pageUrl: 'https://corporate.icbc.com.cn/login',
    actionParams:
      '{"username":"admin_demo_yhsec","password":"********","captcha":"a1b2"}',
    executionResult: 'success',
    riskLevel: 'high',
    approvalId: '300000000000000001',
    startedAt: '2026-07-28T09:13:00.000Z',
    completedAt: '2026-07-28T09:13:25.000Z',
    durationMs: 25180,
    beforeScreenshotUrl: MOCK_SCREENSHOT_BEFORE,
    afterScreenshotUrl: MOCK_SCREENSHOT_AFTER,
    llmModel: 'gpt-4o',
    llmTokensUsed: 1850,
    llmCost: 0.013875,
    createTime: '2026-07-28T09:13:00.000Z',
  },
  {
    auditId: '800000000000000003',
    taskId: '700000000000000001',
    orgId: MOCK_USER.orgId,
    departmentId: '1002',
    businessLineId: '2002',
    userId: '100001',
    userName: '张三',
    departmentName: '对公信贷部',
    businessLineName: '对公信贷',
    actionType: 'CLICK',
    targetElement: '#download-statement-btn',
    pageUrl: 'https://corporate.icbc.com.cn/accounts/statements',
    actionParams: '{"selector":"#download-statement-btn"}',
    executionResult: 'success',
    riskLevel: 'medium',
    startedAt: '2026-07-28T09:14:20.000Z',
    completedAt: '2026-07-28T09:14:42.000Z',
    durationMs: 22340,
    beforeScreenshotUrl: MOCK_SCREENSHOT_BEFORE,
    afterScreenshotUrl: MOCK_SCREENSHOT_AFTER,
    createTime: '2026-07-28T09:14:20.000Z',
  },
  {
    auditId: '800000000000000004',
    taskId: '700000000000000001',
    orgId: MOCK_USER.orgId,
    departmentId: '1002',
    businessLineId: '2002',
    userId: '100001',
    userName: '张三',
    departmentName: '对公信贷部',
    businessLineName: '对公信贷',
    actionType: 'FILE_DOWNLOAD',
    targetElement: 'a[href*="statement.pdf"]',
    pageUrl: 'https://corporate.icbc.com.cn/accounts/statements/download',
    actionParams:
      '{"fileKey":"statements/2026-06-icbc.pdf","size":245678,"account":"6222********1234"}',
    executionResult: 'success',
    riskLevel: 'low',
    startedAt: '2026-07-28T09:15:00.000Z',
    completedAt: '2026-07-28T09:15:42.000Z',
    durationMs: 42180,
    createTime: '2026-07-28T09:15:00.000Z',
  },
  {
    auditId: '800000000000000010',
    taskId: '700000000000000002',
    orgId: MOCK_USER.orgId,
    departmentId: '1003',
    businessLineId: '2003',
    userId: '100002',
    userName: '李四',
    departmentName: '个人金融部',
    businessLineName: '个人金融',
    actionType: 'LOGIN',
    targetElement: 'input[name="password"]',
    pageUrl: 'https://biz.cmbchina.com/login',
    actionParams:
      '{"username":"admin_demo_yhsec","password":"********","smsCode":"123456"}',
    executionResult: 'failed',
    errorMessage: '密码错误连续 3 次，账户被临时锁定',
    riskLevel: 'critical',
    approvalId: '300000000000000002',
    startedAt: '2026-07-28T14:22:15.000Z',
    completedAt: '2026-07-28T14:25:30.000Z',
    durationMs: 195000,
    beforeScreenshotUrl: MOCK_SCREENSHOT_BEFORE,
    afterScreenshotUrl: MOCK_SCREENSHOT_AFTER,
    llmModel: 'gpt-4o',
    llmTokensUsed: 2450,
    llmCost: 0.018375,
    createTime: '2026-07-28T14:22:15.000Z',
  },
  {
    auditId: '800000000000000020',
    taskId: '700000000000000003',
    orgId: MOCK_USER.orgId,
    departmentId: '1004',
    businessLineId: '2004',
    userId: '100003',
    userName: '王五',
    departmentName: '保险业务部',
    businessLineName: '保险业务',
    actionType: 'NAVIGATE',
    targetElement: 'https://biz.ccb.com/',
    pageUrl: 'https://biz.ccb.com/corporate/dashboard',
    actionParams: '{"url":"https://biz.ccb.com/corporate/dashboard"}',
    executionResult: 'success',
    riskLevel: 'low',
    startedAt: '2026-07-29T08:30:05.000Z',
    completedAt: '2026-07-29T08:30:50.000Z',
    durationMs: 45230,
    llmModel: 'gpt-4o-mini',
    llmTokensUsed: 410,
    llmCost: 0.000615,
    createTime: '2026-07-29T08:30:05.000Z',
  },
  {
    auditId: '800000000000000021',
    taskId: '700000000000000003',
    orgId: MOCK_USER.orgId,
    departmentId: '1004',
    businessLineId: '2004',
    userId: '100003',
    userName: '王五',
    departmentName: '保险业务部',
    businessLineName: '保险业务',
    actionType: 'INPUT_TEXT',
    targetElement: 'input[name="account_number"]',
    pageUrl: 'https://biz.ccb.com/corporate/quarterly',
    actionParams:
      '{"selector":"input[name=\\"account_number\\"]","value":"6227********5678"}',
    executionResult: 'success',
    riskLevel: 'medium',
    startedAt: '2026-07-29T08:31:10.000Z',
    completedAt: '2026-07-29T08:31:25.000Z',
    durationMs: 15420,
    createTime: '2026-07-29T08:31:10.000Z',
  },
  {
    auditId: '800000000000000022',
    taskId: '700000000000000003',
    orgId: MOCK_USER.orgId,
    departmentId: '1004',
    businessLineId: '2004',
    userId: '100003',
    userName: '王五',
    departmentName: '保险业务部',
    businessLineName: '保险业务',
    actionType: 'FORM_FILL',
    targetElement: 'form#quarterly-form',
    pageUrl: 'https://biz.ccb.com/corporate/quarterly',
    actionParams:
      '{"quarter":"Q2","year":2026,"account":"6227********5678","format":"pdf"}',
    executionResult: 'success',
    riskLevel: 'high',
    approvalId: '300000000000000003',
    startedAt: '2026-07-29T08:32:00.000Z',
    completedAt: '2026-07-29T08:32:10.000Z',
    durationMs: 10240,
    llmModel: 'gpt-4o',
    llmTokensUsed: 920,
    llmCost: 0.0069,
    createTime: '2026-07-29T08:32:00.000Z',
  },
  {
    auditId: '800000000000000023',
    taskId: '700000000000000003',
    orgId: MOCK_USER.orgId,
    departmentId: '1004',
    businessLineId: '2004',
    userId: '100003',
    userName: '王五',
    departmentName: '保险业务部',
    businessLineName: '保险业务',
    actionType: 'WAIT',
    targetElement: '.loading-spinner',
    pageUrl: 'https://biz.ccb.com/corporate/quarterly',
    actionParams: '{"selector":".loading-spinner","timeoutMs":30000}',
    executionResult: 'success',
    riskLevel: 'low',
    startedAt: '2026-07-29T08:32:30.000Z',
    completedAt: '2026-07-29T08:33:00.000Z',
    durationMs: 30180,
    createTime: '2026-07-29T08:32:30.000Z',
  },
  {
    auditId: '800000000000000024',
    taskId: '700000000000000003',
    orgId: MOCK_USER.orgId,
    departmentId: '1004',
    businessLineId: '2004',
    userId: '100003',
    userName: '王五',
    departmentName: '保险业务部',
    businessLineName: '保险业务',
    actionType: 'SCREENSHOT',
    targetElement: 'body',
    pageUrl: 'https://biz.ccb.com/corporate/quarterly/result',
    actionParams: '{"selector":"body"}',
    executionResult: 'success',
    riskLevel: 'low',
    startedAt: '2026-07-29T08:33:20.000Z',
    completedAt: '2026-07-29T08:33:22.000Z',
    durationMs: 1820,
    beforeScreenshotUrl: MOCK_SCREENSHOT_BEFORE,
    afterScreenshotUrl: MOCK_SCREENSHOT_AFTER,
    createTime: '2026-07-29T08:33:20.000Z',
  },
  {
    auditId: '800000000000000030',
    taskId: '700000000000000004',
    orgId: MOCK_USER.orgId,
    departmentId: '1002',
    businessLineId: '2002',
    userId: '100001',
    userName: '张三',
    departmentName: '对公信贷部',
    businessLineName: '对公信贷',
    actionType: 'CLICK',
    targetElement: 'button#transfer',
    pageUrl: 'https://biz.boc.com/transfer/confirm',
    actionParams:
      '{"selector":"button#transfer","amount":50000,"toAccount":"6214********9876"}',
    executionResult: 'failed',
    errorMessage: '风控触发：单笔转账金额超过 5 万元阈值，需要审批',
    riskLevel: 'critical',
    approvalId: '300000000000000004',
    startedAt: '2026-08-01T10:15:30.000Z',
    completedAt: '2026-08-01T10:15:45.000Z',
    durationMs: 15280,
    beforeScreenshotUrl: MOCK_SCREENSHOT_BEFORE,
    afterScreenshotUrl: MOCK_SCREENSHOT_AFTER,
    llmModel: 'gpt-4o',
    llmTokensUsed: 3200,
    llmCost: 0.024,
    createTime: '2026-08-01T10:15:30.000Z',
  },
  {
    auditId: '800000000000000031',
    taskId: '700000000000000004',
    orgId: MOCK_USER.orgId,
    departmentId: '1002',
    businessLineId: '2002',
    userId: '100001',
    userName: '张三',
    departmentName: '对公信贷部',
    businessLineName: '对公信贷',
    actionType: 'INPUT_TEXT',
    targetElement: 'input[name="to_account"]',
    pageUrl: 'https://biz.boc.com/transfer',
    actionParams:
      '{"selector":"input[name=\\"to_account\\"]","value":"6214********9876"}',
    executionResult: 'success',
    riskLevel: 'high',
    approvalId: '300000000000000004',
    startedAt: '2026-08-01T10:14:00.000Z',
    completedAt: '2026-08-01T10:14:15.000Z',
    durationMs: 15340,
    createTime: '2026-08-01T10:14:00.000Z',
  },
]

/** 排序字段白名单（对齐后端 AuditConstant.ALLOWED_SORT_FIELDS） */
const AUDIT_SORT_FIELDS = new Set([
  'auditId',
  'taskId',
  'riskLevel',
  'startedAt',
  'durationMs',
  'createTime',
])

/** 风险等级排序权重 */
const RISK_LEVEL_WEIGHT: Record<string, number> = {
  low: 1,
  medium: 2,
  high: 3,
  critical: 4,
}

/**
 * 应用筛选条件到 mock 数据
 *
 * @param logs   原始数据
 * @param params 查询参数
 * @returns 过滤后的数据（未排序、未分页）
 */
function filterAuditLogs(
  logs: MockAuditLog[],
  params: Record<string, string>,
): MockAuditLog[] {
  return logs.filter((log) => {
    if (params.taskId && log.taskId !== params.taskId) return false
    if (params.userId && log.userId !== params.userId) return false
    if (params.departmentId && log.departmentId !== params.departmentId)
      return false
    if (params.businessLineId && log.businessLineId !== params.businessLineId)
      return false
    if (params.riskLevel && log.riskLevel !== params.riskLevel) return false
    if (params.actionType && log.actionType !== params.actionType) return false
    if (params.executionResult && log.executionResult !== params.executionResult)
      return false
    if (
      params.startTime &&
      log.startedAt &&
      log.startedAt < params.startTime
    )
      return false
    if (
      params.endTime &&
      log.startedAt &&
      log.startedAt > params.endTime
    )
      return false
    return true
  })
}

/**
 * 应用排序
 *
 * @param logs  原始数据
 * @param field 排序字段
 * @param order 排序顺序
 * @returns 排序后的数据
 */
function sortAuditLogs(
  logs: MockAuditLog[],
  field: string,
  order: string,
): MockAuditLog[] {
  const sorted = [...logs]
  const dir = order === 'ascend' ? 1 : -1
  sorted.sort((a, b) => {
    let av: string | number = ''
    let bv: string | number = ''
    switch (field) {
      case 'auditId':
        av = a.auditId
        bv = b.auditId
        break
      case 'taskId':
        av = a.taskId
        bv = b.taskId
        break
      case 'riskLevel':
        av = RISK_LEVEL_WEIGHT[a.riskLevel ?? ''] ?? 0
        bv = RISK_LEVEL_WEIGHT[b.riskLevel ?? ''] ?? 0
        break
      case 'startedAt':
        av = a.startedAt ?? ''
        bv = b.startedAt ?? ''
        break
      case 'durationMs':
        av = a.durationMs ?? 0
        bv = b.durationMs ?? 0
        break
      case 'createTime':
      default:
        av = a.createTime
        bv = b.createTime
        break
    }
    if (av < bv) return -1 * dir
    if (av > bv) return 1 * dir
    return 0
  })
  return sorted
}

/** GET /api/v1/audit/logs */
function handleListAuditLogs(
  req: IncomingMessage,
  res: ServerResponse,
): void {
  const params = parseQuery(req.url || '')
  const current = Math.max(1, parseInt(params.current || '1', 10))
  const pageSize = Math.max(1, parseInt(params.pageSize || '10', 10))
  const sortField = AUDIT_SORT_FIELDS.has(params.sortField)
    ? params.sortField
    : 'createTime'
  const sortOrder = params.sortOrder === 'ascend' ? 'ascend' : 'descend'

  // 1. 过滤 → 排序 → 分页
  const filtered = filterAuditLogs(mockAuditLogs, params)
  const sorted = sortAuditLogs(filtered, sortField, sortOrder)
  const total = sorted.length
  const pages = Math.max(1, Math.ceil(total / pageSize))
  const start = (current - 1) * pageSize
  const records = sorted.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: {
      records,
      current,
      size: pageSize,
      total,
      pages,
    },
    message: 'ok',
  })
}

/** GET /api/v1/audit/logs/:auditId */
function handleGetAuditLogDetail(
  res: ServerResponse,
  auditId: string,
): void {
  const log = mockAuditLogs.find((l) => l.auditId === auditId)
  if (!log) {
    return sendJson(res, 200, {
      code: 40400,
      data: null,
      message: `审计日志 ${auditId} 不存在`,
    })
  }
  sendJson(res, 200, { code: 0, data: log, message: 'ok' })
}

/**
 * GET /api/v1/audit/logs/export
 *
 * 返回 text/csv 二进制流（UTF-8 BOM + RFC 4180 转义），
 * 文件名 `audit_logs_yyyyMMdd.csv`，对齐后端 CsvExporter。
 */
function handleExportAuditLogs(
  req: IncomingMessage,
  res: ServerResponse,
): void {
  const params = parseQuery(req.url || '')
  // 1. 过滤 + 排序（同 list，但忽略分页）
  const sortField = AUDIT_SORT_FIELDS.has(params.sortField)
    ? params.sortField
    : 'createTime'
  const sortOrder = params.sortOrder === 'ascend' ? 'ascend' : 'descend'
  const filtered = filterAuditLogs(mockAuditLogs, params)
  const sorted = sortAuditLogs(filtered, sortField, sortOrder)

  // 2. CSV 表头（与后端 CsvExporter 23 列对齐）
  const headers = [
    '审计ID',
    '任务ID',
    '组织ID',
    '部门ID',
    '业务线ID',
    '用户ID',
    '动作类型',
    '目标元素',
    '页面URL',
    '操作参数',
    '执行结果',
    '错误信息',
    '风险等级',
    '审批单ID',
    '开始时间',
    '完成时间',
    '耗时(ms)',
    '操作前截图URL',
    '操作后截图URL',
    'LLM模型',
    'LLM token用量',
    'LLM成本(美元)',
    '创建时间',
  ]

  /** RFC 4180 转义：含 , " \n \r 时用 " 包裹，内部 " 转义为 "" */
  const escape = (v: unknown): string => {
    const s = v == null ? '' : String(v)
    if (/[",\n\r]/.test(s)) {
      return `"${s.replace(/"/g, '""')}"`
    }
    return s
  }

  // 3. 拼装 CSV 内容
  const rows = [headers.map(escape).join(',')]
  for (const log of sorted) {
    rows.push(
      [
        log.auditId,
        log.taskId,
        log.orgId,
        log.departmentId ?? '',
        log.businessLineId ?? '',
        log.userId ?? '',
        log.actionType,
        log.targetElement ?? '',
        log.pageUrl ?? '',
        log.actionParams ?? '',
        log.executionResult,
        log.errorMessage ?? '',
        log.riskLevel ?? '',
        log.approvalId ?? '',
        log.startedAt ?? '',
        log.completedAt ?? '',
        log.durationMs ?? '',
        log.beforeScreenshotUrl ?? '',
        log.afterScreenshotUrl ?? '',
        log.llmModel ?? '',
        log.llmTokensUsed ?? '',
        log.llmCost ?? '',
        log.createTime,
      ]
        .map(escape)
        .join(','),
    )
  }
  // 4. UTF-8 BOM（Excel 中文不乱码）
  const bom = '\uFEFF'
  const csv = bom + rows.join('\r\n') + '\r\n'

  // 5. 设置响应头：Content-Type + Content-Disposition（RFC 5987）
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  const filename = `audit_logs_${today}.csv`
  res.statusCode = 200
  res.setHeader('Content-Type', 'text/csv; charset=UTF-8')
  res.setHeader(
    'Content-Disposition',
    `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
  )
  res.end(Buffer.from(csv, 'utf-8'))
}

// ============================================================
// 运营大屏接口（M8.2）
// 对齐 com.finrpa.dashboard.controller.DashboardController
// ============================================================

/** GET /api/v1/dashboard/overview */
function handleGetDashboardOverview(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: {
      totalTasks: '100',
      successTasks: '85',
      failedTasks: '10',
      runningTasks: '5',
      successRate: 0.85,
      avgDurationMs: 124500,
      p95DurationMs: '312000',
      llmCallCount: '156',
      llmTotalCost: 2.8473,
      llmCacheHitRate: 0.2436,
      humanTakeoverQueueSize: '3',
      avgResolveDurationMs: 185000,
      riskLevelDistribution: [
        { riskLevel: 'low', count: '60' },
        { riskLevel: 'medium', count: '30' },
        { riskLevel: 'high', count: '8' },
        { riskLevel: 'critical', count: '2' },
      ],
      // 环比趋势（对齐原型 02-dashboard.html KPI 卡片 trend 文案，今日 vs 昨日）
      taskGrowthRate: 0.12, // +12%
      successRateDelta: 0.021, // +2.1%
      llmCostDelta: -0.08, // -8%
    },
    message: 'ok',
  })
}

/** GET /api/v1/dashboard/trends?days=7 */
function handleGetDashboardTrends(
  req: IncomingMessage,
  res: ServerResponse,
): void {
  // 1. 解析 days 参数（默认 7，最大 90）
  const query = parseQuery(req.url || '')
  const days = Math.min(Math.max(Number(query.days) || 7, 1), 90)
  // 2. 生成最近 N 天的趋势数据（含周末波动）
  const points: Array<{
    date: string
    taskCount: string
    successCount: string
    failedCount: string
    cost: number
  }> = []
  const now = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(now.getDate() - i)
    const dateStr = d.toISOString().slice(0, 10)
    // 模拟波动：工作日任务多，周末任务少
    const dayOfWeek = d.getDay()
    const isWeekend = dayOfWeek === 0 || dayOfWeek === 6
    const base = isWeekend ? 6 : 14
    const taskCount = base + Math.floor(Math.random() * 8)
    const failedCount = Math.floor(taskCount * 0.12)
    const successCount = taskCount - failedCount
    const cost = Number((taskCount * 0.18 + Math.random() * 0.5).toFixed(4))
    points.push({
      date: dateStr,
      taskCount: String(taskCount),
      successCount: String(successCount),
      failedCount: String(failedCount),
      cost,
    })
  }
  sendJson(res, 200, { code: 0, data: { points }, message: 'ok' })
}

/** GET /api/v1/dashboard/business-lines */
function handleGetDashboardBusinessLines(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: [
      {
        businessLineId: '1',
        businessLineName: '银行流水下载',
        taskCount: '42',
        successCount: '38',
        successRate: 0.9048,
      },
      {
        businessLineId: '2',
        businessLineName: '保险理赔录入',
        taskCount: '28',
        successCount: '24',
        successRate: 0.8571,
      },
      {
        businessLineId: '3',
        businessLineName: '证券对账',
        taskCount: '18',
        successCount: '15',
        successRate: 0.8333,
      },
      {
        businessLineId: '4',
        businessLineName: '税务申报',
        taskCount: '12',
        successCount: '8',
        successRate: 0.6667,
      },
    ],
    message: 'ok',
  })
}

/** GET /api/v1/dashboard/errors */
function handleGetDashboardErrors(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: [
      { errorType: 'LOGIN', count: '12' },
      { errorType: 'CLICK', count: '8' },
      { errorType: 'INPUT_TEXT', count: '6' },
      { errorType: 'FILE_DOWNLOAD', count: '4' },
      { errorType: 'NAVIGATE', count: '3' },
      { errorType: 'FORM_FILL', count: '2' },
      { errorType: 'WAIT', count: '1' },
    ],
    message: 'ok',
  })
}

/** GET /api/v1/dashboard/costs */
function handleGetDashboardCosts(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: {
      totalCalls: '156',
      totalCost: 2.8473,
      totalTokens: '380000',
      cacheHitRate: 0.2436,
      modelCosts: [
        {
          model: 'gpt-4o-mini',
          calls: '85',
          cost: 0.18,
          tokens: '120000',
        },
        {
          model: 'gpt-4o',
          calls: '52',
          cost: 1.35,
          tokens: '180000',
        },
        {
          model: 'gpt-4o-2024-08-06',
          calls: '19',
          cost: 1.3173,
          tokens: '80000',
        },
      ],
    },
    message: 'ok',
  })
}

/** GET /api/v1/dashboard/approvals */
function handleGetDashboardApprovals(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: {
      totalApprovals: '42',
      approvedCount: '32',
      rejectedCount: '6',
      timeoutCount: '2',
      pendingCount: '2',
      avgResponseMinutes: 18.5,
    },
    message: 'ok',
  })
}

/** GET /api/approvals — 审批列表（审批中心双栏工作台用，对齐原型 05-approval-center.html） */
function handleListApprovals(
  req: IncomingMessage,
  res: ServerResponse,
): void {
  const query = parseQuery(req.url || '')
  const status = query.status as string | undefined
  const userId = query.userId as string | undefined
  const current = Math.max(Number(query.current) || 1, 1)
  const pageSize = Math.max(Number(query.pageSize) || 10, 1)

  // 模拟审批数据（对齐原型 05-approval-center.html，含风险理由 + 任务参数）
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const allApprovals: any[] = [
    {
      approvalId: '1900000000000000001',
      taskId: 'TASK-2026-007',
      orgId: 'org-001',
      userId: 'zhaoliu',
      userName: '赵六',
      riskLevel: 'critical',
      approvalRoute: 'compliance',
      status: 'PENDING',
      riskReasoning: '关键词命中：检测到敏感关键词"放款"、"对公贷款"，命中敏感词库规则 R-012。金额超限：转账金额 ¥500,000 超出阈值 ¥100,000（5.0 倍）。LLM 风险判断：大模型综合评估为 Critical，置信度 92.4%，建议人工复核后执行。',
      requestPayload: JSON.stringify({
        goal: '对公贷款放款',
        params: {
          账户: '6225-7788-1234-5678',
          金额: '¥500,000.00',
          用途: '对公贷款放款',
        },
      }),
      timeoutAt: new Date(Date.now() + 23 * 60 * 1000 + 42 * 1000).toISOString(),
      createTime: new Date(Date.now() - 36 * 60 * 1000).toISOString(),
    },
    {
      approvalId: '1900000000000000002',
      taskId: 'TASK-2026-006',
      orgId: 'org-001',
      userId: 'lisi',
      userName: '李四',
      riskLevel: 'high',
      approvalRoute: 'department',
      status: 'PENDING',
      riskReasoning: '金额超限：跨行转账金额 ¥85,000 超出单笔阈值 ¥50,000。非工作时间操作：当前为非营业时间跨行转账请求。',
      requestPayload: JSON.stringify({
        goal: '跨行转账核对',
        params: {
          账户: '6225-3300-9876-5432',
          金额: '¥85,000.00',
          用途: '供应商货款',
        },
      }),
      timeoutAt: new Date(Date.now() + 2 * 3600 * 1000 + 15 * 60 * 1000).toISOString(),
      createTime: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
    },
    {
      approvalId: '1900000000000000003',
      taskId: 'TASK-2026-005',
      orgId: 'org-001',
      userId: 'wangwu',
      userName: '王五',
      riskLevel: 'high',
      approvalRoute: 'department',
      status: 'PENDING',
      riskReasoning: '理赔金额超限：单笔理赔金额 ¥120,000 超出授权额度 ¥50,000。受益人信息变更：受益人账户近期发生过变更，需人工核实。',
      requestPayload: JSON.stringify({
        goal: '理赔审核提交',
        params: {
          保单号: 'POL-2026-008231',
          金额: '¥120,000.00',
          理赔类型: '意外伤害',
        },
      }),
      timeoutAt: new Date(Date.now() + 5 * 3600 * 1000 + 42 * 60 * 1000).toISOString(),
      createTime: new Date(Date.now() - 18 * 60 * 1000).toISOString(),
    },
    // 历史：已通过
    {
      approvalId: '1900000000000000004',
      taskId: 'TASK-2026-003',
      orgId: 'org-001',
      userId: 'zhangsan',
      userName: '张三',
      riskLevel: 'high',
      approvalRoute: 'department',
      status: 'APPROVED',
      riskReasoning: '金额超限：转账金额 ¥65,000 超出阈值 ¥50,000。',
      requestPayload: JSON.stringify({
        goal: '批量代发工资',
        params: { 金额: '¥65,000.00', 用途: '员工工资' },
      }),
      approvedAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(),
      approveReason: '核实无误，准予执行',
      createTime: new Date(Date.now() - 3 * 3600 * 1000).toISOString(),
    },
    // 历史：已拒绝
    {
      approvalId: '1900000000000000005',
      taskId: 'TASK-2026-002',
      orgId: 'org-001',
      userId: 'zhangsan',
      userName: '张三',
      riskLevel: 'critical',
      approvalRoute: 'compliance',
      status: 'REJECTED',
      riskReasoning: '关键词命中：检测到敏感关键词"洗钱"。金额异常：大额连续转账行为可疑。',
      requestPayload: JSON.stringify({
        goal: '可疑大额转账',
        params: { 金额: '¥980,000.00', 用途: '不明' },
      }),
      approvedAt: new Date(Date.now() - 5 * 3600 * 1000).toISOString(),
      rejectReason: '存在合规风险，已上报合规部门进一步调查',
      createTime: new Date(Date.now() - 6 * 3600 * 1000).toISOString(),
    },
    // 历史：已超时
    {
      approvalId: '1900000000000000006',
      taskId: 'TASK-2026-001',
      orgId: 'org-001',
      userId: 'zhaoliu',
      userName: '赵六',
      riskLevel: 'high',
      approvalRoute: 'department',
      status: 'TIMEOUT',
      riskReasoning: '金额超限：转账金额 ¥58,000 超出阈值 ¥50,000。',
      requestPayload: JSON.stringify({
        goal: '跨行转账',
        params: { 金额: '¥58,000.00', 用途: '货款' },
      }),
      timeoutAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(),
      createTime: new Date(Date.now() - 4 * 3600 * 1000).toISOString(),
    },
  ]

  // 按状态 + 用户 ID 筛选
  let filtered = allApprovals
  if (status && status !== '') {
    filtered = filtered.filter((a) => a.status === status)
  }
  if (userId && userId !== '') {
    filtered = filtered.filter((a) => a.userId === userId)
  }

  // 分页
  const start = (current - 1) * pageSize
  const records = filtered.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: {
      records,
      current,
      size: pageSize,
      total: filtered.length,
      pages: Math.ceil(filtered.length / pageSize),
    },
    message: 'ok',
  })
}

// ============================================================
// Workflow 接口（M3.6，对齐原型 04-workflows.html 的 6 个工作流）
// ============================================================

/** Mock 工作流模板（对齐 WorkflowVO，params/steps 为 JSON 字符串） */
const mockWorkflows: Array<{
  workflowId: string
  name: string
  description: string
  industry: 'banking' | 'insurance' | 'securities'
  riskLevel: 'low' | 'medium' | 'high' | 'critical'
  params: string
  steps: string
  version: string
  enabled: number
  createTime: string
  updateTime: string
}> = [
  {
    workflowId: '300000000000000001',
    name: '银行流水下载',
    description:
      '自动登录主流商业银行企业网银，按指定账号与日期范围下载交易流水明细，并导出为 Excel 文件供后续对账使用。',
    industry: 'banking',
    riskLevel: 'medium',
    params: JSON.stringify([
      { name: 'account', type: 'string', required: true, encrypted: true, description: '银行账号' },
      { name: 'startDate', type: 'date', required: true, encrypted: false, description: '起始日期' },
      { name: 'endDate', type: 'date', required: false, encrypted: false, description: '结束日期' },
    ]),
    steps: JSON.stringify([
      { skill: 'browser_start', params_mapping: { browser_type: 'chromium', headless: 'false', profile: 'icbc_session' } },
      { skill: 'bank_login', params_mapping: { bank: 'icbc', account: '{{account}}', password: '****', mfa: 'true' } },
      { skill: 'transaction_query', params_mapping: { date_range: '{{startDate}}:{{endDate}}', account: '{{account}}', type: 'enterprise' } },
      { skill: 'data_extract', params_mapping: { format: 'xlsx', fields: 'date,amount,balance,counterparty' } },
      { skill: 'browser_close', params_mapping: { save_session: 'true', clear_cache: 'false' } },
    ]),
    version: 'v1.2.0',
    enabled: 1,
    createTime: '2024-12-15T09:00:00.000Z',
    updateTime: '2025-07-20T10:30:00.000Z',
  },
  {
    workflowId: '300000000000000002',
    name: '跨行转账核对',
    description:
      '跨行转账后自动登录收款行网银核对到账情况，比对转账金额、收款人、时间，异常时触发人工审批。',
    industry: 'banking',
    riskLevel: 'high',
    params: JSON.stringify([
      { name: 'transferAmount', type: 'number', required: true, encrypted: false, description: '转账金额' },
      { name: 'payeeName', type: 'string', required: true, encrypted: false, description: '收款人名称' },
      { name: 'payeeAccount', type: 'string', required: true, encrypted: true, description: '收款账号' },
    ]),
    steps: JSON.stringify([
      { skill: 'browser_start', params_mapping: { browser_type: 'chromium', headless: 'false' } },
      { skill: 'bank_login', params_mapping: { bank: 'cmb', account: '{{payeeAccount}}' } },
      { skill: 'transfer_query', params_mapping: { amount: '{{transferAmount}}', payee: '{{payeeName}}' } },
      { skill: 'reconcile_check', params_mapping: { mode: 'strict', tolerance: '0.01' } },
      { skill: 'alert_notify', params_mapping: { channel: 'email', escalate: 'true' } },
      { skill: 'browser_close', params_mapping: { save_session: 'true' } },
    ]),
    version: 'v1.0.3',
    enabled: 1,
    createTime: '2025-01-08T14:20:00.000Z',
    updateTime: '2025-07-18T16:00:00.000Z',
  },
  {
    workflowId: '300000000000000003',
    name: '对公贷款放款',
    description:
      '对公贷款放款全流程自动化：合同信息核对 → 放款指令录入 → 授权人审批 → 放款执行 → 回单归档。极高风险，需合规审计部审批。',
    industry: 'banking',
    riskLevel: 'critical',
    params: JSON.stringify([
      { name: 'loanContractNo', type: 'string', required: true, encrypted: false, description: '贷款合同号' },
      { name: 'loanAmount', type: 'number', required: true, encrypted: false, description: '放款金额' },
      { name: 'borrowerAccount', type: 'string', required: true, encrypted: true, description: '借款人账号' },
      { name: 'approver', type: 'string', required: true, encrypted: false, description: '授权审批人' },
    ]),
    steps: JSON.stringify([
      { skill: 'browser_start', params_mapping: { browser_type: 'chromium' } },
      { skill: 'bank_login', params_mapping: { bank: 'icbc', profile: 'corp_loan_session' } },
      { skill: 'contract_verify', params_mapping: { contract_no: '{{loanContractNo}}' } },
      { skill: 'disbursement_input', params_mapping: { amount: '{{loanAmount}}', account: '{{borrowerAccount}}' } },
      { skill: 'approval_request', params_mapping: { approver: '{{approver}}', level: 'compliance' } },
      { skill: 'disbursement_execute', params_mapping: { confirm: 'true' } },
      { skill: 'receipt_archive', params_mapping: { format: 'pdf', storage: 'oss' } },
      { skill: 'browser_close', params_mapping: { save_session: 'true' } },
    ]),
    version: 'v2.0.1',
    enabled: 1,
    createTime: '2025-02-20T09:30:00.000Z',
    updateTime: '2025-07-25T11:15:00.000Z',
  },
  {
    workflowId: '300000000000000004',
    name: '保单申请填写',
    description:
      '自动登录保险公司核心系统，按客户信息填写保单申请表，校验字段完整性后提交，支持多险种模板。',
    industry: 'insurance',
    riskLevel: 'high',
    params: JSON.stringify([
      { name: 'applicantName', type: 'string', required: true, encrypted: false, description: '投保人姓名' },
      { name: 'applicantId', type: 'string', required: true, encrypted: true, description: '投保人身份证号' },
      { name: 'insuranceType', type: 'string', required: true, encrypted: false, description: '险种' },
      { name: 'sumInsured', type: 'number', required: true, encrypted: false, description: '保额' },
    ]),
    steps: JSON.stringify([
      { skill: 'browser_start', params_mapping: { browser_type: 'chromium' } },
      { skill: 'insurance_login', params_mapping: { platform: 'pingan' } },
      { skill: 'form_fill', params_mapping: { applicant: '{{applicantName}}', id_no: '{{applicantId}}' } },
      { skill: 'product_select', params_mapping: { type: '{{insuranceType}}', sum_insured: '{{sumInsured}}' } },
      { skill: 'form_validate', params_mapping: { rules: 'strict' } },
      { skill: 'form_submit', params_mapping: { confirm: 'true' } },
      { skill: 'browser_close', params_mapping: { save_session: 'true' } },
    ]),
    version: 'v1.1.0',
    enabled: 1,
    createTime: '2025-03-10T10:00:00.000Z',
    updateTime: '2025-07-22T14:45:00.000Z',
  },
  {
    workflowId: '300000000000000005',
    name: '理赔审核提交',
    description:
      '理赔材料自动录入与初审：OCR 识别凭证 → 信息录入理赔系统 → 规则引擎初审 → 生成审核报告并提交。',
    industry: 'insurance',
    riskLevel: 'high',
    params: JSON.stringify([
      { name: 'claimNo', type: 'string', required: true, encrypted: false, description: '理赔编号' },
      { name: 'claimAmount', type: 'number', required: true, encrypted: false, description: '理赔金额' },
      { name: 'incidentDate', type: 'date', required: true, encrypted: false, description: '出险日期' },
    ]),
    steps: JSON.stringify([
      { skill: 'browser_start', params_mapping: { browser_type: 'chromium' } },
      { skill: 'insurance_login', params_mapping: { platform: 'pingan' } },
      { skill: 'ocr_recognize', params_mapping: { doc_type: 'invoice', language: 'zh' } },
      { skill: 'claim_input', params_mapping: { claim_no: '{{claimNo}}', amount: '{{claimAmount}}' } },
      { skill: 'rule_engine_check', params_mapping: { ruleset: 'claim_v2' } },
      { skill: 'report_generate', params_mapping: { format: 'pdf' } },
      { skill: 'claim_submit', params_mapping: { confirm: 'true' } },
      { skill: 'browser_close', params_mapping: { save_session: 'true' } },
    ]),
    version: 'v1.0.5',
    enabled: 1,
    createTime: '2025-03-25T13:30:00.000Z',
    updateTime: '2025-07-19T09:20:00.000Z',
  },
  {
    workflowId: '300000000000000006',
    name: '委托下单',
    description:
      '证券委托下单自动化：登录交易终端 → 录入委托指令 → 风控校验 → 提交委托 → 回报确认。高风险，需部门审批。',
    industry: 'securities',
    riskLevel: 'high',
    params: JSON.stringify([
      { name: 'stockCode', type: 'string', required: true, encrypted: false, description: '股票代码' },
      { name: 'orderSide', type: 'string', required: true, encrypted: false, description: '买卖方向' },
      { name: 'orderPrice', type: 'number', required: true, encrypted: false, description: '委托价格' },
      { name: 'orderQty', type: 'number', required: true, encrypted: false, description: '委托数量' },
    ]),
    steps: JSON.stringify([
      { skill: 'browser_start', params_mapping: { browser_type: 'chromium' } },
      { skill: 'trader_login', params_mapping: { terminal: 'cts' } },
      { skill: 'order_input', params_mapping: { code: '{{stockCode}}', side: '{{orderSide}}' } },
      { skill: 'price_qty_fill', params_mapping: { price: '{{orderPrice}}', qty: '{{orderQty}}' } },
      { skill: 'risk_check', params_mapping: { ruleset: 'securities_v3' } },
      { skill: 'order_submit', params_mapping: { confirm: 'true' } },
      { skill: 'browser_close', params_mapping: { save_session: 'true' } },
    ]),
    version: 'v1.3.2',
    enabled: 1,
    createTime: '2025-04-05T15:00:00.000Z',
    updateTime: '2025-07-26T10:10:00.000Z',
  },
]

/** GET /api/workflows */
function handleListWorkflows(req: IncomingMessage, res: ServerResponse): void {
  const q = parseQuery(req.url || '')
  const current = Number(q.current) || 1
  const pageSize = Number(q.pageSize) || 12
  const name = (q.name || '').toLowerCase()
  const industry = q.industry || ''
  const riskLevel = q.riskLevel || ''
  // enabled：未传(undefined)或空串表示不筛选，'0'/'1' 表示筛选禁用/启用
  const enabled = q.enabled ?? ''

  // 1. 过滤
  let filtered = [...mockWorkflows]
  if (name) {
    filtered = filtered.filter((w) => w.name.toLowerCase().includes(name))
  }
  if (industry) {
    filtered = filtered.filter((w) => w.industry === industry)
  }
  if (riskLevel) {
    filtered = filtered.filter((w) => w.riskLevel === riskLevel)
  }
  if (enabled !== '') {
    filtered = filtered.filter((w) => String(w.enabled) === enabled)
  }

  // 2. 分页
  const total = filtered.length
  const start = (current - 1) * pageSize
  const records = filtered.slice(start, start + pageSize)

  // 3. 返回 IPage 结构
  sendJson(res, 200, {
    code: 0,
    data: {
      records,
      current,
      size: pageSize,
      total,
      pages: Math.ceil(total / pageSize),
    },
    message: 'ok',
  })
}

/** GET /api/workflows/:workflowId */
function handleGetWorkflowDetail(
  res: ServerResponse,
  workflowId: string,
): void {
  const workflow = mockWorkflows.find((w) => w.workflowId === workflowId)
  if (!workflow) {
    return sendJson(res, 200, {
      code: 40400,
      data: null,
      message: `工作流 ${workflowId} 不存在`,
    })
  }
  sendJson(res, 200, { code: 0, data: workflow, message: 'ok' })
}

/** POST /api/workflows/:workflowId/run */
async function handleRunWorkflow(
  req: IncomingMessage,
  res: ServerResponse,
  workflowId: string,
): Promise<void> {
  const body = await readBody(req)
  console.log('[mock] 触发工作流执行:', workflowId, body)

  const workflow = mockWorkflows.find((w) => w.workflowId === workflowId)
  if (!workflow) {
    return sendJson(res, 200, {
      code: 40400,
      data: null,
      message: `工作流 ${workflowId} 不存在`,
    })
  }

  // 生成新任务 ID（对齐 WorkflowRunVO）
  const taskId = genId()
  sendJson(res, 200, {
    code: 0,
    data: {
      taskId,
      workflowId,
      state: 'PENDING',
    },
    message: 'ok',
  })
}

// ============================================================
// 租户接口（M7.6 三维度 RBAC：部门 / 业务线列表，用于任务列表筛选）
// ============================================================

/** Mock 部门列表（对齐原型 03-tasks.html 筛选栏：对公信贷部 / 个人金融部 / 保险业务部 / 资金运营部 / 同业业务部） */
const MOCK_DEPARTMENTS = [
  { deptId: '1001', deptName: '财务部', deptCode: 'FIN', parentId: '0', sortOrder: 1, status: 1 },
  { deptId: '1002', deptName: '对公信贷部', deptCode: 'CORP', parentId: '0', sortOrder: 2, status: 1 },
  { deptId: '1003', deptName: '个人金融部', deptCode: 'RETAIL', parentId: '0', sortOrder: 3, status: 1 },
  { deptId: '1004', deptName: '保险业务部', deptCode: 'INS', parentId: '0', sortOrder: 4, status: 1 },
  { deptId: '1005', deptName: '资金运营部', deptCode: 'TREASURY', parentId: '0', sortOrder: 5, status: 1 },
  { deptId: '1006', deptName: '同业业务部', deptCode: 'IB', parentId: '0', sortOrder: 6, status: 1 },
]

/** Mock 业务线列表（对齐原型 03-tasks.html 筛选栏：对公信贷 / 个人金融 / 保险业务 / 同业业务 / 资金运营） */
const MOCK_BUSINESS_LINES = [
  { businessLineId: '2001', businessLineName: '证券交易', businessLineCode: 'SEC', description: '证券交易业务线', sortOrder: 1, status: 1 },
  { businessLineId: '2002', businessLineName: '对公信贷', businessLineCode: 'CORP_LOAN', description: '对公信贷业务线', sortOrder: 2, status: 1 },
  { businessLineId: '2003', businessLineName: '个人金融', businessLineCode: 'RETAIL_FIN', description: '个人金融业务线', sortOrder: 3, status: 1 },
  { businessLineId: '2004', businessLineName: '保险业务', businessLineCode: 'INSURANCE', description: '保险业务线', sortOrder: 4, status: 1 },
  { businessLineId: '2005', businessLineName: '同业业务', businessLineCode: 'INTERBANK', description: '同业业务线', sortOrder: 5, status: 1 },
  { businessLineId: '2006', businessLineName: '资金运营', businessLineCode: 'TREASURY_OP', description: '资金运营业务线', sortOrder: 6, status: 1 },
]

/** GET /api/tenant/departments */
function handleListDepartments(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: MOCK_DEPARTMENTS,
    message: 'ok',
  })
}

/** GET /api/tenant/business-lines */
function handleListBusinessLines(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: MOCK_BUSINESS_LINES,
    message: 'ok',
  })
}

// ============================================================
// P4 settings 原型对齐：用户/角色/通知配置 Mock 数据
// ============================================================

/**
 * Mock 用户列表（P1 USR-1，对齐后端 UserVO）
 *
 * 字段说明：
 * - status: 0-禁用 1-启用（替代旧版 enabled 布尔）
 * - roles: 角色编码列表（联表 sys_role.role_code）
 */
const MOCK_USERS = [
  {
    userId: '100001',
    username: 'zhangsan',
    realName: '张三',
    deptName: '对公信贷部',
    roles: ['operator'],
    status: 1,
    createTime: '2026-06-01T09:00:00.000Z',
    updateTime: '2026-06-01T09:00:00.000Z',
  },
  {
    userId: '100002',
    username: 'lisi',
    realName: '李四',
    deptName: '个人金融部',
    roles: ['approver'],
    status: 1,
    createTime: '2026-06-05T14:30:00.000Z',
    updateTime: '2026-06-05T14:30:00.000Z',
  },
  {
    userId: '100003',
    username: 'wangwu',
    realName: '王五',
    deptName: '保险业务部',
    roles: ['viewer'],
    status: 1,
    createTime: '2026-06-10T10:15:00.000Z',
    updateTime: '2026-06-10T10:15:00.000Z',
  },
  {
    userId: '100004',
    username: 'zhaoliu',
    realName: '赵六',
    deptName: '对公信贷部',
    roles: ['operator'],
    status: 0,
    createTime: '2026-06-15T16:45:00.000Z',
    updateTime: '2026-07-20T09:00:00.000Z',
  },
]

/** 用户 ID 自增计数器（Mock 用） */
let mockUserIdSeq = 100005

/**
 * Mock 角色列表（P1 USR-2，对齐后端 RoleVO）
 *
 * 字段说明：
 * - builtIn: 内置角色标记（super_admin / org_admin / operator / approver / viewer）
 * - permissionScope / mutualExclusion: 原型对齐字段（仅前端展示用，后端 RoleVO 不返回）
 */
const MOCK_ROLES = [
  {
    roleId: '200001',
    roleCode: 'operator',
    roleName: '操作员',
    description: '任务执行 · Skill 调用 · 数据查看',
    orgId: null,
    isCrossOrgRead: 0,
    isCrossOrgApprove: 0,
    status: 1,
    builtIn: true,
    permissionScope: '任务执行 · Skill 调用 · 数据查看',
    mutualExclusion: '不可兼任 approver',
    createTime: '2026-06-01T09:00:00.000Z',
    updateTime: '2026-06-01T09:00:00.000Z',
  },
  {
    roleId: '200002',
    roleCode: 'approver',
    roleName: '审批员',
    description: '审批处理 · 风险查看 · 审计日志',
    orgId: null,
    isCrossOrgRead: 0,
    isCrossOrgApprove: 0,
    status: 1,
    builtIn: true,
    permissionScope: '审批处理 · 风险查看 · 审计日志',
    mutualExclusion: '不可兼任 operator',
    createTime: '2026-06-01T09:00:00.000Z',
    updateTime: '2026-06-01T09:00:00.000Z',
  },
  {
    roleId: '200003',
    roleCode: 'viewer',
    roleName: '观察员',
    description: '数据查看 · 报表导出',
    orgId: null,
    isCrossOrgRead: 0,
    isCrossOrgApprove: 0,
    status: 1,
    builtIn: true,
    permissionScope: '数据查看 · 报表导出',
    mutualExclusion: '',
    createTime: '2026-06-01T09:00:00.000Z',
    updateTime: '2026-06-01T09:00:00.000Z',
  },
]

/** 角色 ID 自增计数器（Mock 用） */
let mockRoleIdSeq = 200004

/** 内置角色编码保护列表（对齐后端 RoleConstant.BUILT_IN_ROLE_CODES） */
const BUILT_IN_ROLE_CODES = [
  'super_admin',
  'org_admin',
  'operator',
  'approver',
  'viewer',
]

/**
 * Mock 通知通道配置（含 webhookUrl / enabled 字段）
 *
 * 说明：后端 ChannelVO 仅含 channel/label/configured，
 * Mock 端在原型对齐时补充 webhookUrl（明文展示）与 enabled（开关状态）。
 */
const MOCK_NOTIFICATION_CHANNELS = [
  {
    channel: 'wecom' as const,
    label: '企业微信',
    configured: true,
    webhookUrl: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx',
    enabled: true,
  },
  {
    channel: 'dingtalk' as const,
    label: '钉钉',
    configured: true,
    webhookUrl: 'https://oapi.dingtalk.com/robot/send?access_token=xxx',
    enabled: true,
  },
]

/**
 * Mock 通知模板配置列表（对齐原型 08-settings.html 通知模板勾选：4 项）
 */
const MOCK_NOTIFICATION_TEMPLATES = [
  {
    templateType: 'APPROVAL_PENDING' as const,
    label: '审批待处理',
    description: '当有新审批任务待处理时通知审批人',
    frequency: 'high' as const,
    enabled: true,
  },
  {
    templateType: 'TASK_FAILED' as const,
    label: '任务超时',
    description: '任务执行超过预设时长时通知负责人',
    frequency: 'high' as const,
    enabled: true,
  },
  {
    templateType: 'NEEDS_HUMAN' as const,
    label: 'NEEDS_HUMAN 触发',
    description: 'LLM 校验失败需人工接管时立即通知',
    frequency: 'urgent' as const,
    enabled: true,
  },
  {
    templateType: 'RISK_ESCALATION' as const,
    label: '风险升级',
    description: '风险等级升级到高/严重时通知合规岗',
    frequency: 'urgent' as const,
    enabled: false,
  },
]

/** 通知配置最后保存时间（Mock 内存状态，每次保存更新） */
let notificationConfigSavedAt = '2026-07-26T06:00:00.000Z'

// ============================================================
// P1 USR-1 用户管理 Mock handlers（对齐 UserController）
// ============================================================

/** GET /api/users（分页查询，对齐 UserQueryRequest） */
function handleListUsers(req: IncomingMessage, res: ServerResponse): void {
  const url = new URL(req.url ?? '', 'http://localhost')
  const current = Number(url.searchParams.get('current') ?? '1')
  const pageSize = Number(url.searchParams.get('pageSize') ?? '10')
  const keyword = url.searchParams.get('keyword') ?? ''
  const status = url.searchParams.get('status') ?? ''

  let records = MOCK_USERS.filter((u) => {
    if (keyword && !u.username.includes(keyword) && !u.realName.includes(keyword)) {
      return false
    }
    if (status !== '' && String(u.status) !== status) return false
    return true
  })

  const total = records.length
  const pages = Math.ceil(total / pageSize) || 1
  const start = (current - 1) * pageSize
  records = records.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: { records, total, current, size: pageSize, pages },
    message: 'ok',
  })
}

/** GET /api/users/{userId} */
function handleGetUser(res: ServerResponse, userId: string): void {
  const target = MOCK_USERS.find((u) => u.userId === userId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `用户不存在: ${userId}` })
    return
  }
  sendJson(res, 200, { code: 0, data: target, message: 'ok' })
}

/** POST /api/users（新增） */
async function handleAddUser(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const username = body.username as string
  if (!username) {
    sendJson(res, 200, { code: 40000, data: null, message: '用户名不能为空' })
    return
  }
  if (MOCK_USERS.some((u) => u.username === username)) {
    sendJson(res, 200, { code: 50001, data: null, message: `用户名已存在: ${username}` })
    return
  }
  const now = nowIso()
  const newUser = {
    userId: String(mockUserIdSeq++),
    username,
    realName: (body.realName as string) ?? '',
    deptName: (body.deptName as string) ?? '',
    roles: [] as string[],
    status: body.status !== undefined ? Number(body.status) : 1,
    createTime: now,
    updateTime: now,
  }
  MOCK_USERS.unshift(newUser)
  sendJson(res, 200, { code: 0, data: newUser.userId, message: 'ok' })
}

/** PUT /api/users（编辑，对齐 UserUpdateRequest） */
async function handleUpdateUser(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const userId = body.userId as string
  const target = MOCK_USERS.find((u) => u.userId === userId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `用户不存在: ${userId}` })
    return
  }
  if (body.realName !== undefined) target.realName = body.realName as string
  if (body.deptName !== undefined) target.deptName = body.deptName as string
  if (body.email !== undefined) (target as { email?: string }).email = body.email as string
  if (body.phone !== undefined) (target as { phone?: string }).phone = body.phone as string
  if (body.status !== undefined) target.status = Number(body.status)
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** PUT /api/users/{userId}/status（启停） */
async function handleToggleUserStatus(
  req: IncomingMessage,
  res: ServerResponse,
  userId: string,
): Promise<void> {
  const url = new URL(req.url ?? '', 'http://localhost')
  const status = Number(url.searchParams.get('status') ?? '1')
  const target = MOCK_USERS.find((u) => u.userId === userId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `用户不存在: ${userId}` })
    return
  }
  target.status = status
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** PUT /api/users/reset-password */
async function handleResetPassword(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const userId = body.userId as string
  const target = MOCK_USERS.find((u) => u.userId === userId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `用户不存在: ${userId}` })
    return
  }
  // Mock 端不存储密码，仅返回成功
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** DELETE /api/users/{userId} */
function handleDeleteUser(res: ServerResponse, userId: string): void {
  const idx = MOCK_USERS.findIndex((u) => u.userId === userId)
  if (idx < 0) {
    sendJson(res, 200, { code: 40400, data: null, message: `用户不存在: ${userId}` })
    return
  }
  MOCK_USERS.splice(idx, 1)
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** POST /api/users/roles（分配角色，全量替换语义） */
async function handleAssignUserRoles(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const userId = body.userId as string
  const target = MOCK_USERS.find((u) => u.userId === userId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `用户不存在: ${userId}` })
    return
  }
  const relations = (body.relations as Array<{ roleId: string }>) || []
  // 1. 全量替换：根据 roleId 反查 roleCode
  const roleCodes: string[] = []
  for (const rel of relations) {
    const role = MOCK_ROLES.find((r) => r.roleId === rel.roleId)
    if (role) roleCodes.push(role.roleCode)
  }
  target.roles = roleCodes
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

// ============================================================
// P1 USR-2 角色管理 Mock handlers（对齐 RoleController）
// ============================================================

/** GET /api/roles（分页查询，对齐 RoleQueryRequest） */
function handleListRoles(req: IncomingMessage, res: ServerResponse): void {
  const url = new URL(req.url ?? '', 'http://localhost')
  const current = Number(url.searchParams.get('current') ?? '1')
  const pageSize = Number(url.searchParams.get('pageSize') ?? '10')
  const keyword = url.searchParams.get('keyword') ?? ''
  const status = url.searchParams.get('status') ?? ''

  let records = MOCK_ROLES.filter((r) => {
    if (keyword && !r.roleName.includes(keyword) && !r.roleCode.includes(keyword)) {
      return false
    }
    if (status !== '' && String(r.status) !== status) return false
    return true
  })

  const total = records.length
  const pages = Math.ceil(total / pageSize) || 1
  const start = (current - 1) * pageSize
  records = records.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: { records, total, current, size: pageSize, pages },
    message: 'ok',
  })
}

/** GET /api/roles/all（不分页，用于分配角色下拉选项） */
function handleListAllRoles(res: ServerResponse): void {
  const enabledRoles = MOCK_ROLES.filter((r) => r.status === 1)
  sendJson(res, 200, { code: 0, data: enabledRoles, message: 'ok' })
}

/** GET /api/roles/{roleId} */
function handleGetRole(res: ServerResponse, roleId: string): void {
  const target = MOCK_ROLES.find((r) => r.roleId === roleId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `角色不存在: ${roleId}` })
    return
  }
  sendJson(res, 200, { code: 0, data: target, message: 'ok' })
}

/** POST /api/roles（新增，内置角色编码保护） */
async function handleAddRole(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const roleCode = body.roleCode as string
  const roleName = body.roleName as string
  if (!roleCode || !roleName) {
    sendJson(res, 200, { code: 40000, data: null, message: '角色编码 / 名称不能为空' })
    return
  }
  if (BUILT_IN_ROLE_CODES.includes(roleCode)) {
    sendJson(res, 200, { code: 50001, data: null, message: `内置角色编码禁止新增: ${roleCode}` })
    return
  }
  if (MOCK_ROLES.some((r) => r.roleCode === roleCode)) {
    sendJson(res, 200, { code: 50001, data: null, message: `角色编码已存在: ${roleCode}` })
    return
  }
  const now = nowIso()
  const newRole = {
    roleId: String(mockRoleIdSeq++),
    roleCode,
    roleName,
    description: (body.description as string) ?? '',
    orgId: (body.orgId as string) ?? null,
    isCrossOrgRead: body.isCrossOrgRead !== undefined ? Number(body.isCrossOrgRead) : 0,
    isCrossOrgApprove: body.isCrossOrgApprove !== undefined ? Number(body.isCrossOrgApprove) : 0,
    status: body.status !== undefined ? Number(body.status) : 1,
    builtIn: false,
    permissionScope: (body.description as string) ?? '',
    mutualExclusion: '',
    createTime: now,
    updateTime: now,
  }
  MOCK_ROLES.unshift(newRole)
  sendJson(res, 200, { code: 0, data: newRole.roleId, message: 'ok' })
}

/** PUT /api/roles（编辑，对齐 RoleUpdateRequest） */
async function handleUpdateRole(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const roleId = body.roleId as string
  const target = MOCK_ROLES.find((r) => r.roleId === roleId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `角色不存在: ${roleId}` })
    return
  }
  if (body.roleName !== undefined) target.roleName = body.roleName as string
  if (body.description !== undefined) {
    target.description = body.description as string
    target.permissionScope = body.description as string
  }
  if (body.isCrossOrgRead !== undefined) target.isCrossOrgRead = Number(body.isCrossOrgRead)
  if (body.isCrossOrgApprove !== undefined) target.isCrossOrgApprove = Number(body.isCrossOrgApprove)
  if (body.status !== undefined) target.status = Number(body.status)
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** PUT /api/roles/{roleId}/status（启停，super_admin / org_admin 禁止禁用） */
async function handleToggleRoleStatus(
  req: IncomingMessage,
  res: ServerResponse,
  roleId: string,
): Promise<void> {
  const url = new URL(req.url ?? '', 'http://localhost')
  const status = Number(url.searchParams.get('status') ?? '1')
  const target = MOCK_ROLES.find((r) => r.roleId === roleId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `角色不存在: ${roleId}` })
    return
  }
  if (status === 0 && ['super_admin', 'org_admin'].includes(target.roleCode)) {
    sendJson(res, 200, { code: 50001, data: null, message: `内置管理员角色禁止禁用: ${target.roleCode}` })
    return
  }
  target.status = status
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** DELETE /api/roles/{roleId}（内置角色 + 有用户关联的角色禁止删除） */
function handleDeleteRole(res: ServerResponse, roleId: string): void {
  const target = MOCK_ROLES.find((r) => r.roleId === roleId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `角色不存在: ${roleId}` })
    return
  }
  if (target.builtIn) {
    sendJson(res, 200, { code: 50001, data: null, message: `内置角色不可删除: ${target.roleCode}` })
    return
  }
  if (MOCK_USERS.some((u) => u.roles.includes(target.roleCode))) {
    sendJson(res, 200, { code: 50001, data: null, message: `角色已关联用户，不可删除: ${target.roleCode}` })
    return
  }
  const idx = MOCK_ROLES.findIndex((r) => r.roleId === roleId)
  MOCK_ROLES.splice(idx, 1)
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

// ============================================================
// P1 RSK-1 审批超时阈值配置 Mock（对齐 ApprovalTimeoutConfigController）
// ============================================================

/**
 * Mock 审批超时配置列表
 *
 * 字段说明：
 * - riskLevel: high / critical（low / medium 无需审批，无超时配置）
 * - timeoutMinutes: 超时分钟数（对齐 ApprovalConstant 默认值：high=30, critical=60）
 */
const MOCK_APPROVAL_TIMEOUT_CONFIGS = [
  {
    configId: '500001',
    riskLevel: 'high',
    timeoutMinutes: 30,
    description: '高风险审批超时阈值（默认 30 分钟，超时后任务自动终止）',
    enabled: 1,
    createTime: '2026-06-01T08:00:00.000Z',
    updateTime: '2026-06-01T08:00:00.000Z',
  },
  {
    configId: '500002',
    riskLevel: 'critical',
    timeoutMinutes: 60,
    description: '严重风险审批超时阈值（默认 60 分钟，超时后任务自动终止并通知合规）',
    enabled: 1,
    createTime: '2026-06-01T08:00:00.000Z',
    updateTime: '2026-06-01T08:00:00.000Z',
  },
]

/** GET /api/approval-timeout */
function handleListApprovalTimeoutConfigs(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: MOCK_APPROVAL_TIMEOUT_CONFIGS,
    message: 'ok',
  })
}

/** PUT /api/approval-timeout/{riskLevel} */
async function handleUpdateApprovalTimeoutConfig(
  req: IncomingMessage,
  res: ServerResponse,
  riskLevel: string,
): Promise<void> {
  const body = await readBody(req)
  const target = MOCK_APPROVAL_TIMEOUT_CONFIGS.find((c) => c.riskLevel === riskLevel)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `超时配置不存在: ${riskLevel}` })
    return
  }
  if (body.timeoutMinutes !== undefined) {
    const minutes = Number(body.timeoutMinutes)
    if (minutes < 1 || minutes > 1440) {
      sendJson(res, 200, { code: 40000, data: null, message: '超时分钟数应在 1-1440 之间' })
      return
    }
    target.timeoutMinutes = minutes
  }
  if (body.description !== undefined) target.description = body.description as string
  if (body.enabled !== undefined) target.enabled = Number(body.enabled)
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: target, message: 'ok' })
}

// ============================================================
// P1 RSK-3 审批人映射配置 Mock（对齐 ApprovalRouteConfigController）
// ============================================================

/**
 * Mock 审批人映射列表
 *
 * 字段说明：
 * - riskLevel: high / critical
 * - businessLineId: null 表示默认路由（精确匹配未命中时回退）
 * - approverUserId / approverName: 审批人用户 ID 与姓名
 */
const MOCK_APPROVAL_ROUTE_CONFIGS = [
  {
    configId: '600001',
    orgId: '100',
    riskLevel: 'high',
    businessLineId: null,
    businessLineName: '默认路由',
    approverUserId: '100002',
    approverName: '李四',
    departmentId: null,
    description: '高风险默认审批人（部门审批人）',
    enabled: 1,
    createTime: '2026-06-01T08:00:00.000Z',
    updateTime: '2026-06-01T08:00:00.000Z',
  },
  {
    configId: '600002',
    orgId: '100',
    riskLevel: 'critical',
    businessLineId: null,
    businessLineName: '默认路由',
    approverUserId: '100003',
    approverName: '王五',
    departmentId: null,
    description: '严重风险默认审批人（合规审计部）',
    enabled: 1,
    createTime: '2026-06-01T08:00:00.000Z',
    updateTime: '2026-06-01T08:00:00.000Z',
  },
  {
    configId: '600003',
    orgId: '100',
    riskLevel: 'high',
    businessLineId: '201',
    businessLineName: '对公信贷',
    approverUserId: '100002',
    approverName: '李四',
    departmentId: '301',
    description: '对公信贷业务线高风险审批人',
    enabled: 1,
    createTime: '2026-07-10T10:00:00.000Z',
    updateTime: '2026-07-10T10:00:00.000Z',
  },
]

/** 审批人映射 ID 自增计数器 */
let mockApprovalRouteIdSeq = 600004

/** GET /api/approval-routes（分页查询） */
function handleListApprovalRouteConfigs(
  req: IncomingMessage,
  res: ServerResponse,
): void {
  const url = new URL(req.url ?? '', 'http://localhost')
  const current = Number(url.searchParams.get('current') ?? '1')
  const pageSize = Number(url.searchParams.get('pageSize') ?? '10')
  const riskLevel = url.searchParams.get('riskLevel') ?? ''
  const businessLineId = url.searchParams.get('businessLineId') ?? ''
  const enabled = url.searchParams.get('enabled') ?? ''

  let records = MOCK_APPROVAL_ROUTE_CONFIGS.filter((c) => {
    if (riskLevel && c.riskLevel !== riskLevel) return false
    if (businessLineId && (c.businessLineId ?? '') !== businessLineId) return false
    if (enabled !== '' && String(c.enabled) !== enabled) return false
    return true
  })

  const total = records.length
  const pages = Math.ceil(total / pageSize) || 1
  const start = (current - 1) * pageSize
  records = records.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: { records, total, current, size: pageSize, pages },
    message: 'ok',
  })
}

/** POST /api/approval-routes（新增） */
async function handleAddApprovalRouteConfig(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const riskLevel = body.riskLevel as string
  const approverUserId = body.approverUserId as string
  if (!riskLevel || !approverUserId) {
    sendJson(res, 200, { code: 40000, data: null, message: '风险等级 / 审批人不能为空' })
    return
  }
  // 1. 唯一性校验：同 riskLevel + businessLineId 不能重复
  const businessLineId = (body.businessLineId as string) ?? null
  const duplicate = MOCK_APPROVAL_ROUTE_CONFIGS.find(
    (c) => c.riskLevel === riskLevel && (c.businessLineId ?? null) === businessLineId,
  )
  if (duplicate) {
    sendJson(res, 200, { code: 50001, data: null, message: '同风险等级 × 业务线的映射已存在' })
    return
  }
  // 2. 反查审批人姓名
  const approver = MOCK_USERS.find((u) => u.userId === approverUserId)
  const now = nowIso()
  const newConfig = {
    configId: String(mockApprovalRouteIdSeq++),
    orgId: '100',
    riskLevel,
    businessLineId,
    businessLineName: businessLineId ? '自定义业务线' : '默认路由',
    approverUserId,
    approverName: approver?.realName ?? '',
    departmentId: (body.departmentId as string) ?? null,
    description: (body.description as string) ?? '',
    enabled: body.enabled !== undefined ? Number(body.enabled) : 1,
    createTime: now,
    updateTime: now,
  }
  MOCK_APPROVAL_ROUTE_CONFIGS.unshift(newConfig)
  sendJson(res, 200, { code: 0, data: newConfig.configId, message: 'ok' })
}

/** PUT /api/approval-routes/{configId} */
async function handleUpdateApprovalRouteConfig(
  req: IncomingMessage,
  res: ServerResponse,
  configId: string,
): Promise<void> {
  const body = await readBody(req)
  const target = MOCK_APPROVAL_ROUTE_CONFIGS.find((c) => c.configId === configId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `映射配置不存在: ${configId}` })
    return
  }
  if (body.approverUserId !== undefined) {
    target.approverUserId = body.approverUserId as string
    const approver = MOCK_USERS.find((u) => u.userId === target.approverUserId)
    target.approverName = approver?.realName ?? ''
  }
  if (body.departmentId !== undefined) target.departmentId = (body.departmentId as string) ?? null
  if (body.description !== undefined) target.description = body.description as string
  if (body.enabled !== undefined) target.enabled = Number(body.enabled)
  target.updateTime = nowIso()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** DELETE /api/approval-routes/{configId} */
function handleDeleteApprovalRouteConfig(
  res: ServerResponse,
  configId: string,
): void {
  const idx = MOCK_APPROVAL_ROUTE_CONFIGS.findIndex((c) => c.configId === configId)
  if (idx < 0) {
    sendJson(res, 200, { code: 40400, data: null, message: `映射配置不存在: ${configId}` })
    return
  }
  MOCK_APPROVAL_ROUTE_CONFIGS.splice(idx, 1)
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

// ============================================================
// P2 SEC-1 密码策略配置 Mock
// ============================================================

/** 密码策略 Mock 数据（全局单行配置） */
const MOCK_PASSWORD_POLICY = {
  policyId: '1751000000000000010',
  minLength: 8,
  requireUppercase: 1,
  requireLowercase: 1,
  requireDigit: 1,
  requireSpecial: 1,
  specialChars: '!@#$%^&*()_+-=[]{}|;:,.<>?',
  expireDays: 90,
  historyCount: 5,
  enabled: 1,
  createTime: '2026-08-04T00:00:00.000Z',
  updateTime: '2026-08-04T00:00:00.000Z',
}

/** GET /api/password-policy */
function handleGetPasswordPolicy(res: ServerResponse): void {
  sendJson(res, 200, { code: 0, data: MOCK_PASSWORD_POLICY, message: 'ok' })
}

/** PUT /api/password-policy */
async function handleUpdatePasswordPolicy(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readJsonBody(req)
  if (body.minLength !== undefined) MOCK_PASSWORD_POLICY.minLength = body.minLength
  if (body.requireUppercase !== undefined)
    MOCK_PASSWORD_POLICY.requireUppercase = body.requireUppercase
  if (body.requireLowercase !== undefined)
    MOCK_PASSWORD_POLICY.requireLowercase = body.requireLowercase
  if (body.requireDigit !== undefined)
    MOCK_PASSWORD_POLICY.requireDigit = body.requireDigit
  if (body.requireSpecial !== undefined)
    MOCK_PASSWORD_POLICY.requireSpecial = body.requireSpecial
  if (body.specialChars !== undefined)
    MOCK_PASSWORD_POLICY.specialChars = body.specialChars
  if (body.expireDays !== undefined)
    MOCK_PASSWORD_POLICY.expireDays = body.expireDays
  if (body.historyCount !== undefined)
    MOCK_PASSWORD_POLICY.historyCount = body.historyCount
  if (body.enabled !== undefined) MOCK_PASSWORD_POLICY.enabled = body.enabled
  MOCK_PASSWORD_POLICY.updateTime = new Date().toISOString()
  sendJson(res, 200, { code: 0, data: MOCK_PASSWORD_POLICY, message: 'ok' })
}

// ============================================================
// P2 SEC-2 登录安全策略 Mock
// ============================================================

/** 登录安全策略 Mock 数据（全局单行配置） */
const MOCK_LOGIN_POLICY = {
  policyId: '1751000000000000011',
  maxLoginAttempts: 5,
  lockMinutes: 30,
  ipWhitelist: '',
  ipBlacklist: '',
  allowMultiLogin: 0,
  sessionTimeoutMinutes: 30,
  enabled: 1,
  createTime: '2026-08-04T00:00:00.000Z',
  updateTime: '2026-08-04T00:00:00.000Z',
}

/** GET /api/login-policy */
function handleGetLoginPolicy(res: ServerResponse): void {
  sendJson(res, 200, { code: 0, data: MOCK_LOGIN_POLICY, message: 'ok' })
}

/** PUT /api/login-policy */
async function handleUpdateLoginPolicy(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readJsonBody(req)
  if (body.maxLoginAttempts !== undefined)
    MOCK_LOGIN_POLICY.maxLoginAttempts = body.maxLoginAttempts
  if (body.lockMinutes !== undefined)
    MOCK_LOGIN_POLICY.lockMinutes = body.lockMinutes
  if (body.ipWhitelist !== undefined)
    MOCK_LOGIN_POLICY.ipWhitelist = body.ipWhitelist
  if (body.ipBlacklist !== undefined)
    MOCK_LOGIN_POLICY.ipBlacklist = body.ipBlacklist
  if (body.allowMultiLogin !== undefined)
    MOCK_LOGIN_POLICY.allowMultiLogin = body.allowMultiLogin
  if (body.sessionTimeoutMinutes !== undefined)
    MOCK_LOGIN_POLICY.sessionTimeoutMinutes = body.sessionTimeoutMinutes
  if (body.enabled !== undefined) MOCK_LOGIN_POLICY.enabled = body.enabled
  MOCK_LOGIN_POLICY.updateTime = new Date().toISOString()
  sendJson(res, 200, { code: 0, data: MOCK_LOGIN_POLICY, message: 'ok' })
}

// ============================================================
// P2 SEC-3 在线会话管理 Mock
// ============================================================

/**
 * Mock 在线会话列表（内存存储）
 *
 * 模拟 4 个在线会话：3 个真实用户 + 1 个管理员，分布在不同 IP / 设备
 */
interface MockSession {
  sessionId: string
  userId: string
  username: string
  loginIp: string
  loginTime: string
  lastAccessTime: string
  expiresAt: string
  userAgent: string
}

const MOCK_SESSIONS: MockSession[] = [
  {
    sessionId: 'a1b2c3d4e5f60718293a4b5c6d7e8f90',
    userId: '100001',
    username: 'zhangsan',
    loginIp: '192.168.1.101',
    loginTime: new Date(Date.now() - 30 * 60_000).toISOString(),
    lastAccessTime: new Date(Date.now() - 2 * 60_000).toISOString(),
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  },
  {
    sessionId: 'b2c3d4e5f60718293a4b5c6d7e8f9011',
    userId: '100002',
    username: 'lisi',
    loginIp: '192.168.1.102',
    loginTime: new Date(Date.now() - 90 * 60_000).toISOString(),
    lastAccessTime: new Date(Date.now() - 45 * 60_000).toISOString(),
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    userAgent:
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
  },
  {
    sessionId: 'c3d4e5f60718293a4b5c6d7e8f901122',
    userId: '100003',
    username: 'wangwu',
    loginIp: '10.0.0.5',
    loginTime: new Date(Date.now() - 5 * 60_000).toISOString(),
    lastAccessTime: new Date(Date.now() - 30_000).toISOString(),
    expiresAt: new Date(Date.now() + 55 * 60_000).toISOString(),
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
  },
  {
    sessionId: 'd4e5f60718293a4b5c6d7e8f90112233',
    userId: '100001',
    username: 'zhangsan',
    loginIp: '192.168.1.201',
    loginTime: new Date(Date.now() - 120 * 60_000).toISOString(),
    lastAccessTime: new Date(Date.now() - 60 * 60_000).toISOString(),
    expiresAt: new Date(Date.now() - 5 * 60_000).toISOString(),
    userAgent:
      'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  },
]

/**
 * GET /api/sessions（分页查询，支持 userId / username 筛选）
 *
 * 查询参数：current（默认 1）、pageSize（默认 10）、userId、username
 */
function handleListSessions(req: IncomingMessage, res: ServerResponse): void {
  // 1. 解析 query
  const url = new URL(req.url ?? '', 'http://localhost')
  const current = Number(url.searchParams.get('current') ?? '1') || 1
  const pageSize = Number(url.searchParams.get('pageSize') ?? '10') || 10
  const userId = url.searchParams.get('userId') ?? ''
  const username = url.searchParams.get('username') ?? ''

  // 2. 筛选
  let list = [...MOCK_SESSIONS]
  if (userId) {
    list = list.filter((s) => s.userId === userId)
  }
  if (username) {
    list = list.filter((s) => s.username.includes(username))
  }

  // 3. 按 loginTime 倒序
  list.sort(
    (a, b) =>
      new Date(b.loginTime).getTime() - new Date(a.loginTime).getTime(),
  )

  // 4. 分页
  const total = list.length
  const start = (current - 1) * pageSize
  const records = list.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: {
      records,
      total,
      size: pageSize,
      current,
      pages: Math.ceil(total / pageSize) || 1,
    },
    message: 'ok',
  })
}

/**
 * DELETE /api/sessions/{sessionId}（踢人下线）
 *
 * 从内存列表中移除会话；找不到时返回 40404 业务码
 */
function handleKillSession(res: ServerResponse, sessionId: string): void {
  const idx = MOCK_SESSIONS.findIndex((s) => s.sessionId === sessionId)
  if (idx < 0) {
    sendJson(res, 200, {
      code: 40404,
      data: null,
      message: '会话不存在或已下线',
    })
    return
  }
  MOCK_SESSIONS.splice(idx, 1)
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

// ============================================================
// P2 OPS-1 系统健康检查 Mock
// ============================================================

/**
 * GET /api/system-health（一键检测 DB / Redis / Python AI / MinIO 连通性）
 *
 * 模拟全 UP 状态：4 个组件全部可达，整体耗时 80ms
 */
function handleCheckSystemHealth(res: ServerResponse): void {
  const now = Date.now()
  const data = {
    overallStatus: 'UP',
    checkedAt: new Date(now).toISOString(),
    durationMs: 80,
    components: [
      {
        name: 'database',
        displayName: 'PostgreSQL',
        status: 'UP',
        latencyMs: 12,
        errorMessage: null,
        detail: 'SELECT 1 → 1',
      },
      {
        name: 'redis',
        displayName: 'Redis',
        status: 'UP',
        latencyMs: 3,
        errorMessage: null,
        detail: 'keys=42',
      },
      {
        name: 'ai_service',
        displayName: 'Python AI',
        status: 'UP',
        latencyMs: 45,
        errorMessage: null,
        detail: 'GET /api/v1/ai/skills OK',
      },
      {
        name: 'minio',
        displayName: 'MinIO',
        status: 'UP',
        latencyMs: 20,
        errorMessage: null,
        detail: 'buckets=3, endpoint=http://localhost:9000',
      },
    ],
  }
  sendJson(res, 200, { code: 0, data, message: 'ok' })
}

/** GET /api/notification/channels */
function handleListNotificationChannels(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: MOCK_NOTIFICATION_CHANNELS,
    message: 'ok',
  })
}

/** GET /api/notification/templates */
function handleListNotificationTemplates(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: MOCK_NOTIFICATION_TEMPLATES,
    message: 'ok',
  })
}

/**
 * PUT /api/notification/config
 *
 * 说明：Mock 端仅更新内存中的通道启用状态与模板启用状态，并刷新最后保存时间。
 */
async function handleSaveNotificationConfig(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  // 1. 更新通道启用状态
  const channels = (body.channels as Array<{ channel: string; enabled: boolean }>) || []
  for (const ch of channels) {
    const target = MOCK_NOTIFICATION_CHANNELS.find((c) => c.channel === ch.channel)
    if (target) {
      target.enabled = ch.enabled
      target.configured = Boolean(target.webhookUrl)
    }
  }
  // 2. 更新模板启用状态
  const templates = (body.templates as Array<{ templateType: string; enabled: boolean }>) || []
  for (const tpl of templates) {
    const target = MOCK_NOTIFICATION_TEMPLATES.find(
      (t) => t.templateType === tpl.templateType,
    )
    if (target) {
      target.enabled = tpl.enabled
    }
  }
  // 3. 刷新最后保存时间
  notificationConfigSavedAt = new Date().toISOString()
  sendJson(res, 200, { code: 0, data: notificationConfigSavedAt, message: 'ok' })
}

// ============================================================
// P0-4 通知通道 Webhook 配置保存 Mock
// PUT /api/notification/channels/{channel}
// ============================================================

/**
 * PUT /api/notification/channels/{channel}
 *
 * 说明：Mock 端更新通道的 webhookUrl / secret / enabled，并返回脱敏后的通道信息。
 */
async function handleSaveChannelConfig(
  req: IncomingMessage,
  res: ServerResponse,
  channel: string,
): Promise<void> {
  const body = await readBody(req)
  const target = MOCK_NOTIFICATION_CHANNELS.find((c) => c.channel === channel)
  if (!target) {
    sendJson(res, 200, {
      code: 40000,
      data: null,
      message: `无效的通道类型: ${channel}`,
    })
    return
  }
  // 1. 更新通道配置
  const webhookUrl = (body.webhookUrl as string) ?? ''
  const secret = (body.secret as string) ?? ''
  const enabled = (body.enabled as boolean) ?? true
  target.webhookUrl = webhookUrl
  target.enabled = enabled
  target.configured = Boolean(webhookUrl)
  // 2. 钉钉通道额外保存 secret（Mock 端不持久化，仅日志）
  if (channel === 'dingtalk' && secret) {
    // Mock 端不存储 secret，真实后端会持久化
  }
  // 3. 返回脱敏后的通道信息（与后端 ChannelVO 脱敏规则一致）
  const maskedUrl = webhookUrl
    ? webhookUrl.replace(/(key|access_token)=[^&]+/, '$1=***')
    : ''
  sendJson(res, 200, {
    code: 0,
    data: {
      channel: target.channel,
      label: target.label,
      configured: target.configured,
      webhookUrl: maskedUrl,
      enabled: target.enabled,
    },
    message: 'ok',
  })
}

// ============================================================
// P0-1 风险关键词库 Mock
// ============================================================

/** Mock 风险关键词列表（对齐后端 RiskKeywordVO，keywordId/builtin/enabled 为 number/string 兼容） */
const MOCK_RISK_KEYWORDS = [
  { keywordId: '30001', keyword: '大额转账', industry: 'banking', category: 'large_amount', riskType: 'high', description: '单笔超过 50 万的转账操作', enabled: 1, builtin: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { keywordId: '30002', keyword: '修改收款账户', industry: 'banking', category: 'high_risk_operation', riskType: 'high', description: '修改供应商收款账户信息', enabled: 1, builtin: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { keywordId: '30003', keyword: '客户身份证号', industry: 'banking', category: 'sensitive_data', riskType: 'medium', description: '涉及客户身份证号操作', enabled: 1, builtin: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { keywordId: '30004', keyword: '保单退保', industry: 'insurance', category: 'high_risk_operation', riskType: 'high', description: '保单退保操作', enabled: 1, builtin: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { keywordId: '30005', keyword: '理赔打款', industry: 'insurance', category: 'large_amount', riskType: 'medium', description: '理赔金额打款操作', enabled: 1, builtin: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { keywordId: '30006', keyword: '股票大宗交易', industry: 'securities', category: 'large_amount', riskType: 'high', description: '大宗股票交易操作', enabled: 1, builtin: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { keywordId: '30007', keyword: '账户解冻', industry: 'banking', category: 'high_risk_operation', riskType: 'high', description: '冻结账户解冻操作', enabled: 0, builtin: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { keywordId: '30008', keyword: '客户手机号', industry: 'banking', category: 'sensitive_data', riskType: 'low', description: '涉及客户手机号操作', enabled: 1, builtin: 0, createTime: '2026-07-15T10:00:00.000Z', updateTime: '2026-07-15T10:00:00.000Z' },
]

/** 雪花算法 ID 自增计数器（Mock 用） */
let mockRiskKeywordIdSeq = 30009

/** GET /api/risk-keywords（分页查询） */
function handleListRiskKeywords(
  req: IncomingMessage,
  res: ServerResponse,
): void {
  // 1. 解析查询参数
  const url = new URL(req.url ?? '', 'http://localhost')
  const current = Number(url.searchParams.get('current') ?? '1')
  const pageSize = Number(url.searchParams.get('pageSize') ?? '10')
  const keyword = url.searchParams.get('keyword') ?? ''
  const industry = url.searchParams.get('industry') ?? ''
  const category = url.searchParams.get('category') ?? ''
  const riskType = url.searchParams.get('riskType') ?? ''
  const enabled = url.searchParams.get('enabled') ?? ''

  // 2. 筛选
  let records = MOCK_RISK_KEYWORDS.filter((k) => {
    if (keyword && !k.keyword.includes(keyword)) return false
    if (industry && k.industry !== industry) return false
    if (category && k.category !== category) return false
    if (riskType && k.riskType !== riskType) return false
    if (enabled !== '' && String(k.enabled) !== enabled) return false
    return true
  })

  // 3. 分页
  const total = records.length
  const pages = Math.ceil(total / pageSize) || 1
  const start = (current - 1) * pageSize
  records = records.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: { records, total, current, size: pageSize, pages },
    message: 'ok',
  })
}

/** POST /api/risk-keywords（新增） */
async function handleAddRiskKeyword(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  const now = new Date().toISOString()
  const newKeyword = {
    keywordId: String(mockRiskKeywordIdSeq++),
    keyword: body.keyword as string,
    industry: body.industry as string,
    category: body.category as string,
    riskType: body.riskType as string,
    description: (body.description as string) ?? '',
    enabled: body.enabled !== undefined ? Number(body.enabled) : 1,
    builtin: 0,
    createTime: now,
    updateTime: now,
  }
  MOCK_RISK_KEYWORDS.unshift(newKeyword)
  sendJson(res, 200, { code: 0, data: newKeyword.keywordId, message: 'ok' })
}

/** PUT /api/risk-keywords/{keywordId}（更新） */
async function handleUpdateRiskKeyword(
  req: IncomingMessage,
  res: ServerResponse,
  keywordId: string,
): Promise<void> {
  const body = await readBody(req)
  const target = MOCK_RISK_KEYWORDS.find((k) => k.keywordId === keywordId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `关键词不存在: ${keywordId}` })
    return
  }
  // 1. 内置关键词仅可改 enabled / description
  if (target.builtin === 1) {
    if (body.enabled !== undefined) target.enabled = Number(body.enabled)
    if (body.description !== undefined) target.description = body.description as string
  } else {
    // 2. 自定义关键词全字段可更新
    if (body.keyword !== undefined) target.keyword = body.keyword as string
    if (body.industry !== undefined) target.industry = body.industry as string
    if (body.category !== undefined) target.category = body.category as string
    if (body.riskType !== undefined) target.riskType = body.riskType as string
    if (body.description !== undefined) target.description = body.description as string
    if (body.enabled !== undefined) target.enabled = Number(body.enabled)
  }
  target.updateTime = new Date().toISOString()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

/** DELETE /api/risk-keywords/{keywordId}（删除） */
function handleDeleteRiskKeyword(
  res: ServerResponse,
  keywordId: string,
): void {
  const target = MOCK_RISK_KEYWORDS.find((k) => k.keywordId === keywordId)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `关键词不存在: ${keywordId}` })
    return
  }
  if (target.builtin === 1) {
    sendJson(res, 200, { code: 50001, data: null, message: '内置关键词不可删除' })
    return
  }
  const idx = MOCK_RISK_KEYWORDS.findIndex((k) => k.keywordId === keywordId)
  MOCK_RISK_KEYWORDS.splice(idx, 1)
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

// ============================================================
// P0-2 Skill 元数据 Mock
// ============================================================

/** Mock Skill 列表（对齐后端 SkillVO，skillId/enabled 为 number/string 兼容） */
const MOCK_SKILLS = [
  { skillId: '40001', name: 'login', description: '银行网银登录 Skill', category: 'auth', paramSchema: '{"username":{"type":"string"},"password":{"type":"string"}}', errorStrategy: 'RETRY', maxRetries: 3, version: '1.0.0', enabled: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { skillId: '40002', name: 'form_fill', description: '表单填写 Skill', category: 'interaction', paramSchema: '{"fields":{"type":"object"}}', errorStrategy: 'RETRY', maxRetries: 2, version: '1.0.0', enabled: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { skillId: '40003', name: 'file_download', description: '文件下载 Skill', category: 'extraction', paramSchema: '{"url":{"type":"string"},"savePath":{"type":"string"}}', errorStrategy: 'SKIP', maxRetries: 1, version: '1.0.0', enabled: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { skillId: '40004', name: 'data_extract', description: '数据提取 Skill', category: 'extraction', paramSchema: '{"selector":{"type":"string"}}', errorStrategy: 'ABORT', maxRetries: 0, version: '1.0.0', enabled: 1, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
  { skillId: '40005', name: 'page_navigate', description: '页面跳转 Skill', category: 'interaction', paramSchema: '{"url":{"type":"string"}}', errorStrategy: 'RETRY', maxRetries: 2, version: '1.0.0', enabled: 0, createTime: '2026-06-01T08:00:00.000Z', updateTime: '2026-06-01T08:00:00.000Z' },
]

/** 雪花算法 ID 自增计数器（Mock 用） */
let mockSkillIdSeq = 40006

/** GET /api/skills（分页查询） */
function handleListSkills(
  req: IncomingMessage,
  res: ServerResponse,
): void {
  // 1. 解析查询参数
  const url = new URL(req.url ?? '', 'http://localhost')
  const current = Number(url.searchParams.get('current') ?? '1')
  const pageSize = Number(url.searchParams.get('pageSize') ?? '10')
  const category = url.searchParams.get('category') ?? ''
  const enabled = url.searchParams.get('enabled') ?? ''
  const searchText = url.searchParams.get('searchText') ?? ''

  // 2. 筛选
  let records = MOCK_SKILLS.filter((s) => {
    if (category && s.category !== category) return false
    if (enabled !== '' && String(s.enabled) !== enabled) return false
    if (searchText && !s.name.includes(searchText) && !(s.description ?? '').includes(searchText)) return false
    return true
  })

  // 3. 分页
  const total = records.length
  const pages = Math.ceil(total / pageSize) || 1
  const start = (current - 1) * pageSize
  records = records.slice(start, start + pageSize)

  sendJson(res, 200, {
    code: 0,
    data: { records, total, current, size: pageSize, pages },
    message: 'ok',
  })
}

/** POST /api/skills（注册） */
async function handleRegisterSkill(
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> {
  const body = await readBody(req)
  // 1. 校验 name 唯一性
  const exists = MOCK_SKILLS.some((s) => s.name === body.name)
  if (exists) {
    sendJson(res, 200, { code: 50001, data: null, message: `Skill 已存在: ${body.name}` })
    return
  }
  const now = new Date().toISOString()
  const newSkill = {
    skillId: String(mockSkillIdSeq++),
    name: body.name as string,
    description: (body.description as string) ?? '',
    category: body.category as string,
    paramSchema: (body.paramSchema as string) ?? '',
    errorStrategy: (body.errorStrategy as string) ?? 'RETRY',
    maxRetries: body.maxRetries !== undefined ? Number(body.maxRetries) : 1,
    version: (body.version as string) ?? '1.0.0',
    enabled: 1,
    createTime: now,
    updateTime: now,
  }
  MOCK_SKILLS.unshift(newSkill)
  sendJson(res, 200, { code: 0, data: newSkill, message: 'ok' })
}

/** PUT /api/skills/{name}（更新） */
async function handleUpdateSkill(
  req: IncomingMessage,
  res: ServerResponse,
  name: string,
): Promise<void> {
  const body = await readBody(req)
  const target = MOCK_SKILLS.find((s) => s.name === name)
  if (!target) {
    sendJson(res, 200, { code: 40400, data: null, message: `Skill 不存在: ${name}` })
    return
  }
  // 1. name 不可修改，其他字段可更新
  if (body.description !== undefined) target.description = body.description as string
  if (body.category !== undefined) target.category = body.category as string
  if (body.paramSchema !== undefined) target.paramSchema = body.paramSchema as string
  if (body.errorStrategy !== undefined) target.errorStrategy = body.errorStrategy as string
  if (body.maxRetries !== undefined) target.maxRetries = Number(body.maxRetries)
  if (body.version !== undefined) target.version = body.version as string
  if (body.enabled !== undefined) target.enabled = Number(body.enabled)
  target.updateTime = new Date().toISOString()
  sendJson(res, 200, { code: 0, data: true, message: 'ok' })
}

export default mockServerPlugin

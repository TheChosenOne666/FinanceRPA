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
 *   GET  /api/llm/calls/stats           LLM 调用统计（M5.6）
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
              return handleGetLlmStats(res)
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

  // 1. 过滤
  let filtered = mockTasks.filter((t) => t.orgId === MOCK_USER.orgId)
  if (status) {
    filtered = filtered.filter((t) => t.status === status)
  }
  if (searchText) {
    filtered = filtered.filter((t) => t.goal.toLowerCase().includes(searchText))
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
      records: records.map((t) => ({
        taskId: t.taskId,
        orgId: t.orgId,
        userId: t.userId,
        goal: t.goal,
        status: t.status,
        currentStep: t.currentStep,
        totalSteps: t.totalSteps,
        message: t.message,
        errorMessage: t.errorMessage,
        createTime: t.createTime,
        updateTime: t.updateTime,
      })),
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

  // 1. 过滤
  let filtered = mockNeedsHumanQueue.filter((i) => i.orgId === MOCK_USER.orgId)
  if (status) {
    filtered = filtered.filter((i) => i.status === status)
  }
  if (taskId) {
    filtered = filtered.filter((i) => i.taskId === taskId)
  }

  // 2. 排序（按创建时间倒序）
  filtered.sort((a, b) => b.createTime.localeCompare(a.createTime))

  sendJson(res, 200, {
    code: 0,
    data: filtered,
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
function handleGetLlmStats(res: ServerResponse): void {
  sendJson(res, 200, {
    code: 0,
    data: {
      totalCalls: 156,
      successCalls: 142,
      failedCalls: 14,
      cacheHitCalls: 38,
      cacheHitRate: 0.2436,
      totalPromptTokens: 285000,
      totalCompletionTokens: 95000,
      totalTokens: 380000,
      totalCost: 2.8473,
      avgDurationMs: 2350,
      modelStats: [
        {
          model: 'gpt-4o-mini',
          calls: 85,
          successCalls: 82,
          totalTokens: 120000,
          cost: 0.18,
        },
        {
          model: 'gpt-4o',
          calls: 52,
          successCalls: 47,
          totalTokens: 180000,
          cost: 1.35,
        },
        {
          model: 'gpt-4o-2024-08-06',
          calls: 19,
          successCalls: 13,
          totalTokens: 80000,
          cost: 1.3173,
        },
      ],
    },
    message: 'ok',
  })
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

/** Mock 审计日志数据：覆盖不同 actionType / riskLevel / executionResult / 截图 / LLM */
const mockAuditLogs: MockAuditLog[] = [
  {
    auditId: '800000000000000001',
    taskId: '700000000000000001',
    orgId: MOCK_USER.orgId,
    departmentId: '500000000000000001',
    businessLineId: '600000000000000001',
    userId: MOCK_USER.userId,
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
    userId: MOCK_USER.userId,
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
    userId: MOCK_USER.userId,
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

export default mockServerPlugin

/**
 * 路由配置
 *
 * 结构：
 * - /login             公开登录页
 * - /                  受保护区域（RequireAuth 包裹）
 *   - index            占位首页（展示当前用户角色与权限）
 *   - /tasks           任务列表（M2.5）
 *   - /tasks/:taskId   任务详情（M2.5）
 *   - /workflows       工作流模板列表（M3.6）
 *   - /workflows/:workflowId           工作流详情（M3.6）
 * - /workflows/:workflowId/runs      工作流执行历史（M3.6）
 * - /needs-human     NEEDS_HUMAN 接管队列（M5.6）
 * - /llm-monitor     LLM 调用监控（M5.6）
 * - /403             403 无权限页
 *   - *                404 兜底
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RequireAuth } from '@/components/AuthGuard'
import LoginPage from '@/routes/auth/LoginPage'
import Forbidden from '@/routes/Forbidden'
import RootLayout, { HomePlaceholder } from '@/routes/RootLayout'
import TasksPage from '@/routes/tasks/TasksPage'
import TaskDetail from '@/routes/tasks/TaskDetail'
import WorkflowsPage from '@/routes/workflows/Workflows'
import WorkflowDetail from '@/routes/workflows/WorkflowDetail'
import WorkflowRunsPage from '@/routes/workflows/WorkflowRuns'
import NeedsHumanPage from '@/routes/llm/NeedsHuman'
import LlmMonitorPage from '@/routes/llm/LlmMonitor'

/** 路由配置 */
export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <RootLayout />
      </RequireAuth>
    ),
    children: [
      {
        index: true,
        element: <HomePlaceholder />,
      },
      {
        path: 'tasks',
        element: <TasksPage />,
      },
      {
        path: 'tasks/:taskId',
        element: <TaskDetail />,
      },
      {
        path: 'workflows',
        element: <WorkflowsPage />,
      },
      {
        path: 'workflows/:workflowId',
        element: <WorkflowDetail />,
      },
      {
        path: 'workflows/:workflowId/runs',
        element: <WorkflowRunsPage />,
      },
      {
        path: 'needs-human',
        element: <NeedsHumanPage />,
      },
      {
        path: 'llm-monitor',
        element: <LlmMonitorPage />,
      },
      {
        path: '403',
        element: <Forbidden />,
      },
      {
        path: '*',
        element: <Navigate to="/" replace />,
      },
    ],
  },
])

export default router

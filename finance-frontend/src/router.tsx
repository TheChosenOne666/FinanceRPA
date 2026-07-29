/**
 * 路由配置
 *
 * 结构：
 * - /login             公开登录页
 * - /                  受保护区域（RequireAuth 包裹）
 *   - index            占位首页（展示当前用户角色与权限）
 *   - /tasks           任务列表（M2.5）
 *   - /tasks/:taskId   任务详情（M2.5）
 *   - /403             403 无权限页
 *   - *                404 兜底
 *
 * 后续 M3 阶段会在 / 下扩展 /workflows、/approvals 等业务路由
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

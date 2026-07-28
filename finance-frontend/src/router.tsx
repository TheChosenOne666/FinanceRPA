/**
 * 路由配置
 *
 * 结构：
 * - /login             公开登录页
 * - /                  受保护区域（RequireAuth 包裹）
 *   - index            占位首页（展示当前用户角色与权限）
 *   - /403             403 无权限页
 *   - *                404 兜底
 *
 * 后续 M2/M3 阶段会在 / 下扩展任务、工作流、审批等业务路由
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RequireAuth } from '@/components/AuthGuard'
import LoginPage from '@/routes/auth/LoginPage'
import Forbidden from '@/routes/Forbidden'
import RootLayout, { HomePlaceholder } from '@/routes/RootLayout'

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

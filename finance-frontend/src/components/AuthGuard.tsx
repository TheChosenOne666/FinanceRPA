/**
 * 路由守卫组件
 *
 * 提供两个组件：
 * 1. RequireAuth：包裹需要登录才能访问的路由，未登录重定向到 /login（携带 redirect）
 * 2. RequirePermission：包裹需要特定权限的路由，权限不足重定向到 /403
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/AuthStore'

/**
 * 登录守卫：未登录跳 /login，并携带 redirect 参数
 *
 * @param children 受保护的子节点
 * @returns 子节点或重定向
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated())
  const location = useLocation()

  // 1. 未登录 → 重定向 /login，携带原路径作为 redirect
  if (!isAuthenticated) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }

  // 2. 已登录 → 渲染子节点
  return <>{children}</>
}

/**
 * 权限守卫：无指定权限跳 /403
 *
 * @param permission 所需权限编码
 * @param children 受保护的子节点
 * @param fallback  可选的自定义无权限节点（不传则重定向 /403）
 * @returns 子节点或重定向
 */
export function RequirePermission({
  permission,
  children,
  fallback,
}: {
  permission: string
  children: ReactNode
  fallback?: ReactNode
}) {
  const hasPermission = useAuthStore((s) => s.hasPermission(permission))

  // 1. 有权限 → 渲染子节点
  if (hasPermission) {
    return <>{children}</>
  }

  // 2. 提供了 fallback → 渲染 fallback（用于局部权限控制）
  if (fallback !== undefined) {
    return <>{fallback}</>
  }

  // 3. 无 fallback → 重定向 /403
  return <Navigate to="/403" replace />
}

/**
 * 角色守卫：无指定角色跳 /403
 *
 * @param role 所需角色编码
 * @param children 受保护的子节点
 * @returns 子节点或重定向
 */
export function RequireRole({ role, children }: { role: string; children: ReactNode }) {
  const hasRole = useAuthStore((s) => s.hasRole(role))

  if (hasRole) {
    return <>{children}</>
  }

  return <Navigate to="/403" replace />
}

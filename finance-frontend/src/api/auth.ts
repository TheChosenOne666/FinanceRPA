/**
 * 认证相关 API 封装
 *
 * 对齐后端 com.finrpa.auth.controller.AuthController：
 * - POST /auth/login        用户登录
 * - POST /auth/refresh      刷新 token
 * - GET  /auth/me           获取当前用户信息
 * - POST /auth/permissions/check  权限检查
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  LoginRequest,
  LoginResponse,
  PermissionCheckRequest,
  PermissionCheckResponse,
  UserInfoResponse,
} from './types'

/**
 * 用户登录
 *
 * @param payload 登录请求（用户名 + 密码）
 * @returns 登录响应（含 accessToken / refreshToken / user）
 */
export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const res = await axiosClient.post<BaseResponse<LoginResponse>>('/auth/login', payload)
  return res.data.data
}

/**
 * 刷新 token（一般由 AxiosClient 拦截器自动调用，业务代码不直接调用）
 *
 * @param refreshToken 刷新令牌
 * @returns 登录响应（含新的 accessToken / refreshToken / user）
 */
export async function refresh(refreshToken: string): Promise<LoginResponse> {
  const res = await axiosClient.post<BaseResponse<LoginResponse>>('/auth/refresh', { refreshToken })
  return res.data.data
}

/**
 * 获取当前登录用户信息（含角色与权限列表）
 *
 * @returns 用户信息响应
 */
export async function getCurrentUser(): Promise<UserInfoResponse> {
  const res = await axiosClient.get<BaseResponse<UserInfoResponse>>('/auth/me')
  return res.data.data
}

/**
 * 权限检查（业务页面按需调用，检查对指定资源的具体操作权限）
 *
 * @param payload 权限检查请求（资源类型 / 资源 ID / 操作）
 * @returns 是否有权限
 */
export async function checkPermission(
  payload: PermissionCheckRequest,
): Promise<PermissionCheckResponse> {
  const res = await axiosClient.post<BaseResponse<PermissionCheckResponse>>(
    '/auth/permissions/check',
    payload,
  )
  return res.data.data
}

/**
 * 用户登出
 *
 * @returns 登出结果
 */
export async function logout(): Promise<void> {
  await axiosClient.post<BaseResponse>('/auth/logout')
}

/** 认证 API 聚合导出 */
export const authApi = {
  login,
  refresh,
  getCurrentUser,
  checkPermission,
  logout,
}

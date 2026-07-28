/**
 * 认证状态 Store（Zustand）
 *
 * 职责：
 * 1. 管理 accessToken / refreshToken / user / permissions
 * 2. 提供 login / logout / setTokens / fetchCurrentUser 等 action
 * 3. 通过 persist 中间件持久化到 localStorage（key: finrpa-auth）
 *
 * 注意：
 * - AxiosClient 在 refresh 成功后调用 setTokens 同步新 token
 * - 业务页面通过 useAuthStore((s) => s.permissions) 订阅权限列表
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authApi } from '@/api/auth'
import type { LoginUserInfo, LoginResponse } from '@/api/types'

/**
 * 认证状态
 */
export interface AuthState {
  /** 访问令牌 */
  accessToken: string | null
  /** 刷新令牌 */
  refreshToken: string | null
  /** accessToken 过期时间戳（毫秒） */
  expiresAt: number | null
  /** 登录用户信息（来自 LoginResponse.user，简化版） */
  user: LoginUserInfo | null
  /** 权限编码列表（来自 UserInfoResponse.permissions） */
  permissions: string[]
  /** 登录加载中（用于登录页按钮 loading） */
  loading: boolean

  /** 登录 */
  login: (username: string, password: string) => Promise<void>
  /** 登出（清空状态，由调用方决定是否调后端 logout 接口） */
  logout: () => void
  /** 设置 token（AxiosClient refresh 成功后调用） */
  setTokens: (tokens: LoginResponse) => void
  /** 拉取当前用户详情（含角色与权限） */
  fetchCurrentUser: () => Promise<void>
  /** 是否已登录（token 存在且未过期） */
  isAuthenticated: () => boolean
  /** 是否拥有指定权限 */
  hasPermission: (perm: string) => boolean
  /** 是否拥有指定角色 */
  hasRole: (role: string) => boolean
}

/** localStorage 持久化 key */
const STORAGE_KEY = 'finrpa-auth'

/**
 * 创建认证 Store
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      user: null,
      permissions: [],
      loading: false,

      // region 登录

      login: async (username, password) => {
        // 1. 调用登录接口
        set({ loading: true })
        try {
          const res = await authApi.login({ username, password })
          // 2. 计算 token 过期时间戳
          const expiresAt = Date.now() + res.expiresIn * 1000
          // 3. 先存 token，便于后续请求附加 Authorization
          set({
            accessToken: res.accessToken,
            refreshToken: res.refreshToken,
            expiresAt,
            user: res.user,
            permissions: [],
          })
          // 4. 拉取用户详情（含 permissions）
          try {
            await get().fetchCurrentUser()
          } catch (e) {
            // 用户详情拉取失败不影响登录主流程（permissions 后续按需拉取）
            console.warn('[AuthStore] 登录后拉取用户详情失败:', e)
          }
        } finally {
          set({ loading: false })
        }
      },

      // endregion

      // region 登出

      logout: () => {
        set({
          accessToken: null,
          refreshToken: null,
          expiresAt: null,
          user: null,
          permissions: [],
          loading: false,
        })
      },

      // endregion

      // region 设置 token（refresh 后调用）

      setTokens: (tokens) => {
        const expiresAt = Date.now() + tokens.expiresIn * 1000
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          expiresAt,
          // refresh 接口返回的 user 一般与原 user 一致，覆盖更新即可
          user: tokens.user,
        })
      },

      // endregion

      // region 拉取当前用户详情

      fetchCurrentUser: async () => {
        const userInfo = await authApi.getCurrentUser()
        set({
          user: {
            userId: userInfo.userId,
            username: userInfo.username,
            realName: userInfo.realName,
            orgId: userInfo.orgId,
            orgName: userInfo.orgName,
            deptName: userInfo.deptName,
            roles: userInfo.roles,
          },
          permissions: userInfo.permissions,
        })
      },

      // endregion

      // region 状态查询

      isAuthenticated: () => {
        const { accessToken, expiresAt } = get()
        // 1. 无 token → 未登录
        if (!accessToken) return false
        // 2. token 过期 → 未登录（响应拦截器会自动 refresh，此处仅作判断）
        if (expiresAt && Date.now() >= expiresAt) return false
        return true
      },

      hasPermission: (perm) => {
        const { permissions } = get()
        return permissions.includes(perm)
      },

      hasRole: (role) => {
        const { user } = get()
        return user?.roles.includes(role) ?? false
      },

      // endregion
    }),
    {
      name: STORAGE_KEY,
      // 仅持久化数据字段，actions 不持久化（函数序列化会丢失）
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        expiresAt: state.expiresAt,
        user: state.user,
        permissions: state.permissions,
      }),
    },
  ),
)

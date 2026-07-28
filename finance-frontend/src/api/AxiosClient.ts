/**
 * Axios 客户端实例 + 拦截器
 *
 * 职责：
 * 1. 请求拦截：自动附加 Authorization: Bearer <accessToken>
 * 2. 响应拦截：业务码非 0 抛 ApiError；401/40100 自动触发 refresh；refresh 失败登出并跳登录
 * 3. refresh 并发合并：多个 401 同时到达时只发一次 refresh，其余排队等待
 *
 * 说明：通过 useAuthStore.getState() 运行时获取 token 与 actions，避免模块加载时循环依赖
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/AuthStore'
import { BaseResponse, ErrorCode, LoginResponse } from './types'

/** 基础路径，对齐 server.servlet.context-path: /api */
const baseURL = '/api'

/** 不需要附加 token 的接口白名单 */
const WHITE_LIST = ['/auth/login', '/auth/refresh']

/** 标记请求已重试过，避免无限循环 */
const RETRY_FLAG = '__retried'

/** Axios 实例 */
const axiosClient: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

/** refresh 并发控制 */
let isRefreshing = false
/** 排队等待 refresh 完成的请求 */
let pendingQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

/**
 * 业务异常（业务码非 0 时抛出）
 */
export class ApiError extends Error {
  /** 业务错误码 */
  code: number

  constructor(message: string, code: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/**
 * 跳转登录页（强制跳转，携带原路径作为 redirect 参数）
 */
function redirectToLogin(): void {
  if (window.location.pathname !== '/login') {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}&expired=1`
  }
}

// region 请求拦截器

axiosClient.interceptors.request.use((config) => {
  const url = config.url ?? ''
  // 1. 白名单接口不附加 token
  if (!WHITE_LIST.includes(url)) {
    // 2. 从 AuthStore 获取 accessToken 并附加到请求头
    const token = useAuthStore.getState().accessToken
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

// endregion

// region 响应拦截器

axiosClient.interceptors.response.use(
  (response) => {
    // 1. 业务码校验：非 0 视为业务异常
    const body = response.data as BaseResponse
    if (body.code !== ErrorCode.SUCCESS) {
      return Promise.reject(new ApiError(body.message || '业务异常', body.code))
    }
    return response
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as
      | (InternalAxiosRequestConfig & { [RETRY_FLAG]?: boolean })
      | undefined

    // 1. 无原始配置，直接抛错
    if (!originalRequest) {
      return Promise.reject(error)
    }

    const url = originalRequest.url ?? ''
    const status = error.response?.status
    const body = error.response?.data as BaseResponse | undefined
    // 2. 判断是否为认证失效（HTTP 401 或业务码 40100）
    const isAuthError = status === 401 || body?.code === ErrorCode.NOT_LOGIN_ERROR

    // 3. 白名单接口或已重试过 → 不再 refresh
    if (!isAuthError || WHITE_LIST.includes(url) || originalRequest[RETRY_FLAG]) {
      // refresh 接口失败 → 直接登出跳登录
      if (url === '/auth/refresh') {
        useAuthStore.getState().logout()
        redirectToLogin()
      }
      return Promise.reject(error)
    }

    // 4. 标记已重试
    originalRequest[RETRY_FLAG] = true

    // 5. 已有 refresh 进行中 → 排队等待
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pendingQueue.push({
          resolve: (token) => {
            originalRequest.headers!.Authorization = `Bearer ${token}`
            resolve(axiosClient(originalRequest))
          },
          reject,
        })
      })
    }

    // 6. 触发 refresh
    isRefreshing = true
    const refreshToken = useAuthStore.getState().refreshToken
    // 6.1 无 refreshToken → 直接登出
    if (!refreshToken) {
      useAuthStore.getState().logout()
      redirectToLogin()
      return Promise.reject(error)
    }

    try {
      // 6.2 直接用原始 axios 发请求，避免再次进入拦截器循环
      const res = await axios.post<BaseResponse<LoginResponse>>(`${baseURL}/auth/refresh`, {
        refreshToken,
      })
      if (res.data.code !== ErrorCode.SUCCESS) {
        throw new ApiError(res.data.message || '刷新 token 失败', res.data.code)
      }
      const newTokens = res.data.data
      // 6.3 更新 AuthStore（含 localStorage 持久化）
      useAuthStore.getState().setTokens(newTokens)

      // 6.4 重发排队请求
      pendingQueue.forEach(({ resolve }) => resolve(newTokens.accessToken))
      pendingQueue = []

      // 6.5 重发原请求
      originalRequest.headers!.Authorization = `Bearer ${newTokens.accessToken}`
      return axiosClient(originalRequest)
    } catch (refreshError) {
      // 6.6 refresh 失败 → 清空队列 + 登出跳转
      pendingQueue.forEach(({ reject }) => reject(refreshError))
      pendingQueue = []
      useAuthStore.getState().logout()
      redirectToLogin()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

// endregion

export default axiosClient

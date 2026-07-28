/**
 * 登录页
 *
 * 实现：
 * - 用户名密码表单（毛玻璃卡片样式，对齐 prototypes/01-auth-and-layout.html）
 * - 客户端校验（用户名 / 密码非空）
 * - 服务端错误提示（登录失败展示 form-error）
 * - 显示 / 隐藏密码切换
 * - 已登录用户访问 /login 自动跳转 redirect 或 /
 * - URL 参数 expired=1 显示 "登录已过期" 提示
 *
 * 说明：原型中的 2FA 输入框暂未实现（后端 LoginRequest 不支持）；语言切换按钮暂未实现（M1.6 i18n 任务统一处理）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuthStore } from '@/store/AuthStore'
import { ApiError } from '@/api/AxiosClient'

/** 表单字段错误 */
interface FieldErrors {
  username?: string
  password?: string
}

function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const login = useAuthStore((s) => s.login)
  const loading = useAuthStore((s) => s.loading)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated())

  // 1. 表单状态
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  // 2. URL expired=1 → 显示过期提示（仅初始化时读取）
  useEffect(() => {
    if (searchParams.get('expired') === '1') {
      setFormError('登录已过期，请重新登录')
    }
  }, [searchParams])

  // 3. 已登录用户访问 /login → 自动跳转（避免重复登录）
  if (isAuthenticated) {
    const redirect = searchParams.get('redirect')
    return <Navigate to={redirect ? decodeURIComponent(redirect) : '/'} replace />
  }

  // 4. 客户端校验
  const validate = (): boolean => {
    const errs: FieldErrors = {}
    if (!username.trim()) errs.username = '请输入用户名'
    if (!password) errs.password = '请输入密码'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  // 5. 提交登录
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setFormError(null)
    try {
      await login(username.trim(), password)
      // 登录成功 → 跳转 redirect 或 /
      const redirect = searchParams.get('redirect')
      navigate(redirect ? decodeURIComponent(redirect) : '/', { replace: true })
    } catch (err) {
      // 业务错误（ApiError）展示 message；其他错误展示通用提示
      const msg = err instanceof ApiError ? err.message : '登录失败，请稍后重试'
      setFormError(msg)
    }
  }

  return (
    <div className="login-screen">
      <div className="glass-card login-card">
        {/* region Logo + 标题 */}
        <div className="login-logo">
          <div className="logo-mark">
            <svg
              width="22"
              height="22"
              viewBox="0 0 24 24"
              fill="none"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 2L4 7l8 5 8-5-8-5z" />
              <path d="M4 12l8 5 8-5" />
              <path d="M4 17l8 5 8-5" />
            </svg>
          </div>
          <div>
            <div className="login-title">FinanceRPA</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
              金融级 AI 浏览器自动化平台
            </div>
          </div>
        </div>
        <div className="login-subtitle">登录以开始管理您的自动化任务</div>
        {/* endregion */}

        {/* region 服务端错误提示 */}
        {formError && (
          <div className="form-error">
            <svg
              className="icon-sm"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            {formError}
          </div>
        )}
        {/* endregion */}

        {/* region 登录表单 */}
        <form className="login-form" onSubmit={handleSubmit}>
          {/* 用户名 */}
          <div className="form-group">
            <label className="label" htmlFor="login-username">
              用户名
            </label>
            <div className="input-with-icon">
              <svg
                className="icon-sm input-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
              <input
                id="login-username"
                className={`input ${errors.username ? 'input-error' : ''}`}
                placeholder="请输入工号或用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                autoFocus
              />
            </div>
            {errors.username && <div className="field-error">{errors.username}</div>}
          </div>

          {/* 密码 */}
          <div className="form-group">
            <label className="label" htmlFor="login-password">
              密码
            </label>
            <div className="input-with-icon">
              <svg
                className="icon-sm input-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              <input
                id="login-password"
                className={`input ${errors.password ? 'input-error' : ''}`}
                type={showPassword ? 'text' : 'password'}
                placeholder="请输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
              <button
                type="button"
                className="input-toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? '隐藏密码' : '显示密码'}
              >
                {showPassword ? (
                  <svg
                    className="icon-sm"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                    <line x1="1" y1="1" x2="23" y2="23" />
                  </svg>
                ) : (
                  <svg
                    className="icon-sm"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                )}
              </button>
            </div>
            {errors.password && <div className="field-error">{errors.password}</div>}
          </div>

          {/* 登录按钮 */}
          <button
            className="btn btn-primary btn-lg"
            type="submit"
            disabled={loading}
            style={{ width: '100%', marginTop: 8 }}
          >
            <svg
              className="icon-sm"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
              <polyline points="10 17 15 12 10 7" />
              <line x1="15" y1="12" x2="3" y2="12" />
            </svg>
            {loading ? '登录中…' : '登 录'}
          </button>
        </form>
        {/* endregion */}

        {/* region 页脚 */}
        <div className="login-footer">
          <div style={{ marginTop: 18 }}>© 2026 FinanceRPA · 纯私有化部署</div>
        </div>
        {/* endregion */}
      </div>
    </div>
  )
}

export default LoginPage

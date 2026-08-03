/**
 * 受保护区域根布局
 *
 * 职责：
 * - 渲染顶部 Header（logo + 全局搜索 + 通知 + 用户信息）
 * - 渲染左侧 Sidebar（按"监控 / 自动化 / 合规 / 管理"分组）
 * - 通过 Outlet 渲染子路由
 *
 * 设计对齐（prototypes/01-auth-and-layout.html）：
 * - grid 布局：240px Sidebar + 64px Header
 * - Sidebar nav-item.active：金色左边框 3px
 * - Header/Sidebar 均为毛玻璃悬浮（glass-floating）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/AuthStore'
import {
  IconApproval,
  IconBell,
  IconChart,
  IconList,
  IconSearch,
  IconShield,
  IconTerminal,
  IconWorkflow,
} from '@/components/Icons'

/** 导航项定义 */
interface NavItem {
  /** 路由路径 */
  path: string
  /** 显示文字 */
  label: string
  /** 图标 */
  icon: React.ReactNode
  /** title 提示 */
  title: string
  /** 右侧徽章（待办数） */
  badge?: { text: string; variant: 'warning' | 'danger' }
}

/** 监控组：大屏 + LLM 监控 */
const MONITOR_NAV: NavItem[] = [
  {
    path: '/dashboard',
    label: '运营大屏',
    title: '运营大屏',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="14" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
      </svg>
    ),
  },
  {
    path: '/llm-monitor',
    label: 'LLM 监控',
    title: 'LLM 调用监控',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
      </svg>
    ),
  },
]

/** 自动化组：任务 + 工作流 */
const AUTOMATION_NAV: NavItem[] = [
  {
    path: '/tasks',
    label: '任务管理',
    title: '任务列表',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M9 2h6a1 1 0 0 1 1 1v2H8V3a1 1 0 0 1 1-1z" />
        <path d="M8 5h8a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" />
        <line x1="9" y1="12" x2="15" y2="12" />
        <line x1="9" y1="16" x2="13" y2="16" />
      </svg>
    ),
  },
  {
    path: '/workflows',
    label: '工作流',
    title: '工作流模板',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="4" y="4" width="6" height="6" rx="1" />
        <rect x="14" y="14" width="6" height="6" rx="1" />
        <path d="M10 7h4a3 3 0 0 1 3 3v4" />
      </svg>
    ),
  },
]

/** 合规组：审批 + 接管 + 审计 */
const COMPLIANCE_NAV: NavItem[] = [
  {
    path: '/approvals',
    label: '审批中心',
    title: '审批中心',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        <polyline points="9 12 11 14 15 10" />
      </svg>
    ),
    badge: { text: '3', variant: 'warning' },
  },
  {
    path: '/needs-human',
    label: '人工接管',
    title: '人工接管队列',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    ),
    badge: { text: '7', variant: 'danger' },
  },
  {
    path: '/audit-logs',
    label: '审计日志',
    title: '审计日志',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
        <polyline points="14 2 14 8 20 8" />
        <line x1="8" y1="13" x2="16" y2="13" />
        <line x1="8" y1="17" x2="14" y2="17" />
      </svg>
    ),
  },
]

/** 通知中心：独立项（不在分组标题下，但归属合规区） */
const NOTIFICATION_NAV: NavItem = {
  path: '/notification',
  label: '通知中心',
  title: '通知中心',
  icon: <IconBell size={20} />,
}

/** 管理组：首页 + 通知 */
const MISC_NAV: NavItem[] = [
  {
    path: '/',
    label: '首页',
    title: '首页',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
        <polyline points="9 22 9 12 15 12 15 22" />
      </svg>
    ),
  },
  NOTIFICATION_NAV,
]

/** 所有导航分组 */
const NAV_GROUPS: Array<{ title: string; items: NavItem[] }> = [
  { title: '监控', items: MONITOR_NAV },
  { title: '自动化', items: AUTOMATION_NAV },
  { title: '合规', items: COMPLIANCE_NAV },
  { title: '管理', items: MISC_NAV },
]

function RootLayout() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()
  const location = useLocation()

  // 1. 退出登录：清空状态 + 跳登录页
  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  // 2. 头像首字符（取 realName 或 username 首字）
  const avatarChar = (user?.realName || user?.username || '?').charAt(0).toUpperCase()

  // 3. 判断导航项是否 active（精确匹配或子路由匹配；首页仅精确匹配）
  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    return location.pathname === path || location.pathname.startsWith(`${path}/`)
  }

  return (
    <div className="root-layout">
      {/* region 顶部 Header */}
      <header className="root-header">
        {/* 左侧：Logo + 搜索框 */}
        <div className="root-header-left">
          <div className="root-brand">
            <div className="logo-mark">
              <svg
                width="16"
                height="16"
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
            <strong className="root-brand-name">FinanceRPA</strong>
          </div>

          {/* 全局搜索框 */}
          <div className="root-search">
            <IconSearch size={16} />
            <input placeholder="搜索任务、工作流、审计日志…" />
          </div>
        </div>

        {/* 右侧：通知 + 用户 + 退出 */}
        <div className="root-header-right">
          {/* 通知铃铛 */}
          <button
            type="button"
            className="root-icon-btn"
            onClick={() => navigate('/notification')}
            title="通知中心"
            aria-label="通知中心"
          >
            <IconBell size={16} />
            <span className="nav-badge nav-badge-danger">7</span>
          </button>

          {/* 用户信息 */}
          <div
            className="root-user-chip"
            onClick={() => navigate('/')}
            role="button"
            tabIndex={0}
            title="返回首页"
          >
            <div className="root-avatar">{avatarChar}</div>
            <span className="root-user-name">
              {user?.realName || user?.username}
            </span>
            {user && user.roles.length > 0 && (
              <span className="root-user-roles">· {user.roles.join('/')}</span>
            )}
          </div>

          {/* 退出按钮 */}
          <button className="btn btn-ghost btn-sm" onClick={handleLogout} title="退出登录">
            <svg
              className="icon-sm"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
            退出
          </button>
        </div>
      </header>
      {/* endregion */}

      {/* region 左侧 Sidebar */}
      <aside className="root-sidebar">
        {NAV_GROUPS.map((group) => (
          <div key={group.title}>
            <div className="nav-group-title">{group.title}</div>
            {group.items.map((item) => (
              <button
                key={item.path}
                type="button"
                className={`nav-item ${isActive(item.path) ? 'active' : ''}`}
                onClick={() => navigate(item.path)}
                title={item.title}
              >
                {item.icon}
                <span>{item.label}</span>
                {item.badge && (
                  <span className={`nav-badge nav-badge-${item.badge.variant}`}>
                    {item.badge.text}
                  </span>
                )}
              </button>
            ))}
          </div>
        ))}
      </aside>
      {/* endregion */}

      {/* region 内容区 Outlet */}
      <main className="root-main">
        <Outlet />
      </main>
      {/* endregion */}
    </div>
  )
}

/**
 * 首页：欢迎页 + 快捷入口 + 用户信息
 *
 * M8.2 UI 重构：从 M1.3 占位页升级为有意义的欢迎页，
 * 提供快捷入口卡片跳转到常用功能，展示用户基本信息。
 */
function HomePlaceholder() {
  const user = useAuthStore((s) => s.user)
  const permissions = useAuthStore((s) => s.permissions)
  const navigate = useNavigate()

  /** 快捷入口配置 */
  const shortcuts: Array<{
    path: string
    name: string
    desc: string
    icon: React.ReactNode
    iconClass: string
  }> = [
    {
      path: '/dashboard',
      name: '运营大屏',
      desc: '查看任务统计与性能监控',
      icon: <IconChart size={22} />,
      iconClass: 'stat-icon-blue',
    },
    {
      path: '/tasks',
      name: '任务管理',
      desc: '创建与跟踪自动化任务',
      icon: <IconList size={22} />,
      iconClass: 'stat-icon-green',
    },
    {
      path: '/workflows',
      name: '工作流模板',
      desc: '配置与管理自动化流程',
      icon: <IconWorkflow size={22} />,
      iconClass: 'stat-icon-purple',
    },
    {
      path: '/approvals',
      name: '审批中心',
      desc: '处理待审批的高风险任务',
      icon: <IconApproval size={22} />,
      iconClass: 'stat-icon-amber',
    },
    {
      path: '/needs-human',
      name: '人工接管',
      desc: '处置需要人工介入的子任务',
      icon: <IconShield size={22} />,
      iconClass: 'stat-icon-blue',
    },
    {
      path: '/audit-logs',
      name: '审计日志',
      desc: '检索操作记录与截图对比',
      icon: <IconTerminal size={22} />,
      iconClass: 'stat-icon-green',
    },
  ]

  return (
    <div>
      {/* region 欢迎区 */}
      <div className="home-welcome">
        <h1 className="home-welcome-title">
          欢迎回来，{user?.realName || user?.username}
        </h1>
        <p className="home-welcome-subtitle">
          FinanceRPA · 金融级 AI 浏览器自动化平台 · 从下方快捷入口开始
        </p>
      </div>
      {/* endregion */}

      {/* region 快捷入口 */}
      <div className="home-shortcuts">
        {shortcuts.map((s) => (
          <div
            key={s.path}
            className="glass-card-static home-shortcut-card"
            onClick={() => navigate(s.path)}
            role="link"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                navigate(s.path)
              }
            }}
          >
            <div className={`home-shortcut-icon ${s.iconClass}`}>{s.icon}</div>
            <div className="home-shortcut-text">
              <span className="home-shortcut-name">{s.name}</span>
              <span className="home-shortcut-desc">{s.desc}</span>
            </div>
          </div>
        ))}
      </div>
      {/* endregion */}

      {/* region 用户信息 + 角色 + 权限 */}
      <div className="home-info-grid">
        {/* 用户基本信息 */}
        <div className="glass-card-static section-card">
          <div className="section-label">用户信息</div>
          <div className="info-row">
            <span className="info-label">用户名</span>
            <strong className="info-value">{user?.username}</strong>
          </div>
          <div className="info-row">
            <span className="info-label">真实姓名</span>
            <strong className="info-value">{user?.realName || '-'}</strong>
          </div>
          <div className="info-row">
            <span className="info-label">所属组织</span>
            <strong className="info-value">{user?.orgName || '-'}</strong>
          </div>
          <div className="info-row">
            <span className="info-label">所属部门</span>
            <strong className="info-value">{user?.deptName || '-'}</strong>
          </div>
        </div>

        {/* 角色列表 */}
        <div className="glass-card-static section-card">
          <div className="section-label">角色编码</div>
          {user && user.roles.length > 0 ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {user.roles.map((role) => (
                <span key={role} className="role-tag">
                  {role}
                </span>
              ))}
            </div>
          ) : (
            <div className="info-value">暂无角色</div>
          )}
        </div>

        {/* 权限列表 */}
        <div className="glass-card-static section-card">
          <div className="section-label">权限编码（共 {permissions.length} 项）</div>
          {permissions.length > 0 ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {permissions.map((p) => (
                <span key={p} className="perm-tag">
                  {p}
                </span>
              ))}
            </div>
          ) : (
            <div className="info-value">暂无权限</div>
          )}
        </div>
      </div>
      {/* endregion */}
    </div>
  )
}

export default RootLayout
export { HomePlaceholder }

/**
 * 受保护区域根布局
 *
 * 职责：
 * - 渲染顶部 Header（logo + 用户信息 + 退出按钮）
 * - 通过 Outlet 渲染子路由
 *
 * 说明：M1.3 阶段为占位实现，验证登录守卫与角色差异。
 * M1.5（UI 系统改造）将扩展为完整 Header + SideNav + PageLayout
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { Outlet, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/AuthStore'
import { IconChart, IconList, IconShield, IconTarget, IconWorkflow } from '@/components/Icons'

function RootLayout() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()

  // 1. 退出登录：清空状态 + 跳登录页
  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  // 2. 头像首字符（取 realName 或 username 首字）
  const avatarChar = (user?.realName || user?.username || '?').charAt(0).toUpperCase()

  return (
    <div className="root-layout">
      {/* region 顶部 Header */}
      <header className="root-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
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
          <strong style={{ fontSize: 15, letterSpacing: '-0.02em' }}>FinanceRPA</strong>
          <span
            style={{
              fontSize: 11,
              color: 'var(--text-muted)',
              marginLeft: 8,
              padding: '2px 8px',
              background: 'rgba(26, 58, 92, 0.06)',
              borderRadius: 999,
            }}
          >
            M3.6
          </span>
          {/* region 顶部导航 */}
          <nav className="root-nav">
            <button
              type="button"
              className="root-nav-btn"
              onClick={() => navigate('/')}
              title="首页"
            >
              <IconTarget size={14} />
              首页
            </button>
            <button
              type="button"
              className="root-nav-btn"
              onClick={() => navigate('/tasks')}
              title="任务列表"
            >
              <IconList size={14} />
              任务
            </button>
            <button
              type="button"
              className="root-nav-btn"
              onClick={() => navigate('/workflows')}
              title="工作流模板"
            >
              <IconWorkflow size={14} />
              工作流
            </button>
            <button
              type="button"
              className="root-nav-btn"
              onClick={() => navigate('/needs-human')}
              title="人工接管队列"
            >
              <IconShield size={14} />
              接管
            </button>
            <button
              type="button"
              className="root-nav-btn"
              onClick={() => navigate('/llm-monitor')}
              title="LLM 调用监控"
            >
              <IconChart size={14} />
              监控
            </button>
          </nav>
          {/* endregion */}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div className="root-user-chip">
            <div className="root-avatar">{avatarChar}</div>
            <span style={{ fontSize: 13, fontWeight: 500 }}>
              {user?.realName || user?.username}
            </span>
            {user && user.roles.length > 0 && (
              <span
                style={{
                  fontSize: 11,
                  color: 'var(--text-muted)',
                  marginLeft: 4,
                }}
              >
                · {user.roles.join('/')}
              </span>
            )}
          </div>
          <button className="btn btn-ghost btn-sm" onClick={handleLogout}>
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

      {/* region 内容区 Outlet */}
      <main className="root-main">
        <Outlet />
      </main>
      {/* endregion */}
    </div>
  )
}

/**
 * 占位首页：展示当前登录用户的角色与权限
 *
 * 用于 M1.3 验证"5 种角色登录后看到不同菜单/数据"的验收点
 */
function HomePlaceholder() {
  const user = useAuthStore((s) => s.user)
  const permissions = useAuthStore((s) => s.permissions)

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1
          style={{
            fontSize: 22,
            fontWeight: 700,
            color: 'var(--text-primary)',
            marginBottom: 8,
            letterSpacing: '-0.02em',
          }}
        >
          欢迎回来，{user?.realName || user?.username}
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>
          FinanceRPA · M1.3 阶段占位首页。完整功能页面将在 M2/M3 阶段实现。
        </p>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: 16,
        }}
      >
        {/* region 用户基本信息 */}
        <div className="glass-card-static" style={{ padding: 24 }}>
          <div
            style={{
              fontSize: 12,
              color: 'var(--text-muted)',
              marginBottom: 12,
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
            }}
          >
            用户信息
          </div>
          <div style={{ fontSize: 14, lineHeight: 2 }}>
            <div>
              <span style={{ color: 'var(--text-muted)', marginRight: 12 }}>用户名</span>
              <strong>{user?.username}</strong>
            </div>
            <div>
              <span style={{ color: 'var(--text-muted)', marginRight: 12 }}>真实姓名</span>
              <strong>{user?.realName || '-'}</strong>
            </div>
            <div>
              <span style={{ color: 'var(--text-muted)', marginRight: 12 }}>所属组织</span>
              <strong>{user?.orgName || '-'}</strong>
            </div>
            <div>
              <span style={{ color: 'var(--text-muted)', marginRight: 12 }}>所属部门</span>
              <strong>{user?.deptName || '-'}</strong>
            </div>
          </div>
        </div>
        {/* endregion */}

        {/* region 角色列表 */}
        <div className="glass-card-static" style={{ padding: 24 }}>
          <div
            style={{
              fontSize: 12,
              color: 'var(--text-muted)',
              marginBottom: 12,
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
            }}
          >
            角色编码
          </div>
          {user && user.roles.length > 0 ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {user.roles.map((role) => (
                <span key={role} className="role-tag">
                  {role}
                </span>
              ))}
            </div>
          ) : (
            <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>暂无角色</div>
          )}
        </div>
        {/* endregion */}

        {/* region 权限列表 */}
        <div className="glass-card-static" style={{ padding: 24 }}>
          <div
            style={{
              fontSize: 12,
              color: 'var(--text-muted)',
              marginBottom: 12,
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
            }}
          >
            权限编码（共 {permissions.length} 项）
          </div>
          {permissions.length > 0 ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {permissions.map((p) => (
                <span key={p} className="perm-tag">
                  {p}
                </span>
              ))}
            </div>
          ) : (
            <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>暂无权限</div>
          )}
        </div>
        {/* endregion */}
      </div>
    </div>
  )
}

export default RootLayout
export { HomePlaceholder }

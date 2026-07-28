/**
 * 403 无权限页面
 *
 * 触发场景：访问 RequirePermission/RequireRole 保护的路由但权限不足时重定向到此页
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/AuthStore'

function Forbidden() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const permissions = useAuthStore((s) => s.permissions)

  return (
    <div className="error-screen">
      <div className="glass-card-static error-card">
        <div className="error-code">403</div>
        <div className="error-title">无权限访问</div>
        <div className="error-desc">
          抱歉，您当前的账号没有访问该页面的权限。
          {user && (
            <>
              <br />
              当前账号：<strong>{user.realName || user.username}</strong>
              {user.roles.length > 0 && <>（角色：{user.roles.join(' / ')}）</>}
            </>
          )}
        </div>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center', marginTop: 24 }}>
          <button className="btn btn-ghost btn-lg" onClick={() => navigate(-1)}>
            返回上一页
          </button>
          <button className="btn btn-primary btn-lg" onClick={() => navigate('/', { replace: true })}>
            返回首页
          </button>
        </div>
        {permissions.length > 0 && (
          <div style={{ marginTop: 32, textAlign: 'left' }}>
            <div
              style={{
                fontSize: 12,
                color: 'var(--text-muted)',
                marginBottom: 8,
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}
            >
              当前拥有的权限
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {permissions.map((p) => (
                <span key={p} className="perm-tag">
                  {p}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default Forbidden

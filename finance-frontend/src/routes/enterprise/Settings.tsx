/**
 * 系统设置页面（P4 settings 原型对齐 + P0 功能扩展）
 *
 * 功能：
 * - 两栏布局：240px 子导航 + 1fr 内容区
 * - 8 个子导航项：用户管理 / 角色管理 / 部门管理 / 业务线 / 风险关键词 / 通知配置 / Skill 管理 / 权限矩阵
 * - 已实现区块：
 *   - 用户管理 / 角色管理（Mock 数据，后端待开发）
 *   - 部门 / 业务线（只读列表，复用 TenantController）
 *   - 风险关键词库（CRUD + 筛选，复用 RiskKeywordController）
 *   - 通知配置（通道开关 + Webhook 弹窗编辑 + 模板勾选保存）
 *   - Skill 管理（CRUD + 筛选，复用 SkillController）
 * - 权限矩阵：占位「敬请期待」
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { settingsApi } from '@/api/settings'
import { tenantApi } from '@/api/tenant'
import type { DepartmentVO, BusinessLineVO } from '@/api/tenant'
import type {
  ChannelConfigSaveRequest,
  ChannelVO,
  IPage,
  NotificationChannelType,
  NotificationConfigSaveRequest,
  NotificationTemplateConfigVO,
  NotificationTemplateType,
  RiskKeywordAddRequest,
  RiskKeywordQueryRequest,
  RiskKeywordVO,
  RoleVO,
  SkillAddRequest,
  SkillQueryRequest,
  SkillUpdateRequest,
  SkillVO,
  UserVO,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'

/** 子导航项类型 */
type SettingsTab =
  | 'users'
  | 'roles'
  | 'departments'
  | 'business-lines'
  | 'risk-keywords'
  | 'notification'
  | 'skills'
  | 'permissions'

/** 子导航配置 */
interface SubNavItem {
  /** 子导航 key */
  key: SettingsTab
  /** 显示文字 */
  label: string
  /** 图标 */
  icon: React.ReactNode
}

/** 子导航项列表（对齐原型 08-settings.html） */
const SUB_NAV_ITEMS: SubNavItem[] = [
  {
    key: 'users',
    label: '用户管理',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    ),
  },
  {
    key: 'roles',
    label: '角色管理',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </svg>
    ),
  },
  {
    key: 'departments',
    label: '部门管理',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 21h18" />
        <path d="M5 21V7l7-4 7 4v14" />
        <path d="M9 21v-6h6v6" />
      </svg>
    ),
  },
  {
    key: 'business-lines',
    label: '业务线',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <line x1="8" y1="6" x2="21" y2="6" />
        <line x1="8" y1="12" x2="21" y2="12" />
        <line x1="8" y1="18" x2="21" y2="18" />
        <line x1="3" y1="6" x2="3.01" y2="6" />
        <line x1="3" y1="12" x2="3.01" y2="12" />
        <line x1="3" y1="18" x2="3.01" y2="18" />
      </svg>
    ),
  },
  {
    key: 'risk-keywords',
    label: '风险关键词',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    ),
  },
  {
    key: 'notification',
    label: '通知配置',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
    ),
  },
  {
    key: 'skills',
    label: 'Skill 管理',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.6 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
      </svg>
    ),
  },
  {
    key: 'permissions',
    label: '权限矩阵',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
      </svg>
    ),
  },
]

/** 角色编码 → badge class 映射（对齐原型 operator/approver/viewer 三种颜色） */
const ROLE_BADGE_CLASS: Record<string, string> = {
  operator: 'role-badge-info',
  approver: 'role-badge-warning',
  viewer: 'role-badge-muted',
  org_admin: 'badge-success',
}

/** 频率标签映射（对齐原型「高频」「紧急」徽章） */
const FREQUENCY_LABEL: Record<string, string> = {
  high: '高频',
  urgent: '紧急',
  normal: '普通',
}

/** 频率徽章 class 映射 */
const FREQUENCY_BADGE_CLASS: Record<string, string> = {
  high: 'badge-warning',
  urgent: 'badge-danger',
  normal: 'badge-muted',
}

/** 用户头像背景色（按用户名首字符哈希） */
const AVATAR_COLORS: Record<string, string> = {
  张: '#1A3A5C',
  李: 'linear-gradient(135deg,#F97316,#F97316)',
  王: 'linear-gradient(135deg,#3B82F6,#79C0FF)',
  赵: 'linear-gradient(135deg,#EF4444,#F97316)',
}

/**
 * 系统设置页面
 *
 * 对齐原型 prototypes/08-settings.html：
 * - 左侧子导航（7 项）+ 右侧内容区
 * - 已实现 3 个区块（用户管理 / 角色管理 / 通知配置）
 * - 其余 4 项展示「敬请期待」占位
 */
function Settings() {
  // 1. 当前选中的子导航
  const [activeTab, setActiveTab] = useState<SettingsTab>('users')

  return (
    <div>
      {/* region 页面标题 + 面包屑 */}
      <div className="settings-header">
        <div>
          <h1 className="page-title" style={{ margin: 0 }}>
            设置
          </h1>
          <div className="breadcrumb">首页 / 管理 / 设置</div>
        </div>
      </div>
      {/* endregion */}

      {/* region 两栏布局 */}
      <div className="settings-layout">
        {/* 左侧子导航 */}
        <div className="glass-floating settings-sub-nav">
          {SUB_NAV_ITEMS.map((item) => (
            <button
              key={item.key}
              type="button"
              className={`settings-sub-nav-item ${
                activeTab === item.key ? 'active' : ''
              }`}
              onClick={() => setActiveTab(item.key)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </div>

        {/* 右侧内容区 */}
        <div>
          {activeTab === 'users' && <UsersSection />}
          {activeTab === 'roles' && <RolesSection />}
          {activeTab === 'departments' && <DepartmentsSection />}
          {activeTab === 'business-lines' && <BusinessLinesSection />}
          {activeTab === 'risk-keywords' && <RiskKeywordsSection />}
          {activeTab === 'notification' && <NotificationSection />}
          {activeTab === 'skills' && <SkillsSection />}
          {activeTab === 'permissions' && (
            <PlaceholderSection
              title="权限矩阵"
              desc="权限矩阵可视化编辑界面待开发。当前 RBAC 已支持三维度（部门×业务线×角色）权限校验。"
            />
          )}
        </div>
      </div>
      {/* endregion */}
    </div>
  )
}

// ============================================================
// 用户管理区块
// ============================================================

/** 用户管理区块（Mock 数据，后端 UserController 待开发） */
function UsersSection() {
  // 1. 查询用户列表
  const { data: users, isLoading, error } = useQuery<UserVO[], ApiError>({
    queryKey: ['settings', 'users'],
    queryFn: settingsApi.listUsers,
  })

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">
          用户管理
          <span className="mock-data-badge">Mock 数据 · 后端 TODO</span>
        </div>
        <button type="button" className="btn btn-primary btn-sm">
          <svg className="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          添加用户
        </button>
      </div>

      {error && (
        <div className="alert-danger">
          <span>加载用户列表失败：{error.message}</span>
        </div>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>用户名</th>
              <th>部门</th>
              <th>角色</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  加载中…
                </td>
              </tr>
            )}
            {!isLoading && users && users.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无用户数据
                </td>
              </tr>
            )}
            {users?.map((user) => {
              // 2. 取首字符作为头像
              const avatarChar = (user.realName || user.username || '?').charAt(0).toUpperCase()
              const avatarBg = AVATAR_COLORS[user.realName?.charAt(0)] || '#1A3A5C'
              return (
                <tr key={user.userId}>
                  <td>
                    <div className="flex items-center gap-sm">
                      <div
                        className="avatar"
                        style={{
                          width: 28,
                          height: 28,
                          fontSize: 11,
                          background: avatarBg,
                        }}
                      >
                        {avatarChar}
                      </div>
                      <span className="mono">{user.username}</span>
                    </div>
                  </td>
                  <td>{user.deptName}</td>
                  <td>
                    {user.roles.map((role) => (
                      <span
                        key={role}
                        className={`badge ${
                          ROLE_BADGE_CLASS[role] || 'badge-info'
                        }`}
                        style={{ marginRight: 4 }}
                      >
                        {role}
                      </span>
                    ))}
                  </td>
                  <td>
                    {user.enabled ? (
                      <span className="badge badge-success">启用</span>
                    ) : (
                      <span className="badge badge-muted">已禁用</span>
                    )}
                  </td>
                  <td>
                    <button type="button" className="btn-link">
                      编辑
                    </button>
                    <button type="button" className="btn-link">
                      重置密码
                    </button>
                    <button
                      type="button"
                      className="btn-link"
                      style={{
                        color: user.enabled
                          ? 'var(--accent-danger)'
                          : 'var(--accent-success)',
                      }}
                    >
                      {user.enabled ? '禁用' : '启用'}
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ============================================================
// 角色管理区块
// ============================================================

/** 角色管理区块（Mock 数据，后端 RoleController 待开发） */
function RolesSection() {
  // 1. 查询角色列表
  const { data: roles, isLoading, error } = useQuery<RoleVO[], ApiError>({
    queryKey: ['settings', 'roles'],
    queryFn: settingsApi.listRoles,
  })

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">
          角色管理
          <span className="mock-data-badge">Mock 数据 · 后端 TODO</span>
        </div>
        <button type="button" className="btn btn-primary btn-sm">
          <svg className="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新建角色
        </button>
      </div>

      {error && (
        <div className="alert-danger">
          <span>加载角色列表失败：{error.message}</span>
        </div>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>角色</th>
              <th>权限范围</th>
              <th>互斥约束</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  加载中…
                </td>
              </tr>
            )}
            {!isLoading && roles && roles.length === 0 && (
              <tr>
                <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无角色数据
                </td>
              </tr>
            )}
            {roles?.map((role) => (
              <tr key={role.roleId}>
                <td>
                  <span
                    className={`badge ${
                      ROLE_BADGE_CLASS[role.roleCode] || 'badge-info'
                    }`}
                  >
                    {role.roleCode}
                  </span>
                  <div
                    style={{
                      fontSize: 11,
                      color: 'var(--text-muted)',
                      marginTop: 4,
                    }}
                  >
                    {role.roleName}
                  </div>
                </td>
                <td>{role.permissionScope}</td>
                <td>
                  {role.mutualExclusion ? (
                    <span className="badge badge-warning">
                      {role.mutualExclusion}
                    </span>
                  ) : (
                    <span className="badge badge-success">无约束</span>
                  )}
                </td>
                <td>
                  <button type="button" className="btn-link">
                    编辑权限
                  </button>
                  <button
                    type="button"
                    className="btn-link"
                    style={{ color: 'var(--accent-danger)' }}
                    disabled={role.builtIn}
                    title={role.builtIn ? '内置角色不可删除' : undefined}
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ============================================================
// 通知配置区块
// ============================================================

/** 通知配置区块（复用现有 notification API，通道开关 + 模板勾选 + 保存） */
function NotificationSection() {
  const queryClient = useQueryClient()

  // 1. 查询通道列表
  const { data: channels, isLoading: channelsLoading } = useQuery<
    ChannelVO[],
    ApiError
  >({
    queryKey: ['settings', 'notification-channels'],
    queryFn: settingsApi.listNotificationChannels,
  })

  // 2. 查询模板列表
  const { data: templates, isLoading: templatesLoading } = useQuery<
    NotificationTemplateConfigVO[],
    ApiError
  >({
    queryKey: ['settings', 'notification-templates'],
    queryFn: settingsApi.listNotificationTemplates,
  })

  // 3. 通道开关本地状态（初始化自查询结果）
  const [channelEnabled, setChannelEnabled] = useState<
    Record<NotificationChannelType, boolean>
  >({
    wecom: true,
    dingtalk: true,
  })

  // 4. 模板启用本地状态
  const [templateEnabled, setTemplateEnabled] = useState<
    Record<NotificationTemplateType, boolean>
  >({
    APPROVAL_PENDING: true,
    APPROVAL_TIMEOUT: true,
    TASK_FAILED: true,
    NEEDS_HUMAN: true,
    RISK_ESCALATION: false,
  })

  // 5. 最后保存时间
  const [savedAt, setSavedAt] = useState<string>('2026-07-26T06:00:00.000Z')

  // 5.1 Webhook 编辑弹窗状态（P0-4：通道行「编辑 Webhook」按钮触发）
  const [webhookModalOpen, setWebhookModalOpen] = useState(false)
  const [editingChannel, setEditingChannel] = useState<ChannelVO | null>(null)
  const [webhookForm, setWebhookForm] = useState<{
    webhookUrl: string
    secret: string
    enabled: boolean
  }>({ webhookUrl: '', secret: '', enabled: true })
  const [webhookFormError, setWebhookFormError] = useState('')

  // 5.2 保存通道 Webhook 配置 mutation（P0-4，调用 PUT /notification/channels/{channel}）
  const saveChannelMutation = useMutation<
    ChannelVO,
    ApiError,
    { channel: string; body: ChannelConfigSaveRequest }
  >({
    mutationFn: ({ channel, body }) => settingsApi.saveChannelConfig(channel, body),
    onSuccess: () => {
      // 5.2.1 失效通道列表缓存，触发重新加载（拉取最新脱敏 webhookUrl）
      queryClient.invalidateQueries({
        queryKey: ['settings', 'notification-channels'],
      })
      setWebhookModalOpen(false)
    },
    onError: (err) => setWebhookFormError(err.message),
  })

  // 6. 通道数据加载完成后初始化本地状态（仅首次加载时同步，避免覆盖用户切换）
  useEffect(() => {
    if (channels) {
      setChannelEnabled((prev) => {
        const next = { ...prev }
        for (const ch of channels) {
          next[ch.channel] = ch.enabled ?? true
        }
        return next
      })
    }
  }, [channels])

  // 7. 模板数据加载完成后初始化本地状态
  useEffect(() => {
    if (templates) {
      setTemplateEnabled((prev) => {
        const next = { ...prev }
        for (const tpl of templates) {
          next[tpl.templateType] = tpl.enabled
        }
        return next
      })
    }
  }, [templates])

  // 8. 保存配置 mutation
  const saveMutation = useMutation<string, ApiError, NotificationConfigSaveRequest>(
    {
      mutationFn: settingsApi.saveNotificationConfig,
      onSuccess: (data) => {
        setSavedAt(data)
        // 8.1 失效通道与模板查询缓存，触发重新加载
        queryClient.invalidateQueries({
          queryKey: ['settings', 'notification-channels'],
        })
        queryClient.invalidateQueries({
          queryKey: ['settings', 'notification-templates'],
        })
      },
    },
  )

  // 9. 保存配置
  const handleSave = () => {
    const body: NotificationConfigSaveRequest = {
      channels: (channels || []).map((ch) => ({
        channel: ch.channel,
        enabled: channelEnabled[ch.channel] ?? true,
      })),
      templates: (templates || []).map((tpl) => ({
        templateType: tpl.templateType,
        enabled: templateEnabled[tpl.templateType] ?? false,
      })),
    }
    saveMutation.mutate(body)
  }

  // 10. 测试通道（调用 notification API 测试发送）
  const handleTest = (channel: NotificationChannelType) => {
    // 复用现有 notification 测试接口（这里仅给出提示，避免引入额外依赖）
    window.alert(`测试通道：${channel}（需调用 POST /api/notification/test）`)
  }

  // 10.1 打开 Webhook 编辑弹窗（P0-4）
  const handleEditWebhook = (channel: ChannelVO) => {
    setEditingChannel(channel)
    // 10.1.1 注意：通道列表返回的 webhookUrl 已脱敏（key/access_token=***），编辑时清空让用户重新填
    setWebhookForm({
      webhookUrl: '',
      secret: '',
      enabled: channel.enabled ?? true,
    })
    setWebhookFormError('')
    setWebhookModalOpen(true)
  }

  // 10.2 保存 Webhook 配置（P0-4）
  const handleSaveWebhook = () => {
    setWebhookFormError('')
    if (!editingChannel) return
    if (!webhookForm.webhookUrl.trim()) {
      setWebhookFormError('Webhook URL 不能为空')
      return
    }
    // 10.2.1 钉钉通道必填加签密钥（其他通道不传 secret）
    const isDingTalk = editingChannel.channel === 'dingtalk'
    if (isDingTalk && !webhookForm.secret.trim()) {
      setWebhookFormError('钉钉通道需填写加签密钥')
      return
    }
    const body: ChannelConfigSaveRequest = {
      webhookUrl: webhookForm.webhookUrl.trim(),
      secret: isDingTalk ? webhookForm.secret.trim() : undefined,
      enabled: webhookForm.enabled,
    }
    saveChannelMutation.mutate({ channel: editingChannel.channel, body })
  }

  return (
    <div className="settings-section">
      <div className="section-title">通知配置</div>

      <div className="glass-card-static notification-config-card">
        {/* region 通道表格 */}
        <div className="sub-block-title">通知通道</div>
        <div className="table-wrap" style={{ marginBottom: 'var(--space-md)' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>通道</th>
                <th>Webhook 地址</th>
                <th>启用</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {channelsLoading && (
                <tr>
                  <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                    加载中…
                  </td>
                </tr>
              )}
              {channels?.map((ch) => (
                <tr key={ch.channel}>
                  <td>
                    <div className="channel-name">
                      <svg
                        className="icon-sm"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        style={{ color: 'var(--accent-primary)' }}
                      >
                        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
                      </svg>
                      <span>{ch.label}</span>
                    </div>
                  </td>
                  <td className="mono" style={{ fontSize: 12 }}>
                    {ch.webhookUrl || '—'}
                  </td>
                  <td>
                    <label className="switch">
                      <input
                        type="checkbox"
                        checked={channelEnabled[ch.channel] ?? true}
                        onChange={(e) =>
                          setChannelEnabled((prev) => ({
                            ...prev,
                            [ch.channel]: e.target.checked,
                          }))
                        }
                      />
                      <span className="switch-slider"></span>
                    </label>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="btn-link"
                      onClick={() => handleEditWebhook(ch)}
                    >
                      编辑 Webhook
                    </button>
                    <button
                      type="button"
                      className="btn-test"
                      onClick={() => handleTest(ch.channel)}
                    >
                      测试
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {/* endregion */}

        {/* region 通知模板勾选 */}
        <div
          className="sub-block-title"
          style={{ marginTop: 'var(--space-md)' }}
        >
          通知模板
        </div>
        <div className="check-list">
          {templatesLoading && (
            <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
              加载中…
            </div>
          )}
          {templates?.map((tpl) => (
            <label key={tpl.templateType} className="check-item">
              <input
                type="checkbox"
                checked={templateEnabled[tpl.templateType] ?? false}
                onChange={(e) =>
                  setTemplateEnabled((prev) => ({
                    ...prev,
                    [tpl.templateType]: e.target.checked,
                  }))
                }
              />
              <div className="check-label">
                <div>{tpl.label}</div>
                <div className="check-desc">{tpl.description}</div>
              </div>
              <span
                className={`badge ${FREQUENCY_BADGE_CLASS[tpl.frequency] || 'badge-muted'}`}
              >
                {FREQUENCY_LABEL[tpl.frequency] || tpl.frequency}
              </span>
            </label>
          ))}
        </div>
        {/* endregion */}

        {/* region 底部保存栏 */}
        <div className="notification-config-footer">
          <div className="notification-config-footer-hint">
            最后保存于 {dayjs(savedAt).format('YYYY-MM-DD HH:mm:ss')}
          </div>
          <div className="flex gap-sm">
            <button type="button" className="btn btn-ghost btn-sm">
              取消
            </button>
            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={handleSave}
              disabled={saveMutation.isPending}
            >
              <svg
                className="icon-sm"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
                <polyline points="17 21 17 13 7 13 7 21" />
                <polyline points="7 3 7 8 15 8" />
              </svg>
              {saveMutation.isPending ? '保存中…' : '保存配置'}
            </button>
          </div>
        </div>
        {/* endregion */}

        {/* region 保存错误提示 */}
        {saveMutation.isError && (
          <div
            className="alert-danger"
            style={{ marginTop: 'var(--space-md)' }}
          >
            <span>保存失败：{saveMutation.error?.message}</span>
          </div>
        )}
        {/* endregion */}
      </div>

      {/* region Webhook 编辑弹窗（P0-4） */}
      {webhookModalOpen && editingChannel && (
        <div className="modal-overlay" onClick={() => setWebhookModalOpen(false)}>
          <div
            className="glass-card-static modal-card"
            style={{ maxWidth: 520 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title">
                编辑 Webhook · {editingChannel.label}
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setWebhookModalOpen(false)}
              >
                ✕
              </button>
            </div>
            <div className="modal-form">
              <div className="form-group">
                <label className="label">
                  Webhook URL<span className="required">*</span>
                </label>
                <input
                  className="input"
                  value={webhookForm.webhookUrl}
                  onChange={(e) =>
                    setWebhookForm((f) => ({ ...f, webhookUrl: e.target.value }))
                  }
                  placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
                />
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                  当前配置：
                  <span className="mono">
                    {editingChannel.webhookUrl || '未配置'}
                  </span>
                  （出于安全考虑，保存时需重新填入完整 URL）
                </div>
              </div>
              {editingChannel.channel === 'dingtalk' && (
                <div className="form-group">
                  <label className="label">
                    加签密钥<span className="required">*</span>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>
                      （仅钉钉机器人需要）
                    </span>
                  </label>
                  <input
                    className="input"
                    type="password"
                    value={webhookForm.secret}
                    onChange={(e) =>
                      setWebhookForm((f) => ({ ...f, secret: e.target.value }))
                    }
                    placeholder="SEC..."
                  />
                </div>
              )}
              <div className="form-group">
                <label className="label">启用状态</label>
                <label className="switch" style={{ display: 'inline-flex' }}>
                  <input
                    type="checkbox"
                    checked={webhookForm.enabled}
                    onChange={(e) =>
                      setWebhookForm((f) => ({ ...f, enabled: e.target.checked }))
                    }
                  />
                  <span className="switch-slider"></span>
                  <span style={{ marginLeft: 8, fontSize: 13 }}>
                    {webhookForm.enabled ? '启用' : '禁用'}
                  </span>
                </label>
              </div>
              {webhookFormError && (
                <div className="alert-danger" style={{ marginBottom: 12 }}>
                  <span>{webhookFormError}</span>
                </div>
              )}
            </div>
            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setWebhookModalOpen(false)}
                disabled={saveChannelMutation.isPending}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSaveWebhook}
                disabled={saveChannelMutation.isPending}
              >
                {saveChannelMutation.isPending ? '保存中…' : '保存配置'}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* endregion */}
    </div>
  )
}

// ============================================================
// 部门管理区块（只读列表，P0-3）
// ============================================================

/** 部门管理区块（只读，CRUD 待 P1） */
function DepartmentsSection() {
  // 1. 查询部门列表
  const { data: departments, isLoading, error } = useQuery<
    DepartmentVO[],
    ApiError
  >({
    queryKey: ['settings', 'departments'],
    queryFn: tenantApi.listDepartments,
  })

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">
          部门管理
          <span className="badge badge-muted" style={{ marginLeft: 8 }}>
            只读
          </span>
        </div>
      </div>

      {error && (
        <div className="alert-danger">
          <span>加载部门列表失败：{error.message}</span>
        </div>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>部门名称</th>
              <th>部门编码</th>
              <th>父部门 ID</th>
              <th>排序</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  加载中…
                </td>
              </tr>
            )}
            {!isLoading && departments && departments.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无部门数据
                </td>
              </tr>
            )}
            {departments?.map((dept) => (
              <tr key={dept.deptId}>
                <td>{dept.deptName}</td>
                <td className="mono">{dept.deptCode || '—'}</td>
                <td className="mono">{dept.parentId || '0'}</td>
                <td>{dept.sortOrder ?? '—'}</td>
                <td>
                  {dept.status === 1 ? (
                    <span className="badge badge-success">启用</span>
                  ) : (
                    <span className="badge badge-muted">禁用</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ============================================================
// 业务线管理区块（只读列表，P0-3）
// ============================================================

/** 业务线管理区块（只读，CRUD 待 P1） */
function BusinessLinesSection() {
  // 1. 查询业务线列表
  const { data: businessLines, isLoading, error } = useQuery<
    BusinessLineVO[],
    ApiError
  >({
    queryKey: ['settings', 'business-lines'],
    queryFn: tenantApi.listBusinessLines,
  })

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">
          业务线管理
          <span className="badge badge-muted" style={{ marginLeft: 8 }}>
            只读
          </span>
        </div>
      </div>

      {error && (
        <div className="alert-danger">
          <span>加载业务线列表失败：{error.message}</span>
        </div>
      )}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>业务线名称</th>
              <th>业务线编码</th>
              <th>描述</th>
              <th>排序</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  加载中…
                </td>
              </tr>
            )}
            {!isLoading && businessLines && businessLines.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无业务线数据
                </td>
              </tr>
            )}
            {businessLines?.map((bl) => (
              <tr key={bl.businessLineId}>
                <td>{bl.businessLineName}</td>
                <td className="mono">{bl.businessLineCode || '—'}</td>
                <td>{bl.description || '—'}</td>
                <td>{bl.sortOrder ?? '—'}</td>
                <td>
                  {bl.status === 1 ? (
                    <span className="badge badge-success">启用</span>
                  ) : (
                    <span className="badge badge-muted">禁用</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ============================================================
// 风险关键词库区块（P0-1，CRUD + 筛选）
// ============================================================

/** 行业选项 */
const RISK_INDUSTRY_OPTIONS = [
  { value: '', label: '全部行业' },
  { value: 'banking', label: '银行' },
  { value: 'insurance', label: '保险' },
  { value: 'securities', label: '证券' },
]

/** 行业标签映射 */
const RISK_INDUSTRY_LABEL: Record<string, string> = {
  banking: '银行',
  insurance: '保险',
  securities: '证券',
}

/** 分类选项 */
const RISK_CATEGORY_OPTIONS = [
  { value: '', label: '全部分类' },
  { value: 'high_risk_operation', label: '高风险操作' },
  { value: 'sensitive_data', label: '敏感数据' },
  { value: 'large_amount', label: '大额交易' },
]

/** 分类标签映射 */
const RISK_CATEGORY_LABEL: Record<string, string> = {
  high_risk_operation: '高风险操作',
  sensitive_data: '敏感数据',
  large_amount: '大额交易',
}

/** 风险类型选项 */
const RISK_TYPE_OPTIONS = [
  { value: '', label: '全部风险' },
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
]

/** 风险类型徽章 class 映射 */
const RISK_TYPE_BADGE_CLASS: Record<string, string> = {
  high: 'badge-danger',
  medium: 'badge-warning',
  low: 'badge-info',
}

/** 启用状态筛选选项 */
const RISK_ENABLED_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: '1', label: '启用' },
  { value: '0', label: '禁用' },
]

/** 风险关键词表单状态（新增 / 编辑共用） */
interface RiskKeywordFormState {
  keyword: string
  industry: string
  category: string
  riskType: string
  description: string
  enabled: number
}

/** 空表单初始值 */
const EMPTY_RISK_KEYWORD_FORM: RiskKeywordFormState = {
  keyword: '',
  industry: 'banking',
  category: 'high_risk_operation',
  riskType: 'medium',
  description: '',
  enabled: 1,
}

/** 风险关键词库区块 */
function RiskKeywordsSection() {
  const queryClient = useQueryClient()

  // 1. 筛选状态
  const [filters, setFilters] = useState<RiskKeywordQueryRequest>({
    current: 1,
    pageSize: 10,
    keyword: '',
    industry: '',
    category: '',
    riskType: '',
    enabled: '',
  })

  // 2. 弹窗状态
  const [modalOpen, setModalOpen] = useState(false)
  const [editingKeyword, setEditingKeyword] = useState<RiskKeywordVO | null>(null)
  const [form, setForm] = useState<RiskKeywordFormState>(EMPTY_RISK_KEYWORD_FORM)
  const [formError, setFormError] = useState('')

  // 3. 查询关键词列表
  const { data, isLoading, error } = useQuery<IPage<RiskKeywordVO>, ApiError>({
    queryKey: ['settings', 'risk-keywords', filters],
    queryFn: () => settingsApi.listRiskKeywords(filters),
  })

  // 4. 新增 mutation
  const addMutation = useMutation<string, ApiError, RiskKeywordAddRequest>({
    mutationFn: settingsApi.addRiskKeyword,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'risk-keywords'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 5. 更新 mutation
  const updateMutation = useMutation<boolean, ApiError, { id: string; body: RiskKeywordAddRequest }>({
    mutationFn: ({ id, body }) => settingsApi.updateRiskKeyword(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'risk-keywords'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 6. 删除 mutation
  const deleteMutation = useMutation<boolean, ApiError, string>({
    mutationFn: settingsApi.deleteRiskKeyword,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'risk-keywords'] })
    },
  })

  // 7. 启用 / 禁用切换（直接调 update）
  const toggleMutation = useMutation<
    boolean,
    ApiError,
    { keyword: RiskKeywordVO; enabled: number }
  >({
    mutationFn: ({ keyword, enabled }) =>
      settingsApi.updateRiskKeyword(keyword.keywordId, {
        keyword: keyword.keyword,
        industry: keyword.industry,
        category: keyword.category,
        riskType: keyword.riskType,
        description: keyword.description ?? '',
        enabled,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'risk-keywords'] })
    },
  })

  // 8. 打开新增弹窗
  const handleAdd = () => {
    setEditingKeyword(null)
    setForm(EMPTY_RISK_KEYWORD_FORM)
    setFormError('')
    setModalOpen(true)
  }

  // 9. 打开编辑弹窗
  const handleEdit = (keyword: RiskKeywordVO) => {
    setEditingKeyword(keyword)
    setForm({
      keyword: keyword.keyword,
      industry: keyword.industry,
      category: keyword.category,
      riskType: keyword.riskType,
      description: keyword.description ?? '',
      enabled: keyword.enabled,
    })
    setFormError('')
    setModalOpen(true)
  }

  // 10. 提交表单
  const handleSubmit = () => {
    setFormError('')
    if (!form.keyword.trim()) {
      setFormError('关键词不能为空')
      return
    }
    const body: RiskKeywordAddRequest = {
      keyword: form.keyword.trim(),
      industry: form.industry,
      category: form.category,
      riskType: form.riskType,
      description: form.description.trim(),
      enabled: form.enabled,
    }
    if (editingKeyword) {
      updateMutation.mutate({ id: editingKeyword.keywordId, body })
    } else {
      addMutation.mutate(body)
    }
  }

  // 11. 删除确认
  const handleDelete = (keyword: RiskKeywordVO) => {
    if (keyword.builtin === 1) {
      window.alert('内置关键词不可删除，仅可禁用')
      return
    }
    if (window.confirm(`确认删除关键词「${keyword.keyword}」？`)) {
      deleteMutation.mutate(keyword.keywordId)
    }
  }

  // 12. 重置筛选
  const handleResetFilters = () => {
    setFilters({
      current: 1,
      pageSize: 10,
      keyword: '',
      industry: '',
      category: '',
      riskType: '',
      enabled: '',
    })
  }

  // 13. 内置关键词编辑时禁用部分字段
  const isBuiltinEditing = editingKeyword?.builtin === 1
  const isSubmitting = addMutation.isPending || updateMutation.isPending

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">风险关键词库</div>
        <button type="button" className="btn btn-primary btn-sm" onClick={handleAdd}>
          <svg className="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新增关键词
        </button>
      </div>

      {error && (
        <div className="alert-danger">
          <span>加载关键词列表失败：{error.message}</span>
        </div>
      )}

      {/* region 筛选栏 */}
      <div className="filter-bar" style={{ marginBottom: 'var(--space-md)' }}>
        <div className="flex gap-sm" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            className="input"
            style={{ width: 200 }}
            placeholder="关键词搜索"
            value={filters.keyword ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, keyword: e.target.value, current: 1 }))
            }
          />
          <select
            className="select"
            style={{ width: 130 }}
            value={filters.industry ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, industry: e.target.value, current: 1 }))
            }
          >
            {RISK_INDUSTRY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <select
            className="select"
            style={{ width: 140 }}
            value={filters.category ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, category: e.target.value, current: 1 }))
            }
          >
            {RISK_CATEGORY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <select
            className="select"
            style={{ width: 110 }}
            value={filters.riskType ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, riskType: e.target.value, current: 1 }))
            }
          >
            {RISK_TYPE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <select
            className="select"
            style={{ width: 110 }}
            value={filters.enabled ?? ''}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                enabled: e.target.value === '' ? '' : Number(e.target.value),
                current: 1,
              }))
            }
          >
            {RISK_ENABLED_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <button type="button" className="btn btn-ghost btn-sm" onClick={handleResetFilters}>
            重置
          </button>
        </div>
      </div>
      {/* endregion */}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>关键词</th>
              <th>行业</th>
              <th>分类</th>
              <th>风险类型</th>
              <th>启用</th>
              <th>来源</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  加载中…
                </td>
              </tr>
            )}
            {!isLoading && data && data.records.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无关键词数据
                </td>
              </tr>
            )}
            {data?.records.map((keyword) => (
              <tr key={keyword.keywordId}>
                <td>
                  <div className="mono">{keyword.keyword}</div>
                  {keyword.description && (
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                      {keyword.description}
                    </div>
                  )}
                </td>
                <td>{RISK_INDUSTRY_LABEL[keyword.industry] || keyword.industry}</td>
                <td>{RISK_CATEGORY_LABEL[keyword.category] || keyword.category}</td>
                <td>
                  <span className={`badge ${RISK_TYPE_BADGE_CLASS[keyword.riskType] || 'badge-muted'}`}>
                    {keyword.riskType}
                  </span>
                </td>
                <td>
                  <label className="switch">
                    <input
                      type="checkbox"
                      checked={keyword.enabled === 1}
                      onChange={(e) =>
                        toggleMutation.mutate({
                          keyword,
                          enabled: e.target.checked ? 1 : 0,
                        })
                      }
                      disabled={toggleMutation.isPending}
                    />
                    <span className="switch-slider"></span>
                  </label>
                </td>
                <td>
                  {keyword.builtin === 1 ? (
                    <span className="badge badge-info">内置</span>
                  ) : (
                    <span className="badge badge-muted">自定义</span>
                  )}
                </td>
                <td>
                  <button type="button" className="btn-link" onClick={() => handleEdit(keyword)}>
                    编辑
                  </button>
                  <button
                    type="button"
                    className="btn-link"
                    style={{ color: 'var(--accent-danger)' }}
                    disabled={keyword.builtin === 1}
                    title={keyword.builtin === 1 ? '内置关键词不可删除' : undefined}
                    onClick={() => handleDelete(keyword)}
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* region 分页 */}
      {data && data.total > 0 && (
        <div className="filter-bar" style={{ marginTop: 'var(--space-md)' }}>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            共 {data.total} 条 · 第 {data.current} / {data.pages} 页
          </span>
          <div className="flex gap-sm">
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={data.current <= 1}
              onClick={() => setFilters((f) => ({ ...f, current: f.current - 1 }))}
            >
              上一页
            </button>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={data.current >= data.pages}
              onClick={() => setFilters((f) => ({ ...f, current: f.current + 1 }))}
            >
              下一页
            </button>
          </div>
        </div>
      )}
      {/* endregion */}

      {/* region 新增 / 编辑弹窗 */}
      {modalOpen && (
        <div className="modal-overlay" onClick={() => setModalOpen(false)}>
          <div
            className="glass-card-static modal-card"
            style={{ maxWidth: 480 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title">
                {editingKeyword ? '编辑关键词' : '新增关键词'}
                {isBuiltinEditing && (
                  <span className="badge badge-info" style={{ marginLeft: 8, fontSize: 11 }}>
                    内置 · 仅可改启用 / 描述
                  </span>
                )}
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setModalOpen(false)}
              >
                ✕
              </button>
            </div>
            <div className="modal-form">
              <div className="form-group">
                <label className="label">
                  关键词文本<span className="required">*</span>
                </label>
                <input
                  className="input"
                  value={form.keyword}
                  disabled={isBuiltinEditing}
                  onChange={(e) => setForm((f) => ({ ...f, keyword: e.target.value }))}
                  placeholder="如：大额转账"
                />
              </div>
              <div className="form-group">
                <label className="label">所属行业</label>
                <select
                  className="select"
                  value={form.industry}
                  disabled={isBuiltinEditing}
                  onChange={(e) => setForm((f) => ({ ...f, industry: e.target.value }))}
                >
                  <option value="banking">银行</option>
                  <option value="insurance">保险</option>
                  <option value="securities">证券</option>
                </select>
              </div>
              <div className="form-group">
                <label className="label">分类</label>
                <select
                  className="select"
                  value={form.category}
                  disabled={isBuiltinEditing}
                  onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                >
                  <option value="high_risk_operation">高风险操作</option>
                  <option value="sensitive_data">敏感数据</option>
                  <option value="large_amount">大额交易</option>
                </select>
              </div>
              <div className="form-group">
                <label className="label">风险类型</label>
                <select
                  className="select"
                  value={form.riskType}
                  disabled={isBuiltinEditing}
                  onChange={(e) => setForm((f) => ({ ...f, riskType: e.target.value }))}
                >
                  <option value="high">高</option>
                  <option value="medium">中</option>
                  <option value="low">低</option>
                </select>
              </div>
              <div className="form-group">
                <label className="label">描述说明</label>
                <textarea
                  className="textarea"
                  rows={2}
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="可选"
                />
              </div>
              <div className="form-group">
                <label className="label">启用状态</label>
                <select
                  className="select"
                  value={String(form.enabled)}
                  onChange={(e) => setForm((f) => ({ ...f, enabled: Number(e.target.value) }))}
                >
                  <option value="1">启用</option>
                  <option value="0">禁用</option>
                </select>
              </div>
              {formError && (
                <div className="alert-danger" style={{ marginBottom: 12 }}>
                  <span>{formError}</span>
                </div>
              )}
            </div>
            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setModalOpen(false)}
                disabled={isSubmitting}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSubmit}
                disabled={isSubmitting}
              >
                {isSubmitting ? '保存中…' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* endregion */}
    </div>
  )
}

// ============================================================
// Skill 元数据管理区块（P0-2，CRUD + 筛选，name 不可改）
// ============================================================

/** Skill 分类选项 */
const SKILL_CATEGORY_OPTIONS = [
  { value: '', label: '全部分类' },
  { value: 'auth', label: '认证' },
  { value: 'interaction', label: '交互' },
  { value: 'extraction', label: '提取' },
]

/** Skill 分类标签映射 */
const SKILL_CATEGORY_LABEL: Record<string, string> = {
  auth: '认证',
  interaction: '交互',
  extraction: '提取',
}

/** Skill 分类徽章 class 映射 */
const SKILL_CATEGORY_BADGE_CLASS: Record<string, string> = {
  auth: 'badge-info',
  interaction: 'badge-warning',
  extraction: 'badge-success',
}

/** 失败策略选项 */
const SKILL_ERROR_STRATEGY_OPTIONS = [
  { value: 'RETRY', label: '重试（RETRY）' },
  { value: 'SKIP', label: '跳过（SKIP）' },
  { value: 'ABORT', label: '终止（ABORT）' },
]

/** 失败策略标签映射 */
const SKILL_ERROR_STRATEGY_LABEL: Record<string, string> = {
  RETRY: '重试',
  SKIP: '跳过',
  ABORT: '终止',
}

/** 启用状态筛选选项 */
const SKILL_ENABLED_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: '1', label: '启用' },
  { value: '0', label: '禁用' },
]

/** Skill 表单状态（新增 / 编辑共用） */
interface SkillFormState {
  name: string
  description: string
  category: string
  errorStrategy: string
  maxRetries: number
  version: string
  paramSchema: string
  enabled: number
}

/** 空表单初始值 */
const EMPTY_SKILL_FORM: SkillFormState = {
  name: '',
  description: '',
  category: 'interaction',
  errorStrategy: 'RETRY',
  maxRetries: 1,
  version: '1.0.0',
  paramSchema: '',
  enabled: 1,
}

/**
 * Skill 元数据管理区块
 *
 * 功能：
 * - 分页表格展示 Skill 列表（name / 描述 / 分类 / 失败策略 / 版本 / 启用状态 / 操作）
 * - 筛选栏：分类 + 启用状态 + 名称搜索
 * - 操作：注册自定义 Skill（弹窗） / 更新（弹窗，name 不可改） / 启用-禁用切换
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
function SkillsSection() {
  const queryClient = useQueryClient()

  // 1. 筛选状态
  const [filters, setFilters] = useState<SkillQueryRequest>({
    current: 1,
    pageSize: 10,
    category: '',
    enabled: '',
    searchText: '',
  })

  // 2. 弹窗状态
  const [modalOpen, setModalOpen] = useState(false)
  const [editingSkill, setEditingSkill] = useState<SkillVO | null>(null)
  const [form, setForm] = useState<SkillFormState>(EMPTY_SKILL_FORM)
  const [formError, setFormError] = useState('')

  // 3. 查询 Skill 列表
  const { data, isLoading, error } = useQuery<IPage<SkillVO>, ApiError>({
    queryKey: ['settings', 'skills', filters],
    queryFn: () => settingsApi.listSkills(filters),
  })

  // 4. 注册 mutation
  const addMutation = useMutation<SkillVO, ApiError, SkillAddRequest>({
    mutationFn: settingsApi.registerSkill,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'skills'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 5. 更新 mutation（按 name 路径参数更新）
  const updateMutation = useMutation<
    boolean,
    ApiError,
    { name: string; body: SkillUpdateRequest }
  >({
    mutationFn: ({ name, body }) => settingsApi.updateSkill(name, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'skills'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 6. 启用 / 禁用切换（直接调 update，仅传 enabled）
  const toggleMutation = useMutation<
    boolean,
    ApiError,
    { skill: SkillVO; enabled: number }
  >({
    mutationFn: ({ skill, enabled }) =>
      settingsApi.updateSkill(skill.name, { enabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'skills'] })
    },
  })

  // 7. 打开新增弹窗
  const handleAdd = () => {
    setEditingSkill(null)
    setForm(EMPTY_SKILL_FORM)
    setFormError('')
    setModalOpen(true)
  }

  // 8. 打开编辑弹窗（name 不可改，禁用 name 输入框）
  const handleEdit = (skill: SkillVO) => {
    setEditingSkill(skill)
    setForm({
      name: skill.name,
      description: skill.description ?? '',
      category: skill.category,
      errorStrategy: skill.errorStrategy ?? 'RETRY',
      maxRetries: skill.maxRetries ?? 1,
      version: skill.version ?? '1.0.0',
      paramSchema: skill.paramSchema ?? '',
      enabled: skill.enabled,
    })
    setFormError('')
    setModalOpen(true)
  }

  // 9. 提交表单
  const handleSubmit = () => {
    setFormError('')
    if (!editingSkill && !form.name.trim()) {
      setFormError('Skill name 不能为空')
      return
    }
    if (editingSkill) {
      // 9.1 编辑：name 不可改，仅传可更新字段
      const body: SkillUpdateRequest = {
        description: form.description.trim(),
        category: form.category,
        errorStrategy: form.errorStrategy,
        maxRetries: form.maxRetries,
        version: form.version.trim() || undefined,
        paramSchema: form.paramSchema.trim() || undefined,
        enabled: form.enabled,
      }
      updateMutation.mutate({ name: editingSkill.name, body })
    } else {
      // 9.2 新增：POST 前后端会调 Python 校验 name 存在性
      const body: SkillAddRequest = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        category: form.category,
        errorStrategy: form.errorStrategy,
        maxRetries: form.maxRetries,
        version: form.version.trim() || undefined,
        paramSchema: form.paramSchema.trim() || undefined,
      }
      addMutation.mutate(body)
    }
  }

  // 10. 重置筛选
  const handleResetFilters = () => {
    setFilters({
      current: 1,
      pageSize: 10,
      category: '',
      enabled: '',
      searchText: '',
    })
  }

  // 11. 编辑模式下 name 字段禁用
  const isEditing = editingSkill !== null
  const isSubmitting = addMutation.isPending || updateMutation.isPending

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">Skill 元数据管理</div>
        <button type="button" className="btn btn-primary btn-sm" onClick={handleAdd}>
          <svg className="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          注册 Skill
        </button>
      </div>

      {error && (
        <div className="alert-danger">
          <span>加载 Skill 列表失败：{error.message}</span>
        </div>
      )}

      {/* region 筛选栏 */}
      <div className="filter-bar" style={{ marginBottom: 'var(--space-md)' }}>
        <div className="flex gap-sm" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            className="input"
            style={{ width: 200 }}
            placeholder="名称 / 描述搜索"
            value={filters.searchText ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, searchText: e.target.value, current: 1 }))
            }
          />
          <select
            className="select"
            style={{ width: 130 }}
            value={filters.category ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, category: e.target.value, current: 1 }))
            }
          >
            {SKILL_CATEGORY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <select
            className="select"
            style={{ width: 110 }}
            value={filters.enabled ?? ''}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                enabled: e.target.value === '' ? '' : Number(e.target.value),
                current: 1,
              }))
            }
          >
            {SKILL_ENABLED_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <button type="button" className="btn btn-ghost btn-sm" onClick={handleResetFilters}>
            重置
          </button>
        </div>
      </div>
      {/* endregion */}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Skill 名称</th>
              <th>描述</th>
              <th>分类</th>
              <th>失败策略</th>
              <th>重试</th>
              <th>版本</th>
              <th>启用</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  加载中…
                </td>
              </tr>
            )}
            {!isLoading && data && data.records.length === 0 && (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无 Skill 数据
                </td>
              </tr>
            )}
            {data?.records.map((skill) => (
              <tr key={skill.skillId}>
                <td>
                  <div className="mono">{skill.name}</div>
                </td>
                <td>{skill.description || '—'}</td>
                <td>
                  <span className={`badge ${SKILL_CATEGORY_BADGE_CLASS[skill.category] || 'badge-muted'}`}>
                    {SKILL_CATEGORY_LABEL[skill.category] || skill.category}
                  </span>
                </td>
                <td>
                  <span className="badge badge-muted">
                    {SKILL_ERROR_STRATEGY_LABEL[skill.errorStrategy ?? ''] || skill.errorStrategy || '—'}
                  </span>
                </td>
                <td className="mono">{skill.maxRetries ?? '—'}</td>
                <td className="mono">{skill.version || '—'}</td>
                <td>
                  <label className="switch">
                    <input
                      type="checkbox"
                      checked={skill.enabled === 1}
                      onChange={(e) =>
                        toggleMutation.mutate({
                          skill,
                          enabled: e.target.checked ? 1 : 0,
                        })
                      }
                      disabled={toggleMutation.isPending}
                    />
                    <span className="switch-slider"></span>
                  </label>
                </td>
                <td>
                  <button type="button" className="btn-link" onClick={() => handleEdit(skill)}>
                    编辑
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* region 分页 */}
      {data && data.total > 0 && (
        <div className="filter-bar" style={{ marginTop: 'var(--space-md)' }}>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            共 {data.total} 条 · 第 {data.current} / {data.pages} 页
          </span>
          <div className="flex gap-sm">
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={data.current <= 1}
              onClick={() => setFilters((f) => ({ ...f, current: f.current - 1 }))}
            >
              上一页
            </button>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={data.current >= data.pages}
              onClick={() => setFilters((f) => ({ ...f, current: f.current + 1 }))}
            >
              下一页
            </button>
          </div>
        </div>
      )}
      {/* endregion */}

      {/* region 新增 / 编辑弹窗 */}
      {modalOpen && (
        <div className="modal-overlay" onClick={() => setModalOpen(false)}>
          <div
            className="glass-card-static modal-card"
            style={{ maxWidth: 560 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title">
                {isEditing ? '编辑 Skill' : '注册 Skill'}
                {isEditing && (
                  <span className="badge badge-info" style={{ marginLeft: 8, fontSize: 11 }}>
                    name 不可修改
                  </span>
                )}
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setModalOpen(false)}
              >
                ✕
              </button>
            </div>
            <div className="modal-form">
              <div className="form-group">
                <label className="label">
                  Skill 名称<span className="required">*</span>
                  <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>
                    （唯一标识，注册后不可改）
                  </span>
                </label>
                <input
                  className="input"
                  value={form.name}
                  disabled={isEditing}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder="如：login / form_fill"
                />
              </div>
              <div className="form-group">
                <label className="label">用途描述</label>
                <input
                  className="input"
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="可选"
                />
              </div>
              <div className="form-group">
                <label className="label">分类</label>
                <select
                  className="select"
                  value={form.category}
                  onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                >
                  <option value="auth">认证（auth）</option>
                  <option value="interaction">交互（interaction）</option>
                  <option value="extraction">提取（extraction）</option>
                </select>
              </div>
              <div className="form-group">
                <label className="label">失败策略</label>
                <select
                  className="select"
                  value={form.errorStrategy}
                  onChange={(e) => setForm((f) => ({ ...f, errorStrategy: e.target.value }))}
                >
                  {SKILL_ERROR_STRATEGY_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label className="label">最大重试次数</label>
                <input
                  type="number"
                  className="input"
                  min={0}
                  max={10}
                  value={form.maxRetries}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      maxRetries: Math.max(0, Number(e.target.value) || 0),
                    }))
                  }
                />
              </div>
              <div className="form-group">
                <label className="label">版本号</label>
                <input
                  className="input"
                  value={form.version}
                  onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))}
                  placeholder="如：1.0.0"
                />
              </div>
              <div className="form-group">
                <label className="label">
                  参数 JSON Schema
                  <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>
                    （可选）
                  </span>
                </label>
                <textarea
                  className="textarea"
                  rows={3}
                  value={form.paramSchema}
                  onChange={(e) => setForm((f) => ({ ...f, paramSchema: e.target.value }))}
                  placeholder='{"username":{"type":"string"}}'
                />
              </div>
              {isEditing && (
                <div className="form-group">
                  <label className="label">启用状态</label>
                  <select
                    className="select"
                    value={String(form.enabled)}
                    onChange={(e) => setForm((f) => ({ ...f, enabled: Number(e.target.value) }))}
                  >
                    <option value="1">启用</option>
                    <option value="0">禁用</option>
                  </select>
                </div>
              )}
              {formError && (
                <div className="alert-danger" style={{ marginBottom: 12 }}>
                  <span>{formError}</span>
                </div>
              )}
            </div>
            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setModalOpen(false)}
                disabled={isSubmitting}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSubmit}
                disabled={isSubmitting}
              >
                {isSubmitting ? '保存中…' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* endregion */}
    </div>
  )
}

// ============================================================
// 占位区块
// ============================================================

/** 占位区块（未实现子导航内容） */
function PlaceholderSection({ title, desc }: { title: string; desc: string }) {
  return (
    <div className="settings-section">
      <div className="section-title">{title}</div>
      <div className="glass-card-static settings-placeholder">
        <div className="settings-placeholder-icon">
          <svg
            width="48"
            height="48"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <div className="settings-placeholder-title">敬请期待</div>
        <div className="settings-placeholder-desc">{desc}</div>
      </div>
    </div>
  )
}

export default Settings

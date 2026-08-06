/**
 * 系统设置页面（P4 settings 原型对齐 + P0/P1 功能扩展）
 *
 * 功能：
 * - 两栏布局：240px 子导航 + 1fr 内容区
 * - 9 个子导航项：用户管理 / 角色管理 / 部门管理 / 业务线 / 风险关键词 / 风控配置 / 通知配置 / Skill 管理 / 权限矩阵
 * - 已实现区块：
 *   - 用户管理（P1 USR-1，CRUD + 启停 + 重置密码 + 分配角色）
 *   - 角色管理（P1 USR-2，CRUD + 启停，内置角色保护）
 *   - 部门 / 业务线（只读列表，复用 TenantController）
 *   - 风险关键词库（CRUD + 筛选，复用 RiskKeywordController）
 *   - 风控配置（P1 RSK-1 审批超时 + P1 RSK-3 审批人映射）
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
import type {
  ApprovalRouteConfigAddRequest,
  ApprovalRouteConfigQueryRequest,
  ApprovalRouteConfigUpdateRequest,
  ApprovalRouteConfigVO,
  ApprovalTimeoutConfigUpdateRequest,
  ApprovalTimeoutConfigVO,
  ChannelConfigSaveRequest,
  ChannelVO,
  IPage,
  LoginPolicyUpdateRequest,
  LoginPolicyVO,
  NotificationChannelType,
  NotificationConfigSaveRequest,
  NotificationTemplateConfigVO,
  NotificationTemplateType,
  PasswordPolicyUpdateRequest,
  PasswordPolicyVO,
  PasswordResetRequest,
  RiskKeywordAddRequest,
  RiskKeywordQueryRequest,
  RiskKeywordVO,
  RoleAddRequest,
  RolePermissionMatrixVO,
  RolePermissionSaveRequest,
  RoleQueryRequest,
  RoleUpdateRequest,
  RoleVO,
  SessionQueryRequest,
  SessionVO,
  SkillAddRequest,
  SystemConfigUpdateRequest,
  SystemConfigVO,
  SystemHealthVO,
  SkillQueryRequest,
  SkillUpdateRequest,
  SkillVO,
  UserAddRequest,
  UserQueryRequest,
  UserRoleAssignRequest,
  UserUpdateRequest,
  UserVO,
} from '@/api/types'
import { settingsApi } from '@/api/settings'
import { tenantApi } from '@/api/tenant'
import type { DepartmentVO, BusinessLineVO } from '@/api/tenant'
import { ApiError } from '@/api/AxiosClient'

/** 子导航项类型 */
type SettingsTab =
  | 'users'
  | 'roles'
  | 'departments'
  | 'business-lines'
  | 'risk-keywords'
  | 'risk-control'
  | 'security'
  | 'notification'
  | 'skills'
  | 'ai-config'
  | 'storage-config'
  | 'scheduler-config'
  | 'system-params'
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
    key: 'risk-control',
    label: '风控配置',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        <path d="M9 12l2 2 4-4" />
      </svg>
    ),
  },
  {
    key: 'security',
    label: '安全策略',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
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
  {
    key: 'ai-config',
    label: 'AI 配置',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="4" y="4" width="16" height="16" rx="2" />
        <rect x="9" y="9" width="6" height="6" />
        <line x1="9" y1="2" x2="9" y2="4" />
        <line x1="15" y1="2" x2="15" y2="4" />
        <line x1="9" y1="20" x2="9" y2="22" />
        <line x1="15" y1="20" x2="15" y2="22" />
        <line x1="20" y1="9" x2="22" y2="9" />
        <line x1="20" y1="14" x2="22" y2="14" />
        <line x1="2" y1="9" x2="4" y2="9" />
        <line x1="2" y1="14" x2="4" y2="14" />
      </svg>
    ),
  },
  {
    key: 'storage-config',
    label: '存储配置',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <ellipse cx="12" cy="5" rx="9" ry="3" />
        <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
        <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
      </svg>
    ),
  },
  {
    key: 'scheduler-config',
    label: '定时任务',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" />
        <polyline points="12 6 12 12 16 14" />
      </svg>
    ),
  },
  {
    key: 'system-params',
    label: '系统参数',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.6 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
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
          {activeTab === 'risk-control' && <RiskControlSection />}
          {activeTab === 'security' && (
            <>
              <SecurityPolicySection />
              <LoginPolicySection />
              <SessionManagementSection />
              <SystemHealthSection />
            </>
          )}
          {activeTab === 'notification' && <NotificationSection />}
          {activeTab === 'skills' && <SkillsSection />}
          {activeTab === 'ai-config' && (
            <SystemConfigSection title="AI 配置" prefix="ai." />
          )}
          {activeTab === 'storage-config' && (
            <SystemConfigSection title="存储配置" prefix="minio." />
          )}
          {activeTab === 'scheduler-config' && (
            <SystemConfigSection title="定时任务" prefix="scheduler." />
          )}
          {activeTab === 'system-params' && (
            <SystemConfigSection title="系统参数" prefix="__others__" />
          )}
          {activeTab === 'permissions' && <PermissionMatrixSection />}
        </div>
      </div>
      {/* endregion */}
    </div>
  )
}

// ============================================================
// 用户管理区块（P1 USR-1，完整 CRUD + 启停 + 重置密码 + 分配角色）
// ============================================================

/** 用户表单空状态 */
const EMPTY_USER_FORM: UserFormState = {
  username: '',
  realName: '',
  deptName: '',
  email: '',
  phone: '',
  password: '',
  status: 1,
}

/** 用户表单状态 */
interface UserFormState {
  username: string
  realName: string
  deptName: string
  email: string
  phone: string
  password: string
  status: number
}

/** 用户管理区块（P1 USR-1） */
function UsersSection() {
  const queryClient = useQueryClient()

  // 1. 筛选状态
  const [filters, setFilters] = useState<UserQueryRequest>({
    keyword: '',
    status: undefined,
    current: 1,
    pageSize: 10,
  })

  // 2. 新增/编辑弹窗状态
  const [modalOpen, setModalOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<UserVO | null>(null)
  const [form, setForm] = useState<UserFormState>(EMPTY_USER_FORM)
  const [formError, setFormError] = useState('')

  // 3. 分配角色弹窗状态
  const [rolesModalOpen, setRolesModalOpen] = useState(false)
  const [rolesTarget, setRolesTarget] = useState<UserVO | null>(null)
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([])
  const [rolesError, setRolesError] = useState('')

  // 4. 查询用户列表（分页）
  const { data, isLoading, error } = useQuery<IPage<UserVO>, ApiError>({
    queryKey: ['settings', 'users', filters],
    queryFn: () => settingsApi.listUsers(filters),
  })

  // 5. 查询全部启用角色（分配角色弹窗用）
  const { data: allRoles } = useQuery<RoleVO[], ApiError>({
    queryKey: ['settings', 'roles-all'],
    queryFn: settingsApi.listAllRoles,
  })

  // 6. 新增 mutation
  const addMutation = useMutation<string, ApiError, UserAddRequest>({
    mutationFn: settingsApi.addUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'users'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 7. 编辑 mutation
  const updateMutation = useMutation<boolean, ApiError, UserUpdateRequest>({
    mutationFn: settingsApi.updateUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'users'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 8. 启停 mutation
  const toggleMutation = useMutation<
    boolean,
    ApiError,
    { userId: string; status: number }
  >({
    mutationFn: ({ userId, status }) =>
      settingsApi.toggleUserStatus(userId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'users'] })
    },
  })

  // 9. 重置密码 mutation
  const resetPwdMutation = useMutation<boolean, ApiError, PasswordResetRequest>({
    mutationFn: settingsApi.resetPassword,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'users'] })
    },
  })

  // 10. 删除 mutation
  const deleteMutation = useMutation<boolean, ApiError, string>({
    mutationFn: settingsApi.deleteUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'users'] })
    },
  })

  // 11. 分配角色 mutation
  const assignRolesMutation = useMutation<boolean, ApiError, UserRoleAssignRequest>(
    {
      mutationFn: settingsApi.assignUserRoles,
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['settings', 'users'] })
        setRolesModalOpen(false)
      },
      onError: (err) => setRolesError(err.message),
    },
  )

  // 12. 打开新增弹窗
  const handleAdd = () => {
    setEditingUser(null)
    setForm(EMPTY_USER_FORM)
    setFormError('')
    setModalOpen(true)
  }

  // 13. 打开编辑弹窗
  const handleEdit = (user: UserVO) => {
    setEditingUser(user)
    setForm({
      username: user.username,
      realName: user.realName,
      deptName: user.deptName ?? '',
      email: user.email ?? '',
      phone: user.phone ?? '',
      password: '',
      status: user.status,
    })
    setFormError('')
    setModalOpen(true)
  }

  // 14. 提交表单
  const handleSubmit = () => {
    setFormError('')
    if (!form.username.trim()) {
      setFormError('用户名不能为空')
      return
    }
    if (!form.realName.trim()) {
      setFormError('真实姓名不能为空')
      return
    }
    if (editingUser) {
      // 14.1 编辑：用户名不可改
      updateMutation.mutate({
        userId: editingUser.userId,
        realName: form.realName.trim(),
        deptName: form.deptName.trim() || undefined,
        email: form.email.trim() || undefined,
        phone: form.phone.trim() || undefined,
        status: form.status,
      })
    } else {
      // 14.2 新增
      addMutation.mutate({
        username: form.username.trim(),
        realName: form.realName.trim(),
        password: form.password.trim() || undefined,
        deptName: form.deptName.trim() || undefined,
        email: form.email.trim() || undefined,
        phone: form.phone.trim() || undefined,
        status: form.status,
      })
    }
  }

  // 15. 启停用户
  const handleToggle = (user: UserVO) => {
    const nextStatus = user.status === 1 ? 0 : 1
    toggleMutation.mutate({ userId: user.userId, status: nextStatus })
  }

  // 16. 重置密码
  const handleResetPassword = (user: UserVO) => {
    if (
      window.confirm(
        `确认重置用户「${user.realName || user.username}」的密码？将恢复为默认密码 Finrpa@2026`,
      )
    ) {
      resetPwdMutation.mutate({ userId: user.userId })
    }
  }

  // 17. 删除用户
  const handleDelete = (user: UserVO) => {
    if (
      window.confirm(`确认删除用户「${user.realName || user.username}」？`)
    ) {
      deleteMutation.mutate(user.userId)
    }
  }

  // 18. 打开分配角色弹窗
  const handleOpenRoles = (user: UserVO) => {
    setRolesTarget(user)
    // 18.1 根据用户当前 roleCode 反查 roleId 预选
    const preSelected = (allRoles || [])
      .filter((r) => user.roles.includes(r.roleCode))
      .map((r) => r.roleId)
    setSelectedRoleIds(preSelected)
    setRolesError('')
    setRolesModalOpen(true)
  }

  // 19. 提交分配角色
  const handleSubmitRoles = () => {
    if (!rolesTarget) return
    setRolesError('')
    if (selectedRoleIds.length === 0) {
      setRolesError('至少需要分配一个角色')
      return
    }
    // 19.1 三维度 RBAC：当前简化为不带部门/业务线维度（仅角色维度）
    const relations = selectedRoleIds.map((roleId) => ({ roleId }))
    assignRolesMutation.mutate({
      userId: rolesTarget.userId,
      relations,
    })
  }

  // 20. 重置筛选
  const handleResetFilters = () => {
    setFilters({ keyword: '', status: undefined, current: 1, pageSize: 10 })
  }

  const isSubmitting = addMutation.isPending || updateMutation.isPending
  const users = data?.records ?? []

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">用户管理</div>
        <button type="button" className="btn btn-primary btn-sm" onClick={handleAdd}>
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

      {/* region 筛选栏 */}
      <div className="filter-bar" style={{ marginBottom: 'var(--space-md)' }}>
        <div className="flex gap-sm" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            className="input"
            style={{ width: 200 }}
            placeholder="用户名 / 真实姓名"
            value={filters.keyword ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, keyword: e.target.value, current: 1 }))
            }
          />
          <select
            className="select"
            style={{ width: 130 }}
            value={filters.status ?? ''}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                status: e.target.value === '' ? undefined : Number(e.target.value),
                current: 1,
              }))
            }
          >
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
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
            {!isLoading && users.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无用户数据
                </td>
              </tr>
            )}
            {users.map((user) => {
              // 21. 取首字符作为头像
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
                      <div>
                        <div className="mono">{user.username}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                          {user.realName}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td>{user.deptName || '—'}</td>
                  <td>
                    {user.roles.length === 0 ? (
                      <span style={{ color: 'var(--text-muted)' }}>未分配</span>
                    ) : (
                      user.roles.map((role) => (
                        <span
                          key={role}
                          className={`badge ${
                            ROLE_BADGE_CLASS[role] || 'badge-info'
                          }`}
                          style={{ marginRight: 4 }}
                        >
                          {role}
                        </span>
                      ))
                    )}
                  </td>
                  <td>
                    {user.status === 1 ? (
                      <span className="badge badge-success">启用</span>
                    ) : (
                      <span className="badge badge-muted">已禁用</span>
                    )}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="btn-link"
                      onClick={() => handleEdit(user)}
                    >
                      编辑
                    </button>
                    <button
                      type="button"
                      className="btn-link"
                      onClick={() => handleOpenRoles(user)}
                    >
                      分配角色
                    </button>
                    <button
                      type="button"
                      className="btn-link"
                      onClick={() => handleResetPassword(user)}
                      disabled={resetPwdMutation.isPending}
                    >
                      重置密码
                    </button>
                    <button
                      type="button"
                      className="btn-link"
                      style={{
                        color:
                          user.status === 1
                            ? 'var(--accent-danger)'
                            : 'var(--accent-success)',
                      }}
                      onClick={() => handleToggle(user)}
                      disabled={toggleMutation.isPending}
                    >
                      {user.status === 1 ? '禁用' : '启用'}
                    </button>
                    <button
                      type="button"
                      className="btn-link"
                      style={{ color: 'var(--accent-danger)' }}
                      onClick={() => handleDelete(user)}
                      disabled={deleteMutation.isPending}
                    >
                      删除
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* region 分页 */}
      {data && data.total > 0 && (
        <div className="pagination" style={{ marginTop: 'var(--space-md)' }}>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === 1}
            onClick={() => setFilters((f) => ({ ...f, current: 1 }))}
          >
            首页
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === 1}
            onClick={() =>
              setFilters((f) => ({ ...f, current: Math.max(1, f.current! - 1) }))
            }
          >
            上一页
          </button>
          <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>
            第 {filters.current} / {data.pages} 页 · 共 {data.total} 条
          </span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === data.pages}
            onClick={() =>
              setFilters((f) => ({ ...f, current: f.current! + 1 }))
            }
          >
            下一页
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === data.pages}
            onClick={() => setFilters((f) => ({ ...f, current: data.pages }))}
          >
            末页
          </button>
        </div>
      )}
      {/* endregion */}

      {/* region 新增/编辑用户弹窗 */}
      {modalOpen && (
        <div className="modal-overlay" onClick={() => setModalOpen(false)}>
          <div
            className="glass-card-static modal-card"
            style={{ maxWidth: 520 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title">
                {editingUser ? '编辑用户' : '新增用户'}
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
                  用户名<span className="required">*</span>
                </label>
                <input
                  className="input"
                  value={form.username}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, username: e.target.value }))
                  }
                  disabled={!!editingUser}
                  placeholder="3-32 位字母数字下划线"
                />
                {editingUser && (
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                    用户名创建后不可修改
                  </div>
                )}
              </div>
              <div className="form-group">
                <label className="label">
                  真实姓名<span className="required">*</span>
                </label>
                <input
                  className="input"
                  value={form.realName}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, realName: e.target.value }))
                  }
                />
              </div>
              {!editingUser && (
                <div className="form-group">
                  <label className="label">
                    密码
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>
                      （留空使用默认密码 Finrpa@2026）
                    </span>
                  </label>
                  <input
                    className="input"
                    type="password"
                    value={form.password}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, password: e.target.value }))
                    }
                    placeholder="留空使用默认密码"
                  />
                </div>
              )}
              <div className="form-group">
                <label className="label">所属部门名称</label>
                <input
                  className="input"
                  value={form.deptName}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, deptName: e.target.value }))
                  }
                />
              </div>
              <div className="form-group">
                <label className="label">邮箱</label>
                <input
                  className="input"
                  type="email"
                  value={form.email}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, email: e.target.value }))
                  }
                />
              </div>
              <div className="form-group">
                <label className="label">手机号</label>
                <input
                  className="input"
                  value={form.phone}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, phone: e.target.value }))
                  }
                />
              </div>
              <div className="form-group">
                <label className="label">状态</label>
                <label className="switch" style={{ display: 'inline-flex' }}>
                  <input
                    type="checkbox"
                    checked={form.status === 1}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, status: e.target.checked ? 1 : 0 }))
                    }
                  />
                  <span className="switch-slider"></span>
                  <span style={{ marginLeft: 8, fontSize: 13 }}>
                    {form.status === 1 ? '启用' : '禁用'}
                  </span>
                </label>
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

      {/* region 分配角色弹窗 */}
      {rolesModalOpen && rolesTarget && (
        <div className="modal-overlay" onClick={() => setRolesModalOpen(false)}>
          <div
            className="glass-card-static modal-card"
            style={{ maxWidth: 480 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title">
                分配角色 · {rolesTarget.realName || rolesTarget.username}
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setRolesModalOpen(false)}
              >
                ✕
              </button>
            </div>
            <div className="modal-form">
              <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 8 }}>
                勾选要分配的角色（全量替换，提交后原有关联将被清空）
              </div>
              <div className="check-list">
                {(allRoles || []).map((role) => (
                  <label key={role.roleId} className="check-item">
                    <input
                      type="checkbox"
                      checked={selectedRoleIds.includes(role.roleId)}
                      onChange={(e) => {
                        setSelectedRoleIds((prev) =>
                          e.target.checked
                            ? [...prev, role.roleId]
                            : prev.filter((id) => id !== role.roleId),
                        )
                      }}
                    />
                    <div className="check-label">
                      <div>
                        <span
                          className={`badge ${
                            ROLE_BADGE_CLASS[role.roleCode] || 'badge-info'
                          }`}
                          style={{ marginRight: 6 }}
                        >
                          {role.roleCode}
                        </span>
                        {role.roleName}
                      </div>
                      {role.description && (
                        <div className="check-desc">{role.description}</div>
                      )}
                    </div>
                  </label>
                ))}
              </div>
              {rolesError && (
                <div className="alert-danger" style={{ marginBottom: 12 }}>
                  <span>{rolesError}</span>
                </div>
              )}
            </div>
            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setRolesModalOpen(false)}
                disabled={assignRolesMutation.isPending}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSubmitRoles}
                disabled={assignRolesMutation.isPending}
              >
                {assignRolesMutation.isPending ? '保存中…' : '保存分配'}
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
// 角色管理区块（P1 USR-2，完整 CRUD + 启停，内置角色保护）
// ============================================================

/** 角色表单空状态 */
const EMPTY_ROLE_FORM: RoleFormState = {
  roleCode: '',
  roleName: '',
  description: '',
  isCrossOrgRead: 0,
  isCrossOrgApprove: 0,
  status: 1,
}

/** 角色表单状态 */
interface RoleFormState {
  roleCode: string
  roleName: string
  description: string
  isCrossOrgRead: number
  isCrossOrgApprove: number
  status: number
}

/** 角色管理区块（P1 USR-2） */
function RolesSection() {
  const queryClient = useQueryClient()

  // 1. 筛选状态
  const [filters, setFilters] = useState<RoleQueryRequest>({
    keyword: '',
    status: undefined,
    current: 1,
    pageSize: 10,
  })

  // 2. 新增/编辑弹窗状态
  const [modalOpen, setModalOpen] = useState(false)
  const [editingRole, setEditingRole] = useState<RoleVO | null>(null)
  const [form, setForm] = useState<RoleFormState>(EMPTY_ROLE_FORM)
  const [formError, setFormError] = useState('')

  // 3. 查询角色列表（分页）
  const { data, isLoading, error } = useQuery<IPage<RoleVO>, ApiError>({
    queryKey: ['settings', 'roles', filters],
    queryFn: () => settingsApi.listRoles(filters),
  })

  // 4. 新增 mutation
  const addMutation = useMutation<string, ApiError, RoleAddRequest>({
    mutationFn: settingsApi.addRole,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'roles'] })
      queryClient.invalidateQueries({ queryKey: ['settings', 'roles-all'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 5. 编辑 mutation
  const updateMutation = useMutation<boolean, ApiError, RoleUpdateRequest>({
    mutationFn: settingsApi.updateRole,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'roles'] })
      queryClient.invalidateQueries({ queryKey: ['settings', 'roles-all'] })
      setModalOpen(false)
    },
    onError: (err) => setFormError(err.message),
  })

  // 6. 启停 mutation
  const toggleMutation = useMutation<
    boolean,
    ApiError,
    { roleId: string; status: number }
  >({
    mutationFn: ({ roleId, status }) =>
      settingsApi.toggleRoleStatus(roleId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'roles'] })
    },
  })

  // 7. 删除 mutation
  const deleteMutation = useMutation<boolean, ApiError, string>({
    mutationFn: settingsApi.deleteRole,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'roles'] })
      queryClient.invalidateQueries({ queryKey: ['settings', 'roles-all'] })
    },
  })

  // 8. 打开新增弹窗
  const handleAdd = () => {
    setEditingRole(null)
    setForm(EMPTY_ROLE_FORM)
    setFormError('')
    setModalOpen(true)
  }

  // 9. 打开编辑弹窗
  const handleEdit = (role: RoleVO) => {
    setEditingRole(role)
    setForm({
      roleCode: role.roleCode,
      roleName: role.roleName,
      description: role.description ?? '',
      isCrossOrgRead: role.isCrossOrgRead ?? 0,
      isCrossOrgApprove: role.isCrossOrgApprove ?? 0,
      status: role.status,
    })
    setFormError('')
    setModalOpen(true)
  }

  // 10. 提交表单
  const handleSubmit = () => {
    setFormError('')
    if (!form.roleCode.trim()) {
      setFormError('角色编码不能为空')
      return
    }
    if (!form.roleName.trim()) {
      setFormError('角色名称不能为空')
      return
    }
    if (editingRole) {
      // 10.1 编辑：roleCode 不可改；内置角色仅可改 description / status
      updateMutation.mutate({
        roleId: editingRole.roleId,
        roleName: form.roleName.trim(),
        description: form.description.trim() || undefined,
        isCrossOrgRead: form.isCrossOrgRead,
        isCrossOrgApprove: form.isCrossOrgApprove,
        status: form.status,
      })
    } else {
      // 10.2 新增
      addMutation.mutate({
        roleCode: form.roleCode.trim(),
        roleName: form.roleName.trim(),
        description: form.description.trim() || undefined,
        isCrossOrgRead: form.isCrossOrgRead,
        isCrossOrgApprove: form.isCrossOrgApprove,
        status: form.status,
      })
    }
  }

  // 11. 启停角色
  const handleToggle = (role: RoleVO) => {
    const nextStatus = role.status === 1 ? 0 : 1
    toggleMutation.mutate({ roleId: role.roleId, status: nextStatus })
  }

  // 12. 删除角色
  const handleDelete = (role: RoleVO) => {
    if (role.builtIn) {
      window.alert(`内置角色「${role.roleCode}」不可删除`)
      return
    }
    if (window.confirm(`确认删除角色「${role.roleName}」？`)) {
      deleteMutation.mutate(role.roleId)
    }
  }

  // 13. 重置筛选
  const handleResetFilters = () => {
    setFilters({ keyword: '', status: undefined, current: 1, pageSize: 10 })
  }

  const isSubmitting = addMutation.isPending || updateMutation.isPending
  const roles = data?.records ?? []

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <div className="section-title">角色管理</div>
        <button type="button" className="btn btn-primary btn-sm" onClick={handleAdd}>
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

      {/* region 筛选栏 */}
      <div className="filter-bar" style={{ marginBottom: 'var(--space-md)' }}>
        <div className="flex gap-sm" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            className="input"
            style={{ width: 200 }}
            placeholder="角色名称 / 编码"
            value={filters.keyword ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, keyword: e.target.value, current: 1 }))
            }
          />
          <select
            className="select"
            style={{ width: 130 }}
            value={filters.status ?? ''}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                status: e.target.value === '' ? undefined : Number(e.target.value),
                current: 1,
              }))
            }
          >
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
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
              <th>角色</th>
              <th>描述</th>
              <th>跨组织权限</th>
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
            {!isLoading && roles.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  暂无角色数据
                </td>
              </tr>
            )}
            {roles.map((role) => (
              <tr key={role.roleId}>
                <td>
                  <span
                    className={`badge ${
                      ROLE_BADGE_CLASS[role.roleCode] || 'badge-info'
                    }`}
                  >
                    {role.roleCode}
                  </span>
                  {role.builtIn && (
                    <span
                      className="badge badge-muted"
                      style={{ marginLeft: 4 }}
                      title="内置角色，受保护"
                    >
                      内置
                    </span>
                  )}
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
                <td style={{ fontSize: 12 }}>{role.description || '—'}</td>
                <td style={{ fontSize: 12 }}>
                  {role.isCrossOrgRead === 1 && (
                    <span className="badge badge-info" style={{ marginRight: 4 }}>
                      跨组织读
                    </span>
                  )}
                  {role.isCrossOrgApprove === 1 && (
                    <span className="badge badge-warning">跨组织审批</span>
                  )}
                  {role.isCrossOrgRead !== 1 && role.isCrossOrgApprove !== 1 && (
                    <span style={{ color: 'var(--text-muted)' }}>无</span>
                  )}
                </td>
                <td>
                  {role.status === 1 ? (
                    <span className="badge badge-success">启用</span>
                  ) : (
                    <span className="badge badge-muted">已禁用</span>
                  )}
                </td>
                <td>
                  <button
                    type="button"
                    className="btn-link"
                    onClick={() => handleEdit(role)}
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    className="btn-link"
                    style={{
                      color:
                        role.status === 1
                          ? 'var(--accent-danger)'
                          : 'var(--accent-success)',
                    }}
                    onClick={() => handleToggle(role)}
                    disabled={
                      toggleMutation.isPending ||
                      (role.status === 1 &&
                        ['super_admin', 'org_admin'].includes(role.roleCode))
                    }
                    title={
                      role.status === 1 &&
                      ['super_admin', 'org_admin'].includes(role.roleCode)
                        ? '内置管理员角色禁止禁用'
                        : undefined
                    }
                  >
                    {role.status === 1 ? '禁用' : '启用'}
                  </button>
                  <button
                    type="button"
                    className="btn-link"
                    style={{ color: 'var(--accent-danger)' }}
                    onClick={() => handleDelete(role)}
                    disabled={deleteMutation.isPending || role.builtIn}
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

      {/* region 分页 */}
      {data && data.total > 0 && (
        <div className="pagination" style={{ marginTop: 'var(--space-md)' }}>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === 1}
            onClick={() => setFilters((f) => ({ ...f, current: 1 }))}
          >
            首页
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === 1}
            onClick={() =>
              setFilters((f) => ({ ...f, current: Math.max(1, f.current! - 1) }))
            }
          >
            上一页
          </button>
          <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>
            第 {filters.current} / {data.pages} 页 · 共 {data.total} 条
          </span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === data.pages}
            onClick={() =>
              setFilters((f) => ({ ...f, current: f.current! + 1 }))
            }
          >
            下一页
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={filters.current === data.pages}
            onClick={() => setFilters((f) => ({ ...f, current: data.pages }))}
          >
            末页
          </button>
        </div>
      )}
      {/* endregion */}

      {/* region 新增/编辑角色弹窗 */}
      {modalOpen && (
        <div className="modal-overlay" onClick={() => setModalOpen(false)}>
          <div
            className="glass-card-static modal-card"
            style={{ maxWidth: 500 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title">
                {editingRole ? '编辑角色' : '新建角色'}
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
                  角色编码<span className="required">*</span>
                </label>
                <input
                  className="input"
                  value={form.roleCode}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, roleCode: e.target.value }))
                  }
                  disabled={!!editingRole}
                  placeholder="字母开头，仅允许字母数字下划线"
                />
                {editingRole && (
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                    角色编码创建后不可修改
                  </div>
                )}
                {!editingRole && (
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                    内置编码（super_admin / org_admin / operator / approver / viewer）禁止新增
                  </div>
                )}
              </div>
              <div className="form-group">
                <label className="label">
                  角色名称<span className="required">*</span>
                </label>
                <input
                  className="input"
                  value={form.roleName}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, roleName: e.target.value }))
                  }
                />
              </div>
              <div className="form-group">
                <label className="label">角色描述 / 权限范围</label>
                <input
                  className="input"
                  value={form.description}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, description: e.target.value }))
                  }
                  placeholder="如：任务执行 · Skill 调用 · 数据查看"
                />
              </div>
              <div className="form-group">
                <label className="label">跨组织读取</label>
                <label className="switch" style={{ display: 'inline-flex' }}>
                  <input
                    type="checkbox"
                    checked={form.isCrossOrgRead === 1}
                    onChange={(e) =>
                      setForm((f) => ({
                        ...f,
                        isCrossOrgRead: e.target.checked ? 1 : 0,
                      }))
                    }
                  />
                  <span className="switch-slider"></span>
                  <span style={{ marginLeft: 8, fontSize: 13 }}>
                    {form.isCrossOrgRead === 1 ? '允许' : '禁止'}
                  </span>
                </label>
              </div>
              <div className="form-group">
                <label className="label">跨组织审批</label>
                <label className="switch" style={{ display: 'inline-flex' }}>
                  <input
                    type="checkbox"
                    checked={form.isCrossOrgApprove === 1}
                    onChange={(e) =>
                      setForm((f) => ({
                        ...f,
                        isCrossOrgApprove: e.target.checked ? 1 : 0,
                      }))
                    }
                  />
                  <span className="switch-slider"></span>
                  <span style={{ marginLeft: 8, fontSize: 13 }}>
                    {form.isCrossOrgApprove === 1 ? '允许' : '禁止'}
                  </span>
                </label>
              </div>
              <div className="form-group">
                <label className="label">状态</label>
                <label className="switch" style={{ display: 'inline-flex' }}>
                  <input
                    type="checkbox"
                    checked={form.status === 1}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, status: e.target.checked ? 1 : 0 }))
                    }
                  />
                  <span className="switch-slider"></span>
                  <span style={{ marginLeft: 8, fontSize: 13 }}>
                    {form.status === 1 ? '启用' : '禁用'}
                  </span>
                </label>
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
// 风控配置区块（P1 RSK-1 审批超时 + P1 RSK-3 审批人映射）
// ============================================================

/** 风险等级 → 中文标签映射 */
const RISK_LEVEL_LABEL: Record<string, string> = {
  high: '高风险',
  critical: '严重风险',
}

/** 风险等级 → 徽章 class 映射 */
const RISK_LEVEL_BADGE_CLASS: Record<string, string> = {
  high: 'badge-warning',
  critical: 'badge-danger',
}

/** 审批人映射表单空状态 */
const EMPTY_ROUTE_FORM: RouteFormState = {
  riskLevel: 'high',
  businessLineId: '',
  approverUserId: '',
  departmentId: '',
  description: '',
  enabled: 1,
}

/** 审批人映射表单状态 */
interface RouteFormState {
  riskLevel: string
  businessLineId: string
  approverUserId: string
  departmentId: string
  description: string
  enabled: number
}

/**
 * 风控配置区块
 *
 * 功能：
 * - 上半区：审批超时阈值配置（high / critical 两条，行内编辑）
 * - 下半区：审批人映射列表（风险等级 × 业务线 → 审批人，CRUD + 启停）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
function RiskControlSection() {
  const queryClient = useQueryClient()

  // 1. 审批超时配置查询
  const { data: timeoutConfigs, isLoading: timeoutLoading } = useQuery<
    ApprovalTimeoutConfigVO[],
    ApiError
  >({
    queryKey: ['settings', 'approval-timeout'],
    queryFn: settingsApi.listApprovalTimeoutConfigs,
  })

  // 2. 审批超时本地编辑态（riskLevel → timeoutMinutes/description/enabled）
  const [timeoutEdits, setTimeoutEdits] = useState<
    Record<string, { timeoutMinutes: number; description: string; enabled: number }>
  >({})
  const [timeoutSavingLevel, setTimeoutSavingLevel] = useState<string>('')
  const [timeoutError, setTimeoutError] = useState('')

  // 3. 同步后端数据到本地编辑态
  useEffect(() => {
    if (timeoutConfigs) {
      const next: typeof timeoutEdits = {}
      timeoutConfigs.forEach((c) => {
        next[c.riskLevel] = {
          timeoutMinutes: c.timeoutMinutes,
          description: c.description ?? '',
          enabled: c.enabled,
        }
      })
      setTimeoutEdits(next)
    }
  }, [timeoutConfigs])

  // 4. 审批超时更新 mutation
  const updateTimeoutMutation = useMutation<
    ApprovalTimeoutConfigVO,
    ApiError,
    { riskLevel: string; body: ApprovalTimeoutConfigUpdateRequest }
  >({
    mutationFn: ({ riskLevel, body }) =>
      settingsApi.updateApprovalTimeoutConfig(riskLevel, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'approval-timeout'] })
      setTimeoutSavingLevel('')
      setTimeoutError('')
    },
    onError: (err) => {
      setTimeoutError(err.message)
      setTimeoutSavingLevel('')
    },
  })

  // 5. 保存审批超时配置
  const handleSaveTimeout = (riskLevel: string) => {
    const edit = timeoutEdits[riskLevel]
    if (!edit) return
    setTimeoutError('')
    if (edit.timeoutMinutes < 1 || edit.timeoutMinutes > 1440) {
      setTimeoutError('超时分钟数应在 1-1440 之间')
      return
    }
    setTimeoutSavingLevel(riskLevel)
    updateTimeoutMutation.mutate({
      riskLevel,
      body: {
        timeoutMinutes: edit.timeoutMinutes,
        description: edit.description || undefined,
        enabled: edit.enabled,
      },
    })
  }

  // 6. 审批人映射筛选状态
  const [routeFilters, setRouteFilters] = useState<ApprovalRouteConfigQueryRequest>({
    current: 1,
    pageSize: 10,
    riskLevel: '',
    businessLineId: '',
    enabled: undefined,
  })

  // 7. 审批人映射列表查询
  const { data: routePage, isLoading: routeLoading, error: routeError } = useQuery<
    IPage<ApprovalRouteConfigVO>,
    ApiError
  >({
    queryKey: ['settings', 'approval-routes', routeFilters],
    queryFn: () => settingsApi.listApprovalRouteConfigs(routeFilters),
  })

  // 8. 业务线 / 用户下拉数据（用于新增 / 编辑弹窗）
  const { data: businessLines } = useQuery<BusinessLineVO[], ApiError>({
    queryKey: ['tenant', 'business-lines'],
    queryFn: tenantApi.listBusinessLines,
  })
  const { data: usersPage } = useQuery<IPage<UserVO>, ApiError>({
    queryKey: ['settings', 'users', { current: 1, pageSize: 1000 }],
    queryFn: () =>
      settingsApi.listUsers({ current: 1, pageSize: 1000 }),
  })
  const approverOptions = (usersPage?.records ?? []).filter((u) => u.status === 1)

  // 9. 新增 / 编辑弹窗状态
  const [routeModalOpen, setRouteModalOpen] = useState(false)
  const [editingRoute, setEditingRoute] = useState<ApprovalRouteConfigVO | null>(null)
  const [routeForm, setRouteForm] = useState<RouteFormState>(EMPTY_ROUTE_FORM)
  const [routeFormError, setRouteFormError] = useState('')

  // 10. 新增 mutation
  const addRouteMutation = useMutation<string, ApiError, ApprovalRouteConfigAddRequest>({
    mutationFn: settingsApi.addApprovalRouteConfig,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'approval-routes'] })
      setRouteModalOpen(false)
    },
    onError: (err) => setRouteFormError(err.message),
  })

  // 11. 更新 mutation
  const updateRouteMutation = useMutation<
    boolean,
    ApiError,
    { configId: string; body: ApprovalRouteConfigUpdateRequest }
  >({
    mutationFn: ({ configId, body }) =>
      settingsApi.updateApprovalRouteConfig(configId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'approval-routes'] })
      setRouteModalOpen(false)
    },
    onError: (err) => setRouteFormError(err.message),
  })

  // 12. 删除 mutation
  const deleteRouteMutation = useMutation<boolean, ApiError, string>({
    mutationFn: settingsApi.deleteApprovalRouteConfig,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'approval-routes'] })
    },
  })

  // 13. 启停 mutation（直接调 update）
  const toggleRouteMutation = useMutation<
    boolean,
    ApiError,
    { route: ApprovalRouteConfigVO; enabled: number }
  >({
    mutationFn: ({ route, enabled }) =>
      settingsApi.updateApprovalRouteConfig(route.configId, { enabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'approval-routes'] })
    },
  })

  // 14. 打开新增弹窗
  const handleAddRoute = () => {
    setEditingRoute(null)
    setRouteForm(EMPTY_ROUTE_FORM)
    setRouteFormError('')
    setRouteModalOpen(true)
  }

  // 15. 打开编辑弹窗（riskLevel / businessLineId 不可改）
  const handleEditRoute = (route: ApprovalRouteConfigVO) => {
    setEditingRoute(route)
    setRouteForm({
      riskLevel: route.riskLevel,
      businessLineId: route.businessLineId ?? '',
      approverUserId: route.approverUserId,
      departmentId: route.departmentId ?? '',
      description: route.description ?? '',
      enabled: route.enabled,
    })
    setRouteFormError('')
    setRouteModalOpen(true)
  }

  // 16. 提交表单
  const handleSubmitRoute = () => {
    setRouteFormError('')
    if (!routeForm.riskLevel) {
      setRouteFormError('请选择风险等级')
      return
    }
    if (!routeForm.approverUserId) {
      setRouteFormError('请选择审批人')
      return
    }
    if (editingRoute) {
      updateRouteMutation.mutate({
        configId: editingRoute.configId,
        body: {
          approverUserId: routeForm.approverUserId,
          departmentId: routeForm.departmentId || undefined,
          description: routeForm.description || undefined,
          enabled: routeForm.enabled,
        },
      })
    } else {
      addRouteMutation.mutate({
        riskLevel: routeForm.riskLevel,
        businessLineId: routeForm.businessLineId || undefined,
        approverUserId: routeForm.approverUserId,
        departmentId: routeForm.departmentId || undefined,
        description: routeForm.description || undefined,
        enabled: routeForm.enabled,
      })
    }
  }

  // 17. 删除审批人映射
  const handleDeleteRoute = (route: ApprovalRouteConfigVO) => {
    if (
      window.confirm(
        `确认删除该审批人映射？\n风险等级：${RISK_LEVEL_LABEL[route.riskLevel] ?? route.riskLevel}\n业务线：${route.businessLineName ?? '默认路由'}\n审批人：${route.approverName ?? route.approverUserId}`,
      )
    ) {
      deleteRouteMutation.mutate(route.configId)
    }
  }

  // 18. 启停审批人映射
  const handleToggleRoute = (route: ApprovalRouteConfigVO) => {
    const next = route.enabled === 1 ? 0 : 1
    toggleRouteMutation.mutate({ route, enabled: next })
  }

  // 19. 重置筛选
  const handleResetRouteFilters = () => {
    setRouteFilters({
      current: 1,
      pageSize: 10,
      riskLevel: '',
      businessLineId: '',
      enabled: undefined,
    })
  }

  const routeRecords = routePage?.records ?? []
  const isRouteSubmitting = addRouteMutation.isPending || updateRouteMutation.isPending

  return (
    <div className="settings-section">
      <div className="section-title">风控配置</div>

      {/* region 审批超时阈值配置 */}
      <div className="glass-card-static" style={{ padding: 20, marginBottom: 20 }}>
        <div
          className="flex items-center justify-between"
          style={{ marginBottom: 12 }}
        >
          <div>
            <div style={{ fontWeight: 600, fontSize: 15 }}>
              审批超时阈值配置
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
              高风险 / 严重风险审批超时后任务自动终止并通知对应审批人
            </div>
          </div>
        </div>

        {timeoutError && (
          <div className="alert-danger" style={{ marginBottom: 12 }}>
            <span>{timeoutError}</span>
          </div>
        )}

        {timeoutLoading && (
          <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>加载中…</div>
        )}

        {timeoutConfigs &&
          timeoutConfigs.map((cfg) => {
            const edit = timeoutEdits[cfg.riskLevel] ?? {
              timeoutMinutes: cfg.timeoutMinutes,
              description: cfg.description ?? '',
              enabled: cfg.enabled,
            }
            return (
              <div
                key={cfg.riskLevel}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '120px 160px 1fr 120px auto',
                  gap: 12,
                  alignItems: 'center',
                  padding: '12px 0',
                  borderTop: '1px solid var(--border-subtle)',
                }}
              >
                <span
                  className={`badge ${RISK_LEVEL_BADGE_CLASS[cfg.riskLevel] ?? 'badge-info'}`}
                >
                  {RISK_LEVEL_LABEL[cfg.riskLevel] ?? cfg.riskLevel}
                </span>
                <div className="flex items-center gap-sm">
                  <input
                    type="number"
                    className="input"
                    style={{ width: 90 }}
                    min={1}
                    max={1440}
                    value={edit.timeoutMinutes}
                    onChange={(e) =>
                      setTimeoutEdits((s) => ({
                        ...s,
                        [cfg.riskLevel]: {
                          ...s[cfg.riskLevel],
                          timeoutMinutes: Number(e.target.value),
                        },
                      }))
                    }
                  />
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    分钟
                  </span>
                </div>
                <input
                  className="input"
                  placeholder="描述说明"
                  value={edit.description}
                  onChange={(e) =>
                    setTimeoutEdits((s) => ({
                      ...s,
                      [cfg.riskLevel]: {
                        ...s[cfg.riskLevel],
                        description: e.target.value,
                      },
                    }))
                  }
                />
                <label className="switch" style={{ display: 'inline-flex' }}>
                  <input
                    type="checkbox"
                    checked={edit.enabled === 1}
                    onChange={(e) =>
                      setTimeoutEdits((s) => ({
                        ...s,
                        [cfg.riskLevel]: {
                          ...s[cfg.riskLevel],
                          enabled: e.target.checked ? 1 : 0,
                        },
                      }))
                    }
                  />
                  <span className="switch-slider"></span>
                  <span style={{ marginLeft: 8, fontSize: 12 }}>
                    {edit.enabled === 1 ? '启用' : '禁用'}
                  </span>
                </label>
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => handleSaveTimeout(cfg.riskLevel)}
                  disabled={timeoutSavingLevel === cfg.riskLevel}
                >
                  {timeoutSavingLevel === cfg.riskLevel ? '保存中…' : '保存'}
                </button>
              </div>
            )
          })}
      </div>
      {/* endregion */}

      {/* region 审批人映射配置 */}
      <div className="settings-section-header">
        <div className="section-title" style={{ fontSize: 15 }}>
          审批人映射配置
        </div>
        <button type="button" className="btn btn-primary btn-sm" onClick={handleAddRoute}>
          <svg
            className="icon-sm"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新增映射
        </button>
      </div>

      {routeError && (
        <div className="alert-danger" style={{ marginBottom: 12 }}>
          <span>加载审批人映射失败：{routeError.message}</span>
        </div>
      )}

      {/* 筛选栏 */}
      <div className="filter-bar" style={{ marginBottom: 'var(--space-md)' }}>
        <div
          className="flex gap-sm"
          style={{ flexWrap: 'wrap', alignItems: 'center' }}
        >
          <select
            className="select"
            style={{ width: 140 }}
            value={routeFilters.riskLevel ?? ''}
            onChange={(e) =>
              setRouteFilters((f) => ({
                ...f,
                riskLevel: e.target.value || undefined,
                current: 1,
              }))
            }
          >
            <option value="">全部风险等级</option>
            <option value="high">高风险</option>
            <option value="critical">严重风险</option>
          </select>
          <select
            className="select"
            style={{ width: 160 }}
            value={routeFilters.businessLineId ?? ''}
            onChange={(e) =>
              setRouteFilters((f) => ({
                ...f,
                businessLineId: e.target.value || undefined,
                current: 1,
              }))
            }
          >
            <option value="">全部业务线</option>
            {(businessLines ?? []).map((bl) => (
              <option key={bl.businessLineId} value={bl.businessLineId}>
                {bl.businessLineName}
              </option>
            ))}
          </select>
          <select
            className="select"
            style={{ width: 130 }}
            value={
              routeFilters.enabled === undefined ? '' : String(routeFilters.enabled)
            }
            onChange={(e) =>
              setRouteFilters((f) => ({
                ...f,
                enabled:
                  e.target.value === '' ? undefined : Number(e.target.value),
                current: 1,
              }))
            }
          >
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
          </select>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={handleResetRouteFilters}
          >
            重置
          </button>
        </div>
      </div>

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>风险等级</th>
              <th>业务线</th>
              <th>审批人</th>
              <th>说明</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {routeLoading && (
              <tr>
                <td
                  colSpan={6}
                  style={{ textAlign: 'center', color: 'var(--text-muted)' }}
                >
                  加载中…
                </td>
              </tr>
            )}
            {!routeLoading && routeRecords.length === 0 && (
              <tr>
                <td
                  colSpan={6}
                  style={{ textAlign: 'center', color: 'var(--text-muted)' }}
                >
                  暂无审批人映射数据
                </td>
              </tr>
            )}
            {routeRecords.map((route) => (
              <tr key={route.configId}>
                <td>
                  <span
                    className={`badge ${RISK_LEVEL_BADGE_CLASS[route.riskLevel] ?? 'badge-info'}`}
                  >
                    {RISK_LEVEL_LABEL[route.riskLevel] ?? route.riskLevel}
                  </span>
                </td>
                <td>{route.businessLineName ?? '默认路由'}</td>
                <td>{route.approverName ?? route.approverUserId}</td>
                <td>{route.description || '—'}</td>
                <td>
                  {route.enabled === 1 ? (
                    <span className="badge badge-success">启用</span>
                  ) : (
                    <span className="badge badge-muted">已禁用</span>
                  )}
                </td>
                <td>
                  <button
                    type="button"
                    className="btn-link"
                    onClick={() => handleEditRoute(route)}
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    className="btn-link"
                    style={{
                      color:
                        route.enabled === 1
                          ? 'var(--accent-danger)'
                          : 'var(--accent-success)',
                    }}
                    onClick={() => handleToggleRoute(route)}
                    disabled={toggleRouteMutation.isPending}
                  >
                    {route.enabled === 1 ? '禁用' : '启用'}
                  </button>
                  <button
                    type="button"
                    className="btn-link"
                    style={{ color: 'var(--accent-danger)' }}
                    onClick={() => handleDeleteRoute(route)}
                    disabled={deleteRouteMutation.isPending}
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 分页 */}
      {routePage && routePage.total > 0 && (
        <div className="pagination" style={{ marginTop: 'var(--space-md)' }}>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={routeFilters.current === 1}
            onClick={() => setRouteFilters((f) => ({ ...f, current: 1 }))}
          >
            首页
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={routeFilters.current === 1}
            onClick={() =>
              setRouteFilters((f) => ({
                ...f,
                current: Math.max(1, (f.current ?? 1) - 1),
              }))
            }
          >
            上一页
          </button>
          <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>
            第 {routeFilters.current} / {routePage.pages} 页 · 共 {routePage.total} 条
          </span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={routeFilters.current === routePage.pages}
            onClick={() =>
              setRouteFilters((f) => ({ ...f, current: (f.current ?? 1) + 1 }))
            }
          >
            下一页
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={routeFilters.current === routePage.pages}
            onClick={() =>
              setRouteFilters((f) => ({ ...f, current: routePage.pages }))
            }
          >
            末页
          </button>
        </div>
      )}

      {/* 新增 / 编辑审批人映射弹窗 */}
      {routeModalOpen && (
        <div className="modal-overlay" onClick={() => setRouteModalOpen(false)}>
          <div
            className="glass-card-static modal-card"
            style={{ maxWidth: 520 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="modal-title">
                {editingRoute ? '编辑审批人映射' : '新增审批人映射'}
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setRouteModalOpen(false)}
              >
                ✕
              </button>
            </div>
            <div className="modal-form">
              <div className="form-group">
                <label className="label">
                  风险等级<span className="required">*</span>
                </label>
                <select
                  className="select"
                  value={routeForm.riskLevel}
                  onChange={(e) =>
                    setRouteForm((f) => ({ ...f, riskLevel: e.target.value }))
                  }
                  disabled={!!editingRoute}
                >
                  <option value="high">高风险</option>
                  <option value="critical">严重风险</option>
                </select>
                {editingRoute && (
                  <div
                    style={{
                      fontSize: 11,
                      color: 'var(--text-muted)',
                      marginTop: 4,
                    }}
                  >
                    风险等级创建后不可修改
                  </div>
                )}
              </div>
              <div className="form-group">
                <label className="label">业务线</label>
                <select
                  className="select"
                  value={routeForm.businessLineId}
                  onChange={(e) =>
                    setRouteForm((f) => ({ ...f, businessLineId: e.target.value }))
                  }
                  disabled={!!editingRoute}
                >
                  <option value="">默认路由（未命中精确匹配时回退）</option>
                  {(businessLines ?? []).map((bl) => (
                    <option key={bl.businessLineId} value={bl.businessLineId}>
                      {bl.businessLineName}
                    </option>
                  ))}
                </select>
                {editingRoute && (
                  <div
                    style={{
                      fontSize: 11,
                      color: 'var(--text-muted)',
                      marginTop: 4,
                    }}
                  >
                    业务线创建后不可修改
                  </div>
                )}
              </div>
              <div className="form-group">
                <label className="label">
                  审批人<span className="required">*</span>
                </label>
                <select
                  className="select"
                  value={routeForm.approverUserId}
                  onChange={(e) =>
                    setRouteForm((f) => ({ ...f, approverUserId: e.target.value }))
                  }
                >
                  <option value="">请选择审批人</option>
                  {approverOptions.map((u) => (
                    <option key={u.userId} value={u.userId}>
                      {u.realName}（{u.username}）
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label className="label">审批人所属部门 ID</label>
                <input
                  className="input"
                  placeholder="可选，留空表示不指定"
                  value={routeForm.departmentId}
                  onChange={(e) =>
                    setRouteForm((f) => ({ ...f, departmentId: e.target.value }))
                  }
                />
              </div>
              <div className="form-group">
                <label className="label">描述说明</label>
                <textarea
                  className="input"
                  rows={2}
                  placeholder="可选"
                  value={routeForm.description}
                  onChange={(e) =>
                    setRouteForm((f) => ({ ...f, description: e.target.value }))
                  }
                />
              </div>
              <div className="form-group">
                <label className="label">状态</label>
                <label className="switch" style={{ display: 'inline-flex' }}>
                  <input
                    type="checkbox"
                    checked={routeForm.enabled === 1}
                    onChange={(e) =>
                      setRouteForm((f) => ({
                        ...f,
                        enabled: e.target.checked ? 1 : 0,
                      }))
                    }
                  />
                  <span className="switch-slider"></span>
                  <span style={{ marginLeft: 8, fontSize: 13 }}>
                    {routeForm.enabled === 1 ? '启用' : '禁用'}
                  </span>
                </label>
              </div>
              {routeFormError && (
                <div className="alert-danger" style={{ marginBottom: 12 }}>
                  <span>{routeFormError}</span>
                </div>
              )}
            </div>
            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setRouteModalOpen(false)}
                disabled={isRouteSubmitting}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSubmitRoute}
                disabled={isRouteSubmitting}
              >
                {isRouteSubmitting ? '保存中…' : '保存'}
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
// P2 SEC-1 安全策略 · 密码策略配置
// ============================================================

/** 密码策略本地编辑态（同步自后端 PasswordPolicyVO） */
type PasswordPolicyForm = {
  minLength: number
  requireUppercase: number
  requireLowercase: number
  requireDigit: number
  requireSpecial: number
  specialChars: string
  expireDays: number
  historyCount: number
  enabled: number
}

/** 密码策略区块（P2 SEC-1） */
function SecurityPolicySection() {
  const queryClient = useQueryClient()

  // 1. 查询当前密码策略
  const { data: policy, isLoading } = useQuery<
    PasswordPolicyVO | null,
    ApiError
  >({
    queryKey: ['settings', 'password-policy'],
    queryFn: settingsApi.getPasswordPolicy,
  })

  // 2. 本地编辑态
  const [form, setForm] = useState<PasswordPolicyForm>({
    minLength: 8,
    requireUppercase: 1,
    requireLowercase: 1,
    requireDigit: 1,
    requireSpecial: 1,
    specialChars: '!@#$%^&*()_+-=[]{}|;:,.<>?',
    expireDays: 90,
    historyCount: 5,
    enabled: 1,
  })
  const [formError, setFormError] = useState('')

  // 3. 同步后端数据到本地编辑态（仅首次加载时同步）
  useEffect(() => {
    if (policy) {
      setForm({
        minLength: policy.minLength,
        requireUppercase: policy.requireUppercase,
        requireLowercase: policy.requireLowercase,
        requireDigit: policy.requireDigit,
        requireSpecial: policy.requireSpecial,
        specialChars: policy.specialChars,
        expireDays: policy.expireDays,
        historyCount: policy.historyCount,
        enabled: policy.enabled,
      })
    }
  }, [policy])

  // 4. 更新 mutation
  const updateMutation = useMutation<
    PasswordPolicyVO,
    ApiError,
    PasswordPolicyUpdateRequest
  >({
    mutationFn: settingsApi.updatePasswordPolicy,
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['settings', 'password-policy'],
      })
      setFormError('')
    },
    onError: (err) => setFormError(err.message),
  })

  // 5. 保存密码策略
  const handleSave = () => {
    setFormError('')
    if (form.minLength < 4 || form.minLength > 64) {
      setFormError('最小长度应在 4-64 之间')
      return
    }
    if (form.expireDays < 0 || form.expireDays > 365) {
      setFormError('过期天数应在 0-365 之间（0 表示不过期）')
      return
    }
    if (form.historyCount < 0 || form.historyCount > 24) {
      setFormError('历史密码检查数量应在 0-24 之间（0 表示不检查）')
      return
    }
    if (
      form.requireSpecial === 1 &&
      !form.specialChars.trim()
    ) {
      setFormError('启用特殊字符要求时，特殊字符集合不能为空')
      return
    }
    updateMutation.mutate({
      minLength: form.minLength,
      requireUppercase: form.requireUppercase,
      requireLowercase: form.requireLowercase,
      requireDigit: form.requireDigit,
      requireSpecial: form.requireSpecial,
      specialChars: form.specialChars,
      expireDays: form.expireDays,
      historyCount: form.historyCount,
      enabled: form.enabled,
    })
  }

  // 6. 切换策略启停
  const handleToggleEnabled = (checked: boolean) => {
    setForm((f) => ({ ...f, enabled: checked ? 1 : 0 }))
  }

  // 7. 切换字符要求开关
  const handleToggleRequire = (
    key: 'requireUppercase' | 'requireLowercase' | 'requireDigit' | 'requireSpecial',
    checked: boolean,
  ) => {
    setForm((f) => ({ ...f, [key]: checked ? 1 : 0 }))
  }

  return (
    <div className="settings-section">
      <div className="section-title">安全策略 · 密码策略</div>

      {isLoading && (
        <div className="glass-card-static" style={{ padding: 16, color: 'var(--text-muted)' }}>
          加载中…
        </div>
      )}

      {!isLoading && (
        <div className="glass-card-static password-policy-card">
          {/* region 策略启停 */}
          <div className="password-policy-header">
            <div>
              <div className="sub-block-title" style={{ marginBottom: 4 }}>
                密码策略总开关
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                关闭后，新增 / 重置密码时不校验强度、过期与历史密码
              </div>
            </div>
            <label className="switch" style={{ display: 'inline-flex' }}>
              <input
                type="checkbox"
                checked={form.enabled === 1}
                onChange={(e) => handleToggleEnabled(e.target.checked)}
              />
              <span className="switch-slider"></span>
              <span style={{ marginLeft: 8, fontSize: 13 }}>
                {form.enabled === 1 ? '启用' : '禁用'}
              </span>
            </label>
          </div>
          {/* endregion */}

          {/* region 密码强度规则 */}
          <div className="sub-block-title" style={{ marginTop: 'var(--space-md)' }}>
            密码强度规则
          </div>
          <div className="password-policy-grid">
            <div className="form-group">
              <label className="label">最小密码长度</label>
              <input
                className="input"
                type="number"
                min={4}
                max={64}
                value={form.minLength}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    minLength: Number(e.target.value),
                  }))
                }
                disabled={form.enabled === 0}
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                范围 4-64
              </div>
            </div>
            <label className="check-item">
              <input
                type="checkbox"
                checked={form.requireUppercase === 1}
                onChange={(e) =>
                  handleToggleRequire('requireUppercase', e.target.checked)
                }
                disabled={form.enabled === 0}
              />
              <div className="check-label">
                <div>必须包含大写字母</div>
                <div className="check-desc">A-Z</div>
              </div>
            </label>
            <label className="check-item">
              <input
                type="checkbox"
                checked={form.requireLowercase === 1}
                onChange={(e) =>
                  handleToggleRequire('requireLowercase', e.target.checked)
                }
                disabled={form.enabled === 0}
              />
              <div className="check-label">
                <div>必须包含小写字母</div>
                <div className="check-desc">a-z</div>
              </div>
            </label>
            <label className="check-item">
              <input
                type="checkbox"
                checked={form.requireDigit === 1}
                onChange={(e) =>
                  handleToggleRequire('requireDigit', e.target.checked)
                }
                disabled={form.enabled === 0}
              />
              <div className="check-label">
                <div>必须包含数字</div>
                <div className="check-desc">0-9</div>
              </div>
            </label>
            <label className="check-item">
              <input
                type="checkbox"
                checked={form.requireSpecial === 1}
                onChange={(e) =>
                  handleToggleRequire('requireSpecial', e.target.checked)
                }
                disabled={form.enabled === 0}
              />
              <div className="check-label">
                <div>必须包含特殊字符</div>
                <div className="check-desc">从下方字符集合中取</div>
              </div>
            </label>
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="label">特殊字符集合</label>
              <input
                className="input mono"
                value={form.specialChars}
                onChange={(e) =>
                  setForm((f) => ({ ...f, specialChars: e.target.value }))
                }
                disabled={form.enabled === 0 || form.requireSpecial === 0}
                placeholder="!@#$%^&*()_+-=[]{}|;:,.<>?"
              />
            </div>
          </div>
          {/* endregion */}

          {/* region 密码过期 & 历史密码 */}
          <div className="sub-block-title" style={{ marginTop: 'var(--space-md)' }}>
            密码过期与历史检查
          </div>
          <div className="password-policy-grid">
            <div className="form-group">
              <label className="label">密码过期天数</label>
              <input
                className="input"
                type="number"
                min={0}
                max={365}
                value={form.expireDays}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    expireDays: Number(e.target.value),
                  }))
                }
                disabled={form.enabled === 0}
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                0 表示不过期；超过天数后登录时强制改密
              </div>
            </div>
            <div className="form-group">
              <label className="label">历史密码检查数量</label>
              <input
                className="input"
                type="number"
                min={0}
                max={24}
                value={form.historyCount}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    historyCount: Number(e.target.value),
                  }))
                }
                disabled={form.enabled === 0}
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                0 表示不检查；改密时禁止复用最近 N 次密码
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region 错误提示 */}
          {formError && (
            <div
              className="alert-danger"
              style={{ marginTop: 'var(--space-md)' }}
            >
              <span>{formError}</span>
            </div>
          )}
          {updateMutation.isError && !formError && (
            <div
              className="alert-danger"
              style={{ marginTop: 'var(--space-md)' }}
            >
              <span>保存失败：{updateMutation.error?.message}</span>
            </div>
          )}
          {/* endregion */}

          {/* region 底部保存栏 */}
          <div className="notification-config-footer">
            <div className="notification-config-footer-hint">
              {policy?.updateTime
                ? `最后保存于 ${dayjs(policy.updateTime).format('YYYY-MM-DD HH:mm:ss')}`
                : '尚未保存'}
            </div>
            <div className="flex gap-sm">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => policy && setForm({
                  minLength: policy.minLength,
                  requireUppercase: policy.requireUppercase,
                  requireLowercase: policy.requireLowercase,
                  requireDigit: policy.requireDigit,
                  requireSpecial: policy.requireSpecial,
                  specialChars: policy.specialChars,
                  expireDays: policy.expireDays,
                  historyCount: policy.historyCount,
                  enabled: policy.enabled,
                })}
                disabled={updateMutation.isPending}
              >
                重置
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSave}
                disabled={updateMutation.isPending}
              >
                {updateMutation.isPending ? '保存中…' : '保存配置'}
              </button>
            </div>
          </div>
          {/* endregion */}
        </div>
      )}
    </div>
  )
}

// ============================================================
// P2 SEC-2 安全策略 · 登录安全策略
// ============================================================

/** 登录策略本地编辑态（同步自后端 LoginPolicyVO） */
type LoginPolicyForm = {
  maxLoginAttempts: number
  lockMinutes: number
  ipWhitelist: string
  ipBlacklist: string
  allowMultiLogin: number
  sessionTimeoutMinutes: number
  enabled: number
}

/** 登录安全策略区块（P2 SEC-2） */
function LoginPolicySection() {
  const queryClient = useQueryClient()

  // 1. 查询当前登录策略
  const { data: policy, isLoading } = useQuery<
    LoginPolicyVO | null,
    ApiError
  >({
    queryKey: ['settings', 'login-policy'],
    queryFn: settingsApi.getLoginPolicy,
  })

  // 2. 本地编辑态
  const [form, setForm] = useState<LoginPolicyForm>({
    maxLoginAttempts: 5,
    lockMinutes: 30,
    ipWhitelist: '',
    ipBlacklist: '',
    allowMultiLogin: 0,
    sessionTimeoutMinutes: 30,
    enabled: 1,
  })
  const [formError, setFormError] = useState('')

  // 3. 同步后端数据到本地编辑态（仅首次加载时同步）
  useEffect(() => {
    if (policy) {
      setForm({
        maxLoginAttempts: policy.maxLoginAttempts,
        lockMinutes: policy.lockMinutes,
        ipWhitelist: policy.ipWhitelist ?? '',
        ipBlacklist: policy.ipBlacklist ?? '',
        allowMultiLogin: policy.allowMultiLogin,
        sessionTimeoutMinutes: policy.sessionTimeoutMinutes,
        enabled: policy.enabled,
      })
    }
  }, [policy])

  // 4. 更新 mutation
  const updateMutation = useMutation<
    LoginPolicyVO,
    ApiError,
    LoginPolicyUpdateRequest
  >({
    mutationFn: settingsApi.updateLoginPolicy,
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['settings', 'login-policy'],
      })
      setFormError('')
    },
    onError: (err) => setFormError(err.message),
  })

  // 5. 保存登录策略
  const handleSave = () => {
    setFormError('')
    if (form.maxLoginAttempts < 1 || form.maxLoginAttempts > 20) {
      setFormError('最大登录失败次数应在 1-20 之间')
      return
    }
    if (form.lockMinutes < 1 || form.lockMinutes > 1440) {
      setFormError('账号锁定时长应在 1-1440 分钟之间')
      return
    }
    if (
      form.sessionTimeoutMinutes < 1 ||
      form.sessionTimeoutMinutes > 1440
    ) {
      setFormError('会话空闲超时应在 1-1440 分钟之间')
      return
    }
    updateMutation.mutate({
      maxLoginAttempts: form.maxLoginAttempts,
      lockMinutes: form.lockMinutes,
      ipWhitelist: form.ipWhitelist.trim() || undefined,
      ipBlacklist: form.ipBlacklist.trim() || undefined,
      allowMultiLogin: form.allowMultiLogin,
      sessionTimeoutMinutes: form.sessionTimeoutMinutes,
      enabled: form.enabled,
    })
  }

  return (
    <div className="settings-section">
      <div className="section-title">安全策略 · 登录安全</div>

      {isLoading && (
        <div className="glass-card-static" style={{ padding: 16, color: 'var(--text-muted)' }}>
          加载中…
        </div>
      )}

      {!isLoading && (
        <div className="glass-card-static login-policy-card">
          {/* region 策略启停 */}
          <div className="password-policy-header">
            <div>
              <div className="sub-block-title" style={{ marginBottom: 4 }}>
                登录策略总开关
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                关闭后，账号锁定 / IP 限制 / 并发登录限制均不生效
              </div>
            </div>
            <label className="switch" style={{ display: 'inline-flex' }}>
              <input
                type="checkbox"
                checked={form.enabled === 1}
                onChange={(e) =>
                  setForm((f) => ({ ...f, enabled: e.target.checked ? 1 : 0 }))
                }
              />
              <span className="switch-slider"></span>
              <span style={{ marginLeft: 8, fontSize: 13 }}>
                {form.enabled === 1 ? '启用' : '禁用'}
              </span>
            </label>
          </div>
          {/* endregion */}

          {/* region 账号锁定规则 */}
          <div className="sub-block-title" style={{ marginTop: 'var(--space-md)' }}>
            账号锁定规则
          </div>
          <div className="password-policy-grid">
            <div className="form-group">
              <label className="label">最大连续登录失败次数</label>
              <input
                className="input"
                type="number"
                min={1}
                max={20}
                value={form.maxLoginAttempts}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    maxLoginAttempts: Number(e.target.value),
                  }))
                }
                disabled={form.enabled === 0}
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                范围 1-20；达到阈值后自动锁定账号
              </div>
            </div>
            <div className="form-group">
              <label className="label">账号锁定时长（分钟）</label>
              <input
                className="input"
                type="number"
                min={1}
                max={1440}
                value={form.lockMinutes}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    lockMinutes: Number(e.target.value),
                  }))
                }
                disabled={form.enabled === 0}
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                范围 1-1440；到期后自动解锁
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region IP 限制 */}
          <div className="sub-block-title" style={{ marginTop: 'var(--space-md)' }}>
            IP 白 / 黑名单
          </div>
          <div className="password-policy-grid">
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="label">IP 白名单</label>
              <input
                className="input mono"
                value={form.ipWhitelist}
                onChange={(e) =>
                  setForm((f) => ({ ...f, ipWhitelist: e.target.value }))
                }
                disabled={form.enabled === 0}
                placeholder="192.168.1.1,10.0.0.0/24（逗号分隔，留空表示不限制）"
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                配置后只有白名单内 IP 可登录；同时配置时白名单优先
              </div>
            </div>
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="label">IP 黑名单</label>
              <input
                className="input mono"
                value={form.ipBlacklist}
                onChange={(e) =>
                  setForm((f) => ({ ...f, ipBlacklist: e.target.value }))
                }
                disabled={form.enabled === 0}
                placeholder="1.2.3.4,5.6.7.8（逗号分隔，命中即拒绝）"
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                黑名单内的 IP 一律拒绝登录
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region 会话策略 */}
          <div className="sub-block-title" style={{ marginTop: 'var(--space-md)' }}>
            会话与并发登录
          </div>
          <div className="password-policy-grid">
            <div className="form-group">
              <label className="label">会话空闲超时（分钟）</label>
              <input
                className="input"
                type="number"
                min={1}
                max={1440}
                value={form.sessionTimeoutMinutes}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    sessionTimeoutMinutes: Number(e.target.value),
                  }))
                }
                disabled={form.enabled === 0}
              />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                范围 1-1440；超过未操作自动下线（P2 SEC-3 会话管理使用）
              </div>
            </div>
            <label className="check-item">
              <input
                type="checkbox"
                checked={form.allowMultiLogin === 1}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    allowMultiLogin: e.target.checked ? 1 : 0,
                  }))
                }
                disabled={form.enabled === 0}
              />
              <div className="check-label">
                <div>允许多端并发登录</div>
                <div className="check-desc">
                  关闭后，新登录会踢掉同账号旧会话（P2 SEC-3 会话管理使用）
                </div>
              </div>
            </label>
          </div>
          {/* endregion */}

          {/* region 错误提示 */}
          {formError && (
            <div
              className="alert-danger"
              style={{ marginTop: 'var(--space-md)' }}
            >
              <span>{formError}</span>
            </div>
          )}
          {updateMutation.isError && !formError && (
            <div
              className="alert-danger"
              style={{ marginTop: 'var(--space-md)' }}
            >
              <span>保存失败：{updateMutation.error?.message}</span>
            </div>
          )}
          {/* endregion */}

          {/* region 底部保存栏 */}
          <div className="notification-config-footer">
            <div className="notification-config-footer-hint">
              {policy?.updateTime
                ? `最后保存于 ${dayjs(policy.updateTime).format('YYYY-MM-DD HH:mm:ss')}`
                : '尚未保存'}
            </div>
            <div className="flex gap-sm">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => policy && setForm({
                  maxLoginAttempts: policy.maxLoginAttempts,
                  lockMinutes: policy.lockMinutes,
                  ipWhitelist: policy.ipWhitelist ?? '',
                  ipBlacklist: policy.ipBlacklist ?? '',
                  allowMultiLogin: policy.allowMultiLogin,
                  sessionTimeoutMinutes: policy.sessionTimeoutMinutes,
                  enabled: policy.enabled,
                })}
                disabled={updateMutation.isPending}
              >
                重置
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSave}
                disabled={updateMutation.isPending}
              >
                {updateMutation.isPending ? '保存中…' : '保存配置'}
              </button>
            </div>
          </div>
          {/* endregion */}
        </div>
      )}
    </div>
  )
}

// ============================================================
// 在线会话管理区块（P2 SEC-3，查询 + 踢人下线）
// ============================================================

/** User-Agent → 设备类型简短标签（用于表格展示） */
function parseDevice(ua: string | undefined): string {
  if (!ua) return '未知设备'
  if (/iPhone|iPad|iPod/i.test(ua)) return 'iOS'
  if (/Android/i.test(ua)) return 'Android'
  if (/Macintosh|Mac OS X/i.test(ua)) return 'macOS'
  if (/Windows/i.test(ua)) return 'Windows'
  if (/Linux/i.test(ua)) return 'Linux'
  return '其他'
}

/** User-Agent → 浏览器简短标签 */
function parseBrowser(ua: string | undefined): string {
  if (!ua) return '未知'
  if (/Edg\//i.test(ua)) return 'Edge'
  if (/Chrome\//i.test(ua) && !/Chromium\//i.test(ua)) return 'Chrome'
  if (/Chromium\//i.test(ua)) return 'Chromium'
  if (/Safari\//i.test(ua) && !/Chrome\//i.test(ua)) return 'Safari'
  if (/Firefox\//i.test(ua)) return 'Firefox'
  return '其他'
}

/** 在线会话管理区块（P2 SEC-3） */
function SessionManagementSection() {
  const queryClient = useQueryClient()

  // 1. 查询参数（含分页 + 筛选）
  const [query, setQuery] = useState<SessionQueryRequest>({
    current: 1,
    pageSize: 10,
    username: '',
    userId: '',
  })

  // 2. 查询在线会话列表
  const { data, isLoading } = useQuery<
    IPage<SessionVO>,
    ApiError
  >({
    queryKey: ['settings', 'sessions', query],
    queryFn: () => settingsApi.listSessions(query),
  })

  // 3. 踢人 mutation
  const killMutation = useMutation<boolean, ApiError, string>({
    mutationFn: settingsApi.killSession,
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['settings', 'sessions'],
      })
    },
  })

  // 4. 踢人确认
  const handleKill = (sessionId: string, username: string) => {
    if (!window.confirm(`确认将会话 ${username}（${sessionId.slice(0, 8)}…）强制下线？`)) {
      return
    }
    killMutation.mutate(sessionId)
  }

  // 5. 切换页码
  const handlePageChange = (newCurrent: number) => {
    setQuery((prev) => ({ ...prev, current: newCurrent }))
  }

  // 6. 应用筛选
  const handleApplyFilter = () => {
    setQuery((prev) => ({ ...prev, current: 1 }))
  }

  // 7. 重置筛选
  const handleResetFilter = () => {
    setQuery({ current: 1, pageSize: 10, username: '', userId: '' })
  }

  const sessions = data?.records ?? []
  const total = data?.total ?? 0
  const current = data?.current ?? query.current ?? 1
  const pageSize = data?.size ?? query.pageSize ?? 10
  const totalPages = data?.pages ?? (Math.ceil(total / pageSize) || 1)

  return (
    <div className="settings-section">
      <div className="section-title">安全策略 · 在线会话</div>

      {isLoading && (
        <div className="glass-card-static" style={{ padding: 16, color: 'var(--text-muted)' }}>
          加载中…
        </div>
      )}

      {!isLoading && (
        <div className="glass-card-static session-management-card">
          {/* region 筛选栏 */}
          <div className="session-filter-bar">
            <div className="session-filter-item">
              <label>用户名</label>
              <input
                type="text"
                className="input"
                placeholder="模糊匹配"
                value={query.username ?? ''}
                onChange={(e) =>
                  setQuery((prev) => ({ ...prev, username: e.target.value }))
                }
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleApplyFilter()
                }}
              />
            </div>
            <div className="session-filter-item">
              <label>用户 ID</label>
              <input
                type="text"
                className="input"
                placeholder="精确匹配"
                value={query.userId ?? ''}
                onChange={(e) =>
                  setQuery((prev) => ({ ...prev, userId: e.target.value }))
                }
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleApplyFilter()
                }}
              />
            </div>
            <div className="flex gap-sm">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleResetFilter}
              >
                重置
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleApplyFilter}
              >
                查询
              </button>
            </div>
            <div className="session-count-badge">
              在线 {total} 个会话
            </div>
          </div>
          {/* endregion */}

          {/* region 会话表格 */}
          <div className="session-table-wrapper">
            <table className="session-table">
              <thead>
                <tr>
                  <th>用户名</th>
                  <th>用户 ID</th>
                  <th>登录 IP</th>
                  <th>设备 / 浏览器</th>
                  <th>登录时间</th>
                  <th>最后访问</th>
                  <th>过期时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {sessions.length === 0 && (
                  <tr>
                    <td colSpan={8} className="session-empty-row">
                      暂无在线会话
                    </td>
                  </tr>
                )}
                {sessions.map((s) => {
                  const isExpired =
                    new Date(s.expiresAt).getTime() < Date.now()
                  return (
                    <tr key={s.sessionId}>
                      <td className="session-username-cell">{s.username}</td>
                      <td className="session-userid-cell">{s.userId}</td>
                      <td>{s.loginIp ?? '-'}</td>
                      <td>
                        <span className="session-device-badge">
                          {parseDevice(s.userAgent)}
                        </span>
                        <span className="session-browser-text">
                          {parseBrowser(s.userAgent)}
                        </span>
                      </td>
                      <td>{dayjs(s.loginTime).format('YYYY-MM-DD HH:mm:ss')}</td>
                      <td>{dayjs(s.lastAccessTime).format('YYYY-MM-DD HH:mm:ss')}</td>
                      <td>
                        <span
                          className={`session-expires-text ${
                            isExpired ? 'expired' : ''
                          }`}
                        >
                          {dayjs(s.expiresAt).format('HH:mm:ss')}
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-danger btn-sm"
                          onClick={() => handleKill(s.sessionId, s.username)}
                          disabled={killMutation.isPending}
                        >
                          踢下线
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {/* endregion */}

          {/* region 分页 */}
          {totalPages > 1 && (
            <div className="session-pagination">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => handlePageChange(Math.max(1, current - 1))}
                disabled={current <= 1}
              >
                上一页
              </button>
              <span className="session-pagination-info">
                第 {current} / {totalPages} 页（共 {total} 条）
              </span>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => handlePageChange(Math.min(totalPages, current + 1))}
                disabled={current >= totalPages}
              >
                下一页
              </button>
            </div>
          )}
          {/* endregion */}
        </div>
      )}
    </div>
  )
}

// ============================================================
// P3 系统配置区块（AI 配置 / 存储配置 / 定时任务 / 系统参数 共用）
// 对齐后端 SystemConfigController：GET /system-config / PUT /system-config/{key} / POST /system-config/refresh
// ============================================================

/** 系统配置区块属性 */
interface SystemConfigSectionProps {
  /** 区块标题 */
  title: string
  /** 配置键前缀过滤；`__others__` 表示展示未命中已知前缀的所有配置 */
  prefix: string
}

/** 配置值类型 → 输入控件类型映射 */
const CONFIG_INPUT_TYPE: Record<string, string> = {
  STRING: 'text',
  INTEGER: 'number',
  BOOLEAN: 'checkbox',
}

/** 已知前缀（用于「系统参数」聚合其余项） */
const KNOWN_PREFIXES = ['ai.', 'minio.', 'scheduler.']

/**
 * 系统配置区块（可复用，按前缀过滤展示）
 *
 * 设计：一次加载全量 sys_config，前端按 prefix 过滤分组，减少后端多次请求；
 * 编辑按 config_key 单项保存，保存后触发本地缓存刷新。
 */
function SystemConfigSection({ title, prefix }: SystemConfigSectionProps) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Record<string, string>>({})
  const [savingKey, setSavingKey] = useState<string | null>(null)

  // 1. 加载全部系统配置
  const { data: allConfigs, isLoading } = useQuery({
    queryKey: ['system-config', 'all'],
    queryFn: () => settingsApi.listSystemConfigs(),
  })

  // 2. 按前缀过滤
  const configs = (allConfigs ?? []).filter((c) => {
    if (prefix === '__others__') {
      return !KNOWN_PREFIXES.some((p) => c.configKey.startsWith(p))
    }
    return c.configKey.startsWith(prefix)
  })

  // 3. 进入编辑态
  const startEdit = (key: string, value: string) => {
    setEditing((prev) => ({ ...prev, [key]: value }))
  }
  const cancelEdit = (key: string) => {
    setEditing((prev) => {
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  // 4. 保存单项配置
  const updateMutation = useMutation({
    mutationFn: ({
      key,
      body,
    }: {
      key: string
      body: SystemConfigUpdateRequest
    }) => settingsApi.updateSystemConfig(key, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['system-config', 'all'] })
    },
  })

  // 5. 刷新缓存（重建 AI / MinIO 配置属性）
  const refreshMutation = useMutation({
    mutationFn: () => settingsApi.refreshSystemConfig(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['system-config', 'all'] })
    },
  })

  const handleSave = (cfg: SystemConfigVO) => {
    const value = editing[cfg.configKey] ?? ''
    setSavingKey(cfg.configKey)
    updateMutation.mutate(
      { key: cfg.configKey, body: { configValue: value } },
      {
        onSuccess: () => {
          cancelEdit(cfg.configKey)
          setSavingKey(null)
        },
        onError: () => setSavingKey(null),
      },
    )
  }

  const handleToggleBoolean = (cfg: SystemConfigVO) => {
    const next = cfg.configValue === 'true' ? 'false' : 'true'
    setSavingKey(cfg.configKey)
    updateMutation.mutate(
      { key: cfg.configKey, body: { configValue: next } },
      { onSettled: () => setSavingKey(null) },
    )
  }

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <h2 className="section-title">{title}</h2>
        <button
          type="button"
          className="btn-test"
          onClick={() => refreshMutation.mutate()}
          disabled={refreshMutation.isPending}
        >
          {refreshMutation.isPending ? '刷新中…' : '刷新缓存热生效'}
        </button>
      </div>
      <div className="glass-card-static settings-config-list">
        {isLoading && <div className="settings-empty">加载中…</div>}
        {!isLoading && configs.length === 0 && (
          <div className="settings-empty">暂无可配置项</div>
        )}
        {configs.map((cfg) => {
          const isBool = cfg.configType === 'BOOLEAN'
          const isEditing = editing[cfg.configKey] !== undefined
          const inputType = CONFIG_INPUT_TYPE[cfg.configType] ?? 'text'
          return (
            <div className="settings-config-item" key={cfg.configKey}>
              <div className="settings-config-meta">
                <div className="settings-config-key">{cfg.configKey}</div>
                <div className="settings-config-desc">
                  {cfg.description ?? '—'}
                </div>
                <div className="settings-config-time">
                  更新于 {cfg.updateTime ? dayjs(cfg.updateTime).format('YYYY-MM-DD HH:mm') : '—'}
                </div>
              </div>
              <div className="settings-config-control">
                {isBool ? (
                  <label className="switch">
                    <input
                      type="checkbox"
                      checked={cfg.configValue === 'true'}
                      onChange={() => handleToggleBoolean(cfg)}
                      disabled={savingKey === cfg.configKey}
                    />
                    <span className="switch-slider" />
                  </label>
                ) : isEditing ? (
                  <div className="settings-config-edit">
                    <input
                      type={inputType}
                      className="settings-config-input"
                      value={editing[cfg.configKey]}
                      onChange={(e) =>
                        setEditing((prev) => ({
                          ...prev,
                          [cfg.configKey]: e.target.value,
                        }))
                      }
                    />
                    <button
                      type="button"
                      className="btn-link"
                      onClick={() => handleSave(cfg)}
                      disabled={savingKey === cfg.configKey}
                    >
                      {savingKey === cfg.configKey ? '保存中…' : '保存'}
                    </button>
                    <button
                      type="button"
                      className="btn-link"
                      onClick={() => cancelEdit(cfg.configKey)}
                    >
                      取消
                    </button>
                  </div>
                ) : (
                  <div className="settings-config-view">
                    <span className="settings-config-value">
                      {cfg.configValue}
                    </span>
                    <button
                      type="button"
                      className="btn-link"
                      onClick={() => startEdit(cfg.configKey, cfg.configValue)}
                    >
                      编辑
                    </button>
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
      {(updateMutation.isError || refreshMutation.isError) && (
        <div className="settings-config-error">
          操作失败：{(updateMutation.error as ApiError)?.message || (refreshMutation.error as ApiError)?.message}
        </div>
      )}
    </div>
  )
}

// ============================================================
// P3 权限矩阵区块（对齐 PermissionController）
// GET /permissions（列定义）+ GET /permissions/matrix（行 + 已勾选）+ PUT /permissions/roles/{roleId}
// ============================================================

/**
 * 权限矩阵区块
 *
 * 设计：行=角色，列=权限点；勾选状态本地维护，按行保存（全量替换语义）。
 * 内置角色可查看可编辑，但保存前会二次确认。
 */
function PermissionMatrixSection() {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<Record<string, Set<string>>>({})

  // 1. 加载权限点（列定义）
  const { data: permissions, isLoading: permLoading } = useQuery({
    queryKey: ['permissions', 'all'],
    queryFn: () => settingsApi.listAllPermissions(),
  })

  // 2. 加载角色权限矩阵（行 + 已勾选）
  const { data: matrix, isLoading: matrixLoading } = useQuery({
    queryKey: ['permissions', 'matrix'],
    queryFn: () => settingsApi.getPermissionMatrix(),
  })

  // 3. 初始化草稿（矩阵加载完成后）
  useEffect(() => {
    if (matrix) {
      const init: Record<string, Set<string>> = {}
      matrix.forEach((row) => {
        init[row.roleId] = new Set(row.permissionIds)
      })
      setDraft(init)
    }
  }, [matrix])

  const toggle = (roleId: string, permId: string) => {
    setDraft((prev) => {
      const next = { ...prev }
      const set = new Set(next[roleId] ?? [])
      if (set.has(permId)) {
        set.delete(permId)
      } else {
        set.add(permId)
      }
      next[roleId] = set
      return next
    })
  }

  // 4. 保存某角色权限
  const saveMutation = useMutation({
    mutationFn: ({
      roleId,
      permissionIds,
    }: {
      roleId: string
      permissionIds: string[]
    }) =>
      settingsApi.saveRolePermissions(roleId, {
        permissionIds,
      } as RolePermissionSaveRequest),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permissions', 'matrix'] })
    },
  })

  const handleSaveRow = (row: RolePermissionMatrixVO) => {
    const ids = Array.from(draft[row.roleId] ?? [])
    saveMutation.mutate({ roleId: row.roleId, permissionIds: ids })
  }

  const isDirty = (roleId: string) => {
    const original = matrix?.find((r) => r.roleId === roleId)
    if (!original) return false
    const orig = new Set(original.permissionIds)
    const cur = draft[roleId] ?? new Set()
    if (orig.size !== cur.size) return true
    for (const id of cur) {
      if (!orig.has(id)) return true
    }
    return false
  }

  const perms = permissions ?? []
  const rows = matrix ?? []
  const loading = permLoading || matrixLoading

  return (
    <div className="settings-section">
      <div className="settings-section-header">
        <h2 className="section-title">权限矩阵</h2>
        <span className="settings-config-desc">
          勾选 = 授予该角色对应权限，按行保存（全量替换）
        </span>
      </div>
      <div className="glass-card-static permission-matrix-wrap">
        {loading && <div className="settings-empty">加载中…</div>}
        {!loading && perms.length === 0 && (
          <div className="settings-empty">暂无权限点</div>
        )}
        {!loading && perms.length > 0 && (
          <div className="permission-matrix-scroll">
            <table className="permission-matrix-table">
              <thead>
                <tr>
                  <th className="pm-role-col">角色 / 权限</th>
                  {perms.map((p) => (
                    <th key={p.permissionId} title={p.permissionCode}>
                      <div className="pm-perm-name">{p.permissionName}</div>
                      <div className="pm-perm-code">{p.permissionCode}</div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const checked = draft[row.roleId] ?? new Set<string>()
                  const dirty = isDirty(row.roleId)
                  return (
                    <tr key={row.roleId}>
                      <td className="pm-role-cell">
                        <div className="pm-role-name">{row.roleName}</div>
                        <div className="pm-role-code">{row.roleCode}</div>
                        {row.builtIn && (
                          <span className="pm-builtin-badge">内置</span>
                        )}
                      </td>
                      {perms.map((p) => (
                        <td key={p.permissionId} className="pm-check-cell">
                          <input
                            type="checkbox"
                            checked={checked.has(p.permissionId)}
                            onChange={() => toggle(row.roleId, p.permissionId)}
                          />
                        </td>
                      ))}
                      <td className="pm-action-cell">
                        <button
                          type="button"
                          className="btn-link"
                          onClick={() => handleSaveRow(row)}
                          disabled={!dirty || saveMutation.isPending}
                        >
                          {saveMutation.isPending ? '保存中…' : '保存'}
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {saveMutation.isError && (
          <div className="settings-config-error">
            保存失败：{(saveMutation.error as ApiError)?.message}
          </div>
        )}
      </div>
    </div>
  )
}

// ============================================================
// 系统健康检查区块（P2 OPS-1，一键检测 DB / Redis / Python AI / MinIO）
// ============================================================

/** 健康状态 → 状态徽章 CSS class */
function healthStatusClass(status: string | undefined): string {
  if (status === 'UP') return 'health-up'
  if (status === 'DOWN') return 'health-down'
  return 'health-unknown'
}

/** 健康状态 → 中文展示 */
function healthStatusText(status: string | undefined): string {
  if (status === 'UP') return '正常'
  if (status === 'DOWN') return '异常'
  if (status === 'DEGRADED') return '降级'
  return '未知'
}

/** 整体状态 → CSS class */
function overallStatusClass(status: string | undefined): string {
  if (status === 'UP') return 'health-overall-up'
  if (status === 'DEGRADED') return 'health-overall-degraded'
  if (status === 'DOWN') return 'health-overall-down'
  return 'health-overall-unknown'
}

/** 系统健康检查区块（P2 OPS-1） */
function SystemHealthSection() {
  // 1. 查询状态（enabled: false 不自动查询，手动触发）
  const { data, isLoading, refetch, isFetching } = useQuery<
    SystemHealthVO,
    ApiError
  >({
    queryKey: ['settings', 'system-health'],
    queryFn: settingsApi.checkSystemHealth,
    enabled: false,
  })

  // 2. 一键检测
  const handleCheck = () => {
    refetch()
  }

  // 3. 组件列表
  const components = data?.components ?? []
  const hasResult = !!data

  return (
    <div className="settings-section">
      <div className="section-title">安全策略 · 系统健康</div>

      <div className="glass-card-static system-health-card">
        {/* region 操作栏 */}
        <div className="system-health-toolbar">
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={handleCheck}
            disabled={isFetching}
          >
            {isFetching ? '检测中…' : '一键检测'}
          </button>
          {hasResult && (
            <>
              <div
                className={`system-health-overall ${overallStatusClass(data?.overallStatus)}`}
              >
                整体状态：{healthStatusText(data?.overallStatus)}
              </div>
              <div className="system-health-meta">
                检查时间：
                {dayjs(data?.checkedAt).format('YYYY-MM-DD HH:mm:ss')}
                · 耗时 {data?.durationMs}ms
              </div>
            </>
          )}
        </div>
        {/* endregion */}

        {/* region 结果展示 */}
        {!hasResult && !isLoading && (
          <div className="system-health-placeholder">
            点击「一键检测」检查 DB / Redis / Python AI / MinIO 连通性
          </div>
        )}

        {hasResult && (
          <div className="system-health-grid">
            {components.map((c) => (
              <div key={c.name} className="system-health-item">
                <div className="system-health-item-header">
                  <span className="system-health-item-name">{c.displayName}</span>
                  <span
                    className={`system-health-item-status ${healthStatusClass(c.status)}`}
                  >
                    {healthStatusText(c.status)}
                  </span>
                </div>
                <div className="system-health-item-detail">
                  {c.status === 'UP' ? (
                    <>
                      <span className="system-health-item-latency">
                        {c.latencyMs}ms
                      </span>
                      {c.detail && (
                        <span className="system-health-item-info">{c.detail}</span>
                      )}
                    </>
                  ) : (
                    <span className="system-health-item-error">
                      {c.errorMessage}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
        {/* endregion */}
      </div>
    </div>
  )
}

export default Settings

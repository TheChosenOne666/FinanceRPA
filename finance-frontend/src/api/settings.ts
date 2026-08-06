/**
 * 系统设置 API 封装（P4 settings 原型对齐 + P0/P1 功能扩展）
 *
 * 对齐后端：
 * - 用户管理（P1 USR-1）：UserController(@RequestMapping("/users"))
 *   GET /users（分页）/ GET /users/{id} / POST /users / PUT /users / PUT /users/{id}/status
 *   / PUT /users/reset-password / DELETE /users/{id} / POST /users/roles
 * - 角色管理（P1 USR-2）：RoleController(@RequestMapping("/roles"))
 *   GET /roles（分页）/ GET /roles/all / GET /roles/{id} / POST /roles / PUT /roles
 *   / PUT /roles/{id}/status / DELETE /roles/{id}
 * - 风险关键词库（P0-1）：复用已有 RiskKeywordController
 * - Skill 元数据（P0-2）：复用已有 SkillController
 * - 部门 / 业务线（P0-3）：复用已有 TenantController
 * - 通知通道（P0-4）：复用 GET /notification/channels；新增 PUT /notification/channels/{channel} 保存 Webhook
 * - 通知模板：后端暂无对应实体，由 Mock 端提供数据 + Mock 持久化启停
 * - 审批超时阈值（P1 RSK-1）：ApprovalTimeoutConfigController(@RequestMapping("/approval-timeout"))
 *   GET /approval-timeout / PUT /approval-timeout/{riskLevel}
 * - 审批人映射（P1 RSK-3）：ApprovalRouteConfigController(@RequestMapping("/approval-routes"))
 *   GET /approval-routes（分页）/ POST /approval-routes / PUT /approval-routes/{id} / DELETE /approval-routes/{id}
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  ApprovalRouteConfigAddRequest,
  ApprovalRouteConfigQueryRequest,
  ApprovalRouteConfigUpdateRequest,
  ApprovalRouteConfigVO,
  ApprovalTimeoutConfigUpdateRequest,
  ApprovalTimeoutConfigVO,
  BaseResponse,
  ChannelConfigSaveRequest,
  ChannelVO,
  IPage,
  LoginPolicyUpdateRequest,
  LoginPolicyVO,
  NotificationConfigSaveRequest,
  NotificationTemplateConfigVO,
  PasswordPolicyUpdateRequest,
  PasswordPolicyVO,
  PasswordResetRequest,
  PermissionVO,
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
  UserRoleAssignRequest,
  UserQueryRequest,
  UserUpdateRequest,
  UserVO,
} from './types'

// ============================================================
// P1 USR-1 用户管理（对齐 UserController）
// ============================================================

/**
 * 分页查询用户列表
 *
 * @param query 查询请求（关键词 / 状态 / 分页）
 * @returns 用户分页列表
 */
export async function listUsers(
  query: UserQueryRequest,
): Promise<IPage<UserVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<UserVO>>>('/users', {
    params: query,
  })
  return res.data.data
}

/**
 * 查询用户详情
 *
 * @param userId 用户业务 ID
 * @returns 用户视图对象
 */
export async function getUser(userId: string): Promise<UserVO> {
  const res = await axiosClient.get<BaseResponse<UserVO>>(`/users/${userId}`)
  return res.data.data
}

/**
 * 新增用户（用户名 + 真实姓名必填；密码可省略，默认 Finrpa@2026）
 *
 * @param body 新增请求
 * @returns 新建用户业务 ID
 */
export async function addUser(body: UserAddRequest): Promise<string> {
  const res = await axiosClient.post<BaseResponse<string>>('/users', body)
  return res.data.data
}

/**
 * 编辑用户（用户名不可改）
 *
 * @param body 编辑请求
 * @returns 操作结果
 */
export async function updateUser(body: UserUpdateRequest): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>('/users', body)
  return res.data.data
}

/**
 * 启停用户
 *
 * @param userId 用户业务 ID
 * @param status 目标状态（0-禁用 1-启用）
 * @returns 操作结果
 */
export async function toggleUserStatus(
  userId: string,
  status: number,
): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>(
    `/users/${userId}/status`,
    null,
    { params: { status } },
  )
  return res.data.data
}

/**
 * 重置密码（不传 newPassword 时使用默认密码 Finrpa@2026）
 *
 * @param body 重置请求
 * @returns 操作结果
 */
export async function resetPassword(
  body: PasswordResetRequest,
): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>(
    '/users/reset-password',
    body,
  )
  return res.data.data
}

/**
 * 逻辑删除用户（同时清理用户-角色关联）
 *
 * @param userId 用户业务 ID
 * @returns 操作结果
 */
export async function deleteUser(userId: string): Promise<boolean> {
  const res = await axiosClient.delete<BaseResponse<boolean>>(
    `/users/${userId}`,
  )
  return res.data.data
}

/**
 * 分配角色（三维度 RBAC，全量替换语义）
 *
 * @param body 分配请求
 * @returns 操作结果
 */
export async function assignUserRoles(
  body: UserRoleAssignRequest,
): Promise<boolean> {
  const res = await axiosClient.post<BaseResponse<boolean>>(
    '/users/roles',
    body,
  )
  return res.data.data
}

// ============================================================
// P1 USR-2 角色管理（对齐 RoleController）
// ============================================================

/**
 * 分页查询角色列表
 *
 * @param query 查询请求（关键词 / 状态 / 分页）
 * @returns 角色分页列表
 */
export async function listRoles(
  query: RoleQueryRequest,
): Promise<IPage<RoleVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<RoleVO>>>('/roles', {
    params: query,
  })
  return res.data.data
}

/**
 * 查询全部启用角色（不分页，用于分配角色下拉选项）
 *
 * @returns 角色列表
 */
export async function listAllRoles(): Promise<RoleVO[]> {
  const res = await axiosClient.get<BaseResponse<RoleVO[]>>('/roles/all')
  return res.data.data
}

/**
 * 查询角色详情
 *
 * @param roleId 角色业务 ID
 * @returns 角色 VO
 */
export async function getRole(roleId: string): Promise<RoleVO> {
  const res = await axiosClient.get<BaseResponse<RoleVO>>(`/roles/${roleId}`)
  return res.data.data
}

/**
 * 新增角色（内置角色编码 super_admin / org_admin / operator / approver / viewer 受保护，禁止新增）
 *
 * @param body 新增请求
 * @returns 新建角色业务 ID
 */
export async function addRole(body: RoleAddRequest): Promise<string> {
  const res = await axiosClient.post<BaseResponse<string>>('/roles', body)
  return res.data.data
}

/**
 * 编辑角色（roleCode 不可改；内置角色仅可改状态/描述）
 *
 * @param body 编辑请求
 * @returns 操作结果
 */
export async function updateRole(body: RoleUpdateRequest): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>('/roles', body)
  return res.data.data
}

/**
 * 启停角色（super_admin / org_admin 内置角色禁止禁用）
 *
 * @param roleId 角色业务 ID
 * @param status 目标状态（0-禁用 1-启用）
 * @returns 操作结果
 */
export async function toggleRoleStatus(
  roleId: string,
  status: number,
): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>(
    `/roles/${roleId}/status`,
    null,
    { params: { status } },
  )
  return res.data.data
}

/**
 * 逻辑删除角色（内置角色 + 有用户关联的角色禁止删除）
 *
 * @param roleId 角色业务 ID
 * @returns 操作结果
 */
export async function deleteRole(roleId: string): Promise<boolean> {
  const res = await axiosClient.delete<BaseResponse<boolean>>(
    `/roles/${roleId}`,
  )
  return res.data.data
}

// ============================================================
// P1 RSK-1 审批超时阈值配置（对齐 ApprovalTimeoutConfigController）
// ============================================================

/**
 * 查询全部审批超时配置（按风险等级返回 high / critical 两条）
 *
 * @returns 超时配置列表
 */
export async function listApprovalTimeoutConfigs(): Promise<
  ApprovalTimeoutConfigVO[]
> {
  const res = await axiosClient.get<
    BaseResponse<ApprovalTimeoutConfigVO[]>
  >('/approval-timeout')
  return res.data.data
}

/**
 * 更新指定风险等级的审批超时配置
 *
 * @param riskLevel 风险等级（high / critical）
 * @param body 更新请求
 * @returns 更新后的配置 VO
 */
export async function updateApprovalTimeoutConfig(
  riskLevel: string,
  body: ApprovalTimeoutConfigUpdateRequest,
): Promise<ApprovalTimeoutConfigVO> {
  const res = await axiosClient.put<
    BaseResponse<ApprovalTimeoutConfigVO>
  >(`/approval-timeout/${riskLevel}`, body)
  return res.data.data
}

// ============================================================
// P1 RSK-3 审批人映射配置（对齐 ApprovalRouteConfigController）
// ============================================================

/**
 * 分页查询审批人映射列表
 *
 * @param query 查询请求（风险等级 / 业务线 / 启用状态 / 分页）
 * @returns 审批人映射分页列表
 */
export async function listApprovalRouteConfigs(
  query: ApprovalRouteConfigQueryRequest,
): Promise<IPage<ApprovalRouteConfigVO>> {
  const res = await axiosClient.get<
    BaseResponse<IPage<ApprovalRouteConfigVO>>
  >('/approval-routes', { params: query })
  return res.data.data
}

/**
 * 新增审批人映射（风险等级 × 业务线 → 审批人）
 *
 * @param body 新增请求
 * @returns 新建的配置业务 ID
 */
export async function addApprovalRouteConfig(
  body: ApprovalRouteConfigAddRequest,
): Promise<string> {
  const res = await axiosClient.post<BaseResponse<string>>(
    '/approval-routes',
    body,
  )
  return res.data.data
}

/**
 * 更新审批人映射配置
 *
 * @param configId 配置业务 ID
 * @param body 更新请求
 * @returns 操作结果
 */
export async function updateApprovalRouteConfig(
  configId: string,
  body: ApprovalRouteConfigUpdateRequest,
): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>(
    `/approval-routes/${configId}`,
    body,
  )
  return res.data.data
}

/**
 * 删除审批人映射配置
 *
 * @param configId 配置业务 ID
 * @returns 操作结果
 */
export async function deleteApprovalRouteConfig(
  configId: string,
): Promise<boolean> {
  const res = await axiosClient.delete<BaseResponse<boolean>>(
    `/approval-routes/${configId}`,
  )
  return res.data.data
}

// ============================================================
// 通知通道 / 模板（复用现有 notification API，Mock 端提供模板数据）
// ============================================================

/**
 * 查询通知通道列表（含 Webhook URL 与启用状态）
 *
 * 说明：复用 GET /api/notification/channels 端点，
 * Mock 端在 ChannelVO 基础上补充 webhookUrl / enabled 字段。
 *
 * @returns 通道列表
 */
export async function listNotificationChannels(): Promise<ChannelVO[]> {
  const res = await axiosClient.get<BaseResponse<ChannelVO[]>>(
    '/notification/channels',
  )
  return res.data.data
}

/**
 * 查询通知模板配置列表
 *
 * @returns 模板配置列表
 */
export async function listNotificationTemplates(): Promise<
  NotificationTemplateConfigVO[]
> {
  const res = await axiosClient.get<BaseResponse<NotificationTemplateConfigVO[]>>(
    '/notification/templates',
  )
  return res.data.data
}

/**
 * 保存通知模板启停配置（通道开关已切至 PUT /channels/{channel}，此处仅保留模板启停 Mock 持久化）
 *
 * @param body 配置保存请求
 * @returns 最后保存时间（ISO 字符串）
 */
export async function saveNotificationConfig(
  body: NotificationConfigSaveRequest,
): Promise<string> {
  const res = await axiosClient.put<BaseResponse<string>>(
    '/notification/config',
    body,
  )
  return res.data.data
}

// ============================================================
// P0-1 风险关键词库（对齐 RiskKeywordController）
// ============================================================

/**
 * 分页查询风险关键词库
 *
 * @param query 查询请求（含分页 + 筛选参数）
 * @returns 关键词分页列表
 */
export async function listRiskKeywords(
  query: RiskKeywordQueryRequest,
): Promise<IPage<RiskKeywordVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<RiskKeywordVO>>>(
    '/risk-keywords',
    { params: query },
  )
  return res.data.data
}

/**
 * 新增自定义风险关键词
 *
 * @param body 新增请求
 * @returns 新增的关键词业务 ID
 */
export async function addRiskKeyword(
  body: RiskKeywordAddRequest,
): Promise<string> {
  const res = await axiosClient.post<BaseResponse<string>>('/risk-keywords', body)
  return res.data.data
}

/**
 * 更新风险关键词（内置关键词仅可改 enabled/description）
 *
 * @param keywordId 关键词业务 ID
 * @param body 更新请求
 * @returns 操作结果
 */
export async function updateRiskKeyword(
  keywordId: string,
  body: RiskKeywordAddRequest,
): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>(
    `/risk-keywords/${keywordId}`,
    body,
  )
  return res.data.data
}

/**
 * 删除风险关键词（内置不可删除）
 *
 * @param keywordId 关键词业务 ID
 * @returns 操作结果
 */
export async function deleteRiskKeyword(keywordId: string): Promise<boolean> {
  const res = await axiosClient.delete<BaseResponse<boolean>>(
    `/risk-keywords/${keywordId}`,
  )
  return res.data.data
}

// ============================================================
// P0-2 Skill 元数据管理（对齐 SkillController）
// ============================================================

/**
 * 分页查询 Skill 列表
 *
 * @param query 查询请求（含分类、启用状态、关键词）
 * @returns Skill 分页列表
 */
export async function listSkills(
  query: SkillQueryRequest,
): Promise<IPage<SkillVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<SkillVO>>>('/skills', {
    params: query,
  })
  return res.data.data
}

/**
 * 注册自定义 Skill（同步调 Python 校验 name 存在性）
 *
 * @param body 新增请求
 * @returns 新建的 Skill 视图对象
 */
export async function registerSkill(body: SkillAddRequest): Promise<SkillVO> {
  const res = await axiosClient.post<BaseResponse<SkillVO>>('/skills', body)
  return res.data.data
}

/**
 * 更新 Skill 元数据（不允许修改 name）
 *
 * @param name Skill 唯一标识
 * @param body 更新请求
 * @returns 操作结果
 */
export async function updateSkill(
  name: string,
  body: SkillUpdateRequest,
): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>(
    `/skills/${name}`,
    body,
  )
  return res.data.data
}

// ============================================================
// P0-4 通知通道 Webhook 配置保存（对齐 PUT /notification/channels/{channel}）
// ============================================================

/**
 * 保存通道 Webhook 配置（持久化 + 热生效）
 *
 * @param channel 通道类型：wecom / dingtalk
 * @param body 保存请求（webhookUrl / secret / enabled）
 * @returns 保存后的脱敏通道信息
 */
export async function saveChannelConfig(
  channel: string,
  body: ChannelConfigSaveRequest,
): Promise<ChannelVO> {
  const res = await axiosClient.put<BaseResponse<ChannelVO>>(
    `/notification/channels/${channel}`,
    body,
  )
  return res.data.data
}

// ============================================================
// P2 SEC-1 密码策略配置（对齐 PasswordPolicyController）
// ============================================================

/**
 * 查询当前密码策略
 *
 * @returns 密码策略 VO（策略被禁用时返回 null）
 */
export async function getPasswordPolicy(): Promise<PasswordPolicyVO | null> {
  const res = await axiosClient.get<BaseResponse<PasswordPolicyVO | null>>(
    '/password-policy',
  )
  return res.data.data
}

/**
 * 更新密码策略配置
 *
 * @param body 更新请求
 * @returns 更新后的策略 VO
 */
export async function updatePasswordPolicy(
  body: PasswordPolicyUpdateRequest,
): Promise<PasswordPolicyVO> {
  const res = await axiosClient.put<BaseResponse<PasswordPolicyVO>>(
    '/password-policy',
    body,
  )
  return res.data.data
}

// ============================================================
// P2 SEC-2 登录安全策略（对齐 LoginPolicyController）
// ============================================================

/**
 * 查询当前登录安全策略
 *
 * @returns 登录策略 VO（策略被禁用时返回 null）
 */
export async function getLoginPolicy(): Promise<LoginPolicyVO | null> {
  const res = await axiosClient.get<BaseResponse<LoginPolicyVO | null>>(
    '/login-policy',
  )
  return res.data.data
}

/**
 * 更新登录安全策略配置
 *
 * @param body 更新请求
 * @returns 更新后的策略 VO
 */
export async function updateLoginPolicy(
  body: LoginPolicyUpdateRequest,
): Promise<LoginPolicyVO> {
  const res = await axiosClient.put<BaseResponse<LoginPolicyVO>>(
    '/login-policy',
    body,
  )
  return res.data.data
}

// ============================================================
// P2 SEC-3 在线会话管理（对齐 SessionController）
// ============================================================

/**
 * 分页查询在线会话列表
 *
 * @param query 查询请求（userId / username 筛选 + 分页）
 * @returns 在线会话分页列表
 */
export async function listSessions(
  query: SessionQueryRequest,
): Promise<IPage<SessionVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<SessionVO>>>(
    '/sessions',
    { params: query },
  )
  return res.data.data
}

/**
 * 踢人下线（按 sessionId 销毁会话，对应 token 加入黑名单）
 *
 * @param sessionId 会话 ID
 * @returns 操作结果
 */
export async function killSession(sessionId: string): Promise<boolean> {
  const res = await axiosClient.delete<BaseResponse<boolean>>(
    `/sessions/${sessionId}`,
  )
  return res.data.data
}

// ============================================================
// P2 OPS-1 系统健康检查（对齐 SystemHealthController）
// ============================================================

/**
 * 一键检测系统健康状态（DB / Redis / Python AI / MinIO 连通性）
 *
 * @returns 健康检查结果
 */
export async function checkSystemHealth(): Promise<SystemHealthVO> {
  const res = await axiosClient.get<BaseResponse<SystemHealthVO>>(
    '/system-health',
  )
  return res.data.data
}

/** 系统设置 API 聚合导出 */
export const settingsApi = {
  // P1 USR-1 用户管理
  listUsers,
  getUser,
  addUser,
  updateUser,
  toggleUserStatus,
  resetPassword,
  deleteUser,
  assignUserRoles,
  // P1 USR-2 角色管理
  listRoles,
  listAllRoles,
  getRole,
  addRole,
  updateRole,
  toggleRoleStatus,
  deleteRole,
  // P1 RSK-1 审批超时阈值配置
  listApprovalTimeoutConfigs,
  updateApprovalTimeoutConfig,
  // P1 RSK-3 审批人映射配置
  listApprovalRouteConfigs,
  addApprovalRouteConfig,
  updateApprovalRouteConfig,
  deleteApprovalRouteConfig,
  // 通知通道 / 模板
  listNotificationChannels,
  listNotificationTemplates,
  saveNotificationConfig,
  // P0-1 风险关键词库
  listRiskKeywords,
  addRiskKeyword,
  updateRiskKeyword,
  deleteRiskKeyword,
  // P0-2 Skill 元数据
  listSkills,
  registerSkill,
  updateSkill,
  // P0-4 通知通道 Webhook
  saveChannelConfig,
  // P2 SEC-1 密码策略配置
  getPasswordPolicy,
  updatePasswordPolicy,
  // P2 SEC-2 登录安全策略
  getLoginPolicy,
  updateLoginPolicy,
  // P2 SEC-3 在线会话管理
  listSessions,
  killSession,
  // P2 OPS-1 系统健康检查
  checkSystemHealth,
  // P3 统一配置中心
  listSystemConfigs,
  updateSystemConfig,
  refreshSystemConfig,
  // P3 权限矩阵
  listAllPermissions,
  getPermissionMatrix,
  saveRolePermissions,
}

// ============================================================
// P3 统一配置中心（对齐 SystemConfigController @RequestMapping("/system-config")）
// ============================================================

/**
 * 查询全部系统配置项
 *
 * 用于设置页 AI 配置 / 存储配置 / 定时任务 / 系统参数 四个子区块统一加载，前端按 config_key 前缀过滤分组。
 *
 * @returns 配置列表
 */
export async function listSystemConfigs(): Promise<SystemConfigVO[]> {
  const res = await axiosClient.get<BaseResponse<SystemConfigVO[]>>(
    '/system-config',
  )
  return res.data.data
}

/**
 * 按 config_key 更新配置（运行时热生效）
 *
 * @param key 配置键
 * @param body 更新请求
 * @returns 更新后的配置 VO
 */
export async function updateSystemConfig(
  key: string,
  body: SystemConfigUpdateRequest,
): Promise<SystemConfigVO> {
  const res = await axiosClient.put<BaseResponse<SystemConfigVO>>(
    `/system-config/${key}`,
    body,
  )
  return res.data.data
}

/**
 * 手动刷新缓存并重建 AI / MinIO 配置属性（高频字段立即热生效）
 *
 * @returns 操作结果
 */
export async function refreshSystemConfig(): Promise<boolean> {
  const res = await axiosClient.post<BaseResponse<boolean>>(
    '/system-config/refresh',
  )
  return res.data.data
}

// ============================================================
// P3 权限矩阵（对齐 PermissionController @RequestMapping("/permissions")）
// ============================================================

/**
 * 查询全部权限点（矩阵列定义）
 *
 * @returns 权限点列表
 */
export async function listAllPermissions(): Promise<PermissionVO[]> {
  const res = await axiosClient.get<BaseResponse<PermissionVO[]>>(
    '/permissions',
  )
  return res.data.data
}

/**
 * 查询角色权限矩阵（角色列表 + 每个角色已勾选的权限 ID 集合）
 *
 * @returns 角色权限矩阵行列表
 */
export async function getPermissionMatrix(): Promise<
  RolePermissionMatrixVO[]
> {
  const res = await axiosClient.get<
    BaseResponse<RolePermissionMatrixVO[]>
  >('/permissions/matrix')
  return res.data.data
}

/**
 * 保存角色权限（全量替换语义：先删后插）
 *
 * @param roleId 角色业务 ID
 * @param body 保存请求（含权限 ID 集合）
 * @returns 操作结果
 */
export async function saveRolePermissions(
  roleId: string,
  body: RolePermissionSaveRequest,
): Promise<boolean> {
  const res = await axiosClient.put<BaseResponse<boolean>>(
    `/permissions/roles/${roleId}`,
    body,
  )
  return res.data.data
}

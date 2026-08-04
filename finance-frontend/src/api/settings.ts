/**
 * 系统设置 API 封装（P4 settings 原型对齐 + P0 功能扩展）
 *
 * 对齐后端：
 * - 用户管理：后端 com.finrpa.auth 包下暂无 UserController，由 Mock 端提供数据
 *   TODO 后端待开发：GET /api/v1/users（列表）/ POST /api/v1/users（创建）/ PUT /api/v1/users/:id（更新）
 * - 角色管理：后端暂无 RoleController，由 Mock 端提供数据
 *   TODO 后端待开发：GET /api/v1/roles（列表）/ POST /api/v1/roles（创建）/ DELETE /api/v1/roles/:id（删除）
 * - 风险关键词库（P0-1）：复用已有 RiskKeywordController（GET / POST / PUT / DELETE）
 * - Skill 元数据（P0-2）：复用已有 SkillController（GET / POST / PUT）
 * - 部门 / 业务线（P0-3）：复用已有 TenantController（GET /tenant/departments / GET /tenant/business-lines）
 * - 通知通道（P0-4）：复用 GET /notification/channels；新增 PUT /notification/channels/{channel} 保存 Webhook
 * - 通知模板：后端暂无对应实体，由 Mock 端提供数据 + Mock 持久化启停
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  ChannelConfigSaveRequest,
  ChannelVO,
  IPage,
  NotificationConfigSaveRequest,
  NotificationTemplateConfigVO,
  RiskKeywordAddRequest,
  RiskKeywordQueryRequest,
  RiskKeywordVO,
  RoleVO,
  SkillAddRequest,
  SkillQueryRequest,
  SkillUpdateRequest,
  SkillVO,
  UserVO,
} from './types'

// ============================================================
// 用户 / 角色 / 通知模板（Mock 数据，后端待开发）
// ============================================================

/**
 * 查询用户列表
 *
 * @returns 用户列表
 */
export async function listUsers(): Promise<UserVO[]> {
  const res = await axiosClient.get<BaseResponse<UserVO[]>>('/users')
  return res.data.data
}

/**
 * 查询角色列表
 *
 * @returns 角色列表
 */
export async function listRoles(): Promise<RoleVO[]> {
  const res = await axiosClient.get<BaseResponse<RoleVO[]>>('/roles')
  return res.data.data
}

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

/** 系统设置 API 聚合导出 */
export const settingsApi = {
  listUsers,
  listRoles,
  listNotificationChannels,
  listNotificationTemplates,
  saveNotificationConfig,
  listRiskKeywords,
  addRiskKeyword,
  updateRiskKeyword,
  deleteRiskKeyword,
  listSkills,
  registerSkill,
  updateSkill,
  saveChannelConfig,
}

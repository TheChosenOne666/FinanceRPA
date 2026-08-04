/**
 * 后端 API 类型定义
 *
 * 对齐 finance-backend 的 DTO：
 * - BaseResponse<T> 对齐 com.finrpa.common.response.BaseResponse
 * - LoginResponse 对齐 com.finrpa.auth.dto.response.LoginResponse
 * - UserInfoResponse 对齐 com.finrpa.auth.dto.response.UserInfoResponse
 * - ErrorCode 对齐 com.finrpa.common.response.ErrorCode
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

/**
 * 后端统一响应封装
 *
 * @template T 业务数据类型
 */
export interface BaseResponse<T = unknown> {
  /** 状态码（0 表示成功，非 0 表示业务错误） */
  code: number;
  /** 响应数据 */
  data: T;
  /** 响应消息 */
  message: string;
}

/**
 * 后端业务错误码（对齐 ErrorCode 枚举）
 */
export const ErrorCode = {
  SUCCESS: 0,
  PARAMS_ERROR: 40000,
  NOT_LOGIN_ERROR: 40100,
  NO_AUTH_ERROR: 40101,
  NOT_FOUND_ERROR: 40400,
  FORBIDDEN_ERROR: 40300,
  SYSTEM_ERROR: 50000,
  OPERATION_ERROR: 50001,
} as const;

/**
 * 登录请求 DTO（对齐 com.finrpa.auth.dto.request.LoginRequest）
 */
export interface LoginRequest {
  /** 用户名（登录账号） */
  username: string;
  /** 密码 */
  password: string;
}

/**
 * 刷新 token 请求 DTO（对齐 com.finrpa.auth.dto.request.RefreshRequest）
 */
export interface RefreshRequest {
  /** 刷新令牌 */
  refreshToken: string;
}

/**
 * 权限检查请求 DTO（对齐 com.finrpa.auth.dto.request.PermissionCheckRequest）
 */
export interface PermissionCheckRequest {
  /** 资源类型 */
  resourceType: string;
  /** 资源 ID */
  resourceId: string;
  /** 操作类型 */
  action: string;
}

/**
 * 登录用户信息（LoginResponse 内嵌）
 */
export interface LoginUserInfo {
  /** 用户业务 ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 真实姓名 */
  realName: string;
  /** 所属组织 ID */
  orgId: string;
  /** 所属组织名称 */
  orgName: string;
  /** 所属部门名称 */
  deptName: string;
  /** 角色编码列表 */
  roles: string[];
}

/**
 * 登录响应 DTO（对齐 com.finrpa.auth.dto.response.LoginResponse）
 */
export interface LoginResponse {
  /** 访问令牌 */
  accessToken: string;
  /** 刷新令牌 */
  refreshToken: string;
  /** 过期时间（秒） */
  expiresIn: number;
  /** 登录用户信息 */
  user: LoginUserInfo;
}

/**
 * 用户详细信息响应 DTO（对齐 com.finrpa.auth.dto.response.UserInfoResponse）
 */
export interface UserInfoResponse {
  /** 用户业务 ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 真实姓名 */
  realName: string;
  /** 头像地址 */
  avatar?: string;
  /** 邮箱 */
  email?: string;
  /** 手机号 */
  phone?: string;
  /** 所属组织 ID */
  orgId: string;
  /** 所属组织名称 */
  orgName: string;
  /** 所属部门名称 */
  deptName: string;
  /** 角色编码列表 */
  roles: string[];
  /** 权限编码列表 */
  permissions: string[];
}

/**
 * 权限检查响应
 */
export interface PermissionCheckResponse {
  /** 是否有权限 */
  hasPermission: boolean;
}

// ============================================================
// 任务管理（M2.5）
// 对齐 com.finrpa.agent.dto.request / response
// ============================================================

/**
 * 任务状态枚举（对齐 com.finrpa.agent.enums.TaskStateEnum）
 */
export type TaskStatus =
  | 'PENDING' // 待执行
  | 'EXECUTING' // 执行中
  | 'SUCCESS' // 成功
  | 'FAILED' // 失败
  | 'NEEDS_HUMAN' // 需要人工介入
  | 'ABORTED'; // 已终止

/**
 * 子任务状态枚举（对齐 com.finrpa.agent.enums.SubTaskStateEnum）
 */
export type SubTaskStatus =
  | 'PENDING' // 待执行
  | 'RUNNING' // 执行中
  | 'COMPLETED' // 已完成
  | 'FAILED' // 已失败
  | 'SKIPPED' // 已跳过
  | 'REPLANNED'; // 已重规划

/**
 * 任务视图对象（对齐 com.finrpa.agent.dto.response.TaskVO）
 */
export interface TaskVO {
  /** 任务 ID（雪花算法） */
  taskId: string;
  /** 组织 ID */
  orgId: string;
  /** 触发用户 ID */
  userId: string;
  /** 任务目标 */
  goal: string;
  /** 任务状态 */
  status: TaskStatus;
  /** 当前步骤序号 */
  currentStep: number;
  /** 总步骤数 */
  totalSteps: number;
  /** 状态消息 */
  message?: string;
  /** 错误信息 */
  errorMessage?: string;
  /** Skyvern 任务 ID（M3.8 引入） */
  skyvernTaskId?: string;
  /** 触发用户姓名（关联 sys_user.real_name） */
  userName?: string;
  /** 任务耗时（毫秒，仅终态任务计算） */
  durationMs?: number;
  /** 风险等级（关联工作流模板：low / medium / high / critical） */
  riskLevel?: string;
  /** 部门业务 ID（M7.6 三维度 RBAC） */
  departmentId?: string;
  /** 部门名称（关联 enterprise_department.dept_name，前端展示用） */
  departmentName?: string;
  /** 业务线业务 ID（M7.6 三维度 RBAC） */
  businessLineId?: string;
  /** 业务线名称（关联 enterprise_business_line.business_line_name，前端展示用） */
  businessLineName?: string;
  /** 创建时间（ISO 字符串） */
  createTime: string;
  /** 更新时间（ISO 字符串） */
  updateTime: string;
}

/**
 * 子任务视图对象（对齐 com.finrpa.agent.dto.response.SubTaskVO）
 */
export interface SubTaskVO {
  /** 子任务 ID */
  subtaskId: string;
  /** 所属任务 ID */
  taskId: string;
  /** 子任务序号（从 0 开始） */
  subtaskIndex: number;
  /** 子任务目标 */
  goal: string;
  /** 完成条件 */
  completionCondition?: string;
  /** 最大重试次数 */
  maxRetries?: number;
  /** 失败策略：RETRY / SKIP / ABORT / REPLAN */
  failureStrategy?: string;
  /** 子任务状态 */
  status: SubTaskStatus;
  /** 错误信息 */
  errorMessage?: string;
  /** 执行结果数据（JSON 字符串） */
  resultData?: string;
  /** 开始执行时间 */
  startedAt?: string;
  /** 完成时间 */
  completedAt?: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
}

/**
 * 任务详情视图对象（对齐 com.finrpa.agent.dto.response.TaskDetailVO）
 */
export interface TaskDetailVO extends TaskVO {
  /** 任务参数（JSON 字符串） */
  params?: string;
  /** 关联工作流模板 ID */
  workflowId?: string;
  /** 子任务列表 */
  subtasks: SubTaskVO[];
}

/**
 * 任务分页查询请求（对齐 com.finrpa.agent.dto.request.TaskQueryRequest）
 */
export interface TaskQueryRequest {
  /** 当前页号（从 1 开始） */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 排序字段 */
  sortField?: string;
  /** 排序顺序：asc / desc */
  sortOrder?: 'asc' | 'desc';
  /** 任务状态筛选 */
  status?: TaskStatus | '';
  /** 关键词搜索（匹配 goal） */
  searchText?: string;
  /** 工作流模板 ID 筛选（用于查询某个工作流的执行历史） */
  workflowId?: string;
  /** 业务线 ID 筛选（M7.6 三维度 RBAC，org_admin 全局可筛） */
  businessLineId?: string;
  /** 部门 ID 筛选（M7.6 三维度 RBAC） */
  departmentId?: string;
}

/**
 * MyBatis-Plus 分页响应（对齐 com.baomidou.mybatisplus.core.metadata.IPage）
 */
export interface IPage<T> {
  /** 当前页数据 */
  records: T[];
  /** 当前页号 */
  current: number;
  /** 页面大小 */
  size: number;
  /** 总记录数 */
  total: number;
  /** 总页数 */
  pages: number;
}

/**
 * 任务触发请求（对齐 com.finrpa.ai.client.dto.TaskTriggerRequest）
 */
export interface TaskTriggerRequest {
  /** 任务目标（如 "下载银行流水"） */
  goal: string;
  /** 任务参数（业务自定义） */
  params?: Record<string, unknown>;
  /** 关联工作流模板 ID（可选） */
  workflowId?: string;
  /** 业务线业务 ID（可选；M7.6 三维度 RBAC，不传则后端从用户主关联中推断） */
  businessLineId?: string;
  /** 部门业务 ID（可选；M7.6 三维度 RBAC，不传则后端从用户主关联中推断） */
  departmentId?: string;
}

/**
 * 任务触发响应（对齐 com.finrpa.ai.client.dto.TaskTriggerResponse）
 */
export interface TaskTriggerResponse {
  /** 任务 ID */
  taskId: string;
  /** 初始状态 */
  status: string;
  /** 响应消息 */
  message: string;
}

/**
 * SSE 事件类型（对齐 Python app/schemas.py::SseEvent）
 */
export type SseEventType =
  | 'step_start'
  | 'step_end'
  | 'progress'
  | 'replan'
  | 'screenshot'
  | 'error'
  | 'complete';

/**
 * SSE 事件数据载荷（各事件类型共用，按需取字段）
 */
export interface SseEventData {
  /** 任务 ID */
  taskId?: string;
  /** 当前步骤序号 */
  currentStep?: number;
  /** 总步骤数 */
  totalSteps?: number;
  /** 状态消息 */
  message?: string;
  /** 任务状态（终态事件携带） */
  state?: string;
  /** 子任务序号 */
  subtaskIndex?: number;
  /** 子任务目标 */
  goal?: string;
  /** 子任务是否成功 */
  success?: boolean;
  /** 执行耗时（毫秒） */
  durationMs?: number;
  /** 错误信息 */
  error?: string;
  /** 截图对象 key */
  screenshotKey?: string;
  /** 重新规划次数 */
  totalReplans?: number;
  /** 最大重新规划次数 */
  maxReplans?: number;
  /** 失败子任务序号 */
  failedSubtaskIndex?: number;
  /** 时间戳（ISO 字符串） */
  timestamp?: string;
}

/**
 * SSE 事件（EventSource 解析后结构）
 */
export interface SseEvent {
  /** 事件类型 */
  event: SseEventType;
  /** 事件数据 */
  data: SseEventData;
}

// ============================================================
// 工作流模板管理（M3.6）
// 对齐 com.finrpa.workflows.dto.request / response
// ============================================================

/**
 * 行业枚举（对齐 com.finrpa.workflows.enums.IndustryEnum）
 */
export type WorkflowIndustry = 'banking' | 'insurance' | 'securities';

/**
 * 风险等级枚举（对齐 com.finrpa.workflows.enums.RiskLevelEnum）
 */
export type WorkflowRiskLevel = 'low' | 'medium' | 'high' | 'critical';

/**
 * 工作流模板视图（对齐 com.finrpa.workflows.dto.response.WorkflowVO）
 *
 * 说明：params / steps 后端返回为 JSON 字符串，前端使用时按需 JSON.parse
 */
export interface WorkflowVO {
  /** 工作流业务 ID */
  workflowId: string;
  /** 模板名称 */
  name: string;
  /** 模板描述 */
  description?: string;
  /** 行业：banking / insurance / securities */
  industry: WorkflowIndustry;
  /** 风险等级：low / medium / high / critical */
  riskLevel: WorkflowRiskLevel;
  /** 参数定义 JSON 数组字符串（[{name,type,required,encrypted,description}]） */
  params: string;
  /** 步骤 JSON 数组字符串（[{skill,params_mapping}]） */
  steps: string;
  /** 版本号 */
  version?: string;
  /** 启用状态：0-禁用 1-启用 */
  enabled: number;
  /** 创建人姓名（null 表示系统创建） */
  createUser?: string;
  /** 执行次数（统计 rpa_agent_task 表中 workflow_id = 此模板的记录数） */
  runCount?: number;
  /** 创建时间（ISO 字符串） */
  createTime: string;
  /** 更新时间（ISO 字符串） */
  updateTime: string;
}

/**
 * 工作流参数定义（解析自 WorkflowVO.params 数组项）
 */
export interface WorkflowParam {
  /** 参数名（用于 params_mapping 中的 {{name}} 引用） */
  name: string;
  /** 参数类型：string / number / boolean / date 等 */
  type: string;
  /** 是否必填 */
  required: boolean;
  /** 是否加密存储（敏感参数如密码） */
  encrypted: boolean;
  /** 参数描述 */
  description?: string;
}

/**
 * 工作流步骤定义（解析自 WorkflowVO.steps 数组项）
 */
export interface WorkflowStep {
  /** 引用的 Skill 名称（如 login / form_fill / file_download） */
  skill: string;
  /** 参数映射（key 为 Skill 入参字段，value 为 {{param_name}} 模板字符串或字面量） */
  params_mapping: Record<string, unknown>;
}

/**
 * 工作流模板分页查询请求（对齐 com.finrpa.workflows.dto.request.WorkflowQueryRequest）
 */
export interface WorkflowQueryRequest {
  /** 当前页号 */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 模板名称（模糊搜索） */
  name?: string;
  /** 行业筛选 */
  industry?: WorkflowIndustry | '';
  /** 风险等级筛选 */
  riskLevel?: WorkflowRiskLevel | '';
  /** 启用状态筛选：null-全部 0-禁用 1-启用 */
  enabled?: number | '';
}

/**
 * 工作流触发执行请求（对齐 com.finrpa.workflows.dto.request.WorkflowRunRequest）
 */
export interface WorkflowRunRequest {
  /** 运行参数键值对（key 对应 params 中的 name） */
  params: Record<string, unknown>;
}

/**
 * 工作流触发执行结果（对齐 com.finrpa.workflows.dto.response.WorkflowRunVO）
 */
export interface WorkflowRunVO {
  /** 任务 ID（agent_task 表主键） */
  taskId: string;
  /** 工作流模板 ID */
  workflowId: string;
  /** 任务初始状态 */
  state: string;
}

// ============================================================
// NEEDS_HUMAN 队列管理（M5.5）
// 对齐 com.finrpa.llm.dto.request / response
// ============================================================

/**
 * NEEDS_HUMAN 队列状态
 */
export type NeedsHumanStatus = 'PENDING' | 'RESOLVED';

/**
 * 处置动作
 */
export type ResolveAction = 'skip' | 'manual' | 'abort';

/**
 * NEEDS_HUMAN 队列视图对象（对齐 com.finrpa.llm.dto.response.NeedsHumanQueueVO）
 */
export interface NeedsHumanQueueVO {
  /** 队列业务 ID */
  queueId: string;
  /** 任务 ID */
  taskId: string;
  /** 组织 ID */
  orgId: string;
  /** 业务线 ID（P3 ai-monitoring 原型对齐） */
  businessLineId?: string;
  /** 业务线名称（用于队列卡片展示） */
  businessLineName?: string;
  /** 子任务 ID */
  subtaskId?: string;
  /** 调用上下文名称 */
  contextName: string;
  /** 截图 URL */
  screenshotUrl?: string;
  /** LLM 最后一次原始输出 */
  llmRawOutput?: string;
  /** 校验错误信息 */
  validationError?: string;
  /** 总尝试次数 */
  attempts: number;
  /** 队列状态 */
  status: NeedsHumanStatus;
  /** 处置动作 */
  resolveAction?: ResolveAction;
  /** 处置人用户 ID */
  resolvedBy?: string;
  /** 处置时间 */
  resolvedAt?: string;
  /** 创建时间 */
  createTime: string;
  /** 任务目标（关联 rpa_agent_task.goal，Mock 端联表填充，用于队列卡片"子任务"展示） */
  taskTitle?: string;
  /** 子任务目标（关联 rpa_agent_subtask.goal，Mock 端联表填充，用于队列卡片"子任务"展示） */
  subtaskGoal?: string;
}

/**
 * NEEDS_HUMAN 队列查询请求
 */
export interface NeedsHumanQueryRequest {
  /** 当前页号 */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 状态筛选 */
  status?: NeedsHumanStatus | '';
  /** 任务 ID 筛选 */
  taskId?: string;
  /** 业务线 ID 筛选（P3 ai-monitoring 原型对齐） */
  businessLineId?: string;
}

/**
 * NEEDS_HUMAN 处置请求
 */
export interface NeedsHumanResolveRequest {
  /** 处置动作 */
  action: ResolveAction;
}

// ============================================================
// LLM 调用统计（M5.4）
// 对齐 com.finrpa.llm.dto.response.LlmCallStatsVO / ModelStatsVO
// ============================================================

/**
 * 单模型统计（对齐 com.finrpa.llm.dto.response.ModelStatsVO）
 */
export interface ModelStatsVO {
  /** 模型名 */
  model: string;
  /** 调用次数 */
  calls: number;
  /** 成功调用次数 */
  successCalls: number;
  /** 总 token 数 */
  totalTokens: number;
  /** 总成本（美元） */
  cost: number;
}

/**
 * LLM 调用统计 VO（对齐 com.finrpa.llm.dto.response.LlmCallStatsVO）
 */
export interface LlmCallStatsVO {
  /** 总调用次数 */
  totalCalls: number;
  /** 成功调用次数 */
  successCalls: number;
  /** 失败调用次数 */
  failedCalls: number;
  /** 缓存命中次数 */
  cacheHitCalls: number;
  /** 缓存命中率（0-1） */
  cacheHitRate: number;
  /** 总 prompt token 数 */
  totalPromptTokens: number;
  /** 总 completion token 数 */
  totalCompletionTokens: number;
  /** 总 token 数 */
  totalTokens: number;
  /** 总成本（美元） */
  totalCost: number;
  /** 平均调用耗时（毫秒） */
  avgDurationMs: number;
  /** 按模型维度的统计列表 */
  modelStats: ModelStatsVO[];
  // ===== 趋势字段（P3 ai-monitoring 原型对齐：对比上一周期） =====
  /** 总调用次数环比变化百分比（正数↑增长 / 负数↓下降，null 表示无对比数据） */
  totalCallsTrendPct?: number | null;
  /** 总成本环比变化百分比 */
  totalCostTrendPct?: number | null;
  /** 缓存命中率环比变化（百分点） */
  cacheHitRateTrendPct?: number | null;
  /** 平均耗时环比变化百分比 */
  avgDurationTrendPct?: number | null;
}

/**
 * LLM 调用统计查询请求
 */
export interface LlmCallStatsQueryRequest {
  /** 起始时间（ISO 字符串） */
  startTime?: string;
  /** 结束时间（ISO 字符串） */
  endTime?: string;
  /** 模型名筛选 */
  model?: string;
  /** 任务 ID 筛选 */
  taskId?: string;
  /** 业务线 ID 筛选（P3 ai-monitoring 原型对齐） */
  businessLineId?: string;
}

/**
 * LLM 调用记录 VO（单条记录，对齐 com.finrpa.llm.dto.response.LlmCallRecordVO）
 */
export interface LlmCallRecordVO {
  /** 调用记录业务 ID */
  callId: string;
  /** 任务 ID（可空） */
  taskId?: string;
  /** 任务标题（关联 rpa_agent_task.goal） */
  taskTitle?: string;
  /** LLM 模型名 */
  model: string;
  /** 调用上下文名称 */
  contextName: string;
  /** 调用是否成功 */
  success: boolean;
  /** 是否命中缓存 */
  cacheHit: boolean;
  /** 本次调用成本（美元） */
  cost: number;
  /** 调用耗时（毫秒） */
  durationMs: number;
  /** 调用发生时间 */
  callTime: string;
}

/**
 * LLM 调用记录分页查询请求（继承分页基类，P3 ai-monitoring 原型对齐）
 */
export interface LlmCallRecordQueryRequest {
  /** 当前页号 */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 起始时间 */
  startTime?: string;
  /** 结束时间 */
  endTime?: string;
  /** 模型名筛选 */
  model?: string;
  /** 任务 ID 筛选 */
  taskId?: string;
  /** 业务线 ID 筛选 */
  businessLineId?: string;
  /** 是否仅查询缓存命中记录 */
  cacheHit?: boolean;
}

/**
 * LLM 调用按日聚合趋势 VO（对齐 com.finrpa.llm.dto.response.LlmCallDailyTrendVO）
 */
export interface LlmCallDailyTrendVO {
  /** 日期（格式 yyyy-MM-dd） */
  date: string;
  /** 当日调用次数 */
  calls: number;
  /** 当日总成本（美元） */
  cost: number;
  /** 当日平均耗时（毫秒） */
  avgDurationMs: number;
}

/**
 * 通用分页结果（对齐 MyBatis-Plus IPage）
 */
export interface IPage<T> {
  /** 当前页号 */
  current: number;
  /** 页面大小 */
  size: number;
  /** 总记录数 */
  total: number;
  /** 总页数 */
  pages: number;
  /** 当前页数据 */
  records: T[];
}

// ============================================================
// 审批管理（M6.5）
// 对齐 com.finrpa.approval.dto.request / response
// ============================================================

/**
 * 审批状态（对齐 com.finrpa.approval.enums.ApprovalStatusEnum）
 */
export type ApprovalStatus =
  | 'PENDING' // 待审批
  | 'APPROVED' // 已通过
  | 'REJECTED' // 已拒绝
  | 'TIMEOUT'; // 已超时

/**
 * 审批路由（对齐 com.finrpa.approval.enums.ApprovalRouteEnum）
 */
export type ApprovalRoute = 'auto' | 'department' | 'compliance';

/**
 * 审批请求视图（对齐 com.finrpa.approval.dto.response.ApprovalRequestVO）
 */
export interface ApprovalRequestVO {
  /** 审批单 ID（雪花算法） */
  approvalId: string;
  /** 关联任务 ID */
  taskId: string;
  /** 组织 ID */
  orgId: string;
  /** 关联工作流模板 ID */
  workflowId?: string;
  /** 触发用户 ID */
  userId: string;
  /** 触发用户姓名（联表 sys_user.real_name 填充，对齐原型 02-dashboard.html 申请人列显示） */
  userName?: string;
  /** 风险等级：low / medium / high / critical */
  riskLevel: WorkflowRiskLevel;
  /** 审批路由：auto / department / compliance */
  approvalRoute: ApprovalRoute;
  /** 审批状态 */
  status: ApprovalStatus;
  /** 审批人 ID（审批完成后填充） */
  approverId?: string;
  /** 通过理由 */
  approveReason?: string;
  /** 拒绝理由 */
  rejectReason?: string;
  /** 风险判断理由（LLM / 关键词检测结果） */
  riskReasoning?: string;
  /** 请求负载 JSON 字符串（TaskTriggerRequest 序列化，含 goal / params / workflowId） */
  requestPayload?: string;
  /** 超时截止时间（ISO 字符串） */
  timeoutAt?: string;
  /** 审批完成时间（ISO 字符串） */
  approvedAt?: string;
  /** 创建时间（ISO 字符串） */
  createTime: string;
}

/**
 * 审批分页查询请求（对齐 com.finrpa.approval.dto.request.ApprovalQueryRequest）
 */
export interface ApprovalQueryRequest {
  /** 当前页号（从 1 开始） */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 审批状态筛选 */
  status?: ApprovalStatus | '';
  /** 审批路由筛选 */
  approvalRoute?: ApprovalRoute | '';
  /** 风险等级筛选 */
  riskLevel?: WorkflowRiskLevel | '';
  /** 任务 ID 精确查询 */
  taskId?: string;
  /** 触发用户 ID 筛选（用于"我发起的"Tab，对齐原型 05-approval-center.html） */
  userId?: string;
}

/**
 * 审批操作请求（对齐 com.finrpa.approval.dto.request.ApprovalActionRequest）
 */
export interface ApprovalActionRequest {
  /** 审批理由（通过或拒绝的原因说明） */
  reason?: string;
}

// ============================================================
// 通知管理（M6.6）
// 对齐 com.finrpa.notification.dto.request / response
// ============================================================

/**
 * 通知通道类型（对齐 com.finrpa.notification.enums.NotificationChannelEnum）
 */
export type NotificationChannelType = 'wecom' | 'dingtalk';

/**
 * 通知模板类型（对齐 com.finrpa.notification.enums.NotificationTemplateEnum）
 */
export type NotificationTemplateType =
  | 'APPROVAL_PENDING' // 审批待处理
  | 'APPROVAL_TIMEOUT' // 审批超时告警
  | 'TASK_FAILED' // 任务失败
  | 'NEEDS_HUMAN' // NEEDS_HUMAN 接管
  | 'RISK_ESCALATION'; // 风险等级升级

/**
 * 通知通道信息（对齐 com.finrpa.notification.dto.response.ChannelVO）
 *
 * 说明：webhookUrl / enabled 为 P4 settings 原型对齐扩展字段（后端 ChannelVO 暂未包含，
 * 由 Mock 端提供；后端 CRUD 接口待开发，前端标注 TODO）。
 */
export interface ChannelVO {
  /** 通道类型：wecom / dingtalk */
  channel: NotificationChannelType;
  /** 通道中文名 */
  label: string;
  /** 是否已配置 Webhook URL */
  configured: boolean;
  /** Webhook URL（P4 settings 原型对齐：通道表格展示用，Mock 端填充明文） */
  webhookUrl?: string;
  /** 是否启用（P4 settings 原型对齐：通道开关，Mock 端维护状态） */
  enabled?: boolean;
}

/**
 * 通知模板配置项（P4 settings 原型对齐：08-settings.html 通知模板勾选列表）
 *
 * 说明：后端暂无对应实体，由 Mock 端维护；后端 CRUD 接口待开发，前端标注 TODO。
 */
export interface NotificationTemplateConfigVO {
  /** 模板类型 */
  templateType: NotificationTemplateType;
  /** 模板中文名 */
  label: string;
  /** 模板描述 */
  description: string;
  /** 频率标签：高频 / 紧急 / 普通 */
  frequency: 'high' | 'urgent' | 'normal';
  /** 是否启用 */
  enabled: boolean;
}

/**
 * 通知配置保存请求（P4 settings 原型对齐：通道开关 + 模板启停）
 *
 * 说明：后端暂无对应接口，由 Mock 端接受并返回成功；后端 CRUD 接口待开发。
 */
export interface NotificationConfigSaveRequest {
  /** 通道启用状态（key 为 channel 类型，value 为是否启用） */
  channels: Array<{ channel: NotificationChannelType; enabled: boolean }>;
  /** 模板启用状态（key 为 templateType，value 为是否启用） */
  templates: Array<{ templateType: NotificationTemplateType; enabled: boolean }>;
}

// ============================================================
// 系统设置 - 用户/角色管理（P4 settings 原型对齐）
// 说明：后端 com.finrpa.auth 包下仅有 UserMapper/RoleMapper/UserRoleMapper，
// 无 UserController/RoleController/Service/DTO。下列类型仅用于前端展示，
// 由 Mock 端提供数据；后端 CRUD 接口待开发，前端标注 TODO。
// ============================================================

/**
 * 用户视图（P4 settings 原型对齐：08-settings.html 用户管理表格）
 */
export interface UserVO {
  /** 用户业务 ID */
  userId: string;
  /** 用户名（登录账号） */
  username: string;
  /** 真实姓名 */
  realName: string;
  /** 所属部门名称（联表 sys_department.dept_name） */
  deptName: string;
  /** 角色编码列表（联表 sys_role.role_code） */
  roles: string[];
  /** 启用状态：true-启用 / false-已禁用 */
  enabled: boolean;
  /** 创建时间（ISO 字符串） */
  createTime: string;
}

/**
 * 角色视图（P4 settings 原型对齐：08-settings.html 角色管理表格）
 */
export interface RoleVO {
  /** 角色 ID */
  roleId: string;
  /** 角色编码（如 operator / approver / viewer） */
  roleCode: string;
  /** 角色中文名（如 操作员 / 审批员 / 观察员） */
  roleName: string;
  /** 权限范围描述（用于表格"权限范围"列展示） */
  permissionScope: string;
  /** 互斥约束（如 "不可兼任 approver"，空字符串表示无约束） */
  mutualExclusion: string;
  /** 是否内置角色（内置角色不可删除） */
  builtIn: boolean;
  /** 创建时间（ISO 字符串） */
  createTime: string;
}

/**
 * 通知发送结果（对齐 com.finrpa.notification.dto.response.NotificationSendResultVO）
 */
export interface NotificationSendResultVO {
  /** 通道类型 */
  channel: string;
  /** 是否发送成功 */
  success: boolean;
  /** 错误信息（失败时填充） */
  errorMessage?: string;
  /** 通道原始响应（用于审计 / 调试） */
  rawResponse?: string;
}

/**
 * 通知测试发送请求（对齐 com.finrpa.notification.dto.request.NotificationTestRequest）
 */
export interface NotificationTestRequest {
  /** 通道类型：wecom / dingtalk（必填） */
  channel: NotificationChannelType;
  /** 模板类型（必填） */
  templateType: NotificationTemplateType;
  /** 模板参数（键值对，可选） */
  params?: Record<string, unknown>;
}

/**
 * 通知重试队列统计（对齐 com.finrpa.notification.dto.response.RetryQueueStatsVO）
 *
 * 说明：后端 JsonConfig 将 Long 字段序列化为 String（防 JS 精度丢失），
 * 此处类型声明为 string | number 兼容，使用时通过 Number() 转换做数学运算。
 */
export interface RetryQueueStatsVO {
  /** 当前队列待重试任务数 */
  queueSize: string | number;
  /** 总尝试次数（含首次发送与所有重试） */
  totalAttempts: string | number;
  /** 成功次数 */
  successCount: string | number;
  /** 失败次数 */
  failureCount: string | number;
  /** 成功率（0.0 ~ 1.0） */
  successRate: string | number;
  /** 超过最大重试次数的告警数（待人工介入） */
  alertCount: string | number;
}

// ============================================================
// 审计日志（M7.5）
// 对齐 com.finrpa.audit.dto.request / response
// ============================================================

/**
 * 审计执行结果（对齐 com.finrpa.audit.constant.AuditConstant.RESULT_*）
 */
export type AuditExecutionResult = 'success' | 'failed';

/**
 * 审计操作类型（对齐 AuditLogCreateRequest.actionType 注释示例）
 *
 * 说明：后端 actionType 为字符串字段，未做枚举强约束；前端枚举仅作为筛选下拉项。
 */
export type AuditActionType =
  | 'NAVIGATE' // 页面跳转
  | 'CLICK' // 元素点击
  | 'INPUT_TEXT' // 文本输入
  | 'LOGIN' // 登录操作
  | 'FILE_DOWNLOAD' // 文件下载
  | 'FORM_FILL' // 表单填写
  | 'WAIT' // 等待
  | 'SCREENSHOT'; // 截图

/**
 * 审计排序字段白名单（对齐 com.finrpa.audit.constant.AuditConstant.ALLOWED_SORT_FIELDS）
 */
export type AuditSortField =
  | 'auditId'
  | 'taskId'
  | 'riskLevel'
  | 'startedAt'
  | 'durationMs'
  | 'createTime';

/**
 * 排序顺序（对齐 com.finrpa.common.constant.CommonConstant.SORT_ORDER_*）
 *
 * 后端约定：`ascend` 升序、`descend` 降序（默认）
 */
export type SortOrder = 'ascend' | 'descend';

/**
 * 审计日志视图（对齐 com.finrpa.audit.dto.response.AuditLogVO）
 *
 * 说明：后端 JsonConfig 将 Long 字段序列化为 String（防 JS 精度丢失），
 * 所有 ID 字段类型声明为 string。
 */
export interface AuditLogVO {
  /** 审计 ID（雪花算法） */
  auditId: string;
  /** 关联任务 ID */
  taskId: string;
  /** 组织 ID */
  orgId: string;
  /** 部门 ID */
  departmentId?: string;
  /** 业务线 ID */
  businessLineId?: string;
  /** 用户 ID */
  userId?: string;
  /** 触发用户姓名（联表 sys_user.real_name 填充，对齐原型 06-audit-logs.html 列表显示） */
  userName?: string;
  /** 部门名称（联表 sys_department.dept_name 填充） */
  departmentName?: string;
  /** 业务线名称（联表 sys_business_line.business_line_name 填充） */
  businessLineName?: string;
  /** 动作类型：NAVIGATE / CLICK / INPUT_TEXT / LOGIN 等 */
  actionType: string;
  /** 目标元素（CSS Selector / XPath） */
  targetElement?: string;
  /** 页面 URL */
  pageUrl?: string;
  /** 操作参数 JSON 字符串（已脱敏） */
  actionParams?: string;
  /** 执行结果：success / failed */
  executionResult: AuditExecutionResult;
  /** 错误信息（执行失败时填充） */
  errorMessage?: string;
  /** 风险等级：low / medium / high / critical */
  riskLevel?: WorkflowRiskLevel;
  /** 关联审批单 ID */
  approvalId?: string;
  /** 开始时间（ISO 字符串） */
  startedAt?: string;
  /** 完成时间（ISO 字符串） */
  completedAt?: string;
  /** 执行耗时（毫秒） */
  durationMs?: number;
  /** 操作前截图 URL（MinIO 预签名，1 小时有效） */
  beforeScreenshotUrl?: string;
  /** 操作后截图 URL（MinIO 预签名，1 小时有效） */
  afterScreenshotUrl?: string;
  /** LLM 模型名 */
  llmModel?: string;
  /** LLM token 用量 */
  llmTokensUsed?: number;
  /** LLM 成本（美元） */
  llmCost?: number;
  /** 创建时间（ISO 字符串） */
  createTime: string;
}

/**
 * 审计日志分页查询请求（对齐 com.finrpa.audit.dto.request.AuditLogQueryRequest）
 *
 * 说明：orgId 由后端从 TenantContext 自动填充，前端无需传递。
 */
export interface AuditLogQueryRequest {
  /** 当前页号（从 1 开始） */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 排序字段（仅允许白名单） */
  sortField?: AuditSortField;
  /** 排序顺序 */
  sortOrder?: SortOrder;
  /** 任务 ID 精确查询 */
  taskId?: string;
  /** 用户 ID 精确查询 */
  userId?: string;
  /** 部门 ID 精确查询 */
  departmentId?: string;
  /** 业务线 ID 精确查询 */
  businessLineId?: string;
  /** 风险等级筛选 */
  riskLevel?: WorkflowRiskLevel | '';
  /** 操作类型筛选 */
  actionType?: AuditActionType | '';
  /** 执行结果筛选 */
  executionResult?: AuditExecutionResult | '';
  /** 起始时间（包含，ISO 字符串，对应 java.sql.Timestamp） */
  startTime?: string;
  /** 截止时间（包含，ISO 字符串，对应 java.sql.Timestamp） */
  endTime?: string;
}

// ============================================================
// 运营大屏（M8.2）
// 对齐 com.finrpa.dashboard.dto.response
// ============================================================

/**
 * 风险等级统计 VO（对齐 com.finrpa.dashboard.dto.response.RiskLevelStatVO）
 */
export interface RiskLevelStatVO {
  /** 风险等级：low / medium / high / critical */
  riskLevel: WorkflowRiskLevel;
  /** 该风险等级的任务数 */
  count: string | number;
}

/**
 * 大屏概览 VO（对齐 com.finrpa.dashboard.dto.response.OverviewVO）
 *
 * 说明：后端 JsonConfig 将 Long 字段序列化为 String（防 JS 精度丢失），
 * 数值字段类型声明为 string | number 兼容，使用时通过 Number() 转换。
 */
export interface OverviewVO {
  /** 任务总数 */
  totalTasks: string | number;
  /** 成功任务数 */
  successTasks: string | number;
  /** 失败任务数 */
  failedTasks: string | number;
  /** 进行中任务数（EXECUTING + PENDING + NEEDS_HUMAN） */
  runningTasks: string | number;
  /** 任务成功率（0-1） */
  successRate: number;
  /** 平均执行时长（毫秒） */
  avgDurationMs: number;
  /** P95 执行时长（毫秒） */
  p95DurationMs: string | number;
  /** LLM 调用总次数 */
  llmCallCount: string | number;
  /** LLM 总成本（美元） */
  llmTotalCost: number;
  /** Action 缓存命中率（0-1） */
  llmCacheHitRate: number;
  /** 接管队列长度（PENDING 待处置数） */
  humanTakeoverQueueSize: string | number;
  /** 平均处置时长（毫秒） */
  avgResolveDurationMs: number;
  /** 风险等级分布 */
  riskLevelDistribution: RiskLevelStatVO[];
  /** 任务总数环比增长率（今日 vs 昨日，0.12 表示 +12%；null 表示无上期数据） */
  taskGrowthRate?: number;
  /** 成功率环比差值（百分点，0.021 表示 +2.1%；null 表示无上期数据） */
  successRateDelta?: number;
  /** LLM 成本环比变化率（今日 vs 昨日，-0.08 表示 -8%；null 表示无上期数据） */
  llmCostDelta?: number;
}

/**
 * 趋势数据点（对齐 TrendsVO.TrendPointVO）
 */
export interface TrendPointVO {
  /** 日期（yyyy-MM-dd） */
  date: string;
  /** 当日任务总数 */
  taskCount: string | number;
  /** 当日成功任务数 */
  successCount: string | number;
  /** 当日失败任务数 */
  failedCount: string | number;
  /** 当日 LLM 成本（美元） */
  cost: number;
}

/**
 * 大屏趋势 VO（对齐 com.finrpa.dashboard.dto.response.TrendsVO）
 */
export interface TrendsVO {
  /** 趋势数据点列表（按日期升序） */
  points: TrendPointVO[];
}

/**
 * 业务线统计 VO（对齐 com.finrpa.dashboard.dto.response.BusinessLineStatVO）
 */
export interface BusinessLineStatVO {
  /** 业务线 ID */
  businessLineId: string;
  /** 业务线名称 */
  businessLineName: string;
  /** 任务总数 */
  taskCount: string | number;
  /** 成功任务数 */
  successCount: string | number;
  /** 成功率（0-1） */
  successRate: number;
}

/**
 * 错误类型统计 VO（对齐 com.finrpa.dashboard.dto.response.ErrorTypeStatVO）
 */
export interface ErrorTypeStatVO {
  /** 错误类型（失败操作类型） */
  errorType: string;
  /** 出现次数 */
  count: string | number;
}

/**
 * 单模型成本统计（对齐 CostStatVO.ModelCostStatVO）
 */
export interface ModelCostStatVO {
  /** 模型名称 */
  model: string;
  /** 调用次数 */
  calls: string | number;
  /** 成本（美元） */
  cost: number;
  /** token 总数 */
  tokens: string | number;
}

/**
 * LLM 成本统计 VO（对齐 com.finrpa.dashboard.dto.response.CostStatVO）
 */
export interface CostStatVO {
  /** LLM 调用总次数 */
  totalCalls: string | number;
  /** LLM 总成本（美元） */
  totalCost: number;
  /** 总 token 数 */
  totalTokens: string | number;
  /** Action 缓存命中率（0-1） */
  cacheHitRate: number;
  /** 按模型维度的成本统计列表 */
  modelCosts: ModelCostStatVO[];
}

/**
 * 审批统计 VO（对齐 com.finrpa.dashboard.dto.response.ApprovalStatVO）
 */
export interface ApprovalStatVO {
  /** 审批单总数 */
  totalApprovals: string | number;
  /** 已通过数 */
  approvedCount: string | number;
  /** 已拒绝数 */
  rejectedCount: string | number;
  /** 超时数 */
  timeoutCount: string | number;
  /** 待处理数（PENDING） */
  pendingCount: string | number;
  /** 平均响应时长（分钟） */
  avgResponseMinutes: number;
}

// ============================================================
// 系统设置 - 风险关键词库（P0-1）
// 对齐 com.finrpa.approval.dto.request / response
// ============================================================

/** 风险关键词 VO（对齐 com.finrpa.approval.dto.response.RiskKeywordVO） */
export interface RiskKeywordVO {
  /** 关键词业务 ID */
  keywordId: string;
  /** 关键词文本 */
  keyword: string;
  /** 所属行业：banking / insurance / securities */
  industry: string;
  /** 分类：high_risk_operation / sensitive_data / large_amount */
  category: string;
  /** 风险类型：high / medium / low */
  riskType: string;
  /** 描述说明 */
  description?: string;
  /** 启用状态（0-禁用 1-启用） */
  enabled: number;
  /** 是否内置（0-自定义 1-内置） */
  builtin: number;
  /** 创建时间（ISO 字符串） */
  createTime: string;
  /** 更新时间（ISO 字符串） */
  updateTime: string;
}

/** 风险关键词新增 / 更新请求（对齐 com.finrpa.approval.dto.request.RiskKeywordAddRequest） */
export interface RiskKeywordAddRequest {
  /** 关键词文本 */
  keyword: string;
  /** 所属行业：banking / insurance / securities */
  industry: string;
  /** 分类：high_risk_operation / sensitive_data / large_amount */
  category: string;
  /** 风险类型：high / medium / low */
  riskType: string;
  /** 描述说明（可空） */
  description?: string;
  /** 启用状态（默认 1-启用） */
  enabled?: number;
}

/** 风险关键词分页查询请求（对齐 com.finrpa.approval.dto.request.RiskKeywordQueryRequest） */
export interface RiskKeywordQueryRequest {
  /** 当前页号（从 1 开始） */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 关键词模糊匹配（可空） */
  keyword?: string;
  /** 所属行业（可空） */
  industry?: string | '';
  /** 分类（可空） */
  category?: string | '';
  /** 风险类型（可空） */
  riskType?: string | '';
  /** 启用状态（可空，默认全部） */
  enabled?: number | '';
}

// ============================================================
// 系统设置 - Skill 元数据管理（P0-2）
// 对齐 com.finrpa.skills.dto.request / response
// ============================================================

/** Skill 视图对象（对齐 com.finrpa.skills.dto.response.SkillVO） */
export interface SkillVO {
  /** Skill 业务 ID */
  skillId: string;
  /** Skill 唯一标识 */
  name: string;
  /** 用途描述 */
  description?: string;
  /** 分类：auth / interaction / extraction */
  category: string;
  /** 参数 JSON Schema */
  paramSchema?: string;
  /** 失败策略：RETRY / SKIP / ABORT */
  errorStrategy?: string;
  /** 最大重试次数 */
  maxRetries?: number;
  /** 版本号 */
  version?: string;
  /** 启用状态（0-禁用 1-启用） */
  enabled: number;
  /** 创建时间（ISO 字符串） */
  createTime: string;
  /** 更新时间（ISO 字符串） */
  updateTime: string;
}

/** Skill 新增请求（对齐 com.finrpa.skills.dto.request.SkillAddRequest） */
export interface SkillAddRequest {
  /** Skill 唯一标识 */
  name: string;
  /** 用途描述 */
  description?: string;
  /** 分类：auth / interaction / extraction */
  category: string;
  /** 参数 JSON Schema */
  paramSchema?: string;
  /** 失败策略：RETRY / SKIP / ABORT */
  errorStrategy?: string;
  /** 最大重试次数 */
  maxRetries?: number;
  /** 版本号 */
  version?: string;
}

/** Skill 更新请求（对齐 com.finrpa.skills.dto.request.SkillUpdateRequest，name 不可改） */
export interface SkillUpdateRequest {
  /** 用途描述 */
  description?: string;
  /** 分类 */
  category?: string;
  /** 参数 JSON Schema */
  paramSchema?: string;
  /** 失败策略 */
  errorStrategy?: string;
  /** 最大重试次数 */
  maxRetries?: number;
  /** 版本号 */
  version?: string;
  /** 启用状态（0-禁用 1-启用） */
  enabled?: number;
}

/** Skill 分页查询请求（对齐 com.finrpa.skills.dto.request.SkillQueryRequest） */
export interface SkillQueryRequest {
  /** 当前页号 */
  current: number;
  /** 页面大小 */
  pageSize: number;
  /** 分类筛选 */
  category?: string | '';
  /** 启用状态筛选 */
  enabled?: number | '';
  /** 名称关键词搜索 */
  searchText?: string;
}

// ============================================================
// 通知通道 Webhook 配置保存（P0-4）
// 对齐 com.finrpa.notification.dto.request.ChannelConfigSaveRequest
// ============================================================

/** 通道 Webhook 配置保存请求 */
export interface ChannelConfigSaveRequest {
  /** Webhook URL（必填，空串表示清除配置） */
  webhookUrl: string;
  /** 加签密钥（仅 dingtalk 使用，可空） */
  secret?: string;
  /** 启用状态（必填） */
  enabled: boolean;
}

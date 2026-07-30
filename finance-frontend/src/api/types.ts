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
  /** 参数类型：string / number / boolean 等 */
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

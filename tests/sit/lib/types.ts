/**
 * SIT 测试类型定义（M9.5）。
 *
 * 对齐 Java 后端 VO/DTO + Python schemas 字段，覆盖 SIT 5 个场景所需类型。
 * 字段名与 Java JSON 序列化（驼峰）一致。
 */

/** 统一响应包装（对齐 BaseResponse<T>）。 */
export interface BaseResponse<T> {
  code: number;
  data: T;
  message: string;
}

/** 分页结果（对齐 IPage<T>）。 */
export interface IPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// region 认证
export interface LoginRequest {
  username: string;
  password: string;
}

export interface UserInfo {
  userId: string;
  username: string;
  realName: string;
  orgId: string;
  orgName: string;
  deptName: string;
  roles: string[];
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserInfo;
}
// endregion

// region 工作流
export interface WorkflowParam {
  name: string;
  type: string;
  required: boolean;
  encrypted: boolean;
  description: string;
}

export interface WorkflowVO {
  workflowId: string;
  name: string;
  industry: string;
  riskLevel: string;
  description: string;
  steps: string;
  params: string;
  enabled: number;
  createUser?: string;
  runCount?: number;
  createTime?: string;
}

export interface WorkflowRunRequest {
  params: Record<string, string>;
}

/** 触发工作流响应。medium 返回 EXECUTING；high/critical 返回 PENDING_APPROVAL + approvalId。 */
export interface WorkflowRunVO {
  taskId: string;
  workflowId: string;
  state: 'EXECUTING' | 'PENDING_APPROVAL';
  approvalId?: string;
}
// endregion

// region 任务
export type TaskStatus =
  | 'PENDING'
  | 'EXECUTING'
  | 'SUCCESS'
  | 'FAILED'
  | 'NEEDS_HUMAN'
  | 'ABORTED';

export interface TaskVO {
  taskId: string;
  orgId: string;
  userId: string;
  goal: string;
  status: TaskStatus;
  currentStep: number;
  totalSteps: number;
  message: string;
  errorMessage?: string;
  skyvernTaskId?: string;
  userName?: string;
  durationMs?: number;
  riskLevel?: string;
  departmentId?: string;
  departmentName?: string;
  businessLineId?: string;
  businessLineName?: string;
  workflowId?: string;
  createTime?: string;
  updateTime?: string;
}

export type SubTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED'
  | 'REPLANNED';

export interface SubTaskVO {
  subtaskId: string;
  taskId: string;
  subtaskIndex: number;
  goal: string;
  status: SubTaskStatus;
  errorMessage?: string;
  resultData?: string;
  startedAt?: string;
  completedAt?: string;
}

export interface TaskDetailVO extends TaskVO {
  params?: string;
  subtasks?: SubTaskVO[];
}

/** 协调状态视图（场景3 断点续跑用）。 */
export interface CoordinationStateVO {
  taskId: string;
  navigationGoal: string;
  currentPlan: string;
  completedSubtasks: string[];
  totalReplans: number;
  maxReplans: number;
  status: string;
  errorMessage?: string;
  updateTime?: string;
}
// endregion

// region 审批
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'TIMEOUT';
export type ApprovalRoute = 'auto' | 'department' | 'compliance';

export interface ApprovalRequestVO {
  approvalId: string;
  taskId: string;
  orgId: string;
  workflowId: string;
  userId: string;
  userName: string;
  riskLevel: string;
  approvalRoute: ApprovalRoute;
  status: ApprovalStatus;
  approverId?: string;
  approveReason?: string;
  rejectReason?: string;
  riskReasoning?: string;
  timeoutAt?: string;
  approvedAt?: string;
  createTime?: string;
}

export interface ApprovalActionRequest {
  reason?: string;
}

/** 审批超时配置（场景4 用）。 */
export interface ApprovalTimeoutConfigVO {
  riskLevel: string;
  timeoutMinutes: number;
  description?: string;
  updateTime?: string;
}

/** 审批超时配置更新请求（场景4 用）。 */
export interface ApprovalTimeoutConfigUpdateRequest {
  timeoutMinutes: number;
}
// endregion

// region 审计
export interface AuditLogVO {
  auditId: string;
  taskId: string;
  orgId: string;
  departmentId?: string;
  departmentName?: string;
  businessLineId?: string;
  businessLineName?: string;
  userId?: string;
  userName?: string;
  actionType: string;
  targetElement?: string;
  pageUrl?: string;
  actionParams?: string;
  executionResult: string;
  errorMessage?: string;
  riskLevel?: string;
  approvalId?: string;
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  beforeScreenshotUrl?: string;
  afterScreenshotUrl?: string;
  llmModel?: string;
  llmTokensUsed?: number;
  llmCost?: number;
  createTime?: string;
}
// endregion

// region NEEDS_HUMAN 队列
export type NeedsHumanStatus = 'PENDING' | 'RESOLVED';

export interface NeedsHumanQueueVO {
  queueId: string;
  taskId: string;
  orgId: string;
  businessLineId?: string;
  subtaskId?: string;
  contextName: string;
  screenshotUrl?: string;
  llmRawOutput?: string;
  validationError?: string;
  attempts: number;
  status: NeedsHumanStatus;
  resolveAction?: string;
  resolverId?: string;
  resolverName?: string;
  resolveReason?: string;
  resolvedAt?: string;
  createTime?: string;
}

/** NEEDS_HUMAN 事件上报请求（Python → Java，模拟 Python 回调用）。 */
export interface NeedsHumanReportRequest {
  taskId: string;
  orgId: string;
  businessLineId?: string;
  subtaskId?: string;
  contextName: string;
  screenshotUrl?: string;
  llmRawOutput?: string;
  validationError?: string;
  attempts: number;
}

/** NEEDS_HUMAN 处置请求（操作员 → Java）。 */
export interface NeedsHumanResolveRequest {
  action: 'skip' | 'manual' | 'abort';
}
// endregion

// region 内部回调请求 DTO（模拟 Python → Java）
/** 任务状态更新请求（对齐 Java TaskStateUpdateRequest）。 */
export interface TaskStateUpdateRequest {
  state: TaskStatus;
  currentStep?: number;
  totalSteps?: number;
  message?: string;
  errorMessage?: string;
}

/** 子任务状态更新请求（对齐 Java SubTaskUpdateRequest）。 */
export interface SubTaskUpdateRequest {
  subtaskIndex: number;
  status: SubTaskStatus;
  errorMessage?: string;
  resultData?: Record<string, unknown>;
}

/** 协调状态更新请求（对齐 Java CoordinationStateUpdateRequest）。 */
export interface CoordinationStateUpdateRequest {
  navigationGoal: string;
  currentPlan: string;
  completedSubtasks: string[];
  totalReplans: number;
  maxReplans: number;
  status: string;
  errorMessage?: string;
}
// endregion

// region 大屏
export interface RiskLevelStatVO {
  riskLevel: string;
  count: number;
}

export interface OverviewVO {
  totalTasks: number;
  successTasks: number;
  failedTasks: number;
  runningTasks: number;
  successRate: number;
  avgDurationMs?: number;
  p95DurationMs?: number;
  llmCallCount?: number;
  llmTotalCost?: number;
  llmCacheHitRate?: number;
  humanTakeoverQueueSize?: number;
  riskLevelDistribution?: RiskLevelStatVO[];
  taskGrowthRate?: number;
  successRateDelta?: number;
}
// endregion

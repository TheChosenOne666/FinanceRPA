/**
 * E2E 测试类型定义（M9.1）。
 *
 * 对齐 Java 后端 VO/DTO 的关键字段，仅声明 E2E 断言所需字段，不追求完整对齐。
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
  userId: number;
  username: string;
  realName: string;
  orgId: number;
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
  workflowId: number;
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
  taskId: number;
  workflowId: number;
  state: 'EXECUTING' | 'PENDING_APPROVAL';
  approvalId?: number;
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
  taskId: number;
  orgId: number;
  userId: number;
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
  departmentId?: number;
  departmentName?: string;
  businessLineId?: number;
  businessLineName?: string;
  workflowId?: number;
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
  subtaskId: number;
  taskId: number;
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
// endregion

// region 审批
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'TIMEOUT';
export type ApprovalRoute = 'auto' | 'department' | 'compliance';

export interface ApprovalRequestVO {
  approvalId: number;
  taskId: number;
  orgId: number;
  workflowId: number;
  userId: number;
  userName: string;
  riskLevel: string;
  approvalRoute: ApprovalRoute;
  status: ApprovalStatus;
  approverId?: number;
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
// endregion

// region 审计
export interface AuditLogVO {
  auditId: number;
  taskId: number;
  orgId: number;
  departmentId?: number;
  departmentName?: string;
  businessLineId?: number;
  businessLineName?: string;
  userId?: number;
  userName?: string;
  actionType: string;
  targetElement?: string;
  pageUrl?: string;
  actionParams?: string;
  executionResult: string;
  errorMessage?: string;
  riskLevel?: string;
  approvalId?: number;
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

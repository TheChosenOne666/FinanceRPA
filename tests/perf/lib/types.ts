/**
 * 性能测试类型定义（M9.2）。
 *
 * 性能测试套件独立，不依赖 tests/sit 类型。仅定义本套件所需的 VO/DTO 子集。
 */
/** BaseResponse 包装。 */
export interface BaseResponse<T> {
  code: number;
  data: T;
  message: string;
}

/** 分页结果。 */
export interface IPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// region 认证
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

/** 触发工作流响应。 medium 返回 EXECUTING；high/critical 返回 PENDING_APPROVAL + approvalId。 */
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

export interface TaskDetailVO extends TaskVO {
  params?: string;
  subtasks?: unknown[];
}

/** 任务状态更新请求（对齐 Java TaskStateUpdateRequest，场景2 模拟并发用）。 */
export interface TaskStateUpdateRequest {
  state: TaskStatus;
  currentStep?: number;
  totalSteps?: number;
  message?: string;
  errorMessage?: string;
}
// endregion

// region 审批
export interface ApprovalRequestVO {
  approvalId: string;
  taskId: string;
  orgId: string;
  workflowId: string;
  userId: string;
  riskLevel: string;
  approvalRoute: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'TIMEOUT';
  timeoutAt?: string;
  createTime?: string;
}

/** 审批超时配置（场景4 用）。 */
export interface ApprovalTimeoutConfigVO {
  riskLevel: string;
  timeoutMinutes: number;
  description?: string;
  updateTime?: string;
}
// endregion

// region 审计
export interface AuditLogVO {
  auditId: string;
  taskId: string;
  orgId: string;
  departmentId?: string;
  businessLineId?: string;
  userId?: string;
  userName?: string;
  actionType: string;
  targetElement?: string;
  pageUrl?: string;
  actionParams?: string;
  executionResult: string;
  errorMessage?: string;
  riskLevel?: string;
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  createTime?: string;
}
// endregion

// region LLM 调用记录
/** LLM 调用记录 VO（对齐 Java LlmCallRecordVO）。 */
export interface LlmCallRecordVO {
  callId: string;
  taskId?: string;
  orgId?: string;
  businessLineId?: string;
  model: string;
  contextName: string;
  retryAttempt: number;
  success: boolean;
  errorMessage?: string;
  durationMs: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  cacheHit: boolean;
  cost: number;
  callTime: string;
}

/** LLM 调用记录上报请求（对齐 Java LlmCallLogCreateRequest，场景2/5 用）。 */
export interface LlmCallLogCreateRequest {
  taskId?: string;
  orgId?: string;
  model: string;
  contextName: string;
  retryAttempt: number;
  success: boolean;
  errorMessage?: string;
  durationMs: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  cacheHit: boolean;
  cost?: number;
  timestamp?: string;
}

/** LLM 风险判断请求（对齐 Python RiskJudgeRequest，场景5 真实调 LLM 用）。 */
export interface RiskJudgeRequest {
  task_id: string;
  org_id: string;
  industry: string;
  goal: string;
  params?: Record<string, string>;
  pre_screen_risk_level: string;
  hit_keywords: string[];
  max_amount?: string;
}

/** LLM 风险判断响应（对齐 Python RiskJudgeResponse）。 */
export interface RiskJudgeResponse {
  task_id: string;
  final_risk_level: 'low' | 'medium' | 'high' | 'critical';
  approval_route: 'auto' | 'department' | 'compliance';
  reasoning?: string;
  confidence?: number;
  model_used?: string;
  latency_ms?: number;
}
// endregion

// region 性能指标
/** 单次测量样本。 */
export interface PerfSample {
  /** 序号 */
  index: number;
  /** 耗时（毫秒） */
  durationMs: number;
  /** 是否成功 */
  success: boolean;
  /** 错误信息（失败时） */
  errorMessage?: string;
  /** 附加元数据 */
  meta?: Record<string, unknown>;
}

/** 性能统计结果。 */
export interface PerfStats {
  /** 样本总数 */
  total: number;
  /** 成功数 */
  success: number;
  /** 失败数 */
  failed: number;
  /** 成功率（0-1） */
  successRate: number;
  /** 最小耗时（毫秒） */
  minMs: number;
  /** 最大耗时（毫秒） */
  maxMs: number;
  /** 平均耗时（毫秒） */
  avgMs: number;
  /** P50 中位数（毫秒） */
  p50Ms: number;
  /** P95（毫秒） */
  p95Ms: number;
  /** P99（毫秒） */
  p99Ms: number;
  /** 总耗时（毫秒） */
  totalMs: number;
  /** 吞吐量（每秒处理数，仅并发场景有意义） */
  throughputPerSec?: number;
}

/** SSE 事件样本。 */
export interface SseEventSample {
  /** 事件类型 */
  event: string;
  /** 收到事件的时间戳（毫秒） */
  receivedAt: number;
  /** 事件数据 */
  data?: string;
  /** 从订阅到收到此事件的延迟（毫秒） */
  latencyFromStartMs?: number;
  /** 事件推送延迟（毫秒，基于 data.timestamp 计算的端到端延迟） */
  latencyMs?: number;
}
// endregion

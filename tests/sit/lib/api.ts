/**
 * Java 后端 API 客户端（M9.5 SIT）。
 *
 * 在 E2E ApiClient 基础上扩展：
 * - 内部回调接口（POST /api/internal/tasks/{id}/state、subtasks、coordination-state）
 * - LLM 内部回调接口（POST /api/internal/llm/calls、needs-human）
 * - NEEDS_HUMAN 队列查询 + 处置接口
 * - 审批超时配置查询 + 更新接口
 * - 任务续跑接口
 *
 * 内部接口（/api/internal/**）使用 X-Internal-Token Header 鉴权（模拟 Python 回调）。
 * 对外接口使用 Bearer JWT 鉴权（登录后调用）。
 *
 * 所有方法返回解包后的 data（BaseResponse.data），失败抛错。
 */
import axios, { AxiosInstance } from 'axios';
import { BACKEND_URL, INTERNAL_TOKEN } from './env';
import type {
  ApprovalActionRequest,
  ApprovalRequestVO,
  ApprovalTimeoutConfigUpdateRequest,
  ApprovalTimeoutConfigVO,
  AuditLogVO,
  CoordinationStateUpdateRequest,
  CoordinationStateVO,
  LoginResponse,
  NeedsHumanQueueVO,
  NeedsHumanReportRequest,
  NeedsHumanResolveRequest,
  OverviewVO,
  SubTaskUpdateRequest,
  TaskDetailVO,
  TaskStateUpdateRequest,
  TaskVO,
  WorkflowRunVO,
  WorkflowVO,
} from './types';

export class ApiClient {
  private http: AxiosInstance;
  private token: string | null = null;

  constructor() {
    this.http = axios.create({ baseURL: BACKEND_URL, timeout: 120_000 });
  }

  /** 设置 JWT token（登录后调用）。 */
  setToken(token: string): void {
    this.token = token;
  }

  /** 清除 token（场景5 隔离测试切换账号时用）。 */
  clearToken(): void {
    this.token = null;
  }

  /** 带鉴权的请求头。 */
  private authHeaders(): Record<string, string> {
    return this.token ? { Authorization: `Bearer ${this.token}` } : {};
  }

  /** 内部回调请求头（X-Internal-Token）。 */
  private internalHeaders(): Record<string, string> {
    return { 'X-Internal-Token': INTERNAL_TOKEN };
  }

  /** 统一 GET：解包 BaseResponse.data，业务错误码非 0 抛异常。 */
  private async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    const resp = await this.http.get(url, { params, headers: this.authHeaders() });
    const body = resp.data;
    if (body?.code !== 0) {
      throw new Error(body?.message ?? `API 错误: GET ${url} code=${body?.code}`);
    }
    return body?.data as T;
  }

  /** 统一 POST：解包 BaseResponse.data，业务错误码非 0 抛异常。 */
  private async post<T>(url: string, body?: unknown): Promise<T> {
    const resp = await this.http.post(url, body, { headers: this.authHeaders() });
    const respBody = resp.data;
    if (respBody?.code !== 0) {
      throw new Error(respBody?.message ?? `API 错误: POST ${url} code=${respBody?.code}`);
    }
    return respBody?.data as T;
  }

  /** 内部 POST（X-Internal-Token 鉴权）：解包 BaseResponse.data，业务错误码非 0 抛异常。 */
  private async internalPost<T>(url: string, body?: unknown): Promise<T> {
    const resp = await this.http.post(url, body, { headers: this.internalHeaders() });
    const respBody = resp.data;
    if (respBody?.code !== 0) {
      throw new Error(respBody?.message ?? `内部 API 错误: POST ${url} code=${respBody?.code}`);
    }
    return respBody?.data as T;
  }

  /** 内部 GET（X-Internal-Token 鉴权）：解包 BaseResponse.data，业务错误码非 0 抛异常。 */
  private async internalGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    const resp = await this.http.get(url, { params, headers: this.internalHeaders() });
    const body = resp.data;
    if (body?.code !== 0) {
      throw new Error(body?.message ?? `内部 API 错误: GET ${url} code=${body?.code}`);
    }
    return body?.data as T;
  }

  // region 认证
  /** 登录（POST /api/auth/login），返回 accessToken 等。业务错误码非 0 抛异常。 */
  async login(username: string, password: string): Promise<LoginResponse> {
    const resp = await this.http.post('/api/auth/login', { username, password });
    const body = resp.data;
    if (body?.code !== 0) {
      throw new Error(body?.message ?? `登录失败: code=${body?.code}`);
    }
    return body?.data as LoginResponse;
  }
  // endregion

  // region 工作流
  /** 按名称模糊查询工作流模板（GET /api/workflows）。 */
  async listWorkflows(name?: string): Promise<WorkflowVO[]> {
    const page = await this.get<{ records: WorkflowVO[] }>(
      '/api/workflows',
      { name, current: 1, pageSize: 100 },
    );
    return page?.records ?? [];
  }

  /** 按名称精确查找工作流模板（雪花 ID 每次部署变化，必须按 name 查）。 */
  async findWorkflowByName(name: string): Promise<WorkflowVO> {
    const list = await this.listWorkflows(name);
    const exact = list.find((w) => w.name === name);
    if (!exact) {
      throw new Error(`工作流模板未找到: ${name}（模糊查询返回 ${list.length} 条）`);
    }
    return exact;
  }

  /** 触发工作流执行（POST /api/workflows/{workflowId}/run）。 */
  async runWorkflow(workflowId: string, params: Record<string, string>): Promise<WorkflowRunVO> {
    return this.post<WorkflowRunVO>(`/api/workflows/${workflowId}/run`, { params });
  }
  // endregion

  // region 任务（对外）
  /** 任务详情（含子任务，GET /api/tasks/{taskId}）。 */
  async getTask(taskId: string): Promise<TaskDetailVO> {
    return this.get<TaskDetailVO>(`/api/tasks/${taskId}`);
  }

  /** 任务列表（GET /api/tasks）。 */
  async listTasks(workflowId?: string): Promise<TaskVO[]> {
    const page = await this.get<{ records: TaskVO[] }>(
      '/api/tasks',
      { workflowId, current: 1, pageSize: 100 },
    );
    return page?.records ?? [];
  }

  /** 终止任务（POST /api/tasks/{taskId}/abort）。 */
  async abortTask(taskId: string): Promise<boolean> {
    return this.post<boolean>(`/api/tasks/${taskId}/abort`);
  }

  /** 任务续跑（POST /api/tasks/{taskId}/resume，M4.3）。 */
  async resumeTask(taskId: string): Promise<boolean> {
    return this.post<boolean>(`/api/tasks/${taskId}/resume`);
  }

  /** 查询协调状态（GET /api/tasks/{taskId}/coordination-state，场景3 验证持久化用）。 */
  async getCoordinationState(taskId: string): Promise<CoordinationStateVO | null> {
    try {
      return await this.get<CoordinationStateVO>(`/api/tasks/${taskId}/coordination-state`);
    } catch {
      // 接口可能不存在或返回 404，返回 null 让上层处理
      return null;
    }
  }
  // endregion

  // region 任务内部回调（Python → Java，模拟用）
  /** 更新任务状态（POST /api/internal/tasks/{taskId}/state）。 */
  async internalUpdateTaskState(taskId: string, request: TaskStateUpdateRequest): Promise<boolean> {
    return this.internalPost<boolean>(`/api/internal/tasks/${taskId}/state`, request);
  }

  /** 更新子任务状态（POST /api/internal/tasks/{taskId}/subtasks）。 */
  async internalUpdateSubTask(taskId: string, request: SubTaskUpdateRequest): Promise<boolean> {
    return this.internalPost<boolean>(`/api/internal/tasks/${taskId}/subtasks`, request);
  }

  /** 更新协调状态（POST /api/internal/tasks/{taskId}/coordination-state，M4.2）。 */
  async internalUpdateCoordinationState(
    taskId: string,
    request: CoordinationStateUpdateRequest,
  ): Promise<boolean> {
    return this.internalPost<boolean>(`/api/internal/tasks/${taskId}/coordination-state`, request);
  }

  /** 查询审批结果（GET /api/internal/approvals/{taskId}/result，M6.3，Python 回调用）。 */
  async internalGetApprovalResult(taskId: string): Promise<{
    approvalStatus: string;
    approverId?: number;
    reason?: string;
    timeoutAt?: string;
  } | null> {
    return this.internalGet(`/api/internal/approvals/${taskId}/result`);
  }
  // endregion

  // region 审批
  /** 按 taskId 查审批单（GET /api/approvals?taskId=xxx）。 */
  async listApprovalsByTaskId(taskId: string): Promise<ApprovalRequestVO[]> {
    const page = await this.get<{ records: ApprovalRequestVO[] }>(
      '/api/approvals',
      { taskId, current: 1, pageSize: 10 },
    );
    return page?.records ?? [];
  }

  /** 审批通过（POST /api/approvals/{approvalId}/approve）。 */
  async approve(approvalId: string, reason?: string): Promise<ApprovalRequestVO> {
    const body: ApprovalActionRequest = { reason };
    return this.post<ApprovalRequestVO>(`/api/approvals/${approvalId}/approve`, body);
  }

  /** 审批拒绝（POST /api/approvals/{approvalId}/reject）。 */
  async reject(approvalId: string, reason?: string): Promise<ApprovalRequestVO> {
    const body: ApprovalActionRequest = { reason };
    return this.post<ApprovalRequestVO>(`/api/approvals/${approvalId}/reject`, body);
  }
  // endregion

  // region 审批超时配置（场景4 用）
  /** 查询全部超时配置（GET /api/approval-timeout）。 */
  async listApprovalTimeoutConfigs(): Promise<ApprovalTimeoutConfigVO[]> {
    return this.get<ApprovalTimeoutConfigVO[]>('/api/approval-timeout');
  }

  /** 更新指定风险等级的超时配置（PUT /api/approval-timeout/{riskLevel}）。 */
  async updateApprovalTimeoutConfig(
    riskLevel: string,
    request: ApprovalTimeoutConfigUpdateRequest,
  ): Promise<ApprovalTimeoutConfigVO> {
    const resp = await this.http.put(
      `/api/approval-timeout/${riskLevel}`,
      request,
      { headers: this.authHeaders() },
    );
    return resp.data?.data as ApprovalTimeoutConfigVO;
  }
  // endregion

  // region NEEDS_HUMAN 队列
  /** 分页查询 NEEDS_HUMAN 队列（GET /api/llm/needs-human）。 */
  async listNeedsHuman(taskId?: string): Promise<NeedsHumanQueueVO[]> {
    const params: Record<string, unknown> = { current: 1, pageSize: 100 };
    if (taskId !== undefined) params.taskId = taskId;
    const list = await this.get<NeedsHumanQueueVO[]>('/api/llm/needs-human', params);
    return list ?? [];
  }

  /** 查询 NEEDS_HUMAN 事件详情（GET /api/llm/needs-human/{queueId}）。 */
  async getNeedsHumanDetail(queueId: string): Promise<NeedsHumanQueueVO> {
    return this.get<NeedsHumanQueueVO>(`/api/llm/needs-human/${queueId}`);
  }

  /** 处置 NEEDS_HUMAN 事件（POST /api/llm/needs-human/{queueId}/resolve）。 */
  async resolveNeedsHuman(queueId: string, request: NeedsHumanResolveRequest): Promise<boolean> {
    return this.post<boolean>(`/api/llm/needs-human/${queueId}/resolve`, request);
  }
  // endregion

  // region LLM 内部回调（Python → Java，模拟用）
  /** 上报 NEEDS_HUMAN 事件入队（POST /api/internal/llm/needs-human，模拟 Python 回调）。 */
  async internalReportNeedsHuman(request: NeedsHumanReportRequest): Promise<boolean> {
    return this.internalPost<boolean>('/api/internal/llm/needs-human', request);
  }

  /** 上报 LLM 调用记录（POST /api/internal/llm/calls，模拟 Python 回调）。 */
  async internalReportLlmCall(request: {
    taskId: string;
    orgId: string;
    model: string;
    contextName: string;
    success: boolean;
    retryAttempt: number;
    latencyMs: number;
    tokensUsed?: number;
    cost?: number;
    errorMessage?: string;
  }): Promise<boolean> {
    return this.internalPost<boolean>('/api/internal/llm/calls', request);
  }
  // endregion

  // region 审计
  /** 按 taskId 查审计日志（GET /api/v1/audit/logs?taskId=xxx）。 */
  async listAuditLogsByTaskId(taskId: string): Promise<AuditLogVO[]> {
    const page = await this.get<{ records: AuditLogVO[] }>(
      '/api/v1/audit/logs',
      { taskId, current: 1, pageSize: 100 },
    );
    return page?.records ?? [];
  }
  // endregion

  // region 大屏
  /** 大屏概览（GET /api/v1/dashboard/overview）。 */
  async getOverview(): Promise<OverviewVO> {
    return this.get<OverviewVO>('/api/v1/dashboard/overview');
  }
  // endregion
}

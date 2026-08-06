/**
 * Java 后端 + Python finance-ai API 客户端（M9.2 性能测试）。
 *
 * 在 SIT ApiClient 基础上扩展：
 * - LLM 调用记录分页查询（GET /api/llm/calls）
 * - LLM 调用记录统计（GET /api/llm/calls/stats）
 * - LLM 风险判断（POST Python /api/v1/ai/risk/judge，真实调 LLM）
 * - 审计日志分页查询（GET /api/v1/audit/logs，百万级数据压测用）
 * - 大屏概览（GET /api/v1/dashboard/overview）
 *
 * 内部接口（/api/internal/**）用 X-Internal-Token 鉴权（场景2 模拟并发回调用）。
 * 对外接口用 Bearer JWT 鉴权。
 */
import axios, { AxiosInstance } from 'axios';
import { AI_URL, BACKEND_URL, INTERNAL_TOKEN } from './env';
import type {
  AuditLogVO,
  BaseResponse,
  IPage,
  LlmCallLogCreateRequest,
  LlmCallRecordVO,
  LoginResponse,
  RiskJudgeRequest,
  RiskJudgeResponse,
  TaskDetailVO,
  TaskStateUpdateRequest,
  TaskVO,
  WorkflowRunVO,
  WorkflowVO,
} from './types';

export class ApiClient {
  private http: AxiosInstance;
  private aiHttp: AxiosInstance;
  private token: string | null = null;

  constructor() {
    this.http = axios.create({ baseURL: BACKEND_URL, timeout: 120_000 });
    this.aiHttp = axios.create({ baseURL: AI_URL, timeout: 60_000 });
  }

  /** 设置 JWT token（登录后调用）。 */
  setToken(token: string): void {
    this.token = token;
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
    const body = resp.data as BaseResponse<T>;
    if (body?.code !== 0) {
      throw new Error(body?.message ?? `API 错误: GET ${url} code=${body?.code}`);
    }
    return body.data;
  }

  /** 统一 POST：解包 BaseResponse.data，业务错误码非 0 抛异常。 */
  private async post<T>(url: string, body?: unknown): Promise<T> {
    const resp = await this.http.post(url, body, { headers: this.authHeaders() });
    const respBody = resp.data as BaseResponse<T>;
    if (respBody?.code !== 0) {
      throw new Error(respBody?.message ?? `API 错误: POST ${url} code=${respBody?.code}`);
    }
    return respBody.data;
  }

  /** 内部 POST（X-Internal-Token 鉴权）。 */
  private async internalPost<T>(url: string, body?: unknown): Promise<T> {
    const resp = await this.http.post(url, body, { headers: this.internalHeaders() });
    const respBody = resp.data as BaseResponse<T>;
    if (respBody?.code !== 0) {
      throw new Error(respBody?.message ?? `内部 API 错误: POST ${url} code=${respBody?.code}`);
    }
    return respBody.data;
  }

  // region 认证
  /** 登录（POST /api/auth/login）。 */
  async login(username: string, password: string): Promise<LoginResponse> {
    const resp = await this.http.post('/api/auth/login', { username, password });
    const body = resp.data as BaseResponse<LoginResponse>;
    if (body?.code !== 0) {
      throw new Error(body?.message ?? `登录失败: code=${body?.code}`);
    }
    return body.data;
  }
  // endregion

  // region 工作流
  /** 按名称模糊查询工作流模板。 */
  async listWorkflows(name?: string): Promise<WorkflowVO[]> {
    const page = await this.get<IPage<WorkflowVO>>('/api/workflows', {
      name,
      current: 1,
      pageSize: 100,
    });
    return page?.records ?? [];
  }

  /** 按名称精确查找工作流模板。 */
  async findWorkflowByName(name: string): Promise<WorkflowVO> {
    const list = await this.listWorkflows(name);
    const exact = list.find((w) => w.name === name);
    if (!exact) {
      throw new Error(`工作流模板未找到: ${name}`);
    }
    return exact;
  }

  /** 触发工作流执行（POST /api/workflows/{workflowId}/run）。 */
  async runWorkflow(workflowId: string, params: Record<string, string>): Promise<WorkflowRunVO> {
    return this.post<WorkflowRunVO>(`/api/workflows/${workflowId}/run`, { params });
  }
  // endregion

  // region 任务
  /** 任务详情（GET /api/tasks/{taskId}）。 */
  async getTask(taskId: string): Promise<TaskDetailVO> {
    return this.get<TaskDetailVO>(`/api/tasks/${taskId}`);
  }

  /** 任务列表（GET /api/tasks）。 */
  async listTasks(workflowId?: string): Promise<TaskVO[]> {
    const page = await this.get<IPage<TaskVO>>('/api/tasks', {
      workflowId,
      current: 1,
      pageSize: 100,
    });
    return page?.records ?? [];
  }

  /** 终止任务（POST /api/tasks/{taskId}/abort）。 */
  async abortTask(taskId: string): Promise<boolean> {
    return this.post<boolean>(`/api/tasks/${taskId}/abort`);
  }

  /** 内部回调：更新任务状态（POST /api/internal/tasks/{taskId}/state，模拟 Python 回调）。 */
  async internalUpdateTaskState(taskId: string, request: TaskStateUpdateRequest): Promise<boolean> {
    return this.internalPost<boolean>(`/api/internal/tasks/${taskId}/state`, request);
  }
  // endregion

  // region 审计
  /** 分页查询审计日志（GET /api/v1/audit/logs，百万级数据压测用）。 */
  async listAuditLogs(params: {
    taskId?: string;
    userId?: string;
    riskLevel?: string;
    actionType?: string;
    startTime?: string;
    endTime?: string;
    current?: number;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
  }): Promise<{ records: AuditLogVO[]; total: number; size: number; current: number; pages: number }> {
    return this.get('/api/v1/audit/logs', params);
  }
  // endregion

  // region LLM 调用记录
  /** 分页查询 LLM 调用记录（GET /api/llm/calls，按 call_time 倒序）。 */
  async listLlmCallRecords(params: {
    startTime?: string;
    endTime?: string;
    model?: string;
    taskId?: string;
    cacheHit?: boolean;
    current?: number;
    pageSize?: number;
  }): Promise<{ records: LlmCallRecordVO[]; total: number }> {
    return this.get('/api/llm/calls', params);
  }

  /** 内部回调：上报 LLM 调用记录（POST /api/internal/llm/calls，场景2/5 用）。 */
  async internalReportLlmCall(request: LlmCallLogCreateRequest): Promise<boolean> {
    return this.internalPost<boolean>('/api/internal/llm/calls', request);
  }
  // endregion

  // region LLM 风险判断（直连 Python，真实调 LLM）
  /**
   * 触发 LLM 风险判断（POST Python /api/v1/ai/risk/judge，场景5 用）。
   *
   * 真实调用火山方舟豆包，产生 tokens 消耗与费用。
   */
  async judgeRisk(request: RiskJudgeRequest): Promise<RiskJudgeResponse> {
    const resp = await this.aiHttp.post('/api/v1/ai/risk/judge', request);
    return resp.data as RiskJudgeResponse;
  }
  // endregion

  // region 大屏
  /** 大屏概览（GET /api/v1/dashboard/overview）。 */
  async getOverview(): Promise<{
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
  }> {
    return this.get('/api/v1/dashboard/overview');
  }
  // endregion
}

/**
 * Java 后端 API 客户端（M9.1）。
 *
 * 封装 E2E 全链路所需接口：登录 / 工作流 / 任务 / 审批 / 审计 / 大屏。
 * 路径对齐 Java context-path=/api（审计/大屏自带 /v1 前缀）。
 * 所有方法返回解包后的 data（BaseResponse.data），失败抛错。
 */
import axios, { AxiosInstance } from 'axios';
import { BACKEND_URL } from './env';
import type {
  ApprovalActionRequest,
  ApprovalRequestVO,
  AuditLogVO,
  LoginResponse,
  OverviewVO,
  TaskDetailVO,
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

  /** 带鉴权的请求头。 */
  private authHeaders(): Record<string, string> {
    return this.token ? { Authorization: `Bearer ${this.token}` } : {};
  }

  /** 统一 GET：解包 BaseResponse.data。 */
  private async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    const resp = await this.http.get(url, { params, headers: this.authHeaders() });
    return resp.data?.data as T;
  }

  /** 统一 POST：解包 BaseResponse.data。 */
  private async post<T>(url: string, body?: unknown): Promise<T> {
    const resp = await this.http.post(url, body, { headers: this.authHeaders() });
    return resp.data?.data as T;
  }

  // region 认证
  /** 登录（POST /api/auth/login），返回 accessToken 等。 */
  async login(username: string, password: string): Promise<LoginResponse> {
    const resp = await this.http.post('/api/auth/login', { username, password });
    return resp.data?.data as LoginResponse;
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
  async runWorkflow(workflowId: number, params: Record<string, string>): Promise<WorkflowRunVO> {
    return this.post<WorkflowRunVO>(`/api/workflows/${workflowId}/run`, { params });
  }
  // endregion

  // region 任务
  /** 任务详情（含子任务，GET /api/tasks/{taskId}）。 */
  async getTask(taskId: number): Promise<TaskDetailVO> {
    return this.get<TaskDetailVO>(`/api/tasks/${taskId}`);
  }

  /** 任务列表（GET /api/tasks）。 */
  async listTasks(workflowId?: number): Promise<TaskVO[]> {
    const page = await this.get<{ records: TaskVO[] }>(
      '/api/tasks',
      { workflowId, current: 1, pageSize: 100 },
    );
    return page?.records ?? [];
  }
  // endregion

  // region 审批
  /** 按 taskId 查审批单（GET /api/approvals?taskId=xxx）。 */
  async listApprovalsByTaskId(taskId: number): Promise<ApprovalRequestVO[]> {
    const page = await this.get<{ records: ApprovalRequestVO[] }>(
      '/api/approvals',
      { taskId, current: 1, pageSize: 10 },
    );
    return page?.records ?? [];
  }

  /** 审批通过（POST /api/approvals/{approvalId}/approve）。任意登录用户均可审批。 */
  async approve(approvalId: number, reason?: string): Promise<ApprovalRequestVO> {
    const body: ApprovalActionRequest = { reason };
    return this.post<ApprovalRequestVO>(`/api/approvals/${approvalId}/approve`, body);
  }

  /** 审批拒绝（POST /api/approvals/{approvalId}/reject）。 */
  async reject(approvalId: number, reason?: string): Promise<ApprovalRequestVO> {
    const body: ApprovalActionRequest = { reason };
    return this.post<ApprovalRequestVO>(`/api/approvals/${approvalId}/reject`, body);
  }
  // endregion

  // region 审计
  /** 按 taskId 查审计日志（GET /api/v1/audit/logs?taskId=xxx）。 */
  async listAuditLogsByTaskId(taskId: number): Promise<AuditLogVO[]> {
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

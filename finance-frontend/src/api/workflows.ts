/**
 * 工作流模板管理 API 封装
 *
 * 对齐后端 com.finrpa.workflows.controller.WorkflowController：
 * - GET    /workflows                 分页查询模板列表（支持行业/风险等级筛选与名称搜索）
 * - GET    /workflows/{workflowId}    查询模板详情
 * - POST   /workflows                 创建工作流模板
 * - PUT    /workflows/{workflowId}    更新模板
 * - DELETE /workflows/{workflowId}    删除模板（逻辑删除）
 * - POST   /workflows/{workflowId}/run 触发执行（加载模板 → 参数映射 → 创建任务 → 调 Python）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  IPage,
  WorkflowIndustry,
  WorkflowParam,
  WorkflowQueryRequest,
  WorkflowRiskLevel,
  WorkflowRunRequest,
  WorkflowRunVO,
  WorkflowStep,
  WorkflowVO,
} from './types'

/**
 * 分页查询工作流模板列表
 *
 * @param query 查询请求（分页 + 行业/风险等级筛选 + 名称搜索）
 * @returns 模板分页列表
 */
export async function listWorkflows(
  query: WorkflowQueryRequest,
): Promise<IPage<WorkflowVO>> {
  // 1. GET 查询参数通过 params 传递
  const res = await axiosClient.get<BaseResponse<IPage<WorkflowVO>>>('/workflows', {
    params: {
      current: query.current,
      pageSize: query.pageSize,
      name: query.name || undefined,
      industry: query.industry || undefined,
      riskLevel: query.riskLevel || undefined,
      enabled: query.enabled === '' ? undefined : query.enabled,
    },
  })
  return res.data.data
}

/**
 * 查询工作流模板详情
 *
 * @param workflowId 工作流业务 ID
 * @returns 模板视图
 */
export async function getWorkflow(workflowId: string): Promise<WorkflowVO> {
  const res = await axiosClient.get<BaseResponse<WorkflowVO>>(
    `/workflows/${workflowId}`,
  )
  return res.data.data
}

/**
 * 触发工作流执行
 *
 * @param workflowId 工作流业务 ID
 * @param payload   运行参数键值对
 * @returns 执行结果（含 taskId 与初始状态）
 */
export async function runWorkflow(
  workflowId: string,
  payload: WorkflowRunRequest,
): Promise<WorkflowRunVO> {
  const res = await axiosClient.post<BaseResponse<WorkflowRunVO>>(
    `/workflows/${workflowId}/run`,
    payload,
  )
  return res.data.data
}

/**
 * 批量创建任务
 *
 * 将一组用户数据（前端解析 CSV / 粘贴多行得到的 rows）按 columnMapping 映射为
 * 同一工作流模板参数，后端逐条生成任务，消灭重复手动录入。
 *
 * @param payload 批量请求（workflowId + columnMapping + rows）
 * @returns 批量结果（含每条明细）
 */
export async function batchCreateTasks(payload: BatchTaskRequest): Promise<BatchTaskResultVO> {
  const res = await axiosClient.post<BaseResponse<BatchTaskResultVO>>(
    '/batch-tasks',
    payload,
  )
  return res.data.data
}

/** 工作流 API 聚合导出 */
export const workflowApi = {
  listWorkflows,
  getWorkflow,
  runWorkflow,
  batchCreateTasks,
}

// ============================================================
// 工具函数：解析模板的 params / steps JSON 字符串
// ============================================================

/**
 * 解析工作流参数定义
 *
 * @param paramsJson 参数定义 JSON 字符串
 * @returns 参数定义数组（解析失败返回空数组）
 */
export function parseWorkflowParams(paramsJson: string): WorkflowParam[] {
  if (!paramsJson) return []
  try {
    const parsed = JSON.parse(paramsJson)
    if (!Array.isArray(parsed)) return []
    return parsed as WorkflowParam[]
  } catch {
    return []
  }
}

/**
 * 解析工作流步骤定义
 *
 * @param stepsJson 步骤 JSON 字符串
 * @returns 步骤数组（解析失败返回空数组）
 */
export function parseWorkflowSteps(stepsJson: string): WorkflowStep[] {
  if (!stepsJson) return []
  try {
    const parsed = JSON.parse(stepsJson)
    if (!Array.isArray(parsed)) return []
    return parsed as WorkflowStep[]
  } catch {
    return []
  }
}

/**
 * 行业枚举值 → 中文标签
 */
export const INDUSTRY_LABELS: Record<WorkflowIndustry, string> = {
  banking: '银行',
  insurance: '保险',
  securities: '证券',
}

/**
 * 风险等级枚举值 → 中文标签
 */
export const RISK_LEVEL_LABELS: Record<WorkflowRiskLevel, string> = {
  low: '低',
  medium: '中',
  high: '高',
  critical: '极高',
}

/**
 * Skill 英文名 → 中文标签（对齐后端 SkillConstant 7 个内置 Skill）
 *
 * 工作流步骤展示用中文替换 skill 代码名，避免暴露开发层标识符。
 */
export const SKILL_LABELS: Record<string, string> = {
  login: '登录',
  session_keep_alive: '会话保活',
  form_fill: '表单填充',
  search_and_select: '搜索选择',
  pagination: '分页遍历',
  table_extract: '表格提取',
  file_download: '文件下载',
}

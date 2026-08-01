/**
 * 任务管理 API 封装
 *
 * 对齐后端 com.finrpa.agent.controller.TaskController + com.finrpa.ai.controller.AiProxyController：
 * - GET  /tasks                分页查询任务列表
 * - GET  /tasks/{taskId}       查询任务详情（含子任务列表）
 * - POST /tasks/{taskId}/abort 终止任务
 * - POST /tasks/{taskId}/resume 任务续跑（M4.3：从断点继续）
 * - POST /ai/tasks             触发任务执行（前端 → Java → Python）
 * - GET  /ai/tasks/{taskId}/state  查询任务状态（Python 透传）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  IPage,
  TaskDetailVO,
  TaskQueryRequest,
  TaskTriggerRequest,
  TaskTriggerResponse,
  TaskVO,
} from './types'

/**
 * 分页查询任务列表
 *
 * @param query 查询请求（分页 + 状态筛选 + 关键词搜索）
 * @returns 任务分页列表
 */
export async function listTasks(query: TaskQueryRequest): Promise<IPage<TaskVO>> {
  // 1. GET 查询参数通过 params 传递
  const res = await axiosClient.get<BaseResponse<IPage<TaskVO>>>('/tasks', {
    params: {
      current: query.current,
      pageSize: query.pageSize,
      sortField: query.sortField,
      sortOrder: query.sortOrder,
      status: query.status || undefined,
      searchText: query.searchText || undefined,
      workflowId: query.workflowId || undefined,
    },
  })
  return res.data.data
}

/**
 * 查询任务详情（含子任务列表）
 *
 * @param taskId 任务 ID
 * @returns 任务详情
 */
export async function getTaskDetail(taskId: string): Promise<TaskDetailVO> {
  const res = await axiosClient.get<BaseResponse<TaskDetailVO>>(`/tasks/${taskId}`)
  return res.data.data
}

/**
 * 终止任务
 *
 * @param taskId 任务 ID
 * @returns 操作结果
 */
export async function abortTask(taskId: string): Promise<boolean> {
  const res = await axiosClient.post<BaseResponse<boolean>>(`/tasks/${taskId}/abort`)
  return res.data.data
}

/**
 * 任务续跑（M4.3：从断点继续执行失败或需人工介入的任务）
 *
 * 后端校验：仅 FAILED / NEEDS_HUMAN 状态可续跑，
 * 读取 rpa_agent_coordination_state 中已存计划 + completed_subtasks，
 * 调 Python POST /api/v1/ai/tasks/{taskId}/resume 从断点继续。
 *
 * @param taskId 任务 ID
 * @returns 操作结果
 */
export async function resumeTask(taskId: string): Promise<boolean> {
  const res = await axiosClient.post<BaseResponse<boolean>>(`/tasks/${taskId}/resume`)
  return res.data.data
}

/**
 * 触发任务执行（前端 → Java 持久化 → Python 执行）
 *
 * @param payload 任务触发请求（goal / params / workflowId）
 * @returns 任务触发响应（含雪花 taskId）
 */
export async function triggerTask(
  payload: TaskTriggerRequest,
): Promise<TaskTriggerResponse> {
  const res = await axiosClient.post<BaseResponse<TaskTriggerResponse>>(
    '/ai/tasks',
    payload,
  )
  return res.data.data
}

/** 任务 API 聚合导出 */
export const taskApi = {
  listTasks,
  getTaskDetail,
  abortTask,
  resumeTask,
  triggerTask,
}

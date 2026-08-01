/**
 * NEEDS_HUMAN 队列管理 API 封装
 *
 * 对齐后端 com.finrpa.llm.controller.NeedsHumanController：
 * - GET  /llm/needs-human                分页查询 NEEDS_HUMAN 队列
 * - GET  /llm/needs-human/{queueId}      查询事件详情
 * - POST /llm/needs-human/{queueId}/resolve 处置事件（skip/manual/abort）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  NeedsHumanQueueVO,
  NeedsHumanQueryRequest,
  NeedsHumanResolveRequest,
} from './types'

/**
 * 分页查询 NEEDS_HUMAN 队列
 *
 * @param query 查询请求
 * @returns 队列列表
 */
export async function listNeedsHuman(
  query: NeedsHumanQueryRequest,
): Promise<NeedsHumanQueueVO[]> {
  const res = await axiosClient.get<BaseResponse<NeedsHumanQueueVO[]>>(
    '/llm/needs-human',
    {
      params: {
        current: query.current,
        pageSize: query.pageSize,
        status: query.status || undefined,
        taskId: query.taskId || undefined,
      },
    },
  )
  return res.data.data
}

/**
 * 查询 NEEDS_HUMAN 事件详情
 *
 * @param queueId 队列业务 ID
 * @returns 事件详情
 */
export async function getNeedsHumanDetail(
  queueId: string,
): Promise<NeedsHumanQueueVO> {
  const res = await axiosClient.get<BaseResponse<NeedsHumanQueueVO>>(
    `/llm/needs-human/${queueId}`,
  )
  return res.data.data
}

/**
 * 处置 NEEDS_HUMAN 事件
 *
 * @param queueId 队列业务 ID
 * @param payload 处置请求（action: skip/manual/abort）
 * @returns 操作结果
 */
export async function resolveNeedsHuman(
  queueId: string,
  payload: NeedsHumanResolveRequest,
): Promise<boolean> {
  const res = await axiosClient.post<BaseResponse<boolean>>(
    `/llm/needs-human/${queueId}/resolve`,
    payload,
  )
  return res.data.data
}

/** NEEDS_HUMAN API 聚合导出 */
export const needsHumanApi = {
  listNeedsHuman,
  getNeedsHumanDetail,
  resolveNeedsHuman,
}

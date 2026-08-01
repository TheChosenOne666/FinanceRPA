/**
 * LLM 调用统计 API 封装
 *
 * 对齐后端 com.finrpa.llm.controller.LlmCallLogController：
 * - GET /llm/calls/stats 查询 LLM 调用统计
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  LlmCallStatsQueryRequest,
  LlmCallStatsVO,
} from './types'

/**
 * 查询 LLM 调用统计
 *
 * @param query 查询请求（startTime / endTime / model / taskId 均可选）
 * @returns 聚合统计结果
 */
export async function getCallStats(
  query?: LlmCallStatsQueryRequest,
): Promise<LlmCallStatsVO> {
  const res = await axiosClient.get<BaseResponse<LlmCallStatsVO>>(
    '/llm/calls/stats',
    {
      params: {
        startTime: query?.startTime || undefined,
        endTime: query?.endTime || undefined,
        model: query?.model || undefined,
        taskId: query?.taskId || undefined,
      },
    },
  )
  return res.data.data
}

/** LLM 监控 API 聚合导出 */
export const llmMonitorApi = {
  getCallStats,
}

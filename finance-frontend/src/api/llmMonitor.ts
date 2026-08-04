/**
 * LLM 调用统计 API 封装
 *
 * 对齐后端 com.finrpa.llm.controller.LlmCallLogController：
 * - GET /llm/calls/stats 查询 LLM 调用统计（含环比趋势）
 * - GET /llm/calls 分页查询 LLM 调用记录
 * - GET /llm/calls/daily-trend 查询按日聚合趋势
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  IPage,
  LlmCallDailyTrendVO,
  LlmCallRecordQueryRequest,
  LlmCallRecordVO,
  LlmCallStatsQueryRequest,
  LlmCallStatsVO,
} from './types'

/**
 * 查询 LLM 调用统计（含环比趋势）
 *
 * @param query 查询请求（startTime / endTime / model / taskId / businessLineId 均可选）
 * @returns 聚合统计结果（含趋势字段）
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
        businessLineId: query?.businessLineId || undefined,
      },
    },
  )
  return res.data.data
}

/**
 * 分页查询 LLM 调用记录（P3 ai-monitoring 原型对齐：调用记录列表）
 *
 * @param query 分页查询请求
 * @returns 分页结果（按 call_time 倒序）
 */
export async function listCallRecords(
  query: LlmCallRecordQueryRequest,
): Promise<IPage<LlmCallRecordVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<LlmCallRecordVO>>>(
    '/llm/calls',
    {
      params: {
        current: query.current,
        pageSize: query.pageSize,
        startTime: query.startTime || undefined,
        endTime: query.endTime || undefined,
        model: query.model || undefined,
        taskId: query.taskId || undefined,
        businessLineId: query.businessLineId || undefined,
        cacheHit: query.cacheHit,
      },
    },
  )
  return res.data.data
}

/**
 * 查询按日聚合趋势（P3 ai-monitoring 原型对齐：成本趋势 7 日折线图）
 *
 * @param query 查询请求（用 startTime/endTime/businessLineId 筛选）
 * @returns 按日期升序的每日聚合数据列表
 */
export async function getDailyTrend(
  query?: LlmCallStatsQueryRequest,
): Promise<LlmCallDailyTrendVO[]> {
  const res = await axiosClient.get<BaseResponse<LlmCallDailyTrendVO[]>>(
    '/llm/calls/daily-trend',
    {
      params: {
        startTime: query?.startTime || undefined,
        endTime: query?.endTime || undefined,
        model: query?.model || undefined,
        taskId: query?.taskId || undefined,
        businessLineId: query?.businessLineId || undefined,
      },
    },
  )
  return res.data.data
}

/** LLM 监控 API 聚合导出 */
export const llmMonitorApi = {
  getCallStats,
  listCallRecords,
  getDailyTrend,
}

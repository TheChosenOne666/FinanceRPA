/**
 * 运营大屏 API 封装
 *
 * 对齐后端 com.finrpa.dashboard.controller.DashboardController：
 * - GET /v1/dashboard/overview        概览（任务/性能/LLM/人工/风险 五类汇总）
 * - GET /v1/dashboard/trends          趋势（任务量 + 成本按日聚合）
 * - GET /v1/dashboard/business-lines  业务线分布 + 成功率
 * - GET /v1/dashboard/errors          错误类型分布 Top 10
 * - GET /v1/dashboard/costs           LLM 成本统计（按模型）
 * - GET /v1/dashboard/approvals       审批统计（响应时长/超时数）
 *
 * 说明：
 * - orgId 由后端从 TenantContext 自动填充，前端无需传递
 * - 后端 Redis 缓存：实时指标 TTL 5min，趋势 TTL 1h，任务终态主动失效
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  ApprovalStatVO,
  BaseResponse,
  BusinessLineStatVO,
  CostStatVO,
  ErrorTypeStatVO,
  OverviewVO,
  TrendsVO,
} from './types'

/**
 * 获取概览指标（任务 / 性能 / LLM / 人工 / 风险 五类汇总）
 *
 * @returns 概览 VO
 */
export async function getOverview(): Promise<OverviewVO> {
  const res = await axiosClient.get<BaseResponse<OverviewVO>>(
    '/v1/dashboard/overview',
  )
  return res.data.data
}

/**
 * 获取趋势指标（任务量 + 成本按日聚合）
 *
 * @param days 天数（最近 N 天，默认 7，最大 90）
 * @returns 趋势 VO
 */
export async function getTrends(days?: number): Promise<TrendsVO> {
  const res = await axiosClient.get<BaseResponse<TrendsVO>>(
    '/v1/dashboard/trends',
    { params: days != null ? { days } : undefined },
  )
  return res.data.data
}

/**
 * 获取各业务线任务分布 + 成功率
 *
 * @returns 业务线统计列表
 */
export async function getBusinessLines(): Promise<BusinessLineStatVO[]> {
  const res = await axiosClient.get<BaseResponse<BusinessLineStatVO[]>>(
    '/v1/dashboard/business-lines',
  )
  return res.data.data
}

/**
 * 获取错误类型分布 Top 10
 *
 * @returns 错误类型统计列表
 */
export async function getErrors(): Promise<ErrorTypeStatVO[]> {
  const res = await axiosClient.get<BaseResponse<ErrorTypeStatVO[]>>(
    '/v1/dashboard/errors',
  )
  return res.data.data
}

/**
 * 获取 LLM 成本统计（含按模型维度）
 *
 * @returns 成本统计 VO
 */
export async function getCosts(): Promise<CostStatVO> {
  const res = await axiosClient.get<BaseResponse<CostStatVO>>(
    '/v1/dashboard/costs',
  )
  return res.data.data
}

/**
 * 获取审批统计（响应时长 / 超时数）
 *
 * @returns 审批统计 VO
 */
export async function getApprovals(): Promise<ApprovalStatVO> {
  const res = await axiosClient.get<BaseResponse<ApprovalStatVO>>(
    '/v1/dashboard/approvals',
  )
  return res.data.data
}

/** 大屏 API 聚合导出 */
export const dashboardApi = {
  getOverview,
  getTrends,
  getBusinessLines,
  getErrors,
  getCosts,
  getApprovals,
}

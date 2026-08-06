/**
 * 审批管理 API 封装
 *
 * 对齐后端 com.finrpa.approval.controller.ApprovalController：
 * - GET  /approvals                       分页查询审批列表
 * - GET  /approvals/{approvalId}          查询审批详情
 * - POST /approvals/{approvalId}/approve  审批通过
 * - POST /approvals/{approvalId}/reject   审批拒绝
 *
 * 说明：approve / reject 的 approverId 由后端从登录上下文获取，前端无需传递。
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  ApprovalActionRequest,
  ApprovalQueryRequest,
  ApprovalRequestVO,
  BaseResponse,
  IPage,
} from './types'

/**
 * 分页查询审批列表
 *
 * @param query 查询请求（分页 + 状态 / 路由 / 风险等级筛选）
 * @returns 审批分页列表
 */
export async function listApprovals(
  query: ApprovalQueryRequest,
): Promise<IPage<ApprovalRequestVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<ApprovalRequestVO>>>('/approvals', {
    params: {
      current: query.current,
      pageSize: query.pageSize,
      status: query.status || undefined,
      approvalRoute: query.approvalRoute || undefined,
      riskLevel: query.riskLevel || undefined,
      taskId: query.taskId || undefined,
      userId: query.userId || undefined,
    },
  })
  return res.data.data
}

/**
 * 查询审批详情
 *
 * @param approvalId 审批单 ID
 * @returns 审批详情
 */
export async function getApprovalDetail(
  approvalId: string,
): Promise<ApprovalRequestVO> {
  const res = await axiosClient.get<BaseResponse<ApprovalRequestVO>>(
    `/approvals/${approvalId}`,
  )
  return res.data.data
}

/**
 * 审批通过
 *
 * @param approvalId 审批单 ID
 * @param body       审批操作请求（含通过理由，可选）
 * @returns 更新后的审批请求
 */
export async function approveApproval(
  approvalId: string,
  body?: ApprovalActionRequest,
): Promise<ApprovalRequestVO> {
  const res = await axiosClient.post<BaseResponse<ApprovalRequestVO>>(
    `/approvals/${approvalId}/approve`,
    body,
  )
  return res.data.data
}

/**
 * 审批拒绝
 *
 * @param approvalId 审批单 ID
 * @param body       审批操作请求（含拒绝理由，可选）
 * @returns 更新后的审批请求
 */
export async function rejectApproval(
  approvalId: string,
  body?: ApprovalActionRequest,
): Promise<ApprovalRequestVO> {
  const res = await axiosClient.post<BaseResponse<ApprovalRequestVO>>(
    `/approvals/${approvalId}/reject`,
    body,
  )
  return res.data.data
}

/** 审批 API 聚合导出 */
export const approvalApi = {
  listApprovals,
  getApprovalDetail,
  approveApproval,
  rejectApproval,
}

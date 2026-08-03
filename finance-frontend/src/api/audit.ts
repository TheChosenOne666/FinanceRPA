/**
 * 审计日志 API 封装
 *
 * 对齐后端 com.finrpa.audit.controller.AuditController：
 * - GET  /v1/audit/logs           分页多维检索
 * - GET  /v1/audit/logs/export    CSV 导出（二进制流）
 * - GET  /v1/audit/logs/{auditId} 审计详情
 *
 * 说明：
 * - orgId 由后端从 TenantContext 自动填充，前端无需传递
 * - 导出端点返回 text/csv 二进制流，需用 responseType: 'blob' 接收并触发下载
 * - 截图 URL 字段为 MinIO 预签名（1 小时有效），需注意时效
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axios from 'axios'
import axiosClient from './AxiosClient'
import { useAuthStore } from '@/store/AuthStore'
import type {
  AuditLogQueryRequest,
  AuditLogVO,
  BaseResponse,
  IPage,
} from './types'

/**
 * 分页查询审计日志列表
 *
 * @param query 查询请求（分页 + 多维筛选 + 排序）
 * @returns 审计日志分页列表
 */
export async function listAuditLogs(
  query: AuditLogQueryRequest,
): Promise<IPage<AuditLogVO>> {
  const res = await axiosClient.get<BaseResponse<IPage<AuditLogVO>>>(
    '/v1/audit/logs',
    {
      params: {
        current: query.current,
        pageSize: query.pageSize,
        sortField: query.sortField,
        sortOrder: query.sortOrder,
        taskId: query.taskId || undefined,
        userId: query.userId || undefined,
        departmentId: query.departmentId || undefined,
        businessLineId: query.businessLineId || undefined,
        riskLevel: query.riskLevel || undefined,
        actionType: query.actionType || undefined,
        executionResult: query.executionResult || undefined,
        startTime: query.startTime || undefined,
        endTime: query.endTime || undefined,
      },
    },
  )
  return res.data.data
}

/**
 * 查询审计日志详情
 *
 * @param auditId 审计 ID
 * @returns 审计详情
 */
export async function getAuditLogDetail(
  auditId: string,
): Promise<AuditLogVO> {
  const res = await axiosClient.get<BaseResponse<AuditLogVO>>(
    `/v1/audit/logs/${auditId}`,
  )
  return res.data.data
}

/**
 * 按当前筛选条件导出 CSV
 *
 * 说明：
 * - 后端限制最大 10000 条防 OOM
 * - 响应体为 text/csv; charset=UTF-8（含 UTF-8 BOM 头），文件名 `audit_logs_yyyyMMdd.csv`
 * - 直接用原始 axios 触发下载（绕过业务响应拦截器，按 Blob 处理）
 *
 * @param query 查询条件（与 listAuditLogs 同参数）
 * @returns 下载是否成功（失败时返回错误信息）
 */
export async function exportAuditLogs(
  query: AuditLogQueryRequest,
): Promise<{ success: boolean; message?: string }> {
  // 1. 从 AuthStore 获取 accessToken（绕过 axiosClient 业务码拦截，按二进制流处理）
  const token = useAuthStore.getState().accessToken
  // 2. 构造查询参数
  const params: Record<string, string | number | undefined> = {
    current: query.current,
    pageSize: query.pageSize,
    sortField: query.sortField,
    sortOrder: query.sortOrder,
    taskId: query.taskId || undefined,
    userId: query.userId || undefined,
    departmentId: query.departmentId || undefined,
    businessLineId: query.businessLineId || undefined,
    riskLevel: query.riskLevel || undefined,
    actionType: query.actionType || undefined,
    executionResult: query.executionResult || undefined,
    startTime: query.startTime || undefined,
    endTime: query.endTime || undefined,
  }
  try {
    // 3. 直接用原始 axios 发请求，responseType: 'blob' 接收二进制流
    const response = await axios.get('/api/v1/audit/logs/export', {
      params,
      responseType: 'blob',
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    })
    // 4. 从 Content-Disposition 解析文件名，解析失败回退默认名
    const disposition = response.headers['content-disposition'] as
      | string
      | undefined
    let filename = `audit_logs_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`
    if (disposition) {
      // RFC 5987 格式：filename*=UTF-8''<encoded>
      const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
      if (match?.[1]) {
        filename = decodeURIComponent(match[1])
      } else {
        // 兼容普通 filename="xxx" 格式
        const fallback = /filename="?([^";]+)"?/i.exec(disposition)
        if (fallback?.[1]) filename = fallback[1]
      }
    }
    // 5. 触发浏览器下载：创建 Blob URL → 模拟点击 a 标签 → 释放 URL
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    return { success: true }
  } catch (err) {
    const msg = err instanceof Error ? err.message : 'CSV 导出失败，请稍后重试'
    return { success: false, message: msg }
  }
}

/** 审计 API 聚合导出 */
export const auditApi = {
  listAuditLogs,
  getAuditLogDetail,
  exportAuditLogs,
}

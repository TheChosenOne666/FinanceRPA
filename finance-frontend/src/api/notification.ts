/**
 * 通知管理 API 封装
 *
 * 对齐后端 com.finrpa.notification.controller.NotificationController：
 * - GET  /notification/channels       查询所有通道及其配置状态
 * - POST /notification/test           测试发送（指定通道 + 模板 + 参数）
 * - GET  /notification/retry/queue    查询重试队列待处理任务数
 * - GET  /notification/retry/stats    查询重试队列统计（成功率 / 失败次数 / 告警数）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import axiosClient from './AxiosClient'
import type {
  BaseResponse,
  ChannelVO,
  NotificationSendResultVO,
  NotificationTestRequest,
  RetryQueueStatsVO,
} from './types'

/**
 * 查询所有通知通道及其配置状态
 *
 * @returns 通道列表
 */
export async function listChannels(): Promise<ChannelVO[]> {
  const res = await axiosClient.get<BaseResponse<ChannelVO[]>>('/notification/channels')
  return res.data.data
}

/**
 * 测试发送通知
 *
 * @param body 测试请求（通道 + 模板类型 + 参数）
 * @returns 发送结果
 */
export async function sendTestNotification(
  body: NotificationTestRequest,
): Promise<NotificationSendResultVO> {
  const res = await axiosClient.post<BaseResponse<NotificationSendResultVO>>(
    '/notification/test',
    body,
  )
  return res.data.data
}

/**
 * 查询重试队列待处理任务数
 *
 * @returns 队列长度
 */
export async function getRetryQueueSize(): Promise<number> {
  const res = await axiosClient.get<BaseResponse<number>>('/notification/retry/queue')
  return res.data.data
}

/**
 * 查询重试队列统计
 *
 * @returns 统计 VO（队列长度 + 总尝试次数 + 成功率 + 失败次数 + 告警数）
 */
export async function getRetryStats(): Promise<RetryQueueStatsVO> {
  const res = await axiosClient.get<BaseResponse<RetryQueueStatsVO>>(
    '/notification/retry/stats',
  )
  return res.data.data
}

/** 通知 API 聚合导出 */
export const notificationApi = {
  listChannels,
  sendTestNotification,
  getRetryQueueSize,
  getRetryStats,
}

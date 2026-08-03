/**
 * 通知中心页面（M6.6 扩展）
 *
 * 功能：
 * - 重试队列统计卡片：队列长度 / 总尝试次数 / 成功率 / 告警数
 * - 通道列表：企业微信 / 钉钉 Webhook 配置状态
 * - 测试发送表单：选择通道 + 模板类型 + 自定义参数 JSON
 * - 发送结果展示：成功 / 失败 + 通道原始响应
 * - 自动轮询：30s 刷新统计与通道状态
 *
 * 对齐后端 com.finrpa.notification.controller.NotificationController：
 * - GET  /notification/channels       通道列表
 * - POST /notification/test           测试发送
 * - GET  /notification/retry/stats    重试队列统计
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api/notification'
import type {
  ChannelVO,
  NotificationChannelType,
  NotificationSendResultVO,
  NotificationTemplateType,
  RetryQueueStatsVO,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import {
  IconAlert,
  IconBell,
  IconCheck,
  IconClose,
  IconRefresh,
  IconSend,
} from '@/components/Icons'

/** 通道选项 */
const CHANNEL_OPTIONS: Array<{ value: NotificationChannelType; label: string }> = [
  { value: 'wecom', label: '企业微信群机器人' },
  { value: 'dingtalk', label: '钉钉群机器人' },
]

/** 模板选项 */
const TEMPLATE_OPTIONS: Array<{
  value: NotificationTemplateType
  label: string
  /** 默认参数示例（JSON 字符串） */
  defaultParams: string
}> = [
  {
    value: 'APPROVAL_PENDING',
    label: '审批待处理',
    defaultParams: '{"approvalId":"apr_demo","taskId":"100","riskLevel":"high"}',
  },
  {
    value: 'APPROVAL_TIMEOUT',
    label: '审批超时告警',
    defaultParams: '{"approvalId":"apr_demo","taskId":"100","timeoutMinutes":30}',
  },
  {
    value: 'TASK_FAILED',
    label: '任务失败',
    defaultParams: '{"taskId":"100","errorMessage":"元素未找到","subtaskIndex":2}',
  },
  {
    value: 'NEEDS_HUMAN',
    label: 'NEEDS_HUMAN 接管',
    defaultParams: '{"taskId":"100","subtaskIndex":2","validationError":"校验失败"}',
  },
  {
    value: 'RISK_ESCALATION',
    label: '风险等级升级',
    defaultParams: '{"taskId":"100","fromRisk":"medium","toRisk":"high","reason":"命中敏感操作"}',
  },
]

/** 统计刷新间隔（30 秒） */
const STATS_REFRESH_MS = 30_000

/** 默认表单状态 */
const DEFAULT_FORM = {
  channel: 'wecom' as NotificationChannelType,
  templateType: 'APPROVAL_PENDING' as NotificationTemplateType,
  paramsText: TEMPLATE_OPTIONS[0].defaultParams,
}

/**
 * 通知中心页面
 */
function NotificationCenter() {
  const queryClient = useQueryClient()

  // 1. 统计查询
  const statsQuery = useQuery<RetryQueueStatsVO>({
    queryKey: ['notification', 'stats'],
    queryFn: () => notificationApi.getRetryStats(),
    refetchInterval: STATS_REFRESH_MS,
    refetchOnWindowFocus: false,
  })

  // 2. 通道列表查询
  const channelsQuery = useQuery<ChannelVO[]>({
    queryKey: ['notification', 'channels'],
    queryFn: () => notificationApi.listChannels(),
    refetchInterval: STATS_REFRESH_MS,
    refetchOnWindowFocus: false,
  })

  // 3. 测试发送表单状态
  const [form, setForm] = useState(DEFAULT_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [result, setResult] = useState<NotificationSendResultVO | null>(null)

  /** 切换模板：自动填充默认参数示例 */
  const handleTemplateChange = (value: NotificationTemplateType) => {
    const opt = TEMPLATE_OPTIONS.find((o) => o.value === value)
    setForm((prev) => ({
      ...prev,
      templateType: value,
      paramsText: opt?.defaultParams ?? '',
    }))
  }

  /** 提交测试发送 */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    setResult(null)

    // 1. 校验 JSON 参数
    let params: Record<string, unknown> | undefined
    const trimmed = form.paramsText.trim()
    if (trimmed) {
      try {
        const parsed = JSON.parse(trimmed)
        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
          setFormError('参数必须是 JSON 对象（{}）')
          return
        }
        params = parsed as Record<string, unknown>
      } catch {
        setFormError('参数 JSON 格式错误，请检查语法')
        return
      }
    }

    setSubmitting(true)
    try {
      // 2. 调用测试发送接口
      const res = await notificationApi.sendTestNotification({
        channel: form.channel,
        templateType: form.templateType,
        params,
      })
      setResult(res)
      // 3. 发送成功后刷新统计（成功 / 失败都会影响统计）
      queryClient.invalidateQueries({ queryKey: ['notification', 'stats'] })
    } catch (err) {
      const msg =
        err instanceof ApiError ? err.message : '发送失败，请稍后重试'
      setFormError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  /** 手动刷新 */
  const handleManualRefresh = () => {
    statsQuery.refetch()
    channelsQuery.refetch()
  }

  const stats: RetryQueueStatsVO | undefined = statsQuery.data
  const channels: ChannelVO[] = channelsQuery.data ?? []
  const isLoadingStats = statsQuery.isLoading
  const statsError = statsQuery.error
  const channelsError = channelsQuery.error

  /** 成功率百分比（后端 Long 序列化为 String，需 Number() 显式转换） */
  const successRatePct =
    stats && Number(stats.totalAttempts) > 0
      ? Math.round(Number(stats.successRate) * 100)
      : 0

  return (
    <div className="tasks-page">
      {/* region 页面标题 + 操作区 */}
      <div className="tasks-header">
        <div>
          <h1 className="page-title">
            通知中心
          </h1>
          <p className="page-subtitle">
            查看通知通道配置状态、重试队列统计，并触发测试发送验证 Webhook 连通性
          </p>
        </div>
        <div className="tasks-header-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={handleManualRefresh}
            disabled={statsQuery.isFetching || channelsQuery.isFetching}
            title="刷新统计"
          >
            <IconRefresh size={14} />
            {statsQuery.isFetching ? '刷新中…' : '刷新'}
          </button>
        </div>
      </div>
      {/* endregion */}

      {/* region 统计卡片网格 */}
      <div className="notification-stats-grid">
        <StatCard
          title="队列待重试"
          value={isLoadingStats ? '—' : String(stats?.queueSize ?? 0)}
          hint="主通道 + Fallback 均失败"
          variant="warning"
        />
        <StatCard
          title="总尝试次数"
          value={isLoadingStats ? '—' : String(stats?.totalAttempts ?? 0)}
          hint="含首次发送与所有重试"
          variant="info"
        />
        <StatCard
          title="成功率"
          value={isLoadingStats ? '—' : `${successRatePct}%`}
          hint={`成功 ${stats?.successCount ?? 0} · 失败 ${stats?.failureCount ?? 0}`}
          variant="success"
        />
        <StatCard
          title="告警数"
          value={isLoadingStats ? '—' : String(stats?.alertCount ?? 0)}
          hint="超最大重试次数 · 待人工介入"
          variant="danger"
        />
      </div>
      {/* endregion */}

      {/* region 错误提示 */}
      {(statsError || channelsError) && (
        <div className="form-error" style={{ margin: '16px 0' }}>
          <IconAlert />
          {statsError && (
            <span style={{ marginLeft: 4 }}>
              统计加载失败：
              {statsError instanceof ApiError
                ? statsError.message
                : (statsError as Error).message}
            </span>
          )}
          {channelsError && (
            <span style={{ marginLeft: 4 }}>
              通道列表加载失败：
              {channelsError instanceof ApiError
                ? channelsError.message
                : (channelsError as Error).message}
            </span>
          )}
        </div>
      )}
      {/* endregion */}

      {/* region 通道配置状态 */}
      <div className="notification-section">
        <div className="notification-section-title">
          <IconBell size={16} />
          通道配置状态
        </div>
        <div className="notification-channels">
          {channelsQuery.isLoading ? (
            <div className="tasks-empty" style={{ padding: 16 }}>
              加载中…
            </div>
          ) : channels.length === 0 ? (
            <div className="tasks-empty" style={{ padding: 16 }}>
              <IconBell size={28} />
              <div className="tasks-empty-title">暂无通道配置</div>
            </div>
          ) : (
            channels.map((c) => (
              <div
                key={c.channel}
                className={`notification-channel-card${
                  c.configured ? ' notification-channel-configured' : ''
                }`}
              >
                <div className="notification-channel-label">{c.label}</div>
                <div className="notification-channel-status">
                  {c.configured ? (
                    <>
                      <IconCheck size={14} />
                      <span>已配置</span>
                    </>
                  ) : (
                    <>
                      <IconClose size={14} />
                      <span>未配置</span>
                    </>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
      {/* endregion */}

      {/* region 测试发送表单 */}
      <div className="notification-section">
        <div className="notification-section-title">
          <IconSend size={16} />
          测试发送
        </div>
        <form className="glass-card-static notification-test-form" onSubmit={handleSubmit}>
          <div className="notification-form-row">
            <div className="form-group" style={{ flex: 1 }}>
              <label className="label" htmlFor="notify-channel">
                通道
              </label>
              <select
                id="notify-channel"
                className="select"
                value={form.channel}
                onChange={(e) =>
                  setForm((prev) => ({
                    ...prev,
                    channel: e.target.value as NotificationChannelType,
                  }))
                }
                disabled={submitting}
              >
                {CHANNEL_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group" style={{ flex: 1 }}>
              <label className="label" htmlFor="notify-template">
                模板类型
              </label>
              <select
                id="notify-template"
                className="select"
                value={form.templateType}
                onChange={(e) =>
                  handleTemplateChange(e.target.value as NotificationTemplateType)
                }
                disabled={submitting}
              >
                {TEMPLATE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-group">
            <label className="label" htmlFor="notify-params">
              模板参数
              <span
                style={{ color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}
              >
                （JSON 对象，可选）
              </span>
            </label>
            <textarea
              id="notify-params"
              className="textarea notification-params-input"
              placeholder='{"approvalId":"apr_demo","taskId":"100"}'
              value={form.paramsText}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, paramsText: e.target.value }))
              }
              rows={4}
              spellCheck={false}
              disabled={submitting}
            />
          </div>

          {formError && <div className="form-error">{formError}</div>}

          <div className="notification-form-actions">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting}
            >
              <IconSend size={14} />
              {submitting ? '发送中…' : '发送测试通知'}
            </button>
          </div>

          {/* region 发送结果 */}
          {result && (
            <div
              className={`notification-result${
                result.success ? ' notification-result-success' : ' notification-result-failure'
              }`}
            >
              <div className="notification-result-header">
                {result.success ? (
                  <>
                    <IconCheck size={16} />
                    <span>发送成功</span>
                  </>
                ) : (
                  <>
                    <IconClose size={16} />
                    <span>发送失败</span>
                  </>
                )}
                <span className="notification-result-channel">
                  通道：{result.channel}
                </span>
              </div>
              {result.errorMessage && (
                <div className="notification-result-body">
                  <strong>错误信息：</strong>
                  <pre className="notification-result-pre">{result.errorMessage}</pre>
                </div>
              )}
              {result.rawResponse && (
                <div className="notification-result-body">
                  <strong>通道响应：</strong>
                  <pre className="notification-result-pre">{result.rawResponse}</pre>
                </div>
              )}
            </div>
          )}
          {/* endregion */}
        </form>
      </div>
      {/* endregion */}
    </div>
  )
}

/** StatCard 配色变体 */
type StatVariant = 'info' | 'success' | 'warning' | 'danger'

/** 统计卡片属性 */
interface StatCardProps {
  /** 卡片标题 */
  title: string
  /** 主数值 */
  value: string
  /** 辅助说明 */
  hint?: string
  /** 配色变体 */
  variant: StatVariant
}

/**
 * 统计卡片
 *
 * @param title   标题
 * @param value   主数值
 * @param hint    辅助说明
 * @param variant 配色变体
 */
function StatCard({ title, value, hint, variant }: StatCardProps) {
  return (
    <div className={`glass-card-static notification-stat-card notification-stat-${variant}`}>
      <div className="notification-stat-title">{title}</div>
      <div className="notification-stat-value">{value}</div>
      {hint && <div className="notification-stat-hint">{hint}</div>}
    </div>
  )
}

export default NotificationCenter

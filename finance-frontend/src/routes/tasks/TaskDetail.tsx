/**
 * 任务详情页
 *
 * 功能（M2.5 + M4.4 增强）：
 * - 基本信息：任务 ID / 目标 / 状态 / 进度 / 创建时间 / 更新时间 / 错误信息
 * - 子任务时间线：Timeline 组件（M4.4：replan 标记 + 可展开详情）
 * - 浏览器实时流：BrowserStream 组件（SSE 接收事件，执行中实时更新）
 * - 操作日志：当前阶段以 SSE 事件流为准（M3 后接入审计日志 API）
 * - 操作：终止任务（POST /tasks/{taskId}/abort）
 * - 操作：断点续跑（POST /tasks/{taskId}/resume，仅 FAILED / NEEDS_HUMAN 可用）
 *
 * 数据刷新策略：
 * - 初次进入：调 GET /tasks/{taskId} 拉取详情
 * - 执行中：BrowserStream 的 SSE 实时推送进度，详情每隔 5s 拉取一次
 * - 终态：BrowserStream 关闭后强制刷新一次详情
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { taskApi } from '@/api/tasks'
import { ApiError } from '@/api/AxiosClient'
import type { TaskStatus } from '@/api/types'
import BrowserStream from '@/components/BrowserStream'
import StatusBadge from '@/components/StatusBadge'
import Timeline from '@/components/Timeline'
import {
  IconAlert,
  IconArrowLeft,
  IconCheck,
  IconClock,
  IconRefresh,
  IconResume,
  IconStop,
  IconTerminal,
} from '@/components/Icons'

/** 终态状态集合 */
const TERMINAL_STATUSES: ReadonlySet<TaskStatus> = new Set([
  'SUCCESS',
  'FAILED',
  'NEEDS_HUMAN',
  'ABORTED',
])

/** 任务详情页 */
function TaskDetail() {
  const navigate = useNavigate()
  const { taskId } = useParams<{ taskId: string }>()
  const queryClient = useQueryClient()

  // 1. 终止操作状态
  const [aborting, setAborting] = useState(false)
  const [abortError, setAbortError] = useState<string | null>(null)

  // 1.1 续跑操作状态（M4.4）
  const [resuming, setResuming] = useState(false)
  const [resumeError, setResumeError] = useState<string | null>(null)

  // 2. 查询任务详情
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['task', taskId],
    queryFn: () => taskApi.getTaskDetail(taskId!),
    enabled: !!taskId,
    refetchOnWindowFocus: false,
    // 执行中每 5s 轮询，终态后停止
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && TERMINAL_STATUSES.has(status) ? false : 5000
    },
  })

  // 3. 终态判定
  const isTerminal = !!data && TERMINAL_STATUSES.has(data.status)

  // 3.1 可续跑判定（M4.4：仅 FAILED / NEEDS_HUMAN 可续跑）
  const canResume = !!data && (data.status === 'FAILED' || data.status === 'NEEDS_HUMAN')

  // 4. 终止任务
  const handleAbort = async () => {
    if (!taskId) return
    if (!window.confirm('确定要终止此任务吗？终止后无法恢复。')) return
    setAborting(true)
    setAbortError(null)
    try {
      await taskApi.abortTask(taskId)
      await refetch()
    } catch (err) {
      setAbortError(err instanceof ApiError ? err.message : '终止任务失败')
    } finally {
      setAborting(false)
    }
  }

  // 4.1 断点续跑（M4.4）
  const handleResume = async () => {
    if (!taskId) return
    if (!window.confirm('确定要从此任务的断点续跑吗？\n将跳过已完成的子任务，从上次中断处继续执行。')) return
    setResuming(true)
    setResumeError(null)
    try {
      await taskApi.resumeTask(taskId)
      await refetch()
    } catch (err) {
      setResumeError(err instanceof ApiError ? err.message : '续跑任务失败')
    } finally {
      setResuming(false)
    }
  }

  // 5. SSE 终态回调 → 刷新详情
  const handleStreamTerminal = () => {
    queryClient.invalidateQueries({ queryKey: ['task', taskId] })
  }

  // 6. 加载中 / 错误兜底
  if (isLoading) {
    return (
      <div className="task-detail">
        <div className="task-detail-loading glass-card-static">加载任务详情中…</div>
      </div>
    )
  }
  if (error) {
    return (
      <div className="task-detail">
        <BackButton onClick={() => navigate('/tasks')} />
        <div className="form-error" style={{ margin: '16px 0' }}>
          <IconAlert size={14} />
          加载失败：
          {error instanceof ApiError ? error.message : (error as Error).message}
        </div>
      </div>
    )
  }
  if (!data || !taskId) {
    return (
      <div className="task-detail">
        <BackButton onClick={() => navigate('/tasks')} />
        <div className="task-detail-loading glass-card-static">未找到任务</div>
      </div>
    )
  }

  // 7. 进度计算（对齐原型 detail-progress-row，仅 totalSteps > 0 时展示进度条）
  const hasProgress = data.totalSteps > 0
  const progressPct = hasProgress
    ? Math.min(100, Math.round((data.currentStep / data.totalSteps) * 100))
    : 0

  return (
    <div className="task-detail">
      {/* region 顶部：返回 + 标题 + 进度 + 操作（卡片化，对齐原型 detail-header） */}
      <div className="glass-card-static task-detail-header-card">
        <div className="task-detail-header">
          <BackButton onClick={() => navigate('/tasks')} />
          <div className="task-detail-title">
            <h1 className="page-title">
              <span className="task-id-chip" title={taskId}>
                #{taskId.slice(-12)}
              </span>
              <StatusBadge status={data.status} size="md" />
            </h1>
            <p className="task-goal-display">{data.goal}</p>
            <div className="task-detail-meta">
              创建于 {dayjs(data.createTime).format('YYYY-MM-DD HH:mm:ss')}
              <span className="dot">·</span>
              触发用户 {data.userId}
              {data.workflowId && (
                <>
                  <span className="dot">·</span>
                  工作流 #{data.workflowId.slice(-10)}
                </>
              )}
            </div>
            {hasProgress && (
              <div className="task-detail-progress">
                <div className="progress">
                  <div
                    className="progress-bar"
                    style={{ width: `${progressPct}%` }}
                  />
                </div>
                <span className="pct">
                  {progressPct}%（{data.currentStep}/{data.totalSteps}）
                </span>
              </div>
            )}
          </div>
          <div className="task-detail-actions">
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => refetch()}
              disabled={isFetching}
              title="刷新详情"
            >
              <IconRefresh size={14} />
              {isFetching ? '刷新中…' : '刷新'}
            </button>
            {canResume && (
              <button
                type="button"
                className="btn btn-sm"
                style={{
                  background: 'rgba(16, 185, 129, 0.08)',
                  color: 'var(--status-completed)',
                  border: '1px solid rgba(16, 185, 129, 0.32)',
                }}
                onClick={handleResume}
                disabled={resuming}
                title="从断点续跑（跳过已完成子任务）"
              >
                <IconResume size={14} />
                {resuming ? '续跑中…' : '断点续跑'}
              </button>
            )}
            {!isTerminal && (
              <button
                type="button"
                className="btn btn-sm"
                style={{
                  background: 'rgba(239, 68, 68, 0.08)',
                  color: 'var(--accent-danger)',
                  border: '1px solid rgba(239, 68, 68, 0.32)',
                }}
                onClick={handleAbort}
                disabled={aborting}
              >
                <IconStop size={14} />
                {aborting ? '终止中…' : '终止任务'}
              </button>
            )}
          </div>
        </div>
      </div>
      {/* endregion */}

      {/* region 终止 / 续跑 / 任务错误提示 */}
      {abortError && (
        <div className="form-error" style={{ margin: '0 0 16px' }}>
          <IconAlert size={14} />
          {abortError}
        </div>
      )}
      {resumeError && (
        <div className="form-error" style={{ margin: '0 0 16px' }}>
          <IconAlert size={14} />
          {resumeError}
        </div>
      )}
      {data.errorMessage && (
        <div className="task-detail-alert task-detail-alert-danger">
          <IconAlert size={14} />
          <span><strong>错误信息：</strong>{data.errorMessage}</span>
        </div>
      )}
      {data.message && (
        <div className="task-detail-alert task-detail-alert-info">
          <span><strong>状态消息：</strong>{data.message}</span>
        </div>
      )}
      {/* endregion */}

      <div className="task-detail-grid">
        {/* region 左侧：浏览器实时流 + 操作日志（对齐原型 detail-grid 左栏） */}
        <div className="task-detail-left">
          <section className="glass-card-static detail-section detail-stream-section">
            <h2 className="section-title">
              浏览器实时流
            </h2>
            {!isTerminal ? (
              <BrowserStream
                taskId={taskId}
                initialStatus={data.status}
                initialCurrentStep={data.currentStep}
                initialTotalSteps={data.totalSteps}
                onTerminal={handleStreamTerminal}
              />
            ) : (
              <div className="detail-empty">
                <IconCheck size={28} />
                <div>任务已结束</div>
                <div className="detail-empty-desc">
                  终态：{data.status}
                  {data.errorMessage ? ` · ${data.errorMessage}` : ''}
                </div>
              </div>
            )}
          </section>

          {/* 操作日志（M3 阶段接入审计日志 API） */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">
              操作日志
            </h2>
            <div className="detail-empty">
              <IconTerminal size={28} />
              <div>M3 阶段接入审计日志 API</div>
              <div className="detail-empty-desc">
                当前阶段可参考上方"浏览器实时流"中的事件日志
              </div>
            </div>
          </section>
        </div>
        {/* endregion */}

        {/* region 右侧：子任务时间线 + 基本信息（对齐原型 detail-grid 右栏） */}
        <div className="task-detail-right">
          {/* 子任务时间线（M4.4：Timeline 组件，含 replan 标记 + 可展开详情） */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">
              子任务时间线
            </h2>
            {data.subtasks && data.subtasks.length > 0 ? (
              <Timeline subtasks={data.subtasks} />
            ) : (
              <div className="detail-empty">
                <IconClock size={28} />
                <div>暂无子任务</div>
                <div className="detail-empty-desc">
                  {isTerminal ? '任务已结束，未生成子任务' : '等待 Planner 规划…'}
                </div>
              </div>
            )}
          </section>

          {/* 基本信息（扩展信息：更新时间 / 工作流 ID / 任务参数） */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">
              基本信息
            </h2>
            <div className="info-grid">
              <InfoItem label="任务 ID" value={data.taskId} mono />
              <InfoItem label="状态" value={<StatusBadge status={data.status} />} />
              <InfoItem
                label="进度"
                value={
                  data.totalSteps > 0
                    ? `${data.currentStep} / ${data.totalSteps}`
                    : '等待规划'
                }
              />
              <InfoItem
                label="创建时间"
                value={dayjs(data.createTime).format('YYYY-MM-DD HH:mm:ss')}
                mono
              />
              <InfoItem
                label="更新时间"
                value={dayjs(data.updateTime).format('YYYY-MM-DD HH:mm:ss')}
                mono
              />
              {data.workflowId && (
                <InfoItem label="工作流 ID" value={data.workflowId} mono />
              )}
              <InfoItem label="触发用户" value={data.userId} mono />
            </div>
            {data.params && (
              <div className="info-params">
                <div className="info-params-title">任务参数：</div>
                <pre className="info-params-code">{formatJson(data.params)}</pre>
              </div>
            )}
          </section>
        </div>
        {/* endregion */}
      </div>
    </div>
  )
}

/**
 * 返回按钮
 *
 * @param onClick 点击回调
 */
function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="btn btn-ghost btn-sm task-detail-back" onClick={onClick}>
      <IconArrowLeft size={14} />
      返回列表
    </button>
  )
}

/**
 * 信息项
 *
 * @param label  标签
 * @param value  值（字符串或 ReactNode）
 * @param mono   是否使用等宽字体
 */
function InfoItem({
  label,
  value,
  mono = false,
}: {
  label: string
  value: React.ReactNode
  mono?: boolean
}) {
  return (
    <div className="info-item">
      <span className="info-label">{label}</span>
      <span className={`info-value${mono ? ' info-value-mono' : ''}`}>{value}</span>
    </div>
  )
}

/**
 * 格式化 JSON 字符串（解析失败时返回原值）
 */
function formatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

export default TaskDetail

/**
 * 浏览器实时流组件
 *
 * M2.5 阶段实现：基于 SSE 接收 Python 执行事件流（透传自 Java），
 * 实时渲染执行进度、事件日志和截图占位。
 *
 * 与参考项目 finrpa-enterprise/skyvern-frontend 的差异：
 *   - 参考项目通过 @novnc/novnc + WebSocket 连接 VNC 服务实现真实浏览器流，
 *     依赖 Skyvern 启动 browser_session 并暴露 VNC 端口。
 *   - 当前项目 M2.x 阶段未集成 Skyvern，使用 fallback 模式（模拟执行），
 *     没有 VNC 流可接入；故本组件改为 SSE 事件日志 + 进度可视化。
 *   - 预留 screenshotKey 字段，M3.1 接入 Skyvern 后通过 MinIO URL 展示真实截图。
 *
 * 组件结构：
 *   1. 顶部：进度条 + 当前状态徽章 + 终止按钮（可选）
 *   2. 中部：截图展示区（screenshotKey 事件触发，含占位符）
 *   3. 底部：实时事件日志（按时间倒序，自动滚动到最新）
 *
 * 事件类型（对齐 Python app/agent/event_bus.py + executor.py / coordinator.py）：
 *   - step_start：子任务开始（携带 subtaskIndex / goal）
 *   - step_end：子任务结束（携带 success / durationMs）
 *   - progress：任务级进度（携带 currentStep / totalSteps）
 *   - replan：重新规划（携带 totalReplans / maxReplans）
 *   - screenshot：截图已上传（携带 screenshotKey）
 *   - complete：任务完成（终态）
 *   - error：执行错误（终态）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import { createTaskSse } from '@/api/sse'
import type { SseEvent, TaskStatus } from '@/api/types'
import { IconAlert, IconCamera, IconCheck, IconRefresh, IconTerminal } from '@/components/Icons'
import StatusBadge from '@/components/StatusBadge'

/** BrowserStream 属性 */
export interface BrowserStreamProps {
  /** 任务 ID */
  taskId: string
  /** 初始任务状态（用于立即渲染徽章，SSE 终态事件到达后会被覆盖） */
  initialStatus: TaskStatus
  /** 初始进度（currentStep / totalSteps，可选） */
  initialCurrentStep?: number
  initialTotalSteps?: number
  /** 任务终态回调（complete / error 事件触发） */
  onTerminal?: (event: SseEvent | null) => void
  /** 是否自动重连（默认 false，调用方控制） */
  autoReconnect?: boolean
}

/** 事件日志条目 */
interface LogEntry {
  /** 唯一 key（用于 React 列表） */
  id: string
  /** 原始事件 */
  event: SseEvent
  /** 接收时间戳（毫秒） */
  receivedAt: number
}

/** 日志最大条数（避免长任务内存膨胀） */
const MAX_LOG_ENTRIES = 200

/**
 * 浏览器实时流组件
 */
export function BrowserStream({
  taskId,
  initialStatus,
  initialCurrentStep = 0,
  initialTotalSteps = 0,
  onTerminal,
  autoReconnect = false,
}: BrowserStreamProps) {
  // 1. 状态
  const [status, setStatus] = useState<TaskStatus>(initialStatus)
  const [currentStep, setCurrentStep] = useState<number>(initialCurrentStep)
  const [totalSteps, setTotalSteps] = useState<number>(initialTotalSteps)
  const [latestMessage, setLatestMessage] = useState<string>('')
  const [latestScreenshotKey, setLatestScreenshotKey] = useState<string | null>(null)
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [connected, setConnected] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)

  // 2. 引用：onTerminal 不应触发重连，用 ref 保存最新值
  const onTerminalRef = useRef(onTerminal)
  onTerminalRef.current = onTerminal
  // 日志容器引用（用于自动滚动到底部）
  const logContainerRef = useRef<HTMLDivElement | null>(null)

  // 3. 已是终态时不再重连
  const isTerminal = useMemo(
    () =>
      status === 'SUCCESS' ||
      status === 'FAILED' ||
      status === 'ABORTED' ||
      status === 'NEEDS_HUMAN',
    [status],
  )

  // 4. 终态事件 → 任务状态映射
  const applyTerminalEvent = (event: SseEvent) => {
    const state = event.data.state?.toUpperCase()
    if (state === 'SUCCESS') setStatus('SUCCESS')
    else if (state === 'FAILED') setStatus('FAILED')
    else if (state === 'ABORTED') setStatus('ABORTED')
    else if (state === 'NEEDS_HUMAN') setStatus('NEEDS_HUMAN')
    else if (event.event === 'complete') setStatus('SUCCESS')
    else if (event.event === 'error') setStatus('FAILED')
  }

  // 5. 事件处理
  const handleEvent = (event: SseEvent) => {
    // 5.1 追加到日志
    setLogs((prev) => {
      const entry: LogEntry = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        event,
        receivedAt: Date.now(),
      }
      const next = [...prev, entry]
      // 超出上限时丢弃最早的
      if (next.length > MAX_LOG_ENTRIES) next.splice(0, next.length - MAX_LOG_ENTRIES)
      return next
    })

    // 5.2 按事件类型更新状态
    switch (event.event) {
      case 'step_start':
        if (event.data.subtaskIndex !== undefined) {
          setCurrentStep(event.data.subtaskIndex)
        }
        if (event.data.totalSteps) setTotalSteps(event.data.totalSteps)
        break
      case 'step_end':
        if (event.data.totalSteps) setTotalSteps(event.data.totalSteps)
        break
      case 'progress':
        if (event.data.currentStep !== undefined) setCurrentStep(event.data.currentStep)
        if (event.data.totalSteps) setTotalSteps(event.data.totalSteps)
        break
      case 'replan':
        // 重新规划不改状态，只更新消息
        break
      case 'screenshot':
        if (event.data.screenshotKey) setLatestScreenshotKey(event.data.screenshotKey)
        break
      case 'complete':
      case 'error':
        applyTerminalEvent(event)
        break
    }

    // 5.3 更新最新消息
    if (event.data.message) setLatestMessage(event.data.message)
    if (event.data.error) setError(event.data.error)
  }

  // 6. 建立 SSE 连接
  useEffect(() => {
    if (isTerminal) return // 终态后不再订阅

    let ctrl: ReturnType<typeof createTaskSse> | null = null

    ctrl = createTaskSse(taskId, {
      onOpen: () => setConnected(true),
      onEvent: handleEvent,
      onTerminal: (event) => {
        setConnected(false)
        onTerminalRef.current?.(event)
      },
      onError: () => {
        setConnected(false)
        // EventSource 默认会自动重连；这里仅在已关闭时记录
      },
    })

    return () => {
      ctrl?.close()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId, isTerminal, autoReconnect])

  // 7. 日志自动滚动到底部
  useEffect(() => {
    const el = logContainerRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [logs])

  // 8. 进度计算
  const progressPct =
    totalSteps > 0 ? Math.min(100, Math.round((currentStep / totalSteps) * 100)) : 0

  return (
    <div className="browser-stream glass-card-static">
      {/* region 顶部：状态 + 进度条 + 连接状态 */}
      <div className="browser-stream-header">
        <div className="browser-stream-status">
          <StatusBadge status={status} size="md" />
          <span className={`browser-stream-conn${connected ? ' connected' : ''}`}>
            <span className="browser-stream-conn-dot" />
            {connected ? '已连接' : isTerminal ? '已结束' : '连接中…'}
          </span>
        </div>
        {latestMessage && (
          <div className="browser-stream-message" title={latestMessage}>
            {latestMessage}
          </div>
        )}
      </div>

      <div className="browser-stream-progress">
        <div className="browser-stream-progress-bar">
          <div
            className="browser-stream-progress-fill"
            style={{ width: `${progressPct}%` }}
          />
        </div>
        <span className="browser-stream-progress-text">
          {totalSteps > 0 ? `${currentStep} / ${totalSteps}（${progressPct}%）` : '等待规划…'}
        </span>
      </div>
      {/* endregion */}

      {/* region 中部：截图展示区 */}
      <div className="browser-stream-canvas">
        {latestScreenshotKey ? (
          <div className="browser-stream-screenshot">
            <div className="browser-stream-screenshot-meta">
              <IconCamera size={14} />
              <span title={latestScreenshotKey}>截图：{latestScreenshotKey.slice(-16)}</span>
            </div>
            {/* M3.1 接入 Skyvern 后通过 MinIO URL 展示真实截图 */}
            <div className="browser-stream-screenshot-placeholder">
              <IconCamera size={32} />
              <div>截图预览（M3.1 接入 MinIO 后展示）</div>
              <code>{latestScreenshotKey}</code>
            </div>
          </div>
        ) : (
          <div className="browser-stream-placeholder">
            <IconTerminal size={32} />
            <div>等待浏览器事件流…</div>
            <div className="browser-stream-placeholder-desc">
              任务开始执行后，子任务进度与截图将在此处实时展示
            </div>
          </div>
        )}
      </div>
      {/* endregion */}

      {/* region 错误提示 */}
      {error && (
        <div className="form-error" style={{ margin: '12px 0' }}>
          <IconAlert size={14} />
          {error}
        </div>
      )}
      {/* endregion */}

      {/* region 底部：事件日志 */}
      <div className="browser-stream-logs" ref={logContainerRef}>
        {logs.length === 0 ? (
          <div className="browser-stream-logs-empty">
            <IconRefresh size={14} />
            等待事件…
          </div>
        ) : (
          logs.map((entry) => <LogRow key={entry.id} entry={entry} />)
        )}
      </div>
      {/* endregion */}
    </div>
  )
}

/**
 * 单条事件日志
 *
 * @param entry 日志条目
 */
function LogRow({ entry }: { entry: LogEntry }) {
  const { event, receivedAt } = entry
  const time = new Date(receivedAt).toLocaleTimeString('zh-CN', { hour12: false })

  // 1. 事件类型对应的图标与颜色
  const { icon, color, label } = getEventMeta(event.event)

  // 2. 主要内容
  const msg = event.data.message ?? ''
  const extra: string[] = []
  if (event.data.subtaskIndex !== undefined) {
    extra.push(`子任务 #${event.data.subtaskIndex + 1}`)
  }
  if (event.data.success !== undefined) {
    extra.push(event.data.success ? '成功' : '失败')
  }
  if (event.data.durationMs !== undefined) {
    extra.push(`${event.data.durationMs}ms`)
  }
  if (event.data.totalReplans !== undefined) {
    extra.push(`第 ${event.data.totalReplans} 次重规划`)
  }

  return (
    <div className="log-row">
      <span className="log-time">{time}</span>
      <span className="log-icon" style={{ color }}>
        {icon}
      </span>
      <span className="log-label" style={{ color }}>
        {label}
      </span>
      <span className="log-extra">{extra.join(' · ')}</span>
      {msg && <span className="log-message">{msg}</span>}
    </div>
  )
}

/**
 * 事件类型 → 图标/颜色/标签
 */
function getEventMeta(type: SseEvent['event']): {
  icon: React.ReactNode
  color: string
  label: string
} {
  switch (type) {
    case 'step_start':
      return {
        icon: <IconRefresh size={12} />,
        color: 'var(--status-running)',
        label: '开始',
      }
    case 'step_end':
      return {
        icon: <IconCheck size={12} />,
        color: 'var(--status-completed)',
        label: '结束',
      }
    case 'progress':
      return {
        icon: <IconRefresh size={12} />,
        color: 'var(--status-running)',
        label: '进度',
      }
    case 'replan':
      return {
        icon: <IconRefresh size={12} />,
        color: 'var(--status-needs-human)',
        label: '重规划',
      }
    case 'screenshot':
      return {
        icon: <IconCamera size={12} />,
        color: 'var(--finrpa-gold-dark)',
        label: '截图',
      }
    case 'complete':
      return {
        icon: <IconCheck size={12} />,
        color: 'var(--status-completed)',
        label: '完成',
      }
    case 'error':
      return {
        icon: <IconAlert size={12} />,
        color: 'var(--status-failed)',
        label: '错误',
      }
    default:
      return {
        icon: <IconTerminal size={12} />,
        color: 'var(--text-muted)',
        label: type,
      }
  }
}

export default BrowserStream

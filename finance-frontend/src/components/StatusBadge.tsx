/**
 * 状态徽章组件
 *
 * 用于任务列表与详情页展示任务 / 子任务状态。
 * 样式对齐 prototypes/ 中的状态色：
 *   - PENDING / EXECUTING → 蓝（进行类）
 *   - SUCCESS / COMPLETED → 绿（成功类）
 *   - FAILED / ABORTED    → 红（失败类）
 *   - NEEDS_HUMAN         → 橙（人工介入）
 *   - SKIPPED / REPLANNED → 紫/灰（中性）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import type { SubTaskStatus, TaskStatus } from '@/api/types'

/** 状态外观配置 */
interface StatusStyle {
  /** 中文标签 */
  label: string
  /** Emoji 前缀（对齐原型 03-tasks.html 的状态徽章：✅ ⚡ ⚠ ⏳ ❌） */
  emoji?: string
  /** 文字色（CSS 变量或具体色值） */
  color: string
  /** 背景色（带透明度） */
  bg: string
  /** 边框色 */
  border: string
  /** 是否显示脉动小点（执行中类） */
  pulse?: boolean
}

/** 任务状态外观映射 */
const TASK_STATUS_STYLES: Record<TaskStatus, StatusStyle> = {
  PENDING: {
    label: '待执行',
    emoji: '⏳',
    color: 'var(--status-queued)',
    bg: 'rgba(107, 114, 128, 0.10)',
    border: 'rgba(107, 114, 128, 0.28)',
  },
  EXECUTING: {
    label: '执行中',
    emoji: '⚡',
    color: 'var(--status-running)',
    bg: 'rgba(59, 130, 246, 0.10)',
    border: 'rgba(59, 130, 246, 0.32)',
    pulse: true,
  },
  SUCCESS: {
    label: '成功',
    emoji: '✅',
    color: 'var(--status-completed)',
    bg: 'rgba(16, 185, 129, 0.10)',
    border: 'rgba(16, 185, 129, 0.30)',
  },
  FAILED: {
    label: '失败',
    emoji: '❌',
    color: 'var(--status-failed)',
    bg: 'rgba(239, 68, 68, 0.10)',
    border: 'rgba(239, 68, 68, 0.30)',
  },
  NEEDS_HUMAN: {
    label: '需人工',
    emoji: '⚠',
    color: 'var(--status-needs-human)',
    bg: 'rgba(249, 115, 22, 0.10)',
    border: 'rgba(249, 115, 22, 0.32)',
  },
  ABORTED: {
    label: '已终止',
    emoji: '⛔',
    color: 'var(--status-timeout)',
    bg: 'rgba(220, 38, 38, 0.08)',
    border: 'rgba(220, 38, 38, 0.26)',
  },
}

/** 子任务状态外观映射 */
const SUBTASK_STATUS_STYLES: Record<SubTaskStatus, StatusStyle> = {
  PENDING: {
    label: '待执行',
    emoji: '⏳',
    color: 'var(--status-queued)',
    bg: 'rgba(107, 114, 128, 0.10)',
    border: 'rgba(107, 114, 128, 0.28)',
  },
  RUNNING: {
    label: '执行中',
    emoji: '⚡',
    color: 'var(--status-running)',
    bg: 'rgba(59, 130, 246, 0.10)',
    border: 'rgba(59, 130, 246, 0.32)',
    pulse: true,
  },
  COMPLETED: {
    label: '已完成',
    emoji: '✅',
    color: 'var(--status-completed)',
    bg: 'rgba(16, 185, 129, 0.10)',
    border: 'rgba(16, 185, 129, 0.30)',
  },
  FAILED: {
    label: '失败',
    emoji: '❌',
    color: 'var(--status-failed)',
    bg: 'rgba(239, 68, 68, 0.10)',
    border: 'rgba(239, 68, 68, 0.30)',
  },
  SKIPPED: {
    label: '已跳过',
    emoji: '⏭',
    color: 'var(--status-paused)',
    bg: 'rgba(139, 92, 246, 0.08)',
    border: 'rgba(139, 92, 246, 0.26)',
  },
  REPLANNED: {
    label: '已重规划',
    emoji: '🔄',
    color: 'var(--status-paused)',
    bg: 'rgba(139, 92, 246, 0.08)',
    border: 'rgba(139, 92, 246, 0.26)',
  },
}

/** StatusBadge 属性 */
export interface StatusBadgeProps {
  /** 状态值（任务或子任务） */
  status: TaskStatus | SubTaskStatus
  /** 尺寸：sm（列表紧凑）/ md（详情大号） */
  size?: 'sm' | 'md'
  /** 是否为子任务模式（用于区分类型推断，默认 false） */
  subtask?: boolean
}

/**
 * 状态徽章
 *
 * @param status 状态值
 * @param size   尺寸
 * @param subtask 是否为子任务状态
 */
export function StatusBadge({ status, size = 'sm', subtask = false }: StatusBadgeProps) {
  const style = subtask
    ? SUBTASK_STATUS_STYLES[status as SubTaskStatus]
    : TASK_STATUS_STYLES[status as TaskStatus] ??
      SUBTASK_STATUS_STYLES[status as SubTaskStatus]

  if (!style) {
    return <span className="status-badge status-badge-sm">未知</span>
  }

  const padding = size === 'md' ? '5px 12px' : '3px 10px'
  const fontSize = size === 'md' ? '13px' : '11px'

  return (
    <span
      className={`status-badge status-badge-${size}${style.pulse ? ' status-badge-pulse' : ''}`}
      style={{
        color: style.color,
        background: style.bg,
        border: `1px solid ${style.border}`,
        padding,
        fontSize,
      }}
    >
      {style.pulse && <span className="status-dot" style={{ background: style.color }} />}
      {style.emoji && <span className="status-badge-emoji">{style.emoji}</span>}
      {style.label}
    </span>
  )
}

export default StatusBadge

/**
 * 审批状态徽章组件
 *
 * 用于审批中心展示审批单状态（PENDING / APPROVED / REJECTED / TIMEOUT）。
 * 独立于 StatusBadge（后者强绑 TaskStatus / SubTaskStatus 类型），
 * 避免扩展联合类型破坏现有调用方。
 *
 * 状态色对齐 variables.css：
 *   - PENDING  → 金（待审批，脉动）
 *   - APPROVED → 绿（已通过）
 *   - REJECTED → 红（已拒绝）
 *   - TIMEOUT  → 深红（已超时）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import type { ApprovalStatus } from '@/api/types'

/** 状态外观配置 */
interface ApprovalStatusStyle {
  /** 中文标签 */
  label: string
  /** 文字色 */
  color: string
  /** 背景色（带透明度） */
  bg: string
  /** 边框色 */
  border: string
  /** 是否显示脉动小点（待审批类） */
  pulse?: boolean
}

/** 审批状态外观映射 */
const APPROVAL_STATUS_STYLES: Record<ApprovalStatus, ApprovalStatusStyle> = {
  PENDING: {
    label: '待审批',
    color: 'var(--status-pending-approval)',
    bg: 'rgba(201, 168, 76, 0.12)',
    border: 'rgba(201, 168, 76, 0.32)',
    pulse: true,
  },
  APPROVED: {
    label: '已通过',
    color: 'var(--status-completed)',
    bg: 'rgba(16, 185, 129, 0.10)',
    border: 'rgba(16, 185, 129, 0.30)',
  },
  REJECTED: {
    label: '已拒绝',
    color: 'var(--status-failed)',
    bg: 'rgba(239, 68, 68, 0.10)',
    border: 'rgba(239, 68, 68, 0.30)',
  },
  TIMEOUT: {
    label: '已超时',
    color: 'var(--status-timeout)',
    bg: 'rgba(220, 38, 38, 0.08)',
    border: 'rgba(220, 38, 38, 0.26)',
  },
}

/** ApprovalStatusBadge 属性 */
export interface ApprovalStatusBadgeProps {
  /** 审批状态值 */
  status: ApprovalStatus
  /** 尺寸：sm（列表紧凑）/ md（详情大号） */
  size?: 'sm' | 'md'
}

/**
 * 审批状态徽章
 *
 * @param status 审批状态值
 * @param size   尺寸
 */
export function ApprovalStatusBadge({
  status,
  size = 'sm',
}: ApprovalStatusBadgeProps) {
  const style = APPROVAL_STATUS_STYLES[status]
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
      {style.label}
    </span>
  )
}

export default ApprovalStatusBadge

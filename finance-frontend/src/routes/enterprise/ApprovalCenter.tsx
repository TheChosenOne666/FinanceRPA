/**
 * 审批中心页面
 *
 * 功能（M6.5）：
 * - 待审批列表：PENDING 状态审批单，按风险等级排序（critical 优先）
 * - 历史记录：已处理审批（APPROVED / REJECTED / TIMEOUT）
 * - 审批详情：任务信息 + 风险判断理由 + 超时时间
 * - 审批操作：批准 / 拒绝（含理由）
 * - 筛选：风险等级 + 审批路由 + 状态（历史记录 Tab）
 * - 自动轮询：待审批 Tab 10s 刷新（及时感知新审批单）
 *
 * 对齐后端 com.finrpa.approval.controller.ApprovalController：
 * - GET  /approvals                  分页查询
 * - POST /approvals/{id}/approve     审批通过
 * - POST /approvals/{id}/reject      审批拒绝
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { approvalApi } from '@/api/approval'
import type {
  ApprovalActionRequest,
  ApprovalQueryRequest,
  ApprovalRequestVO,
  ApprovalRoute,
  ApprovalStatus,
  WorkflowRiskLevel,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import Pagination from '@/components/Pagination'
import ApprovalStatusBadge from '@/components/ApprovalStatusBadge'
import {
  IconAlert,
  IconApproval,
  IconCheck,
  IconClock,
  IconClose,
  IconRefresh,
} from '@/components/Icons'

/** 默认页大小 */
const DEFAULT_PAGE_SIZE = 10

/** 风险等级标签映射 */
const RISK_LEVEL_LABELS: Record<WorkflowRiskLevel, string> = {
  low: '低',
  medium: '中',
  high: '高',
  critical: '极高',
}

/** 审批路由标签映射 */
const ROUTE_LABELS: Record<ApprovalRoute, string> = {
  auto: '自动通过',
  department: '部门审批',
  compliance: '合规审计',
}

/** 风险等级排序权重（critical 最高，便于待审批列表排序） */
const RISK_LEVEL_ORDER: Record<WorkflowRiskLevel, number> = {
  critical: 4,
  high: 3,
  medium: 2,
  low: 1,
}

/** Tab 类型 */
type ApprovalTab = 'pending' | 'history'

/** 风险等级筛选选项 */
const RISK_OPTIONS: Array<{ value: '' | WorkflowRiskLevel; label: string }> = [
  { value: '', label: '全部风险' },
  { value: 'high', label: '高' },
  { value: 'critical', label: '极高' },
]

/** 路由筛选选项 */
const ROUTE_OPTIONS: Array<{ value: '' | ApprovalRoute; label: string }> = [
  { value: '', label: '全部路由' },
  { value: 'department', label: '部门审批' },
  { value: 'compliance', label: '合规审计' },
]

/** 历史记录状态筛选选项 */
const HISTORY_STATUS_OPTIONS: Array<{ value: '' | ApprovalStatus; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'TIMEOUT', label: '已超时' },
]

/**
 * 从 requestPayload JSON 中解析任务目标
 *
 * @param payload 请求负载 JSON 字符串（TaskTriggerRequest 序列化）
 * @returns 任务目标（解析失败返回空串）
 */
function parseGoal(payload?: string): string {
  if (!payload) return ''
  try {
    const obj = JSON.parse(payload)
    return typeof obj.goal === 'string' ? obj.goal : ''
  } catch {
    return ''
  }
}

/** 审批中心页面 */
function ApprovalCenter() {
  const queryClient = useQueryClient()

  // 1. Tab 切换：待审批 / 历史记录
  const [tab, setTab] = useState<ApprovalTab>('pending')

  // 2. 分页
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)

  // 3. 筛选条件
  const [riskLevel, setRiskLevel] = useState<'' | WorkflowRiskLevel>('')
  const [approvalRoute, setApprovalRoute] = useState<'' | ApprovalRoute>('')
  const [historyStatus, setHistoryStatus] = useState<'' | ApprovalStatus>('')

  // 4. 详情弹窗
  const [selectedApproval, setSelectedApproval] = useState<ApprovalRequestVO | null>(null)

  // 5. 查询参数（根据 Tab 构造）
  const queryKey = useMemo(
    () =>
      [
        'approvals',
        {
          tab,
          current,
          pageSize,
          riskLevel,
          approvalRoute,
          historyStatus,
        },
      ] as const,
    [tab, current, pageSize, riskLevel, approvalRoute, historyStatus],
  )

  const queryFn = () => {
    const query: ApprovalQueryRequest = {
      current,
      pageSize,
      riskLevel,
      approvalRoute,
    }
    // 待审批 Tab 固定查 PENDING；历史记录 Tab 按状态筛选（默认全部）
    if (tab === 'pending') {
      query.status = 'PENDING'
    } else {
      query.status = historyStatus
    }
    return approvalApi.listApprovals(query)
  }

  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn,
    refetchOnWindowFocus: false,
  })

  // 6. 自动轮询：待审批 10s 刷新（及时感知新审批单），历史记录 30s
  const refreshMs = tab === 'pending' ? 10_000 : 30_000
  const refreshMsRef = useRef(refreshMs)
  refreshMsRef.current = refreshMs
  useEffect(() => {
    const id = setInterval(() => {
      refetch()
    }, refreshMsRef.current)
    return () => clearInterval(id)
  }, [refetch])

  /** 切换 Tab：重置分页与筛选 */
  const handleTabChange = (next: ApprovalTab) => {
    if (next === tab) return
    setTab(next)
    setCurrent(1)
    setRiskLevel('')
    setApprovalRoute('')
    setHistoryStatus('')
  }

  /** 分页变更 */
  const handlePageChange = (next: number, nextSize: number) => {
    if (nextSize !== pageSize) {
      setPageSize(nextSize)
    } else {
      setCurrent(next)
    }
  }

  /** 审批操作成功后：刷新列表 + 关闭弹窗 */
  const handleActionSuccess = () => {
    setSelectedApproval(null)
    queryClient.invalidateQueries({ queryKey: ['approvals'] })
  }

  // 列表记录：待审批 Tab 按风险等级降序排序（critical 优先）
  const records: ApprovalRequestVO[] = useMemo(() => {
    const list = data?.records ?? []
    if (tab === 'pending') {
      return [...list].sort(
        (a, b) => RISK_LEVEL_ORDER[b.riskLevel] - RISK_LEVEL_ORDER[a.riskLevel],
      )
    }
    return list
  }, [data?.records, tab])

  const total: number = data?.total ?? 0

  return (
    <div className="tasks-page">
      {/* region 页面标题 + 操作区 */}
      <div className="tasks-header">
        <div>
          <h1 className="page-title">
            <IconApproval size={22} /> 审批中心
          </h1>
          <p className="page-subtitle">
            审核高风险任务的执行申请，管理审批历史记录
          </p>
        </div>
        <div className="tasks-header-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => refetch()}
            disabled={isFetching}
            title="刷新列表"
          >
            <IconRefresh size={14} />
            {isFetching ? '刷新中…' : '刷新'}
          </button>
        </div>
      </div>
      {/* endregion */}

      {/* region Tab 切换：待审批 / 历史记录 */}
      <div className="approval-tabs">
        <button
          type="button"
          className={`approval-tab${tab === 'pending' ? ' approval-tab-active' : ''}`}
          onClick={() => handleTabChange('pending')}
        >
          待审批
        </button>
        <button
          type="button"
          className={`approval-tab${tab === 'history' ? ' approval-tab-active' : ''}`}
          onClick={() => handleTabChange('history')}
        >
          历史记录
        </button>
      </div>
      {/* endregion */}

      {/* region 筛选栏 */}
      <div className="tasks-toolbar glass-card-static">
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">风险等级</label>
          <select
            className="select toolbar-select"
            value={riskLevel}
            onChange={(e) => {
              setRiskLevel(e.target.value as '' | WorkflowRiskLevel)
              setCurrent(1)
            }}
          >
            {RISK_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">审批路由</label>
          <select
            className="select toolbar-select"
            value={approvalRoute}
            onChange={(e) => {
              setApprovalRoute(e.target.value as '' | ApprovalRoute)
              setCurrent(1)
            }}
          >
            {ROUTE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        {tab === 'history' && (
          <div className="toolbar-filter">
            <label className="toolbar-filter-label">状态</label>
            <select
              className="select toolbar-select"
              value={historyStatus}
              onChange={(e) => {
                setHistoryStatus(e.target.value as '' | ApprovalStatus)
                setCurrent(1)
              }}
            >
              {HISTORY_STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>
      {/* endregion */}

      {/* region 错误提示 */}
      {error && (
        <div className="form-error" style={{ margin: '16px 0' }}>
          <IconAlert />
          加载失败：
          {error instanceof ApiError ? error.message : (error as Error).message}
        </div>
      )}
      {/* endregion */}

      {/* region 审批表格 */}
      <div className="tasks-table-wrapper glass-card-static">
        {isLoading ? (
          <div className="tasks-empty">加载中…</div>
        ) : records.length === 0 ? (
          <div className="tasks-empty">
            <IconApproval size={36} />
            <div className="tasks-empty-title">
              {tab === 'pending' ? '暂无待审批申请' : '暂无审批历史'}
            </div>
            <div className="tasks-empty-desc">
              {riskLevel || approvalRoute || historyStatus
                ? '当前筛选条件下没有匹配的审批记录'
                : tab === 'pending'
                  ? '所有高风险任务审批已处理完毕'
                  : '尚无已处理的审批记录'}
            </div>
          </div>
        ) : (
          <table className="tasks-table">
            <thead>
              <tr>
                <th style={{ width: '12%' }}>审批单</th>
                <th style={{ width: '12%' }}>任务 ID</th>
                <th>任务目标</th>
                <th style={{ width: '9%' }}>风险等级</th>
                <th style={{ width: '10%' }}>审批路由</th>
                <th style={{ width: '10%' }}>状态</th>
                <th style={{ width: '15%' }}>创建时间</th>
                <th style={{ width: '8%' }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((approval) => (
                <ApprovalRow
                  key={approval.approvalId}
                  approval={approval}
                  onClick={() => setSelectedApproval(approval)}
                />
              ))}
            </tbody>
          </table>
        )}
      </div>
      {/* endregion */}

      {/* region 分页 */}
      {total > 0 && (
        <div className="tasks-pagination">
          <Pagination
            current={current}
            pageSize={pageSize}
            total={total}
            pages={data?.pages}
            onChange={handlePageChange}
            disabled={isFetching}
          />
        </div>
      )}
      {/* endregion */}

      {/* region 审批详情弹窗 */}
      {selectedApproval && (
        <ApprovalDetailModal
          approval={selectedApproval}
          onClose={() => setSelectedApproval(null)}
          onActionSuccess={handleActionSuccess}
        />
      )}
      {/* endregion */}
    </div>
  )
}

/**
 * 审批表格行
 *
 * @param approval 审批对象
 * @param onClick  点击行回调（打开详情）
 */
function ApprovalRow({
  approval,
  onClick,
}: {
  approval: ApprovalRequestVO
  onClick: () => void
}) {
  const createTime = dayjs(approval.createTime).format('YYYY-MM-DD HH:mm:ss')
  const goal = parseGoal(approval.requestPayload)

  return (
    <tr className="task-row" onClick={onClick}>
      <td className="cell-mono">
        <span className="task-id-chip" title={approval.approvalId}>
          #{approval.approvalId.slice(-10)}
        </span>
      </td>
      <td className="cell-mono">
        <span className="task-id-chip" title={approval.taskId}>
          #{approval.taskId.slice(-10)}
        </span>
      </td>
      <td className="cell-goal">
        <div className="task-goal-text">{goal || '—'}</div>
      </td>
      <td>
        <span className={`tag tag-risk-${approval.riskLevel}`}>
          {RISK_LEVEL_LABELS[approval.riskLevel]}
        </span>
      </td>
      <td className="cell-mono">{ROUTE_LABELS[approval.approvalRoute]}</td>
      <td>
        <ApprovalStatusBadge status={approval.status} />
      </td>
      <td className="cell-mono cell-time">{createTime}</td>
      <td>
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={(e) => {
            e.stopPropagation()
            onClick()
          }}
        >
          详情
        </button>
      </td>
    </tr>
  )
}

/** 审批详情弹窗属性 */
interface ApprovalDetailModalProps {
  /** 审批对象 */
  approval: ApprovalRequestVO
  /** 关闭弹窗回调 */
  onClose: () => void
  /** 审批操作成功回调（刷新列表 + 关闭弹窗） */
  onActionSuccess: () => void
}

/** 操作类型 */
type ActionType = 'approve' | 'reject'

/**
 * 审批详情弹窗
 *
 * 展示审批详情（任务信息 + 风险理由 + 超时时间），
 * PENDING 状态提供批准 / 拒绝操作表单。
 *
 * @param approval 审批对象
 * @param onClose  关闭回调
 * @param onActionSuccess 操作成功回调
 */
function ApprovalDetailModal({
  approval,
  onClose,
  onActionSuccess,
}: ApprovalDetailModalProps) {
  // 1. 表单状态
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState<null | ActionType>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const isPending = approval.status === 'PENDING'
  const goal = parseGoal(approval.requestPayload)

  /** 执行审批操作 */
  const handleAction = async (action: ActionType) => {
    // 1.1 拒绝时理由必填
    if (action === 'reject' && !reason.trim()) {
      setFormError('拒绝时必须填写理由')
      return
    }

    setFormError(null)
    setSubmitting(action)

    try {
      // 2. 调用审批 API
      const body: ApprovalActionRequest | undefined = reason.trim()
        ? { reason: reason.trim() }
        : undefined
      if (action === 'approve') {
        await approvalApi.approveApproval(approval.approvalId, body)
      } else {
        await approvalApi.rejectApproval(approval.approvalId, body)
      }
      // 3. 成功 → 通知父组件刷新
      onActionSuccess()
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : action === 'approve'
            ? '审批通过失败，请稍后重试'
            : '审批拒绝失败，请稍后重试'
      setFormError(msg)
    } finally {
      setSubmitting(null)
    }
  }

  /** 点击遮罩关闭（提交中时禁止） */
  const handleOverlayClick = () => {
    if (!submitting) onClose()
  }

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div
        className="glass-card modal-card"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: 640 }}
      >
        {/* region 弹窗头部 */}
        <div className="modal-header">
          <div className="modal-title">
            <IconApproval size={18} />
            审批详情
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            disabled={!!submitting}
            aria-label="关闭"
          >
            <IconClose size={16} />
          </button>
        </div>
        {/* endregion */}

        {/* region 基本信息网格 */}
        <div className="approval-detail-meta">
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">审批单 ID</div>
            <div className="approval-detail-meta-value cell-mono">
              #{approval.approvalId}
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">任务 ID</div>
            <div className="approval-detail-meta-value cell-mono">
              #{approval.taskId}
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">风险等级</div>
            <div>
              <span className={`tag tag-risk-${approval.riskLevel}`}>
                {RISK_LEVEL_LABELS[approval.riskLevel]}
              </span>
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">审批路由</div>
            <div className="approval-detail-meta-value">
              {ROUTE_LABELS[approval.approvalRoute]}
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">状态</div>
            <div>
              <ApprovalStatusBadge status={approval.status} size="md" />
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">创建时间</div>
            <div className="approval-detail-meta-value cell-mono">
              {dayjs(approval.createTime).format('YYYY-MM-DD HH:mm:ss')}
            </div>
          </div>
          {approval.timeoutAt && (
            <div className="approval-detail-meta-item">
              <div className="approval-detail-meta-label">超时截止</div>
              <div className="approval-detail-meta-value cell-mono">
                <IconClock size={12} style={{ verticalAlign: '-1px', marginRight: 4 }} />
                {dayjs(approval.timeoutAt).format('YYYY-MM-DD HH:mm:ss')}
              </div>
            </div>
          )}
          {approval.approvedAt && (
            <div className="approval-detail-meta-item">
              <div className="approval-detail-meta-label">审批时间</div>
              <div className="approval-detail-meta-value cell-mono">
                {dayjs(approval.approvedAt).format('YYYY-MM-DD HH:mm:ss')}
              </div>
            </div>
          )}
        </div>
        {/* endregion */}

        {/* region 任务目标 */}
        {goal && (
          <div className="form-group">
            <label className="label">任务目标</label>
            <div className="approval-detail-text">{goal}</div>
          </div>
        )}
        {/* endregion */}

        {/* region 风险判断理由 */}
        {approval.riskReasoning && (
          <div className="form-group">
            <label className="label">风险判断理由</label>
            <div className="approval-detail-reasoning">{approval.riskReasoning}</div>
          </div>
        )}
        {/* endregion */}

        {/* region 审批结果（终态展示） */}
        {!isPending && (approval.approveReason || approval.rejectReason) && (
          <div className="form-group">
            <label className="label">
              {approval.status === 'APPROVED' ? '通过理由' : '拒绝理由'}
            </label>
            <div className="approval-detail-text">
              {approval.approveReason || approval.rejectReason}
            </div>
          </div>
        )}
        {/* endregion */}

        {/* region 操作表单（PENDING 状态） */}
        {isPending && (
          <form
            className="modal-form"
            onSubmit={(e: FormEvent) => {
              e.preventDefault()
              handleAction('approve')
            }}
          >
            <div className="form-group">
              <label className="label" htmlFor="approval-reason">
                审批理由
                <span style={{ color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}>
                  （拒绝时必填）
                </span>
              </label>
              <textarea
                id="approval-reason"
                className="textarea"
                placeholder="请输入审批理由说明"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                disabled={!!submitting}
              />
            </div>

            {formError && <div className="form-error">{formError}</div>}

            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-ghost"
                onClick={onClose}
                disabled={!!submitting}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={() => handleAction('reject')}
                disabled={!!submitting}
              >
                <IconClose size={14} />
                {submitting === 'reject' ? '拒绝中…' : '拒绝'}
              </button>
              <button type="submit" className="btn btn-primary" disabled={!!submitting}>
                <IconCheck size={14} />
                {submitting === 'approve' ? '通过中…' : '通过'}
              </button>
            </div>
          </form>
        )}
        {/* endregion */}
      </div>
    </div>
  )
}

export default ApprovalCenter

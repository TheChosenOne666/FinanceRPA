/**
 * 审批中心页面（双栏工作台布局，对齐原型 05-approval-center.html）
 *
 * 布局：
 * - 左栏（1fr）：审批卡片列表（风险色左边框 + 任务名 + 审批流 + 倒计时）
 * - 右栏（2fr）：固定详情面板（元信息网格 + 风险原因 + 任务参数表 + 执行截图 + 操作区）
 *
 * Tab：待我审批 [计数] / 我发起的 / 历史
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
import { useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { approvalApi } from '@/api/approval'
import { parseWorkflowParams, workflowApi } from '@/api/workflows'
import type {
  ApprovalActionRequest,
  ApprovalQueryRequest,
  ApprovalRequestVO,
  ApprovalRoute,
  WorkflowRiskLevel,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import { useAuthStore } from '@/store/AuthStore'
import Pagination from '@/components/Pagination'
import ApprovalStatusBadge from '@/components/ApprovalStatusBadge'
import {
  IconAlert,
  IconCamera,
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

/** Tab 类型：待我审批 / 我发起的 / 历史 */
type ApprovalTab = 'pending' | 'mine' | 'history'

/** 倒计时级别 */
type CountdownLevel = 'danger' | 'warning' | 'normal'

/** 倒计时计算结果 */
interface Countdown {
  /** 显示文本，如 "剩余 23:42" 或 "已超时" */
  text: string
  /** 紧急级别 */
  level: CountdownLevel
}

/** 任务参数项 */
interface TaskParamItem {
  /** 参数名 */
  name: string
  /** 参数值 */
  value: string
}

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

/**
 * 从 requestPayload JSON 中解析任务参数列表
 *
 * @param payload 请求负载 JSON 字符串
 * @returns 参数键值对列表（解析失败返回空数组）
 */
function parseTaskParams(payload?: string): TaskParamItem[] {
  if (!payload) return []
  try {
    const obj = JSON.parse(payload)
    const params = obj.params
    if (!params || typeof params !== 'object') return []
    return Object.entries(params).map(([name, value]) => ({
      name,
      value: typeof value === 'object' ? JSON.stringify(value) : String(value),
    }))
  } catch {
    return []
  }
}

/**
 * 计算审批剩余时间（倒计时）
 *
 * - 30 分钟内 → danger（红）
 * - 1 小时内 → warning（黄）
 * - 其余 → normal
 * - 已超时 → "已超时"
 *
 * @param timeoutAt 超时截止时间
 * @returns 倒计时对象（无超时时间返回 null）
 */
function calcCountdown(timeoutAt?: string): Countdown | null {
  if (!timeoutAt) return null
  const diff = dayjs(timeoutAt).valueOf() - Date.now()
  if (diff <= 0) return { text: '已超时', level: 'danger' }
  const hours = Math.floor(diff / 3_600_000)
  const minutes = Math.floor((diff % 3_600_000) / 60_000)
  const seconds = Math.floor((diff % 60_000) / 1_000)
  const text =
    hours > 0
      ? `剩余 ${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
      : `剩余 ${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  const level: CountdownLevel =
    diff < 30 * 60 * 1_000 ? 'danger' : diff < 60 * 60 * 1_000 ? 'warning' : 'normal'
  return { text, level }
}

/** 审批中心页面 */
function ApprovalCenter() {
  const queryClient = useQueryClient()
  // 1. 当前登录用户（用于"我发起的"筛选与审批流展示）
  const currentUser = useAuthStore((s) => s.user)

  // 2. URL 查询参数（M9.6：从工作流详情页跳转时携带 approvalId 自动定位）
  const [searchParams] = useSearchParams()

  // 3. Tab 切换
  const [tab, setTab] = useState<ApprovalTab>('pending')

  // 4. 分页
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)

  // 5. 选中的审批单（右侧面板展示；初始化从 URL ?approvalId= 读取）
  const [selectedId, setSelectedId] = useState<string | null>(
    searchParams.get('approvalId'),
  )

  // 6. 倒计时每秒刷新（触发重渲染）
  const [, setTick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 1_000)
    return () => clearInterval(id)
  }, [])

  // 7. 查询参数（根据 Tab 构造）
  const queryKey = useMemo(
    () =>
      [
        'approvals',
        { tab, current, pageSize, userId: currentUser?.userId },
      ] as const,
    [tab, current, pageSize, currentUser?.userId],
  )

  const queryFn = () => {
    const query: ApprovalQueryRequest = { current, pageSize }
    // 待我审批：PENDING；我发起的：按当前用户筛选（全部状态）；历史：终态
    if (tab === 'pending') {
      query.status = 'PENDING'
    } else if (tab === 'mine') {
      query.userId = currentUser?.userId
    } else {
      // 历史：拉取全部，前端按终态过滤（APPROVED/REJECTED/TIMEOUT）
    }
    return approvalApi.listApprovals(query)
  }

  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn,
    refetchOnWindowFocus: false,
  })

  // 8. 自动轮询：待审批 10s 刷新，其他 30s
  const refreshMs = tab === 'pending' ? 10_000 : 30_000
  const refreshMsRef = useRef(refreshMs)
  refreshMsRef.current = refreshMs
  useEffect(() => {
    const id = setInterval(() => refetch(), refreshMsRef.current)
    return () => clearInterval(id)
  }, [refetch])

  /** 切换 Tab：重置分页与选中 */
  const handleTabChange = (next: ApprovalTab) => {
    if (next === tab) return
    setTab(next)
    setCurrent(1)
    setSelectedId(null)
  }

  /** 分页变更 */
  const handlePageChange = (next: number, nextSize: number) => {
    if (nextSize !== pageSize) {
      setPageSize(nextSize)
    } else {
      setCurrent(next)
    }
  }

  /** 审批操作成功后：刷新列表 */
  const handleActionSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['approvals'] })
  }

  // 列表记录：待我审批 Tab 按风险等级降序排序（critical 优先）
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

  // 待我审批计数（Tab 徽章）
  const pendingCount = tab === 'pending' ? total : 0

  // 当前选中的审批单对象
  const selectedApproval = useMemo(() => {
    if (!selectedId) return null
    return records.find((r) => r.approvalId === selectedId) ?? null
  }, [selectedId, records])

  return (
    <div className="approval-page">
      {/* region 标题栏 + 面包屑 + Tab */}
      <div className="approval-header">
        <div>
          <h1 className="page-title">审批中心</h1>
          <div className="breadcrumb">
            <span className="breadcrumb-item">首页</span>
            <span className="sep">/</span>
            <span className="breadcrumb-item">合规</span>
            <span className="sep">/</span>
            <span className="current">审批中心</span>
          </div>
        </div>
        <div className="approval-tabs">
          <button
            type="button"
            className={`approval-tab${tab === 'pending' ? ' approval-tab-active' : ''}`}
            onClick={() => handleTabChange('pending')}
          >
            待我审批
            {pendingCount > 0 && <span className="approval-tab-count">{pendingCount}</span>}
          </button>
          <button
            type="button"
            className={`approval-tab${tab === 'mine' ? ' approval-tab-active' : ''}`}
            onClick={() => handleTabChange('mine')}
          >
            我发起的
          </button>
          <button
            type="button"
            className={`approval-tab${tab === 'history' ? ' approval-tab-active' : ''}`}
            onClick={() => handleTabChange('history')}
          >
            历史
          </button>
          <button
            type="button"
            className="approval-refresh-btn"
            onClick={() => refetch()}
            disabled={isFetching}
            title="刷新列表"
          >
            <IconRefresh size={14} />
          </button>
        </div>
      </div>
      {/* endregion */}

      {/* region 错误提示 */}
      {error && (
        <div className="form-error" style={{ margin: '0 0 16px' }}>
          <IconAlert />
          加载失败：
          {error instanceof ApiError ? error.message : (error as Error).message}
        </div>
      )}
      {/* endregion */}

      {/* region 双栏布局：左卡片列表 + 右固定详情面板 */}
      <div className="page-grid grid-1-2 approval-workbench">
        {/* 左侧：审批卡片列表 */}
        <div className="approval-list-col">
          {isLoading ? (
            <div className="approval-empty">加载中…</div>
          ) : records.length === 0 ? (
            <div className="approval-empty">
              <div className="approval-empty-title">
                {tab === 'pending'
                  ? '暂无待审批申请'
                  : tab === 'mine'
                    ? '暂无我发起的审批'
                    : '暂无审批历史'}
              </div>
              <div className="approval-empty-desc">
                {tab === 'pending'
                  ? '所有高风险任务审批已处理完毕'
                  : '当前没有匹配的审批记录'}
              </div>
            </div>
          ) : (
            <>
              {records.map((approval) => (
                <ApprovalCard
                  key={approval.approvalId}
                  approval={approval}
                  active={selectedId === approval.approvalId}
                  currentUserName={currentUser?.realName}
                  onClick={() => setSelectedId(approval.approvalId)}
                />
              ))}
              {/* 分页（历史记录数据较多时展示） */}
              {total > pageSize && (
                <div className="approval-list-pagination">
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
            </>
          )}
        </div>

        {/* 右侧：固定详情面板 */}
        <div className="glass-card-static approval-detail-panel">
          {selectedApproval ? (
            <ApprovalDetail
              approval={selectedApproval}
              currentUserName={currentUser?.realName}
              onActionSuccess={handleActionSuccess}
            />
          ) : (
            <div className="approval-detail-empty">
              <IconClock size={36} />
              <div className="approval-empty-title">请从左侧选择审批单</div>
              <div className="approval-empty-desc">
                选择一条审批记录查看详情并执行审批操作
              </div>
            </div>
          )}
        </div>
      </div>
      {/* endregion */}
    </div>
  )
}

/**
 * 审批卡片（左侧列表项）
 *
 * @param approval         审批对象
 * @param active           是否选中
 * @param currentUserName  当前用户姓名（审批流"我"的显示）
 * @param onClick          点击回调
 */
function ApprovalCard({
  approval,
  active,
  currentUserName,
  onClick,
}: {
  approval: ApprovalRequestVO
  active: boolean
  currentUserName?: string
  onClick: () => void
}) {
  const countdown = calcCountdown(approval.timeoutAt)
  const isPending = approval.status === 'PENDING'

  return (
    <div
      className={`glass-card approval-card approval-card-risk-${approval.riskLevel}${active ? ' approval-card-active' : ''}`}
      onClick={onClick}
    >
      {/* 卡片头部：任务名 + 风险徽章 */}
      <div className="approval-card-header">
        <div>
          <div className="approval-card-name">{parseGoal(approval.requestPayload) || '未命名任务'}</div>
          <div className="approval-card-id">#{approval.taskId.slice(-10)}</div>
        </div>
        <span className={`tag tag-risk-${approval.riskLevel}`}>
          {RISK_LEVEL_LABELS[approval.riskLevel]}
        </span>
      </div>
      {/* 审批流：申请人 → 我 */}
      <div className="approval-card-flow">
        <span>{approval.userName ?? '未知用户'}</span>
        <span className="arrow">→</span>
        <span>{currentUserName ?? '我'}</span>
      </div>
      {/* 倒计时（仅 PENDING 状态显示） */}
      {isPending && countdown && (
        <div className={`approval-countdown approval-countdown-${countdown.level}`}>
          <IconClock size={12} />
          {countdown.text}
        </div>
      )}
      {/* 历史记录显示状态徽章 */}
      {!isPending && (
        <div className="approval-card-status">
          <ApprovalStatusBadge status={approval.status} />
        </div>
      )}
    </div>
  )
}

/** 审批详情面板属性 */
interface ApprovalDetailProps {
  /** 审批对象 */
  approval: ApprovalRequestVO
  /** 当前用户姓名（审批流"我"的显示） */
  currentUserName?: string
  /** 审批操作成功回调 */
  onActionSuccess: () => void
}

/**
 * 审批详情面板（右侧固定面板）
 *
 * 展示审批详情（元信息 + 风险原因 + 任务参数 + 执行截图 + 操作区），
 * PENDING 状态提供批准 / 拒绝操作表单。
 */
function ApprovalDetail({ approval, currentUserName, onActionSuccess }: ApprovalDetailProps) {
  // 1. 表单状态
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState<null | 'approve' | 'reject'>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const isPending = approval.status === 'PENDING'
  const goal = parseGoal(approval.requestPayload)
  const taskParams = parseTaskParams(approval.requestPayload)
  const countdown = calcCountdown(approval.timeoutAt)

  // 1.1 拉取关联工作流模板，建立 参数代码名 → 中文 description 的映射（用于任务参数表展示业务名）
  const { data: workflowData } = useQuery({
    queryKey: ['workflow', approval.workflowId],
    queryFn: () => workflowApi.getWorkflow(approval.workflowId!),
    enabled: !!approval.workflowId,
    refetchOnWindowFocus: false,
  })
  const paramNameDescMap = useMemo(() => {
    const map: Record<string, string> = {}
    if (!workflowData?.params) return map
    for (const p of parseWorkflowParams(workflowData.params)) {
      map[p.name] = p.description || p.name
    }
    return map
  }, [workflowData?.params])

  // 2. 倒计时每秒刷新
  const [, setTick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 1_000)
    return () => clearInterval(id)
  }, [])

  // 切换审批单时重置表单
  useEffect(() => {
    setReason('')
    setFormError(null)
    setSubmitting(null)
  }, [approval.approvalId])

  /** 执行审批操作 */
  const handleAction = async (action: 'approve' | 'reject') => {
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

  const createTime = dayjs(approval.createTime).format('YYYY-MM-DD HH:mm:ss')

  return (
    <>
      {/* region 标题区 */}
      <div className="approval-detail-title-row">
        <div>
          <div className="approval-detail-title">
            {goal || '未命名任务'}
            <span className={`tag tag-risk-${approval.riskLevel}`}>
              {RISK_LEVEL_LABELS[approval.riskLevel]}风险
            </span>
          </div>
          <div className="approval-detail-id">任务 ID：#{approval.taskId}</div>
        </div>
      </div>
      {/* endregion */}

      {/* region 元信息网格（4 列） */}
      <div className="approval-meta-grid">
        <div className="approval-meta-item">
          <div className="approval-meta-key">风险等级</div>
          <div className="approval-meta-val">
            <span className={`tag tag-risk-${approval.riskLevel}`}>
              {RISK_LEVEL_LABELS[approval.riskLevel]}
            </span>
          </div>
        </div>
        <div className="approval-meta-item">
          <div className="approval-meta-key">申请人</div>
          <div className="approval-meta-val">{approval.userName ?? '—'}</div>
        </div>
        <div className="approval-meta-item">
          <div className="approval-meta-key">触发时间</div>
          <div className="approval-meta-val mono">{createTime}</div>
        </div>
        <div className="approval-meta-item">
          <div className="approval-meta-key">剩余时间</div>
          <div
            className={`approval-meta-val mono${countdown && countdown.level === 'danger' ? ' approval-meta-val-danger' : ''}`}
          >
            {isPending && countdown ? countdown.text : '—'}
          </div>
        </div>
      </div>
      {/* endregion */}

      {/* region 审批流 */}
      <div className="approval-detail-section">
        <div className="section-title">审批流程</div>
        <div className="approval-flow-chain">
          <span className="approval-flow-node">{approval.userName ?? '未知用户'}</span>
          <span className="arrow">→</span>
          <span className="approval-flow-node approval-flow-node-me">
            {currentUserName ?? '我'}
          </span>
          <span className="approval-flow-route">
            （{ROUTE_LABELS[approval.approvalRoute]}）
          </span>
        </div>
      </div>
      {/* endregion */}

      {/* region 风险原因 */}
      {approval.riskReasoning && (
        <div className="approval-detail-section">
          <div className="section-title">风险原因</div>
          <div className="approval-reason-list">
            <div className="approval-reason-item">
              <div className="approval-reason-icon">
                <IconAlert size={12} />
              </div>
              <div>{approval.riskReasoning}</div>
            </div>
          </div>
        </div>
      )}
      {/* endregion */}

      {/* region 任务参数 */}
      {taskParams.length > 0 && (
        <div className="approval-detail-section">
          <div className="section-title">任务参数</div>
          <div className="approval-table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>参数</th>
                  <th>值</th>
                </tr>
              </thead>
              <tbody>
                {taskParams.map((p) => (
                  <tr key={p.name}>
                    <td>{paramNameDescMap[p.name] || p.name}</td>
                    <td className="mono">{p.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {/* endregion */}

      {/* region 执行截图（占位） */}
      <div className="approval-detail-section">
        <div className="section-title">执行截图</div>
        <div className="approval-screenshot-grid">
          <div className="approval-screenshot">
            <div className="approval-screenshot-label">
              <span>操作前</span>
              <span className="tag tag-muted">before</span>
            </div>
            <div className="approval-screenshot-placeholder">
              <IconCamera size={28} />
              <span>暂无截图</span>
            </div>
          </div>
          <div className="approval-screenshot">
            <div className="approval-screenshot-label">
              <span>操作后</span>
              <span className="tag tag-success">after</span>
            </div>
            <div className="approval-screenshot-placeholder">
              <IconCamera size={28} />
              <span>暂无截图</span>
            </div>
          </div>
        </div>
      </div>
      {/* endregion */}

      {/* region 审批操作区（PENDING 状态） */}
      {isPending ? (
        <div className="approval-detail-section">
          <div className="section-title">审批操作</div>
          <form
            className="approval-action-form"
            onSubmit={(e: FormEvent) => {
              e.preventDefault()
              handleAction('approve')
            }}
          >
            <div className="approval-action-bar">
              <button
                type="button"
                className="btn btn-danger approval-action-btn"
                onClick={() => handleAction('reject')}
                disabled={!!submitting}
              >
                <IconClose size={14} />
                {submitting === 'reject' ? '拒绝中…' : '拒绝'}
              </button>
              <button
                type="submit"
                className="btn btn-success approval-action-btn"
                disabled={!!submitting}
              >
                <IconCheck size={14} />
                {submitting === 'approve' ? '通过中…' : '批准'}
              </button>
            </div>
            <div className="approval-reason-input">
              <label className="approval-reason-label">
                <span>
                  拒绝理由
                  <span style={{ color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}>
                    （拒绝时必填）
                  </span>
                </span>
                <span className="approval-reason-count">{reason.length} / 200</span>
              </label>
              <textarea
                className="textarea"
                placeholder="如选择拒绝，请详细说明理由，将记入审计日志…"
                value={reason}
                onChange={(e) => setReason(e.target.value.slice(0, 200))}
                rows={3}
                disabled={!!submitting}
              />
            </div>
            {formError && <div className="form-error">{formError}</div>}
          </form>
        </div>
      ) : (
        /* endregion */
        /* region 审批结果（终态展示） */
        <div className="approval-detail-section">
          <div className="section-title">审批结果</div>
          <div className="approval-result-block">
            <div className="approval-result-status">
              <ApprovalStatusBadge status={approval.status} size="md" />
            </div>
            {approval.approvedAt && (
              <div className="approval-result-time">
                审批时间：{dayjs(approval.approvedAt).format('YYYY-MM-DD HH:mm:ss')}
              </div>
            )}
            {(approval.approveReason || approval.rejectReason) && (
              <div className="approval-result-reason">
                {approval.status === 'APPROVED' ? '通过理由' : '拒绝理由'}：
                {approval.approveReason || approval.rejectReason}
              </div>
            )}
          </div>
        </div>
      )}
      {/* endregion */}
    </>
  )
}

export default ApprovalCenter

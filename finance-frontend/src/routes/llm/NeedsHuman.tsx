/**
 * NEEDS_HUMAN 人工接管队列页
 *
 * 功能（M5.6）：
 * - 分页展示 NEEDS_HUMAN 事件队列
 * - 状态筛选（全部 / 待处理 / 已处置）
 * - 点击行展开详情（截图 + LLM 原始输出 + 校验错误）
 * - 处置操作：skip / manual / abort 三按钮
 * - 处置成功后自动刷新列表
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { needsHumanApi } from '@/api/needsHuman'
import type {
  NeedsHumanQueueVO,
  NeedsHumanQueryRequest,
  NeedsHumanStatus,
  ResolveAction,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import Pagination from '@/components/Pagination'
import {
  IconAlert,
  IconCamera,
  IconChevronDown,
  IconClock,
  IconHand,
  IconRefresh,
  IconShield,
  IconSkip,
  IconStop,
} from '@/components/Icons'

/** 默认页大小 */
const DEFAULT_PAGE_SIZE = 10

/** 状态筛选选项 */
const STATUS_OPTIONS: Array<{ value: '' | NeedsHumanStatus; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待处理' },
  { value: 'RESOLVED', label: '已处置' },
]

/** NEEDS_HUMAN 接管队列页 */
function NeedsHumanPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // 1. 查询条件
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [status, setStatus] = useState<'' | NeedsHumanStatus>('PENDING')
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [resolveError, setResolveError] = useState<string | null>(null)

  // 2. 查询参数
  const queryKey = useMemo(
    () => ['needs-human', { current, pageSize, status }] as const,
    [current, pageSize, status],
  )

  // 3. 查询列表
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn: () =>
      needsHumanApi.listNeedsHuman({
        current,
        pageSize,
        status,
      } satisfies NeedsHumanQueryRequest),
    refetchOnWindowFocus: false,
  })

  // 4. 处置 mutation
  const resolveMutation = useMutation({
    mutationFn: ({
      queueId,
      action,
    }: {
      queueId: string
      action: ResolveAction
    }) => needsHumanApi.resolveNeedsHuman(queueId, { action }),
    onSuccess: () => {
      setResolveError(null)
      queryClient.invalidateQueries({ queryKey: ['needs-human'] })
    },
    onError: (err: unknown) => {
      setResolveError(
        err instanceof ApiError ? err.message : (err as Error).message,
      )
    },
  })

  /** 状态筛选变更 */
  const handleStatusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setStatus(e.target.value as '' | NeedsHumanStatus)
    setCurrent(1)
  }

  /** 分页变更 */
  const handlePageChange = (next: number, nextSize: number) => {
    if (nextSize !== pageSize) {
      setPageSize(nextSize)
    } else {
      setCurrent(next)
    }
  }

  /** 展开行 */
  const handleRowClick = (queueId: string) => {
    setExpandedId(expandedId === queueId ? null : queueId)
  }

  /** 处置 */
  const handleResolve = useCallback(
    (queueId: string, action: ResolveAction) => {
      setResolveError(null)
      resolveMutation.mutate({ queueId, action })
    },
    [resolveMutation],
  )

  const records: NeedsHumanQueueVO[] = data ?? []
  const total: number = records.length

  return (
    <div className="tasks-page needs-human-page">
      {/* region 页面标题 */}
      <div className="tasks-header">
        <div>
          <h1 className="page-title">
            <IconShield size={22} /> 人工接管队列
          </h1>
          <p className="page-subtitle">
            LLM 调用重试耗尽后的事件队列，操作员查看详情并处置（跳过 / 人工处理 / 终止）
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

      {/* region 筛选栏 */}
      <div className="tasks-toolbar glass-card-static">
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">状态</label>
          <select
            className="select toolbar-select"
            value={status}
            onChange={handleStatusChange}
          >
            {STATUS_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
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
      {resolveError && (
        <div className="form-error" style={{ margin: '16px 0' }}>
          <IconAlert />
          处置失败：{resolveError}
        </div>
      )}
      {/* endregion */}

      {/* region 队列表格 */}
      <div className="tasks-table-wrapper glass-card-static">
        {isLoading ? (
          <div className="tasks-empty">加载中…</div>
        ) : records.length === 0 ? (
          <div className="tasks-empty">
            <IconShield size={36} />
            <div className="tasks-empty-title">暂无 NEEDS_HUMAN 事件</div>
            <div className="tasks-empty-desc">
              {status ? '当前筛选条件下没有匹配的事件' : 'LLM 调用正常，无需人工介入'}
            </div>
          </div>
        ) : (
          <table className="tasks-table">
            <thead>
              <tr>
                <th style={{ width: '5%' }}></th>
                <th style={{ width: '12%' }}>队列 ID</th>
                <th style={{ width: '12%' }}>任务 ID</th>
                <th style={{ width: '10%' }}>上下文</th>
                <th style={{ width: '8%' }}>尝试次数</th>
                <th style={{ width: '10%' }}>状态</th>
                <th style={{ width: '15%' }}>创建时间</th>
                <th style={{ width: '13%' }}>处置</th>
              </tr>
            </thead>
            <tbody>
              {records.map((item) => (
                <NeedsHumanRow
                  key={item.queueId}
                  item={item}
                  expanded={expandedId === item.queueId}
                  onToggle={() => handleRowClick(item.queueId)}
                  onResolve={handleResolve}
                  onNavigateTask={(taskId) => navigate(`/tasks/${taskId}`)}
                  resolving={resolveMutation.isPending}
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
            pages={Math.ceil(total / pageSize)}
            onChange={handlePageChange}
            disabled={isFetching}
          />
        </div>
      )}
      {/* endregion */}
    </div>
  )
}

/**
 * NEEDS_HUMAN 行组件
 */
function NeedsHumanRow({
  item,
  expanded,
  onToggle,
  onResolve,
  onNavigateTask,
  resolving,
}: {
  item: NeedsHumanQueueVO
  expanded: boolean
  onToggle: () => void
  onResolve: (queueId: string, action: ResolveAction) => void
  onNavigateTask: (taskId: string) => void
  resolving: boolean
}) {
  const createTime = dayjs(item.createTime).format('YYYY-MM-DD HH:mm:ss')
  const isPending = item.status === 'PENDING'

  return (
    <>
      <tr className="task-row">
        <td className="cell-expand">
          <button
            type="button"
            className="cell-expand-btn"
            onClick={onToggle}
            aria-label={expanded ? '收起详情' : '展开详情'}
          >
            <IconChevronDown
              size={14}
              style={{
                transform: expanded ? 'rotate(180deg)' : 'none',
                transition: 'transform 0.2s',
              }}
            />
          </button>
        </td>
        <td className="cell-mono">
          <span className="task-id-chip" title={item.queueId}>
            #{item.queueId.slice(-10)}
          </span>
        </td>
        <td className="cell-mono">
          <span
            className="task-id-chip"
            title={item.taskId}
            style={{ cursor: 'pointer' }}
            onClick={(e) => {
              e.stopPropagation()
              onNavigateTask(item.taskId)
            }}
          >
            #{item.taskId.slice(-10)}
          </span>
        </td>
        <td>
          <span className="context-badge">{item.contextName}</span>
        </td>
        <td className="cell-mono">{item.attempts}</td>
        <td>
          <span className={`status-badge ${isPending ? 'status-pending' : 'status-resolved'}`}>
            {isPending ? '待处理' : '已处置'}
          </span>
        </td>
        <td className="cell-mono cell-time">{createTime}</td>
        <td>
          {isPending ? (
            <span className="status-badge status-pending">待处置</span>
          ) : (
            <span className="resolve-action-badge">
              {item.resolveAction === 'skip' && '已跳过'}
              {item.resolveAction === 'manual' && '已人工处理'}
              {item.resolveAction === 'abort' && '已终止'}
            </span>
          )}
        </td>
      </tr>
      {expanded && (
        <tr className="detail-row">
          <td colSpan={8}>
            <div className="needs-human-detail">
              {/* region 左侧：截图 */}
              <div className="detail-section">
                <div className="detail-section-title">
                  <IconCamera size={14} /> 截图
                </div>
                {item.screenshotUrl ? (
                  <img
                    src={item.screenshotUrl}
                    alt="错误截图"
                    className="detail-screenshot"
                  />
                ) : (
                  <div className="detail-empty">暂无截图</div>
                )}
              </div>
              {/* endregion */}

              {/* region 右侧：LLM 输出 + 错误 */}
              <div className="detail-info">
                <div className="detail-section">
                  <div className="detail-section-title">
                    <IconAlert size={14} /> 校验错误
                  </div>
                  <pre className="detail-code-block detail-error">
                    {item.validationError || '无错误信息'}
                  </pre>
                </div>
                <div className="detail-section">
                  <div className="detail-section-title">
                    <IconClock size={14} /> LLM 原始输出
                  </div>
                  <pre className="detail-code-block">
                    {item.llmRawOutput || '无原始输出'}
                  </pre>
                </div>
              </div>
              {/* endregion */}
            </div>

            {/* region 处置按钮 */}
            {isPending && (
              <div className="resolve-actions">
                <button
                  type="button"
                  className="btn btn-ghost btn-sm resolve-btn-skip"
                  onClick={(e) => {
                    e.stopPropagation()
                    onResolve(item.queueId, 'skip')
                  }}
                  disabled={resolving}
                >
                  <IconSkip size={14} />
                  跳过（续跑任务）
                </button>
                <button
                  type="button"
                  className="btn btn-primary btn-sm resolve-btn-manual"
                  onClick={(e) => {
                    e.stopPropagation()
                    onResolve(item.queueId, 'manual')
                  }}
                  disabled={resolving}
                >
                  <IconHand size={14} />
                  人工已处理（续跑任务）
                </button>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm resolve-btn-abort"
                  onClick={(e) => {
                    e.stopPropagation()
                    onResolve(item.queueId, 'abort')
                  }}
                  disabled={resolving}
                >
                  <IconStop size={14} />
                  终止任务
                </button>
              </div>
            )}
            {/* endregion */}
          </td>
        </tr>
      )}
    </>
  )
}

export default NeedsHumanPage

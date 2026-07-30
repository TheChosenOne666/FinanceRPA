/**
 * 工作流执行历史页
 *
 * 功能（M3.6）：
 * - 展示指定工作流模板的所有执行任务记录
 * - 状态筛选（全部 / PENDING / EXECUTING / SUCCESS / FAILED / NEEDS_HUMAN / ABORTED）
 * - 分页展示
 * - 点击行跳转任务详情页
 * - 自动轮询刷新（执行中任务存在时刷新间隔更短）
 *
 * 数据来源：GET /tasks?workflowId={workflowId}
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { workflowApi } from '@/api/workflows'
import { taskApi } from '@/api/tasks'
import type { TaskQueryRequest, TaskStatus, TaskVO } from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import Pagination from '@/components/Pagination'
import StatusBadge from '@/components/StatusBadge'
import {
  IconAlert,
  IconArrowLeft,
  IconClock,
  IconRefresh,
  IconWorkflow,
} from '@/components/Icons'

/** 默认页大小 */
const DEFAULT_PAGE_SIZE = 10

/** 状态筛选选项 */
const STATUS_OPTIONS: Array<{ value: '' | TaskStatus; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待执行' },
  { value: 'EXECUTING', label: '执行中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'NEEDS_HUMAN', label: '需人工' },
  { value: 'ABORTED', label: '已终止' },
]

/** 工作流执行历史页 */
function WorkflowRunsPage() {
  const navigate = useNavigate()
  const { workflowId } = useParams<{ workflowId: string }>()

  // 1. 查询条件
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [status, setStatus] = useState<'' | TaskStatus>('')

  // 2. 查询工作流详情（用于标题展示）
  const { data: workflow } = useQuery({
    queryKey: ['workflow', workflowId],
    queryFn: () => workflowApi.getWorkflow(workflowId!),
    enabled: !!workflowId,
    refetchOnWindowFocus: false,
  })

  // 3. 查询参数
  const queryKey = useMemo(
    () => ['workflow-runs', { workflowId, current, pageSize, status }] as const,
    [workflowId, current, pageSize, status],
  )

  // 4. 查询任务列表（按 workflowId 筛选）
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn: () =>
      taskApi.listTasks({
        current,
        pageSize,
        status,
        workflowId,
        sortField: 'createTime',
        sortOrder: 'desc',
      } satisfies TaskQueryRequest),
    enabled: !!workflowId,
    refetchOnWindowFocus: false,
  })

  // 5. 自动轮询：列表中存在执行中 / 待执行任务时 5s 刷新，否则 30s
  const hasActiveTask = (data?.records ?? []).some(
    (t) => t.status === 'EXECUTING' || t.status === 'PENDING',
  )
  const refreshMs = hasActiveTask ? 5000 : 30000
  const refreshMsRef = useRef(refreshMs)
  refreshMsRef.current = refreshMs
  useEffect(() => {
    const id = setInterval(() => {
      refetch()
    }, refreshMsRef.current)
    return () => clearInterval(id)
  }, [refetch])

  /** 状态筛选变更 */
  const handleStatusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setStatus(e.target.value as '' | TaskStatus)
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

  const records: TaskVO[] = data?.records ?? []
  const total: number = data?.total ?? 0

  return (
    <div className="workflow-runs-page">
      {/* region 顶部：返回 + 标题 + 操作 */}
      <div className="workflow-runs-header">
        <button
          type="button"
          className="btn btn-ghost btn-sm workflow-runs-back"
          onClick={() => navigate(workflowId ? `/workflows/${workflowId}` : '/workflows')}
        >
          <IconArrowLeft size={14} />
          返回详情
        </button>
        <div className="workflow-runs-title">
          <h1 className="page-title">
            <IconClock size={20} />
            执行历史
            {workflow && <span className="workflow-runs-name"> · {workflow.name}</span>}
          </h1>
          <p className="page-subtitle">
            查看该工作流的所有执行记录，点击行进入任务详情
          </p>
        </div>
        <div className="workflow-runs-actions">
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

      {/* region 状态筛选 */}
      <div className="workflow-runs-toolbar glass-card-static">
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
        {workflowId && (
          <div className="workflow-runs-meta">
            <IconWorkflow size={12} />
            <span className="cell-mono">{workflowId}</span>
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

      {/* region 任务表格 */}
      <div className="tasks-table-wrapper glass-card-static">
        {isLoading ? (
          <div className="tasks-empty">加载中…</div>
        ) : records.length === 0 ? (
          <div className="tasks-empty">
            <IconClock size={36} />
            <div className="tasks-empty-title">暂无执行历史</div>
            <div className="tasks-empty-desc">
              {status
                ? '当前状态筛选下没有匹配的任务'
                : '该工作流尚未被触发过执行'}
            </div>
          </div>
        ) : (
          <table className="tasks-table">
            <thead>
              <tr>
                <th style={{ width: '12%' }}>任务 ID</th>
                <th>任务目标</th>
                <th style={{ width: '11%' }}>状态</th>
                <th style={{ width: '14%' }}>进度</th>
                <th style={{ width: '15%' }}>创建时间</th>
                <th style={{ width: '10%' }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((task) => (
                <TaskRow
                  key={task.taskId}
                  task={task}
                  onClick={() => navigate(`/tasks/${task.taskId}`)}
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
    </div>
  )
}

/**
 * 任务行
 *
 * @param task    任务对象
 * @param onClick 点击行回调
 */
function TaskRow({ task, onClick }: { task: TaskVO; onClick: () => void }) {
  // 1. 时间格式化
  const createTime = dayjs(task.createTime).format('YYYY-MM-DD HH:mm:ss')

  // 2. 进度展示
  const hasProgress = task.totalSteps > 0
  const progressPct = hasProgress
    ? Math.min(100, Math.round((task.currentStep / task.totalSteps) * 100))
    : 0

  return (
    <tr className="task-row" onClick={onClick}>
      <td className="cell-mono">
        <span className="task-id-chip" title={task.taskId}>
          #{task.taskId.slice(-10)}
        </span>
      </td>
      <td className="cell-goal">
        <div className="task-goal-text">{task.goal}</div>
        {task.errorMessage && (
          <div className="task-error-text" title={task.errorMessage}>
            {task.errorMessage}
          </div>
        )}
      </td>
      <td>
        <StatusBadge status={task.status} />
      </td>
      <td>
        {hasProgress ? (
          <div className="task-progress">
            <div className="task-progress-bar">
              <div
                className="task-progress-fill"
                style={{ width: `${progressPct}%` }}
              />
            </div>
            <span className="task-progress-text">
              {task.currentStep}/{task.totalSteps}
            </span>
          </div>
        ) : (
          <span className="task-progress-text-muted">—</span>
        )}
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

export default WorkflowRunsPage

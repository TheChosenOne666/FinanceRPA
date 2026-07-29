/**
 * 任务列表页
 *
 * 功能（M2.5）：
 * - 分页展示当前组织下的任务列表
 * - 状态筛选（全部 / PENDING / EXECUTING / SUCCESS / FAILED / NEEDS_HUMAN / ABORTED）
 * - 关键词搜索（匹配 goal）
 * - 触发新任务入口（弹窗表单）
 * - 点击行跳转任务详情页
 * - 自动轮询刷新（执行中任务存在时刷新间隔更短）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { taskApi } from '@/api/tasks'
import type { TaskQueryRequest, TaskStatus, TaskVO } from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import Pagination from '@/components/Pagination'
import StatusBadge from '@/components/StatusBadge'
import {
  IconAlert,
  IconPlay,
  IconRefresh,
  IconSearch,
  IconTarget,
} from '@/components/Icons'
import TriggerTaskModal from './TriggerTaskModal'

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

/** 任务列表页 */
function TasksPage() {
  const navigate = useNavigate()

  // 1. 查询条件
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [status, setStatus] = useState<'' | TaskStatus>('')
  const [searchInput, setSearchInput] = useState('') // 输入框值（即时变化）
  const [searchText, setSearchText] = useState('') // 实际提交的搜索值（防抖后）

  // 2. 触发任务弹窗
  const [triggerOpen, setTriggerOpen] = useState(false)

  // 3. 搜索防抖（输入停止 400ms 后触发查询）
  useEffect(() => {
    const t = setTimeout(() => {
      setSearchText(searchInput.trim())
      setCurrent(1)
    }, 400)
    return () => clearTimeout(t)
  }, [searchInput])

  // 4. 查询参数（useQuery 依赖项）
  const queryKey = useMemo(
    () => ['tasks', { current, pageSize, status, searchText }] as const,
    [current, pageSize, status, searchText],
  )

  // 5. 查询任务列表
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn: () =>
      taskApi.listTasks({
        current,
        pageSize,
        status,
        searchText,
        sortField: 'createTime',
        sortOrder: 'desc',
      } satisfies TaskQueryRequest),
    refetchOnWindowFocus: false,
  })

  // 6. 自动轮询：列表中存在执行中 / 待执行任务时 5s 刷新，否则 30s
  const hasActiveTask = (data?.records ?? []).some(
    (t) => t.status === 'EXECUTING' || t.status === 'PENDING',
  )
  const refreshMs = hasActiveTask ? 5000 : 30000
  const refreshMsRef = useRef(refreshMs)
  refreshMsRef.current = refreshMs
  useEffect(() => {
    const id = setInterval(() => {
      // 仅在没有手动加载中时轮询
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

  /** 触发任务成功后跳转详情页 */
  const handleTriggered = useCallback(
    (taskId: string) => {
      setTriggerOpen(false)
      navigate(`/tasks/${taskId}`)
    },
    [navigate],
  )

  const records: TaskVO[] = data?.records ?? []
  const total: number = data?.total ?? 0

  return (
    <div className="tasks-page">
      {/* region 页面标题 + 操作区 */}
      <div className="tasks-header">
        <div>
          <h1 className="page-title">
            <IconTarget size={22} /> 任务列表
          </h1>
          <p className="page-subtitle">
            管理当前组织下的自动化任务，查看执行进度与历史结果
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
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={() => setTriggerOpen(true)}
          >
            <IconPlay size={14} />
            触发任务
          </button>
        </div>
      </div>
      {/* endregion */}

      {/* region 筛选栏 */}
      <div className="tasks-toolbar glass-card-static">
        <div className="toolbar-search">
          <IconSearch size={14} className="toolbar-search-icon" />
          <input
            type="text"
            className="input toolbar-input"
            placeholder="搜索任务目标（输入后自动搜索）"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>
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
      {/* endregion */}

      {/* region 任务表格 */}
      <div className="tasks-table-wrapper glass-card-static">
        {isLoading ? (
          <div className="tasks-empty">加载中…</div>
        ) : records.length === 0 ? (
          <div className="tasks-empty">
            <IconTarget size={36} />
            <div className="tasks-empty-title">暂无任务</div>
            <div className="tasks-empty-desc">
              {searchText || status
                ? '当前筛选条件下没有匹配的任务'
                : '点击右上角"触发任务"开始第一个自动化任务'}
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

      {/* region 触发任务弹窗 */}
      {triggerOpen && (
        <TriggerTaskModal
          onClose={() => setTriggerOpen(false)}
          onTriggered={handleTriggered}
        />
      )}
      {/* endregion */}
    </div>
  )
}

/**
 * 任务行
 *
 * @param task  任务对象
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

export default TasksPage

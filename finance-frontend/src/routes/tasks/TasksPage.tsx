/**
 * 任务列表页
 *
 * 功能（M2.5）：
 * - 分页展示当前组织下的任务列表
 * - 状态筛选（全部 / PENDING / EXECUTING / SUCCESS / FAILED / NEEDS_HUMAN / ABORTED）
 * - 关键词搜索（匹配 goal）
 * - 触发新任务入口（弹窗表单）
 * - 点击卡片跳转任务详情页
 * - 自动轮询刷新（执行中任务存在时刷新间隔更短）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
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

/**
 * 任务状态图标配置
 *
 * 映射任务状态到状态图标的样式类名 + SVG 图标。
 * 对齐原型 03-tasks.html 的 .task-status-icon 五种颜色变体：
 * - SUCCESS        → success（绿色勾选）
 * - EXECUTING      → running（蓝色闪电）
 * - NEEDS_HUMAN    → takeover（橙色警告）
 * - PENDING        → pending（灰色沙漏）
 * - FAILED/ABORTED → failed（红色 X）
 */
const STATUS_ICON_CONFIG: Record<TaskStatus, { iconClass: string; icon: ReactNode }> = {
  SUCCESS: {
    iconClass: 'success',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
        <polyline points="22 4 12 14.01 9 11.01" />
      </svg>
    ),
  },
  EXECUTING: {
    iconClass: 'running',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
      </svg>
    ),
  },
  NEEDS_HUMAN: {
    iconClass: 'takeover',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    ),
  },
  PENDING: {
    iconClass: 'pending',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 22h14" />
        <path d="M5 2h14" />
        <path d="M17 22v-4.172a2 2 0 0 0-.586-1.414L12 12l-4.414 4.414A2 2 0 0 0 7 17.828V22" />
        <path d="M7 2v4.172a2 2 0 0 0 .586 1.414L12 12l4.414-4.414A2 2 0 0 0 17 6.172V2" />
      </svg>
    ),
  },
  FAILED: {
    iconClass: 'failed',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" />
        <line x1="15" y1="9" x2="9" y2="15" />
        <line x1="9" y1="9" x2="15" y2="15" />
      </svg>
    ),
  },
  ABORTED: {
    iconClass: 'failed',
    icon: (
      <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" />
        <line x1="15" y1="9" x2="9" y2="15" />
        <line x1="9" y1="9" x2="15" y2="15" />
      </svg>
    ),
  },
}

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
            任务列表
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

      {/* region 筛选栏（对齐原型 .filter-bar 网格布局） */}
      <div className="tasks-toolbar glass-card-static">
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">搜索任务</label>
          <div className="toolbar-search">
            <IconSearch size={14} className="toolbar-search-icon" />
            <input
              type="text"
              className="input toolbar-input"
              placeholder="输入任务目标关键词（自动搜索）"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>
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

      {/* region 任务卡片列表 */}
      <div className="tasks-card-list">
        {isLoading ? (
          <div className="tasks-empty glass-card-static">加载中…</div>
        ) : records.length === 0 ? (
          <div className="tasks-empty glass-card-static">
            <IconTarget size={36} />
            <div className="tasks-empty-title">暂无任务</div>
            <div className="tasks-empty-desc">
              {searchText || status
                ? '当前筛选条件下没有匹配的任务'
                : '点击右上角"触发任务"开始第一个自动化任务'}
            </div>
          </div>
        ) : (
          records.map((task) => (
            <TaskCard
              key={task.taskId}
              task={task}
              onClick={() => navigate(`/tasks/${task.taskId}`)}
            />
          ))
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
 * 任务卡片
 *
 * 对齐原型 03-tasks.html 的 .task-card 结构：
 * 左侧状态图标 + 中间任务主体（标题 / 元信息 / 统计 / 进度）+ 右侧操作按钮。
 *
 * @param task    任务对象
 * @param onClick 点击卡片回调（跳转详情页）
 */
function TaskCard({ task, onClick }: { task: TaskVO; onClick: () => void }) {
  // 1. 时间格式化
  const createTime = dayjs(task.createTime).format('YYYY-MM-DD HH:mm:ss')

  // 2. 进度展示（仅当 totalSteps > 0 时显示进度条）
  const hasProgress = task.totalSteps > 0
  const progressPct = hasProgress
    ? Math.min(100, Math.round((task.currentStep / task.totalSteps) * 100))
    : 0

  // 3. 状态图标配置
  const iconConfig = STATUS_ICON_CONFIG[task.status]

  return (
    <div className="glass-card task-card" onClick={onClick}>
      {/* region 左侧状态图标 */}
      <div className={`task-status-icon ${iconConfig.iconClass}`}>
        {iconConfig.icon}
      </div>
      {/* endregion */}

      {/* region 任务主体 */}
      <div className="task-body">
        {/* 标题行：task-id 标签 + task-name */}
        <div className="task-title-row">
          <span className="task-id" title={task.taskId}>
            #{task.taskId.slice(-10)}
          </span>
          <span className="task-name">{task.goal}</span>
        </div>

        {/* 元信息：创建时间 + 用户 ID */}
        <div className="task-meta">
          创建于 {createTime}
          <span className="dot">·</span>
          用户 {task.userId}
        </div>

        {/* 统计信息：状态徽章 + 步骤进度 + 错误信息 */}
        <div className="task-stats">
          <span className="stat-item">
            <StatusBadge status={task.status} />
          </span>
          {hasProgress && (
            <span className="stat-item">
              步骤 <span className="mono">{task.currentStep}/{task.totalSteps}</span>
            </span>
          )}
          {task.errorMessage && (
            <span className="stat-item task-stat-error" title={task.errorMessage}>
              错误：{task.errorMessage}
            </span>
          )}
        </div>

        {/* 进度条（仅当 totalSteps > 0 时显示） */}
        {hasProgress && (
          <div className="task-progress-wrap">
            <div className="progress">
              <div
                className="progress-bar"
                style={{ width: `${progressPct}%` }}
              />
            </div>
            <span className="pct">{progressPct}%</span>
          </div>
        )}
      </div>
      {/* endregion */}

      {/* region 操作按钮 */}
      <div className="task-actions">
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={(e) => {
            e.stopPropagation()
            onClick()
          }}
        >
          查看详情
        </button>
      </div>
      {/* endregion */}
    </div>
  )
}

export default TasksPage

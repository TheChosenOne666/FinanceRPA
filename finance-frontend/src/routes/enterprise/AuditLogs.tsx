/**
 * 审计日志页面
 *
 * 功能（M7.5）：
 * - 列表视图：多维筛选（任务ID / 风险 / 操作类型 / 执行结果 / 时间范围）+ 分页 + 排序
 * - 详情弹窗：基本信息 + 操作参数 JSON + 截图对比（before/after）+ LLM 信息
 * - 导出按钮：按当前筛选条件触发 CSV 下载（后端限制 10000 条）
 * - 时间线视图：按任务维度聚合，展示操作时间线
 *
 * 对齐后端 com.finrpa.audit.controller.AuditController：
 * - GET  /v1/audit/logs           分页多维检索
 * - GET  /v1/audit/logs/export    CSV 导出（二进制流）
 * - GET  /v1/audit/logs/{auditId} 审计详情
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { auditApi } from '@/api/audit'
import type {
  AuditActionType,
  AuditExecutionResult,
  AuditLogQueryRequest,
  AuditLogVO,
  AuditSortField,
  SortOrder,
  WorkflowRiskLevel,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import Pagination from '@/components/Pagination'
import {
  IconAlert,
  IconCamera,
  IconChevronDown,
  IconClose,
  IconDownload,
  IconList,
  IconRefresh,
  IconShield,
  IconTerminal,
} from '@/components/Icons'

/** 默认页大小 */
const DEFAULT_PAGE_SIZE = 10

/** Tab 类型：列表 / 时间线 */
type AuditTab = 'list' | 'timeline'

/** 风险等级标签 */
const RISK_LABELS: Record<WorkflowRiskLevel, string> = {
  low: '低',
  medium: '中',
  high: '高',
  critical: '极高',
}

/** 执行结果标签 */
const RESULT_LABELS: Record<AuditExecutionResult, string> = {
  success: '成功',
  failed: '失败',
}

/** 排序字段标签 */
const SORT_FIELD_OPTIONS: Array<{ value: AuditSortField; label: string }> = [
  { value: 'createTime', label: '创建时间' },
  { value: 'startedAt', label: '开始时间' },
  { value: 'durationMs', label: '执行耗时' },
  { value: 'riskLevel', label: '风险等级' },
  { value: 'auditId', label: '审计 ID' },
  { value: 'taskId', label: '任务 ID' },
]

/** 风险等级筛选选项 */
const RISK_OPTIONS: Array<{ value: '' | WorkflowRiskLevel; label: string }> = [
  { value: '', label: '全部风险' },
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'critical', label: '极高' },
]

/** 操作类型筛选选项 */
const ACTION_OPTIONS: Array<{ value: '' | AuditActionType; label: string }> = [
  { value: '', label: '全部操作' },
  { value: 'NAVIGATE', label: '页面跳转' },
  { value: 'CLICK', label: '元素点击' },
  { value: 'INPUT_TEXT', label: '文本输入' },
  { value: 'LOGIN', label: '登录操作' },
  { value: 'FILE_DOWNLOAD', label: '文件下载' },
  { value: 'FORM_FILL', label: '表单填写' },
  { value: 'WAIT', label: '等待' },
  { value: 'SCREENSHOT', label: '截图' },
]

/** 执行结果筛选选项 */
const RESULT_OPTIONS: Array<{ value: '' | AuditExecutionResult; label: string }> = [
  { value: '', label: '全部结果' },
  { value: 'success', label: '成功' },
  { value: 'failed', label: '失败' },
]

/** 时间线视图每页任务数 */
const TIMELINE_PAGE_SIZE = 10

/**
 * 格式化耗时（毫秒 → 人类可读）
 *
 * @param ms 毫秒数
 * @returns 形如 "1.2s" / "2分3秒" 的可读字符串
 */
function formatDuration(ms?: number): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(2)}s`
  const minutes = Math.floor(ms / 60_000)
  const seconds = Math.floor((ms % 60_000) / 1000)
  return `${minutes}分${seconds}秒`
}

/**
 * 格式化时间戳
 *
 * @param ts ISO 字符串
 * @returns YYYY-MM-DD HH:mm:ss 格式
 */
function formatTime(ts?: string): string {
  if (!ts) return '—'
  return dayjs(ts).format('YYYY-MM-DD HH:mm:ss')
}

/**
 * 美化 JSON 字符串展示（解析失败返回原值）
 *
 * @param jsonStr JSON 字符串
 * @returns 缩进 2 空格的 JSON 字符串
 */
function prettyJson(jsonStr?: string): string {
  if (!jsonStr) return ''
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

/**
 * 将 datetime-local 输入值（YYYY-MM-DDTHH:mm）转为后端期望的 ISO 字符串
 *
 * @param localValue 本地时间输入值
 * @returns ISO 字符串（带时区），空值返回 undefined
 */
function toIso(localValue: string): string | undefined {
  if (!localValue) return undefined
  return dayjs(localValue).toISOString()
}

/** 审计日志页面 */
function AuditLogs() {
  // 1. Tab 切换：列表 / 时间线
  const [tab, setTab] = useState<AuditTab>('list')

  // 2. 分页
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)

  // 3. 筛选条件
  const [taskId, setTaskId] = useState('')
  const [riskLevel, setRiskLevel] = useState<'' | WorkflowRiskLevel>('')
  const [actionType, setActionType] = useState<'' | AuditActionType>('')
  const [executionResult, setExecutionResult] = useState<
    '' | AuditExecutionResult
  >('')
  const [startTimeLocal, setStartTimeLocal] = useState('')
  const [endTimeLocal, setEndTimeLocal] = useState('')

  // 4. 排序
  const [sortField, setSortField] = useState<AuditSortField>('createTime')
  const [sortOrder, setSortOrder] = useState<SortOrder>('descend')

  // 5. 详情弹窗
  const [selectedLog, setSelectedLog] = useState<AuditLogVO | null>(null)

  // 6. 导出状态
  const [exporting, setExporting] = useState(false)
  const [exportMsg, setExportMsg] = useState<{
    type: 'success' | 'error'
    text: string
  } | null>(null)

  // 7. 查询参数构造
  const query: AuditLogQueryRequest = useMemo(
    () => ({
      current,
      pageSize,
      sortField,
      sortOrder,
      taskId: taskId.trim() || undefined,
      riskLevel,
      actionType,
      executionResult,
      startTime: toIso(startTimeLocal),
      endTime: toIso(endTimeLocal),
    }),
    [
      current,
      pageSize,
      sortField,
      sortOrder,
      taskId,
      riskLevel,
      actionType,
      executionResult,
      startTimeLocal,
      endTimeLocal,
    ],
  )

  const queryKey = useMemo(
    () => ['auditLogs', tab, query] as const,
    [tab, query],
  )

  // 列表视图查询
  const listQuery = useQuery({
    queryKey,
    queryFn: () => auditApi.listAuditLogs(query),
    enabled: tab === 'list',
    refetchOnWindowFocus: false,
  })

  // 时间线视图查询：同参数但取前 TIMELINE_PAGE_SIZE 条（按 startedAt 升序）
  const timelineQuery = useQuery({
    queryKey: ['auditLogs', 'timeline', query] as const,
    queryFn: () =>
      auditApi.listAuditLogs({
        ...query,
        pageSize: 200, // 时间线聚合需要更多数据
        sortField: 'startedAt',
        sortOrder: 'ascend',
      }),
    enabled: tab === 'timeline',
    refetchOnWindowFocus: false,
  })

  /** 切换 Tab：保留筛选条件 */
  const handleTabChange = (next: AuditTab) => {
    if (next === tab) return
    setTab(next)
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

  /** 重置筛选条件 */
  const handleReset = () => {
    setTaskId('')
    setRiskLevel('')
    setActionType('')
    setExecutionResult('')
    setStartTimeLocal('')
    setEndTimeLocal('')
    setSortField('createTime')
    setSortOrder('descend')
    setCurrent(1)
  }

  /** 触发 CSV 导出 */
  const handleExport = async () => {
    setExporting(true)
    setExportMsg(null)
    const result = await auditApi.exportAuditLogs({
      ...query,
      // 导出忽略分页，由后端按 EXPORT_MAX_ROWS 限制
      current: 1,
      pageSize: 10_000,
    })
    setExporting(false)
    setExportMsg(
      result.success
        ? { type: 'success', text: 'CSV 导出已开始下载' }
        : { type: 'error', text: result.message || '导出失败' },
    )
    // 3 秒后清空提示
    setTimeout(() => setExportMsg(null), 3000)
  }

  // 列表数据
  const listData = listQuery.data
  const records: AuditLogVO[] = listData?.records ?? []
  const total: number = listData?.total ?? 0
  const isLoading = tab === 'list' ? listQuery.isLoading : timelineQuery.isLoading
  const isFetching = tab === 'list' ? listQuery.isFetching : timelineQuery.isFetching
  const error = tab === 'list' ? listQuery.error : timelineQuery.error

  // 时间线数据：按 taskId 聚合
  const timelineGroups = useMemo(() => {
    const list = timelineQuery.data?.records ?? []
    const groups = new Map<string, AuditLogVO[]>()
    for (const log of list) {
      const arr = groups.get(log.taskId) ?? []
      arr.push(log)
      groups.set(log.taskId, arr)
    }
    // 转为数组，按任务的首条记录 startedAt 倒序
    return Array.from(groups.entries())
      .map(([taskId, logs]) => ({
        taskId,
        logs: logs.sort(
          (a, b) =>
            dayjs(a.startedAt ?? a.createTime).valueOf() -
            dayjs(b.startedAt ?? b.createTime).valueOf(),
        ),
        firstStartedAt: logs[0]?.startedAt ?? logs[0]?.createTime,
      }))
      .sort(
        (a, b) =>
          dayjs(b.firstStartedAt).valueOf() -
          dayjs(a.firstStartedAt).valueOf(),
      )
  }, [timelineQuery.data])

  const hasFilters =
    !!taskId ||
    !!riskLevel ||
    !!actionType ||
    !!executionResult ||
    !!startTimeLocal ||
    !!endTimeLocal

  return (
    <div className="tasks-page">
      {/* region 页面标题 + 操作区 */}
      <div className="tasks-header">
        <div>
          <h1 className="page-title">
            <IconShield size={22} /> 审计日志
          </h1>
          <p className="page-subtitle">
            全链路操作审计 · 多维检索 · 截图对比 · CSV 导出
          </p>
        </div>
        <div className="tasks-header-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => {
              if (tab === 'list') listQuery.refetch()
              else timelineQuery.refetch()
            }}
            disabled={isFetching}
            title="刷新列表"
          >
            <IconRefresh size={14} />
            {isFetching ? '刷新中…' : '刷新'}
          </button>
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={handleExport}
            disabled={exporting || isLoading}
            title="按当前筛选条件导出 CSV（最多 10000 条）"
          >
            <IconDownload size={14} />
            {exporting ? '导出中…' : '导出 CSV'}
          </button>
        </div>
      </div>
      {/* endregion */}

      {/* region 导出状态提示 */}
      {exportMsg && (
        <div
          className={`audit-export-toast${
            exportMsg.type === 'error' ? ' audit-export-toast-error' : ''
          }`}
        >
          {exportMsg.type === 'success' ? <IconCamera size={14} /> : <IconAlert size={14} />}
          {exportMsg.text}
        </div>
      )}
      {/* endregion */}

      {/* region Tab 切换：列表 / 时间线 */}
      <div className="approval-tabs">
        <button
          type="button"
          className={`approval-tab${tab === 'list' ? ' approval-tab-active' : ''}`}
          onClick={() => handleTabChange('list')}
        >
          <IconList size={13} />
          列表视图
        </button>
        <button
          type="button"
          className={`approval-tab${tab === 'timeline' ? ' approval-tab-active' : ''}`}
          onClick={() => handleTabChange('timeline')}
        >
          <IconTerminal size={13} />
          时间线视图
        </button>
      </div>
      {/* endregion */}

      {/* region 筛选栏 */}
      <div className="tasks-toolbar glass-card-static audit-toolbar">
        <div className="toolbar-filter audit-filter-taskid">
          <label className="toolbar-filter-label">任务 ID</label>
          <input
            type="text"
            className="input audit-filter-input"
            placeholder="精确匹配任务 ID"
            value={taskId}
            onChange={(e) => {
              setTaskId(e.target.value)
              setCurrent(1)
            }}
          />
        </div>
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
          <label className="toolbar-filter-label">操作类型</label>
          <select
            className="select toolbar-select"
            value={actionType}
            onChange={(e) => {
              setActionType(e.target.value as '' | AuditActionType)
              setCurrent(1)
            }}
          >
            {ACTION_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">执行结果</label>
          <select
            className="select toolbar-select"
            value={executionResult}
            onChange={(e) => {
              setExecutionResult(e.target.value as '' | AuditExecutionResult)
              setCurrent(1)
            }}
          >
            {RESULT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">开始时间起</label>
          <input
            type="datetime-local"
            className="input audit-filter-input"
            value={startTimeLocal}
            onChange={(e) => {
              setStartTimeLocal(e.target.value)
              setCurrent(1)
            }}
          />
        </div>
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">开始时间止</label>
          <input
            type="datetime-local"
            className="input audit-filter-input"
            value={endTimeLocal}
            onChange={(e) => {
              setEndTimeLocal(e.target.value)
              setCurrent(1)
            }}
          />
        </div>
        {tab === 'list' && (
          <>
            <div className="toolbar-filter">
              <label className="toolbar-filter-label">排序字段</label>
              <select
                className="select toolbar-select"
                value={sortField}
                onChange={(e) => {
                  setSortField(e.target.value as AuditSortField)
                  setCurrent(1)
                }}
              >
                {SORT_FIELD_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="toolbar-filter">
              <label className="toolbar-filter-label">排序顺序</label>
              <select
                className="select toolbar-select"
                value={sortOrder}
                onChange={(e) => {
                  setSortOrder(e.target.value as SortOrder)
                  setCurrent(1)
                }}
              >
                <option value="descend">倒序</option>
                <option value="ascend">正序</option>
              </select>
            </div>
          </>
        )}
        <div className="toolbar-filter audit-filter-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={handleReset}
            disabled={!hasFilters}
          >
            重置
          </button>
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

      {/* region 列表视图 */}
      {tab === 'list' && (
        <>
          <div className="tasks-table-wrapper glass-card-static">
            {isLoading ? (
              <div className="tasks-empty">加载中…</div>
            ) : records.length === 0 ? (
              <div className="tasks-empty">
                <IconShield size={36} />
                <div className="tasks-empty-title">暂无审计日志</div>
                <div className="tasks-empty-desc">
                  {hasFilters
                    ? '当前筛选条件下没有匹配的审计记录'
                    : '尚未有任何任务执行审计上报'}
                </div>
              </div>
            ) : (
              <table className="tasks-table audit-table">
                <thead>
                  <tr>
                    <th style={{ width: '11%' }}>审计 ID</th>
                    <th style={{ width: '11%' }}>任务 ID</th>
                    <th style={{ width: '10%' }}>操作类型</th>
                    <th style={{ width: '8%' }}>执行结果</th>
                    <th style={{ width: '8%' }}>风险等级</th>
                    <th style={{ width: '11%' }}>开始时间</th>
                    <th style={{ width: '8%' }}>耗时</th>
                    <th style={{ width: '8%' }}>LLM</th>
                    <th>页面</th>
                    <th style={{ width: '7%' }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map((log) => (
                    <AuditRow
                      key={log.auditId}
                      log={log}
                      onClick={() => setSelectedLog(log)}
                    />
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {total > 0 && (
            <div className="tasks-pagination">
              <Pagination
                current={current}
                pageSize={pageSize}
                total={total}
                pages={listData?.pages}
                onChange={handlePageChange}
                disabled={isFetching}
              />
            </div>
          )}
        </>
      )}
      {/* endregion */}

      {/* region 时间线视图 */}
      {tab === 'timeline' && (
        <div className="tasks-table-wrapper glass-card-static">
          {isLoading ? (
            <div className="tasks-empty">加载中…</div>
          ) : timelineGroups.length === 0 ? (
            <div className="tasks-empty">
              <IconTerminal size={36} />
              <div className="tasks-empty-title">暂无时间线数据</div>
              <div className="tasks-empty-desc">
                {hasFilters
                  ? '当前筛选条件下没有匹配的审计记录'
                  : '尚未有任何任务执行审计上报'}
              </div>
            </div>
          ) : (
            <div className="audit-timeline-container">
              {timelineGroups.slice(0, TIMELINE_PAGE_SIZE).map((group) => (
                <AuditTimelineGroup
                  key={group.taskId}
                  taskId={group.taskId}
                  logs={group.logs}
                  onSelectLog={(log) => setSelectedLog(log)}
                />
              ))}
              {timelineGroups.length > TIMELINE_PAGE_SIZE && (
                <div className="audit-timeline-more">
                  仅展示前 {TIMELINE_PAGE_SIZE} 个任务，共 {timelineGroups.length} 个
                </div>
              )}
            </div>
          )}
        </div>
      )}
      {/* endregion */}

      {/* region 详情弹窗 */}
      {selectedLog && (
        <AuditDetailModal log={selectedLog} onClose={() => setSelectedLog(null)} />
      )}
      {/* endregion */}
    </div>
  )
}

/**
 * 审计表格行
 *
 * @param log    审计日志
 * @param onClick 点击行回调（打开详情）
 */
function AuditRow({
  log,
  onClick,
}: {
  log: AuditLogVO
  onClick: () => void
}) {
  return (
    <tr className="task-row" onClick={onClick}>
      <td className="cell-mono">
        <span className="task-id-chip" title={log.auditId}>
          #{log.auditId.slice(-10)}
        </span>
      </td>
      <td className="cell-mono">
        <span className="task-id-chip" title={log.taskId}>
          #{log.taskId.slice(-10)}
        </span>
      </td>
      <td className="cell-mono">{log.actionType}</td>
      <td>
        <span
          className={`tag tag-result-${log.executionResult}`}
          title={log.errorMessage}
        >
          {RESULT_LABELS[log.executionResult]}
        </span>
      </td>
      <td>
        {log.riskLevel ? (
          <span className={`tag tag-risk-${log.riskLevel}`}>
            {RISK_LABELS[log.riskLevel]}
          </span>
        ) : (
          <span className="text-muted">—</span>
        )}
      </td>
      <td className="cell-mono cell-time">{formatTime(log.startedAt)}</td>
      <td className="cell-mono">{formatDuration(log.durationMs)}</td>
      <td className="cell-mono">
        {log.llmModel ? (
          <span title={`${log.llmModel} · ${log.llmTokensUsed ?? 0} tokens`}>
            {log.llmModel}
          </span>
        ) : (
          <span className="text-muted">—</span>
        )}
      </td>
      <td className="cell-url" title={log.pageUrl}>
        {log.pageUrl || '—'}
      </td>
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

/**
 * 时间线分组组件：按 taskId 聚合展示操作时间线
 *
 * @param taskId 任务 ID
 * @param logs   该任务下的所有审计日志（按时间升序）
 * @param onSelectLog 点击单条日志回调（打开详情）
 */
function AuditTimelineGroup({
  taskId,
  logs,
  onSelectLog,
}: {
  taskId: string
  logs: AuditLogVO[]
  onSelectLog: (log: AuditLogVO) => void
}) {
  const [expanded, setExpanded] = useState(true)
  const first = logs[0]
  const last = logs[logs.length - 1]
  const totalDuration = logs.reduce((sum, l) => sum + (l.durationMs ?? 0), 0)
  const successCount = logs.filter((l) => l.executionResult === 'success').length
  const successRate = logs.length > 0 ? (successCount / logs.length) * 100 : 0

  return (
    <div className="audit-timeline-group">
      {/* 任务头部 */}
      <div
        className="audit-timeline-header"
        onClick={() => setExpanded((v) => !v)}
        role="button"
        tabIndex={0}
      >
        <IconChevronDown
          size={14}
          style={{
            transform: expanded ? 'rotate(0deg)' : 'rotate(-90deg)',
            transition: 'transform 0.2s',
          }}
        />
        <span className="audit-timeline-taskid" title={taskId}>
          任务 #{taskId.slice(-10)}
        </span>
        <span className="audit-timeline-meta">
          共 <strong>{logs.length}</strong> 步 · 成功率{' '}
          <strong>{successRate.toFixed(0)}%</strong> · 累计耗时{' '}
          <strong>{formatDuration(totalDuration)}</strong>
        </span>
        <span className="audit-timeline-range">
          {formatTime(first?.startedAt)} → {formatTime(last?.completedAt)}
        </span>
      </div>

      {/* 时间线条目 */}
      {expanded && (
        <ol className="audit-timeline-list">
          {logs.map((log, idx) => (
            <li key={log.auditId} className="audit-timeline-item">
              <div
                className={`audit-timeline-dot audit-timeline-dot-${log.executionResult}${
                  log.riskLevel ? ` audit-timeline-dot-risk-${log.riskLevel}` : ''
                }`}
                title={`第 ${idx + 1} 步`}
              >
                {idx + 1}
              </div>
              <div className="audit-timeline-content" onClick={() => onSelectLog(log)}>
                <div className="audit-timeline-line1">
                  <span className="audit-timeline-action">{log.actionType}</span>
                  {log.riskLevel && (
                    <span className={`tag tag-risk-${log.riskLevel}`}>
                      {RISK_LABELS[log.riskLevel]}
                    </span>
                  )}
                  <span className={`tag tag-result-${log.executionResult}`}>
                    {RESULT_LABELS[log.executionResult]}
                  </span>
                  {log.llmModel && (
                    <span className="audit-timeline-llm" title={log.llmModel}>
                      <IconCamera size={11} />
                      {log.llmModel}
                    </span>
                  )}
                </div>
                <div className="audit-timeline-line2">
                  <span className="cell-mono">{formatTime(log.startedAt)}</span>
                  <span className="audit-timeline-duration">
                    耗时 {formatDuration(log.durationMs)}
                  </span>
                  {log.pageUrl && (
                    <span className="audit-timeline-url" title={log.pageUrl}>
                      {log.pageUrl}
                    </span>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

/** 审计详情弹窗属性 */
interface AuditDetailModalProps {
  /** 审计日志 */
  log: AuditLogVO
  /** 关闭弹窗回调 */
  onClose: () => void
}

/**
 * 审计详情弹窗
 *
 * 展示审计详情（基本信息 + 操作参数 + 截图对比 + LLM 信息）。
 *
 * @param log     审计日志
 * @param onClose 关闭回调
 */
function AuditDetailModal({ log, onClose }: AuditDetailModalProps) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="glass-card modal-card audit-modal-card"
        onClick={(e) => e.stopPropagation()}
      >
        {/* region 弹窗头部 */}
        <div className="modal-header">
          <div className="modal-title">
            <IconShield size={18} />
            审计详情
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="关闭"
          >
            <IconClose size={16} />
          </button>
        </div>
        {/* endregion */}

        {/* region 基本信息网格 */}
        <div className="approval-detail-meta">
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">审计 ID</div>
            <div className="approval-detail-meta-value cell-mono">#{log.auditId}</div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">任务 ID</div>
            <div className="approval-detail-meta-value cell-mono">#{log.taskId}</div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">操作类型</div>
            <div className="approval-detail-meta-value cell-mono">{log.actionType}</div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">执行结果</div>
            <div>
              <span className={`tag tag-result-${log.executionResult}`}>
                {RESULT_LABELS[log.executionResult]}
              </span>
            </div>
          </div>
          {log.riskLevel && (
            <div className="approval-detail-meta-item">
              <div className="approval-detail-meta-label">风险等级</div>
              <div>
                <span className={`tag tag-risk-${log.riskLevel}`}>
                  {RISK_LABELS[log.riskLevel]}
                </span>
              </div>
            </div>
          )}
          {log.approvalId && (
            <div className="approval-detail-meta-item">
              <div className="approval-detail-meta-label">审批单 ID</div>
              <div className="approval-detail-meta-value cell-mono">
                #{log.approvalId}
              </div>
            </div>
          )}
          {log.userId && (
            <div className="approval-detail-meta-item">
              <div className="approval-detail-meta-label">用户 ID</div>
              <div className="approval-detail-meta-value cell-mono">
                #{log.userId}
              </div>
            </div>
          )}
          {log.departmentId && (
            <div className="approval-detail-meta-item">
              <div className="approval-detail-meta-label">部门 ID</div>
              <div className="approval-detail-meta-value cell-mono">
                #{log.departmentId}
              </div>
            </div>
          )}
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">开始时间</div>
            <div className="approval-detail-meta-value cell-mono">
              {formatTime(log.startedAt)}
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">完成时间</div>
            <div className="approval-detail-meta-value cell-mono">
              {formatTime(log.completedAt)}
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">执行耗时</div>
            <div className="approval-detail-meta-value">
              {formatDuration(log.durationMs)}
            </div>
          </div>
          <div className="approval-detail-meta-item">
            <div className="approval-detail-meta-label">创建时间</div>
            <div className="approval-detail-meta-value cell-mono">
              {formatTime(log.createTime)}
            </div>
          </div>
        </div>
        {/* endregion */}

        {/* region 页面 URL */}
        {log.pageUrl && (
          <div className="form-group">
            <label className="label">页面 URL</label>
            <div className="approval-detail-text audit-detail-url">
              <a
                href={log.pageUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="audit-detail-link"
              >
                {log.pageUrl}
              </a>
            </div>
          </div>
        )}
        {/* endregion */}

        {/* region 目标元素 */}
        {log.targetElement && (
          <div className="form-group">
            <label className="label">目标元素</label>
            <div className="approval-detail-text audit-detail-mono">
              {log.targetElement}
            </div>
          </div>
        )}
        {/* endregion */}

        {/* region 错误信息（失败时展示） */}
        {log.executionResult === 'failed' && log.errorMessage && (
          <div className="form-group">
            <label className="label">错误信息</label>
            <div className="approval-detail-reasoning audit-detail-error">
              {log.errorMessage}
            </div>
          </div>
        )}
        {/* endregion */}

        {/* region 截图对比 */}
        {(log.beforeScreenshotUrl || log.afterScreenshotUrl) && (
          <div className="form-group">
            <label className="label">
              <IconCamera size={12} style={{ verticalAlign: '-1px', marginRight: 4 }} />
              截图对比（before / after）
            </label>
            <div className="audit-screenshot-grid">
              <div className="audit-screenshot-cell">
                <div className="audit-screenshot-label">
                  <span>Before</span>
                </div>
                {log.beforeScreenshotUrl ? (
                  <img
                    src={log.beforeScreenshotUrl}
                    alt="操作前截图"
                    className="audit-screenshot-img"
                    loading="lazy"
                  />
                ) : (
                  <div className="audit-screenshot-placeholder">
                    <IconCamera size={24} />
                    <div>无截图</div>
                  </div>
                )}
              </div>
              <div className="audit-screenshot-cell">
                <div className="audit-screenshot-label">
                  <span>After</span>
                </div>
                {log.afterScreenshotUrl ? (
                  <img
                    src={log.afterScreenshotUrl}
                    alt="操作后截图"
                    className="audit-screenshot-img"
                    loading="lazy"
                  />
                ) : (
                  <div className="audit-screenshot-placeholder">
                    <IconCamera size={24} />
                    <div>无截图</div>
                  </div>
                )}
              </div>
            </div>
            <div className="audit-screenshot-hint">
              截图为 MinIO 预签名 URL，有效期 1 小时
            </div>
          </div>
        )}
        {/* endregion */}

        {/* region 操作参数（已脱敏） */}
        {log.actionParams && (
          <div className="form-group">
            <label className="label">
              操作参数
              <span style={{ color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}>
                （已脱敏）
              </span>
            </label>
            <pre className="audit-detail-code">{prettyJson(log.actionParams)}</pre>
          </div>
        )}
        {/* endregion */}

        {/* region LLM 信息 */}
        {(log.llmModel || log.llmTokensUsed || log.llmCost != null) && (
          <div className="form-group">
            <label className="label">LLM 调用信息</label>
            <div className="audit-llm-grid">
              {log.llmModel && (
                <div className="audit-llm-item">
                  <span className="audit-llm-label">模型</span>
                  <span className="audit-llm-value cell-mono">{log.llmModel}</span>
                </div>
              )}
              {log.llmTokensUsed != null && (
                <div className="audit-llm-item">
                  <span className="audit-llm-label">Token 用量</span>
                  <span className="audit-llm-value cell-mono">
                    {log.llmTokensUsed.toLocaleString()}
                  </span>
                </div>
              )}
              {log.llmCost != null && (
                <div className="audit-llm-item">
                  <span className="audit-llm-label">成本（USD）</span>
                  <span className="audit-llm-value cell-mono">
                    ${log.llmCost.toFixed(6)}
                  </span>
                </div>
              )}
            </div>
          </div>
        )}
        {/* endregion */}
      </div>
    </div>
  )
}

export default AuditLogs

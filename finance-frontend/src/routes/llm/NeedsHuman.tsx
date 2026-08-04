/**
 * NEEDS_HUMAN 人工接管队列页（P3 ai-monitoring 原型对齐）
 *
 * 功能（M5.6 + P3 原型对齐）：
 * - 顶部 Tab 切换：待处置 / 已处理
 * - 两栏布局（1:2）：左侧队列卡片 + 右侧处置详情面板
 * - 队列卡片：NEEDS_HUMAN 徽章 + 等待时长 + 任务 ID + 子任务 + 业务线
 * - 详情面板：触发原因 + LLM 原始输出（深色代码块）+ 校验错误框 + 操作前截图 + 处置按钮
 * - 处置操作：skip / manual / abort 三按钮
 * - 处置成功后自动刷新列表
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { needsHumanApi } from '@/api/needsHuman'
import type {
  NeedsHumanQueueVO,
  NeedsHumanQueryRequest,
  ResolveAction,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import {
  IconAlert,
  IconHand,
  IconRefresh,
  IconShield,
  IconSkip,
  IconStop,
} from '@/components/Icons'

/** 默认页大小（队列卡片较多时使用） */
const DEFAULT_PAGE_SIZE = 50

/** Tab 选项：待处置 / 已处理 */
type TabStatus = 'PENDING' | 'RESOLVED'

/** NEEDS_HUMAN 接管队列页 */
function NeedsHumanPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // 1. 查询条件
  const [tabStatus, setTabStatus] = useState<TabStatus>('PENDING')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [resolveError, setResolveError] = useState<string | null>(null)

  // 2. 查询参数
  const queryKey = useMemo(
    () =>
      [
        'needs-human',
        { current: 1, pageSize: DEFAULT_PAGE_SIZE, status: tabStatus },
      ] as const,
    [tabStatus],
  )

  // 3. 查询列表
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn: () =>
      needsHumanApi.listNeedsHuman({
        current: 1,
        pageSize: DEFAULT_PAGE_SIZE,
        status: tabStatus,
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

  // 5. 切换 Tab 时清空选中项
  useEffect(() => {
    setSelectedId(null)
  }, [tabStatus])

  const records: NeedsHumanQueueVO[] = data ?? []

  // 6. 默认选中第一条
  useEffect(() => {
    if (!selectedId && records.length > 0) {
      setSelectedId(records[0].queueId)
    }
    if (selectedId && !records.some((r) => r.queueId === selectedId)) {
      setSelectedId(records.length > 0 ? records[0].queueId : null)
    }
  }, [records, selectedId])

  const selectedItem = useMemo(
    () => records.find((r) => r.queueId === selectedId) ?? null,
    [records, selectedId],
  )

  /** 处置 */
  const handleResolve = useCallback(
    (queueId: string, action: ResolveAction) => {
      setResolveError(null)
      resolveMutation.mutate({ queueId, action })
    },
    [resolveMutation],
  )

  /** 统计待处置条数（用于 Tab 徽章） */
  const pendingCount = useMemo(
    () => records.filter((r) => r.status === 'PENDING').length,
    [records],
  )

  return (
    <div className="tasks-page needs-human-page">
      {/* region 页面头部 */}
      <div className="ai-monitor-header">
        <div className="ai-monitor-header-text">
          <h1 className="page-title">人工接管队列</h1>
          <div className="breadcrumb">首页 / 合规 / 人工接管</div>
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

      {/* region Tab 切换栏 */}
      <div className="ai-monitor-tabs">
        <button
          type="button"
          className={`ai-monitor-tab ${tabStatus === 'PENDING' ? 'active' : ''}`}
          onClick={() => setTabStatus('PENDING')}
        >
          待处置
          {tabStatus === 'PENDING' && pendingCount > 0 && (
            <span className="badge badge-danger">{pendingCount}</span>
          )}
        </button>
        <button
          type="button"
          className={`ai-monitor-tab ${tabStatus === 'RESOLVED' ? 'active' : ''}`}
          onClick={() => setTabStatus('RESOLVED')}
        >
          已处理
        </button>
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
      {resolveError && (
        <div className="form-error" style={{ margin: '0 0 16px' }}>
          <IconAlert />
          处置失败：{resolveError}
        </div>
      )}
      {/* endregion */}

      {/* region 主体两栏布局 */}
      {isLoading ? (
        <div className="tasks-empty">加载中…</div>
      ) : records.length === 0 ? (
        <div className="tasks-empty">
          <IconShield size={36} />
          <div className="tasks-empty-title">
            {tabStatus === 'PENDING' ? '暂无待处置事件' : '暂无已处理记录'}
          </div>
          <div className="tasks-empty-desc">
            {tabStatus === 'PENDING'
              ? 'LLM 调用正常，无需人工介入'
              : '处置过的 NEEDS_HUMAN 事件将在此展示'}
          </div>
        </div>
      ) : (
        <div className="takeover-layout">
          {/* 左侧：队列卡片列表 */}
          <div className="takeover-list-pane">
            {records.map((item) => (
              <TakeoverCard
                key={item.queueId}
                item={item}
                active={item.queueId === selectedId}
                onClick={() => setSelectedId(item.queueId)}
              />
            ))}
          </div>

          {/* 右侧：处置详情面板 */}
          {selectedItem ? (
            <TakeoverDetail
              item={selectedItem}
              onResolve={handleResolve}
              onNavigateTask={(taskId) => navigate(`/tasks/${taskId}`)}
              resolving={resolveMutation.isPending}
            />
          ) : (
            <div className="glass-card-static takeover-detail-panel">
              <div className="audit-detail-placeholder">
                <IconShield size={36} className="audit-detail-placeholder-icon" />
                <div>请从左侧选择一条事件查看详情</div>
              </div>
            </div>
          )}
        </div>
      )}
      {/* endregion */}
    </div>
  )
}

/**
 * 队列卡片（左侧列表项）
 */
function TakeoverCard({
  item,
  active,
  onClick,
}: {
  item: NeedsHumanQueueVO
  active: boolean
  onClick: () => void
}) {
  // 1. 计算等待时长（仅待处置显示）
  const waitText = useMemo(() => {
    if (item.status !== 'PENDING') return ''
    const diffSec = dayjs().diff(dayjs(item.createTime), 'second')
    const m = Math.floor(diffSec / 60)
    const s = diffSec % 60
    return `等待 ${m}m${s.toString().padStart(2, '0')}s`
  }, [item.createTime, item.status])

  const isPending = item.status === 'PENDING'

  return (
    <div
      className={`takeover-item ${active ? 'active' : ''}`}
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick()
        }
      }}
    >
      {/* 顶部：徽章 + 等待时长 */}
      <div className="takeover-top">
        <span
          className={`badge ${isPending ? 'badge-NEEDS_HUMAN' : 'badge-muted'}`}
        >
          {isPending ? 'NEEDS_HUMAN' : 'RESOLVED'}
        </span>
        {waitText && <span className="takeover-wait">{waitText}</span>}
      </div>
      {/* 任务 ID 行 */}
      <div className="takeover-title">
        任务 <span className="mono">#{item.taskId.slice(-10)}</span>
      </div>
      {/* 子任务 / 上下文 */}
      <div className="takeover-meta">
        {item.subtaskGoal
          ? `子任务：${item.subtaskGoal}`
          : `上下文：${item.contextName}`}
      </div>
      {/* 业务线 */}
      {item.businessLineName && (
        <div className="takeover-meta">{item.businessLineName}</div>
      )}
    </div>
  )
}

/**
 * 处置详情面板（右侧）
 */
function TakeoverDetail({
  item,
  onResolve,
  onNavigateTask,
  resolving,
}: {
  item: NeedsHumanQueueVO
  onResolve: (queueId: string, action: ResolveAction) => void
  onNavigateTask: (taskId: string) => void
  resolving: boolean
}) {
  const isPending = item.status === 'PENDING'
  const triggerTime = dayjs(item.createTime).format('YYYY-MM-DD HH:mm:ss')

  // 1. 计算等待时长
  const waitText = useMemo(() => {
    if (item.status !== 'PENDING') return ''
    const diffSec = dayjs().diff(dayjs(item.createTime), 'second')
    const m = Math.floor(diffSec / 60)
    const s = diffSec % 60
    return `等待处置 ${m}m${s.toString().padStart(2, '0')}s`
  }, [item.createTime, item.status])

  // 2. 解析 LLM 原始输出为高亮 JSON
  const highlightedJson = useMemo(
    () => highlightJson(item.llmRawOutput),
    [item.llmRawOutput],
  )

  // 3. 解析校验错误为标题 + 详情
  const errorParts = useMemo(() => parseValidationError(item.validationError), [item.validationError])

  return (
    <div className="glass-card-static takeover-detail-panel">
      {/* 头部：任务 ID + 子任务 + 触发时间 + 等待 + 徽章 */}
      <div className="takeover-detail-header">
        <div>
          <div className="takeover-detail-title">
            任务{' '}
            <span
              className="mono"
              style={{ cursor: 'pointer' }}
              onClick={() => onNavigateTask(item.taskId)}
              title="跳转到任务详情"
            >
              #{item.taskId.slice(-10)}
            </span>
            {item.subtaskGoal ? ` · ${item.subtaskGoal}` : ''}
          </div>
          <div className="takeover-detail-sub">
            触发时间 {triggerTime}
            {waitText ? ` · ${waitText}` : ''}
            {item.businessLineName ? ` · ${item.businessLineName}` : ''}
          </div>
        </div>
        <span
          className={`badge ${isPending ? 'badge-NEEDS_HUMAN' : 'badge-muted'}`}
        >
          {isPending ? 'NEEDS_HUMAN' : 'RESOLVED'}
        </span>
      </div>

      {/* 触发原因 */}
      <div className="section-title">触发原因</div>
      <div className="takeover-reason">
        <IconAlert size={14} />
        LLM 校验失败（尝试 {item.attempts} 次后耗尽）
      </div>

      {/* LLM 原始输出 */}
      <div className="section-title">LLM 原始输出</div>
      {highlightedJson ? (
        <pre className="code-block" dangerouslySetInnerHTML={{ __html: highlightedJson }} />
      ) : (
        <div className="code-block">无原始输出</div>
      )}

      {/* 校验错误 */}
      <div className="section-title">校验错误</div>
      <div className="error-box">
        <div className="err-title">
          <IconAlert size={14} />
          {errorParts.title}
        </div>
        {errorParts.detail && (
          <div className="err-detail">{errorParts.detail}</div>
        )}
        {errorParts.line && <div className="err-line">{errorParts.line}</div>}
      </div>

      {/* 操作前截图 */}
      <div className="section-title">操作前截图</div>
      <div className="screenshot-box">
        <span className="screenshot-tag">before 截图</span>
        {item.screenshotUrl ? (
          <img src={item.screenshotUrl} alt="操作前截图" />
        ) : (
          <span>[before 截图]</span>
        )}
      </div>

      {/* 处置按钮（仅待处置显示） */}
      {isPending && (
        <div className="takeover-actions">
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => onResolve(item.queueId, 'skip')}
            disabled={resolving}
          >
            <IconSkip size={14} />
            跳过
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => onResolve(item.queueId, 'manual')}
            disabled={resolving}
          >
            <IconHand size={14} />
            手动执行
          </button>
          <button
            type="button"
            className="btn btn-danger"
            onClick={() => onResolve(item.queueId, 'abort')}
            disabled={resolving}
          >
            <IconStop size={14} />
            终止
          </button>
        </div>
      )}

      {/* 已处置记录显示处置结果 */}
      {!isPending && item.resolveAction && (
        <div className="takeover-actions">
          <div
            className="badge badge-muted"
            style={{ padding: '8px 14px', fontSize: 13 }}
          >
            {item.resolveAction === 'skip' && '已跳过（续跑任务）'}
            {item.resolveAction === 'manual' && '已人工处理（续跑任务）'}
            {item.resolveAction === 'abort' && '已终止任务'}
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * JSON 语法高亮：将 JSON 字符串渲染为带 tk-* class 的 HTML
 *
 * @param raw 原始 JSON 字符串
 * @returns HTML 字符串（已转义），null 表示解析失败
 */
function highlightJson(raw: string | undefined | null): string | null {
  if (!raw) return null
  try {
    const obj = JSON.parse(raw)
    const pretty = JSON.stringify(obj, null, 2)
    // 转义 HTML 特殊字符后再高亮
    return pretty
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(
        /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false)\b|\b(null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|([{}\[\],])/g,
        (match, str, colon, bool, nul, num, punct) => {
          if (str) {
            return colon
              ? `<span class="tk-key">${str}</span>${colon}`
              : `<span class="tk-str">${str}</span>`
          }
          if (bool) return `<span class="tk-bool">${bool}</span>`
          if (nul) return `<span class="tk-null">${nul}</span>`
          if (num) return `<span class="tk-num">${num}</span>`
          if (punct) return `<span class="tk-punct">${punct}</span>`
          return match
        },
      )
  } catch {
    // 非 JSON 文本：直接转义后返回
    return raw
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
  }
}

/**
 * 解析校验错误文本为标题 + 详情 + 错误行
 *
 * @param raw 原始错误文本
 * @returns 结构化错误信息
 */
function parseValidationError(
  raw: string | undefined | null,
): { title: string; detail?: string; line?: string } {
  if (!raw) return { title: '未知错误' }
  // 1. 尝试按冒号拆分（常见格式："Pydantic ValidationError: xxx"）
  const colonIdx = raw.indexOf(':')
  if (colonIdx > 0 && colonIdx < 60) {
    const title = raw.slice(0, colonIdx).trim()
    const rest = raw.slice(colonIdx + 1).trim()
    // 2. 详情中按换行拆分首行 + 后续
    const lines = rest.split(/\r?\n/).filter(Boolean)
    if (lines.length >= 2) {
      return { title, detail: lines[0], line: lines.slice(1).join(' ') }
    }
    return { title, detail: rest }
  }
  return { title: '校验失败', detail: raw }
}

export default NeedsHumanPage

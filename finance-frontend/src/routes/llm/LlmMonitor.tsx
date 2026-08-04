/**
 * LLM 调用监控页（P3 ai-monitoring 原型对齐）
 *
 * 功能（M5.6 + P3 原型对齐）：
 * - 顶部：标题 + 面包屑 + 时间筛选（今日 / 本周 / 本月）
 * - KPI 卡片：调用次数 / 总成本 / 缓存命中率 / 平均耗时（含环比趋势）
 * - 两栏：模型分布（横向占比条）+ 成本趋势（SVG 折线图，近 7 日）
 * - 底部：调用记录表（时间 / 模型 / 任务 / 费用 / 缓存命中）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { llmMonitorApi } from '@/api/llmMonitor'
import type {
  LlmCallRecordVO,
  LlmCallDailyTrendVO,
  ModelStatsVO,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import { IconAlert, IconRefresh } from '@/components/Icons'

/** 时间筛选选项 */
type TimeRange = 'today' | 'week' | 'month'

/** 时间筛选项配置 */
const TIME_OPTIONS: Array<{ value: TimeRange; label: string }> = [
  { value: 'today', label: '今日' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
]

/** 模型占比条配色（对齐原型：深海蓝/绿渐变） */
const MODEL_COLORS = [
  '#1A3A5C',
  'linear-gradient(90deg,#34D399,#1A3A5C)',
  'linear-gradient(90deg,#6EE7B7,#34D399)',
  'linear-gradient(90deg,#FCD34D,#F59E0B)',
  'linear-gradient(90deg,#A78BFA,#7C3AED)',
]

/** LLM 监控页 */
function LlmMonitorPage() {
  const [timeRange, setTimeRange] = useState<TimeRange>('today')

  // 1. 计算时间范围
  const { startTime, endTime } = useMemo(() => {
    const now = dayjs()
    switch (timeRange) {
      case 'today':
        return {
          startTime: now.startOf('day').toISOString(),
          endTime: now.toISOString(),
        }
      case 'week':
        return {
          startTime: now.subtract(6, 'day').startOf('day').toISOString(),
          endTime: now.toISOString(),
        }
      case 'month':
        return {
          startTime: now.subtract(29, 'day').startOf('day').toISOString(),
          endTime: now.toISOString(),
        }
    }
  }, [timeRange])

  // 2. 查询统计数据
  const {
    data: stats,
    isLoading,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['llm-stats', timeRange] as const,
    queryFn: () => llmMonitorApi.getCallStats({ startTime, endTime }),
    refetchOnWindowFocus: false,
  })

  // 3. 查询近 7 日成本趋势（独立查询，不受时间筛选影响，固定 7 日）
  const { data: dailyTrend } = useQuery({
    queryKey: ['llm-daily-trend'] as const,
    queryFn: () => llmMonitorApi.getDailyTrend(),
    refetchOnWindowFocus: false,
  })

  // 4. 查询调用记录（首页 10 条）
  const { data: callsPage } = useQuery({
    queryKey: ['llm-calls', timeRange] as const,
    queryFn: () =>
      llmMonitorApi.listCallRecords({
        current: 1,
        pageSize: 10,
        startTime,
        endTime,
      }),
    refetchOnWindowFocus: false,
  })

  return (
    <div className="tasks-page llm-monitor-page">
      {/* region 页面头部 */}
      <div className="ai-monitor-header">
        <div className="ai-monitor-header-text">
          <h1 className="page-title">LLM 监控</h1>
          <div className="breadcrumb">首页 / 监控 / LLM 监控</div>
        </div>
        <div className="tasks-header-actions">
          <div className="time-filter">
            {TIME_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                className={timeRange === opt.value ? 'active' : ''}
                onClick={() => setTimeRange(opt.value)}
              >
                {opt.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => refetch()}
            disabled={isFetching}
            title="刷新统计"
          >
            <IconRefresh size={14} />
            {isFetching ? '刷新中…' : '刷新'}
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

      {isLoading ? (
        <div className="tasks-empty">加载中…</div>
      ) : stats ? (
        <>
          {/* region KPI 卡片 */}
          <div className="llm-kpi-grid">
            <KpiCard
              label="调用次数"
              value={stats.totalCalls.toLocaleString()}
              trendPct={stats.totalCallsTrendPct ?? null}
              trendSuffix="vs 上一周期"
            />
            <KpiCard
              label="总成本"
              value={`¥${stats.totalCost.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`}
              trendPct={stats.totalCostTrendPct ?? null}
              trendSuffix="vs 上一周期"
            />
            <KpiCard
              label="缓存命中率"
              value={`${(stats.cacheHitRate * 100).toFixed(1)}`}
              valueSuffix="%"
              trendPct={stats.cacheHitRateTrendPct ?? null}
              trendSuffix="百分点"
              trendIsPoint
            />
            <KpiCard
              label="平均耗时"
              value={(stats.avgDurationMs / 1000).toFixed(1)}
              valueSuffix="s"
              trendPct={stats.avgDurationTrendPct ?? null}
              trendSuffix="vs 上一周期"
              trendInvert
            />
          </div>
          {/* endregion */}

          {/* region 模型分布 + 成本趋势 */}
          <div className="llm-charts-grid">
            {/* 模型分布 */}
            <div className="glass-card-static section-card">
              <div className="section-card-title">模型分布</div>
              <ModelDistribution modelStats={stats.modelStats ?? []} />
            </div>

            {/* 成本趋势 */}
            <div className="glass-card-static section-card">
              <div className="section-card-title">成本趋势（近 7 日）</div>
              <CostTrendChart trend={dailyTrend ?? []} />
            </div>
          </div>
          {/* endregion */}

          {/* region 调用记录表 */}
          <div className="glass-card-static llm-calls-card">
            <div className="section-card-title">调用记录</div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ width: '15%' }}>时间</th>
                    <th style={{ width: '18%' }}>模型</th>
                    <th>任务</th>
                    <th style={{ width: '12%' }}>费用</th>
                    <th style={{ width: '15%' }}>缓存命中</th>
                  </tr>
                </thead>
                <tbody>
                  {callsPage && callsPage.records.length > 0 ? (
                    callsPage.records.map((r) => (
                      <CallRecordRow key={r.callId} record={r} />
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5} style={{ textAlign: 'center', padding: '32px' }}>
                        暂无调用记录
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
          {/* endregion */}
        </>
      ) : (
        <div className="tasks-empty">
          <div className="tasks-empty-title">暂无 LLM 调用数据</div>
          <div className="tasks-empty-desc">
            触发任务执行后，LLM 调用记录将在此展示
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * KPI 卡片（对齐原型 .kpi-card）
 */
function KpiCard({
  label,
  value,
  valueSuffix,
  trendPct,
  trendSuffix,
  trendIsPoint,
  trendInvert,
}: {
  label: string
  value: string
  valueSuffix?: string
  trendPct: number | null
  trendSuffix: string
  /** 趋势值为百分点变化（非百分比） */
  trendIsPoint?: boolean
  /** 反转趋势好坏（耗时下降为好） */
  trendInvert?: boolean
}) {
  // 1. 计算趋势方向与好坏
  const hasTrend = trendPct !== null && trendPct !== undefined
  const isUp = hasTrend && trendPct > 0
  const isDown = hasTrend && trendPct < 0
  // 2. 判断好坏色（耗时类指标下降为好，需反转）
  const isGood = trendInvert ? isDown : isUp
  const isBad = trendInvert ? isUp : isDown

  const trendText = useMemo(() => {
    if (!hasTrend) return ''
    const abs = Math.abs(trendPct)
    if (trendIsPoint) {
      return `${isUp ? '↑' : isDown ? '↓' : ''} ${abs.toFixed(1)}%`
    }
    return `${isUp ? '↑' : isDown ? '↓' : ''} ${abs.toFixed(1)}% ${trendSuffix}`
  }, [trendPct, trendIsPoint, isUp, isDown, trendSuffix, hasTrend])

  return (
    <div className="glass-card kpi-card">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">
        {value}
        {valueSuffix && (
          <span style={{ fontSize: '18px' }}>{valueSuffix}</span>
        )}
      </div>
      {hasTrend && (
        <div
          className={`kpi-trend ${isGood ? 'up' : isBad ? 'down' : ''}`}
          style={isGood ? { color: 'var(--status-completed)' } : isBad ? { color: 'var(--status-failed)' } : {}}
        >
          {trendText}
          {trendIsPoint && trendSuffix ? ` ${trendSuffix}` : ''}
        </div>
      )}
    </div>
  )
}

/**
 * 模型分布（横向占比条，对齐原型 .model-row）
 */
function ModelDistribution({ modelStats }: { modelStats: ModelStatsVO[] }) {
  // 1. 计算总调用次数
  const totalCalls = useMemo(
    () => modelStats.reduce((sum, m) => sum + m.calls, 0),
    [modelStats],
  )

  if (modelStats.length === 0 || totalCalls === 0) {
    return <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>暂无模型数据</div>
  }

  return (
    <div>
      {modelStats.map((m, idx) => {
        const pct = totalCalls > 0 ? (m.calls / totalCalls) * 100 : 0
        const color = MODEL_COLORS[idx % MODEL_COLORS.length]
        return (
          <div className="model-row" key={m.model}>
            <div className="model-row-head">
              <span>{m.model}</span>
              <span className="mono">
                {pct.toFixed(0)}% · {m.calls.toLocaleString()} 次
              </span>
            </div>
            <div className="model-bar">
              <div
                className="model-bar-fill"
                style={{
                  width: `${pct}%`,
                  background: color,
                }}
              />
            </div>
          </div>
        )
      })}
    </div>
  )
}

/**
 * 成本趋势 SVG 折线图（对齐原型，近 7 日）
 */
function CostTrendChart({ trend }: { trend: LlmCallDailyTrendVO[] }) {
  // 1. 计算坐标
  const chart = useMemo(() => {
    if (trend.length === 0) return null
    const maxCost = Math.max(...trend.map((t) => t.cost), 1)
    // 1.1 网格刻度（向上取整到整百）
    const yMax = Math.ceil(maxCost / 100) * 100 || 100
    const yTicks = [
      { value: yMax, y: 40 },
      { value: (yMax * 2) / 3, y: 90 },
      { value: yMax / 3, y: 140 },
      { value: 0, y: 180 },
    ]
    // 1.2 数据点坐标（x 均匀分布在 70~430）
    const xStart = 70
    const xEnd = 430
    const step = trend.length > 1 ? (xEnd - xStart) / (trend.length - 1) : 0
    const points = trend.map((t, idx) => {
      const x = xStart + idx * step
      const y = 180 - (t.cost / yMax) * 140
      return { x, y, ...t }
    })
    // 1.3 折线 path
    const polyline = points.map((p) => `${p.x},${p.y}`).join(' ')
    // 1.4 区域填充 path
    const areaPath = `M ${points[0].x},180 L ${points
      .map((p) => `${p.x},${p.y}`)
      .join(' L ')} L ${points[points.length - 1].x},180 Z`
    // 1.5 趋势百分比（首尾对比）
    const first = trend[0].cost
    const last = trend[trend.length - 1].cost
    const trendPct =
      first > 0 ? ((last - first) / first) * 100 : 0
    return { yTicks, points, polyline, areaPath, trendPct, last }
  }, [trend])

  if (!chart) {
    return <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>暂无趋势数据</div>
  }

  const isDown = chart.trendPct < 0

  return (
    <div>
      <svg viewBox="0 0 480 200" className="chart-svg" style={{ height: 200 }}>
        <defs>
          <linearGradient id="llmAreaGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#1A3A5C" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#1A3A5C" stopOpacity="0" />
          </linearGradient>
        </defs>
        {/* 网格线 */}
        {chart.yTicks.map((tick) => (
          <line
            key={tick.value}
            x1="40"
            y1={tick.y}
            x2="460"
            y2={tick.y}
            stroke={tick.value === 0 ? '#CBD5E1' : '#E2E8F0'}
            strokeWidth="1"
          />
        ))}
        {/* Y 轴标签 */}
        {chart.yTicks.map((tick) => (
          <text
            key={`label-${tick.value}`}
            x="32"
            y={tick.y + 4}
            textAnchor="end"
            fill="#94A3B8"
            fontSize="10"
            fontFamily="JetBrains Mono, monospace"
          >
            ¥{tick.value}
          </text>
        ))}
        {/* 区域填充 */}
        <path d={chart.areaPath} fill="url(#llmAreaGrad)" />
        {/* 折线 */}
        <polyline
          points={chart.polyline}
          fill="none"
          stroke="#1A3A5C"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {/* 数据点 */}
        {chart.points.map((p, idx) => (
          <circle
            key={`point-${idx}`}
            cx={p.x}
            cy={p.y}
            r={idx === chart.points.length - 1 ? 4.5 : 3.5}
            fill={idx === chart.points.length - 1 ? '#2A5A8C' : '#1A3A5C'}
            stroke="#fff"
            strokeWidth="2"
          />
        ))}
        {/* X 轴标签 */}
        {chart.points.map((p, idx) => (
          <text
            key={`x-${idx}`}
            x={p.x}
            y="196"
            textAnchor="middle"
            fill={idx === chart.points.length - 1 ? '#2A5A8C' : '#94A3B8'}
            fontSize="10"
            fontWeight={idx === chart.points.length - 1 ? 600 : 400}
          >
            {p.date.slice(5)}
          </text>
        ))}
      </svg>
      <div className="trend-summary">
        <span>7 日{isDown ? '下降' : '上升'}趋势</span>
        <span className="text-success">
          {isDown ? '↓' : '↑'} {Math.abs(chart.trendPct).toFixed(0)}%
        </span>
      </div>
    </div>
  )
}

/**
 * 调用记录行（对齐原型表格行）
 */
function CallRecordRow({ record }: { record: LlmCallRecordVO }) {
  // 1. 时间格式化（仅显示时分秒）
  const time = dayjs(record.callTime).format('HH:mm:ss')
  // 2. 模型徽章颜色（gpt-4o-mini 蓝 / gpt-4o 黄 / claude 橙 / 其他灰）
  const modelBadgeClass = useMemo(() => {
    if (record.model.includes('mini')) return 'badge badge-info'
    if (record.model.includes('claude')) return 'badge badge-warning'
    if (record.model.includes('gpt-4o')) return 'badge badge-warning'
    return 'badge badge-muted'
  }, [record.model])
  // 3. 任务标题（关联 taskId + taskTitle）
  const taskLabel = record.taskTitle
    ? `#${record.taskId?.slice(-6) ?? ''} ${record.taskTitle}`
    : record.taskId
      ? `#${record.taskId.slice(-6)}`
      : '-'

  return (
    <tr>
      <td className="mono">{time}</td>
      <td>
        <span className={modelBadgeClass}>{record.model}</span>
      </td>
      <td>{taskLabel}</td>
      <td className="mono">¥ {record.cost.toFixed(2)}</td>
      <td>
        {record.cacheHit ? (
          <span className="badge badge-success">缓存命中</span>
        ) : (
          <span className="badge badge-muted">否</span>
        )}
      </td>
    </tr>
  )
}

export default LlmMonitorPage

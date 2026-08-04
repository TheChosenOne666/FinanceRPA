/**
 * 运营大屏页面
 *
 * 功能（M9 原型对齐）：
 * - 页面标题 + 面包屑 + 时间筛选器 + 刷新按钮
 * - 4 个 KPI 卡片：任务总数 / 成功率 / LLM 成本 / 接管队列
 * - 任务量趋势 SVG 折线图 + 业务线分布进度条（grid-2-1）
 * - LLM 成本趋势 SVG 折线图 + 错误类型分布柱状图（grid-2-1）
 * - 最近审批表格（待审批列表，含查看/批准操作）
 * - 30 秒自动刷新 + 手动刷新
 *
 * 设计对齐（prototypes/02-dashboard.html）：
 * - 使用 inline SVG 折线图（非 ECharts），匹配原型视觉
 * - KPI 卡片使用 kpi-label/kpi-value/kpi-trend 三层结构
 * - 业务线分布使用 biz-row 进度条，错误类型使用 error-row 柱状图
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '@/api/dashboard'
import { approvalApi } from '@/api/approval'
import type {
  ApprovalRequestVO,
  BusinessLineStatVO,
  ErrorTypeStatVO,
  TrendPointVO,
  WorkflowRiskLevel,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import { IconAlert, IconRefresh } from '@/components/Icons'

/** 自动刷新间隔（毫秒） */
const AUTO_REFRESH_INTERVAL = 30_000

/** 趋势默认天数 */
const DEFAULT_TREND_DAYS = 7

/** 风险等级标签 */
const RISK_LABELS: Record<WorkflowRiskLevel, string> = {
  low: 'LOW',
  medium: 'MEDIUM',
  high: 'HIGH',
  critical: 'CRITICAL',
}

/** 业务线配色（对齐原型 02-dashboard.html） */
const BIZ_COLORS = [
  '#1A3A5C',
  '#2A5A8C',
  '#3B82F6',
  '#60A5FA',
  '#C9A84C',
  '#10B981',
]

/** 错误类型配色（对齐原型） */
const ERROR_COLORS = ['#EF4444', '#F97316', '#8B5CF6', '#64748B', '#06B6D4']

/**
 * 安全转数字（后端 Long 字段可能序列化为 String）
 *
 * @param v 字符串或数字
 * @returns 数字
 */
function toNum(v: string | number | undefined | null): number {
  if (v == null) return 0
  return typeof v === 'number' ? v : Number(v) || 0
}

/**
 * 格式化百分比（0-1 → xx.x%）
 *
 * @param rate 比率（0-1）
 * @returns 百分比字符串
 */
function formatPercent(rate?: number): string {
  if (rate == null || Number.isNaN(rate)) return '0%'
  return `${(rate * 100).toFixed(1)}%`
}

/**
 * 格式化百分点差值（环比差值，0.021 → +2.1% / -2.1%）
 *
 * @param delta 比率差值（如 0.021 表示 +2.1 个百分点）
 * @returns 形如 "+2.1%" / "-2.1%" / "—"
 */
function formatPercentDelta(delta?: number): string {
  if (delta == null || Number.isNaN(delta)) return '—'
  const sign = delta >= 0 ? '+' : ''
  return `${sign}${(delta * 100).toFixed(1)}%`
}

/**
 * 格式化环比变化率（0.12 → +12% / -8%）
 *
 * @param rate 变化率（如 0.12 表示 +12%）
 * @returns 形如 "+12%" / "-8%" / "—"
 */
function formatGrowthRate(rate?: number): string {
  if (rate == null || Number.isNaN(rate)) return '—'
  const sign = rate >= 0 ? '+' : ''
  return `${sign}${(rate * 100).toFixed(0)}%`
}

/**
 * 格式化成本（人民币，对齐原型 02-dashboard.html 货币符号 ¥）
 *
 * @param cost 成本
 * @returns ¥x.xx 形式
 */
function formatCost(cost?: number): string {
  if (cost == null || Number.isNaN(cost)) return '¥0.00'
  return `¥${cost.toFixed(2)}`
}

/**
 * 从 requestPayload 中提取任务目标（goal）
 *
 * @param payload JSON 字符串
 * @returns goal 文本，解析失败返回空字符串
 */
function extractGoal(payload?: string): string {
  if (!payload) return ''
  try {
    const obj = JSON.parse(payload)
    return obj.goal || ''
  } catch {
    return ''
  }
}

/**
 * 计算审批剩余时间文本与紧急程度
 *
 * @param timeoutAt 超时截止时间 ISO 字符串
 * @returns { text, level } 剩余时间文本与紧急等级
 */
function calcRemainingTime(
  timeoutAt?: string,
): { text: string; level: 'urgent' | 'warning' | 'normal' | 'expired' } {
  if (!timeoutAt) return { text: '—', level: 'normal' }
  const diff = new Date(timeoutAt).getTime() - Date.now()
  if (diff <= 0) return { text: '已超时', level: 'expired' }
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return { text: '剩 <1 分钟', level: 'urgent' }
  if (minutes < 60) return { text: `剩 ${minutes} 分钟`, level: minutes <= 10 ? 'urgent' : 'warning' }
  const hours = Math.floor(minutes / 60)
  return { text: `剩 ${hours} 小时`, level: 'normal' }
}

/** 运营大屏页面 */
function Dashboard() {
  const navigate = useNavigate()
  // 1. 时间筛选器（今日 / 本周 / 本月）
  const [timeRange, setTimeRange] = useState<'today' | 'week' | 'month'>('today')
  // 2. 最后刷新时间
  const [lastRefreshAt, setLastRefreshAt] = useState<Date | null>(null)

  // 4. 概览查询
  const overviewQuery = useQuery({
    queryKey: ['dashboard-overview'] as const,
    queryFn: () => dashboardApi.getOverview(),
    refetchOnWindowFocus: false,
  })

  // 5. 趋势查询
  const trendsQuery = useQuery({
    queryKey: ['dashboard-trends', DEFAULT_TREND_DAYS] as const,
    queryFn: () => dashboardApi.getTrends(DEFAULT_TREND_DAYS),
    refetchOnWindowFocus: false,
  })

  // 6. 业务线分布查询
  const businessLinesQuery = useQuery({
    queryKey: ['dashboard-business-lines'] as const,
    queryFn: () => dashboardApi.getBusinessLines(),
    refetchOnWindowFocus: false,
  })

  // 7. 错误类型分布查询
  const errorsQuery = useQuery({
    queryKey: ['dashboard-errors'] as const,
    queryFn: () => dashboardApi.getErrors(),
    refetchOnWindowFocus: false,
  })

  // 8. 最近审批（待审批列表，前 5 条）
  const recentApprovalsQuery = useQuery({
    queryKey: ['dashboard-recent-approvals'] as const,
    queryFn: () =>
      approvalApi.listApprovals({
        current: 1,
        pageSize: 5,
        status: 'PENDING',
      }),
    refetchOnWindowFocus: false,
  })

  // 9. 自动刷新：每 30 秒重新拉取所有指标（对齐原型 02-dashboard.html 默认行为）
  useEffect(() => {
    const timer = setInterval(() => {
      overviewQuery.refetch()
      trendsQuery.refetch()
      businessLinesQuery.refetch()
      errorsQuery.refetch()
      recentApprovalsQuery.refetch()
      setLastRefreshAt(new Date())
    }, AUTO_REFRESH_INTERVAL)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 10. 首次加载完成后记录刷新时间
  useEffect(() => {
    if (overviewQuery.data && !lastRefreshAt) {
      setLastRefreshAt(new Date())
    }
  }, [overviewQuery.data, lastRefreshAt])

  /** 手动刷新所有指标 */
  const handleRefreshAll = () => {
    overviewQuery.refetch()
    trendsQuery.refetch()
    businessLinesQuery.refetch()
    errorsQuery.refetch()
    recentApprovalsQuery.refetch()
    setLastRefreshAt(new Date())
  }

  // 整体加载状态
  const isLoading = overviewQuery.isLoading
  const isFetching =
    overviewQuery.isFetching ||
    trendsQuery.isFetching ||
    businessLinesQuery.isFetching ||
    errorsQuery.isFetching ||
    recentApprovalsQuery.isFetching
  const error =
    overviewQuery.error ||
    trendsQuery.error ||
    businessLinesQuery.error ||
    errorsQuery.error ||
    recentApprovalsQuery.error

  const overview = overviewQuery.data
  const trends = trendsQuery.data
  const businessLines = businessLinesQuery.data
  const errors = errorsQuery.data
  const recentApprovals = recentApprovalsQuery.data?.records ?? []

  return (
    <div className="dashboard-page">
      {/* region 页面标题 + 面包屑 + 时间筛选器 */}
      <div className="flex items-center justify-between mb-md">
        <div>
          <h1 className="page-title" style={{ margin: '0 0 4px' }}>
            运营大屏
          </h1>
          <div className="breadcrumb">
            <a
              href="#"
              onClick={(e) => {
                e.preventDefault()
                navigate('/')
              }}
            >
              首页
            </a>
            <span className="sep">/</span>
            <a href="#" onClick={(e) => e.preventDefault()}>
              监控
            </a>
            <span className="sep">/</span>
            <span className="current">运营大屏</span>
          </div>
        </div>
        <div className="flex items-center gap-md">
          {/* 时间筛选器 */}
          <div className="time-filter">
            <button
              className={timeRange === 'today' ? 'active' : ''}
              onClick={() => setTimeRange('today')}
            >
              今日
            </button>
            <button
              className={timeRange === 'week' ? 'active' : ''}
              onClick={() => setTimeRange('week')}
            >
              本周
            </button>
            <button
              className={timeRange === 'month' ? 'active' : ''}
              onClick={() => setTimeRange('month')}
            >
              本月
            </button>
          </div>
          {/* 手动刷新按钮 */}
          <button
            type="button"
            className="btn btn-ghost btn-icon"
            onClick={handleRefreshAll}
            disabled={isFetching}
            title="刷新所有指标"
          >
            <IconRefresh size={16} />
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
      ) : overview ? (
        <>
          {/* region KPI 卡片（4 个核心指标，对齐原型 02-dashboard.html trend 文案） */}
          <div className="page-grid grid-4">
            <div className="glass-card kpi-card">
              <div className="kpi-label">任务总数</div>
              <div className="kpi-value">
                {toNum(overview.totalTasks).toLocaleString()}
              </div>
              <div
                className={
                  overview.taskGrowthRate == null
                    ? 'kpi-trend'
                    : overview.taskGrowthRate >= 0
                      ? 'kpi-trend up'
                      : 'kpi-trend down'
                }
              >
                {overview.taskGrowthRate == null
                  ? '— vs 上期'
                  : `${formatGrowthRate(overview.taskGrowthRate)} vs 上期`}
              </div>
            </div>
            <div className="glass-card kpi-card">
              <div className="kpi-label">成功率</div>
              <div className="kpi-value">
                {formatPercent(overview.successRate)}
              </div>
              <div
                className={
                  overview.successRateDelta == null
                    ? 'kpi-trend'
                    : overview.successRateDelta >= 0
                        ? 'kpi-trend up'
                        : 'kpi-trend down'
                }
              >
                {formatPercentDelta(overview.successRateDelta)}
              </div>
            </div>
            <div className="glass-card kpi-card">
              <div className="kpi-label">LLM 成本</div>
              <div className="kpi-value">
                {formatCost(overview.llmTotalCost)}
              </div>
              <div
                className={
                  overview.llmCostDelta == null
                    ? 'kpi-trend'
                    : overview.llmCostDelta >= 0
                        ? 'kpi-trend up'
                        : 'kpi-trend down'
                }
              >
                {formatGrowthRate(overview.llmCostDelta)}
              </div>
            </div>
            <div className="glass-card kpi-card">
              <div className="kpi-label">接管队列</div>
              <div
                className="kpi-value"
                style={{ color: 'var(--accent-warning)' }}
              >
                {toNum(overview.humanTakeoverQueueSize).toLocaleString()}
              </div>
              <div
                className="kpi-trend"
                style={{ color: 'var(--accent-warning)' }}
              >
                ⚠ 待处理
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region 任务量趋势 + 业务线分布（grid-2-1） */}
          <div className="page-grid grid-2-1 mt-lg">
            {/* 任务量趋势 SVG 折线图 */}
            <div className="glass-card-static p-lg chart-card">
              <div className="chart-header">
                <div className="chart-title">
                  任务量趋势 ({DEFAULT_TREND_DAYS} 天)
                </div>
                <div className="chart-meta">
                  {trends?.points?.length
                    ? `峰值 ${Math.max(
                        ...trends.points.map((p) => toNum(p.taskCount)),
                      )} · 均值 ${Math.round(
                        trends.points.reduce(
                          (s, p) => s + toNum(p.taskCount),
                          0,
                        ) / trends.points.length,
                      )}`
                    : '暂无数据'}
                </div>
              </div>
              <div className="chart-body">
                <TrendLineChart
                  points={trends?.points ?? []}
                  pointClass="point-primary"
                  lineClass="line-primary"
                  areaColor="rgba(26, 58, 92, 0.35)"
                  valueKey="taskCount"
                  yMax={300}
                />
              </div>
            </div>

            {/* 业务线分布进度条 */}
            <div className="glass-card-static p-lg chart-card">
              <div className="chart-header">
                <div className="chart-title">业务线分布</div>
                <div className="chart-meta">总占比 100%</div>
              </div>
              <div className="chart-body">
                <BusinessLineBars data={businessLines ?? []} />
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region LLM 成本趋势 + 错误类型分布（grid-2-1） */}
          <div className="page-grid grid-2-1 mt-lg">
            {/* LLM 成本趋势 SVG 折线图 */}
            <div className="glass-card-static p-lg chart-card">
              <div className="chart-header">
                <div className="chart-title">
                  LLM 成本趋势 ({DEFAULT_TREND_DAYS} 天)
                </div>
                <div className="chart-meta">
                  {trends?.points?.length
                    ? `累计 ${formatCost(
                        trends.points.reduce((s, p) => s + (p.cost ?? 0), 0),
                      )}`
                    : '暂无数据'}
                </div>
              </div>
              <div className="chart-body">
                <TrendLineChart
                  points={trends?.points ?? []}
                  pointClass="point-success"
                  lineClass="line-success"
                  areaColor="rgba(201, 168, 76, 0.35)"
                  valueKey="cost"
                  yMax={undefined}
                  formatValue={(v) => `$${v.toFixed(2)}`}
                />
              </div>
            </div>

            {/* 错误类型分布柱状图 */}
            <div className="glass-card-static p-lg chart-card">
              <div className="chart-header">
                <div className="chart-title">错误类型分布</div>
                <div className="chart-meta">
                  共 {errors?.reduce((s, e) => s + toNum(e.count), 0) ?? 0} 起
                </div>
              </div>
              <div className="chart-body">
                <ErrorTypeBars data={errors ?? []} />
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region 最近审批表格 */}
          <div className="glass-card-static p-lg mt-lg">
            <div className="chart-header">
              <div className="chart-title">最近审批</div>
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault()
                  navigate('/approvals')
                }}
                style={{ fontSize: 12 }}
              >
                查看全部 →
              </a>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>任务名</th>
                    <th>风险等级</th>
                    <th>申请人</th>
                    <th>剩余时间</th>
                    <th style={{ textAlign: 'right' }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {recentApprovals.length > 0 ? (
                    recentApprovals.map((a) => (
                      <RecentApprovalRow
                        key={a.approvalId}
                        approval={a}
                        onView={() => navigate('/approvals')}
                        onApprove={() => navigate('/approvals')}
                      />
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5} style={{ textAlign: 'center', padding: '32px' }}>
                        暂无待审批任务
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
          {/* endregion */}

          {/* region 最后刷新时间 */}
          {lastRefreshAt && (
            <div
              style={{
                textAlign: 'right',
                fontSize: 11,
                color: 'var(--text-muted)',
                marginTop: 12,
              }}
            >
              最后刷新 {lastRefreshAt.toLocaleTimeString('zh-CN')}
            </div>
          )}
          {/* endregion */}
        </>
      ) : (
        <div className="tasks-empty">
          <div className="tasks-empty-title">暂无大屏数据</div>
          <div className="tasks-empty-desc">
            触发任务执行后，统计数据将在此展示
          </div>
        </div>
      )}
    </div>
  )
}

// ============================================================
// 子组件：SVG 折线图（通用）
// ============================================================

/** 折线图 Props */
interface TrendLineChartProps {
  /** 数据点 */
  points: TrendPointVO[]
  /** 数据点 CSS 类名 */
  pointClass: string
  /** 线条 CSS 类名 */
  lineClass: string
  /** 面积填充色 */
  areaColor: string
  /** 取值字段：taskCount 或 cost */
  valueKey: 'taskCount' | 'cost'
  /** Y 轴最大值（不传则自动计算） */
  yMax?: number
  /** 值格式化函数 */
  formatValue?: (v: number) => string
}

/**
 * 通用 SVG 折线图（对齐原型 02-dashboard.html 的 chart-svg）
 *
 * 动态生成网格线、坐标轴、面积填充、折线、数据点和数据标签。
 */
function TrendLineChart({
  points,
  pointClass,
  lineClass,
  areaColor,
  valueKey,
  yMax,
  formatValue,
}: TrendLineChartProps) {
  if (!points.length) {
    return <div className="chart-empty">暂无趋势数据</div>
  }

  // SVG 视口尺寸（对齐原型 viewBox="0 0 700 260"）
  const W = 700
  const H = 260
  const PAD_L = 50
  const PAD_R = 50
  const PAD_T = 20
  const PAD_B = 40
  const chartW = W - PAD_L - PAD_R
  const chartH = H - PAD_T - PAD_B

  // 1. 提取数据值
  const values = points.map((p) => toNum(p[valueKey]))
  const maxVal = yMax ?? Math.max(...values, 1)
  // Y 轴取整刻度（向上取整到合适刻度）
  const yScale = Math.ceil(maxVal / 100) * 100 || 100

  // 2. 计算 X 坐标（均匀分布）
  const xStep = chartW / Math.max(points.length - 1, 1)
  const xCoords = points.map((_, i) => PAD_L + i * xStep)

  // 3. 计算 Y 坐标（值越大越靠上）
  const yCoords = values.map(
    (v) => PAD_T + chartH - (v / yScale) * chartH,
  )

  // 4. 生成折线 points 字符串
  const linePoints = xCoords
    .map((x, i) => `${x},${yCoords[i]}`)
    .join(' ')

  // 5. 生成面积填充 points 字符串（折线 + 底部两个角点）
  const areaPoints = `${linePoints} ${xCoords[xCoords.length - 1]},${PAD_T + chartH} ${xCoords[0]},${PAD_T + chartH}`

  // 6. 生成唯一 gradient ID
  const gradientId = `trend-area-${valueKey}`

  // 7. Y 轴刻度（4 档：0, 1/3, 2/3, 满刻度）
  const yTicks = [0, yScale / 3, (yScale * 2) / 3, yScale]
  const yTickPositions = yTicks.map(
    (v) => PAD_T + chartH - (v / yScale) * chartH,
  )

  // 8. X 蝶签（日期，取 MM/DD 格式）
  const xLabels = points.map((p) => {
    const d = new Date(p.date)
    return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`
  })

  // 9. 数据标签格式化
  const formatLabel = (v: number) => {
    if (formatValue) return formatValue(v)
    return String(v)
  }

  return (
    <svg
      className="chart-svg"
      viewBox={`0 0 ${W} ${H}`}
      preserveAspectRatio="xMidYMid meet"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={areaColor} />
          <stop offset="100%" stopColor={areaColor} stopOpacity="0" />
        </linearGradient>
      </defs>

      {/* 网格线 */}
      {yTickPositions.map((y, i) => (
        <line
          key={`grid-${i}`}
          className="grid-line"
          x1={PAD_L}
          y1={y}
          x2={W - PAD_R}
          y2={y}
        />
      ))}

      {/* X 轴线 */}
      <line
        className="axis-line"
        x1={PAD_L}
        y1={PAD_T + chartH}
        x2={W - PAD_R}
        y2={PAD_T + chartH}
      />

      {/* Y 轴标签 */}
      {yTicks.map((v, i) => (
        <text
          key={`y-label-${i}`}
          className="axis-label"
          x={PAD_L - 8}
          y={yTickPositions[i] + 4}
          textAnchor="end"
        >
          {formatLabel(v)}
        </text>
      ))}

      {/* X 轴标签 */}
      {xLabels.map((label, i) => (
        <text
          key={`x-label-${i}`}
          className="axis-label"
          x={xCoords[i]}
          y={PAD_T + chartH + 20}
          textAnchor="middle"
        >
          {label}
        </text>
      ))}

      {/* 面积填充 */}
      <polygon
        className="data-area"
        style={{ fill: `url(#${gradientId})` }}
        points={areaPoints}
      />

      {/* 折线 */}
      <polyline className={`data-line ${lineClass}`} points={linePoints} />

      {/* 数据点 */}
      {xCoords.map((x, i) => (
        <circle
          key={`point-${i}`}
          className={`data-point ${pointClass}`}
          cx={x}
          cy={yCoords[i]}
          r={i === values.indexOf(maxVal) ? 4.5 : 4}
        />
      ))}

      {/* 数据标签 */}
      {xCoords.map((x, i) => (
        <text
          key={`label-${i}`}
          className="data-point-label"
          x={x}
          y={yCoords[i] - 10}
        >
          {formatLabel(values[i])}
        </text>
      ))}
    </svg>
  )
}

// ============================================================
// 子组件：业务线分布进度条
// ============================================================

/** 业务线分布 Props */
interface BusinessLineBarsProps {
  data: BusinessLineStatVO[]
}

/** 业务线分布进度条（对齐原型 biz-row） */
function BusinessLineBars({ data }: BusinessLineBarsProps) {
  if (!data.length) {
    return <div className="chart-empty">暂无业务线数据</div>
  }

  // 1. 计算总任务数
  const total = data.reduce((s, b) => s + toNum(b.taskCount), 0) || 1

  return (
    <>
      {data.map((biz, i) => {
        const count = toNum(biz.taskCount)
        const percent = ((count / total) * 100).toFixed(1)
        const color = BIZ_COLORS[i % BIZ_COLORS.length]
        return (
          <div className="biz-row" key={biz.businessLineId}>
            <div className="biz-header">
              <span className="biz-name">
                <span className="biz-dot" style={{ background: color }} />
                {biz.businessLineName}
              </span>
              <span className="biz-value">{percent}%</span>
            </div>
            <div className="biz-bar">
              <div
                className="biz-bar-fill"
                style={{
                  width: `${percent}%`,
                  background: `linear-gradient(90deg, ${color}, ${color}dd)`,
                }}
              />
            </div>
          </div>
        )
      })}
    </>
  )
}

// ============================================================
// 子组件：错误类型分布柱状图
// ============================================================

/** 错误类型分布 Props */
interface ErrorTypeBarsProps {
  data: ErrorTypeStatVO[]
}

/** 错误类型分布柱状图（对齐原型 error-row） */
function ErrorTypeBars({ data }: ErrorTypeBarsProps) {
  if (!data.length) {
    return <div className="chart-empty">暂无错误数据</div>
  }

  // 1. 计算最大计数（用于计算柱状图宽度比例）
  const maxCount = Math.max(...data.map((e) => toNum(e.count)), 1)

  return (
    <>
      {data.slice(0, 6).map((err, i) => {
        const count = toNum(err.count)
        const width = (count / maxCount) * 100
        const color = ERROR_COLORS[i % ERROR_COLORS.length]
        return (
          <div className="error-row" key={err.errorType}>
            <div className="error-name" title={err.errorType}>
              {err.errorType.length > 5
                ? err.errorType.slice(0, 5)
                : err.errorType}
            </div>
            <div className="error-bar-wrap">
              <div
                className="error-bar-fill"
                style={{
                  width: `${Math.max(width, 10)}%`,
                  background: color,
                }}
              >
                <span className="error-count">{count}</span>
              </div>
            </div>
          </div>
        )
      })}
    </>
  )
}

// ============================================================
// 子组件：最近审批表格行
// ============================================================

/** 审批行 Props */
interface RecentApprovalRowProps {
  approval: ApprovalRequestVO
  onView: () => void
  onApprove: () => void
}

/** 最近审批表格行（对齐原型最近审批表格） */
function RecentApprovalRow({
  approval,
  onView,
  onApprove,
}: RecentApprovalRowProps) {
  const goal = extractGoal(approval.requestPayload)
  const { text, level } = calcRemainingTime(approval.timeoutAt)
  const riskClass = `risk-${approval.riskLevel}`

  return (
    <tr>
      <td>
        {goal || '未命名任务'} ·{' '}
        <span className="mono" style={{ color: 'var(--text-muted)' }}>
          {approval.taskId}
        </span>
      </td>
      <td>
        <span className={`badge ${riskClass}`}>
          {RISK_LABELS[approval.riskLevel]}
        </span>
      </td>
      <td>{approval.userName || `用户 ${approval.userId}`}</td>
      <td className={`mono time-${level}`}>{text}</td>
      <td style={{ textAlign: 'right' }}>
        <div className="action-group">
          <button className="btn btn-ghost btn-sm" onClick={onView}>
            查看
          </button>
          <button className="btn btn-success btn-sm" onClick={onApprove}>
            批准
          </button>
        </div>
      </td>
    </tr>
  )
}

export default Dashboard

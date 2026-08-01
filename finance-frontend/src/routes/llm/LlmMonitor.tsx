/**
 * LLM 调用监控页
 *
 * 功能（M5.6）：
 * - 汇总卡片：总调用次数 / 成功率 / 缓存命中率 / 总成本
 * - ECharts 可视化：
 *   - 模型调用分布（饼图）
 *   - 模型成本对比（柱状图）
 *   - 模型 Token 用量（柱状图）
 * - 模型统计明细表
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import type { EChartsOption } from 'echarts'
import { llmMonitorApi } from '@/api/llmMonitor'
import type { ModelStatsVO } from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import {
  IconAlert,
  IconChart,
  IconCheck,
  IconDollar,
  IconRefresh,
  IconTarget,
} from '@/components/Icons'

/** LLM 监控页 */
function LlmMonitorPage() {
  // 1. 查询统计数据
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['llm-stats'] as const,
    queryFn: () => llmMonitorApi.getCallStats(),
    refetchOnWindowFocus: false,
  })

  // 2. 计算派生指标
  const stats = useMemo(() => {
    if (!data) return null
    const successRate =
      data.totalCalls > 0 ? (data.successCalls / data.totalCalls) * 100 : 0
    return {
      ...data,
      successRate: Math.round(successRate * 100) / 100,
      cacheHitRatePct: Math.round(data.cacheHitRate * 10000) / 100,
      totalCostFormatted: `$${data.totalCost.toFixed(4)}`,
    }
  }, [data])

  // 3. 饼图：模型调用分布
  const pieOption = useMemo<EChartsOption | null>(() => {
    if (!stats?.modelStats?.length) return null
    return {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} 次 ({d}%)',
      },
      legend: {
        orient: 'vertical',
        right: 10,
        top: 'center',
        textStyle: { fontSize: 12, color: 'var(--text-secondary)' },
      },
      series: [
        {
          name: '模型调用分布',
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['40%', '50%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 6,
            borderColor: '#fff',
            borderWidth: 2,
          },
          label: {
            show: false,
            position: 'center',
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 14,
              fontWeight: 'bold',
            },
          },
          data: stats.modelStats.map((m: ModelStatsVO) => ({
            value: m.calls,
            name: m.model,
          })),
        },
      ],
      color: ['#047857', '#0ea5e9', '#8b5cf6', '#f59e0b', '#ef4444'],
    }
  }, [stats])

  // 4. 柱状图：模型成本对比
  const costBarOption = useMemo<EChartsOption | null>(() => {
    if (!stats?.modelStats?.length) return null
    const models = stats.modelStats.map((m) => m.model)
    const costs = stats.modelStats.map((m) => Number(m.cost.toFixed(4)))
    return {
      tooltip: {
        trigger: 'axis',
        formatter: '{b}: ${c}',
      },
      xAxis: {
        type: 'category',
        data: models,
        axisLabel: { fontSize: 11, color: 'var(--text-muted)' },
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          fontSize: 11,
          color: 'var(--text-muted)',
          formatter: '${value}',
        },
      },
      series: [
        {
          name: '成本',
          type: 'bar',
          data: costs,
          itemStyle: {
            color: '#047857',
            borderRadius: [4, 4, 0, 0],
          },
          barWidth: '50%',
        },
      ],
      grid: { left: 50, right: 20, top: 20, bottom: 40 },
    }
  }, [stats])

  // 5. 柱状图：Token 用量
  const tokenBarOption = useMemo<EChartsOption | null>(() => {
    if (!stats?.modelStats?.length) return null
    const models = stats.modelStats.map((m) => m.model)
    const tokens = stats.modelStats.map((m) => m.totalTokens)
    return {
      tooltip: {
        trigger: 'axis',
        formatter: '{b}: {c} tokens',
      },
      xAxis: {
        type: 'category',
        data: models,
        axisLabel: { fontSize: 11, color: 'var(--text-muted)' },
      },
      yAxis: {
        type: 'value',
        axisLabel: { fontSize: 11, color: 'var(--text-muted)' },
      },
      series: [
        {
          name: 'Token 用量',
          type: 'bar',
          data: tokens,
          itemStyle: {
            color: '#0ea5e9',
            borderRadius: [4, 4, 0, 0],
          },
          barWidth: '50%',
        },
      ],
      grid: { left: 50, right: 20, top: 20, bottom: 40 },
    }
  }, [stats])

  return (
    <div className="tasks-page llm-monitor-page">
      {/* region 页面标题 */}
      <div className="tasks-header">
        <div>
          <h1 className="page-title">
            <IconChart size={22} /> LLM 调用监控
          </h1>
          <p className="page-subtitle">
            监控 LLM 调用次数、成本、缓存命中率与模型分布
          </p>
        </div>
        <div className="tasks-header-actions">
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
        <div className="form-error" style={{ margin: '16px 0' }}>
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
          {/* region 汇总卡片 */}
          <div className="stats-cards">
            <div className="glass-card-static stat-card">
              <div className="stat-icon stat-icon-blue">
                <IconTarget size={20} />
              </div>
              <div className="stat-content">
                <div className="stat-label">总调用次数</div>
                <div className="stat-value">{stats.totalCalls.toLocaleString()}</div>
                <div className="stat-sub">
                  成功 {stats.successCalls.toLocaleString()} · 失败{' '}
                  {stats.failedCalls.toLocaleString()}
                </div>
              </div>
            </div>

            <div className="glass-card-static stat-card">
              <div className="stat-icon stat-icon-green">
                <IconCheck size={20} />
              </div>
              <div className="stat-content">
                <div className="stat-label">成功率</div>
                <div className="stat-value">{stats.successRate}%</div>
                <div className="stat-sub">
                  缓存命中 {stats.cacheHitCalls.toLocaleString()} 次（
                  {stats.cacheHitRatePct}%）
                </div>
              </div>
            </div>

            <div className="glass-card-static stat-card">
              <div className="stat-icon stat-icon-purple">
                <IconChart size={20} />
              </div>
              <div className="stat-content">
                <div className="stat-label">总 Token 用量</div>
                <div className="stat-value">
                  {stats.totalTokens.toLocaleString()}
                </div>
                <div className="stat-sub">
                  Prompt {stats.totalPromptTokens.toLocaleString()} ·
                  Completion {stats.totalCompletionTokens.toLocaleString()}
                </div>
              </div>
            </div>

            <div className="glass-card-static stat-card">
              <div className="stat-icon stat-icon-amber">
                <IconDollar size={20} />
              </div>
              <div className="stat-content">
                <div className="stat-label">总成本</div>
                <div className="stat-value">{stats.totalCostFormatted}</div>
                <div className="stat-sub">
                  平均耗时 {Math.round(stats.avgDurationMs)}ms
                </div>
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region 图表区 */}
          <div className="charts-grid">
            {pieOption && (
              <div className="glass-card-static chart-card">
                <div className="chart-title">模型调用分布</div>
                <ReactECharts
                  option={pieOption}
                  style={{ height: 280 }}
                  opts={{ renderer: 'svg' }}
                />
              </div>
            )}
            {costBarOption && (
              <div className="glass-card-static chart-card">
                <div className="chart-title">模型成本对比</div>
                <ReactECharts
                  option={costBarOption}
                  style={{ height: 280 }}
                  opts={{ renderer: 'svg' }}
                />
              </div>
            )}
            {tokenBarOption && (
              <div className="glass-card-static chart-card">
                <div className="chart-title">模型 Token 用量</div>
                <ReactECharts
                  option={tokenBarOption}
                  style={{ height: 280 }}
                  opts={{ renderer: 'svg' }}
                />
              </div>
            )}
          </div>
          {/* endregion */}

          {/* region 模型统计明细表 */}
          {stats.modelStats && stats.modelStats.length > 0 && (
            <div className="tasks-table-wrapper glass-card-static">
              <div className="chart-title" style={{ padding: '16px 20px 0' }}>
                模型统计明细
              </div>
              <table className="tasks-table">
                <thead>
                  <tr>
                    <th>模型名</th>
                    <th style={{ width: '12%' }}>调用次数</th>
                    <th style={{ width: '12%' }}>成功次数</th>
                    <th style={{ width: '12%' }}>成功率</th>
                    <th style={{ width: '15%' }}>Token 用量</th>
                    <th style={{ width: '12%' }}>成本</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.modelStats.map((m: ModelStatsVO) => {
                    const rate =
                      m.calls > 0
                        ? Math.round((m.successCalls / m.calls) * 10000) / 100
                        : 0
                    return (
                      <tr key={m.model} className="task-row">
                        <td className="cell-mono">{m.model}</td>
                        <td className="cell-mono">{m.calls.toLocaleString()}</td>
                        <td className="cell-mono">
                          {m.successCalls.toLocaleString()}
                        </td>
                        <td>{rate}%</td>
                        <td className="cell-mono">
                          {m.totalTokens.toLocaleString()}
                        </td>
                        <td className="cell-mono">${m.cost.toFixed(4)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
          {/* endregion */}
        </>
      ) : (
        <div className="tasks-empty">
          <IconChart size={36} />
          <div className="tasks-empty-title">暂无 LLM 调用数据</div>
          <div className="tasks-empty-desc">
            触发任务执行后，LLM 调用记录将在此展示
          </div>
        </div>
      )}
    </div>
  )
}

export default LlmMonitorPage

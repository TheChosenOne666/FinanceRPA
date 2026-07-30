/**
 * 工作流模板列表页
 *
 * 功能（M3.6）：
 * - 卡片网格展示当前组织下的工作流模板（6 个内置 + 自定义）
 * - 行业筛选（全部 / 银行 / 保险 / 证券）
 * - 风险等级筛选（全部 / 低 / 中 / 高 / 极高）
 * - 名称模糊搜索（防抖）
 * - 卡片展示：名称 / 描述 / 行业标签 / 风险标签 / 参数数量 / 步骤数量 / 启用状态
 * - 点击卡片跳转详情页
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { workflowApi, INDUSTRY_LABELS, RISK_LEVEL_LABELS } from '@/api/workflows'
import { parseWorkflowParams, parseWorkflowSteps } from '@/api/workflows'
import type {
  WorkflowIndustry,
  WorkflowQueryRequest,
  WorkflowRiskLevel,
  WorkflowVO,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import {
  IconAlert,
  IconRefresh,
  IconSearch,
  IconWorkflow,
} from '@/components/Icons'

/** 默认页大小 */
const DEFAULT_PAGE_SIZE = 12

/** 行业筛选选项 */
const INDUSTRY_OPTIONS: Array<{ value: '' | WorkflowIndustry; label: string }> = [
  { value: '', label: '全部行业' },
  { value: 'banking', label: '银行' },
  { value: 'insurance', label: '保险' },
  { value: 'securities', label: '证券' },
]

/** 风险等级筛选选项 */
const RISK_OPTIONS: Array<{ value: '' | WorkflowRiskLevel; label: string }> = [
  { value: '', label: '全部风险' },
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'critical', label: '极高' },
]

/** 工作流模板列表页 */
function WorkflowsPage() {
  const navigate = useNavigate()

  // 1. 查询条件
  const [current, setCurrent] = useState(1)
  const [pageSize] = useState(DEFAULT_PAGE_SIZE)
  const [industry, setIndustry] = useState<'' | WorkflowIndustry>('')
  const [riskLevel, setRiskLevel] = useState<'' | WorkflowRiskLevel>('')
  const [searchInput, setSearchInput] = useState('') // 输入框值（即时变化）
  const [searchText, setSearchText] = useState('') // 实际提交的搜索值（防抖后）

  // 2. 搜索防抖（输入停止 400ms 后触发查询）
  useEffect(() => {
    const t = setTimeout(() => {
      setSearchText(searchInput.trim())
      setCurrent(1)
    }, 400)
    return () => clearTimeout(t)
  }, [searchInput])

  // 3. 查询参数（useQuery 依赖项）
  const queryKey = useMemo(
    () =>
      ['workflows', { current, pageSize, industry, riskLevel, searchText }] as const,
    [current, pageSize, industry, riskLevel, searchText],
  )

  // 4. 查询工作流模板列表
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn: () =>
      workflowApi.listWorkflows({
        current,
        pageSize,
        industry,
        riskLevel,
        name: searchText,
      } satisfies WorkflowQueryRequest),
    refetchOnWindowFocus: false,
  })

  /** 行业筛选变更 */
  const handleIndustryChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setIndustry(e.target.value as '' | WorkflowIndustry)
    setCurrent(1)
  }

  /** 风险等级筛选变更 */
  const handleRiskChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setRiskLevel(e.target.value as '' | WorkflowRiskLevel)
    setCurrent(1)
  }

  /** 重置筛选 */
  const handleReset = () => {
    setIndustry('')
    setRiskLevel('')
    setSearchInput('')
    setCurrent(1)
  }

  const records: WorkflowVO[] = data?.records ?? []
  const total: number = data?.total ?? 0

  return (
    <div className="workflows-page">
      {/* region 页面标题 + 操作区 */}
      <div className="workflows-header">
        <div>
          <h1 className="page-title">
            <IconWorkflow size={22} />
            工作流模板
          </h1>
          <p className="page-subtitle">
            金融场景自动化流程模板，点击模板查看详情并触发执行
          </p>
        </div>
        <div className="workflows-header-actions">
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
      <div className="workflows-toolbar glass-card-static">
        <div className="toolbar-search">
          <IconSearch size={14} className="toolbar-search-icon" />
          <input
            type="text"
            className="input toolbar-input"
            placeholder="搜索模板名称（输入后自动搜索）"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">行业</label>
          <select
            className="select toolbar-select"
            value={industry}
            onChange={handleIndustryChange}
          >
            {INDUSTRY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-filter">
          <label className="toolbar-filter-label">风险</label>
          <select
            className="select toolbar-select"
            value={riskLevel}
            onChange={handleRiskChange}
          >
            {RISK_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
        {(industry || riskLevel || searchText) && (
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={handleReset}
          >
            重置
          </button>
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

      {/* region 工作流卡片网格 */}
      {isLoading ? (
        <div className="workflows-empty glass-card-static">加载中…</div>
      ) : records.length === 0 ? (
        <div className="workflows-empty glass-card-static">
          <IconWorkflow size={36} />
          <div className="workflows-empty-title">暂无工作流模板</div>
          <div className="workflows-empty-desc">
            {searchText || industry || riskLevel
              ? '当前筛选条件下没有匹配的模板'
              : '请联系管理员配置工作流模板'}
          </div>
        </div>
      ) : (
        <div className="workflows-grid">
          {records.map((wf) => (
            <WorkflowCard
              key={wf.workflowId}
              workflow={wf}
              onClick={() => navigate(`/workflows/${wf.workflowId}`)}
            />
          ))}
        </div>
      )}
      {/* endregion */}

      {/* region 统计信息 */}
      {total > 0 && (
        <div className="workflows-stats">
          共 <strong>{total}</strong> 个模板
          {(industry || riskLevel || searchText) && ` · 已筛选`}
        </div>
      )}
      {/* endregion */}
    </div>
  )
}

/**
 * 工作流卡片
 *
 * @param workflow 工作流模板
 * @param onClick  点击卡片回调
 */
function WorkflowCard({
  workflow,
  onClick,
}: {
  workflow: WorkflowVO
  onClick: () => void
}) {
  // 1. 解析参数与步骤
  const params = parseWorkflowParams(workflow.params)
  const steps = parseWorkflowSteps(workflow.steps)

  // 2. 启用状态
  const enabled = workflow.enabled === 1

  return (
    <div
      className={`workflow-card glass-card-static${enabled ? '' : ' workflow-card-disabled'}`}
      onClick={enabled ? onClick : undefined}
      role="button"
      tabIndex={enabled ? 0 : -1}
      title={enabled ? '点击查看详情' : '模板已禁用'}
    >
      {/* region 卡片头部：名称 + 行业 + 风险 */}
      <div className="workflow-card-header">
        <div className="workflow-card-title-row">
          <h3 className="workflow-card-title">{workflow.name}</h3>
          {!enabled && <span className="workflow-card-badge-disabled">已禁用</span>}
        </div>
        <div className="workflow-card-tags">
          <span className={`tag tag-industry tag-industry-${workflow.industry}`}>
            {INDUSTRY_LABELS[workflow.industry]}
          </span>
          <span className={`tag tag-risk tag-risk-${workflow.riskLevel}`}>
            {RISK_LEVEL_LABELS[workflow.riskLevel]}风险
          </span>
        </div>
      </div>
      {/* endregion */}

      {/* region 卡片描述 */}
      <p className="workflow-card-desc">
        {workflow.description || '暂无描述'}
      </p>
      {/* endregion */}

      {/* region 卡片元信息 */}
      <div className="workflow-card-meta">
        <div className="workflow-card-meta-item">
          <span className="workflow-card-meta-label">参数</span>
          <span className="workflow-card-meta-value">{params.length} 个</span>
        </div>
        <div className="workflow-card-meta-item">
          <span className="workflow-card-meta-label">步骤</span>
          <span className="workflow-card-meta-value">{steps.length} 步</span>
        </div>
        {workflow.version && (
          <div className="workflow-card-meta-item">
            <span className="workflow-card-meta-label">版本</span>
            <span className="workflow-card-meta-value cell-mono">{workflow.version}</span>
          </div>
        )}
      </div>
      {/* endregion */}

      {/* region 卡片底部：Skill 步骤预览 */}
      <div className="workflow-card-steps">
        {steps.slice(0, 4).map((step, idx) => (
          <span key={idx} className="workflow-card-step-chip">
            {idx + 1}. {step.skill}
          </span>
        ))}
        {steps.length > 4 && (
          <span className="workflow-card-step-more">+{steps.length - 4}</span>
        )}
      </div>
      {/* endregion */}
    </div>
  )
}

export default WorkflowsPage

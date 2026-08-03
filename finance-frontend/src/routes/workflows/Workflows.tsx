/**
 * 工作流模板列表页
 *
 * 功能（M3.6）：
 * - 卡片网格展示当前组织下的工作流模板（6 个内置 + 自定义）
 * - 行业 tabs 筛选（全部 / 银行 / 保险 / 证券），对齐原型 filter-tabs
 * - 名称模糊搜索（防抖），对齐原型 search-box
 * - 卡片展示：行业图标 + 名称 + 行业 + 风险 badge + 描述 + 步骤/Skills + 详情/运行按钮
 * - 点击详情/运行跳转详情页
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { workflowApi, INDUSTRY_LABELS, RISK_LEVEL_LABELS } from '@/api/workflows'
import { parseWorkflowSteps } from '@/api/workflows'
import type {
  WorkflowIndustry,
  WorkflowQueryRequest,
  WorkflowVO,
} from '@/api/types'
import { ApiError } from '@/api/AxiosClient'
import {
  IconAlert,
  IconPlay,
  IconRefresh,
  IconSearch,
  IconWorkflow,
} from '@/components/Icons'

/** 默认页大小 */
const DEFAULT_PAGE_SIZE = 12

/** 行业 tabs 选项（对齐原型 filter-tabs：全部 / 银行 / 保险 / 证券） */
const INDUSTRY_TABS: Array<{ value: '' | WorkflowIndustry; label: string }> = [
  { value: '', label: '全部' },
  { value: 'banking', label: '银行' },
  { value: 'insurance', label: '保险' },
  { value: 'securities', label: '证券' },
]

/** 工作流模板列表页 */
function WorkflowsPage() {
  const navigate = useNavigate()

  // 1. 查询条件
  const [current, setCurrent] = useState(1)
  const [pageSize] = useState(DEFAULT_PAGE_SIZE)
  const [industry, setIndustry] = useState<'' | WorkflowIndustry>('')
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
    () => ['workflows', { current, pageSize, industry, searchText }] as const,
    [current, pageSize, industry, searchText],
  )

  // 4. 查询工作流模板列表
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey,
    queryFn: () =>
      workflowApi.listWorkflows({
        current,
        pageSize,
        industry,
        name: searchText,
      } satisfies WorkflowQueryRequest),
    refetchOnWindowFocus: false,
  })

  /** 行业 tab 切换 */
  const handleTabChange = (val: '' | WorkflowIndustry) => {
    setIndustry(val)
    setCurrent(1)
  }

  const records: WorkflowVO[] = data?.records ?? []
  const total: number = data?.total ?? 0

  return (
    <div className="workflows-page">
      {/* region 页面标题 + 操作区 */}
      <div className="workflows-header">
        <div>
          <h1 className="page-title">工作流管理</h1>
          <div className="breadcrumb">首页 / 自动化 / 工作流管理</div>
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

      {/* region 筛选栏：行业 tabs + 搜索框（对齐原型 filter-bar） */}
      <div className="filter-bar">
        <div className="filter-tabs">
          {INDUSTRY_TABS.map((tab) => (
            <button
              key={tab.value || 'all'}
              type="button"
              className={industry === tab.value ? 'active' : ''}
              onClick={() => handleTabChange(tab.value)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="search-box">
          <IconSearch size={14} className="search-box-icon" />
          <input
            type="text"
            placeholder="搜索工作流名称…"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>
      </div>
      {/* endregion */}

      {/* region 错误提示 */}
      {error && (
        <div className="form-error" style={{ margin: '0 0 16px' }}>
          <IconAlert size={14} />
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
            {searchText || industry
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
              onDetail={() => navigate(`/workflows/${wf.workflowId}`)}
              onRun={() => navigate(`/workflows/${wf.workflowId}`)}
            />
          ))}
        </div>
      )}
      {/* endregion */}

      {/* region 统计信息 */}
      {total > 0 && (
        <div className="workflows-stats">
          共 <strong>{total}</strong> 个模板
          {searchText || industry ? ' · 已筛选' : ''}
        </div>
      )}
      {/* endregion */}
    </div>
  )
}

/**
 * 工作流卡片（对齐原型 workflow-card 结构）
 *
 * 结构：行业图标 + 名称 + 行业 + 风险 badge / 描述 / 步骤·Skills / 详情+运行按钮
 *
 * @param workflow 工作流模板
 * @param onDetail 点击详情回调
 * @param onRun    点击运行回调
 */
function WorkflowCard({
  workflow,
  onDetail,
  onRun,
}: {
  workflow: WorkflowVO
  onDetail: () => void
  onRun: () => void
}) {
  // 1. 解析步骤
  const steps = parseWorkflowSteps(workflow.steps)
  const skills = new Set(steps.map((s) => s.skill)).size

  // 2. 启用状态
  const enabled = workflow.enabled === 1

  return (
    <div
      className={`workflow-card glass-card-static${enabled ? '' : ' workflow-card-disabled'}`}
    >
      {/* region 卡片头部：行业图标 + 名称 + 行业 + 风险 badge */}
      <div className="workflow-card-header">
        <div className={`industry-icon industry-${workflow.industry}`}>
          <IndustryIcon industry={workflow.industry} />
        </div>
        <div className="workflow-card-title-wrap">
          <div className="workflow-name">{workflow.name}</div>
          <div className="workflow-industry">
            {INDUSTRY_LABELS[workflow.industry]}业务
          </div>
        </div>
        <span className={`badge risk-${workflow.riskLevel}`}>
          {RISK_LEVEL_LABELS[workflow.riskLevel]}
        </span>
      </div>
      {/* endregion */}

      {/* region 卡片描述 */}
      <p className="workflow-card-desc">{workflow.description || '暂无描述'}</p>
      {/* endregion */}

      {/* region 卡片 meta：步骤数 · Skills 数（对齐原型 workflow-card-meta） */}
      <div className="workflow-card-meta">
        <span>{steps.length} 步骤</span>
        <span className="dot">·</span>
        <span>{skills} Skills</span>
      </div>
      {/* endregion */}

      {/* region 卡片统计：已执行次数（对齐原型 workflow-card-stats） */}
      <div className="workflow-card-stats">
        已执行 <span className="mono">{workflow.runCount ?? 0}</span> 次
      </div>
      {/* endregion */}

      {/* region 卡片操作：详情 + 运行（对齐原型 workflow-card-actions） */}
      <div className="workflow-card-actions">
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={onDetail}
          disabled={!enabled}
        >
          详情
        </button>
        <button
          type="button"
          className="btn btn-primary btn-sm"
          onClick={onRun}
          disabled={!enabled}
        >
          <IconPlay size={12} />
          运行
        </button>
      </div>
      {/* endregion */}
    </div>
  )
}

/**
 * 行业图标（对齐原型 SVG：银行=建筑，保险=盾牌，证券=折线图）
 *
 * @param industry 行业枚举
 */
function IndustryIcon({ industry }: { industry: WorkflowIndustry }) {
  if (industry === 'banking') {
    // 银行：建筑图标
    return (
      <svg
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M3 21h18" />
        <path d="M5 21V7l7-4 7 4v14" />
        <path d="M9 21v-6h6v6" />
        <line x1="9" y1="9" x2="9" y2="9.01" />
        <line x1="15" y1="9" x2="15" y2="9.01" />
      </svg>
    )
  }
  if (industry === 'insurance') {
    // 保险：盾牌图标
    return (
      <svg
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </svg>
    )
  }
  // 证券：折线图图标
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
      <polyline points="17 6 23 6 23 12" />
    </svg>
  )
}

export default WorkflowsPage

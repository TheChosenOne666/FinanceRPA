/**
 * 工作流模板详情页
 *
 * 功能（M3.6）：
 * - 基本信息：名称 / 描述 / 行业 / 风险 / 版本 / 启用状态 / 创建时间
 * - 参数表单：按 params JSON schema 动态生成（string / number 类型 + required + encrypted）
 * - Skill 步骤可视化：横向流程图，每个节点显示 skill 名 + 参数映射预览（脱敏加密参数）
 * - 触发执行：填写参数表单 → POST /workflows/{id}/run → 跳转任务详情页
 * - 查看执行历史：跳转 /workflows/{id}/runs
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import {
  workflowApi,
  INDUSTRY_LABELS,
  RISK_LEVEL_LABELS,
  parseWorkflowParams,
  parseWorkflowSteps,
} from '@/api/workflows'
import { ApiError } from '@/api/AxiosClient'
import type { WorkflowParam } from '@/api/types'
import {
  IconAlert,
  IconArrowLeft,
  IconClock,
  IconExternal,
  IconPlay,
  IconRefresh,
  IconWorkflow,
} from '@/components/Icons'

/** 参数表单值（key 为参数 name，value 为用户输入） */
type ParamFormValues = Record<string, string>

/** 表单字段错误 */
interface FieldErrors {
  [paramName: string]: string
}

/** 工作流详情页 */
function WorkflowDetail() {
  const navigate = useNavigate()
  const { workflowId } = useParams<{ workflowId: string }>()

  // 1. 查询工作流详情
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['workflow', workflowId],
    queryFn: () => workflowApi.getWorkflow(workflowId!),
    enabled: !!workflowId,
    refetchOnWindowFocus: false,
  })

  // 2. 加载中 / 错误兜底
  if (isLoading) {
    return (
      <div className="workflow-detail">
        <div className="workflow-detail-loading glass-card-static">加载工作流详情中…</div>
      </div>
    )
  }
  if (error) {
    return (
      <div className="workflow-detail">
        <BackButton onClick={() => navigate('/workflows')} />
        <div className="form-error" style={{ margin: '16px 0' }}>
          <IconAlert size={14} />
          加载失败：
          {error instanceof ApiError ? error.message : (error as Error).message}
        </div>
      </div>
    )
  }
  if (!data || !workflowId) {
    return (
      <div className="workflow-detail">
        <BackButton onClick={() => navigate('/workflows')} />
        <div className="workflow-detail-loading glass-card-static">未找到工作流模板</div>
      </div>
    )
  }

  return (
    <div className="workflow-detail">
      {/* region 顶部：返回 + 标题 + 操作 */}
      <div className="workflow-detail-header">
        <BackButton onClick={() => navigate('/workflows')} />
        <div className="workflow-detail-title">
          <h1 className="page-title">
            <IconWorkflow size={20} />
            <span title={data.name}>{data.name}</span>
          </h1>
          <p className="workflow-detail-desc">{data.description || '暂无描述'}</p>
        </div>
        <div className="workflow-detail-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => refetch()}
            disabled={isFetching}
            title="刷新详情"
          >
            <IconRefresh size={14} />
            {isFetching ? '刷新中…' : '刷新'}
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => navigate(`/workflows/${workflowId}/runs`)}
            title="查看该工作流的执行历史"
          >
            <IconClock size={14} />
            执行历史
          </button>
        </div>
      </div>
      {/* endregion */}

      <div className="workflow-detail-grid">
        {/* region 左侧：基本信息 + 参数表单 */}
        <div className="workflow-detail-left">
          {/* 基本信息 */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">
              <IconWorkflow size={14} />
              基本信息
            </h2>
            <div className="info-grid">
              <InfoItem label="工作流 ID" value={data.workflowId} mono />
              <InfoItem
                label="行业"
                value={
                  <span className={`tag tag-industry tag-industry-${data.industry}`}>
                    {INDUSTRY_LABELS[data.industry]}
                  </span>
                }
              />
              <InfoItem
                label="风险等级"
                value={
                  <span className={`tag tag-risk tag-risk-${data.riskLevel}`}>
                    {RISK_LEVEL_LABELS[data.riskLevel]}风险
                  </span>
                }
              />
              <InfoItem label="版本" value={data.version || '-'} mono />
              <InfoItem
                label="启用状态"
                value={
                  data.enabled === 1 ? (
                    <span className="tag tag-enabled">启用</span>
                  ) : (
                    <span className="tag tag-disabled">禁用</span>
                  )
                }
              />
              <InfoItem
                label="创建时间"
                value={dayjs(data.createTime).format('YYYY-MM-DD HH:mm:ss')}
                mono
              />
            </div>
          </section>

          {/* 参数表单（触发执行） */}
          <WorkflowRunForm
            workflowId={workflowId}
            workflowName={data.name}
            paramsJson={data.params}
            enabled={data.enabled === 1}
          />
        </div>
        {/* endregion */}

        {/* region 右侧：Skill 步骤可视化 */}
        <div className="workflow-detail-right">
          <section className="glass-card-static detail-section">
            <h2 className="section-title">
              <IconExternal size={14} />
              Skill 步骤流程
            </h2>
            <SkillStepsVisualization
              paramsJson={data.params}
              stepsJson={data.steps}
            />
          </section>

          {/* 参数定义详情 */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">
              <IconClock size={14} />
              参数定义
            </h2>
            <ParamsDefinition paramsJson={data.params} />
          </section>
        </div>
        {/* endregion */}
      </div>
    </div>
  )
}

/**
 * 返回按钮
 *
 * @param onClick 点击回调
 */
function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="btn btn-ghost btn-sm workflow-detail-back" onClick={onClick}>
      <IconArrowLeft size={14} />
      返回列表
    </button>
  )
}

/**
 * 信息项
 *
 * @param label  标签
 * @param value  值
 * @param mono   是否使用等宽字体
 */
function InfoItem({
  label,
  value,
  mono = false,
}: {
  label: string
  value: React.ReactNode
  mono?: boolean
}) {
  return (
    <div className="info-item">
      <span className="info-label">{label}</span>
      <span className={`info-value${mono ? ' info-value-mono' : ''}`}>{value}</span>
    </div>
  )
}

/**
 * 工作流触发执行表单
 *
 * 按 params JSON schema 动态生成表单项，提交后调用 POST /workflows/{id}/run
 *
 * @param workflowId   工作流 ID
 * @param workflowName 工作流名称（用于提示）
 * @param paramsJson   参数定义 JSON 字符串
 * @param enabled      是否启用（禁用时禁止触发）
 */
function WorkflowRunForm({
  workflowId,
  workflowName,
  paramsJson,
  enabled,
}: {
  workflowId: string
  workflowName: string
  paramsJson: string
  enabled: boolean
}) {
  const navigate = useNavigate()
  const [formValues, setFormValues] = useState<ParamFormValues>({})
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  // 1. 解析参数定义
  const params = useMemo(
    () => parseWorkflowParams(paramsJson),
    [paramsJson],
  )

  // 2. 客户端校验
  const validate = (): boolean => {
    const errs: FieldErrors = {}
    for (const p of params) {
      if (p.required && !formValues[p.name]?.trim()) {
        errs[p.name] = `${p.description || p.name} 不能为空`
      }
    }
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  // 3. 提交表单
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    setFormError(null)
    setSubmitting(true)

    try {
      // 3.1 构造运行参数
      const runParams: Record<string, unknown> = {}
      for (const p of params) {
        const val = formValues[p.name]?.trim()
        if (val !== undefined && val !== '') {
          runParams[p.name] = val
        }
      }

      // 3.2 调用触发 API
      const result = await workflowApi.runWorkflow(workflowId, { params: runParams })

      // 3.3 成功 → 跳转任务详情页
      navigate(`/tasks/${result.taskId}`)
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : '触发执行失败，请稍后重试'
      setFormError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  // 4. 表单项值变更
  const handleFieldChange = (name: string, value: string) => {
    setFormValues((prev) => ({ ...prev, [name]: value }))
    // 清除该字段错误
    if (errors[name]) {
      setErrors((prev) => {
        const next = { ...prev }
        delete next[name]
        return next
      })
    }
  }

  if (!enabled) {
    return (
      <section className="glass-card-static detail-section">
        <h2 className="section-title">
          <IconPlay size={14} />
          触发执行
        </h2>
        <div className="detail-empty">
          <IconAlert size={28} />
          <div>模板已禁用</div>
          <div className="detail-empty-desc">无法触发已禁用的工作流模板</div>
        </div>
      </section>
    )
  }

  return (
    <section className="glass-card-static detail-section">
      <h2 className="section-title">
        <IconPlay size={14} />
        触发执行 · {workflowName}
      </h2>

      {/* 服务端错误 */}
      {formError && <div className="form-error">{formError}</div>}

      {params.length === 0 ? (
        <div className="detail-empty">
          <IconPlay size={28} />
          <div>无参数</div>
          <div className="detail-empty-desc">该工作流无运行参数，可直接触发执行</div>
        </div>
      ) : (
        <form className="workflow-run-form" onSubmit={handleSubmit}>
          {params.map((p) => (
            <ParamField
              key={p.name}
              param={p}
              value={formValues[p.name] || ''}
              error={errors[p.name]}
              disabled={submitting}
              onChange={(v) => handleFieldChange(p.name, v)}
            />
          ))}

          <div className="modal-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              <IconPlay size={14} />
              {submitting ? '触发中…' : '触发执行'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

/**
 * 参数表单项
 *
 * @param param    参数定义
 * @param value    当前值
 * @param error    字段错误
 * @param disabled 是否禁用
 * @param onChange 值变更回调
 */
function ParamField({
  param,
  value,
  error,
  disabled,
  onChange,
}: {
  param: WorkflowParam
  value: string
  error?: string
  disabled: boolean
  onChange: (value: string) => void
}) {
  // 1. 加密参数使用 password 输入框
  const isEncrypted = param.encrypted
  const inputType = isEncrypted ? 'password' : 'text'

  // 2. 字段 ID
  const fieldId = `param-${param.name}`

  return (
    <div className="form-group">
      <label className="label" htmlFor={fieldId}>
        {param.description || param.name}
        {param.required && <span style={{ color: 'var(--accent-danger)' }}> *</span>}
        {isEncrypted && (
          <span className="param-encrypted-badge" title="该参数将加密存储">
            加密
          </span>
        )}
      </label>
      <input
        id={fieldId}
        type={inputType}
        className={`input${error ? ' input-error' : ''}`}
        placeholder={`${param.description || param.name}（${param.type}）`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        autoComplete="off"
      />
      {param.description && (
        <div className="param-hint">
          <code>{param.name}</code>
          <span className="param-type"> · {param.type}</span>
        </div>
      )}
      {error && <div className="field-error">{error}</div>}
    </div>
  )
}

/**
 * Skill 步骤可视化
 *
 * 横向流程图：每个节点显示 skill 名称，节点之间用箭头连接。
 * 节点下方显示参数映射预览（脱敏加密参数的值显示为 ***）
 *
 * @param paramsJson 参数定义 JSON 字符串
 * @param stepsJson  步骤 JSON 字符串
 */
function SkillStepsVisualization({
  paramsJson,
  stepsJson,
}: {
  paramsJson: string
  stepsJson: string
}) {
  // 1. 解析参数与步骤
  const params = useMemo(() => parseWorkflowParams(paramsJson), [paramsJson])
  const steps = useMemo(() => parseWorkflowSteps(stepsJson), [stepsJson])

  // 2. 加密参数名集合（用于脱敏显示）
  const encryptedParamNames = useMemo(
    () => new Set(params.filter((p) => p.encrypted).map((p) => p.name)),
    [params],
  )

  if (steps.length === 0) {
    return (
      <div className="detail-empty">
        <IconWorkflow size={28} />
        <div>暂无步骤</div>
      </div>
    )
  }

  return (
    <div className="skill-flow">
      {steps.map((step, idx) => (
        <div key={idx} className="skill-flow-node-wrapper">
          {/* region 节点 */}
          <div className="skill-flow-node">
            <div className="skill-flow-node-index">{idx + 1}</div>
            <div className="skill-flow-node-content">
              <div className="skill-flow-node-title">{step.skill}</div>
              <div className="skill-flow-node-params">
                {Object.entries(step.params_mapping || {}).map(([key, value]) => (
                  <div key={key} className="skill-flow-param">
                    <span className="skill-flow-param-key">{key}:</span>
                    <span className="skill-flow-param-value">
                      {renderParamValue(value, encryptedParamNames)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
          {/* endregion */}

          {/* region 箭头连接线 */}
          {idx < steps.length - 1 && (
            <div className="skill-flow-arrow">
              <svg width="20" height="14" viewBox="0 0 20 14" fill="none">
                <path
                  d="M2 7H17M17 7L11 2M17 7L11 12"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
          )}
          {/* endregion */}
        </div>
      ))}
    </div>
  )
}

/**
 * 渲染参数值（脱敏加密参数引用的值）
 *
 * @param value                原始值
 * @param encryptedParamNames  加密参数名集合
 */
function renderParamValue(
  value: unknown,
  encryptedParamNames: Set<string>,
): string {
  if (typeof value !== 'string') {
    return JSON.stringify(value)
  }
  // 1. 模板变量 {{param_name}} → 若为加密参数则显示 {{param_name}}(加密)
  const templateMatch = value.match(/^\{\{(\w+)\}\}$/)
  if (templateMatch) {
    const paramName = templateMatch[1]
    if (encryptedParamNames.has(paramName)) {
      return `{{${paramName}}} ***`
    }
    return `{{${paramName}}}`
  }
  // 2. 字面量值原样返回
  return value
}

/**
 * 参数定义列表
 *
 * @param paramsJson 参数定义 JSON 字符串
 */
function ParamsDefinition({ paramsJson }: { paramsJson: string }) {
  const params = useMemo(() => parseWorkflowParams(paramsJson), [paramsJson])

  if (params.length === 0) {
    return (
      <div className="detail-empty">
        <IconClock size={28} />
        <div>无参数定义</div>
      </div>
    )
  }

  return (
    <div className="params-definition">
      <table className="params-table">
        <thead>
          <tr>
            <th style={{ width: '24%' }}>参数名</th>
            <th style={{ width: '14%' }}>类型</th>
            <th style={{ width: '14%' }}>必填</th>
            <th style={{ width: '14%' }}>加密</th>
            <th>描述</th>
          </tr>
        </thead>
        <tbody>
          {params.map((p) => (
            <tr key={p.name}>
              <td className="cell-mono">{p.name}</td>
              <td>{p.type}</td>
              <td>
                {p.required ? (
                  <span className="tag tag-required">是</span>
                ) : (
                  <span className="tag tag-optional">否</span>
                )}
              </td>
              <td>
                {p.encrypted ? (
                  <span className="tag tag-encrypted">加密</span>
                ) : (
                  <span style={{ color: 'var(--text-muted)' }}>-</span>
                )}
              </td>
              <td>{p.description || '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default WorkflowDetail

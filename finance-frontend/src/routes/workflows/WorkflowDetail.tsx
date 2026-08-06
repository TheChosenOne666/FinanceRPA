/**
 * 工作流模板详情页
 *
 * 功能（M3.6，UI 对齐原型 04-workflows.html）：
 * - detail-header：返回 + 标题（含风险 badge）+ 面包屑 + 运行按钮
 * - grid-2 左侧：工作流配置（config-grid）+ 执行步骤（workflow-steps）+ 执行历史（history-list）
 * - grid-2 右侧：触发运行表单（trigger-form + risk-notice）
 * - 触发执行：填写参数表单 → POST /workflows/{id}/run → 跳转任务详情页
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useMemo, useRef, useState, type FormEvent } from 'react'
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
import { taskApi } from '@/api/tasks'
import { ApiError } from '@/api/AxiosClient'
import type { TaskVO, WorkflowParam, WorkflowStep } from '@/api/types'
import {
  IconAlert,
  IconArrowLeft,
  IconPlay,
  IconRefresh,
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
  const triggerFormRef = useRef<HTMLDivElement>(null)

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

  /** 滚动到触发表单 */
  const handleScrollToForm = () => {
    triggerFormRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  return (
    <div className="workflow-detail">
      {/* region detail-header：返回 + 标题 + 风险 badge + 运行按钮（对齐原型） */}
      <div className="detail-header">
        <div className="detail-title-row">
          <BackButton onClick={() => navigate('/workflows')} />
          <div>
            <h1 className="detail-title">
              {data.name}
              <span className={`badge risk-${data.riskLevel}`}>
                {RISK_LEVEL_LABELS[data.riskLevel]}风险
              </span>
            </h1>
            <div className="breadcrumb">
              工作流 / {INDUSTRY_LABELS[data.industry]}业务 / {data.name}
            </div>
          </div>
        </div>
        <div className="detail-header-actions">
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
          {data.enabled === 1 && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleScrollToForm}
              title="滚动到触发表单"
            >
              <IconPlay size={14} />
              运行
            </button>
          )}
        </div>
      </div>
      {/* endregion */}

      <div className="workflow-detail-grid">
        {/* region 左侧：工作流配置 + 执行步骤 + 执行历史（对齐原型 grid-2 左栏） */}
        <div className="workflow-detail-left">
          {/* 工作流配置 */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">工作流配置</h2>
            <div className="config-grid">
              <div className="config-item">
                <div className="config-key">工作流 ID</div>
                <div className="config-val cell-mono">{data.workflowId}</div>
              </div>
              <div className="config-item">
                <div className="config-key">所属行业</div>
                <div className="config-val">{INDUSTRY_LABELS[data.industry]}</div>
              </div>
              <div className="config-item">
                <div className="config-key">创建人</div>
                <div className="config-val">{data.createUser || '系统'}</div>
              </div>
              <div className="config-item">
                <div className="config-key">创建时间</div>
                <div className="config-val cell-mono">
                  {dayjs(data.createTime).format('YYYY-MM-DD')}
                </div>
              </div>
              <div className="config-item">
                <div className="config-key">风险等级</div>
                <div className="config-val">
                  <span className={`badge risk-${data.riskLevel}`}>
                    {RISK_LEVEL_LABELS[data.riskLevel]}
                  </span>
                </div>
              </div>
              <div className="config-item">
                <div className="config-key">最近更新</div>
                <div className="config-val cell-mono">
                  {dayjs(data.updateTime).format('YYYY-MM-DD')}
                </div>
              </div>
              <div className="config-desc">
                <strong>描述：</strong>
                {data.description || '暂无描述'}
              </div>
            </div>
          </section>

          {/* 执行步骤 */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">执行步骤</h2>
            <WorkflowSteps stepsJson={data.steps} />
          </section>

          {/* 执行历史 */}
          <section className="glass-card-static detail-section">
            <h2 className="section-title">执行历史</h2>
            <WorkflowHistory workflowId={data.workflowId} />
          </section>
        </div>
        {/* endregion */}

        {/* region 右侧：触发运行表单（对齐原型 grid-2 右栏） */}
        <div className="workflow-detail-right" ref={triggerFormRef}>
          <WorkflowRunForm
            workflowId={workflowId}
            workflowName={data.name}
            riskLevel={data.riskLevel}
            paramsJson={data.params}
            enabled={data.enabled === 1}
          />
        </div>
        {/* endregion */}
      </div>
    </div>
  )
}

/**
 * 返回按钮（对齐原型 icon-btn 风格）
 *
 * @param onClick 点击回调
 */
function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      className="icon-btn workflow-detail-back"
      onClick={onClick}
      title="返回列表"
    >
      <IconArrowLeft size={16} />
    </button>
  )
}

/**
 * 执行步骤列表（对齐原型 workflow-steps：有序列表 + 序号圆圈 + skill 名 + 参数）
 *
 * @param stepsJson 步骤 JSON 字符串
 */
function WorkflowSteps({ stepsJson }: { stepsJson: string }) {
  const steps = useMemo(() => parseWorkflowSteps(stepsJson), [stepsJson])

  if (steps.length === 0) {
    return <div className="detail-empty">暂无步骤</div>
  }

  return (
    <ol className="workflow-steps">
      {steps.map((step, idx) => (
        <li key={idx}>
          <div>
            <div className="step-name">{step.skill}</div>
            <div className="step-params">
              {formatStepParams(step)}
            </div>
          </div>
        </li>
      ))}
    </ol>
  )
}

/**
 * 格式化步骤参数为 key=value, key=value 字符串
 *
 * @param step 步骤定义
 */
function formatStepParams(step: WorkflowStep): string {
  const entries = Object.entries(step.params_mapping || {})
  if (entries.length === 0) return '-'
  return entries
    .map(([k, v]) => `${k}=${typeof v === 'string' ? v : JSON.stringify(v)}`)
    .join(', ')
}

/**
 * 执行历史列表（对齐原型 history-list）
 *
 * 查询最近 5 条该工作流的任务记录
 *
 * @param workflowId 工作流 ID
 */
function WorkflowHistory({ workflowId }: { workflowId: string }) {
  // 1. 查询最近 5 条该工作流的任务记录
  const { data, isLoading } = useQuery({
    queryKey: ['workflow-runs', workflowId],
    queryFn: () =>
      taskApi.listTasks({
        current: 1,
        pageSize: 5,
        workflowId,
      }),
    refetchOnWindowFocus: false,
  })

  if (isLoading) {
    return <div className="detail-empty">加载历史记录中…</div>
  }

  const records: TaskVO[] = data?.records ?? []
  if (records.length === 0) {
    return <div className="detail-empty">暂无执行历史</div>
  }

  return (
    <div className="history-list">
      {records.map((task) => (
        <div
          key={task.taskId}
          className="history-item"
          onClick={() => undefined}
          role="button"
          tabIndex={0}
        >
          <div className="history-left">
            <div className="history-time">
              {dayjs(task.createTime).format('YYYY-MM-DD HH:mm:ss')}
            </div>
            <div className="history-meta">
              用户 {task.userId} · {task.currentStep}/{task.totalSteps} 步
            </div>
          </div>
          <TaskStatusBadge status={task.status} />
        </div>
      ))}
    </div>
  )
}

/**
 * 任务状态 badge（对齐原型 badge-success / badge-danger）
 *
 * @param status 任务状态
 */
function TaskStatusBadge({ status }: { status: TaskVO['status'] }) {
  const map: Record<
    TaskVO['status'],
    { label: string; className: string }
  > = {
    SUCCESS: { label: '成功', className: 'badge badge-success' },
    FAILED: { label: '失败', className: 'badge badge-danger' },
    EXECUTING: { label: '执行中', className: 'badge badge-info' },
    PENDING: { label: '待执行', className: 'badge badge-warning' },
    NEEDS_HUMAN: { label: '待人工', className: 'badge badge-warning' },
    ABORTED: { label: '已终止', className: 'badge badge-danger' },
  }
  const cfg = map[status] || { label: status, className: 'badge' }
  return <span className={cfg.className}>{cfg.label}</span>
}

/**
 * 工作流触发执行表单（对齐原型 trigger-form + risk-notice）
 *
 * 按 params JSON schema 动态生成表单项，提交后调用 POST /workflows/{id}/run
 *
 * @param workflowId   工作流 ID
 * @param workflowName 工作流名称（用于提示）
 * @param riskLevel    风险等级（用于风险提示）
 * @param paramsJson   参数定义 JSON 字符串
 * @param enabled      是否启用（禁用时禁止触发）
 */
function WorkflowRunForm({
  workflowId,
  workflowName,
  riskLevel,
  paramsJson,
  enabled,
}: {
  workflowId: string
  workflowName: string
  riskLevel: string
  paramsJson: string
  enabled: boolean
}) {
  const navigate = useNavigate()
  const [formValues, setFormValues] = useState<ParamFormValues>({})
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  // 1. 解析参数定义
  const params = useMemo(() => parseWorkflowParams(paramsJson), [paramsJson])

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

      // 3.3 根据初始状态跳转：PENDING_APPROVAL → 审批中心（携带 approvalId）；其他 → 任务详情页
      if (result.state === 'PENDING_APPROVAL' && result.approvalId) {
        navigate(`/approvals?approvalId=${result.approvalId}`)
      } else {
        navigate(`/tasks/${result.taskId}`)
      }
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
        <h2 className="section-title">触发运行</h2>
        <div className="detail-empty">
          <IconAlert size={28} />
          <div>模板已禁用</div>
          <div className="detail-empty-desc">无法触发已禁用的工作流模板</div>
        </div>
      </section>
    )
  }

  // 风险提示：high / critical 显示
  const showRiskNotice = riskLevel === 'high' || riskLevel === 'critical'

  return (
    <section className="glass-card-static detail-section">
      <h2 className="section-title">触发运行 · {workflowName}</h2>

      {/* 服务端错误 */}
      {formError && <div className="form-error">{formError}</div>}

      {params.length === 0 ? (
        <div className="detail-empty">
          <IconPlay size={28} />
          <div>无参数</div>
          <div className="detail-empty-desc">该工作流无运行参数，可直接触发执行</div>
        </div>
      ) : (
        <form className="trigger-form" onSubmit={handleSubmit}>
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

          {/* 风险提示（对齐原型 risk-notice） */}
          {showRiskNotice && (
            <div className="risk-notice">
              <IconAlert size={14} />
              <div>
                该工作流风险等级为
                <strong>{RISK_LEVEL_LABELS[riskLevel as keyof typeof RISK_LEVEL_LABELS] || riskLevel}</strong>
                ，运行后将自动提交至审批中心，由审批人确认后执行。
              </div>
            </div>
          )}

          <div className="form-actions">
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => navigate('/workflows')}
              disabled={submitting}
            >
              取消
            </button>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              <IconPlay size={14} />
              {submitting ? '触发中…' : '确认运行'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

/**
 * 参数表单项（对齐原型 form-group + label + input）
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
  // 1. 加密参数使用 password 输入框；date 类型使用 date 输入框；其余 text
  const isEncrypted = param.encrypted
  const inputType = isEncrypted ? 'password' : param.type === 'date' ? 'date' : 'text'

  // 2. 字段 ID
  const fieldId = `param-${param.name}`

  return (
    <div className="form-group">
      <label className="label" htmlFor={fieldId}>
        {param.description || param.name}
        {param.required && <span className="required">*</span>}
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
        placeholder={inputType === 'date' ? undefined : `${param.description || param.name}（${param.type}）`}
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

export default WorkflowDetail

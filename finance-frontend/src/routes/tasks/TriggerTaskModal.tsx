/**
 * 触发任务弹窗
 *
 * 简易表单：
 * - 导航目标 URL（必填）
 * - 任务参数（JSON 文本，可选）
 * - 关联工作流 ID（可选，M3 阶段填充下拉选择）
 *
 * 提交后调用 POST /ai/tasks，成功后通知父组件跳转任务详情页。
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useState, type FormEvent } from 'react'
import { taskApi } from '@/api/tasks'
import { ApiError } from '@/api/AxiosClient'
import { IconClose, IconPlay } from '@/components/Icons'

/** 触发任务弹窗属性 */
export interface TriggerTaskModalProps {
  /** 关闭弹窗回调 */
  onClose: () => void
  /** 触发成功回调（参数为新任务 ID） */
  onTriggered: (taskId: string) => void
}

/** 表单字段错误 */
interface FieldErrors {
  goal?: string
  params?: string
  workflowId?: string
}

/**
 * 触发任务弹窗
 */
function TriggerTaskModal({ onClose, onTriggered }: TriggerTaskModalProps) {
  // 1. 表单状态
  const [goal, setGoal] = useState('')
  const [paramsText, setParamsText] = useState('')
  const [workflowId, setWorkflowId] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  /** 客户端校验 */
  const validate = (): boolean => {
    const errs: FieldErrors = {}
    if (!goal.trim()) errs.goal = '请输入任务目标'
    // 2.1 参数 JSON 可选，但若填写必须可解析
    if (paramsText.trim()) {
      try {
        const parsed = JSON.parse(paramsText)
        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
          errs.params = '参数必须是 JSON 对象'
        }
      } catch {
        errs.params = '参数不是合法的 JSON'
      }
    }
    // 2.2 workflowId 可选，但填写时需为数字字符串
    if (workflowId.trim() && !/^\d+$/.test(workflowId.trim())) {
      errs.workflowId = '工作流 ID 必须为数字'
    }
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  /** 提交表单 */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    setFormError(null)
    setSubmitting(true)

    try {
      // 3. 构造请求体
      const params = paramsText.trim() ? JSON.parse(paramsText) : undefined
      const response = await taskApi.triggerTask({
        goal: goal.trim(),
        params,
        workflowId: workflowId.trim() || undefined,
      })
      // 4. 成功 → 通知父组件
      onTriggered(response.taskId)
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : '触发任务失败，请稍后重试'
      setFormError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  /** 点击遮罩关闭 */
  const handleOverlayClick = () => {
    if (!submitting) onClose()
  }

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div
        className="glass-card modal-card"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: 560 }}
      >
        {/* region 弹窗头部 */}
        <div className="modal-header">
          <div className="modal-title">
            <IconPlay size={18} />
            触发新任务
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            disabled={submitting}
            aria-label="关闭"
          >
            <IconClose size={16} />
          </button>
        </div>
        {/* endregion */}

        {/* region 服务端错误 */}
        {formError && <div className="form-error">{formError}</div>}
        {/* endregion */}

        {/* region 表单 */}
        <form className="modal-form" onSubmit={handleSubmit}>
          {/* 任务目标 */}
          <div className="form-group">
            <label className="label" htmlFor="trigger-goal">
              任务目标 <span style={{ color: 'var(--accent-danger)' }}>*</span>
            </label>
            <input
              id="trigger-goal"
              className={`input${errors.goal ? ' input-error' : ''}`}
              placeholder="如：下载银行流水 / 登录网银系统"
              value={goal}
              onChange={(e) => setGoal(e.target.value)}
              autoFocus
              disabled={submitting}
            />
            {errors.goal && <div className="field-error">{errors.goal}</div>}
          </div>

          {/* 任务参数 */}
          <div className="form-group">
            <label className="label" htmlFor="trigger-params">
              任务参数（可选，JSON 对象）
            </label>
            <textarea
              id="trigger-params"
              className={`textarea${errors.params ? ' input-error' : ''}`}
              placeholder={'{\n  "url": "https://example.com",\n  "account": "***"\n}'}
              value={paramsText}
              onChange={(e) => setParamsText(e.target.value)}
              rows={5}
              disabled={submitting}
              style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}
            />
            {errors.params && <div className="field-error">{errors.params}</div>}
          </div>

          {/* 工作流 ID */}
          <div className="form-group">
            <label className="label" htmlFor="trigger-workflow">
              关联工作流 ID（可选）
            </label>
            <input
              id="trigger-workflow"
              className={`input${errors.workflowId ? ' input-error' : ''}`}
              placeholder="留空表示独立任务"
              value={workflowId}
              onChange={(e) => setWorkflowId(e.target.value)}
              disabled={submitting}
            />
            {errors.workflowId && <div className="field-error">{errors.workflowId}</div>}
          </div>

          {/* 操作按钮 */}
          <div className="modal-actions">
            <button
              type="button"
              className="btn btn-ghost"
              onClick={onClose}
              disabled={submitting}
            >
              取消
            </button>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              <IconPlay size={14} />
              {submitting ? '触发中…' : '触发执行'}
            </button>
          </div>
        </form>
        {/* endregion */}
      </div>
    </div>
  )
}

export default TriggerTaskModal

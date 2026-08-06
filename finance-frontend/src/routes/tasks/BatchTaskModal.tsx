/**
 * 批量任务弹窗（数据驱动）
 *
 * 支持三种数据来源：
 * - 上传文件：CSV / TSV / Excel(.xlsx/.xls)，前端解析为行数据
 * - 粘贴多行：制表符/逗号分隔，首行表头为列名
 * - 外部数据源：指定表名 + 字段映射（后端从业务系统拉取）
 * 统一按 columnMapping 将列名映射到工作流模板 param name，批量生成任务。
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import { workflowApi } from '@/api/workflows'
import { ApiError } from '@/api/AxiosClient'
import type { BatchTaskResultVO, WorkflowVO } from '@/api/types'
import { IconClose, IconPlay, IconUpload, IconDatabase } from '@/components/Icons'
import { parseDelimited, collectColumns, isExcelFile, parseExcelBuffer } from '@/utils/batchParser'

/** 数据来源类型 */
type SourceType = 'csv' | 'paste' | 'external'

/** 弹窗属性 */
export interface BatchTaskModalProps {
  /** 可选预选的工作流 */
  presetWorkflow?: WorkflowVO | null
  /** 关闭弹窗回调 */
  onClose: () => void
  /** 批量完成回调（携带结果用于展示） */
  onCompleted: (result: BatchTaskResultVO) => void
}

/**
 * 批量任务弹窗
 */
function BatchTaskModal({ presetWorkflow, onClose, onCompleted }: BatchTaskModalProps) {
  const [source, setSource] = useState<SourceType>('csv')
  const [workflowId, setWorkflowId] = useState<string>(
    presetWorkflow?.id != null ? String(presetWorkflow.id) : '',
  )
  const [csvText, setCsvText] = useState('')
  const [pasteText, setPasteText] = useState('')
  const [tableName, setTableName] = useState('')
  const [whereClause, setWhereClause] = useState('')
  const [limit, setLimit] = useState(100)
  // columnMapping: 列名(原始) -> 模板 param name
  const [mapping, setMapping] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  /** 当前可用的源行（用于自动推断列名与生成映射） */
  const sourceColumns = useMemo(() => {
    if (source === 'csv' && csvText.trim()) {
      return collectColumns(parseDelimited(csvText))
    }
    if (source === 'paste' && pasteText.trim()) {
      return collectColumns(parseDelimited(pasteText))
    }
    if (source === 'external') {
      // 外部表列名未知，需用户手工录入映射（此处不预填）
      return []
    }
    return []
  }, [source, csvText, pasteText])

  /** 自动将列名映射到同名 param（提交前用户可改） */
  const ensureMapping = (columns: string[]): Record<string, string> => {
    const next = { ...mapping }
    columns.forEach((c) => {
      if (!next[c]) next[c] = c
    })
    return next
  }

  /** 提交表单 */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)

    if (!workflowId.trim() || !/^\d+$/.test(workflowId.trim())) {
      setFormError('请填写有效的工作流 ID')
      return
    }
    if (Object.keys(mapping).length === 0) {
      setFormError('请先提供数据并确认字段映射')
      return
    }

    const payload: Record<string, unknown> = {
      workflowId: Number(workflowId.trim()),
      columnMapping: mapping,
    }

    if (source === 'csv') {
      const rows = parseDelimited(csvText)
      if (rows.length === 0) {
        setFormError('CSV 内容为空或格式不正确')
        return
      }
      payload.rows = rows
    } else if (source === 'paste') {
      const rows = parseDelimited(pasteText)
      if (rows.length === 0) {
        setFormError('粘贴内容为空或格式不正确')
        return
      }
      payload.rows = rows
    } else {
      if (!tableName.trim()) {
        setFormError('请填写外部表名')
        return
      }
      payload.externalQuery = {
        tableName: tableName.trim(),
        whereClause: whereClause.trim() || undefined,
        limit: Math.max(1, Math.min(limit || 100, 1000)),
      }
    }

    setSubmitting(true)
    try {
      const result = await workflowApi.batchCreateTasks(payload as never)
      onCompleted(result)
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : '批量任务创建失败，请稍后重试'
      setFormError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  /** 文件上传（CSV/TSV/Excel） */
  const handleFile = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (isExcelFile(file.name)) {
      const reader = new FileReader()
      reader.onload = async () => {
        try {
          const rows = await parseExcelBuffer(reader.result as ArrayBuffer)
          if (rows.length === 0) {
            setFormError('Excel 文件无有效数据行（请确认首个工作表含表头）')
            return
          }
          // 复用 CSV 文本路径：将 Excel 首个工作表转回 CSV 文本
          const cols = collectColumns(rows)
          const csv = [
            cols.join(','),
            ...rows.map((r) => cols.map((c) => String(r[c] ?? '')).join(',')),
          ].join('\n')
          setCsvText(csv)
          setMapping(ensureMapping(cols))
        } catch {
          setFormError('Excel 解析失败，请确认文件未损坏且为 .xlsx/.xls 格式')
        }
      }
      reader.onerror = () => setFormError('文件读取失败')
      reader.readAsArrayBuffer(file)
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      const text = String(reader.result ?? '')
      setCsvText(text)
      setMapping(ensureMapping(parseDelimited(text).length
        ? Object.keys(parseDelimited(text)[0])
        : []))
    }
    reader.readAsText(file, 'utf-8')
  }

  const handleOverlayClick = () => {
    if (!submitting) onClose()
  }

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div
        className="glass-card modal-card"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: 680 }}
      >
        <div className="modal-header">
          <div className="modal-title">
            <IconPlay size={18} />
            批量创建任务
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

        {formError && <div className="form-error">{formError}</div>}

        <form className="modal-form" onSubmit={handleSubmit}>
          {/* 工作流模板 */}
          <div className="form-group">
            <label className="label" htmlFor="batch-wf">
              工作流模板 ID <span style={{ color: 'var(--accent-danger)' }}>*</span>
            </label>
            <input
              id="batch-wf"
              className="input"
              placeholder="目标模板 ID"
              value={workflowId}
              onChange={(e) => setWorkflowId(e.target.value)}
              disabled={submitting || !!presetWorkflow}
            />
          </div>

          {/* 数据来源切换 */}
          <div className="form-group">
            <label className="label">数据来源</label>
            <div style={{ display: 'flex', gap: 8 }}>
              <SourceTab
                active={source === 'csv'}
                onClick={() => setSource('csv')}
                icon={<IconUpload size={14} />}
                label="上传文件"
              />
              <SourceTab
                active={source === 'paste'}
                onClick={() => setSource('paste')}
                icon={<IconPlay size={14} />}
                label="粘贴多行"
              />
              <SourceTab
                active={source === 'external'}
                onClick={() => setSource('external')}
                icon={<IconDatabase size={14} />}
                label="外部数据源"
              />
            </div>
          </div>

          {/* 来源内容 */}
          {source === 'csv' && (
            <div className="form-group">
              <label className="label" htmlFor="batch-csv">
                上传文件（CSV / TSV / Excel .xlsx .xls，首行为表头）
              </label>
              <input
                id="batch-csv"
                type="file"
                accept=".csv,.tsv,.xlsx,.xls,text/csv"
                onChange={handleFile}
                disabled={submitting}
              />
              {csvText && (
                <div className="field-hint" style={{ wordBreak: 'break-all' }}>
                  已解析 {parseDelimited(csvText).length} 行，共 {sourceColumns.length} 列
                  <br />
                  <span style={{ color: 'var(--text-secondary)', fontSize: 12 }}>
                    {sourceColumns.join('、')}
                  </span>
                </div>
              )}
            </div>
          )}

          {source === 'paste' && (
            <div className="form-group">
              <label className="label" htmlFor="batch-paste">
                粘贴数据（首行表头，逗号或制表符分隔）
              </label>
              <textarea
                id="batch-paste"
                className="textarea"
                rows={6}
                placeholder={'客户姓名,身份证号,保额\n张三,110,50万\n李四,120,30万'}
                value={pasteText}
                onChange={(e) => {
                  setPasteText(e.target.value)
                  const rows = parseDelimited(e.target.value)
                  if (rows.length) setMapping(ensureMapping(Object.keys(rows[0])))
                }}
                disabled={submitting}
                style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}
              />
            </div>
          )}

          {source === 'external' && (
            <>
              <div className="form-group">
                <label className="label" htmlFor="batch-table">
                  外部表名 <span style={{ color: 'var(--accent-danger)' }}>*</span>
                </label>
                <input
                  id="batch-table"
                  className="input"
                  placeholder="如 customers"
                  value={tableName}
                  onChange={(e) => setTableName(e.target.value)}
                  disabled={submitting}
                />
              </div>
              <div className="form-group">
                <label className="label" htmlFor="batch-where">
                  WHERE 条件（可选）
                </label>
                <input
                  id="batch-where"
                  className="input"
                  placeholder="如 status = 'active'"
                  value={whereClause}
                  onChange={(e) => setWhereClause(e.target.value)}
                  disabled={submitting}
                />
              </div>
              <div className="form-group">
                <label className="label" htmlFor="batch-limit">
                  限制条数（1-1000）
                </label>
                <input
                  id="batch-limit"
                  className="input"
                  type="number"
                  min={1}
                  max={1000}
                  value={limit}
                  onChange={(e) => setLimit(Number(e.target.value))}
                  disabled={submitting}
                />
              </div>
            </>
          )}

          {/* 字段映射 */}
          {sourceColumns.length > 0 && (
            <div className="form-group" style={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
              <label className="label">字段映射（列名 → 模板参数名）</label>
              <div
                style={{
                  display: 'grid',
                  gap: 6,
                  maxHeight: 280,
                  overflow: 'auto',
                  paddingRight: 6,
                }}
              >
                {sourceColumns.map((col) => (
                  <div key={col} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ minWidth: 120, fontSize: 12, wordBreak: 'break-all' }}>{col}</span>
                    <span>→</span>
                    <input
                      className="input"
                      style={{ flex: 1 }}
                      value={mapping[col] ?? ''}
                      placeholder="模板参数名"
                      onChange={(e) =>
                        setMapping((prev) => ({ ...prev, [col]: e.target.value }))
                      }
                      disabled={submitting}
                    />
                  </div>
                ))}
              </div>
            </div>
          )}

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
              {submitting ? '提交中…' : '批量生成任务'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/** 来源切换标签 */
function SourceTab({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean
  onClick: () => void
  icon: React.ReactNode
  label: string
}) {
  return (
    <button
      type="button"
      className={`btn ${active ? 'btn-primary' : 'btn-ghost'}`}
      onClick={onClick}
      style={{ display: 'flex', alignItems: 'center', gap: 6 }}
    >
      {icon}
      {label}
    </button>
  )
}

export default BatchTaskModal

/**
 * 分页组件
 *
 * 用于任务列表 / 工作流执行历史等表格底部分页。
 * 对齐后端 PageRequest（current / pageSize）与 IPage（current / size / total / pages）字段。
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

/** 分页组件属性 */
export interface PaginationProps {
  /** 当前页号（从 1 开始） */
  current: number
  /** 页面大小 */
  pageSize: number
  /** 总记录数 */
  total: number
  /** 总页数（可选，不传则按 total/pageSize 计算） */
  pages?: number
  /** 切换页号回调 */
  onChange: (current: number, pageSize: number) => void
  /** 可选的页大小选项 */
  pageSizeOptions?: number[]
  /** 是否禁用 */
  disabled?: boolean
}

/**
 * 分页组件
 */
export function Pagination({
  current,
  pageSize,
  total,
  pages,
  onChange,
  pageSizeOptions = [10, 20, 50],
  disabled = false,
}: PaginationProps) {
  // 1. 计算总页数
  const totalPages = pages ?? Math.max(1, Math.ceil(total / pageSize))

  // 2. 生成页号按钮（最多显示 7 个，当前页居中）
  const pageButtons = getPageButtons(current, totalPages)

  /** 切换页号 */
  const goto = (page: number) => {
    if (disabled) return
    if (page < 1 || page > totalPages || page === current) return
    onChange(page, pageSize)
  }

  /** 切换页大小 */
  const handlePageSizeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    if (disabled) return
    const newSize = Number(e.target.value)
    // 切换页大小后回到第 1 页
    onChange(1, newSize)
  }

  return (
    <div className="pagination">
      {/* region 左侧：占位（让中间按钮组视觉居中，对齐原型 03-tasks.html） */}
      <div className="pagination-side pagination-side-left" />
      {/* endregion */}

      {/* region 中间：页号按钮（居中） */}
      <div className="pagination-buttons">
        <button
          type="button"
          className="btn btn-ghost btn-sm pagination-btn"
          onClick={() => goto(current - 1)}
          disabled={disabled || current <= 1}
          aria-label="上一页"
        >
          ‹
        </button>

        {pageButtons.map((p, idx) =>
          p === '...' ? (
            <span key={`gap-${idx}`} className="pagination-ellipsis">
              …
            </span>
          ) : (
            <button
              key={p}
              type="button"
              className={`btn btn-sm pagination-btn${p === current ? ' pagination-btn-active' : ''}`}
              onClick={() => goto(p)}
              disabled={disabled}
            >
              {p}
            </button>
          ),
        )}

        <button
          type="button"
          className="btn btn-ghost btn-sm pagination-btn"
          onClick={() => goto(current + 1)}
          disabled={disabled || current >= totalPages}
          aria-label="下一页"
        >
          ›
        </button>
      </div>
      {/* endregion */}

      {/* region 右侧：分页信息 + 页大小选择器（对齐原型 .page-info 风格） */}
      <div className="pagination-side pagination-side-right">
        <span className="pagination-info">
          共 <strong>{total}</strong> 条 · 每页
          <select
            className="select pagination-select"
            value={pageSize}
            onChange={handlePageSizeChange}
            disabled={disabled}
          >
            {pageSizeOptions.map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
          条
        </span>
      </div>
      {/* endregion */}
    </div>
  )
}

/**
 * 计算要展示的页号列表
 *
 * 策略：总页数 ≤ 7 全展示；否则当前页周围 ±2 + 首尾 + 省略号
 *
 * @param current 当前页
 * @param totalPages 总页数
 * @returns 页号或 '...' 占位
 */
function getPageButtons(current: number, totalPages: number): Array<number | '...'> {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1)
  }

  const buttons: Array<number | '...'> = [1]

  const left = Math.max(2, current - 2)
  const right = Math.min(totalPages - 1, current + 2)

  if (left > 2) buttons.push('...')
  for (let i = left; i <= right; i++) buttons.push(i)
  if (right < totalPages - 1) buttons.push('...')

  buttons.push(totalPages)
  return buttons
}

export default Pagination

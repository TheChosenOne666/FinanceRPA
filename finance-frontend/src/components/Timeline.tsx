/**
 * 子任务时间线组件（M4.4）
 *
 * 功能：
 * 1. 垂直时间轴：按 subtaskIndex 升序展示每个子任务状态节点 + 内容
 * 2. replan 标记：REPLANNED 状态子任务显示重规划图标节点，并在其后插入
 *    "第 N 次重规划"分隔标记，可视化展示 replan 发生点
 * 3. 子任务详情：点击子任务行展开/折叠详情面板（完成条件 / 执行结果 /
 *    子任务 ID / 耗时）
 * 4. replan 汇总：存在 REPLANNED 子任务时，顶部显示重规划次数汇总条
 *
 * 从 TaskDetail.tsx 中抽取的 SubTaskTimeline 增强而来。
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { Fragment, useMemo, useState } from 'react'
import dayjs from 'dayjs'
import type { SubTaskVO } from '@/api/types'
import StatusBadge from '@/components/StatusBadge'
import {
  IconAlert,
  IconCheck,
  IconChevronDown,
  IconClock,
  IconRefresh,
} from '@/components/Icons'

/** Timeline 属性 */
export interface TimelineProps {
  /** 子任务列表 */
  subtasks: SubTaskVO[]
  /** 是否可展开详情（默认 true） */
  expandable?: boolean
}

/**
 * 子任务时间线
 *
 * @param subtasks  子任务列表
 * @param expandable 是否可点击展开详情
 */
export function Timeline({ subtasks, expandable = true }: TimelineProps) {
  // 1. 按 subtaskIndex 升序
  const sorted = useMemo(
    () => [...subtasks].sort((a, b) => a.subtaskIndex - b.subtaskIndex),
    [subtasks],
  )

  // 2. 计算每个子任务的重规划序号（REPLANNED 子任务递增）
  const replanNumbers = useMemo(() => {
    let counter = 0
    return sorted.map((st) => {
      if (st.status === 'REPLANNED') counter += 1
      return counter
    })
  }, [sorted])

  // 3. 总重规划次数
  const totalReplans = replanNumbers.length > 0 ? replanNumbers[replanNumbers.length - 1] : 0

  // 4. 展开状态
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  const toggleExpand = (subtaskId: string) => {
    if (!expandable) return
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(subtaskId)) next.delete(subtaskId)
      else next.add(subtaskId)
      return next
    })
  }

  return (
    <div className="timeline">
      {/* region 重规划汇总 */}
      {totalReplans > 0 && (
        <div className="timeline-replan-summary">
          <IconRefresh size={12} />
          共发生 {totalReplans} 次重规划
        </div>
      )}
      {/* endregion */}

      {sorted.map((st, idx) => {
        const isExpanded = expanded.has(st.subtaskId)
        const isReplanned = st.status === 'REPLANNED'
        const showReplanMarker = isReplanned && idx < sorted.length - 1

        return (
          <Fragment key={st.subtaskId}>
            {/* region 时间线条目 */}
            <div
              className={`timeline-item${expandable ? ' timeline-item-clickable' : ''}`}
              onClick={() => toggleExpand(st.subtaskId)}
              role={expandable ? 'button' : undefined}
              tabIndex={expandable ? 0 : undefined}
              onKeyDown={
                expandable
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        toggleExpand(st.subtaskId)
                      }
                    }
                  : undefined
              }
            >
              {/* 左侧：连接线 + 节点 */}
              <div className="timeline-node-col">
                <div className={`timeline-node timeline-node-${st.status.toLowerCase()}`}>
                  {st.status === 'COMPLETED' ? (
                    <IconCheck size={12} />
                  ) : st.status === 'FAILED' ? (
                    <IconAlert size={12} />
                  ) : st.status === 'REPLANNED' ? (
                    <IconRefresh size={12} />
                  ) : st.status === 'SKIPPED' ? (
                    <span className="timeline-node-skip">—</span>
                  ) : (
                    <span className="timeline-node-num">{st.subtaskIndex + 1}</span>
                  )}
                </div>
                {idx < sorted.length - 1 && <div className="timeline-line" />}
              </div>

              {/* 右侧：内容 */}
              <div className="timeline-content">
                <div className="timeline-header">
                  <span className="timeline-title">子任务 #{st.subtaskIndex + 1}</span>
                  <StatusBadge status={st.status} subtask />
                  {expandable && (
                    <span className={`timeline-chevron${isExpanded ? ' expanded' : ''}`}>
                      <IconChevronDown size={12} />
                    </span>
                  )}
                </div>
                <div className="timeline-goal">{st.goal}</div>

                {/* 常驻 meta 信息 */}
                <div className="timeline-meta">
                  {st.startedAt && (
                    <span className="timeline-meta-item">
                      <IconClock size={11} />
                      开始：{dayjs(st.startedAt).format('HH:mm:ss')}
                    </span>
                  )}
                  {st.completedAt && (
                    <span className="timeline-meta-item">
                      <IconCheck size={11} />
                      完成：{dayjs(st.completedAt).format('HH:mm:ss')}
                    </span>
                  )}
                  {st.startedAt && st.completedAt && (
                    <span className="timeline-meta-item">
                      <IconClock size={11} />
                      耗时：{dayjs(st.completedAt).diff(dayjs(st.startedAt), 'second')}s
                    </span>
                  )}
                  {st.maxRetries !== undefined && st.maxRetries > 0 && (
                    <span className="timeline-meta-item">
                      <IconRefresh size={11} />
                      最大重试：{st.maxRetries}
                    </span>
                  )}
                  {st.failureStrategy && (
                    <span className="timeline-meta-item">
                      <IconAlert size={11} />
                      策略：{st.failureStrategy}
                    </span>
                  )}
                </div>

                {/* 错误信息（常驻） */}
                {st.errorMessage && (
                  <div className="timeline-error">
                    <IconAlert size={11} />
                    {st.errorMessage}
                  </div>
                )}

                {/* 展开详情 */}
                {isExpanded && (
                  <div className="timeline-details">
                    {st.completionCondition && (
                      <div className="timeline-detail-row">
                        <span className="timeline-detail-label">完成条件</span>
                        <span className="timeline-detail-value">{st.completionCondition}</span>
                      </div>
                    )}
                    {st.resultData && (
                      <div className="timeline-detail-row">
                        <span className="timeline-detail-label">执行结果</span>
                        <pre className="timeline-result-pre">{formatJson(st.resultData)}</pre>
                      </div>
                    )}
                    <div className="timeline-detail-row">
                      <span className="timeline-detail-label">子任务 ID</span>
                      <code className="timeline-detail-code">{st.subtaskId}</code>
                    </div>
                  </div>
                )}
              </div>
            </div>
            {/* endregion */}

            {/* region 重规划分隔标记 */}
            {showReplanMarker && (
              <div className="timeline-replan-marker">
                <span className="timeline-replan-marker-line" />
                <span className="timeline-replan-marker-badge">
                  <IconRefresh size={11} />
                  第 {replanNumbers[idx]} 次重规划
                </span>
                <span className="timeline-replan-marker-line" />
              </div>
            )}
            {/* endregion */}
          </Fragment>
        )
      })}
    </div>
  )
}

/**
 * 格式化 JSON 字符串（解析失败时返回原值）
 *
 * @param raw 原始 JSON 字符串
 * @returns 格式化后的 JSON 字符串
 */
function formatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

export default Timeline

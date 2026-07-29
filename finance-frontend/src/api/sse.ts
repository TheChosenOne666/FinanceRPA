/**
 * SSE 客户端封装
 *
 * 基于浏览器原生 EventSource 实现，订阅 Java 透传的 Python SSE 流。
 *
 * 端点：GET /api/ai/sse/tasks/{taskId}
 *   - 后端 SecurityConfig 已放行 /ai/sse/**（EventSource 无法携带自定义 Header）
 *   - Java AiSseProxy 通过 WebClient 订阅 Python sse-starlette 流并原样转发
 *
 * 事件类型（对齐 Python app/agent/event_bus.py + executor.py / coordinator.py）：
 *   - step_start：子任务开始执行
 *   - step_end：子任务执行结束（含 success / durationMs）
 *   - progress：任务级进度更新
 *   - replan：触发重新规划
 *   - screenshot：截图已上传
 *   - complete：任务完成（终态，关闭流）
 *   - error：执行错误（终态，关闭流）
 *
 * 用法：
 * ```ts
 * const ctrl = createTaskSse(taskId, {
 *   onEvent: (e) => console.log(e.event, e.data),
 *   onTerminal: () => console.log('流结束'),
 *   onError: (err) => console.error(err),
 * })
 * // 关闭：ctrl.close()
 * ```
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import type { SseEvent, SseEventType } from './types'

/** 终态事件类型，收到后应关闭流 */
const TERMINAL_EVENTS: ReadonlySet<SseEventType> = new Set([
  'complete',
  'error',
])

/** SSE 订阅回调 */
export interface SseHandlers {
  /** 收到任意事件（终态事件也会先经过这里） */
  onEvent?: (event: SseEvent) => void
  /** 流正常结束（Python 端关闭）或收到终态事件后触发 */
  onTerminal?: (event: SseEvent | null) => void
  /** EventSource 发生错误（如网络中断、HTTP 非 200） */
  onError?: (error: Event) => void
  /** 连接已建立 */
  onOpen?: () => void
}

/** SSE 订阅控制器 */
export interface SseController {
  /** 主动关闭连接 */
  close: () => void
  /** 当前是否已关闭 */
  closed: () => boolean
}

/**
 * 创建任务 SSE 订阅
 *
 * @param taskId 任务 ID
 * @param handlers 事件回调
 * @returns 控制器（用于主动关闭）
 */
export function createTaskSse(
  taskId: string,
  handlers: SseHandlers = {},
): SseController {
  // 1. 构造 SSE 端点 URL（与 AxiosClient 同前缀 /api）
  const url = `/api/ai/sse/tasks/${encodeURIComponent(taskId)}`

  // 2. 创建 EventSource（不支持自定义 Header，依赖后端放行）
  const source = new EventSource(url, { withCredentials: false })

  let closed = false

  // 3. 连接建立
  source.onopen = () => {
    handlers.onOpen?.()
  }

  // 4. 监听所有已定义事件类型（EventSource 通过 event: 字段分发）
  const eventTypes: SseEventType[] = [
    'step_start',
    'step_end',
    'progress',
    'replan',
    'screenshot',
    'error',
    'complete',
  ]

  for (const type of eventTypes) {
    source.addEventListener(type, (raw: MessageEvent) => {
      // 4.1 解析 data（Python 端 json.dumps 后的字符串）
      let data: Record<string, unknown> = {}
      try {
        data = raw.data ? JSON.parse(raw.data) : {}
      } catch {
        // 解析失败时保留原始字符串作为 message
        data = { message: String(raw.data) }
      }

      const event: SseEvent = {
        event: type,
        data: data as never,
      }

      // 4.2 触发回调
      handlers.onEvent?.(event)

      // 4.3 终态事件 → 关闭流
      if (TERMINAL_EVENTS.has(type)) {
        handlers.onTerminal?.(event)
        cleanup()
      }
    })
  }

  // 5. 未知事件名 / 无事件名的消息（兜底，按 progress 处理）
  source.onmessage = (raw: MessageEvent) => {
    let data: Record<string, unknown> = {}
    try {
      data = raw.data ? JSON.parse(raw.data) : {}
    } catch {
      data = { message: String(raw.data) }
    }
    handlers.onEvent?.({ event: 'progress', data: data as never })
  }

  // 6. 错误处理（连接失败 / 服务端关闭 / 浏览器超时重连失败）
  source.onerror = (err) => {
    // EventSource 默认会自动重连；这里仅在已主动关闭或终态后不再回调
    if (closed) return
    handlers.onError?.(err)
    // 服务端关闭后 readyState 为 CLOSED，触发 onTerminal
    if (source.readyState === EventSource.CLOSED) {
      handlers.onTerminal?.(null)
      cleanup()
    }
  }

  /** 清理资源 */
  function cleanup() {
    if (closed) return
    closed = true
    source.close()
  }

  return {
    close: cleanup,
    closed: () => closed,
  }
}

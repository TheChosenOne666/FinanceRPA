/**
 * 性能测试 fixture 与辅助函数（M9.2）。
 *
 * 提供：
 * - test fixture：已认证的 ApiClient（默认登录 admin 账号）
 * - mockBankUrl：构建 mock 银行页 login_url（场景1 单任务延迟用）
 * - waitForTaskTerminal：轮询任务直到终态
 * - measureConcurrent：并发执行 N 个异步任务并采集耗时样本
 * - parseSseLatency：解析 SSE 事件中的 timestamp 字段计算延迟
 */
import { test as base, expect } from '@playwright/test';
import { ApiClient } from './api';
import { MOCK_BANK_BASE, PERF_PASSWORD, PERF_USERNAME, TASK_POLL_INTERVAL, TASK_WAIT_TIMEOUT } from './env';
import type { PerfSample, TaskDetailVO, TaskStatus } from './types';

/** 任务终态集合。 */
const TERMINAL_STATES: TaskStatus[] = ['SUCCESS', 'FAILED', 'NEEDS_HUMAN', 'ABORTED'];

// region test fixture

/**
 * 性能测试 test fixture：提供已登录的 ApiClient。
 *
 * 默认登录 admin 账号（银河证券 org_admin）。
 */
export const test = base.extend<{ api: ApiClient }>({
  api: async ({}, use) => {
    const client = new ApiClient();
    const resp = await client.login(PERF_USERNAME, PERF_PASSWORD);
    client.setToken(resp.accessToken);
    await use(client);
  },
});

export { expect };

// endregion

// region 辅助函数

/**
 * 构建 mock 银行页 login_url（供 Skyvern 容器内访问，场景1 用）。
 *
 * @param scenario 场景序号 1-6
 */
export function mockBankUrl(scenario: number): string {
  return `${MOCK_BANK_BASE}/scenario${scenario}.html`;
}

/**
 * 等待任务到达终态（SUCCESS / FAILED / NEEDS_HUMAN / ABORTED）。
 *
 * 轮询 GET /api/tasks/{taskId}，直到终态或超时。
 *
 * @param api 已认证的 ApiClient
 * @param taskId 任务 ID
 * @param timeoutMs 超时毫秒（默认 TASK_WAIT_TIMEOUT）
 * @returns 终态任务详情
 */
export async function waitForTaskTerminal(
  api: ApiClient,
  taskId: string,
  timeoutMs: number = TASK_WAIT_TIMEOUT,
): Promise<TaskDetailVO> {
  const start = Date.now();
  let lastStatus: TaskStatus = 'PENDING';
  while (Date.now() - start < timeoutMs) {
    const task = await api.getTask(taskId);
    lastStatus = task.status;
    if (TERMINAL_STATES.includes(task.status)) {
      return task;
    }
    await new Promise((r) => setTimeout(r, TASK_POLL_INTERVAL));
  }
  throw new Error(`任务 ${taskId} 等待终态超时（${timeoutMs}ms），最后状态：${lastStatus}`);
}

/**
 * 并发执行 N 个异步任务并采集耗时样本。
 *
 * 所有任务同时启动（Promise.allSettled），每个任务独立计时。
 * 失败的任务 success=false，但不影响其他任务。
 *
 * @param concurrency 并发数
 * @param taskFactory 任务工厂（接收序号 index，返回异步任务）
 * @returns 样本列表（按完成顺序）
 */
export async function measureConcurrent<T>(
  concurrency: number,
  taskFactory: (index: number) => Promise<T>,
): Promise<PerfSample[]> {
  const tasks: Promise<PerfSample>[] = [];
  for (let i = 0; i < concurrency; i++) {
    const index = i;
    const start = Date.now();
    tasks.push(
      taskFactory(index)
        .then((result) => ({
          index,
          durationMs: Date.now() - start,
          success: true,
          meta: { result },
        }))
        .catch((error: Error) => ({
          index,
          durationMs: Date.now() - start,
          success: false,
          errorMessage: error.message,
        })),
    );
  }
  return Promise.all(tasks);
}

/**
 * 解析 SSE 事件 data 中的 timestamp 字段，计算从事件产生到收到的延迟。
 *
 * Python 端 publish 时携带 ISO 8601 时间戳，Java 透传给前端。
 * 延迟 = 收到时间 - 事件产生时间。
 *
 * @param data SSE 事件 data 字段（JSON 字符串）
 * @param receivedAt 收到事件的时间戳（毫秒）
 * @returns 延迟（毫秒），无法解析返回 null
 */
export function parseSseLatency(data: string | undefined, receivedAt: number): number | null {
  if (!data) return null;
  try {
    const parsed = JSON.parse(data);
    // Python 在 data 中嵌入 timestamp 字段（ISO 8601）
    const ts = parsed.timestamp || parsed.ts || parsed.time;
    if (!ts) return null;
    const eventTime = new Date(ts).getTime();
    if (Number.isNaN(eventTime)) return null;
    return Math.max(0, receivedAt - eventTime);
  } catch {
    return null;
  }
}

/**
 * 生成不重复的伪任务 ID（场景2 模拟并发用，避免与真实任务 ID 冲突）。
 *
 * @param prefix 前缀（如 'mock'）
 * @param index 序号
 */
export function genMockTaskId(prefix: string, index: number): string {
  return `${prefix}-${Date.now()}-${index}`;
}

// endregion

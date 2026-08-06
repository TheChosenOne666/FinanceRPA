/**
 * 场景3 · SSE 推送延迟（M9.2）。
 *
 * 测量 SSE 事件从 Python 发布到 Java 透传再到前端收到的端到端延迟。
 *
 * 测试方式：
 * 1. 用 axios responseType:'stream' 连接 Java SSE 端点 GET /api/ai/sse/tasks/{taskId}
 * 2. 同时触发一个真实 Skyvern 任务（产生 SSE 事件流）
 * 3. 手动解析 SSE 流格式（event: xxx\ndata: xxx\n\n）
 * 4. 采集每个事件的 receivedAt 与 data.timestamp 差值，计算推送延迟
 *
 * 验收标准：SSE 推送延迟 P95 < 500ms。
 *
 * 注：使用 axios stream 代替 EventSource 库，因 EventSource 3.x 在 Node.js 24 中
 * 存在连接问题（readyState 一直为 0=CONNECTING，无法建立连接）。
 *
 * 前置条件：docker-compose 全栈已启动。
 */
import axios from 'axios';
import { BACKEND_URL, SSE_EVENT_TIMEOUT, TASK_WAIT_TIMEOUT } from '../lib/env';
import { computeStats, formatStats, saveStats } from '../lib/metrics';
import { expect, mockBankUrl, test, waitForTaskTerminal } from '../lib/fixtures';
import type { PerfSample, SseEventSample } from '../lib/types';

test.describe('场景3 · SSE 推送延迟', () => {
  test('真实任务 SSE 事件推送延迟测量', async ({ api }) => {
    // 1. 查找工作流模板
    const workflow = await api.findWorkflowByName('银行流水下载');
    console.log(`[SSE] 工作流模板: id=${workflow.workflowId}`);

    // 2. 触发任务
    const params = {
      login_url: mockBankUrl(1),
      login_username: 'testuser',
      login_password: 'testpass',
      account_number: '6228480012345678',
      date_start: '2026-07-01',
      date_end: '2026-07-31',
    };
    const run = await api.runWorkflow(workflow.workflowId, params);
    console.log(`[SSE] 任务已触发: taskId=${run.taskId} state=${run.state}`);

    // 3. 用 axios stream 订阅 Java SSE 端点（透传 Python SSE 流）
    const subscribeStart = Date.now();
    const sseUrl = `${BACKEND_URL}/api/ai/sse/tasks/${run.taskId}`;
    console.log(`[SSE] 订阅: ${sseUrl}`);

    const events: SseEventSample[] = [];
    let firstEventLatency: number | null = null;
    let buffer = ''; // SSE 流缓冲区

    // 启动 axios stream 请求（不 await，让它异步运行）
    const ssePromise = axios.get(sseUrl, {
      headers: { Accept: 'text/event-stream' },
      responseType: 'stream',
      timeout: TASK_WAIT_TIMEOUT + 10_000, // 超时比任务等待长 10 秒
    }).then((resp) => {
      console.log(`[SSE] 连接已建立: status=${resp.status} content-type=${resp.headers['content-type']}`);
      return new Promise<void>((resolve, reject) => {
        resp.data.on('data', (chunk: Buffer) => {
          buffer += chunk.toString('utf-8');
          // SSE 事件以 \n\n 分隔
          const parts = buffer.split('\n\n');
          buffer = parts.pop() ?? ''; // 最后一段可能不完整，保留在 buffer
          for (const part of parts) {
            if (!part.trim()) continue;
            const parsed = parseSseEvent(part);
            if (parsed) {
              const receivedAt = Date.now();
              const latencyFromStart = receivedAt - subscribeStart;
              if (firstEventLatency === null) {
                firstEventLatency = latencyFromStart;
                console.log(`[SSE] 首个事件: type=${parsed.event} 延迟=${firstEventLatency}ms`);
              }
              events.push({
                event: parsed.event,
                receivedAt,
                data: parsed.data,
                latencyFromStartMs: latencyFromStart,
                latencyMs: parseLatencyFromData(parsed.data, receivedAt),
              });
            }
          }
        });
        resp.data.on('end', () => {
          console.log('[SSE] 流结束');
          resolve();
        });
        resp.data.on('error', (err: Error) => {
          console.log(`[SSE] 流错误: ${err.message}`);
          reject(err);
        });
      });
    }).catch((err) => {
      console.log(`[SSE] 连接错误: ${err.message} code=${err.code}`);
    });

    // 4. 等待任务终态（SSE 流会在 complete/error 事件后自动关闭）
    try {
      const task = await waitForTaskTerminal(api, run.taskId, TASK_WAIT_TIMEOUT);
      console.log(`[SSE] 任务终态: ${task.status}`);
    } catch (e) {
      console.log(`[SSE] 任务等待超时或失败: ${(e as Error).message}`);
    }

    // 5. 等待 SSE 流结束（任务完成后 Java 会关闭 SSE 连接）
    await Promise.race([
      ssePromise,
      new Promise((r) => setTimeout(r, 5_000)), // 最多等 5 秒
    ]);

    // 6. 统计事件延迟
    console.log(`[SSE] 共收到 ${events.length} 个事件`);

    // 6.1 解析出可计算延迟的事件样本（data 中含 timestamp 的）
    const latencySamples: PerfSample[] = events
      .filter((e) => e.latencyMs !== undefined)
      .map((e, i) => ({
        index: i,
        durationMs: e.latencyMs!,
        success: true,
        meta: { event: e.event },
      }));

    // 6.2 事件间隔样本（相邻事件 receivedAt 差值）
    const intervalSamples: PerfSample[] = [];
    for (let i = 1; i < events.length; i++) {
      intervalSamples.push({
        index: i - 1,
        durationMs: events[i].receivedAt - events[i - 1].receivedAt,
        success: true,
        meta: { from: events[i - 1].event, to: events[i].event },
      });
    }

    // 7. 汇总
    console.log(`\n[SSE] 首个事件延迟: ${firstEventLatency ?? 'N/A'}ms`);
    if (latencySamples.length > 0) {
      const latencyStats = computeStats(latencySamples);
      console.log('\n' + formatStats('场景3-SSE推送延迟(timestamp差值)', latencyStats));
      saveStats('场景3-SSE推送延迟(timestamp差值)', latencyStats, 'perf-sse-latency.perf.json');
    } else {
      console.log('\n[SSE] 无可解析 timestamp 的事件，跳过延迟统计');
    }
    if (intervalSamples.length > 0) {
      const intervalStats = computeStats(intervalSamples);
      console.log('\n' + formatStats('场景3-SSE事件间隔', intervalStats));
    }

    // 8. 事件类型分布
    const typeDistribution: Record<string, number> = {};
    for (const e of events) {
      typeDistribution[e.event] = (typeDistribution[e.event] ?? 0) + 1;
    }
    console.log(`[SSE] 事件类型分布: ${JSON.stringify(typeDistribution)}`);

    // 9. 断言
    expect(events.length, '应至少收到 1 个 SSE 事件').toBeGreaterThan(0);
    if (firstEventLatency !== null) {
      expect(firstEventLatency as number, `首个事件延迟应 < ${SSE_EVENT_TIMEOUT}ms`).toBeLessThan(SSE_EVENT_TIMEOUT);
    }
    if (latencySamples.length > 0) {
      const latencyStats = computeStats(latencySamples);
      // 验收标准：SSE 推送延迟 P95 < 500ms
      expect(latencyStats.p95Ms, `SSE 推送延迟 P95 应 < 500ms（实际 ${latencyStats.p95Ms}ms）`).toBeLessThan(500);
    }

    console.log(`\n[SSE] 测试完成 ✓ 共 ${events.length} 个事件，首个延迟 ${firstEventLatency ?? 'N/A'}ms`);
  });
});

/**
 * 解析 SSE 事件块（event: xxx\ndata: xxx 格式）。
 *
 * @param block SSE 事件块字符串
 * @returns { event, data } 或 null（解析失败时）
 */
function parseSseEvent(block: string): { event: string; data?: string } | null {
  const lines = block.split('\n');
  let event = 'message';
  let data: string | undefined;
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      data = line.slice(5).trim();
    }
  }
  return { event, data };
}

/**
 * 从 SSE 事件 data 中解析 timestamp，计算推送延迟。
 *
 * Python 在 data 中嵌入 timestamp 字段（ISO 8601），延迟 = 收到时间 - 事件产生时间。
 *
 * @param data SSE 事件 data 字符串
 * @param receivedAt 收到事件的时间戳（毫秒）
 * @returns 延迟（毫秒），无法解析返回 undefined
 */
function parseLatencyFromData(data: string | undefined, receivedAt: number): number | undefined {
  if (!data) return undefined;
  try {
    const parsed = JSON.parse(data);
    const ts = parsed.timestamp || parsed.ts || parsed.time;
    if (!ts) return undefined;
    const eventTime = new Date(ts).getTime();
    if (Number.isNaN(eventTime)) return undefined;
    return Math.max(0, receivedAt - eventTime);
  } catch {
    return undefined;
  }
}

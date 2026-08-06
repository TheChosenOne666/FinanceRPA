/**
 * 性能指标统计工具（M9.2）。
 *
 * 提供：
 * - computeStats：从样本列表计算 P50/P95/P99/avg/min/max/throughput
 * - formatStats：格式化为可读字符串（控制台输出 + 报告）
 * - saveStats：保存为 JSON 文件（供报告引用）
 */
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import type { PerfSample, PerfStats } from './types';

/**
 * 从样本列表计算性能统计。
 *
 * @param samples 样本列表
 * @param totalMs 总耗时（毫秒，并发场景由调用方传入；单任务可传 undefined 自动取样本耗时和）
 * @returns 性能统计结果
 */
export function computeStats(samples: PerfSample[], totalMs?: number): PerfStats {
  const total = samples.length;
  const success = samples.filter((s) => s.success).length;
  const failed = total - success;

  const durations = samples
    .filter((s) => s.success)
    .map((s) => s.durationMs)
    .sort((a, b) => a - b);

  if (durations.length === 0) {
    return {
      total,
      success,
      failed,
      successRate: 0,
      minMs: 0,
      maxMs: 0,
      avgMs: 0,
      p50Ms: 0,
      p95Ms: 0,
      p99Ms: 0,
      totalMs: totalMs ?? 0,
    };
  }

  const sum = durations.reduce((acc, n) => acc + n, 0);
  const sumMs = totalMs ?? sum;

  return {
    total,
    success,
    failed,
    successRate: total > 0 ? success / total : 0,
    minMs: durations[0],
    maxMs: durations[durations.length - 1],
    avgMs: Math.round(sum / durations.length),
    p50Ms: percentile(durations, 50),
    p95Ms: percentile(durations, 95),
    p99Ms: percentile(durations, 99),
    totalMs: sumMs,
    throughputPerSec: sumMs > 0 ? (success / sumMs) * 1000 : undefined,
  };
}

/**
 * 计算分位数（nearest-rank 方法）。
 *
 * @param sortedAsc 已升序排序的耗时数组
 * @param p 百分位（0-100）
 * @returns 分位数值
 */
function percentile(sortedAsc: number[], p: number): number {
  if (sortedAsc.length === 0) return 0;
  if (sortedAsc.length === 1) return sortedAsc[0];
  // nearest-rank：index = ceil(p/100 * N) - 1
  const idx = Math.ceil((p / 100) * sortedAsc.length) - 1;
  return sortedAsc[Math.max(0, Math.min(sortedAsc.length - 1, idx))];
}

/**
 * 格式化性能统计为可读字符串。
 *
 * @param title 标题
 * @param stats 性能统计
 * @returns 多行字符串
 */
export function formatStats(title: string, stats: PerfStats): string {
  const lines = [
    `=== ${title} ===`,
    `  样本数: ${stats.total}（成功 ${stats.success} / 失败 ${stats.failed}）`,
    `  成功率: ${(stats.successRate * 100).toFixed(2)}%`,
    `  耗时(ms): min=${stats.minMs}  avg=${stats.avgMs}  max=${stats.maxMs}`,
    `  分位(ms): P50=${stats.p50Ms}  P95=${stats.p95Ms}  P99=${stats.p99Ms}`,
    `  总耗时: ${stats.totalMs}ms`,
  ];
  if (stats.throughputPerSec !== undefined) {
    lines.push(`  吞吐量: ${stats.throughputPerSec.toFixed(2)} req/s`);
  }
  return lines.join('\n');
}

/**
 * 保存性能统计为 JSON 文件（供报告引用）。
 *
 * @param title 标题
 * @param stats 性能统计
 * @param filename 输出文件名（不含路径，保存到套件根目录）
 */
export function saveStats(title: string, stats: PerfStats, filename: string): void {
  const outputPath = join(process.cwd(), filename);
  const payload = {
    title,
    timestamp: new Date().toISOString(),
    ...stats,
  };
  writeFileSync(outputPath, JSON.stringify(payload, null, 2), 'utf-8');
  console.log(`[saveStats] 性能统计已保存: ${outputPath}`);
}

/**
 * 测量异步函数执行耗时（毫秒）。
 *
 * @param fn 异步函数
 * @returns { durationMs, result, error }
 */
export async function measure<T>(
  fn: () => Promise<T>,
): Promise<{ durationMs: number; result?: T; error?: Error }> {
  const start = Date.now();
  try {
    const result = await fn();
    return { durationMs: Date.now() - start, result };
  } catch (e) {
    return { durationMs: Date.now() - start, error: e as Error };
  }
}

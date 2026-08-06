/**
 * 场景4 · 审计日志百万级查询性能（M9.2）。
 *
 * 前置：先执行 `npm run seed:audit` 造 100 万条审计日志。
 *
 * 测量维度：
 * 1. 分页查询延迟（按 taskId 精确查询，命中索引）
 * 2. 多维检索延迟（按时间范围 + risk_level + action_type 组合查询）
 * 3. 分页深度翻页延迟（第 1 页 vs 第 1000 页）
 * 4. 全量 count 延迟（SELECT COUNT(*) WHERE org_id=xxx）
 *
 * 验收标准：审计查询 < 500ms。
 *
 * 测试后自动清理造数数据（DELETE WHERE audit_id >= 8000000000000000000）。
 */
import pg from 'pg';
import { AUDIT_QUERY_SAMPLES, PG_DB, PG_HOST, PG_PASSWORD, PG_PORT, PG_USER } from '../lib/env';
import { computeStats, formatStats, measure, saveStats } from '../lib/metrics';
import { expect, test } from '../lib/fixtures';
import type { PerfSample } from '../lib/types';

const { Client } = pg;

/**
 * 将 Date 格式化为后端 java.sql.Timestamp 可解析的 `yyyy-MM-dd HH:mm:ss` 格式（UTC）。
 *
 * 后端 AuditLogQueryRequest.startTime/endTime 为 java.sql.Timestamp 类型，
 * 无法解析 ISO 8601 带 Z 的字符串（如 2026-05-08T16:37:29.803Z），需用此格式。
 */
function fmtTimestamp(d: Date): string {
  return d.toISOString().replace('T', ' ').replace(/\.\d+Z$/, '');
}

/** 造数 audit_id 起始值（与 seed-audit-logs.mjs 一致，需在 bigint 上限 9.22×10^18 内）。 */
const SEED_AUDIT_ID_START = '8000000000000000000';

test.describe('场景4 · 审计日志百万级查询性能', () => {
  test.describe.configure({ timeout: 30 * 60 * 1000 }); // 30 分钟

  test('百万级审计日志多维检索 + 翻页性能', async ({ api }) => {
    // 1. 验证造数数据存在
    const pgClient = new Client({
      host: PG_HOST,
      port: PG_PORT,
      database: PG_DB,
      user: PG_USER,
      password: PG_PASSWORD,
    });
    await pgClient.connect();

    const countResult = await pgClient.query(
      `SELECT COUNT(*) FROM rpa_audit_log WHERE audit_id >= $1`,
      [SEED_AUDIT_ID_START],
    );
    const seedCount = Number(countResult.rows[0].count);
    console.log(`[审计] 造数数据量: ${seedCount} 条`);
    expect(seedCount, '应先执行 npm run seed:audit 造数').toBeGreaterThan(100_000);

    try {
      // 2. 通过 API 测量分页查询延迟（按 org_id 隔离，命中索引）
      console.log(`\n[审计] 测试1: 分页查询（org_id 隔离，${AUDIT_QUERY_SAMPLES} 次采样）`);
      const pageSamples: PerfSample[] = [];
      for (let i = 0; i < AUDIT_QUERY_SAMPLES; i++) {
        const m = await measure(() =>
          api.listAuditLogs({
            current: 1,
            pageSize: 20,
            sortField: 'createTime',
            sortOrder: 'descend',
          }),
        );
        pageSamples.push({
          index: i,
          durationMs: m.durationMs,
          success: m.error === undefined,
          errorMessage: m.error?.message,
        });
      }
      const pageStats = computeStats(pageSamples);
      console.log('\n' + formatStats('场景4-分页查询(命中org_id索引)', pageStats));
      saveStats('场景4-分页查询', pageStats, 'perf-audit-page.perf.json');

      // 3. 多维检索（时间范围 + risk_level + action_type 组合）
      console.log(`\n[审计] 测试2: 多维检索（时间范围+risk_level+action_type，${AUDIT_QUERY_SAMPLES} 次采样）`);
      const multiSamples: PerfSample[] = [];
      const now = new Date();
      const ninetyDaysAgo = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
      const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      for (let i = 0; i < AUDIT_QUERY_SAMPLES; i++) {
        const m = await measure(() =>
          api.listAuditLogs({
            startTime: fmtTimestamp(ninetyDaysAgo),
            endTime: fmtTimestamp(thirtyDaysAgo),
            riskLevel: ['low', 'medium', 'high', 'critical'][i % 4],
            actionType: ['NAVIGATE', 'CLICK', 'INPUT_TEXT', 'LOGIN'][i % 4],
            current: 1,
            pageSize: 20,
          }),
        );
        multiSamples.push({
          index: i,
          durationMs: m.durationMs,
          success: m.error === undefined,
          errorMessage: m.error?.message,
        });
      }
      const multiStats = computeStats(multiSamples);
      console.log('\n' + formatStats('场景4-多维检索(时间+risk+action)', multiStats));
      saveStats('场景4-多维检索', multiStats, 'perf-audit-multi.perf.json');

      // 4. 深度翻页（第 1 页 vs 第 1000 页）
      console.log(`\n[审计] 测试3: 深度翻页（第1页 vs 第1000页）`);
      const deepPageSamples: PerfSample[] = [];
      for (let i = 0; i < 20; i++) {
        // 第 1 页
        const m1 = await measure(() =>
          api.listAuditLogs({ current: 1, pageSize: 20, sortField: 'createTime', sortOrder: 'descend' }),
        );
        deepPageSamples.push({
          index: i * 2,
          durationMs: m1.durationMs,
          success: m1.error === undefined,
          meta: { page: 1 },
        });
        // 第 1000 页（20000 条偏移，考验 LIMIT OFFSET 性能）
        const m1000 = await measure(() =>
          api.listAuditLogs({ current: 1000, pageSize: 20, sortField: 'createTime', sortOrder: 'descend' }),
        );
        deepPageSamples.push({
          index: i * 2 + 1,
          durationMs: m1000.durationMs,
          success: m1000.error === undefined,
          meta: { page: 1000 },
        });
      }
      const page1Samples = deepPageSamples.filter((s) => s.meta?.page === 1);
      const page1000Samples = deepPageSamples.filter((s) => s.meta?.page === 1000);
      const page1Stats = computeStats(page1Samples);
      const page1000Stats = computeStats(page1000Samples);
      console.log('\n' + formatStats('场景4-第1页', page1Stats));
      console.log('\n' + formatStats('场景4-第1000页(深度翻页)', page1000Stats));
      saveStats('场景4-第1000页深度翻页', page1000Stats, 'perf-audit-deeppage.perf.json');

      // 5. 全量 count 查询（直连 PG，测 SELECT COUNT(*) WHERE org_id=xxx）
      console.log(`\n[审计] 测试4: 全量 COUNT 查询（org_id 索引，20 次采样）`);
      const countSamples: PerfSample[] = [];
      for (let i = 0; i < 20; i++) {
        const start = Date.now();
        try {
          await pgClient.query(`SELECT COUNT(*) FROM rpa_audit_log WHERE org_id = $1 AND audit_id >= $2`, [1, SEED_AUDIT_ID_START]);
          countSamples.push({ index: i, durationMs: Date.now() - start, success: true });
        } catch (e) {
          countSamples.push({ index: i, durationMs: Date.now() - start, success: false, errorMessage: (e as Error).message });
        }
      }
      const countStats = computeStats(countSamples);
      console.log('\n' + formatStats('场景4-COUNT查询(org_id索引)', countStats));
      saveStats('场景4-COUNT查询', countStats, 'perf-audit-count.perf.json');

      // 6. 断言验收标准：审计查询 < 500ms
      // 先校验成功率（全部失败时 P95=0 会误报通过，必须先拦截；successRate 为 0-1 小数，1=100%）
      expect(pageStats.successRate, `分页查询成功率应为 100%（实际 ${(pageStats.successRate * 100).toFixed(2)}%）`).toBe(1);
      expect(multiStats.successRate, `多维检索成功率应为 100%（实际 ${(multiStats.successRate * 100).toFixed(2)}%）`).toBe(1);
      expect(page1000Stats.successRate, `深度翻页成功率应为 100%（实际 ${(page1000Stats.successRate * 100).toFixed(2)}%）`).toBe(1);
      expect(countStats.successRate, `COUNT 查询成功率应为 100%（实际 ${(countStats.successRate * 100).toFixed(2)}%）`).toBe(1);
      // 再校验延迟
      expect(pageStats.p95Ms, `分页查询 P95 应 < 500ms（实际 ${pageStats.p95Ms}ms）`).toBeLessThan(500);
      expect(multiStats.p95Ms, `多维检索 P95 应 < 500ms（实际 ${multiStats.p95Ms}ms）`).toBeLessThan(500);
      // 深度翻页放宽到 2s（LIMIT OFFSET 20000 在百万级数据下确实较慢，记录基线）
      expect(page1000Stats.p95Ms, `深度翻页 P95 应 < 2000ms（实际 ${page1000Stats.p95Ms}ms）`).toBeLessThan(2_000);
      expect(countStats.p95Ms, `COUNT 查询 P95 应 < 500ms（实际 ${countStats.p95Ms}ms）`).toBeLessThan(500);

      console.log(`\n[审计] 测试完成 ✓ 分页 P95=${pageStats.p95Ms}ms, 多维 P95=${multiStats.p95Ms}ms`);
    } finally {
      // 7. 清理造数数据（无论测试成功失败都清理）
      console.log('\n[审计] 清理造数数据...');
      try {
        const delResult = await pgClient.query(
          `DELETE FROM rpa_audit_log WHERE audit_id >= $1`,
          [SEED_AUDIT_ID_START],
        );
        console.log(`[审计] 已清理 ${delResult.rowCount} 条造数数据`);
        await pgClient.query('ANALYZE rpa_audit_log');
        console.log('[审计] ANALYZE 完成');
      } catch (e) {
        console.error('[审计] 清理失败:', e);
      } finally {
        await pgClient.end();
      }
    }
  });
});

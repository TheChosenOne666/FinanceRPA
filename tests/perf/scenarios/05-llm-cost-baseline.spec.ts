/**
 * 场景5 · LLM 调用成本基线（M9.2）。
 *
 * 触发 N 次（默认 5 次）真实 LLM 风险判断（POST Python /api/v1/ai/risk/judge），
 * 从 rpa_llm_call_log 表统计 prompt_tokens / completion_tokens / cost，
 * 建立单次调用成本基线。
 *
 * 测量维度：
 * 1. 单次 LLM 调用延迟（P50/P95/P99）
 * 2. 单次调用 token 用量（prompt + completion + total）
 * 3. 单次调用成本（美元）
 * 4. 风险判断准确率（final_risk_level 是否符合预期）
 *
 * 前置条件：
 * - docker-compose 全栈已启动
 * - .env 已配置 VOLCENGINE_API_KEY（火山方舟豆包）
 *
 * 注：真实调用会产生费用，预算约 1-2 元（5 次 × doubao-seed 单次约 0.2 元）。
 */
import { LLM_CALL_TIMEOUT, LLM_SAMPLE_SIZE } from '../lib/env';
import { computeStats, formatStats, measure, saveStats } from '../lib/metrics';
import { expect, test } from '../lib/fixtures';
import type { LlmCallRecordVO, PerfSample, RiskJudgeRequest } from '../lib/types';

test.describe('场景5 · LLM 调用成本基线', () => {
  test('真实火山方舟豆包风险判断 - 成本与延迟基线', async ({ api }) => {
    const sampleSize = LLM_SAMPLE_SIZE;
    console.log(`[LLM] 启动 ${sampleSize} 次真实 LLM 风险判断...`);
    console.log(`[LLM] 注: 真实调用火山方舟豆包，会产生 tokens 费用`);

    // 1. 构造 N 个不同的风险判断请求（模拟不同金融场景）
    const testCases: RiskJudgeRequest[] = [
      {
        task_id: `perf-llm-${Date.now()}-1`,
        org_id: '1',
        industry: 'banking',
        goal: '下载客户 6228480012345678 的银行流水',
        params: { account_number: '6228480012345678', date_range: '2026-07' },
        pre_screen_risk_level: 'medium',
        hit_keywords: ['下载', '流水'],
        max_amount: '0',
      },
      {
        task_id: `perf-llm-${Date.now()}-2`,
        org_id: '1',
        industry: 'securities',
        goal: '执行 1000 万市价单买入 600000',
        params: { stock_code: '600000', amount: '10000000', order_type: 'market' },
        pre_screen_risk_level: 'high',
        hit_keywords: ['市价单', '1000万', '买入'],
        max_amount: '10000000',
      },
      {
        task_id: `perf-llm-${Date.now()}-3`,
        org_id: '1',
        industry: 'insurance',
        goal: '审核理赔申请单号 CL202607001，金额 5 万元',
        params: { claim_id: 'CL202607001', amount: '50000' },
        pre_screen_risk_level: 'medium',
        hit_keywords: ['理赔', '5万'],
        max_amount: '50000',
      },
      {
        task_id: `perf-llm-${Date.now()}-4`,
        org_id: '1',
        industry: 'banking',
        goal: '查询账户余额',
        params: { account_number: '6228480012345678' },
        pre_screen_risk_level: 'low',
        hit_keywords: ['查询', '余额'],
        max_amount: '0',
      },
      {
        task_id: `perf-llm-${Date.now()}-5`,
        org_id: '1',
        industry: 'banking',
        goal: '跨行转账 500 万至外部账户 6222001234567890',
        params: { from_account: '6228480012345678', to_account: '6222001234567890', amount: '5000000' },
        pre_screen_risk_level: 'critical',
        hit_keywords: ['跨行转账', '500万', '外部账户'],
        max_amount: '5000000',
      },
    ];

    // 2. 执行 N 次真实 LLM 调用，采集延迟样本
    const samples: PerfSample[] = [];
    const callResults: { taskId: string; riskLevel: string; latencyMs?: number }[] = [];

    for (let i = 0; i < sampleSize; i++) {
      const request = testCases[i % testCases.length];
      console.log(`\n[LLM] 第 ${i + 1}/${sampleSize} 次调用: goal="${request.goal.slice(0, 30)}..."`);

      const m = await measure(() =>
        Promise.race([
          api.judgeRisk(request),
          new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error(`LLM 调用超时（${LLM_CALL_TIMEOUT}ms）`)), LLM_CALL_TIMEOUT),
          ),
        ]),
      );

      if (m.error) {
        console.log(`[LLM] 第 ${i + 1} 次失败: ${m.error.message} (${m.durationMs}ms)`);
        samples.push({
          index: i,
          durationMs: m.durationMs,
          success: false,
          errorMessage: m.error.message,
          meta: { goal: request.goal },
        });
      } else {
        const resp = m.result!;
        console.log(`[LLM] 第 ${i + 1} 次成功: risk=${resp.final_risk_level} route=${resp.approval_route} latency=${resp.latency_ms ?? m.durationMs}ms`);
        samples.push({
          index: i,
          durationMs: resp.latency_ms ?? m.durationMs,
          success: true,
          meta: {
            goal: request.goal,
            finalRiskLevel: resp.final_risk_level,
            approvalRoute: resp.approval_route,
            modelUsed: resp.model_used,
          },
        });
        callResults.push({
          taskId: request.task_id,
          riskLevel: resp.final_risk_level,
          latencyMs: resp.latency_ms,
        });
      }

      // 间隔 1 秒，避免触发限流
      if (i < sampleSize - 1) {
        await new Promise((r) => setTimeout(r, 1_000));
      }
    }

    // 3. 统计调用延迟
    const latencyStats = computeStats(samples);
    console.log('\n' + formatStats('场景5-LLM调用延迟', latencyStats));
    saveStats('场景5-LLM调用延迟', latencyStats, 'perf-llm-latency.perf.json');

    // 4. 从 rpa_llm_call_log 表查询本次调用的 token 与成本
    console.log('\n[LLM] 查询 rpa_llm_call_log 表统计 token 与成本...');
    const taskIds = callResults.map((r) => r.taskId);
    let llmRecords: LlmCallRecordVO[] = [];
    if (taskIds.length > 0) {
      // 逐个 taskId 查询（API 不支持批量 taskId 查询）
      for (const taskId of taskIds) {
        try {
          const resp = await api.listLlmCallRecords({ taskId, current: 1, pageSize: 10 });
          llmRecords.push(...resp.records);
        } catch (e) {
          console.log(`[LLM] 查询 taskId=${taskId} 调用记录失败: ${(e as Error).message}`);
        }
      }
    }

    // 5. 汇总 token 与成本
    console.log(`\n[LLM] 获取到 ${llmRecords.length} 条调用记录`);
    if (llmRecords.length > 0) {
      const totalPromptTokens = llmRecords.reduce((s, r) => s + (r.promptTokens ?? 0), 0);
      const totalCompletionTokens = llmRecords.reduce((s, r) => s + (r.completionTokens ?? 0), 0);
      const totalTokens = llmRecords.reduce((s, r) => s + (r.totalTokens ?? 0), 0);
      const totalCost = llmRecords.reduce((s, r) => s + (r.cost ?? 0), 0);
      const avgPromptTokens = Math.round(totalPromptTokens / llmRecords.length);
      const avgCompletionTokens = Math.round(totalCompletionTokens / llmRecords.length);
      const avgTotalTokens = Math.round(totalTokens / llmRecords.length);
      const avgCost = totalCost / llmRecords.length;

      console.log('\n=== 场景5 LLM 成本基线 ===');
      console.log(`  样本数: ${llmRecords.length}`);
      console.log(`  Token 用量(单次平均): prompt=${avgPromptTokens}  completion=${avgCompletionTokens}  total=${avgTotalTokens}`);
      console.log(`  Token 用量(总计): prompt=${totalPromptTokens}  completion=${totalCompletionTokens}  total=${totalTokens}`);
      console.log(`  成本(单次平均): $${avgCost.toFixed(6)}`);
      console.log(`  成本(总计): $${totalCost.toFixed(6)}`);
      console.log(`  模型: ${llmRecords[0]?.model ?? 'N/A'}`);
      console.log(`  缓存命中率: ${(llmRecords.filter((r) => r.cacheHit).length / llmRecords.length * 100).toFixed(1)}%`);

      // 保存成本统计
      saveStats('场景5-LLM成本基线', {
        total: llmRecords.length,
        success: llmRecords.filter((r) => r.success).length,
        failed: llmRecords.filter((r) => !r.success).length,
        successRate: llmRecords.filter((r) => r.success).length / llmRecords.length,
        minMs: Math.min(...llmRecords.map((r) => r.durationMs)),
        maxMs: Math.max(...llmRecords.map((r) => r.durationMs)),
        avgMs: Math.round(llmRecords.reduce((s, r) => s + r.durationMs, 0) / llmRecords.length),
        p50Ms: 0,
        p95Ms: 0,
        p99Ms: 0,
        totalMs: 0,
      }, 'perf-llm-cost.perf.json');
    }

    // 6. 风险判断准确率（final_risk_level 是否符合预期）
    const expectedRiskLevels = testCases.map((t) => t.pre_screen_risk_level);
    const actualRiskLevels = callResults.map((r) => r.riskLevel);
    let matchCount = 0;
    for (let i = 0; i < actualRiskLevels.length; i++) {
      const expected = expectedRiskLevels[i % expectedRiskLevels.length];
      const actual = actualRiskLevels[i];
      if (expected === actual) matchCount++;
      console.log(`[LLM] 风险判断: 期望=${expected} 实际=${actual} ${expected === actual ? '✓' : '✗'}`);
    }
    const accuracy = actualRiskLevels.length > 0 ? matchCount / actualRiskLevels.length : 0;
    console.log(`\n[LLM] 风险判断准确率: ${(accuracy * 100).toFixed(1)}%（${matchCount}/${actualRiskLevels.length}）`);

    // 7. 断言
    expect(samples.length, '应完成指定次数的采样').toBe(sampleSize);
    const successCount = samples.filter((s) => s.success).length;
    expect(successCount, `至少应成功 1 次 LLM 调用`).toBeGreaterThanOrEqual(1);
    if (latencyStats.p95Ms > 0) {
      // LLM 调用延迟通常 1-10 秒，P95 放宽到 30 秒
      expect(latencyStats.p95Ms, `LLM 调用 P95 应 < 30s（实际 ${latencyStats.p95Ms}ms）`).toBeLessThan(30_000);
    }

    console.log(`\n[LLM] 测试完成 ✓ 成功 ${successCount}/${sampleSize}，平均延迟 ${latencyStats.avgMs}ms`);
  });
});

/**
 * 审计日志百万级造数脚本（M9.2 场景4 前置）。
 *
 * 直连 PostgreSQL，用 generate_series 批量 INSERT 100 万条审计日志。
 * 数据分布：
 * - 6 个 org_id（模拟 6 个组织）
 * - 4 个 risk_level（low/medium/high/critical）
 * - 10 个 action_type（NAVIGATE/CLICK/INPUT_TEXT/LOGIN/EXTRACT/SCREENSHOT/SCROLL/WAIT/LOGOUT/SUBMIT）
 * - 时间范围：过去 90 天（按时间分布，便于测试时间范围查询）
 *
 * 用法：
 *   node scripts/seed-audit-logs.mjs              # 造 100 万条
 *   AUDIT_SEED_COUNT=500000 node scripts/seed-audit-logs.mjs  # 造 50 万条
 *
 * 清理（测试后自动执行）：
 *   DELETE FROM rpa_audit_log WHERE audit_id >= 9900000000000000000;
 *
 * 注：造数用 audit_id >= 9900000000000000000 的范围，避免与雪花算法生成的真实 ID 冲突。
 */
import pg from 'pg';

const { Client } = pg;

// region 配置
const PG_HOST = process.env.PG_HOST || 'localhost';
const PG_PORT = Number(process.env.PG_PORT || 5432);
const PG_DB = process.env.PG_DB || 'finrpa';
const PG_USER = process.env.PG_USER || 'finrpa';
const PG_PASSWORD = process.env.PG_PASSWORD || 'finrpa';

const SEED_COUNT = Number(process.env.AUDIT_SEED_COUNT || 1_000_000);
const BATCH_SIZE = Number(process.env.AUDIT_SEED_BATCH_SIZE || 10_000);

// 造数 audit_id 起始值（避开雪花算法 ID 范围）
const SEED_AUDIT_ID_START = 9_900_000_000_000_000_000n;

// 数据分布
const ORG_IDS = [1, 2, 3, 4, 5, 6];
const RISK_LEVELS = ['low', 'medium', 'high', 'critical'];
const ACTION_TYPES = ['NAVIGATE', 'CLICK', 'INPUT_TEXT', 'LOGIN', 'EXTRACT', 'SCREENSHOT', 'SCROLL', 'WAIT', 'LOGOUT', 'SUBMIT'];
const EXECUTION_RESULTS = ['success', 'success', 'success', 'success', 'failed']; // 80% 成功
// endregion

async function main() {
  console.log(`[造数] 开始造 ${SEED_COUNT} 条审计日志...`);
  console.log(`[造数] PG: ${PG_USER}@${PG_HOST}:${PG_PORT}/${PG_DB}`);
  console.log(`[造数] 批次大小: ${BATCH_SIZE}`);

  const client = new Client({
    host: PG_HOST,
    port: PG_PORT,
    database: PG_DB,
    user: PG_USER,
    password: PG_PASSWORD,
  });

  await client.connect();
  console.log('[造数] PG 连接成功');

  try {
    // 1. 清理旧的造数数据（避免重复执行堆积）
    console.log('[造数] 清理旧造数数据...');
    const deleteResult = await client.query(
      `DELETE FROM rpa_audit_log WHERE audit_id >= $1`,
      [SEED_AUDIT_ID_START.toString()],
    );
    console.log(`[造数] 已清理 ${deleteResult.rowCount} 条旧数据`);

    // 2. 分批插入
    const startTotal = Date.now();
    let inserted = 0;
    const batches = Math.ceil(SEED_COUNT / BATCH_SIZE);

    for (let batch = 0; batch < batches; batch++) {
      const batchStart = Date.now();
      const batchCount = Math.min(BATCH_SIZE, SEED_COUNT - inserted);
      const batchIdStart = SEED_AUDIT_ID_START + BigInt(batch * BATCH_SIZE);

      // 用 generate_series 批量生成，避免 Node 端拼 SQL
      // 数据分布用随机函数（PG 内置 random()）
      const sql = `
        INSERT INTO rpa_audit_log (
          audit_id, task_id, org_id, user_id, department_id, business_line_id,
          action_type, target_element, page_url, action_params,
          execution_result, error_message, risk_level,
          started_at, completed_at, duration_ms, create_time
        )
        SELECT
          $1::bigint + gs,
          9_000_000_000_000_000_000::bigint + (random() * 1_000_000)::bigint,
          $2::int,
          (random() * 1000)::bigint + 1,
          (random() * 100)::bigint + 1,
          (random() * 50)::bigint + 1,
          $3[array_length($3::text[], 1) * random() + 1]::text,
          'target-' || (gs % 1000),
          'https://example.com/page-' || (gs % 100),
          '{"key":"value_' || gs || '"}',
          $4[array_length($4::text[], 1) * random() + 1]::text,
          CASE WHEN random() < 0.2 THEN '模拟错误信息' ELSE NULL END,
          $5[array_length($5::text[], 1) * random() + 1]::text,
          NOW() - (random() * 90)::int * INTERVAL '1 day',
          NOW() - (random() * 90)::int * INTERVAL '1 day' + INTERVAL '1 second',
          (random() * 5000)::int,
          NOW() - (random() * 90)::int * INTERVAL '1 day'
        FROM generate_series(0, $6::int - 1) AS gs
      `;

      await client.query(sql, [
        batchIdStart.toString(),
        ORG_IDS[batch % ORG_IDS.length],
        ACTION_TYPES,
        EXECUTION_RESULTS,
        RISK_LEVELS,
        batchCount,
      ]);

      inserted += batchCount;
      const batchMs = Date.now() - batchStart;
      const totalMs = Date.now() - startTotal;
      const rate = (inserted / (totalMs / 1000)).toFixed(0);
      console.log(`[造数] 批次 ${batch + 1}/${batches}: +${batchCount} 条 (${batchMs}ms, 累计 ${inserted}, ${rate} rows/s)`);
    }

    const totalMs = Date.now() - startTotal;
    console.log(`\n[造数] 完成 ✓ 共 ${inserted} 条，耗时 ${(totalMs / 1000).toFixed(1)}s，平均 ${(inserted / (totalMs / 1000)).toFixed(0)} rows/s`);

    // 3. 验证数据分布
    const countResult = await client.query(`SELECT COUNT(*) FROM rpa_audit_log WHERE audit_id >= $1`, [SEED_AUDIT_ID_START.toString()]);
    console.log(`[造数] 验证: 表中造数数据 ${countResult.rows[0].count} 条`);

    const distResult = await client.query(`
      SELECT org_id, risk_level, COUNT(*) as cnt
      FROM rpa_audit_log
      WHERE audit_id >= $1
      GROUP BY org_id, risk_level
      ORDER BY org_id, risk_level
    `, [SEED_AUDIT_ID_START.toString()]);
    console.log('[造数] 数据分布（org_id × risk_level）:');
    for (const row of distResult.rows) {
      console.log(`  org=${row.org_id} risk=${row.risk_level}: ${row.cnt} 条`);
    }

    // 4. ANALYZE 更新统计信息（让查询优化器基于新数据生成执行计划）
    console.log('[造数] 执行 ANALYZE rpa_audit_log...');
    await client.query('ANALYZE rpa_audit_log');
    console.log('[造数] ANALYZE 完成');
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error('[造数] 失败:', err);
  process.exit(1);
});

package com.finrpa.dashboard.mapper;

import com.finrpa.dashboard.dto.response.ApprovalStatVO;
import com.finrpa.dashboard.dto.response.BusinessLineStatVO;
import com.finrpa.dashboard.dto.response.CostStatVO;
import com.finrpa.dashboard.dto.response.ErrorTypeStatVO;
import com.finrpa.dashboard.dto.response.RiskLevelStatVO;
import com.finrpa.dashboard.dto.stats.HumanTakeoverAggregateDTO;
import com.finrpa.dashboard.dto.stats.LlmAggregateDTO;
import com.finrpa.dashboard.dto.stats.TaskDurationStatDTO;
import com.finrpa.dashboard.dto.stats.TaskStatusCountDTO;
import com.finrpa.dashboard.dto.stats.TrendCostDTO;
import com.finrpa.dashboard.dto.stats.TrendTaskDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 运营大屏统计聚合 Mapper
 *
 * <p>所有查询涉及的 rpa_* 表均在 {@link com.finrpa.tenant.constant.TenantConstant#IGNORED_TABLES} 中，
 * 租户插件不会自动追加 org_id 条件，故此处手动 {@code WHERE org_id = #{orgId}} 过滤。
 * {@code enterprise_business_line} 由租户插件自动追加 org_id，手动重复追加不影响结果。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface DashboardStatsMapper {

    // region 概览（overview）

    /**
     * 按任务状态分组计数
     *
     * @param orgId 组织 ID
     * @return 状态-计数列表
     */
    @Select("""
            SELECT status, COUNT(*) AS count
            FROM rpa_agent_task
            WHERE org_id = #{orgId} AND deleted = 0
            GROUP BY status
            """)
    List<TaskStatusCountDTO> countTaskByStatus(@Param("orgId") Long orgId);

    /**
     * 已终态任务执行时长统计（平均 / P95）
     *
     * <p>基于 update_time - create_time 近似执行时长，仅统计 SUCCESS / FAILED / ABORTED 终态任务。</p>
     *
     * @param orgId 组织 ID
     * @return 时长统计；无终态任务时返回 null
     */
    @Select("""
            SELECT
                AVG(EXTRACT(EPOCH FROM (update_time - create_time)) * 1000) AS avg_duration_ms,
                PERCENTILE_CONT(0.95) WITHIN GROUP (
                    ORDER BY EXTRACT(EPOCH FROM (update_time - create_time)) * 1000
                ) AS p95_duration_ms
            FROM rpa_agent_task
            WHERE org_id = #{orgId} AND deleted = 0
              AND status IN ('SUCCESS', 'FAILED', 'ABORTED')
            """)
    TaskDurationStatDTO selectTaskDurationStat(@Param("orgId") Long orgId);

    /**
     * LLM 调用聚合统计（总次数 / 总成本 / 缓存命中数）
     *
     * @param orgId 组织 ID
     * @return LLM 聚合统计；无记录时返回 null
     */
    @Select("""
            SELECT
                COUNT(*) AS call_count,
                COALESCE(SUM(cost), 0) AS total_cost,
                SUM(CASE WHEN cache_hit = TRUE THEN 1 ELSE 0 END) AS cache_hit_count
            FROM rpa_llm_call_log
            WHERE org_id = #{orgId} AND deleted = 0
            """)
    LlmAggregateDTO selectLlmAggregate(@Param("orgId") Long orgId);

    /**
     * 人工接管队列聚合统计（待处置队列长度 / 平均处置时长）
     *
     * @param orgId 组织 ID
     * @return 人工接管聚合统计
     */
    @Select("""
            SELECT
                SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS queue_size,
                AVG(CASE WHEN status = 'RESOLVED' AND resolved_at IS NOT NULL
                         THEN EXTRACT(EPOCH FROM (resolved_at - create_time)) * 1000 END) AS avg_resolve_ms
            FROM rpa_needs_human_queue
            WHERE org_id = #{orgId} AND deleted = 0
            """)
    HumanTakeoverAggregateDTO selectHumanTakeoverAggregate(@Param("orgId") Long orgId);

    /**
     * 风险等级分布（按任务去重计数）
     *
     * @param orgId 组织 ID
     * @return 风险等级-计数列表
     */
    @Select("""
            SELECT risk_level, COUNT(DISTINCT task_id) AS count
            FROM rpa_audit_log
            WHERE org_id = #{orgId} AND deleted = 0 AND risk_level IS NOT NULL
            GROUP BY risk_level
            """)
    List<RiskLevelStatVO> countRiskLevel(@Param("orgId") Long orgId);

    // endregion

    // region 环比趋势（trends 对比，今日 vs 昨日）

    /**
     * 按日期区间统计任务总数 / 成功数 / 失败数（用于环比对比）
     *
     * @param orgId     组织 ID
     * @param start     起始日期（含）
     * @param end       结束日期（含，exclusive 上界由 startDate + 1 天控制）
     * @return 状态计数列表
     */
    @Select("""
            SELECT status, COUNT(*) AS count
            FROM rpa_agent_task
            WHERE org_id = #{orgId} AND deleted = 0
              AND create_time >= #{start} AND create_time < #{end}
            GROUP BY status
            """)
    List<TaskStatusCountDTO> countTaskByStatusInRange(@Param("orgId") Long orgId,
                                                       @Param("start") LocalDate start,
                                                       @Param("end") LocalDate end);

    /**
     * 按日期区间统计 LLM 总成本（用于环比对比）
     *
     * @param orgId 组织 ID
     * @param start 起始日期（含）
     * @param end   结束日期（不含）
     * @return LLM 聚合统计；无记录时返回 null
     */
    @Select("""
            SELECT
                COUNT(*) AS call_count,
                COALESCE(SUM(cost), 0) AS total_cost,
                SUM(CASE WHEN cache_hit = TRUE THEN 1 ELSE 0 END) AS cache_hit_count
            FROM rpa_llm_call_log
            WHERE org_id = #{orgId} AND deleted = 0
              AND call_time >= #{start} AND call_time < #{end}
            """)
    LlmAggregateDTO selectLlmAggregateInRange(@Param("orgId") Long orgId,
                                               @Param("start") LocalDate start,
                                               @Param("end") LocalDate end);

    // endregion

    // region 趋势（trends）

    /**
     * 任务量按日聚合（任务总数 / 成功 / 失败）
     *
     * @param orgId     组织 ID
     * @param startDate 起始日期（含）
     * @return 按日任务量列表
     */
    @Select("""
            SELECT
                TO_CHAR(create_time, 'YYYY-MM-DD') AS date,
                COUNT(*) AS task_count,
                SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count
            FROM rpa_agent_task
            WHERE org_id = #{orgId} AND deleted = 0 AND create_time >= #{startDate}
            GROUP BY date
            ORDER BY date ASC
            """)
    List<TrendTaskDTO> selectTaskTrend(@Param("orgId") Long orgId, @Param("startDate") LocalDate startDate);

    /**
     * LLM 成本按日聚合
     *
     * @param orgId     组织 ID
     * @param startDate 起始日期（含）
     * @return 按日成本列表
     */
    @Select("""
            SELECT
                TO_CHAR(call_time, 'YYYY-MM-DD') AS date,
                COALESCE(SUM(cost), 0) AS cost
            FROM rpa_llm_call_log
            WHERE org_id = #{orgId} AND deleted = 0 AND call_time >= #{startDate}
            GROUP BY date
            ORDER BY date ASC
            """)
    List<TrendCostDTO> selectCostTrend(@Param("orgId") Long orgId, @Param("startDate") LocalDate startDate);

    // endregion

    // region 业务线分布（business-lines）

    /**
     * 各业务线任务分布 + 成功率
     *
     * <p>任务数来自审计日志 distinct task_id（任务表无 business_line_id 字段），
     * 成功任务数关联 rpa_agent_task 取 status=SUCCESS。子查询中 rpa_* 表均手动 org_id 过滤。</p>
     *
     * @param orgId 组织 ID
     * @return 业务线统计列表
     */
    @Select("""
            SELECT
                bl.business_line_id AS business_line_id,
                bl.business_line_name AS business_line_name,
                COALESCE((
                    SELECT COUNT(DISTINCT al.task_id)
                    FROM rpa_audit_log al
                    WHERE al.business_line_id = bl.business_line_id
                      AND al.org_id = #{orgId} AND al.deleted = 0
                ), 0) AS task_count,
                COALESCE((
                    SELECT COUNT(DISTINCT al.task_id)
                    FROM rpa_audit_log al
                    JOIN rpa_agent_task t ON t.task_id = al.task_id AND t.deleted = 0
                    WHERE al.business_line_id = bl.business_line_id
                      AND al.org_id = #{orgId} AND al.deleted = 0
                      AND t.org_id = #{orgId} AND t.status = 'SUCCESS'
                ), 0) AS success_count
            FROM enterprise_business_line bl
            WHERE bl.deleted = 0 AND bl.status = 1
            ORDER BY task_count DESC
            """)
    List<BusinessLineStatVO> selectBusinessLineStats(@Param("orgId") Long orgId);

    // endregion

    // region 错误类型分布（errors）

    /**
     * 错误类型分布 Top 10（按失败操作类型聚合）
     *
     * @param orgId 组织 ID
     * @return 错误类型-计数列表（最多 10 条）
     */
    @Select("""
            SELECT action_type AS error_type, COUNT(*) AS count
            FROM rpa_audit_log
            WHERE org_id = #{orgId} AND deleted = 0 AND execution_result = 'failed'
            GROUP BY action_type
            ORDER BY count DESC
            LIMIT 10
            """)
    List<ErrorTypeStatVO> selectErrorTypeStats(@Param("orgId") Long orgId);

    // endregion

    // region LLM 成本（costs）

    /**
     * 按模型维度的 LLM 成本统计
     *
     * @param orgId 组织 ID
     * @return 按模型成本列表
     */
    @Select("""
            SELECT
                model,
                COUNT(*) AS calls,
                COALESCE(SUM(cost), 0) AS cost,
                COALESCE(SUM(total_tokens), 0) AS tokens
            FROM rpa_llm_call_log
            WHERE org_id = #{orgId} AND deleted = 0
            GROUP BY model
            ORDER BY cost DESC
            """)
    List<CostStatVO.ModelCostStatVO> selectModelCostStats(@Param("orgId") Long orgId);

    // endregion

    // region 审批统计（approvals）

    /**
     * 审批统计（总数 / 通过 / 拒绝 / 超时 / 待处理 / 平均响应时长）
     *
     * @param orgId 组织 ID
     * @return 审批统计；无记录时返回 null
     */
    @Select("""
            SELECT
                COUNT(*) AS total_approvals,
                SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count,
                SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
                SUM(CASE WHEN status = 'TIMEOUT' THEN 1 ELSE 0 END) AS timeout_count,
                SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                AVG(CASE WHEN approved_at IS NOT NULL
                         THEN EXTRACT(EPOCH FROM (approved_at - create_time)) / 60 END) AS avg_response_minutes
            FROM rpa_approval_request
            WHERE org_id = #{orgId} AND deleted = 0
            """)
    ApprovalStatVO selectApprovalStat(@Param("orgId") Long orgId);
}

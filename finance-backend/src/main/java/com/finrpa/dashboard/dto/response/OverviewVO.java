package com.finrpa.dashboard.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 运营大屏概览 VO（对齐系统设计 6.9.1 任务 / 性能 / LLM / 人工 / 风险 五类指标）
 *
 * <p>供前端概览卡片展示，含任务统计、性能指标、LLM 成本、人工接管队列、风险等级分布。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class OverviewVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    // region 任务指标

    /** 任务总数 */
    private Long totalTasks;

    /** 成功任务数 */
    private Long successTasks;

    /** 失败任务数 */
    private Long failedTasks;

    /** 进行中任务数（EXECUTING + PENDING + NEEDS_HUMAN） */
    private Long runningTasks;

    /** 任务成功率（0-1，successTasks / totalTasks） */
    private Double successRate;

    // endregion

    // region 性能指标

    /** 平均执行时长（毫秒，基于已终态任务） */
    private Double avgDurationMs;

    /** P95 执行时长（毫秒，基于已终态任务） */
    private Long p95DurationMs;

    // endregion

    // region LLM 指标

    /** LLM 调用总次数 */
    private Long llmCallCount;

    /** LLM 总成本（美元） */
    private BigDecimal llmTotalCost;

    /** Action 缓存命中率（0-1） */
    private Double llmCacheHitRate;

    // endregion

    // region 人工指标

    /** 接管队列长度（PENDING 待处置数） */
    private Long humanTakeoverQueueSize;

    /** 平均处置时长（毫秒，已 RESOLVED 的平均 resolved_at - create_time） */
    private Double avgResolveDurationMs;

    // endregion

    // region 风险指标

    /** 风险等级分布（low/medium/high/critical 计数） */
    private List<RiskLevelStatVO> riskLevelDistribution;

    // endregion

    // region 环比趋势（今日 vs 昨日，对齐原型 02-dashboard.html KPI 卡片 trend 文案）

    /** 任务总数环比增长率（今日 vs 昨日，0.12 表示 +12%；null 表示无上期数据） */
    private Double taskGrowthRate;

    /** 成功率环比差值（百分点，0.021 表示 +2.1%；null 表示无上期数据） */
    private Double successRateDelta;

    /** LLM 成本环比变化率（今日 vs 昨日，-0.08 表示 -8%；null 表示无上期数据） */
    private Double llmCostDelta;

    // endregion
}

package com.finrpa.dashboard.constant;

import java.time.Duration;

/**
 * 运营大屏模块常量
 *
 * <p>定义 Redis 缓存 Key 前缀、TTL、Metric 名称等常量，对齐系统设计 6.9.2 缓存策略。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface DashboardConstant {

    // region 缓存 Key

    /** 缓存 Key 前缀，格式 dashboard:{orgId}:{metric}:{date} */
    String CACHE_KEY_PREFIX = "dashboard:";

    /** 缓存 Key 模板（orgId + metric + date） */
    String CACHE_KEY_TEMPLATE = CACHE_KEY_PREFIX + "%s:%s:%s";

    /** 缓存 Key 通配符（按 orgId 失效全部 metric），用于任务终态时批量删除 */
    String CACHE_KEY_PATTERN_BY_ORG = CACHE_KEY_PREFIX + "%s:*";

    // endregion

    // region Metric 名称（Key 第二段）

    /** 概览指标（实时） */
    String METRIC_OVERVIEW = "overview";

    /** 业务线分布指标（实时） */
    String METRIC_BUSINESS_LINES = "business-lines";

    /** 错误类型分布指标（实时） */
    String METRIC_ERRORS = "errors";

    /** LLM 成本指标（实时） */
    String METRIC_COSTS = "costs";

    /** 审批统计指标（实时） */
    String METRIC_APPROVALS = "approvals";

    /** 趋势指标（历史，TTL 较长） */
    String METRIC_TRENDS = "trends";

    // endregion

    // region TTL

    /** 实时指标 TTL（5 分钟） */
    Duration TTL_REALTIME = Duration.ofMinutes(5);

    /** 历史趋势指标 TTL（1 小时） */
    Duration TTL_TRENDS = Duration.ofHours(1);

    // endregion

    // region 默认参数

    /** 趋势查询默认天数（最近 7 天） */
    int DEFAULT_TREND_DAYS = 7;

    /** 趋势查询最大天数（防止一次拉取过多） */
    int MAX_TREND_DAYS = 90;

    /** 日期格式（用于缓存 Key 中的 date 段，对齐 LocalDate） */
    String DATE_KEY_FORMAT = "yyyyMMdd";

    // endregion

    // region 任务状态（对齐 TaskStateEnum，避免跨模块循环依赖此处硬编码）

    /** 任务状态：成功 */
    String TASK_STATUS_SUCCESS = "SUCCESS";

    /** 任务状态：失败 */
    String TASK_STATUS_FAILED = "FAILED";

    /** 任务状态：执行中 */
    String TASK_STATUS_EXECUTING = "EXECUTING";

    /** 任务状态：待执行 */
    String TASK_STATUS_PENDING = "PENDING";

    /** 任务状态：需要人工介入 */
    String TASK_STATUS_NEEDS_HUMAN = "NEEDS_HUMAN";

    /** 任务状态：已终止 */
    String TASK_STATUS_ABORTED = "ABORTED";

    // endregion

    // region 审批状态（对齐 ApprovalConstant，避免跨模块循环依赖此处硬编码）

    /** 审批状态：超时 */
    String APPROVAL_STATUS_TIMEOUT = "TIMEOUT";

    // endregion
}

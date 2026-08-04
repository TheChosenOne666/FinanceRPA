package com.finrpa.dashboard.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.dashboard.constant.DashboardConstant;
import com.finrpa.dashboard.dto.response.ApprovalStatVO;
import com.finrpa.dashboard.dto.response.BusinessLineStatVO;
import com.finrpa.dashboard.dto.response.CostStatVO;
import com.finrpa.dashboard.dto.response.ErrorTypeStatVO;
import com.finrpa.dashboard.dto.response.OverviewVO;
import com.finrpa.dashboard.dto.response.RiskLevelStatVO;
import com.finrpa.dashboard.dto.response.TrendsVO;
import com.finrpa.dashboard.dto.stats.HumanTakeoverAggregateDTO;
import com.finrpa.dashboard.dto.stats.LlmAggregateDTO;
import com.finrpa.dashboard.dto.stats.TaskDurationStatDTO;
import com.finrpa.dashboard.dto.stats.TaskStatusCountDTO;
import com.finrpa.dashboard.dto.stats.TrendCostDTO;
import com.finrpa.dashboard.dto.stats.TrendTaskDTO;
import com.finrpa.dashboard.mapper.DashboardStatsMapper;
import com.finrpa.dashboard.service.DashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * 运营大屏服务实现（M8.1）
 *
 * <p>统计查询走 Redis 缓存（Redisson RBucket + StringCodec + JSON）：
 * 实时指标 TTL 5 分钟，趋势指标 TTL 1 小时。缓存 key 格式 {@code dashboard:{orgId}:{metric}:{date}}。
 * 任务终态时由监听器调用 {@link #invalidateCache(Long)} 按 orgId 通配删除全部缓存。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 统计 Mapper */
    @Resource
    private DashboardStatsMapper dashboardStatsMapper;

    /** Redisson 客户端 */
    @Resource
    private RedissonClient redissonClient;

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    /** 日期格式化（缓存 key 的 date 段） */
    private static final DateTimeFormatter DATE_KEY_FORMATTER =
            DateTimeFormatter.ofPattern(DashboardConstant.DATE_KEY_FORMAT);

    // region 对外接口

    /**
     * 获取概览指标
     */
    @Override
    public OverviewVO getOverview(Long orgId) {
        String key = buildCacheKey(orgId, DashboardConstant.METRIC_OVERVIEW, LocalDate.now());
        return getCached(key, OverviewVO.class, DashboardConstant.TTL_REALTIME, () -> loadOverview(orgId));
    }

    /**
     * 获取趋势指标
     */
    @Override
    public TrendsVO getTrends(Long orgId, Integer days) {
        // 1. 计算实际天数与起始日期
        int actualDays = (days == null || days <= 0)
                ? DashboardConstant.DEFAULT_TREND_DAYS
                : Math.min(days, DashboardConstant.MAX_TREND_DAYS);
        LocalDate startDate = LocalDate.now().minusDays((long) actualDays - 1);

        // 2. 趋势缓存 date 段使用 startDate（同一起始日期的查询共享缓存）
        String key = buildCacheKey(orgId, DashboardConstant.METRIC_TRENDS, startDate);
        return getCached(key, TrendsVO.class, DashboardConstant.TTL_TRENDS, () -> loadTrends(orgId, startDate));
    }

    /**
     * 获取业务线分布
     */
    @Override
    public List<BusinessLineStatVO> getBusinessLineStats(Long orgId) {
        String key = buildCacheKey(orgId, DashboardConstant.METRIC_BUSINESS_LINES, LocalDate.now());
        return getCached(key, new TypeReference<List<BusinessLineStatVO>>() {},
                DashboardConstant.TTL_REALTIME, () -> loadBusinessLineStats(orgId));
    }

    /**
     * 获取错误类型分布
     */
    @Override
    public List<ErrorTypeStatVO> getErrorTypeStats(Long orgId) {
        String key = buildCacheKey(orgId, DashboardConstant.METRIC_ERRORS, LocalDate.now());
        return getCached(key, new TypeReference<List<ErrorTypeStatVO>>() {},
                DashboardConstant.TTL_REALTIME, () -> dashboardStatsMapper.selectErrorTypeStats(orgId));
    }

    /**
     * 获取 LLM 成本统计
     */
    @Override
    public CostStatVO getCosts(Long orgId) {
        String key = buildCacheKey(orgId, DashboardConstant.METRIC_COSTS, LocalDate.now());
        return getCached(key, CostStatVO.class, DashboardConstant.TTL_REALTIME, () -> loadCosts(orgId));
    }

    /**
     * 获取审批统计
     */
    @Override
    public ApprovalStatVO getApprovals(Long orgId) {
        String key = buildCacheKey(orgId, DashboardConstant.METRIC_APPROVALS, LocalDate.now());
        return getCached(key, ApprovalStatVO.class, DashboardConstant.TTL_REALTIME,
                () -> normalizeApprovalStat(dashboardStatsMapper.selectApprovalStat(orgId)));
    }

    /**
     * 失效指定组织的全部大屏缓存
     */
    @Override
    public void invalidateCache(Long orgId) {
        if (orgId == null) {
            return;
        }
        String pattern = String.format(DashboardConstant.CACHE_KEY_PATTERN_BY_ORG, orgId);
        try {
            long deleted = redissonClient.getKeys().deleteByPattern(pattern);
            log.info("[Dashboard] 缓存失效: orgId={}, pattern={}, deleted={}", orgId, pattern, deleted);
        } catch (Exception e) {
            log.error("[Dashboard] 缓存失效异常: orgId={}, pattern={}, error={}", orgId, pattern, e.getMessage(), e);
        }
    }

    // endregion

    // region 概览加载

    /**
     * 加载概览数据（5 类指标聚合）
     *
     * @param orgId 组织 ID
     * @return 概览 VO
     */
    private OverviewVO loadOverview(Long orgId) {
        OverviewVO vo = new OverviewVO();

        // 1. 任务指标（按状态分组）
        List<TaskStatusCountDTO> statusCounts = dashboardStatsMapper.countTaskByStatus(orgId);
        long totalTasks = 0, successTasks = 0, failedTasks = 0, runningTasks = 0;
        if (statusCounts != null) {
            for (TaskStatusCountDTO sc : statusCounts) {
                long c = sc.getCount() == null ? 0 : sc.getCount();
                totalTasks += c;
                if (DashboardConstant.TASK_STATUS_SUCCESS.equals(sc.getStatus())) {
                    successTasks += c;
                } else if (DashboardConstant.TASK_STATUS_FAILED.equals(sc.getStatus())) {
                    failedTasks += c;
                } else if (DashboardConstant.TASK_STATUS_EXECUTING.equals(sc.getStatus())
                        || DashboardConstant.TASK_STATUS_PENDING.equals(sc.getStatus())
                        || DashboardConstant.TASK_STATUS_NEEDS_HUMAN.equals(sc.getStatus())) {
                    runningTasks += c;
                }
            }
        }
        vo.setTotalTasks(totalTasks);
        vo.setSuccessTasks(successTasks);
        vo.setFailedTasks(failedTasks);
        vo.setRunningTasks(runningTasks);
        vo.setSuccessRate(totalTasks > 0 ? (double) successTasks / totalTasks : 0.0);

        // 2. 性能指标（已终态任务执行时长）
        TaskDurationStatDTO duration = dashboardStatsMapper.selectTaskDurationStat(orgId);
        if (duration != null) {
            vo.setAvgDurationMs(duration.getAvgDurationMs());
            vo.setP95DurationMs(duration.getP95DurationMs() == null
                    ? null : Math.round(duration.getP95DurationMs()));
        }

        // 3. LLM 指标
        LlmAggregateDTO llm = dashboardStatsMapper.selectLlmAggregate(orgId);
        long llmCallCount = (llm != null && llm.getCallCount() != null) ? llm.getCallCount() : 0;
        vo.setLlmCallCount(llmCallCount);
        vo.setLlmTotalCost((llm != null && llm.getTotalCost() != null) ? llm.getTotalCost() : BigDecimal.ZERO);
        vo.setLlmCacheHitRate((llmCallCount > 0 && llm != null && llm.getCacheHitCount() != null)
                ? (double) llm.getCacheHitCount() / llmCallCount : 0.0);

        // 4. 人工指标
        HumanTakeoverAggregateDTO human = dashboardStatsMapper.selectHumanTakeoverAggregate(orgId);
        vo.setHumanTakeoverQueueSize((human != null && human.getQueueSize() != null)
                ? human.getQueueSize() : 0L);
        vo.setAvgResolveDurationMs(human != null ? human.getAvgResolveMs() : null);

        // 5. 风险等级分布
        List<RiskLevelStatVO> riskLevels = dashboardStatsMapper.countRiskLevel(orgId);
        vo.setRiskLevelDistribution(riskLevels != null ? riskLevels : List.of());

        // 6. 环比趋势（今日 vs 昨日，对齐原型 KPI 卡片 trend 文案）
        fillGrowthRates(vo, orgId);

        return vo;
    }

    /**
     * 填充环比趋势字段（今日 vs 昨日）
     *
     * <p>口径：
     * <ul>
     *   <li>任务总数增长率 = (今日总数 - 昨日总数) / 昨日总数</li>
     *   <li>成功率差值 = 今日成功率 - 昨日成功率（百分点）</li>
     *   <li>LLM 成本变化率 = (今日成本 - 昨日成本) / 昨日成本</li>
     * </ul>
     * 上期数据为 0 或不存在时，对应字段返回 null（前端显示为 "—"）。</p>
     *
     * @param vo   概览 VO（in-place 填充）
     * @param orgId 组织 ID
     */
    private void fillGrowthRates(OverviewVO vo, Long orgId) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 1. 任务量环比
        List<TaskStatusCountDTO> todayStatus = dashboardStatsMapper.countTaskByStatusInRange(orgId, today, today.plusDays(1));
        List<TaskStatusCountDTO> yesterdayStatus = dashboardStatsMapper.countTaskByStatusInRange(orgId, yesterday, today);
        long todayTotal = sumCount(todayStatus);
        long yesterdayTotal = sumCount(yesterdayStatus);
        if (yesterdayTotal > 0) {
            vo.setTaskGrowthRate((double) (todayTotal - yesterdayTotal) / yesterdayTotal);
        }

        // 2. 成功率环比差值（百分点）
        long todaySuccess = sumCountByStatus(todayStatus, DashboardConstant.TASK_STATUS_SUCCESS);
        long yesterdaySuccess = sumCountByStatus(yesterdayStatus, DashboardConstant.TASK_STATUS_SUCCESS);
        if (todayTotal > 0 && yesterdayTotal > 0) {
            double todayRate = (double) todaySuccess / todayTotal;
            double yesterdayRate = (double) yesterdaySuccess / yesterdayTotal;
            vo.setSuccessRateDelta(todayRate - yesterdayRate);
        }

        // 3. LLM 成本环比
        LlmAggregateDTO todayLlm = dashboardStatsMapper.selectLlmAggregateInRange(orgId, today, today.plusDays(1));
        LlmAggregateDTO yesterdayLlm = dashboardStatsMapper.selectLlmAggregateInRange(orgId, yesterday, today);
        BigDecimal todayCost = (todayLlm != null && todayLlm.getTotalCost() != null) ? todayLlm.getTotalCost() : BigDecimal.ZERO;
        BigDecimal yesterdayCost = (yesterdayLlm != null && yesterdayLlm.getTotalCost() != null) ? yesterdayLlm.getTotalCost() : BigDecimal.ZERO;
        if (yesterdayCost.compareTo(BigDecimal.ZERO) > 0) {
            vo.setLlmCostDelta(todayCost.subtract(yesterdayCost)
                    .divide(yesterdayCost, 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue());
        }
    }

    /**
     * 汇总状态计数列表的总数
     *
     * @param list 状态计数列表
     * @return 总数；null 视为 0
     */
    private long sumCount(List<TaskStatusCountDTO> list) {
        if (list == null) return 0;
        long sum = 0;
        for (TaskStatusCountDTO sc : list) {
            sum += (sc.getCount() == null) ? 0 : sc.getCount();
        }
        return sum;
    }

    /**
     * 按状态过滤并汇总计数
     *
     * @param list   状态计数列表
     * @param status 目标状态
     * @return 该状态的计数
     */
    private long sumCountByStatus(List<TaskStatusCountDTO> list, String status) {
        if (list == null) return 0;
        long sum = 0;
        for (TaskStatusCountDTO sc : list) {
            if (status.equals(sc.getStatus())) {
                sum += (sc.getCount() == null) ? 0 : sc.getCount();
            }
        }
        return sum;
    }

    // endregion

    // region 趋势加载

    /**
     * 加载趋势数据（任务量 + 成本按日合并）
     *
     * @param orgId     组织 ID
     * @param startDate 起始日期
     * @return 趋势 VO
     */
    private TrendsVO loadTrends(Long orgId, LocalDate startDate) {
        // 1. 查询任务量趋势与成本趋势
        List<TrendTaskDTO> taskTrend = dashboardStatsMapper.selectTaskTrend(orgId, startDate);
        List<TrendCostDTO> costTrend = dashboardStatsMapper.selectCostTrend(orgId, startDate);

        // 2. 按 date 合并（TreeMap 保证日期升序），缺失维度补 0
        Map<String, TrendsVO.TrendPointVO> pointMap = new TreeMap<>();
        if (taskTrend != null) {
            for (TrendTaskDTO t : taskTrend) {
                TrendsVO.TrendPointVO p = new TrendsVO.TrendPointVO();
                p.setDate(t.getDate());
                p.setTaskCount(t.getTaskCount());
                p.setSuccessCount(t.getSuccessCount());
                p.setFailedCount(t.getFailedCount());
                p.setCost(BigDecimal.ZERO);
                pointMap.put(t.getDate(), p);
            }
        }
        if (costTrend != null) {
            for (TrendCostDTO c : costTrend) {
                TrendsVO.TrendPointVO p = pointMap.get(c.getDate());
                BigDecimal cost = (c.getCost() == null) ? BigDecimal.ZERO : c.getCost();
                if (p == null) {
                    p = new TrendsVO.TrendPointVO();
                    p.setDate(c.getDate());
                    p.setTaskCount(0L);
                    p.setSuccessCount(0L);
                    p.setFailedCount(0L);
                    p.setCost(cost);
                    pointMap.put(c.getDate(), p);
                } else {
                    p.setCost(cost);
                }
            }
        }

        TrendsVO vo = new TrendsVO();
        vo.setPoints(new ArrayList<>(pointMap.values()));
        return vo;
    }

    // endregion

    // region 业务线加载

    /**
     * 加载业务线统计（计算成功率）
     *
     * @param orgId 组织 ID
     * @return 业务线统计列表
     */
    private List<BusinessLineStatVO> loadBusinessLineStats(Long orgId) {
        List<BusinessLineStatVO> list = dashboardStatsMapper.selectBusinessLineStats(orgId);
        if (list == null) {
            return List.of();
        }
        for (BusinessLineStatVO bl : list) {
            long tc = (bl.getTaskCount() == null) ? 0 : bl.getTaskCount();
            long sc = (bl.getSuccessCount() == null) ? 0 : bl.getSuccessCount();
            bl.setSuccessRate(tc > 0 ? (double) sc / tc : 0.0);
        }
        return list;
    }

    // endregion

    // region 成本加载

    /**
     * 加载 LLM 成本统计（含按模型维度 + 总计）
     *
     * @param orgId 组织 ID
     * @return 成本统计 VO
     */
    private CostStatVO loadCosts(Long orgId) {
        // 1. 按模型统计
        List<CostStatVO.ModelCostStatVO> modelCosts = dashboardStatsMapper.selectModelCostStats(orgId);

        // 2. 总计
        LlmAggregateDTO llm = dashboardStatsMapper.selectLlmAggregate(orgId);
        long totalCalls = (llm != null && llm.getCallCount() != null) ? llm.getCallCount() : 0;

        CostStatVO vo = new CostStatVO();
        vo.setModelCosts(modelCosts != null ? modelCosts : List.of());
        vo.setTotalCalls(totalCalls);
        vo.setTotalCost((llm != null && llm.getTotalCost() != null) ? llm.getTotalCost() : BigDecimal.ZERO);
        // 总 token 从模型维度汇总
        long totalTokens = (modelCosts == null) ? 0
                : modelCosts.stream().mapToLong(m -> (m.getTokens() == null) ? 0 : m.getTokens()).sum();
        vo.setTotalTokens(totalTokens);
        vo.setCacheHitRate((totalCalls > 0 && llm != null && llm.getCacheHitCount() != null)
                ? (double) llm.getCacheHitCount() / totalCalls : 0.0);
        return vo;
    }

    // endregion

    // region 缓存读写

    /**
     * 构建缓存 key
     *
     * @param orgId   组织 ID
     * @param metric  指标名
     * @param dateRef 日期基准
     * @return 缓存 key
     */
    private String buildCacheKey(Long orgId, String metric, LocalDate dateRef) {
        return String.format(DashboardConstant.CACHE_KEY_TEMPLATE, orgId, metric, dateRef.format(DATE_KEY_FORMATTER));
    }

    /**
     * 通用缓存读取（Class 类型）
     *
     * @param key    缓存 key
     * @param type   值类型
     * @param ttl    缓存 TTL
     * @param loader 回源加载器
     * @param <T>    值泛型
     * @return 值
     */
    private <T> T getCached(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return getCachedInternal(key, json -> {
            try {
                return objectMapper.readValue(json, type);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }, ttl, loader);
    }

    /**
     * 通用缓存读取（TypeReference 类型，用于 List 等泛型容器）
     *
     * @param key    缓存 key
     * @param type   值类型引用
     * @param ttl    缓存 TTL
     * @param loader 回源加载器
     * @param <T>    值泛型
     * @return 值
     */
    private <T> T getCached(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        return getCachedInternal(key, json -> {
            try {
                return objectMapper.readValue(json, type);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }, ttl, loader);
    }

    /**
     * 缓存读取内部实现（命中则反序列化返回；未命中则回源加载并写入缓存）
     *
     * @param key        缓存 key
     * @param deserializer 反序列化函数
     * @param ttl        缓存 TTL
     * @param loader     回源加载器
     * @param <T>        值泛型
     * @return 值
     */
    private <T> T getCachedInternal(String key, java.util.function.Function<String, T> deserializer,
                                    Duration ttl, Supplier<T> loader) {
        // 1. 尝试读缓存
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        String json = null;
        try {
            json = bucket.get();
        } catch (Exception e) {
            log.warn("[Dashboard] 缓存读取异常，回源查询: key={}, error={}", key, e.getMessage());
        }
        if (json != null) {
            try {
                return deserializer.apply(json);
            } catch (Exception e) {
                log.warn("[Dashboard] 缓存反序列化失败，回源查询: key={}, error={}", key, e.getMessage());
            }
        }

        // 2. 回源加载
        T data = loader.get();

        // 3. 写入缓存（失败不影响主流程）
        try {
            bucket.set(objectMapper.writeValueAsString(data), ttl);
        } catch (Exception e) {
            log.warn("[Dashboard] 缓存写入失败: key={}, error={}", key, e.getMessage());
        }
        return data;
    }

    // endregion

    // region 工具方法

    /**
     * 规范化审批统计（null 值转 0）
     *
     * @param raw 原始统计（可能为 null 或字段为 null）
     * @return 规范化后的统计
     */
    private ApprovalStatVO normalizeApprovalStat(ApprovalStatVO raw) {
        ApprovalStatVO vo = (raw == null) ? new ApprovalStatVO() : raw;
        if (vo.getTotalApprovals() == null) {
            vo.setTotalApprovals(0L);
        }
        if (vo.getApprovedCount() == null) {
            vo.setApprovedCount(0L);
        }
        if (vo.getRejectedCount() == null) {
            vo.setRejectedCount(0L);
        }
        if (vo.getTimeoutCount() == null) {
            vo.setTimeoutCount(0L);
        }
        if (vo.getPendingCount() == null) {
            vo.setPendingCount(0L);
        }
        return vo;
    }

    // endregion
}

package com.finrpa.dashboard.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.dashboard.dto.response.ApprovalStatVO;
import com.finrpa.dashboard.dto.response.BusinessLineStatVO;
import com.finrpa.dashboard.dto.response.CostStatVO;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 运营大屏服务实现单元测试（M8.1）
 *
 * <p>重点验证：缓存命中/未命中分支、指标组装逻辑（successRate/cacheHitRate 计算、
 * null 安全、趋势日期合并、业务线成功率计算、审批 null 归零）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    /** 测试组织 ID */
    private static final Long ORG_ID = 1001L;

    @Mock
    private DashboardStatsMapper dashboardStatsMapper;

    @Mock
    private RedissonClient redissonClient;

    /** 真实 ObjectMapper（spy 包装），保证序列化/反序列化真实可用 */
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    // region getOverview 缓存未命中 - 指标组装

    @Test
    @DisplayName("概览 - 缓存未命中时组装五类指标并写缓存")
    void getOverview_CacheMiss_AssemblesAndCaches() {
        // 1. mock Mapper
        when(dashboardStatsMapper.countTaskByStatus(ORG_ID)).thenReturn(List.of(
                new TaskStatusCountDTO() {{ setStatus("SUCCESS"); setCount(8L); }},
                new TaskStatusCountDTO() {{ setStatus("FAILED"); setCount(2L); }},
                new TaskStatusCountDTO() {{ setStatus("EXECUTING"); setCount(3L); }},
                new TaskStatusCountDTO() {{ setStatus("PENDING"); setCount(1L); }}
        ));
        TaskDurationStatDTO duration = new TaskDurationStatDTO();
        duration.setAvgDurationMs(10000.0);
        duration.setP95DurationMs(30000.0);
        when(dashboardStatsMapper.selectTaskDurationStat(ORG_ID)).thenReturn(duration);
        LlmAggregateDTO llm = new LlmAggregateDTO();
        llm.setCallCount(100L);
        llm.setTotalCost(new BigDecimal("1.50"));
        llm.setCacheHitCount(30L);
        when(dashboardStatsMapper.selectLlmAggregate(ORG_ID)).thenReturn(llm);
        HumanTakeoverAggregateDTO human = new HumanTakeoverAggregateDTO();
        human.setQueueSize(5L);
        human.setAvgResolveMs(60000.0);
        when(dashboardStatsMapper.selectHumanTakeoverAggregate(ORG_ID)).thenReturn(human);
        when(dashboardStatsMapper.countRiskLevel(ORG_ID)).thenReturn(List.of(
                new RiskLevelStatVO("low", 10L),
                new RiskLevelStatVO("high", 5L)
        ));

        // 2. mock Redis：缓存未命中
        RBucket<String> bucket = mockBucketMiss();

        // 3. 执行
        OverviewVO vo = dashboardService.getOverview(ORG_ID);

        // 4. 验证任务指标
        assertEquals(14L, vo.getTotalTasks());
        assertEquals(8L, vo.getSuccessTasks());
        assertEquals(2L, vo.getFailedTasks());
        assertEquals(4L, vo.getRunningTasks());
        assertEquals(8.0 / 14.0, vo.getSuccessRate(), 0.0001);

        // 5. 验证性能指标
        assertEquals(10000.0, vo.getAvgDurationMs());
        assertEquals(30000L, vo.getP95DurationMs());

        // 6. 验证 LLM 指标
        assertEquals(100L, vo.getLlmCallCount());
        assertEquals(0, vo.getLlmTotalCost().compareTo(new BigDecimal("1.50")));
        assertEquals(0.3, vo.getLlmCacheHitRate(), 0.0001);

        // 7. 验证人工指标
        assertEquals(5L, vo.getHumanTakeoverQueueSize());
        assertEquals(60000.0, vo.getAvgResolveDurationMs());

        // 8. 验证风险分布
        assertEquals(2, vo.getRiskLevelDistribution().size());

        // 9. 验证缓存写入
        verify(bucket, times(1)).set(anyString(), any());
    }

    @Test
    @DisplayName("概览 - 缓存命中时不调用 Mapper")
    void getOverview_CacheHit_SkipsMapper() throws Exception {
        // 1. 构造缓存值
        OverviewVO cached = new OverviewVO();
        cached.setTotalTasks(50L);
        cached.setSuccessTasks(40L);
        cached.setSuccessRate(0.8);
        String json = objectMapper.writeValueAsString(cached);

        // 2. mock Redis：缓存命中
        RBucket<String> bucket = mock(RBucket.class);
        when(bucket.get()).thenReturn(json);
        doReturn(bucket).when(redissonClient).getBucket(anyString(), any());

        // 3. 执行
        OverviewVO vo = dashboardService.getOverview(ORG_ID);

        // 4. 验证返回缓存值
        assertEquals(50L, vo.getTotalTasks());
        assertEquals(40L, vo.getSuccessTasks());

        // 5. 验证 Mapper 未被调用
        verifyNoInteractions(dashboardStatsMapper);
        // 6. 验证未写缓存
        verify(bucket, never()).set(anyString(), any());
    }

    @Test
    @DisplayName("概览 - 空数据时返回零值不报错")
    void getOverview_EmptyData_ReturnsZeros() {
        when(dashboardStatsMapper.countTaskByStatus(ORG_ID)).thenReturn(List.of());
        when(dashboardStatsMapper.selectTaskDurationStat(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.selectLlmAggregate(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.selectHumanTakeoverAggregate(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.countRiskLevel(ORG_ID)).thenReturn(List.of());

        mockBucketMiss();

        OverviewVO vo = dashboardService.getOverview(ORG_ID);

        assertEquals(0L, vo.getTotalTasks());
        assertEquals(0.0, vo.getSuccessRate());
        assertNull(vo.getAvgDurationMs());
        assertEquals(0L, vo.getLlmCallCount());
        assertEquals(0, vo.getLlmTotalCost().compareTo(BigDecimal.ZERO));
        assertEquals(0.0, vo.getLlmCacheHitRate());
        assertEquals(0L, vo.getHumanTakeoverQueueSize());
        assertTrue(vo.getRiskLevelDistribution().isEmpty());
    }

    // endregion

    // region getTrends 趋势日期合并

    @Test
    @DisplayName("趋势 - 任务量与成本按日期合并，缺失维度补零")
    void getTrends_MergesByDate() {
        LocalDate startDate = LocalDate.now().minusDays(6);
        when(dashboardStatsMapper.selectTaskTrend(ORG_ID, startDate)).thenReturn(List.of(
                buildTrendTask("2026-08-01", 10L, 8L, 2L),
                buildTrendTask("2026-08-02", 5L, 5L, 0L)
        ));
        when(dashboardStatsMapper.selectCostTrend(ORG_ID, startDate)).thenReturn(List.of(
                buildTrendCost("2026-08-02", new BigDecimal("0.50")),
                buildTrendCost("2026-08-03", new BigDecimal("0.30"))
        ));

        mockBucketMiss();

        TrendsVO vo = dashboardService.getTrends(ORG_ID, 7);

        // 3 个日期点，按升序
        assertEquals(3, vo.getPoints().size());
        assertEquals("2026-08-01", vo.getPoints().get(0).getDate());
        assertEquals(10L, vo.getPoints().get(0).getTaskCount());
        // 08-01 无成本数据 → 补零
        assertEquals(0, vo.getPoints().get(0).getCost().compareTo(BigDecimal.ZERO));

        // 08-02 同时有任务和成本
        assertEquals("2026-08-02", vo.getPoints().get(1).getDate());
        assertEquals(5L, vo.getPoints().get(1).getTaskCount());
        assertEquals(0, vo.getPoints().get(1).getCost().compareTo(new BigDecimal("0.50")));

        // 08-03 仅成本，任务数补零
        assertEquals("2026-08-03", vo.getPoints().get(2).getDate());
        assertEquals(0L, vo.getPoints().get(2).getTaskCount());
        assertEquals(0, vo.getPoints().get(2).getCost().compareTo(new BigDecimal("0.30")));
    }

    @Test
    @DisplayName("趋势 - days 为 null 时取默认 7 天")
    void getTrends_NullDays_UsesDefault() {
        when(dashboardStatsMapper.selectTaskTrend(eq(ORG_ID), any(LocalDate.class))).thenReturn(List.of());
        when(dashboardStatsMapper.selectCostTrend(eq(ORG_ID), any(LocalDate.class))).thenReturn(List.of());

        mockBucketMiss();

        TrendsVO vo = dashboardService.getTrends(ORG_ID, null);

        assertNotNull(vo);
        assertTrue(vo.getPoints().isEmpty());
        // 验证 startDate = today - 6（默认 7 天）
        verify(dashboardStatsMapper).selectTaskTrend(eq(ORG_ID), eq(LocalDate.now().minusDays(6)));
    }

    // endregion

    // region getBusinessLineStats 成功率计算

    @Test
    @DisplayName("业务线分布 - 成功率计算正确")
    void getBusinessLineStats_SuccessRateCalculated() {
        BusinessLineStatVO bl1 = new BusinessLineStatVO();
        bl1.setBusinessLineId(1L);
        bl1.setBusinessLineName("银行流水");
        bl1.setTaskCount(10L);
        bl1.setSuccessCount(8L);
        BusinessLineStatVO bl2 = new BusinessLineStatVO();
        bl2.setBusinessLineId(2L);
        bl2.setBusinessLineName("跨行转账");
        bl2.setTaskCount(0L);
        bl2.setSuccessCount(0L);
        when(dashboardStatsMapper.selectBusinessLineStats(ORG_ID)).thenReturn(List.of(bl1, bl2));

        mockBucketMiss();

        List<BusinessLineStatVO> list = dashboardService.getBusinessLineStats(ORG_ID);

        assertEquals(0.8, list.get(0).getSuccessRate(), 0.0001);
        // taskCount=0 时成功率 0（避免除零）
        assertEquals(0.0, list.get(1).getSuccessRate(), 0.0001);
    }

    // endregion

    // region getCosts 成本汇总

    @Test
    @DisplayName("成本统计 - 总 token 从模型维度汇总")
    void getCosts_TotalTokensAggregated() {
        CostStatVO.ModelCostStatVO m1 = new CostStatVO.ModelCostStatVO();
        m1.setModel("gpt-4o-mini");
        m1.setCalls(50L);
        m1.setCost(new BigDecimal("0.50"));
        m1.setTokens(5000L);
        CostStatVO.ModelCostStatVO m2 = new CostStatVO.ModelCostStatVO();
        m2.setModel("gpt-4o");
        m2.setCalls(10L);
        m2.setCost(new BigDecimal("1.20"));
        m2.setTokens(3000L);
        when(dashboardStatsMapper.selectModelCostStats(ORG_ID)).thenReturn(List.of(m1, m2));

        LlmAggregateDTO llm = new LlmAggregateDTO();
        llm.setCallCount(60L);
        llm.setTotalCost(new BigDecimal("1.70"));
        llm.setCacheHitCount(15L);
        when(dashboardStatsMapper.selectLlmAggregate(ORG_ID)).thenReturn(llm);

        mockBucketMiss();

        CostStatVO vo = dashboardService.getCosts(ORG_ID);

        assertEquals(60L, vo.getTotalCalls());
        assertEquals(0, vo.getTotalCost().compareTo(new BigDecimal("1.70")));
        // 5000 + 3000 = 8000
        assertEquals(8000L, vo.getTotalTokens());
        assertEquals(0.25, vo.getCacheHitRate(), 0.0001);
        assertEquals(2, vo.getModelCosts().size());
    }

    // endregion

    // region getApprovals null 归零

    @Test
    @DisplayName("审批统计 - null 字段归零")
    void getApprovals_NullFields_NormalizedToZero() {
        ApprovalStatVO raw = new ApprovalStatVO();
        raw.setTotalApprovals(5L);
        // 其他字段为 null
        when(dashboardStatsMapper.selectApprovalStat(ORG_ID)).thenReturn(raw);

        mockBucketMiss();

        ApprovalStatVO vo = dashboardService.getApprovals(ORG_ID);

        assertEquals(5L, vo.getTotalApprovals());
        assertEquals(0L, vo.getApprovedCount());
        assertEquals(0L, vo.getRejectedCount());
        assertEquals(0L, vo.getTimeoutCount());
        assertEquals(0L, vo.getPendingCount());
    }

    @Test
    @DisplayName("审批统计 - Mapper 返回 null 时返回全零对象")
    void getApprovals_NullResult_ReturnsZeros() {
        when(dashboardStatsMapper.selectApprovalStat(ORG_ID)).thenReturn(null);

        mockBucketMiss();

        ApprovalStatVO vo = dashboardService.getApprovals(ORG_ID);

        assertEquals(0L, vo.getTotalApprovals());
        assertEquals(0L, vo.getApprovedCount());
        assertEquals(0L, vo.getTimeoutCount());
    }

    // endregion

    // region invalidateCache

    @Test
    @DisplayName("缓存失效 - 调用 deleteByPattern")
    void invalidateCache_DeletesByPattern() {
        RKeys keys = mock(RKeys.class);
        when(redissonClient.getKeys()).thenReturn(keys);
        when(keys.deleteByPattern(anyString())).thenReturn(3L);

        dashboardService.invalidateCache(ORG_ID);

        verify(keys, times(1)).deleteByPattern(eq("dashboard:1001:*"));
    }

    @Test
    @DisplayName("缓存失效 - orgId 为 null 时跳过")
    void invalidateCache_NullOrgId_Skips() {
        dashboardService.invalidateCache(null);

        verifyNoInteractions(redissonClient);
    }

    // endregion

    // region getOverview 环比趋势（今日 vs 昨日，对齐原型 KPI 卡片 trend）

    @Test
    @DisplayName("概览环比 - 任务量/成功率/LLM 成本环比计算正确")
    void getOverview_GrowthRates_Calculated() {
        // 1. 全量指标 mock（不为空以触发完整流程）
        when(dashboardStatsMapper.countTaskByStatus(ORG_ID)).thenReturn(List.of(
                new TaskStatusCountDTO() {{ setStatus("SUCCESS"); setCount(80L); }},
                new TaskStatusCountDTO() {{ setStatus("FAILED"); setCount(20L); }}
        ));
        when(dashboardStatsMapper.selectTaskDurationStat(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.selectLlmAggregate(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.selectHumanTakeoverAggregate(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.countRiskLevel(ORG_ID)).thenReturn(List.of());

        // 2. 今日：100 总，90 成功；昨日：80 总，64 成功
        when(dashboardStatsMapper.countTaskByStatusInRange(eq(ORG_ID), eq(LocalDate.now()), eq(LocalDate.now().plusDays(1))))
                .thenReturn(List.of(
                        new TaskStatusCountDTO() {{ setStatus("SUCCESS"); setCount(90L); }},
                        new TaskStatusCountDTO() {{ setStatus("FAILED"); setCount(10L); }}
                ));
        when(dashboardStatsMapper.countTaskByStatusInRange(eq(ORG_ID), eq(LocalDate.now().minusDays(1)), eq(LocalDate.now())))
                .thenReturn(List.of(
                        new TaskStatusCountDTO() {{ setStatus("SUCCESS"); setCount(64L); }},
                        new TaskStatusCountDTO() {{ setStatus("FAILED"); setCount(16L); }}
                ));

        // 3. 今日 LLM 成本 200，昨日 250 → -20%
        LlmAggregateDTO todayLlm = new LlmAggregateDTO();
        todayLlm.setTotalCost(new BigDecimal("200.00"));
        when(dashboardStatsMapper.selectLlmAggregateInRange(eq(ORG_ID), eq(LocalDate.now()), eq(LocalDate.now().plusDays(1))))
                .thenReturn(todayLlm);
        LlmAggregateDTO yesterdayLlm = new LlmAggregateDTO();
        yesterdayLlm.setTotalCost(new BigDecimal("250.00"));
        when(dashboardStatsMapper.selectLlmAggregateInRange(eq(ORG_ID), eq(LocalDate.now().minusDays(1)), eq(LocalDate.now())))
                .thenReturn(yesterdayLlm);

        mockBucketMiss();

        OverviewVO vo = dashboardService.getOverview(ORG_ID);

        // 任务总数环比 +25%：(100 - 80) / 80 = 0.25
        assertEquals(0.25, vo.getTaskGrowthRate(), 0.0001);
        // 成功率差值：今日 0.9 - 昨日 0.8 = 0.1（百分点）
        assertEquals(0.1, vo.getSuccessRateDelta(), 0.0001);
        // LLM 成本环比 -20%：(200 - 250) / 250 = -0.2
        assertEquals(-0.20, vo.getLlmCostDelta(), 0.0001);
    }

    @Test
    @DisplayName("概览环比 - 上期数据为 0 时返回 null")
    void getOverview_GrowthRates_NullWhenYesterdayZero() {
        when(dashboardStatsMapper.countTaskByStatus(ORG_ID)).thenReturn(List.of());
        when(dashboardStatsMapper.selectTaskDurationStat(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.selectLlmAggregate(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.selectHumanTakeoverAggregate(ORG_ID)).thenReturn(null);
        when(dashboardStatsMapper.countRiskLevel(ORG_ID)).thenReturn(List.of());

        // 今日 5 条，昨日 0 条 → 上期为 0，所有环比字段应为 null
        when(dashboardStatsMapper.countTaskByStatusInRange(eq(ORG_ID), eq(LocalDate.now()), eq(LocalDate.now().plusDays(1))))
                .thenReturn(List.of(new TaskStatusCountDTO() {{ setStatus("SUCCESS"); setCount(5L); }}));
        when(dashboardStatsMapper.countTaskByStatusInRange(eq(ORG_ID), eq(LocalDate.now().minusDays(1)), eq(LocalDate.now())))
                .thenReturn(List.of());

        // 今日 LLM 成本 100，昨日 0 → llmCostDelta 为 null
        LlmAggregateDTO todayLlm = new LlmAggregateDTO();
        todayLlm.setTotalCost(new BigDecimal("100.00"));
        when(dashboardStatsMapper.selectLlmAggregateInRange(eq(ORG_ID), eq(LocalDate.now()), eq(LocalDate.now().plusDays(1))))
                .thenReturn(todayLlm);
        LlmAggregateDTO yesterdayLlm = new LlmAggregateDTO();
        yesterdayLlm.setTotalCost(BigDecimal.ZERO);
        when(dashboardStatsMapper.selectLlmAggregateInRange(eq(ORG_ID), eq(LocalDate.now().minusDays(1)), eq(LocalDate.now())))
                .thenReturn(yesterdayLlm);

        mockBucketMiss();

        OverviewVO vo = dashboardService.getOverview(ORG_ID);

        assertNull(vo.getTaskGrowthRate());
        assertNull(vo.getSuccessRateDelta());
        assertNull(vo.getLlmCostDelta());
    }

    // endregion

    // region 辅助方法

    /**
     * mock 缓存未命中的 RBucket（get 返回 null）
     */
    @SuppressWarnings("unchecked")
    private RBucket<String> mockBucketMiss() {
        RBucket<String> bucket = mock(RBucket.class);
        when(bucket.get()).thenReturn(null);
        doReturn(bucket).when(redissonClient).getBucket(anyString(), any());
        return bucket;
    }

    /**
     * 构建任务量趋势 DTO
     */
    private TrendTaskDTO buildTrendTask(String date, Long total, Long success, Long failed) {
        TrendTaskDTO dto = new TrendTaskDTO();
        dto.setDate(date);
        dto.setTaskCount(total);
        dto.setSuccessCount(success);
        dto.setFailedCount(failed);
        return dto;
    }

    /**
     * 构建成本趋势 DTO
     */
    private TrendCostDTO buildTrendCost(String date, BigDecimal cost) {
        TrendCostDTO dto = new TrendCostDTO();
        dto.setDate(date);
        dto.setCost(cost);
        return dto;
    }

    // endregion
}

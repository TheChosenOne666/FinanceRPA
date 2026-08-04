package com.finrpa.llm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.mapper.AgentTaskMapper;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.llm.constant.LlmConstant;
import com.finrpa.llm.dto.request.LlmCallLogCreateRequest;
import com.finrpa.llm.dto.request.LlmCallRecordQueryRequest;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
import com.finrpa.llm.dto.response.LlmCallDailyTrendVO;
import com.finrpa.llm.dto.response.LlmCallRecordVO;
import com.finrpa.llm.dto.response.LlmCallStatsVO;
import com.finrpa.llm.dto.response.ModelStatsVO;
import com.finrpa.llm.entity.LlmCallLogEO;
import com.finrpa.llm.mapper.LlmCallLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LLM 调用记录服务实现单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class LlmCallLogServiceImplTest {

    /** 测试用任务 ID */
    private static final String TEST_TASK_ID = "2082333099000000099";

    /** 测试用组织 ID */
    private static final String TEST_ORG_ID = "2082342545947660289";

    /** 测试用业务线 ID */
    private static final Long TEST_BIZ_LINE_ID = 2082380000000000003L;

    @Mock
    private LlmCallLogMapper llmCallLogMapper;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @InjectMocks
    private LlmCallLogServiceImpl llmCallLogService;

    // region createCallLog 成功场景

    @Test
    @DisplayName("创建调用记录 - 成功（完整参数，含业务线）")
    void createCallLog_SuccessWithFullParams() {
        // 1. 构建请求
        LlmCallLogCreateRequest request = buildFullRequest();

        // 2. mock
        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenReturn(1);

        // 3. 执行
        boolean result = llmCallLogService.createCallLog(request);

        // 4. 验证
        assertTrue(result);
        ArgumentCaptor<LlmCallLogEO> captor = ArgumentCaptor.forClass(LlmCallLogEO.class);
        verify(llmCallLogMapper, times(1)).insert(captor.capture());

        LlmCallLogEO eo = captor.getValue();
        assertEquals(Long.parseLong(TEST_TASK_ID), eo.getTaskId());
        assertEquals(Long.parseLong(TEST_ORG_ID), eo.getOrgId());
        assertEquals(TEST_BIZ_LINE_ID, eo.getBusinessLineId());
        assertEquals("gpt-4o", eo.getModel());
        assertEquals("planner", eo.getContextName());
        assertEquals(0, eo.getRetryAttempt());
        assertTrue(eo.getSuccess());
        assertEquals(1500, eo.getDurationMs());
        assertEquals(500, eo.getPromptTokens());
        assertEquals(1000, eo.getCompletionTokens());
        assertEquals(1500, eo.getTotalTokens());
        assertFalse(eo.getCacheHit());
        assertNotNull(eo.getCallTime());
        // 成本 = 500 * 2.50 / 1M + 1000 * 10.00 / 1M = 0.00125 + 0.01 = 0.01125
        assertEquals(new BigDecimal("0.011250"), eo.getCost());
    }

    @Test
    @DisplayName("创建调用记录 - contextName 为空时使用默认值")
    void createCallLog_DefaultContextName() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setContextName("");
        request.setSuccess(true);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertEquals(LlmConstant.DEFAULT_CONTEXT, eo.getContextName());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - success 为 null 时默认 false")
    void createCallLog_NullSuccess_DefaultsFalse() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setSuccess(null);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertFalse(eo.getSuccess());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - cacheHit 为 null 时默认 false")
    void createCallLog_NullCacheHit_DefaultsFalse() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setCacheHit(null);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertFalse(eo.getCacheHit());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - taskId / orgId 为空时解析为 null")
    void createCallLog_NullTaskIdOrgId_ParsesToNull() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setTaskId(null);
        request.setOrgId("");
        request.setSuccess(true);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertNull(eo.getTaskId());
            assertNull(eo.getOrgId());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - timestamp 为空时 callTime 为 null")
    void createCallLog_EmptyTimestamp_CallTimeNull() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setTimestamp("");
        request.setSuccess(true);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertNull(eo.getCallTime());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - timestamp 格式错误时 callTime 为 null")
    void createCallLog_InvalidTimestamp_CallTimeNull() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setTimestamp("not-a-timestamp");
        request.setSuccess(true);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertNull(eo.getCallTime());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - 未知模型成本为 0")
    void createCallLog_UnknownModel_CostZero() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("unknown-model");
        request.setPromptTokens(1000);
        request.setCompletionTokens(2000);
        request.setSuccess(true);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertEquals(BigDecimal.ZERO.setScale(6), eo.getCost());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - gpt-4o-mini 成本计算正确")
    void createCallLog_MiniModel_CostCorrect() {
        // gpt-4o-mini: input $0.15/1M, output $0.60/1M
        // 1000 prompt + 500 completion = 0.00015 + 0.0003 = 0.00045
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setPromptTokens(1000);
        request.setCompletionTokens(500);
        request.setSuccess(true);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertEquals(new BigDecimal("0.000450"), eo.getCost());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    @Test
    @DisplayName("创建调用记录 - 缓存命中时 token 为 null，成本为 0")
    void createCallLog_CacheHit_CostZero() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o");
        request.setCacheHit(true);
        request.setSuccess(true);
        request.setPromptTokens(null);
        request.setCompletionTokens(null);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenAnswer(invocation -> {
            LlmCallLogEO eo = invocation.getArgument(0);
            assertTrue(eo.getCacheHit());
            assertEquals(BigDecimal.ZERO.setScale(6), eo.getCost());
            return 1;
        });

        assertTrue(llmCallLogService.createCallLog(request));
    }

    // endregion

    // region createCallLog 参数校验

    @Test
    @DisplayName("创建调用记录 - 请求为空抛异常")
    void createCallLog_NullRequest_ThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmCallLogService.createCallLog(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建调用记录 - model 为空抛异常")
    void createCallLog_BlankModel_ThrowsException() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmCallLogService.createCallLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建调用记录 - model 为 null 抛异常")
    void createCallLog_NullModel_ThrowsException() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmCallLogService.createCallLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // endregion

    // region createCallLog 数据库异常

    @Test
    @DisplayName("创建调用记录 - insert 失败抛异常")
    void createCallLog_InsertFailed_ThrowsException() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setSuccess(true);

        when(llmCallLogMapper.insert(any(LlmCallLogEO.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmCallLogService.createCallLog(request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    // endregion

    // region getStats 统计查询

    @Test
    @DisplayName("查询统计 - 空结果返回零值")
    void getStats_EmptyResult_ReturnsZeros() {
        when(llmCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        LlmCallStatsVO stats = llmCallLogService.getStats(new LlmCallStatsQueryRequest(), 1L);

        assertEquals(0L, stats.getTotalCalls());
        assertEquals(0L, stats.getSuccessCalls());
        assertEquals(0L, stats.getFailedCalls());
        assertEquals(0L, stats.getCacheHitCalls());
        assertEquals(0.0, stats.getCacheHitRate());
        assertEquals(0L, stats.getTotalTokens());
        assertEquals(BigDecimal.ZERO, stats.getTotalCost());
        assertEquals(0.0, stats.getAvgDurationMs());
        assertTrue(stats.getModelStats().isEmpty());
    }

    @Test
    @DisplayName("查询统计 - 单模型多调用正确聚合")
    void getStats_SingleModel_MultipleCalls() {
        List<LlmCallLogEO> logs = Arrays.asList(
                buildLogEO("gpt-4o-mini", true, 500, 1000, 1500, 100, new BigDecimal("0.000450"), false),
                buildLogEO("gpt-4o-mini", true, 600, 1200, 1800, 200, new BigDecimal("0.000540"), true),
                buildLogEO("gpt-4o-mini", false, 400, 0, 400, 300, BigDecimal.ZERO, false)
        );
        when(llmCallLogMapper.selectList(any())).thenReturn(logs);

        LlmCallStatsVO stats = llmCallLogService.getStats(null, 1L);

        assertEquals(3L, stats.getTotalCalls());
        assertEquals(2L, stats.getSuccessCalls());
        assertEquals(1L, stats.getFailedCalls());
        assertEquals(1L, stats.getCacheHitCalls());
        assertEquals(0.3333, stats.getCacheHitRate(), 0.0001);
        assertEquals(1500L, stats.getTotalPromptTokens());
        assertEquals(2200L, stats.getTotalCompletionTokens());
        assertEquals(3700L, stats.getTotalTokens());
        assertEquals(0, stats.getTotalCost().compareTo(new BigDecimal("0.000990")));
        assertEquals(200.0, stats.getAvgDurationMs(), 0.01);
        assertEquals(1, stats.getModelStats().size());

        ModelStatsVO modelStat = stats.getModelStats().get(0);
        assertEquals("gpt-4o-mini", modelStat.getModel());
        assertEquals(3L, modelStat.getCalls());
        assertEquals(2L, modelStat.getSuccessCalls());
        assertEquals(3700L, modelStat.getTotalTokens());
    }

    @Test
    @DisplayName("查询统计 - 多模型正确分组")
    void getStats_MultipleModels_GroupedCorrectly() {
        List<LlmCallLogEO> logs = Arrays.asList(
                buildLogEO("gpt-4o-mini", true, 100, 200, 300, 50, new BigDecimal("0.000015"), false),
                buildLogEO("gpt-4o", true, 500, 1000, 1500, 200, new BigDecimal("0.011250"), false),
                buildLogEO("gpt-4o-2024-08-06", true, 800, 1600, 2400, 300, new BigDecimal("0.018000"), false)
        );
        when(llmCallLogMapper.selectList(any())).thenReturn(logs);

        LlmCallStatsVO stats = llmCallLogService.getStats(null, 1L);

        assertEquals(3L, stats.getTotalCalls());
        assertEquals(3, stats.getModelStats().size());

        // 验证每个模型的统计
        ModelStatsVO miniStat = stats.getModelStats().stream()
                .filter(s -> "gpt-4o-mini".equals(s.getModel())).findFirst().orElse(null);
        assertNotNull(miniStat);
        assertEquals(1L, miniStat.getCalls());
        assertEquals(300L, miniStat.getTotalTokens());

        ModelStatsVO standardStat = stats.getModelStats().stream()
                .filter(s -> "gpt-4o".equals(s.getModel())).findFirst().orElse(null);
        assertNotNull(standardStat);
        assertEquals(1L, standardStat.getCalls());
        assertEquals(1500L, standardStat.getTotalTokens());
    }

    @Test
    @DisplayName("查询统计 - orgId 为 null 时不添加 org 过滤")
    void getStats_NullOrgId_NoOrgFilter() {
        when(llmCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        LlmCallStatsVO stats = llmCallLogService.getStats(null, null);

        assertNotNull(stats);
        verify(llmCallLogMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("查询统计 - queryRequest 为 null 不报错")
    void getStats_NullQueryRequest_NoError() {
        when(llmCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        LlmCallStatsVO stats = llmCallLogService.getStats(null, 1L);

        assertNotNull(stats);
        assertEquals(0L, stats.getTotalCalls());
    }

    @Test
    @DisplayName("查询统计 - 提供完整时间范围时计算环比趋势")
    void getStats_WithTimeRange_CalculatesTrend() {
        // 当前周期 100 调用，上一周期 50 调用 → 趋势 +100%
        LlmCallLogEO currentLog = buildLogEO("gpt-4o-mini", true, 100, 200, 300, 100,
                new BigDecimal("0.000015"), false);
        LlmCallLogEO prevLog = buildLogEO("gpt-4o-mini", true, 50, 100, 150, 80,
                new BigDecimal("0.0000075"), false);

        // 第一次调用返回当前周期，第二次返回上一周期
        when(llmCallLogMapper.selectList(any()))
                .thenReturn(Collections.singletonList(currentLog))
                .thenReturn(Collections.singletonList(prevLog));

        LlmCallStatsQueryRequest queryRequest = new LlmCallStatsQueryRequest();
        queryRequest.setStartTime(Timestamp.valueOf(LocalDateTime.of(2026, 8, 1, 0, 0)));
        queryRequest.setEndTime(Timestamp.valueOf(LocalDateTime.of(2026, 8, 2, 0, 0)));

        LlmCallStatsVO stats = llmCallLogService.getStats(queryRequest, 1L);

        assertEquals(1L, stats.getTotalCalls());
        assertNotNull(stats.getTotalCallsTrendPct());
        // 上一周期 1 → 当前 1，变化 0%
        assertEquals(0.0, stats.getTotalCallsTrendPct(), 0.01);
        verify(llmCallLogMapper, times(2)).selectList(any());
    }

    @Test
    @DisplayName("查询统计 - 业务线筛选透传到 wrapper")
    void getStats_WithBusinessLine_FiltersCorrectly() {
        when(llmCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        LlmCallStatsQueryRequest queryRequest = new LlmCallStatsQueryRequest();
        queryRequest.setBusinessLineId(TEST_BIZ_LINE_ID);

        llmCallLogService.getStats(queryRequest, 1L);

        verify(llmCallLogMapper, times(1)).selectList(any());
    }

    // endregion

    // region listCallRecords 调用记录分页

    @Test
    @DisplayName("调用记录分页 - 空结果返回空列表")
    void listCallRecords_EmptyResult() {
        LlmCallRecordQueryRequest queryRequest = new LlmCallRecordQueryRequest();

        Page<LlmCallLogEO> emptyPage = new Page<>(1, 10);
        emptyPage.setTotal(0);
        emptyPage.setRecords(Collections.emptyList());
        when(llmCallLogMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage);

        IPage<LlmCallRecordVO> result = llmCallLogService.listCallRecords(queryRequest, 1L);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("调用记录分页 - 含任务标题关联")
    void listCallRecords_WithTaskTitle() {
        LlmCallLogEO log = buildLogEO("gpt-4o", true, 100, 200, 300, 100,
                new BigDecimal("0.001"), false);
        log.setCallId(2082400000000000001L);
        log.setTaskId(2082400000000000002L);

        Page<LlmCallLogEO> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(log));
        when(llmCallLogMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // mock 任务标题查询
        AgentTaskEO task = new AgentTaskEO();
        task.setTaskId(2082400000000000002L);
        task.setGoal("下载银行流水");
        when(agentTaskMapper.selectList(any())).thenReturn(Collections.singletonList(task));

        LlmCallRecordQueryRequest queryRequest = new LlmCallRecordQueryRequest();
        IPage<LlmCallRecordVO> result = llmCallLogService.listCallRecords(queryRequest, 1L);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        LlmCallRecordVO vo = result.getRecords().get(0);
        assertEquals("gpt-4o", vo.getModel());
        assertEquals("下载银行流水", vo.getTaskTitle());
        assertTrue(vo.getSuccess());
    }

    @Test
    @DisplayName("调用记录分页 - 请求为空抛异常")
    void listCallRecords_NullRequest_ThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmCallLogService.listCallRecords(null, 1L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("调用记录分页 - pageSize 超过 100 抛异常")
    void listCallRecords_PageSizeOver100_ThrowsException() {
        LlmCallRecordQueryRequest queryRequest = new LlmCallRecordQueryRequest();
        queryRequest.setPageSize(101);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmCallLogService.listCallRecords(queryRequest, 1L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // endregion

    // region getDailyTrend 按日趋势

    @Test
    @DisplayName("按日趋势 - 默认最近 7 天，含无数据日填充零值")
    void getDailyTrend_DefaultRange_FillsEmptyDays() {
        when(llmCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<LlmCallDailyTrendVO> trend = llmCallLogService.getDailyTrend(null, 1L);

        assertNotNull(trend);
        assertEquals(7, trend.size());
        // 每一天都有零值
        for (LlmCallDailyTrendVO day : trend) {
            assertEquals(0L, day.getCalls());
            assertEquals(BigDecimal.ZERO, day.getCost());
        }
    }

    @Test
    @DisplayName("按日趋势 - 提供时间范围时按日聚合")
    void getDailyTrend_WithTimeRange_AggregatesByDay() {
        // 2 天范围，第 1 天 2 条记录，第 2 天 1 条记录
        Timestamp start = Timestamp.valueOf(LocalDateTime.of(2026, 8, 1, 0, 0));
        Timestamp end = Timestamp.valueOf(LocalDateTime.of(2026, 8, 2, 23, 59));

        LlmCallLogEO log1 = buildLogEO("gpt-4o", true, 100, 200, 300, 100,
                new BigDecimal("0.001"), false);
        log1.setCallTime(Timestamp.valueOf(LocalDateTime.of(2026, 8, 1, 10, 0)));
        LlmCallLogEO log2 = buildLogEO("gpt-4o", true, 100, 200, 300, 200,
                new BigDecimal("0.002"), false);
        log2.setCallTime(Timestamp.valueOf(LocalDateTime.of(2026, 8, 1, 15, 0)));
        LlmCallLogEO log3 = buildLogEO("gpt-4o", true, 100, 200, 300, 300,
                new BigDecimal("0.003"), false);
        log3.setCallTime(Timestamp.valueOf(LocalDateTime.of(2026, 8, 2, 11, 0)));

        when(llmCallLogMapper.selectList(any())).thenReturn(Arrays.asList(log1, log2, log3));

        LlmCallStatsQueryRequest queryRequest = new LlmCallStatsQueryRequest();
        queryRequest.setStartTime(start);
        queryRequest.setEndTime(end);

        List<LlmCallDailyTrendVO> trend = llmCallLogService.getDailyTrend(queryRequest, 1L);

        assertEquals(2, trend.size());
        assertEquals("2026-08-01", trend.get(0).getDate());
        assertEquals(2L, trend.get(0).getCalls());
        assertEquals(0, trend.get(0).getCost().compareTo(new BigDecimal("0.003")));
        assertEquals("2026-08-02", trend.get(1).getDate());
        assertEquals(1L, trend.get(1).getCalls());
    }

    // endregion

    // region 辅助方法

    /**
     * 构建完整参数的创建请求（含业务线）
     */
    private LlmCallLogCreateRequest buildFullRequest() {
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setBusinessLineId(String.valueOf(TEST_BIZ_LINE_ID));
        request.setModel("gpt-4o");
        request.setContextName("planner");
        request.setRetryAttempt(0);
        request.setSuccess(true);
        request.setDurationMs(1500);
        request.setPromptTokens(500);
        request.setCompletionTokens(1000);
        request.setTotalTokens(1500);
        request.setCacheHit(false);
        request.setTimestamp("2026-08-01T12:34:56.789012");
        return request;
    }

    /**
     * 构建 LlmCallLogEO 测试对象
     */
    private LlmCallLogEO buildLogEO(String model, boolean success, int promptTokens,
                                     int completionTokens, int totalTokens, int durationMs,
                                     BigDecimal cost, boolean cacheHit) {
        LlmCallLogEO eo = new LlmCallLogEO();
        eo.setModel(model);
        eo.setSuccess(success);
        eo.setPromptTokens(promptTokens);
        eo.setCompletionTokens(completionTokens);
        eo.setTotalTokens(totalTokens);
        eo.setDurationMs(durationMs);
        eo.setCost(cost);
        eo.setCacheHit(cacheHit);
        eo.setCallTime(new Timestamp(System.currentTimeMillis()));
        return eo;
    }

    // endregion
}

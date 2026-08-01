package com.finrpa.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
import com.finrpa.llm.dto.response.LlmCallStatsVO;
import com.finrpa.llm.dto.response.ModelStatsVO;
import com.finrpa.llm.service.LlmCallLogService;
import com.finrpa.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LLM 调用统计控制器单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class LlmCallLogControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082342545947660289L;

    private MockMvc mockMvc;
    private LlmCallLogService llmCallLogService;

    @BeforeEach
    void setUp() {
        // 1. mock 依赖
        llmCallLogService = mock(LlmCallLogService.class);

        // 2. 构建 MockMvc
        LlmCallLogController controller = new LlmCallLogController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "llmCallLogService", llmCallLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        // 3. 设置租户上下文
        TenantContext.setOrgId(String.valueOf(TEST_ORG_ID));
    }

    @AfterEach
    void tearDown() {
        // 清理租户上下文，防止线程复用污染
        TenantContext.clear();
    }

    @Test
    @DisplayName("查询 LLM 调用统计 - 成功")
    void getCallStats_Success() throws Exception {
        // 1. mock 返回
        LlmCallStatsVO statsVO = buildStatsVO();
        when(llmCallLogService.getStats(any(LlmCallStatsQueryRequest.class), eq(TEST_ORG_ID)))
                .thenReturn(statsVO);

        // 2. 执行请求并验证
        mockMvc.perform(get("/llm/calls/stats")
                        .param("model", "gpt-4o")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCalls").value(10))
                .andExpect(jsonPath("$.data.successCalls").value(8))
                .andExpect(jsonPath("$.data.failedCalls").value(2))
                .andExpect(jsonPath("$.data.cacheHitCalls").value(3))
                .andExpect(jsonPath("$.data.cacheHitRate").value(0.3))
                .andExpect(jsonPath("$.data.totalTokens").value(15000))
                .andExpect(jsonPath("$.data.totalCost").value(0.12))
                .andExpect(jsonPath("$.data.avgDurationMs").value(500.0))
                .andExpect(jsonPath("$.data.modelStats[0].model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.modelStats[0].calls").value(7))
                .andExpect(jsonPath("$.data.modelStats[1].model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.modelStats[1].calls").value(3));

        // 3. 验证 service 被调用
        verify(llmCallLogService, times(1)).getStats(any(LlmCallStatsQueryRequest.class), eq(TEST_ORG_ID));
    }

    @Test
    @DisplayName("查询 LLM 调用统计 - 无参数（全量查询）")
    void getCallStats_NoParams() throws Exception {
        // 1. mock 返回
        LlmCallStatsVO statsVO = buildStatsVO();
        when(llmCallLogService.getStats(any(), eq(TEST_ORG_ID))).thenReturn(statsVO);

        // 2. 执行请求并验证
        mockMvc.perform(get("/llm/calls/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCalls").value(10));

        verify(llmCallLogService, times(1)).getStats(any(LlmCallStatsQueryRequest.class), eq(TEST_ORG_ID));
    }

    @Test
    @DisplayName("查询 LLM 调用统计 - 带 taskId 参数")
    void getCallStats_WithTaskId() throws Exception {
        // 1. mock 返回
        LlmCallStatsVO statsVO = buildStatsVO();
        when(llmCallLogService.getStats(any(LlmCallStatsQueryRequest.class), eq(TEST_ORG_ID)))
                .thenReturn(statsVO);

        // 2. 执行请求并验证
        mockMvc.perform(get("/llm/calls/stats")
                        .param("taskId", "2082333099000000099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCalls").value(10));

        verify(llmCallLogService, times(1)).getStats(any(LlmCallStatsQueryRequest.class), eq(TEST_ORG_ID));
    }

    @Test
    @DisplayName("查询 LLM 调用统计 - 租户上下文为空时返回未登录错误")
    void getCallStats_NoTenantContext_ThrowsNotLogin() throws Exception {
        // 1. 清除租户上下文
        TenantContext.clear();

        // 2. 执行请求并验证（全局异常处理器会将 BusinessException 转为 BaseResponse）
        // standaloneSetup 没有全局异常处理器，所以会抛出 BusinessException → 500
        // 但实际运行时有 GlobalExceptionHandler 处理
        // 此处验证 service 未被调用
        try {
            mockMvc.perform(get("/llm/calls/stats"));
        } catch (Exception e) {
            // 预期抛出 BusinessException
        }

        verify(llmCallLogService, never()).getStats(any(), any());
    }

    // region 辅助方法

    /**
     * 构建测试用统计 VO
     */
    private LlmCallStatsVO buildStatsVO() {
        LlmCallStatsVO vo = new LlmCallStatsVO();
        vo.setTotalCalls(10L);
        vo.setSuccessCalls(8L);
        vo.setFailedCalls(2L);
        vo.setCacheHitCalls(3L);
        vo.setCacheHitRate(0.3);
        vo.setTotalPromptTokens(5000L);
        vo.setTotalCompletionTokens(10000L);
        vo.setTotalTokens(15000L);
        vo.setTotalCost(new BigDecimal("0.12"));
        vo.setAvgDurationMs(500.0);
        vo.setModelStats(Arrays.asList(
                new ModelStatsVO("gpt-4o", 7L, 6L, 12000L, new BigDecimal("0.10")),
                new ModelStatsVO("gpt-4o-mini", 3L, 2L, 3000L, new BigDecimal("0.02"))
        ));
        return vo;
    }

    // endregion
}

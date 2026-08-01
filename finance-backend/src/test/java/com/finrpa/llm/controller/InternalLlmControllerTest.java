package com.finrpa.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.llm.dto.request.LlmCallLogCreateRequest;
import com.finrpa.llm.service.LlmCallLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LLM 内部回调控制器单元测试
 *
 * <p>验证 Python 回调端点的请求转发与响应格式。
 * 鉴权拦截器在集成测试中验证，此处 standaloneSetup 不注册拦截器。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class InternalLlmControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private LlmCallLogService llmCallLogService;

    @BeforeEach
    void setUp() {
        // 1. mock 依赖
        llmCallLogService = mock(LlmCallLogService.class);

        // 2. 构建 MockMvc（standalone，不走拦截器）
        InternalLlmController controller = new InternalLlmController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "llmCallLogService", llmCallLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("上报 LLM 调用记录 - 成功")
    void createCallLog_Success() throws Exception {
        // 1. 构建请求
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setTaskId("2082333099000000099");
        request.setOrgId("2082342545947660289");
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

        // 2. mock
        when(llmCallLogService.createCallLog(any(LlmCallLogCreateRequest.class))).thenReturn(true);

        // 3. 执行请求并验证
        mockMvc.perform(post("/internal/llm/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        // 4. 验证 service 被调用
        verify(llmCallLogService, times(1)).createCallLog(any(LlmCallLogCreateRequest.class));
    }

    @Test
    @DisplayName("上报 LLM 调用记录 - 缓存命中场景")
    void createCallLog_CacheHit() throws Exception {
        // 1. 构建请求（缓存命中，token 为 null）
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setTaskId("2082333099000000099");
        request.setModel("gpt-4o-mini");
        request.setContextName("executor");
        request.setRetryAttempt(0);
        request.setSuccess(true);
        request.setDurationMs(0);
        request.setCacheHit(true);
        request.setTimestamp("2026-08-01T12:34:56");

        // 2. mock
        when(llmCallLogService.createCallLog(any(LlmCallLogCreateRequest.class))).thenReturn(true);

        // 3. 执行请求并验证
        mockMvc.perform(post("/internal/llm/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(llmCallLogService, times(1)).createCallLog(any(LlmCallLogCreateRequest.class));
    }

    @Test
    @DisplayName("上报 LLM 调用记录 - 重试失败场景")
    void createCallLog_RetryFailed() throws Exception {
        // 1. 构建请求（重试失败）
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setTaskId("2082333099000000099");
        request.setModel("gpt-4o");
        request.setContextName("replan");
        request.setRetryAttempt(2);
        request.setSuccess(false);
        request.setErrorMessage("Validation error: missing field 'steps'");
        request.setDurationMs(3000);
        request.setPromptTokens(600);
        request.setCompletionTokens(800);
        request.setTotalTokens(1400);
        request.setCacheHit(false);
        request.setTimestamp("2026-08-01T12:35:00.123456");

        // 2. mock
        when(llmCallLogService.createCallLog(any(LlmCallLogCreateRequest.class))).thenReturn(true);

        // 3. 执行请求并验证
        mockMvc.perform(post("/internal/llm/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(llmCallLogService, times(1)).createCallLog(any(LlmCallLogCreateRequest.class));
    }

    @Test
    @DisplayName("上报 LLM 调用记录 - 无 taskId 场景")
    void createCallLog_NoTaskId() throws Exception {
        // 1. 构建请求（非任务上下文，无 taskId）
        LlmCallLogCreateRequest request = new LlmCallLogCreateRequest();
        request.setModel("gpt-4o-mini");
        request.setContextName("health_check");
        request.setSuccess(true);
        request.setDurationMs(100);

        // 2. mock
        when(llmCallLogService.createCallLog(any(LlmCallLogCreateRequest.class))).thenReturn(true);

        // 3. 执行请求并验证
        mockMvc.perform(post("/internal/llm/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(llmCallLogService, times(1)).createCallLog(any(LlmCallLogCreateRequest.class));
    }
}

package com.finrpa.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.AiException;
import com.finrpa.ai.client.dto.TaskAbortResponse;
import com.finrpa.ai.client.dto.TaskStateResponse;
import com.finrpa.ai.client.dto.TaskTriggerRequest;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import com.finrpa.ai.sse.AiSseProxy;
import com.finrpa.common.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI 服务代理控制器单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class AiProxyControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private AiServiceClient aiServiceClient;
    private AiSseProxy aiSseProxy;

    @BeforeEach
    void setUp() {
        // 1. 创建 mock 依赖
        aiServiceClient = mock(AiServiceClient.class);
        aiSseProxy = mock(AiSseProxy.class);
        // 2. 构建 MockMvc（注意：使用 @Resource 注入，需要使用字段注入或构造器）
        AiProxyController controller = new AiProxyController();
        // 3. 通过反射注入 mock 依赖（@Resource 字段注入）
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiServiceClient", aiServiceClient);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiSseProxy", aiSseProxy);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // region 任务触发

    @Test
    @DisplayName("触发任务 - 成功")
    void triggerTask_Success() throws Exception {
        // 1. 准备 mock 响应
        TaskTriggerResponse response = new TaskTriggerResponse();
        response.setTaskId("task-1");
        response.setStatus("running");
        response.setMessage("Task triggered successfully");
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class))).thenReturn(response);

        // 2. 构建请求
        TaskTriggerRequest request = new TaskTriggerRequest();
        request.setTaskId("task-1");
        request.setOrgId("org-1");
        request.setUserId("user-1");
        request.setGoal("下载银行流水");

        // 3. 执行请求并验证
        mockMvc.perform(post("/ai/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.status").value("running"))
                .andExpect(jsonPath("$.data.message").value("Task triggered successfully"));

        verify(aiServiceClient, times(1)).triggerTask(any(TaskTriggerRequest.class));
    }

    @Test
    @DisplayName("触发任务 - Python 服务不可用应抛 AiException")
    void triggerTask_ServiceUnavailable_ShouldThrowAiException() throws Exception {
        // 1. mock Python 调用抛异常
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // 2. 构建请求
        TaskTriggerRequest request = new TaskTriggerRequest();
        request.setTaskId("task-1");
        request.setGoal("下载银行流水");

        // 3. standaloneSetup 不走 GlobalExceptionHandler，异常会向上抛出
        //    此场景由 triggerTask_ServiceUnavailable_ExceptionShouldContainCorrectCode 验证
        //    这里只验证 aiServiceClient 被调用
        try {
            mockMvc.perform(post("/ai/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        } catch (Exception ignored) {
            // 预期异常
        }

        verify(aiServiceClient, times(1)).triggerTask(any(TaskTriggerRequest.class));
    }

    @Test
    @DisplayName("触发任务 - Python 不可用时抛出 AiException 应包含正确错误码")
    void triggerTask_ServiceUnavailable_ExceptionShouldContainCorrectCode() {
        // 1. mock Python 调用抛异常
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // 2. 构建请求
        TaskTriggerRequest request = new TaskTriggerRequest();
        request.setTaskId("task-1");
        request.setGoal("下载银行流水");

        // 3. 直接调用 Controller 方法验证异常类型
        AiProxyController controller = new AiProxyController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiServiceClient", aiServiceClient);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiSseProxy", aiSseProxy);

        assertThatThrownBy(() -> controller.triggerTask(request))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("AI 服务不可用")
                .hasFieldOrPropertyWithValue("code", ErrorCode.AI_SERVICE_UNAVAILABLE.getCode());
    }

    // endregion

    // region 任务状态查询

    @Test
    @DisplayName("查询任务状态 - 成功")
    void getTaskState_Success() throws Exception {
        // 1. 准备 mock 响应
        TaskStateResponse response = new TaskStateResponse();
        response.setTaskId("task-1");
        response.setState("executing");
        response.setCurrentStep(2);
        response.setTotalSteps(5);
        response.setMessage("Step 2 in progress");
        when(aiServiceClient.getTaskState("task-1")).thenReturn(response);

        // 2. 执行请求并验证
        mockMvc.perform(get("/ai/tasks/task-1/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.state").value("executing"))
                .andExpect(jsonPath("$.data.currentStep").value(2))
                .andExpect(jsonPath("$.data.totalSteps").value(5))
                .andExpect(jsonPath("$.data.message").value("Step 2 in progress"));

        verify(aiServiceClient, times(1)).getTaskState("task-1");
    }

    @Test
    @DisplayName("查询任务状态 - Python 不可用应抛 AiException")
    void getTaskState_ServiceUnavailable_ShouldThrowAiException() {
        // 1. mock Python 调用抛异常
        when(aiServiceClient.getTaskState("task-1"))
                .thenThrow(new RuntimeException("Connection refused"));

        // 2. 构建 Controller
        AiProxyController controller = new AiProxyController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiServiceClient", aiServiceClient);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiSseProxy", aiSseProxy);

        // 3. 验证异常类型
        assertThatThrownBy(() -> controller.getTaskState("task-1"))
                .isInstanceOf(AiException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AI_SERVICE_UNAVAILABLE.getCode());
    }

    // endregion

    // region 任务终止

    @Test
    @DisplayName("终止任务 - 成功")
    void abortTask_Success() throws Exception {
        // 1. 准备 mock 响应
        TaskAbortResponse response = new TaskAbortResponse();
        response.setTaskId("task-1");
        response.setAborted(true);
        response.setMessage("Task aborted");
        when(aiServiceClient.abortTask("task-1")).thenReturn(response);

        // 2. 执行请求并验证
        mockMvc.perform(post("/ai/tasks/task-1/abort"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.aborted").value(true))
                .andExpect(jsonPath("$.data.message").value("Task aborted"));

        verify(aiServiceClient, times(1)).abortTask("task-1");
    }

    @Test
    @DisplayName("终止任务 - Python 不可用应抛 AiException")
    void abortTask_ServiceUnavailable_ShouldThrowAiException() {
        // 1. mock Python 调用抛异常
        when(aiServiceClient.abortTask("task-1"))
                .thenThrow(new RuntimeException("Connection refused"));

        // 2. 构建 Controller
        AiProxyController controller = new AiProxyController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiServiceClient", aiServiceClient);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "aiSseProxy", aiSseProxy);

        // 3. 验证异常类型
        assertThatThrownBy(() -> controller.abortTask("task-1"))
                .isInstanceOf(AiException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AI_SERVICE_UNAVAILABLE.getCode());
    }

    // endregion

    // region SSE 订阅

    @Test
    @DisplayName("SSE 订阅 - 应返回 SseEmitter 并调用 AiSseProxy")
    void subscribeTaskSse_ShouldReturnSseEmitter() throws Exception {
        // 1. 准备 mock SseEmitter
        SseEmitter mockEmitter = new SseEmitter(3600000L);
        when(aiSseProxy.proxySse("task-1")).thenReturn(mockEmitter);

        // 2. 执行请求并验证状态码 200
        mockMvc.perform(get("/ai/sse/tasks/task-1"))
                .andExpect(status().isOk());

        // 3. 验证 AiSseProxy 被调用
        verify(aiSseProxy, times(1)).proxySse("task-1");
    }

    // endregion
}

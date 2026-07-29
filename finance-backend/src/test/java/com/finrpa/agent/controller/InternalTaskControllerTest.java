package com.finrpa.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.agent.dto.request.SubTaskUpdateRequest;
import com.finrpa.agent.dto.request.TaskStateUpdateRequest;
import com.finrpa.agent.service.TaskService;
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
 * 内部任务回调控制器单元测试
 *
 * <p>验证 Python 回调端点的请求转发与响应格式。
 * 鉴权拦截器在集成测试中验证，此处 standaloneSetup 不注册拦截器。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class InternalTaskControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;

    private MockMvc mockMvc;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        // 1. mock 依赖
        taskService = mock(TaskService.class);

        // 2. 构建 MockMvc（standalone，不走拦截器）
        InternalTaskController controller = new InternalTaskController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "taskService", taskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("更新任务状态 - 成功")
    void updateTaskState_Success() throws Exception {
        // 1. 构建请求
        TaskStateUpdateRequest request = new TaskStateUpdateRequest();
        request.setState("EXECUTING");
        request.setCurrentStep(1);
        request.setTotalSteps(5);
        request.setMessage("开始执行");

        // 2. 执行请求并验证
        mockMvc.perform(post("/internal/tasks/{taskId}/state", TEST_TASK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        // 3. 验证 service 被调用
        verify(taskService, times(1)).updateTaskState(eq(TEST_TASK_ID), any(TaskStateUpdateRequest.class));
    }

    @Test
    @DisplayName("更新子任务状态 - 成功")
    void updateSubTask_Success() throws Exception {
        // 1. 构建请求
        SubTaskUpdateRequest request = new SubTaskUpdateRequest();
        request.setSubtaskIndex(0);
        request.setStatus("COMPLETED");

        // 2. 执行请求并验证
        mockMvc.perform(post("/internal/tasks/{taskId}/subtasks", TEST_TASK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        // 3. 验证 service 被调用
        verify(taskService, times(1)).updateSubTask(eq(TEST_TASK_ID), any(SubTaskUpdateRequest.class));
    }
}

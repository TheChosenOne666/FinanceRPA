package com.finrpa.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.agent.dto.request.TaskQueryRequest;
import com.finrpa.agent.dto.response.SubTaskVO;
import com.finrpa.agent.dto.response.TaskDetailVO;
import com.finrpa.agent.dto.response.TaskVO;
import com.finrpa.agent.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务管理控制器单元测试
 *
 * <p>验证对外 API（任务列表、详情、终止）的请求转发与响应格式。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class TaskControllerTest {

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

        // 2. 构建 MockMvc
        TaskController controller = new TaskController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "taskService", taskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("任务列表 - 查询成功")
    void listTasks_Success() throws Exception {
        // 1. mock 分页结果
        TaskVO taskVO = new TaskVO();
        taskVO.setTaskId(TEST_TASK_ID);
        taskVO.setGoal("下载银行流水");
        taskVO.setStatus("EXECUTING");
        Page<TaskVO> page = new Page<>(1, 10);
        page.setRecords(List.of(taskVO));
        page.setTotal(1);
        when(taskService.listTasks(any(TaskQueryRequest.class))).thenReturn(page);

        // 2. 执行请求并验证
        mockMvc.perform(get("/tasks")
                        .param("current", "1")
                        .param("pageSize", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].taskId").value(TEST_TASK_ID.toString()))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(taskService, times(1)).listTasks(any(TaskQueryRequest.class));
    }

    @Test
    @DisplayName("任务详情 - 查询成功（含子任务）")
    void getTaskDetail_Success() throws Exception {
        // 1. mock 详情结果
        TaskDetailVO detail = new TaskDetailVO();
        detail.setTaskId(TEST_TASK_ID);
        detail.setGoal("下载银行流水");
        detail.setStatus("EXECUTING");

        SubTaskVO subtask = new SubTaskVO();
        subtask.setSubtaskIndex(0);
        subtask.setStatus("COMPLETED");
        detail.setSubtasks(List.of(subtask));

        when(taskService.getTaskDetail(TEST_TASK_ID)).thenReturn(detail);

        // 2. 执行请求并验证
        mockMvc.perform(get("/tasks/{taskId}", TEST_TASK_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(TEST_TASK_ID.toString()))
                .andExpect(jsonPath("$.data.goal").value("下载银行流水"))
                .andExpect(jsonPath("$.data.subtasks[0].subtaskIndex").value(0));

        verify(taskService, times(1)).getTaskDetail(TEST_TASK_ID);
    }

    @Test
    @DisplayName("终止任务 - 成功")
    void abortTask_Success() throws Exception {
        // 1. mock service（do nothing 表示成功）
        doNothing().when(taskService).abortTask(TEST_TASK_ID);

        // 2. 执行请求并验证
        mockMvc.perform(post("/tasks/{taskId}/abort", TEST_TASK_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(taskService, times(1)).abortTask(TEST_TASK_ID);
    }
}

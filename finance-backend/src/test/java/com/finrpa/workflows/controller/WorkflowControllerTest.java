package com.finrpa.workflows.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.agent.constant.AgentConstant;
import com.finrpa.common.exception.GlobalExceptionHandler;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.tenant.context.TenantContext;
import com.finrpa.workflows.dto.request.WorkflowAddRequest;
import com.finrpa.workflows.dto.request.WorkflowQueryRequest;
import com.finrpa.workflows.dto.request.WorkflowRunRequest;
import com.finrpa.workflows.dto.request.WorkflowUpdateRequest;
import com.finrpa.workflows.dto.response.WorkflowRunVO;
import com.finrpa.workflows.dto.response.WorkflowVO;
import com.finrpa.workflows.service.WorkflowService;
import com.finrpa.workflows.service.WorkflowTriggerService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作流模板管理控制器单元测试
 *
 * <p>验证 CRUD 端点与 /run 触发执行端点的请求转发与响应格式。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class WorkflowControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 测试用 workflowId */
    private static final Long TEST_WORKFLOW_ID = 2082333099000000099L;
    /** 测试用 orgId */
    private static final String TEST_ORG_ID = "2082333077580967938";
    /** 测试用 userId */
    private static final String TEST_USER_ID = "2082333078168170497";

    private MockMvc mockMvc;
    private WorkflowService workflowService;
    private WorkflowTriggerService workflowTriggerService;
    private HttpServletRequest httpServletRequest;

    @BeforeEach
    void setUp() {
        // 1. mock 依赖
        workflowService = mock(WorkflowService.class);
        workflowTriggerService = mock(WorkflowTriggerService.class);
        httpServletRequest = mock(HttpServletRequest.class);

        // 2. 设置 TenantContext 与 userId 请求属性
        TenantContext.setOrgId(TEST_ORG_ID);
        when(httpServletRequest.getAttribute(AgentConstant.USER_ID_REQUEST_ATTR)).thenReturn(TEST_USER_ID);

        // 3. 构建 MockMvc（注册 GlobalExceptionHandler 让 BusinessException 转换为统一响应）
        WorkflowController controller = new WorkflowController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "workflowService", workflowService);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "workflowTriggerService", workflowTriggerService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        // 清理 TenantContext 避免污染其他测试
        TenantContext.clear();
    }

    // region 查询

    @Test
    @DisplayName("模板列表 - 分页查询成功")
    void listWorkflows_Success() throws Exception {
        // 1. mock 分页结果
        WorkflowVO vo = buildVO("银行流水下载", "banking", "medium");
        Page<WorkflowVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);
        when(workflowService.listWorkflows(any(WorkflowQueryRequest.class))).thenReturn(page);

        // 2. 执行请求并验证
        mockMvc.perform(get("/workflows")
                        .param("current", "1")
                        .param("pageSize", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].name").value("银行流水下载"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(workflowService, times(1)).listWorkflows(any(WorkflowQueryRequest.class));
    }

    @Test
    @DisplayName("模板详情 - 查询成功")
    void getWorkflow_Success() throws Exception {
        WorkflowVO vo = buildVO("银行流水下载", "banking", "medium");
        when(workflowService.getWorkflow(TEST_WORKFLOW_ID)).thenReturn(vo);

        mockMvc.perform(get("/workflows/{workflowId}", TEST_WORKFLOW_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("银行流水下载"));

        verify(workflowService, times(1)).getWorkflow(TEST_WORKFLOW_ID);
    }

    // endregion

    // region 增删改

    @Test
    @DisplayName("创建模板 - 成功")
    void createWorkflow_Success() throws Exception {
        WorkflowVO vo = buildVO("银行流水下载", "banking", "medium");
        when(workflowService.createWorkflow(any(WorkflowAddRequest.class), any())).thenReturn(vo);

        WorkflowAddRequest request = new WorkflowAddRequest();
        request.setName("银行流水下载");
        request.setIndustry("banking");
        request.setRiskLevel("medium");
        request.setSteps("[{\"skill\":\"login\"}]");

        mockMvc.perform(post("/workflows")
                        .requestAttr(AgentConstant.USER_ID_REQUEST_ATTR, TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("银行流水下载"));

        verify(workflowService, times(1)).createWorkflow(any(WorkflowAddRequest.class), any());
    }

    @Test
    @DisplayName("更新模板 - 成功")
    void updateWorkflow_Success() throws Exception {
        when(workflowService.updateWorkflow(eq(TEST_WORKFLOW_ID), any(WorkflowUpdateRequest.class))).thenReturn(true);

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        request.setEnabled(0);

        mockMvc.perform(put("/workflows/{workflowId}", TEST_WORKFLOW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(workflowService, times(1)).updateWorkflow(eq(TEST_WORKFLOW_ID), any(WorkflowUpdateRequest.class));
    }

    @Test
    @DisplayName("删除模板 - 成功")
    void deleteWorkflow_Success() throws Exception {
        when(workflowService.deleteWorkflow(TEST_WORKFLOW_ID)).thenReturn(true);

        mockMvc.perform(delete("/workflows/{workflowId}", TEST_WORKFLOW_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(workflowService, times(1)).deleteWorkflow(TEST_WORKFLOW_ID);
    }

    // endregion

    // region /run 触发执行

    @Test
    @DisplayName("触发执行 - 成功")
    void runWorkflow_Success() throws Exception {
        // 1. mock 触发服务
        WorkflowRunVO runVO = new WorkflowRunVO();
        runVO.setTaskId(2082333099000000100L);
        runVO.setWorkflowId(TEST_WORKFLOW_ID);
        runVO.setState("EXECUTING");
        when(workflowTriggerService.triggerWorkflow(eq(TEST_WORKFLOW_ID), any(WorkflowRunRequest.class),
                eq(Long.parseLong(TEST_ORG_ID)), eq(Long.parseLong(TEST_USER_ID))))
                .thenReturn(runVO);

        // 2. 构建请求体
        WorkflowRunRequest request = new WorkflowRunRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("account", "6228480012345678");
        request.setParams(params);

        // 3. 执行请求并验证
        mockMvc.perform(post("/workflows/{workflowId}/run", TEST_WORKFLOW_ID)
                        .requestAttr(AgentConstant.USER_ID_REQUEST_ATTR, TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value("2082333099000000100"))
                .andExpect(jsonPath("$.data.workflowId").value(TEST_WORKFLOW_ID.toString()))
                .andExpect(jsonPath("$.data.state").value("EXECUTING"));

        verify(workflowTriggerService, times(1)).triggerWorkflow(
                eq(TEST_WORKFLOW_ID), any(WorkflowRunRequest.class),
                eq(Long.parseLong(TEST_ORG_ID)), eq(Long.parseLong(TEST_USER_ID)));
    }

    @Test
    @DisplayName("触发执行 - 未登录（orgId 缺失）返回未登录错误码")
    void runWorkflow_NotLoggedIn() throws Exception {
        // 1. 清空 TenantContext 模拟未登录
        TenantContext.clear();

        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setParams(new HashMap<>());

        // 2. GlobalExceptionHandler 将 BusinessException 转为统一响应（HTTP 200 + code=40100）
        mockMvc.perform(post("/workflows/{workflowId}/run", TEST_WORKFLOW_ID)
                        .requestAttr(AgentConstant.USER_ID_REQUEST_ATTR, TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("无法获取当前用户信息"));

        verifyNoInteractions(workflowTriggerService);
    }

    @Test
    @DisplayName("触发执行 - 业务异常（模板不存在）转换为错误响应")
    void runWorkflow_BusinessException() throws Exception {
        // 1. mock 触发服务抛业务异常
        when(workflowTriggerService.triggerWorkflow(anyLong(), any(WorkflowRunRequest.class),
                anyLong(), anyLong()))
                .thenThrow(new com.finrpa.common.exception.BusinessException(
                        ErrorCode.WORKFLOW_NOT_FOUND, "工作流模板不存在"));

        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setParams(new HashMap<>());

        // 2. GlobalExceptionHandler 将 BusinessException 转为统一响应（HTTP 200 + code=40410）
        mockMvc.perform(post("/workflows/{workflowId}/run", TEST_WORKFLOW_ID)
                        .requestAttr(AgentConstant.USER_ID_REQUEST_ATTR, TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORKFLOW_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("工作流模板不存在"));

        verify(workflowTriggerService, times(1)).triggerWorkflow(
                anyLong(), any(WorkflowRunRequest.class), anyLong(), anyLong());
    }

    // endregion

    // region 辅助方法

    /**
     * 构建测试用 VO
     *
     * @param name      模板名
     * @param industry  行业
     * @param riskLevel 风险等级
     * @return 模板视图
     */
    private WorkflowVO buildVO(String name, String industry, String riskLevel) {
        WorkflowVO vo = new WorkflowVO();
        vo.setWorkflowId(TEST_WORKFLOW_ID);
        vo.setName(name);
        vo.setDescription("测试模板描述");
        vo.setIndustry(industry);
        vo.setRiskLevel(riskLevel);
        vo.setParams("[]");
        vo.setSteps("[{\"skill\":\"login\"}]");
        vo.setVersion("1.0.0");
        vo.setEnabled(1);
        return vo;
    }

    // endregion
}

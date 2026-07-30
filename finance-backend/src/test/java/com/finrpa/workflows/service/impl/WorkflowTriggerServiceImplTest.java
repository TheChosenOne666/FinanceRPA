package com.finrpa.workflows.service.impl;

import com.finrpa.agent.dto.request.TaskCreateRequest;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.service.TaskService;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.TaskTriggerRequest;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.workflows.dto.request.WorkflowRunRequest;
import com.finrpa.workflows.dto.response.WorkflowRunVO;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import com.finrpa.workflows.service.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * WorkflowTriggerServiceImpl 单元测试
 *
 * <p>覆盖模板加载、必填参数校验、参数映射、任务创建与 Python 调用全流程。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTriggerServiceImplTest {

    /** 测试用 workflowId */
    private static final Long TEST_WORKFLOW_ID = 2082333099000000099L;
    /** 测试用 orgId */
    private static final Long TEST_ORG_ID = 2082333077580967938L;
    /** 测试用 userId */
    private static final Long TEST_USER_ID = 2082333078168170497L;
    /** 测试用 taskId */
    private static final Long TEST_TASK_ID = 2082333099000000100L;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private TaskService taskService;

    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private WorkflowTriggerServiceImpl workflowTriggerService;

    // region triggerWorkflow 成功路径

    @Test
    @DisplayName("触发工作流 - 成功（参数映射与任务创建均正常）")
    void triggerWorkflow_Success() {
        // 1. mock 模板查询
        WorkflowTemplateEO template = buildTemplate("银行流水下载", "banking", "medium", 1);
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        // 2. mock 任务创建
        AgentTaskEO taskEO = new AgentTaskEO();
        taskEO.setTaskId(TEST_TASK_ID);
        taskEO.setOrgId(TEST_ORG_ID);
        taskEO.setUserId(TEST_USER_ID);
        when(taskService.createTask(eq(TEST_ORG_ID), eq(TEST_USER_ID), any(TaskCreateRequest.class)))
                .thenReturn(taskEO);

        // 3. mock Python 调用
        TaskTriggerResponse response = new TaskTriggerResponse();
        response.setTaskId(String.valueOf(TEST_TASK_ID));
        response.setStatus("running");
        response.setMessage("已触发");
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class))).thenReturn(response);

        // 4. 执行触发
        WorkflowRunRequest request = new WorkflowRunRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("account", "6228480012345678");
        request.setParams(params);

        WorkflowRunVO result = workflowTriggerService.triggerWorkflow(
                TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID);

        // 5. 验证返回结果
        assertThat(result.getTaskId()).isEqualTo(TEST_TASK_ID);
        assertThat(result.getWorkflowId()).isEqualTo(TEST_WORKFLOW_ID);
        assertThat(result.getState()).isEqualTo("EXECUTING");

        // 6. 验证 TaskCreateRequest 中参数映射已完成（{{account}} → 实际值）
        ArgumentCaptor<TaskCreateRequest> createCaptor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskService).createTask(eq(TEST_ORG_ID), eq(TEST_USER_ID), createCaptor.capture());
        TaskCreateRequest captured = createCaptor.getValue();
        assertThat(captured.getGoal()).contains("银行流水下载");
        assertThat(captured.getWorkflowId()).isEqualTo(TEST_WORKFLOW_ID);
        // steps 字符串中的 {{account}} 已被替换为实际值
        String steps = captured.getParams().get("steps").toString();
        assertThat(steps).contains("6228480012345678");
        assertThat(steps).doesNotContain("{{account}}");
        // industry / riskLevel 透传到任务参数
        assertThat(captured.getParams().get("industry")).isEqualTo("banking");
        assertThat(captured.getParams().get("riskLevel")).isEqualTo("medium");

        // 7. 验证 Python 触发请求
        ArgumentCaptor<TaskTriggerRequest> triggerCaptor = ArgumentCaptor.forClass(TaskTriggerRequest.class);
        verify(aiServiceClient).triggerTask(triggerCaptor.capture());
        TaskTriggerRequest triggerReq = triggerCaptor.getValue();
        assertThat(triggerReq.getTaskId()).isEqualTo(String.valueOf(TEST_TASK_ID));
        assertThat(triggerReq.getOrgId()).isEqualTo(String.valueOf(TEST_ORG_ID));
        assertThat(triggerReq.getUserId()).isEqualTo(String.valueOf(TEST_USER_ID));
        assertThat(triggerReq.getWorkflowId()).isEqualTo(String.valueOf(TEST_WORKFLOW_ID));
    }

    // endregion

    // region 模板加载异常

    @Test
    @DisplayName("触发工作流 - 模板不存在抛异常")
    void triggerWorkflow_TemplateNotFound() {
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(null);

        WorkflowRunRequest request = new WorkflowRunRequest();
        assertThatThrownBy(() -> workflowTriggerService.triggerWorkflow(
                TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板不存在");

        // 不应再调用任务创建或 Python
        verifyNoInteractions(taskService);
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    @DisplayName("触发工作流 - 模板已禁用抛异常")
    void triggerWorkflow_TemplateDisabled() {
        WorkflowTemplateEO template = buildTemplate("银行流水下载", "banking", "medium", 0);
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        WorkflowRunRequest request = new WorkflowRunRequest();
        assertThatThrownBy(() -> workflowTriggerService.triggerWorkflow(
                TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板已禁用");

        verifyNoInteractions(taskService);
        verifyNoInteractions(aiServiceClient);
    }

    // endregion

    // region 参数校验

    @Test
    @DisplayName("触发工作流 - 缺少必填参数抛异常")
    void triggerWorkflow_MissingRequiredParam() {
        WorkflowTemplateEO template = buildTemplate("银行流水下载", "banking", "medium", 1);
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        // 不提供 account 参数
        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setParams(new HashMap<>());

        assertThatThrownBy(() -> workflowTriggerService.triggerWorkflow(
                TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少必填参数")
                .hasMessageContaining("account");

        verifyNoInteractions(taskService);
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    @DisplayName("触发工作流 - params 为 null 时按空 Map 处理，必填参数仍触发校验")
    void triggerWorkflow_NullParamsMap() {
        WorkflowTemplateEO template = buildTemplate("银行流水下载", "banking", "medium", 1);
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setParams(null);

        assertThatThrownBy(() -> workflowTriggerService.triggerWorkflow(
                TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少必填参数");
    }

    @Test
    @DisplayName("触发工作流 - 无必填参数时跳过校验直接执行")
    void triggerWorkflow_NoRequiredParams() {
        // 1. params 定义中无 required:true
        WorkflowTemplateEO template = buildTemplate("查询模板", "banking", "low", 1);
        template.setParams("[{\"name\":\"account\",\"type\":\"string\",\"required\":false}]");
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        // 2. mock 任务创建
        AgentTaskEO taskEO = new AgentTaskEO();
        taskEO.setTaskId(TEST_TASK_ID);
        when(taskService.createTask(anyLong(), anyLong(), any(TaskCreateRequest.class))).thenReturn(taskEO);

        // 3. mock Python 调用
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class))).thenReturn(new TaskTriggerResponse());

        // 4. 不提供任何参数也应执行成功
        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setParams(new HashMap<>());

        WorkflowRunVO result = workflowTriggerService.triggerWorkflow(
                TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID);

        assertThat(result.getTaskId()).isEqualTo(TEST_TASK_ID);
    }

    // endregion

    // region 参数映射

    @Test
    @DisplayName("参数映射 - {{param}} 正确替换为用户值")
    void triggerWorkflow_ParamReplacement() {
        // 1. 模板包含多个参数引用
        WorkflowTemplateEO template = buildTemplate("批量转账", "banking", "high", 1);
        template.setParams("[{\"name\":\"account\",\"type\":\"string\",\"required\":true},"
                + "{\"name\":\"amount\",\"type\":\"number\",\"required\":true}]");
        template.setSteps("[{\"skill\":\"transfer\",\"params_mapping\":{"
                + "\"account\":\"{{account}}\",\"amount\":\"{{amount}}\"}}]");
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        // 2. mock 任务创建
        AgentTaskEO taskEO = new AgentTaskEO();
        taskEO.setTaskId(TEST_TASK_ID);
        when(taskService.createTask(anyLong(), anyLong(), any(TaskCreateRequest.class))).thenReturn(taskEO);
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class))).thenReturn(new TaskTriggerResponse());

        // 3. 提供两个参数
        WorkflowRunRequest request = new WorkflowRunRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("account", "6228480012345678");
        params.put("amount", "5000.00");
        request.setParams(params);

        workflowTriggerService.triggerWorkflow(TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID);

        // 4. 验证 steps 中两个占位符都被替换
        ArgumentCaptor<TaskCreateRequest> captor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskService).createTask(anyLong(), anyLong(), captor.capture());
        String steps = captor.getValue().getParams().get("steps").toString();
        assertThat(steps).contains("6228480012345678");
        assertThat(steps).contains("5000.00");
        assertThat(steps).doesNotContain("{{account}}");
        assertThat(steps).doesNotContain("{{amount}}");
    }

    @Test
    @DisplayName("参数映射 - 未提供的参数替换为空字符串")
    void triggerWorkflow_MissingParamReplacedWithEmpty() {
        // 1. 模板引用未定义为 required 的参数
        WorkflowTemplateEO template = buildTemplate("查询模板", "banking", "low", 1);
        template.setParams("[]");
        template.setSteps("[{\"skill\":\"query\",\"params_mapping\":{\"remark\":\"{{remark}}\"}}]");
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        AgentTaskEO taskEO = new AgentTaskEO();
        taskEO.setTaskId(TEST_TASK_ID);
        when(taskService.createTask(anyLong(), anyLong(), any(TaskCreateRequest.class))).thenReturn(taskEO);
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class))).thenReturn(new TaskTriggerResponse());

        // 2. 不提供 remark 参数
        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setParams(new HashMap<>());

        workflowTriggerService.triggerWorkflow(TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID);

        // 3. 验证 {{remark}} 被替换为空串
        ArgumentCaptor<TaskCreateRequest> captor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskService).createTask(anyLong(), anyLong(), captor.capture());
        String steps = captor.getValue().getParams().get("steps").toString();
        assertThat(steps).doesNotContain("{{remark}}");
    }

    @Test
    @DisplayName("参数映射 - 特殊字符正确转义防 JSON 破坏")
    void triggerWorkflow_SpecialCharEscaped() {
        WorkflowTemplateEO template = buildTemplate("查询模板", "banking", "low", 1);
        template.setParams("[{\"name\":\"remark\",\"type\":\"string\",\"required\":true}]");
        template.setSteps("[{\"skill\":\"query\",\"params_mapping\":{\"remark\":\"{{remark}}\"}}]");
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        AgentTaskEO taskEO = new AgentTaskEO();
        taskEO.setTaskId(TEST_TASK_ID);
        when(taskService.createTask(anyLong(), anyLong(), any(TaskCreateRequest.class))).thenReturn(taskEO);
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class))).thenReturn(new TaskTriggerResponse());

        // 2. 提供包含双引号和反斜杠的参数
        WorkflowRunRequest request = new WorkflowRunRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("remark", "包含\"引号\"和\\反斜杠");
        request.setParams(params);

        workflowTriggerService.triggerWorkflow(TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID);

        // 3. 验证转义后的字符串出现在 steps 中
        ArgumentCaptor<TaskCreateRequest> captor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskService).createTask(anyLong(), anyLong(), captor.capture());
        String steps = captor.getValue().getParams().get("steps").toString();
        assertThat(steps).contains("包含\\\"引号\\\"和\\\\反斜杠");
        assertThat(steps).doesNotContain("{{remark}}");
    }

    // endregion

    // region Python 调用异常

    @Test
    @DisplayName("触发工作流 - Python 服务不可用抛 AI_SERVICE_UNAVAILABLE")
    void triggerWorkflow_PythonUnavailable() {
        WorkflowTemplateEO template = buildTemplate("银行流水下载", "banking", "medium", 1);
        when(workflowService.queryByWorkflowId(TEST_WORKFLOW_ID)).thenReturn(template);

        AgentTaskEO taskEO = new AgentTaskEO();
        taskEO.setTaskId(TEST_TASK_ID);
        when(taskService.createTask(anyLong(), anyLong(), any(TaskCreateRequest.class))).thenReturn(taskEO);
        // Python 调用抛异常
        when(aiServiceClient.triggerTask(any(TaskTriggerRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        WorkflowRunRequest request = new WorkflowRunRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("account", "6228480012345678");
        request.setParams(params);

        assertThatThrownBy(() -> workflowTriggerService.triggerWorkflow(
                TEST_WORKFLOW_ID, request, TEST_ORG_ID, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 服务不可用")
                .hasMessageContaining("connection refused");
    }

    // endregion

    // region 辅助方法

    /**
     * 构建测试用模板实体
     *
     * @param name      模板名
     * @param industry  行业
     * @param riskLevel 风险等级
     * @param enabled   启用状态（1-启用 0-禁用）
     * @return 模板实体
     */
    private WorkflowTemplateEO buildTemplate(String name, String industry, String riskLevel, int enabled) {
        WorkflowTemplateEO entity = new WorkflowTemplateEO();
        entity.setId(1L);
        entity.setWorkflowId(TEST_WORKFLOW_ID);
        entity.setName(name);
        entity.setDescription("测试模板描述");
        entity.setIndustry(industry);
        entity.setRiskLevel(riskLevel);
        entity.setParams("[{\"name\":\"account\",\"type\":\"string\",\"required\":true}]");
        entity.setSteps("[{\"skill\":\"login\",\"params_mapping\":{\"account\":\"{{account}}\"}}]");
        entity.setVersion("1.0.0");
        entity.setEnabled(enabled);
        entity.setDeleted(0);
        return entity;
    }

    // endregion
}

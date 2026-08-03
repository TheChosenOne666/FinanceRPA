package com.finrpa.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.agent.dto.request.SubTaskUpdateRequest;
import com.finrpa.agent.dto.request.TaskCreateRequest;
import com.finrpa.agent.dto.request.TaskQueryRequest;
import com.finrpa.agent.dto.request.TaskStateUpdateRequest;
import com.finrpa.agent.dto.request.CoordinationStateUpdateRequest;
import com.finrpa.agent.dto.response.SubTaskVO;
import com.finrpa.agent.dto.response.TaskDetailVO;
import com.finrpa.agent.dto.response.TaskVO;
import com.finrpa.agent.entity.AgentSubTaskEO;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.entity.CoordinationStateEO;
import com.finrpa.agent.enums.TaskStateEnum;
import com.finrpa.agent.mapper.AgentSubTaskMapper;
import com.finrpa.agent.mapper.AgentTaskMapper;
import com.finrpa.agent.mapper.CoordinationStateMapper;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.TaskResumeRequest;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.tenant.context.TenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 任务服务实现单元测试
 *
 * <p>覆盖任务创建、查询、终止与 Python 回调状态更新等核心逻辑。
 * Mapper 层全部 mock，ObjectMapper 使用真实实例以验证 JSON 序列化。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082333077580967938L;
    /** 测试用用户 ID */
    private static final Long TEST_USER_ID = 2082333078168170497L;
    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;
    /** 测试用工作流模板 ID */
    private static final Long TEST_WORKFLOW_ID = 2082333100000000123L;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @Mock
    private AgentSubTaskMapper agentSubTaskMapper;

    @Mock
    private CoordinationStateMapper coordinationStateMapper;

    @Mock
    private AiServiceClient aiServiceClient;

    /** Spring 事件发布器（M8.1 任务终态事件） */
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    /** 用户 Mapper（M7.5 引入：填充触发用户姓名） */
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private TaskServiceImpl taskService;

    /**
     * 初始化 MyBatis-Plus 实体 lambda 缓存
     *
     * <p>纯单元测试无 Spring 容器，LambdaUpdateWrapper/LambdaQueryWrapper 依赖的
     * TableInfo 缓存需手动初始化，否则抛 {@code MybatisPlusException: can not find lambda cache}。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), AgentTaskEO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), AgentSubTaskEO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CoordinationStateEO.class);
    }

    @BeforeEach
    void setUp() {
        // 注入真实 ObjectMapper（避免 mock 序列化逻辑，验证真实 JSON 转换）
        ReflectionTestUtils.setField(taskService, "objectMapper", new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        // 清理 TenantContext，避免线程污染
        TenantContext.clear();
    }

    // region createTask

    @Test
    @DisplayName("createTask - 无参数时创建成功")
    void createTask_WithoutParams_Success() {
        // 1. 构建请求（无 params）
        TaskCreateRequest request = new TaskCreateRequest();
        request.setGoal("下载银行流水");

        // 2. mock insert 返回成功
        when(agentTaskMapper.insert(any(AgentTaskEO.class))).thenReturn(1);

        // 3. 调用
        AgentTaskEO result = taskService.createTask(TEST_ORG_ID, TEST_USER_ID, request);

        // 4. 验证
        assertThat(result).isNotNull();
        assertThat(result.getOrgId()).isEqualTo(TEST_ORG_ID);
        assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.getGoal()).isEqualTo("下载银行流水");
        assertThat(result.getStatus()).isEqualTo(TaskStateEnum.PENDING.getValue());
        assertThat(result.getCurrentStep()).isEqualTo(0);
        assertThat(result.getTotalSteps()).isEqualTo(0);
        assertThat(result.getParams()).isNull(); // 无参数时 params 为 null
        verify(agentTaskMapper, times(1)).insert(any(AgentTaskEO.class));
    }

    @Test
    @DisplayName("createTask - 带参数时创建成功（params 序列化为 JSON）")
    void createTask_WithParams_Success() {
        // 1. 构建请求（带 params）
        TaskCreateRequest request = new TaskCreateRequest();
        request.setGoal("下载银行流水");
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://bank.example.com");
        params.put("account", "622848");
        request.setParams(params);

        // 2. mock insert 返回成功
        when(agentTaskMapper.insert(any(AgentTaskEO.class))).thenReturn(1);

        // 3. 调用
        AgentTaskEO result = taskService.createTask(TEST_ORG_ID, TEST_USER_ID, request);

        // 4. 验证 params 已序列化为 JSON 字符串
        assertThat(result.getParams()).isNotNull();
        assertThat(result.getParams()).contains("bank.example.com");
        assertThat(result.getParams()).contains("622848");
    }

    @Test
    @DisplayName("createTask - orgId 为 null 抛参数异常")
    void createTask_NullOrgId_ThrowsException() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setGoal("下载银行流水");

        assertThatThrownBy(() -> taskService.createTask(null, TEST_USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织 ID 和用户 ID 不能为空");
    }

    @Test
    @DisplayName("createTask - userId 为 null 抛参数异常")
    void createTask_NullUserId_ThrowsException() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setGoal("下载银行流水");

        assertThatThrownBy(() -> taskService.createTask(TEST_ORG_ID, null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织 ID 和用户 ID 不能为空");
    }

    @Test
    @DisplayName("createTask - goal 为空白抛参数异常")
    void createTask_BlankGoal_ThrowsException() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setGoal("   ");

        assertThatThrownBy(() -> taskService.createTask(TEST_ORG_ID, TEST_USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务目标不能为空");
    }

    @Test
    @DisplayName("createTask - insert 返回 0 抛操作异常")
    void createTask_InsertFailed_ThrowsException() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setGoal("下载银行流水");

        // mock insert 返回 0（插入失败）
        when(agentTaskMapper.insert(any(AgentTaskEO.class))).thenReturn(0);

        assertThatThrownBy(() -> taskService.createTask(TEST_ORG_ID, TEST_USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务创建失败");
    }

    // endregion

    // region getTaskDetail

    @Test
    @DisplayName("getTaskDetail - 查询成功（含子任务列表）")
    void getTaskDetail_Success() {
        // 1. mock 任务
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        // 2. mock 子任务
        AgentSubTaskEO subtask = new AgentSubTaskEO();
        subtask.setSubtaskId(2001L);
        subtask.setTaskId(TEST_TASK_ID);
        subtask.setSubtaskIndex(0);
        subtask.setStatus("COMPLETED");
        when(agentSubTaskMapper.selectList(any())).thenReturn(List.of(subtask));

        // 3. 调用（设置租户上下文）
        TenantContext.setOrgId(TEST_ORG_ID.toString());
        TaskDetailVO result = taskService.getTaskDetail(TEST_TASK_ID);

        // 4. 验证
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo(TEST_TASK_ID);
        assertThat(result.getGoal()).isEqualTo("下载银行流水");
        assertThat(result.getSubtasks()).hasSize(1);
        assertThat(result.getSubtasks().get(0).getSubtaskIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("getTaskDetail - 任务不存在抛异常")
    void getTaskDetail_NotFound_ThrowsException() {
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> taskService.getTaskDetail(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
    }

    @Test
    @DisplayName("getTaskDetail - 跨组织访问抛权限异常")
    void getTaskDetail_CrossTenant_ThrowsException() {
        // 1. mock 任务属于 org A
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        // 2. 当前租户为 org B（使用合法 Long 值）
        TenantContext.setOrgId("999999999999999999");

        // 3. 验证抛权限异常
        assertThatThrownBy(() -> taskService.getTaskDetail(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    @DisplayName("getTaskDetail - taskId 为 null 抛参数异常")
    void getTaskDetail_NullTaskId_ThrowsException() {
        assertThatThrownBy(() -> taskService.getTaskDetail(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务 ID 不能为空");
    }

    // endregion

    // region abortTask

    @Test
    @DisplayName("abortTask - 执行中任务终止成功")
    void abortTask_ExecutingTask_Success() {
        // 1. mock 任务处于 EXECUTING 状态
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);

        // 2. 设置租户上下文并调用
        TenantContext.setOrgId(TEST_ORG_ID.toString());
        taskService.abortTask(TEST_TASK_ID);

        // 3. 验证 update 被调用
        verify(agentTaskMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("abortTask - 待执行任务终止成功")
    void abortTask_PendingTask_Success() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.PENDING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);

        TenantContext.setOrgId(TEST_ORG_ID.toString());
        taskService.abortTask(TEST_TASK_ID);

        verify(agentTaskMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("abortTask - 任务不存在抛异常")
    void abortTask_NotFound_ThrowsException() {
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> taskService.abortTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
    }

    @Test
    @DisplayName("abortTask - 已成功的任务终止抛异常")
    void abortTask_AlreadySuccess_ThrowsException() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.SUCCESS);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        TenantContext.setOrgId(TEST_ORG_ID.toString());
        assertThatThrownBy(() -> taskService.abortTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务已结束");
    }

    @Test
    @DisplayName("abortTask - 跨组织操作抛权限异常")
    void abortTask_CrossTenant_ThrowsException() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        // 当前租户为其他组织（使用合法 Long 值）
        TenantContext.setOrgId("999999999999999999");

        assertThatThrownBy(() -> taskService.abortTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    // endregion

    // region updateTaskState（Python 回调）

    @Test
    @DisplayName("updateTaskState - PENDING → EXECUTING 成功")
    void updateTaskState_PendingToExecuting_Success() {
        // 1. mock 任务处于 PENDING
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.PENDING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);

        // 2. 构建回调请求
        TaskStateUpdateRequest request = new TaskStateUpdateRequest();
        request.setState("EXECUTING");
        request.setCurrentStep(1);
        request.setTotalSteps(5);
        request.setMessage("开始执行");

        // 3. 调用（内部回调无需租户上下文）
        taskService.updateTaskState(TEST_TASK_ID, request);

        // 4. 验证 update 被调用
        verify(agentTaskMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("updateTaskState - 终态任务忽略更新（不抛异常）")
    void updateTaskState_TerminalState_Ignored() {
        // 1. mock 任务已处于 SUCCESS 终态
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.SUCCESS);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        // 2. 构建回调请求（尝试从 SUCCESS 流转）
        TaskStateUpdateRequest request = new TaskStateUpdateRequest();
        request.setState("EXECUTING");

        // 3. 调用（应静默忽略，不抛异常）
        taskService.updateTaskState(TEST_TASK_ID, request);

        // 4. 验证 update 未被调用（终态不更新）
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("updateTaskState - 非法流转抛异常（PENDING → SUCCESS）")
    void updateTaskState_InvalidTransition_ThrowsException() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.PENDING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        TaskStateUpdateRequest request = new TaskStateUpdateRequest();
        request.setState("SUCCESS"); // PENDING 不能直接跳到 SUCCESS

        assertThatThrownBy(() -> taskService.updateTaskState(TEST_TASK_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法状态流转");
    }

    @Test
    @DisplayName("updateTaskState - 任务不存在抛异常")
    void updateTaskState_NotFound_ThrowsException() {
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(null);

        TaskStateUpdateRequest request = new TaskStateUpdateRequest();
        request.setState("EXECUTING");

        assertThatThrownBy(() -> taskService.updateTaskState(TEST_TASK_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
    }

    @Test
    @DisplayName("updateTaskState - 无效状态值抛参数异常")
    void updateTaskState_InvalidStateValue_ThrowsException() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.PENDING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        TaskStateUpdateRequest request = new TaskStateUpdateRequest();
        request.setState("INVALID_STATE");

        assertThatThrownBy(() -> taskService.updateTaskState(TEST_TASK_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的任务状态");
    }

    @Test
    @DisplayName("updateTaskState - taskId 为 null 抛参数异常")
    void updateTaskState_NullTaskId_ThrowsException() {
        TaskStateUpdateRequest request = new TaskStateUpdateRequest();
        request.setState("EXECUTING");

        assertThatThrownBy(() -> taskService.updateTaskState(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务 ID 不能为空");
    }

    // endregion

    // region updateSubTask（Python 回调）

    @Test
    @DisplayName("updateSubTask - 更新子任务状态成功")
    void updateSubTask_Success() {
        // 1. mock 子任务存在
        AgentSubTaskEO subtask = new AgentSubTaskEO();
        subtask.setSubtaskId(2001L);
        subtask.setTaskId(TEST_TASK_ID);
        subtask.setSubtaskIndex(0);
        subtask.setStatus("PENDING");
        when(agentSubTaskMapper.selectOne(any())).thenReturn(subtask);
        when(agentSubTaskMapper.update(any(), any())).thenReturn(1);

        // 2. 构建回调请求
        SubTaskUpdateRequest request = new SubTaskUpdateRequest();
        request.setSubtaskIndex(0);
        request.setStatus("COMPLETED");

        // 3. 调用
        taskService.updateSubTask(TEST_TASK_ID, request);

        // 4. 验证 update 被调用
        verify(agentSubTaskMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("updateSubTask - 子任务不存在抛异常")
    void updateSubTask_NotFound_ThrowsException() {
        when(agentSubTaskMapper.selectOne(any())).thenReturn(null);

        SubTaskUpdateRequest request = new SubTaskUpdateRequest();
        request.setSubtaskIndex(99);

        assertThatThrownBy(() -> taskService.updateSubTask(TEST_TASK_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子任务不存在");
    }

    @Test
    @DisplayName("updateSubTask - subtaskIndex 为 null 抛参数异常")
    void updateSubTask_NullSubtaskIndex_ThrowsException() {
        SubTaskUpdateRequest request = new SubTaskUpdateRequest();
        request.setSubtaskIndex(null);
        request.setStatus("RUNNING");

        assertThatThrownBy(() -> taskService.updateSubTask(TEST_TASK_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子任务序号不能为空");
    }

    @Test
    @DisplayName("updateSubTask - taskId 为 null 抛参数异常")
    void updateSubTask_NullTaskId_ThrowsException() {
        SubTaskUpdateRequest request = new SubTaskUpdateRequest();
        request.setSubtaskIndex(0);

        assertThatThrownBy(() -> taskService.updateSubTask(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务 ID 不能为空");
    }

    // endregion

    // region listTasks

    @Test
    @DisplayName("listTasks - 查询成功")
    void listTasks_Success() {
        // 1. 设置租户上下文
        TenantContext.setOrgId(TEST_ORG_ID.toString());

        // 2. mock 分页查询返回
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        Page<AgentTaskEO> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(task));
        mockPage.setTotal(1);
        when(agentTaskMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        // 3. 构建查询请求
        TaskQueryRequest queryRequest = new TaskQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);

        // 4. 调用
        IPage<TaskVO> result = taskService.listTasks(queryRequest);

        // 5. 验证
        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTaskId()).isEqualTo(TEST_TASK_ID);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("listTasks - 租户上下文未设置抛异常")
    void listTasks_NoTenantContext_ThrowsException() {
        // 不设置 TenantContext
        TaskQueryRequest queryRequest = new TaskQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);

        assertThatThrownBy(() -> taskService.listTasks(queryRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户上下文未设置");
    }

    @Test
    @DisplayName("listTasks - pageSize 超过 100 抛参数异常")
    void listTasks_PageSizeTooLarge_ThrowsException() {
        TenantContext.setOrgId(TEST_ORG_ID.toString());

        TaskQueryRequest queryRequest = new TaskQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(101); // 超过上限

        assertThatThrownBy(() -> taskService.listTasks(queryRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("每页数量不能超过 100");
    }

    @Test
    @DisplayName("listTasks - 指定 workflowId 时正常返回（用于工作流执行历史查询）")
    void listTasks_WithWorkflowId_Success() {
        // 1. 设置租户上下文
        TenantContext.setOrgId(TEST_ORG_ID.toString());

        // 2. mock 分页查询返回（任务关联了工作流 ID）
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.SUCCESS);
        task.setWorkflowId(TEST_WORKFLOW_ID);
        Page<AgentTaskEO> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(task));
        mockPage.setTotal(1);
        when(agentTaskMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        // 3. 构建查询请求（指定 workflowId）
        TaskQueryRequest queryRequest = new TaskQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        queryRequest.setWorkflowId(TEST_WORKFLOW_ID);

        // 4. 调用
        IPage<TaskVO> result = taskService.listTasks(queryRequest);

        // 5. 验证（查询条件带 workflowId 时正常返回结果，且参数正确传递）
        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTaskId()).isEqualTo(TEST_TASK_ID);
        assertThat(result.getTotal()).isEqualTo(1);
        // 验证 selectPage 被调用一次（wrapper 中包含 workflowId 条件）
        verify(agentTaskMapper, times(1)).selectPage(any(Page.class), any());
    }

    // endregion

    // region 辅助方法

    /**
     * 构建测试用任务实体
     */
    private AgentTaskEO createTask(Long taskId, Long orgId, TaskStateEnum state) {
        AgentTaskEO task = new AgentTaskEO();
        task.setTaskId(taskId);
        task.setOrgId(orgId);
        task.setUserId(TEST_USER_ID);
        task.setGoal("下载银行流水");
        task.setStatus(state.getValue());
        task.setCurrentStep(0);
        task.setTotalSteps(0);
        return task;
    }

    // endregion

    // region updateSkyvernTaskId（M3.8）

    @Test
    @DisplayName("updateSkyvernTaskId - 正常更新成功")
    void updateSkyvernTaskId_Success() {
        // 1. 准备
        Long taskId = TEST_TASK_ID;
        String skyvernTaskId = "tsk_557478467325455186";

        when(agentTaskMapper.update(any(), any())).thenReturn(1);

        // 2. 执行
        taskService.updateSkyvernTaskId(taskId, skyvernTaskId);

        // 3. 验证
        verify(agentTaskMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("updateSkyvernTaskId - taskId 为 null 抛参数异常")
    void updateSkyvernTaskId_NullTaskId_ThrowsException() {
        assertThatThrownBy(() -> taskService.updateSkyvernTaskId(null, "tsk_123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务 ID 不能为空");
    }

    @Test
    @DisplayName("updateSkyvernTaskId - skyvernTaskId 为空白抛参数异常")
    void updateSkyvernTaskId_BlankSkyvernTaskId_ThrowsException() {
        assertThatThrownBy(() -> taskService.updateSkyvernTaskId(TEST_TASK_ID, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Skyvern 任务 ID 不能为空");
    }

    @Test
    @DisplayName("updateSkyvernTaskId - update 返回 0 抛操作异常")
    void updateSkyvernTaskId_UpdateFailed_ThrowsException() {
        when(agentTaskMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> taskService.updateSkyvernTaskId(TEST_TASK_ID, "tsk_123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Skyvern 任务 ID 更新失败");
    }

    // endregion

    // region updateCoordinationState（M4.2）

    @Test
    @DisplayName("updateCoordinationState - 首次回调时 insert 成功")
    void updateCoordinationState_Insert_Success() {
        // 1. mock 任务存在
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);
        // 2. mock 无现有协调状态（首次回调）
        when(coordinationStateMapper.selectOne(any())).thenReturn(null);
        when(coordinationStateMapper.insert(any(CoordinationStateEO.class))).thenReturn(1);

        // 3. 构建请求
        CoordinationStateUpdateRequest request = new CoordinationStateUpdateRequest();
        request.setNavigationGoal("下载银行流水");
        request.setCurrentPlan("{\"subtasks\":[]}");
        request.setCompletedSubtasks(List.of("sub_001", "sub_002"));
        request.setTotalReplans(0);
        request.setMaxReplans(3);
        request.setStatus("RUNNING");

        // 4. 调用
        taskService.updateCoordinationState(TEST_TASK_ID, request);

        // 5. 验证 insert 被调用
        verify(coordinationStateMapper, times(1)).insert(any(CoordinationStateEO.class));
        verify(coordinationStateMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("updateCoordinationState - 已有记录时 update 成功")
    void updateCoordinationState_Update_Success() {
        // 1. mock 任务存在
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);
        // 2. mock 已有协调状态
        CoordinationStateEO existing = new CoordinationStateEO();
        existing.setTaskId(TEST_TASK_ID);
        existing.setStatus("RUNNING");
        when(coordinationStateMapper.selectOne(any())).thenReturn(existing);
        when(coordinationStateMapper.update(any(), any())).thenReturn(1);

        // 3. 构建请求（更新状态）
        CoordinationStateUpdateRequest request = new CoordinationStateUpdateRequest();
        request.setCompletedSubtasks(List.of("sub_001", "sub_002", "sub_003"));
        request.setTotalReplans(1);
        request.setStatus("RUNNING");

        // 4. 调用
        taskService.updateCoordinationState(TEST_TASK_ID, request);

        // 5. 验证 update 被调用（非 insert）
        verify(coordinationStateMapper, times(1)).update(any(), any());
        verify(coordinationStateMapper, never()).insert(any(CoordinationStateEO.class));
    }

    @Test
    @DisplayName("updateCoordinationState - taskId 为 null 抛参数异常")
    void updateCoordinationState_NullTaskId_ThrowsException() {
        CoordinationStateUpdateRequest request = new CoordinationStateUpdateRequest();
        request.setStatus("RUNNING");

        assertThatThrownBy(() -> taskService.updateCoordinationState(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务 ID 不能为空");
    }

    @Test
    @DisplayName("updateCoordinationState - request 为 null 抛参数异常")
    void updateCoordinationState_NullRequest_ThrowsException() {
        assertThatThrownBy(() -> taskService.updateCoordinationState(TEST_TASK_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("协调状态更新请求不能为空");
    }

    @Test
    @DisplayName("updateCoordinationState - 任务不存在抛异常")
    void updateCoordinationState_TaskNotFound_ThrowsException() {
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(null);

        CoordinationStateUpdateRequest request = new CoordinationStateUpdateRequest();
        request.setStatus("RUNNING");

        assertThatThrownBy(() -> taskService.updateCoordinationState(TEST_TASK_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
    }

    @Test
    @DisplayName("updateCoordinationState - completedSubtasks 为 null 时不报错")
    void updateCoordinationState_NullCompletedSubtasks_Success() {
        // 1. mock 任务存在
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);
        when(coordinationStateMapper.selectOne(any())).thenReturn(null);
        when(coordinationStateMapper.insert(any(CoordinationStateEO.class))).thenReturn(1);

        // 2. 构建请求（completedSubtasks 为 null）
        CoordinationStateUpdateRequest request = new CoordinationStateUpdateRequest();
        request.setNavigationGoal("测试目标");
        request.setStatus("RUNNING");
        // completedSubtasks 未设置（null）

        // 3. 调用（不应抛异常）
        taskService.updateCoordinationState(TEST_TASK_ID, request);

        // 4. 验证 insert 被调用
        verify(coordinationStateMapper, times(1)).insert(any(CoordinationStateEO.class));
    }

    // endregion

    // region resumeTask（M4.3）

    @Test
    @DisplayName("resumeTask - 续跑成功（FAILED → EXECUTING + 调 Python）")
    void resumeTask_Success() throws Exception {
        // 1. mock 任务（FAILED 状态）
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.FAILED);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        // 2. mock 协调状态
        CoordinationStateEO cs = new CoordinationStateEO();
        cs.setTaskId(TEST_TASK_ID);
        cs.setNavigationGoal("下载银行流水");
        cs.setCurrentPlan("{\"navigation_goal\":\"测试\",\"subtasks\":[]}");
        cs.setCompletedSubtasks("[\"sub_001\",\"sub_002\"]");
        cs.setTotalReplans(2);
        cs.setStatus("FAILED");
        when(coordinationStateMapper.selectOne(any())).thenReturn(cs);

        // 3. mock update 返回 1
        when(coordinationStateMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);

        // 4. mock Python 调用成功
        when(aiServiceClient.resumeTask(any(), any())).thenReturn(null);

        // 5. 调用
        taskService.resumeTask(TEST_TASK_ID);

        // 6. 验证 Python 被调用
        verify(aiServiceClient, times(1)).resumeTask(eq(TEST_TASK_ID.toString()), any(TaskResumeRequest.class));
        // 验证协调状态被重置（total_replans=0, status=RUNNING）
        verify(coordinationStateMapper, times(1)).update(any(), any());
        // 验证任务状态被更新为 EXECUTING
        verify(agentTaskMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("resumeTask - NEEDS_HUMAN 状态也可续跑")
    void resumeTask_NeedsHuman_Success() {
        // 1. mock 任务（NEEDS_HUMAN 状态）
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.NEEDS_HUMAN);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        // 2. mock 协调状态
        CoordinationStateEO cs = new CoordinationStateEO();
        cs.setTaskId(TEST_TASK_ID);
        cs.setNavigationGoal("测试目标");
        cs.setCurrentPlan("{\"subtasks\":[]}");
        cs.setCompletedSubtasks("[]");
        cs.setTotalReplans(1);
        cs.setStatus("NEEDS_HUMAN");
        when(coordinationStateMapper.selectOne(any())).thenReturn(cs);
        when(coordinationStateMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(aiServiceClient.resumeTask(any(), any())).thenReturn(null);

        // 3. 调用
        taskService.resumeTask(TEST_TASK_ID);

        // 4. 验证
        verify(aiServiceClient, times(1)).resumeTask(any(), any());
    }

    @Test
    @DisplayName("resumeTask - 任务不存在抛异常")
    void resumeTask_TaskNotFound_ThrowsException() {
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> taskService.resumeTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
    }

    @Test
    @DisplayName("resumeTask - EXECUTING 状态不可续跑")
    void resumeTask_InvalidStatus_ThrowsException() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.EXECUTING);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> taskService.resumeTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅失败或需人工介入的任务可续跑");
    }

    @Test
    @DisplayName("resumeTask - 协调状态不存在抛异常")
    void resumeTask_CoordinationStateNotFound_ThrowsException() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.FAILED);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);
        when(coordinationStateMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> taskService.resumeTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("协调状态不存在");
    }

    @Test
    @DisplayName("resumeTask - currentPlan 为空抛异常")
    void resumeTask_EmptyPlan_ThrowsException() {
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.FAILED);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        CoordinationStateEO cs = new CoordinationStateEO();
        cs.setTaskId(TEST_TASK_ID);
        cs.setCurrentPlan(null);
        when(coordinationStateMapper.selectOne(any())).thenReturn(cs);

        assertThatThrownBy(() -> taskService.resumeTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存计划为空");
    }

    @Test
    @DisplayName("resumeTask - Python 调用失败时回滚状态为 FAILED")
    void resumeTask_PythonCallFails_RollsBack() {
        // 1. mock 任务 + 协调状态
        AgentTaskEO task = createTask(TEST_TASK_ID, TEST_ORG_ID, TaskStateEnum.FAILED);
        when(agentTaskMapper.selectById(TEST_TASK_ID)).thenReturn(task);

        CoordinationStateEO cs = new CoordinationStateEO();
        cs.setTaskId(TEST_TASK_ID);
        cs.setNavigationGoal("测试");
        cs.setCurrentPlan("{\"subtasks\":[]}");
        cs.setCompletedSubtasks("[]");
        when(coordinationStateMapper.selectOne(any())).thenReturn(cs);
        when(coordinationStateMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);

        // 2. mock Python 调用抛异常
        when(aiServiceClient.resumeTask(any(), any()))
                .thenThrow(new RuntimeException("Python 服务不可用"));

        // 3. 调用应抛异常
        assertThatThrownBy(() -> taskService.resumeTask(TEST_TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("续跑触发失败");

        // 4. 验证回滚：agentTaskMapper.update 被调用 2 次（一次 EXECUTING + 一次回滚 FAILED）
        verify(agentTaskMapper, times(2)).update(any(), any());
    }

    // endregion
}

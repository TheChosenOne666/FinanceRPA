package com.finrpa.workflows.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.workflows.constant.WorkflowConstant;
import com.finrpa.workflows.dto.request.WorkflowAddRequest;
import com.finrpa.workflows.dto.request.WorkflowQueryRequest;
import com.finrpa.workflows.dto.request.WorkflowUpdateRequest;
import com.finrpa.workflows.dto.response.WorkflowVO;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import com.finrpa.workflows.mapper.WorkflowTemplateMapper;
import com.finrpa.workflows.validator.WorkflowValidator;
import com.finrpa.agent.mapper.AgentTaskMapper;
import com.finrpa.auth.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WorkflowServiceImpl 单元测试
 *
 * <p>覆盖工作流模板 CRUD 与 queryByWorkflowId 查询逻辑。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    /** 测试用 workflowId（雪花算法 ID） */
    private static final Long TEST_WORKFLOW_ID = 2082333099000000099L;

    @Mock
    private WorkflowTemplateMapper workflowTemplateMapper;

    @Mock
    private WorkflowValidator workflowValidator;

    /** Agent 任务 Mapper（M7.5 引入：查询工作流执行次数） */
    @Mock
    private AgentTaskMapper agentTaskMapper;

    /** 用户 Mapper（M7.5 引入：查询创建人姓名） */
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    // region createWorkflow

    @Test
    @DisplayName("创建模板 - 成功")
    void createWorkflow_Success() {
        // 1. 名称不重复
        when(workflowTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 2. 插入成功
        when(workflowTemplateMapper.insert(any(WorkflowTemplateEO.class))).thenReturn(1);

        WorkflowAddRequest request = buildAddRequest("银行流水下载", "banking", "medium");
        WorkflowVO result = workflowService.createWorkflow(request, null);

        assertThat(result.getName()).isEqualTo("银行流水下载");
        assertThat(result.getIndustry()).isEqualTo("banking");
        assertThat(result.getRiskLevel()).isEqualTo("medium");
        assertThat(result.getVersion()).isEqualTo("1.0.0");
        assertThat(result.getEnabled()).isEqualTo(1);
        // 2. 校验器被调用一次
        verify(workflowValidator, times(1)).validate(request);
        verify(workflowTemplateMapper, times(1)).insert(any(WorkflowTemplateEO.class));
    }

    @Test
    @DisplayName("创建模板 - 名称重复抛异常")
    void createWorkflow_DuplicateName() {
        when(workflowTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        WorkflowAddRequest request = buildAddRequest("银行流水下载", "banking", "medium");
        assertThatThrownBy(() -> workflowService.createWorkflow(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板已存在");
        // 2. 不应再调用 insert
        verify(workflowTemplateMapper, never()).insert(any(WorkflowTemplateEO.class));
    }

    @Test
    @DisplayName("创建模板 - 风险等级为空时默认 medium")
    void createWorkflow_DefaultRiskLevel() {
        when(workflowTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(workflowTemplateMapper.insert(any(WorkflowTemplateEO.class))).thenReturn(1);

        WorkflowAddRequest request = buildAddRequest("测试模板", "banking", null);
        WorkflowVO result = workflowService.createWorkflow(request, null);

        assertThat(result.getRiskLevel()).isEqualTo("medium");
    }

    @Test
    @DisplayName("创建模板 - params 为空时默认空数组")
    void createWorkflow_DefaultParams() {
        when(workflowTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(workflowTemplateMapper.insert(any(WorkflowTemplateEO.class))).thenReturn(1);

        WorkflowAddRequest request = buildAddRequest("测试模板", "banking", "low");
        request.setParams(null);
        WorkflowVO result = workflowService.createWorkflow(request, null);

        assertThat(result.getParams()).isEqualTo("[]");
    }

    @Test
    @DisplayName("创建模板 - 插入失败抛异常")
    void createWorkflow_InsertFailed() {
        when(workflowTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(workflowTemplateMapper.insert(any(WorkflowTemplateEO.class))).thenReturn(0);

        WorkflowAddRequest request = buildAddRequest("测试模板", "banking", "low");
        assertThatThrownBy(() -> workflowService.createWorkflow(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板创建失败");
    }

    // endregion

    // region updateWorkflow

    @Test
    @DisplayName("更新模板 - 成功")
    void updateWorkflow_Success() {
        // 1. 模板存在
        WorkflowTemplateEO existing = buildEntity("旧名称", "banking", "low");
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        // 2. 更新成功
        doReturn(1).when(workflowTemplateMapper)
                .updateById(org.mockito.ArgumentMatchers.<WorkflowTemplateEO>any());

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        request.setDescription("新描述");
        request.setRiskLevel("high");
        request.setEnabled(0);

        boolean result = workflowService.updateWorkflow(TEST_WORKFLOW_ID, request);

        assertThat(result).isTrue();
        // 3. 验证 updateById 传入的实体保留了 workflowId（业务主键）
        verify(workflowTemplateMapper).updateById(org.mockito.ArgumentMatchers.<WorkflowTemplateEO>argThat(e ->
                e.getWorkflowId().equals(TEST_WORKFLOW_ID)
                        && "新描述".equals(e.getDescription())
                        && "high".equals(e.getRiskLevel())
                        && e.getEnabled() == 0));
    }

    @Test
    @DisplayName("更新模板 - 不存在抛异常")
    void updateWorkflow_NotFound() {
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        request.setDescription("xxx");

        assertThatThrownBy(() -> workflowService.updateWorkflow(TEST_WORKFLOW_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板不存在");
    }

    @Test
    @DisplayName("更新模板 - 非法风险等级抛异常")
    void updateWorkflow_InvalidRiskLevel() {
        WorkflowTemplateEO existing = buildEntity("旧名称", "banking", "low");
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        request.setRiskLevel("invalid_level");

        assertThatThrownBy(() -> workflowService.updateWorkflow(TEST_WORKFLOW_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("风险等级不合法");
    }

    @Test
    @DisplayName("更新模板 - 更新失败抛异常")
    void updateWorkflow_UpdateFailed() {
        WorkflowTemplateEO existing = buildEntity("旧名称", "banking", "low");
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        doReturn(0).when(workflowTemplateMapper)
                .updateById(org.mockito.ArgumentMatchers.<WorkflowTemplateEO>any());

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        request.setEnabled(0);

        assertThatThrownBy(() -> workflowService.updateWorkflow(TEST_WORKFLOW_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板更新失败");
    }

    // endregion

    // region getWorkflow

    @Test
    @DisplayName("查询模板详情 - 成功")
    void getWorkflow_Success() {
        WorkflowTemplateEO entity = buildEntity("银行流水下载", "banking", "medium");
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        WorkflowVO result = workflowService.getWorkflow(TEST_WORKFLOW_ID);

        assertThat(result.getName()).isEqualTo("银行流水下载");
        assertThat(result.getIndustry()).isEqualTo("banking");
    }

    @Test
    @DisplayName("查询模板详情 - 不存在抛异常")
    void getWorkflow_NotFound() {
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> workflowService.getWorkflow(TEST_WORKFLOW_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板不存在");
    }

    // endregion

    // region listWorkflows

    @Test
    @DisplayName("分页查询 - 成功")
    void listWorkflows_Success() {
        WorkflowTemplateEO entity = buildEntity("银行流水下载", "banking", "medium");
        Page<WorkflowTemplateEO> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(workflowTemplateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        WorkflowQueryRequest request = new WorkflowQueryRequest();
        request.setCurrent(1);
        request.setPageSize(10);

        IPage<WorkflowVO> result = workflowService.listWorkflows(request);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("银行流水下载");
    }

    @Test
    @DisplayName("分页查询 - 按行业筛选")
    void listWorkflows_FilterByIndustry() {
        Page<WorkflowTemplateEO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(workflowTemplateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        WorkflowQueryRequest request = new WorkflowQueryRequest();
        request.setCurrent(1);
        request.setPageSize(10);
        request.setIndustry("securities");

        IPage<WorkflowVO> result = workflowService.listWorkflows(request);

        assertThat(result.getRecords()).isEmpty();
    }

    // endregion

    // region deleteWorkflow

    @Test
    @DisplayName("删除模板 - 成功")
    void deleteWorkflow_Success() {
        WorkflowTemplateEO existing = buildEntity("银行流水下载", "banking", "medium");
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(workflowTemplateMapper.deleteById(any(Long.class))).thenReturn(1);

        boolean result = workflowService.deleteWorkflow(TEST_WORKFLOW_ID);

        assertThat(result).isTrue();
        // 2. 验证使用数据库主键 id 调用 deleteById
        verify(workflowTemplateMapper).deleteById(existing.getId());
    }

    @Test
    @DisplayName("删除模板 - 不存在抛异常")
    void deleteWorkflow_NotFound() {
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> workflowService.deleteWorkflow(TEST_WORKFLOW_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板不存在");
    }

    @Test
    @DisplayName("删除模板 - 删除失败抛异常")
    void deleteWorkflow_DeleteFailed() {
        WorkflowTemplateEO existing = buildEntity("银行流水下载", "banking", "medium");
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(workflowTemplateMapper.deleteById(any(Long.class))).thenReturn(0);

        assertThatThrownBy(() -> workflowService.deleteWorkflow(TEST_WORKFLOW_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流模板删除失败");
    }

    // endregion

    // region queryByWorkflowId

    @Test
    @DisplayName("queryByWorkflowId - 存在返回实体")
    void queryByWorkflowId_Found() {
        WorkflowTemplateEO entity = buildEntity("银行流水下载", "banking", "medium");
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        WorkflowTemplateEO result = workflowService.queryByWorkflowId(TEST_WORKFLOW_ID);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("银行流水下载");
    }

    @Test
    @DisplayName("queryByWorkflowId - 不存在返回 null")
    void queryByWorkflowId_NotFound() {
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        WorkflowTemplateEO result = workflowService.queryByWorkflowId(TEST_WORKFLOW_ID);

        assertThat(result).isNull();
    }

    // endregion

    // region registerBuiltinWorkflows

    @Test
    @DisplayName("内置模板注册 - 全新插入 6 个")
    void registerBuiltinWorkflows_AllNew() {
        // 1. 所有模板名称都不存在
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // 2. 名称唯一性检查通过（count=0）
        when(workflowTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 3. 插入成功
        when(workflowTemplateMapper.insert(any(WorkflowTemplateEO.class))).thenReturn(1);

        // 4. 执行注册
        workflowService.registerBuiltinWorkflows();

        // 5. 验证插入 6 次，更新 0 次
        verify(workflowTemplateMapper, times(6)).insert(any(WorkflowTemplateEO.class));
        verify(workflowTemplateMapper, never())
                .updateById(org.mockito.ArgumentMatchers.<WorkflowTemplateEO>any());
    }

    @Test
    @DisplayName("内置模板注册 - 已存在则更新（不动 enabled）")
    void registerBuiltinWorkflows_ExistingUpdate() {
        // 1. 第一个模板（银行流水下载）已存在，其他 5 个不存在
        WorkflowTemplateEO existing = buildEntity("银行流水下载", "banking", "medium");
        existing.setEnabled(0);  // 用户已禁用
        when(workflowTemplateMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existing)   // 第一次 queryByName 返回已存在
                .thenReturn(null)       // 后续 5 个返回 null
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(null);
        // 2. 名称唯一性检查（仅对不存在的 5 个模板调用）
        when(workflowTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 3. 更新与插入都成功
        doReturn(1).when(workflowTemplateMapper)
                .updateById(org.mockito.ArgumentMatchers.<WorkflowTemplateEO>any());
        when(workflowTemplateMapper.insert(any(WorkflowTemplateEO.class))).thenReturn(1);

        // 4. 执行注册
        workflowService.registerBuiltinWorkflows();

        // 5. 验证：1 次 update + 5 次 insert
        verify(workflowTemplateMapper, times(1))
                .updateById(org.mockito.ArgumentMatchers.<WorkflowTemplateEO>any());
        verify(workflowTemplateMapper, times(5)).insert(any(WorkflowTemplateEO.class));
    }

    @Test
    @DisplayName("内置模板注册 - 共 6 个模板且名称/行业/风险等级齐全")
    void registerBuiltinWorkflows_TemplateCount() {
        // 1. 验证 WorkflowConstant 中定义了 6 个模板
        assertThat(WorkflowConstant.BUILTIN_TEMPLATES).hasSize(6);

        // 2. 验证名称、行业、风险等级齐全
        assertThat(WorkflowConstant.BUILTIN_TEMPLATES)
                .extracting(WorkflowTemplateEO::getName)
                .containsExactly(
                        WorkflowConstant.TEMPLATE_BANK_STATEMENT,
                        WorkflowConstant.TEMPLATE_CROSS_BANK_RECONCILE,
                        WorkflowConstant.TEMPLATE_CORPORATE_LOAN,
                        WorkflowConstant.TEMPLATE_POLICY_APPLICATION,
                        WorkflowConstant.TEMPLATE_CLAIM_REVIEW,
                        WorkflowConstant.TEMPLATE_SECURITIES_ORDER
                );
        assertThat(WorkflowConstant.BUILTIN_TEMPLATES)
                .extracting(WorkflowTemplateEO::getIndustry)
                .contains("banking", "insurance", "securities");
        assertThat(WorkflowConstant.BUILTIN_TEMPLATES)
                .extracting(WorkflowTemplateEO::getRiskLevel)
                .contains("medium", "high", "critical");
    }

    // endregion

    // region 辅助方法

    /**
     * 构建创建请求
     *
     * @param name       模板名
     * @param industry   行业
     * @param riskLevel  风险等级
     * @return 创建请求 DTO
     */
    private WorkflowAddRequest buildAddRequest(String name, String industry, String riskLevel) {
        WorkflowAddRequest request = new WorkflowAddRequest();
        request.setName(name);
        request.setDescription("测试模板描述");
        request.setIndustry(industry);
        request.setRiskLevel(riskLevel);
        request.setParams("[{\"name\":\"account\",\"type\":\"string\",\"required\":true}]");
        request.setSteps("[{\"skill\":\"login\",\"params_mapping\":{\"account\":\"{{account}}\"}}]");
        return request;
    }

    /**
     * 构建模板实体（含数据库主键 id）
     *
     * @param name      模板名
     * @param industry  行业
     * @param riskLevel 风险等级
     * @return 模板实体
     */
    private WorkflowTemplateEO buildEntity(String name, String industry, String riskLevel) {
        WorkflowTemplateEO entity = new WorkflowTemplateEO();
        entity.setId(1L);
        entity.setWorkflowId(TEST_WORKFLOW_ID);
        entity.setName(name);
        entity.setDescription("测试模板描述");
        entity.setIndustry(industry);
        entity.setRiskLevel(riskLevel);
        entity.setParams("[]");
        entity.setSteps("[{\"skill\":\"login\"}]");
        entity.setVersion("1.0.0");
        entity.setEnabled(1);
        entity.setDeleted(0);
        return entity;
    }

    // endregion
}

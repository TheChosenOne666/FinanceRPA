package com.finrpa.workflows.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.workflows.dto.request.WorkflowAddRequest;
import com.finrpa.workflows.dto.request.WorkflowQueryRequest;
import com.finrpa.workflows.dto.request.WorkflowUpdateRequest;
import com.finrpa.workflows.dto.response.WorkflowVO;
import com.finrpa.workflows.entity.WorkflowTemplateEO;

/**
 * 工作流模板服务接口
 *
 * <p>负责模板的 CRUD 与校验，敏感参数加密在 {@code WorkflowTriggerService} 中处理。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface WorkflowService {

    /**
     * 创建工作流模板
     *
     * @param request 创建请求
     * @param userId  创建人用户 ID（内置模板注册时传 null）
     * @return 创建后的模板视图
     */
    WorkflowVO createWorkflow(WorkflowAddRequest request, Long userId);

    /**
     * 更新工作流模板
     *
     * @param workflowId 工作流业务 ID
     * @param request    更新请求
     * @return 是否更新成功
     */
    boolean updateWorkflow(Long workflowId, WorkflowUpdateRequest request);

    /**
     * 查询工作流模板详情
     *
     * @param workflowId 工作流业务 ID
     * @return 模板视图
     */
    WorkflowVO getWorkflow(Long workflowId);

    /**
     * 分页查询工作流模板列表
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    IPage<WorkflowVO> listWorkflows(WorkflowQueryRequest queryRequest);

    /**
     * 删除工作流模板（逻辑删除）
     *
     * @param workflowId 工作流业务 ID
     * @return 是否删除成功
     */
    boolean deleteWorkflow(Long workflowId);

    /**
     * 根据 workflowId 查询模板实体（未被逻辑删除）
     *
     * <p>供触发执行流程使用，返回原始实体而非脱敏 VO。</p>
     *
     * @param workflowId 工作流业务 ID
     * @return 模板实体；不存在返回 null
     */
    WorkflowTemplateEO queryByWorkflowId(Long workflowId);

    /**
     * 启动时注册 6 个内置金融场景工作流模板（upsert，不动 enabled 状态）
     *
     * <p>与 SkillMetaInitializer 配合：Skill 元数据先注册（@Order(20)），工作流模板后注册（@Order(30)），
     * 确保 WorkflowValidator 校验 Skill 引用合法性时 Skill 已存在。</p>
     */
    void registerBuiltinWorkflows();
}

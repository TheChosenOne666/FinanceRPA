package com.finrpa.workflows.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.workflows.dto.request.WorkflowAddRequest;
import com.finrpa.workflows.dto.request.WorkflowQueryRequest;
import com.finrpa.workflows.dto.request.WorkflowUpdateRequest;
import com.finrpa.workflows.dto.response.WorkflowVO;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import com.finrpa.workflows.mapper.WorkflowTemplateMapper;
import com.finrpa.workflows.service.WorkflowService;
import com.finrpa.workflows.validator.WorkflowValidator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 工作流模板服务实现
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class WorkflowServiceImpl implements WorkflowService {

    @Resource
    private WorkflowTemplateMapper workflowTemplateMapper;

    @Resource
    private WorkflowValidator workflowValidator;

    // region 增删改查

    @Override
    public WorkflowVO createWorkflow(WorkflowAddRequest request) {
        // 1. 校验
        workflowValidator.validate(request);

        // 2. 校验名称唯一
        Long count = workflowTemplateMapper.selectCount(
                new LambdaQueryWrapper<WorkflowTemplateEO>()
                        .eq(WorkflowTemplateEO::getName, request.getName())
        );
        ThrowUtils.throwIf(count > 0, ErrorCode.WORKFLOW_ALREADY_EXISTS,
                "工作流模板已存在: " + request.getName());

        // 3. 构建实体
        WorkflowTemplateEO entity = new WorkflowTemplateEO();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setIndustry(request.getIndustry());
        entity.setRiskLevel(request.getRiskLevel() == null ? "medium" : request.getRiskLevel());
        entity.setParams(request.getParams() == null ? "[]" : request.getParams());
        entity.setSteps(request.getSteps());
        entity.setVersion("1.0.0");
        entity.setEnabled(1);

        // 4. 插入数据库
        int rows = workflowTemplateMapper.insert(entity);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "工作流模板创建失败");

        log.info("工作流模板创建成功: name={}, workflowId={}", entity.getName(), entity.getWorkflowId());
        return convertToVO(entity);
    }

    @Override
    public boolean updateWorkflow(Long workflowId, WorkflowUpdateRequest request) {
        // 1. 查询是否存在
        WorkflowTemplateEO existing = queryByWorkflowId(workflowId);
        ThrowUtils.throwIf(existing == null, ErrorCode.WORKFLOW_NOT_FOUND,
                "工作流模板不存在: " + workflowId);

        // 2. 构建更新实体（按非空字段更新）
        WorkflowTemplateEO update = new WorkflowTemplateEO();
        update.setWorkflowId(existing.getWorkflowId());
        if (StringUtils.isNotBlank(request.getDescription())) {
            update.setDescription(request.getDescription());
        }
        if (StringUtils.isNotBlank(request.getRiskLevel())) {
            ThrowUtils.throwIf(
                    com.finrpa.workflows.enums.RiskLevelEnum.getEnumByValue(request.getRiskLevel()) == null,
                    ErrorCode.PARAMS_ERROR, "风险等级不合法: " + request.getRiskLevel()
            );
            update.setRiskLevel(request.getRiskLevel());
        }
        if (request.getParams() != null) {
            update.setParams(request.getParams());
        }
        if (request.getSteps() != null) {
            update.setSteps(request.getSteps());
        }
        if (request.getEnabled() != null) {
            update.setEnabled(request.getEnabled());
        }

        // 3. 更新
        int rows = workflowTemplateMapper.updateById(update);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "工作流模板更新失败");

        log.info("工作流模板更新成功: workflowId={}", workflowId);
        return true;
    }

    @Override
    public WorkflowVO getWorkflow(Long workflowId) {
        WorkflowTemplateEO entity = queryByWorkflowId(workflowId);
        ThrowUtils.throwIf(entity == null, ErrorCode.WORKFLOW_NOT_FOUND,
                "工作流模板不存在: " + workflowId);
        return convertToVO(entity);
    }

    @Override
    public IPage<WorkflowVO> listWorkflows(WorkflowQueryRequest queryRequest) {
        long current = queryRequest.getCurrent();
        long pageSize = queryRequest.getPageSize();

        // 1. 构建查询条件
        LambdaQueryWrapper<WorkflowTemplateEO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(queryRequest.getName())) {
            wrapper.like(WorkflowTemplateEO::getName, queryRequest.getName());
        }
        if (StringUtils.isNotBlank(queryRequest.getIndustry())) {
            wrapper.eq(WorkflowTemplateEO::getIndustry, queryRequest.getIndustry());
        }
        if (StringUtils.isNotBlank(queryRequest.getRiskLevel())) {
            wrapper.eq(WorkflowTemplateEO::getRiskLevel, queryRequest.getRiskLevel());
        }
        if (queryRequest.getEnabled() != null) {
            wrapper.eq(WorkflowTemplateEO::getEnabled, queryRequest.getEnabled());
        }
        wrapper.orderByDesc(WorkflowTemplateEO::getCreateTime);

        // 2. 分页查询
        Page<WorkflowTemplateEO> page = new Page<>(current, pageSize);
        IPage<WorkflowTemplateEO> entityPage = workflowTemplateMapper.selectPage(page, wrapper);

        // 3. 转换为 VO
        return entityPage.convert(this::convertToVO);
    }

    @Override
    public boolean deleteWorkflow(Long workflowId) {
        // 1. 查询是否存在
        WorkflowTemplateEO existing = queryByWorkflowId(workflowId);
        ThrowUtils.throwIf(existing == null, ErrorCode.WORKFLOW_NOT_FOUND,
                "工作流模板不存在: " + workflowId);

        // 2. 逻辑删除
        int rows = workflowTemplateMapper.deleteById(existing.getId());
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "工作流模板删除失败");

        log.info("工作流模板删除成功: workflowId={}", workflowId);
        return true;
    }

    // endregion

    // region 内部方法

    /**
     * 根据 workflowId 查询模板（未被逻辑删除）
     *
     * @param workflowId 工作流业务 ID
     * @return 模板实体；不存在返回 null
     */
    @Override
    public WorkflowTemplateEO queryByWorkflowId(Long workflowId) {
        return workflowTemplateMapper.selectOne(
                new LambdaQueryWrapper<WorkflowTemplateEO>()
                        .eq(WorkflowTemplateEO::getWorkflowId, workflowId)
        );
    }

    /**
     * 实体转 VO
     */
    private WorkflowVO convertToVO(WorkflowTemplateEO entity) {
        WorkflowVO vo = new WorkflowVO();
        vo.setWorkflowId(entity.getWorkflowId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setIndustry(entity.getIndustry());
        vo.setRiskLevel(entity.getRiskLevel());
        vo.setParams(entity.getParams());
        vo.setSteps(entity.getSteps());
        vo.setVersion(entity.getVersion());
        vo.setEnabled(entity.getEnabled());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    // endregion
}

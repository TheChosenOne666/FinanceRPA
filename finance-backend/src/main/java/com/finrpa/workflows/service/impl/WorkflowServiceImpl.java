package com.finrpa.workflows.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.agent.mapper.AgentTaskMapper;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.workflows.constant.WorkflowConstant;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** Agent 任务 Mapper（用于查询工作流执行次数） */
    @Resource
    private AgentTaskMapper agentTaskMapper;

    /** 用户 Mapper（用于查询创建人姓名） */
    @Resource
    private UserMapper userMapper;

    // region 增删改查

    @Override
    public WorkflowVO createWorkflow(WorkflowAddRequest request, Long userId) {
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
        entity.setCreateUserId(userId);

        // 4. 插入数据库
        int rows = workflowTemplateMapper.insert(entity);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "工作流模板创建失败");

        log.info("工作流模板创建成功: name={}, workflowId={}, userId={}", entity.getName(), entity.getWorkflowId(), userId);
        return convertToVO(entity, 0L, resolveUserName(userId));
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
        // 1. 查询执行次数
        long runCount = countRunsByWorkflowId(workflowId);
        // 2. 查询创建人姓名
        String createUser = resolveUserName(entity.getCreateUserId());
        return convertToVO(entity, runCount, createUser);
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

        // 3. 批量查询执行次数（避免 N+1）
        List<Long> workflowIds = entityPage.getRecords().stream()
                .map(WorkflowTemplateEO::getWorkflowId)
                .collect(Collectors.toList());
        Map<Long, Long> runCountMap = batchCountRuns(workflowIds);

        // 4. 批量查询创建人姓名（避免 N+1）
        List<Long> userIds = entityPage.getRecords().stream()
                .map(WorkflowTemplateEO::getCreateUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> userNameMap = batchResolveUserNames(userIds);

        // 5. 转换为 VO
        return entityPage.convert(entity -> {
            long runCount = runCountMap.getOrDefault(entity.getWorkflowId(), 0L);
            String createUser = entity.getCreateUserId() == null
                    ? null
                    : userNameMap.get(entity.getCreateUserId());
            return convertToVO(entity, runCount, createUser);
        });
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
     * 启动时注册 6 个内置金融场景工作流模板（upsert，不动 enabled 状态）
     *
     * <p>upsert 语义：
     * <ul>
     *   <li>不存在：校验 Skill 引用合法性后插入</li>
     *   <li>已存在：仅更新元数据字段（description / params / steps / version），不动 enabled</li>
     * </ul>
     * </p>
     */
    @Override
    public void registerBuiltinWorkflows() {
        // 1. 遍历 6 个内置模板常量
        int inserted = 0;
        int updated = 0;
        for (WorkflowTemplateEO builtin : WorkflowConstant.BUILTIN_TEMPLATES) {
            // 2. 按 name 查询是否已存在
            WorkflowTemplateEO existing = queryByName(builtin.getName());
            if (existing == null) {
                // 3. 不存在：校验 Skill 引用合法性后插入
                WorkflowAddRequest addRequest = new WorkflowAddRequest();
                addRequest.setName(builtin.getName());
                addRequest.setDescription(builtin.getDescription());
                addRequest.setIndustry(builtin.getIndustry());
                addRequest.setRiskLevel(builtin.getRiskLevel());
                addRequest.setParams(builtin.getParams());
                addRequest.setSteps(builtin.getSteps());
                // 3.1 复用 createWorkflow 的校验逻辑（包含 Skill 引用合法性，内置模板无创建人）
                createWorkflow(addRequest, null);
                inserted++;
            } else {
                // 4. 已存在：仅更新元数据字段，不动 enabled（避免启动时把用户禁用的模板重新启用）
                WorkflowTemplateEO update = new WorkflowTemplateEO();
                update.setWorkflowId(existing.getWorkflowId());
                update.setDescription(builtin.getDescription());
                update.setParams(builtin.getParams());
                update.setSteps(builtin.getSteps());
                update.setVersion(builtin.getVersion());
                workflowTemplateMapper.updateById(update);
                updated++;
            }
        }
        log.info("内置工作流模板注册完成: 新增 {} 个，更新 {} 个", inserted, updated);
    }

    /**
     * 按 name 查询未删除的工作流模板
     *
     * @param name 模板名称
     * @return 模板实体；不存在返回 null
     */
    private WorkflowTemplateEO queryByName(String name) {
        return workflowTemplateMapper.selectOne(
                new LambdaQueryWrapper<WorkflowTemplateEO>()
                        .eq(WorkflowTemplateEO::getName, name)
        );
    }

    /**
     * 实体转 VO（填充执行次数与创建人姓名）
     *
     * @param entity      工作流模板实体
     * @param runCount    执行次数
     * @param createUser  创建人姓名（null 表示系统创建）
     * @return 工作流模板视图
     */
    private WorkflowVO convertToVO(WorkflowTemplateEO entity, long runCount, String createUser) {
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
        vo.setCreateUser(createUser);
        vo.setRunCount(runCount);
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 查询单个工作流的执行次数
     *
     * @param workflowId 工作流业务 ID
     * @return 执行次数
     */
    private long countRunsByWorkflowId(Long workflowId) {
        if (workflowId == null) {
            return 0L;
        }
        List<Map<String, Object>> result = agentTaskMapper.countByWorkflowIds(List.of(workflowId));
        if (result == null || result.isEmpty()) {
            return 0L;
        }
        Object cnt = result.get(0).get("runCount");
        if (cnt instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    /**
     * 批量查询工作流执行次数（避免 N+1）
     *
     * @param workflowIds 工作流业务 ID 列表
     * @return Map: workflowId → 执行次数
     */
    private Map<Long, Long> batchCountRuns(List<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Map<String, Object>> result = agentTaskMapper.countByWorkflowIds(workflowIds);
        Map<Long, Long> map = new HashMap<>();
        if (result != null) {
            for (Map<String, Object> row : result) {
                Object wfId = row.get("workflowId");
                Object cnt = row.get("runCount");
                if (wfId instanceof Number n1 && cnt instanceof Number n2) {
                    map.put(n1.longValue(), n2.longValue());
                }
            }
        }
        return map;
    }

    /**
     * 根据用户 ID 查询姓名（null 返回 null，表示系统创建）
     *
     * @param userId 用户业务 ID
     * @return 用户姓名；userId 为 null 时返回 null
     */
    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        UserEO user = userMapper.selectByUserId(userId);
        return user != null ? user.getRealName() : null;
    }

    /**
     * 批量查询用户姓名（避免 N+1）
     *
     * @param userIds 用户业务 ID 列表
     * @return Map: userId → 用户姓名
     */
    private Map<Long, String> batchResolveUserNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<UserEO> users = userMapper.selectByUserIds(userIds);
        Map<Long, String> map = new HashMap<>();
        if (users != null) {
            for (UserEO user : users) {
                map.put(user.getUserId(), user.getRealName());
            }
        }
        return map;
    }

    // endregion
}

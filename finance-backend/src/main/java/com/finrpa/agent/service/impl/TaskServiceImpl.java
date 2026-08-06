package com.finrpa.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.agent.dto.request.CoordinationStateUpdateRequest;
import com.finrpa.agent.dto.request.SubTaskUpdateRequest;
import com.finrpa.agent.dto.request.TaskCreateRequest;
import com.finrpa.agent.dto.request.TaskQueryRequest;
import com.finrpa.agent.dto.request.TaskStateUpdateRequest;
import com.finrpa.agent.dto.response.SubTaskVO;
import com.finrpa.agent.dto.response.TaskDetailVO;
import com.finrpa.agent.dto.response.TaskVO;
import com.finrpa.agent.entity.AgentSubTaskEO;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.entity.CoordinationStateEO;
import com.finrpa.agent.enums.TaskStateEnum;
import com.finrpa.agent.event.TaskTerminalEvent;
import com.finrpa.agent.mapper.AgentSubTaskMapper;
import com.finrpa.agent.mapper.AgentTaskMapper;
import com.finrpa.agent.mapper.CoordinationStateMapper;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.TaskResumeRequest;
import com.finrpa.agent.service.TaskService;
import com.finrpa.agent.service.TaskStateMachine;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.entity.UserRoleEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.tenant.context.TenantContext;
import com.finrpa.tenant.entity.BusinessLineEO;
import com.finrpa.tenant.entity.DepartmentEO;
import com.finrpa.tenant.mapper.BusinessLineMapper;
import com.finrpa.tenant.mapper.DepartmentMapper;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import com.finrpa.workflows.mapper.WorkflowTemplateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务服务实现
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    /** 任务 Mapper */
    @Resource
    private AgentTaskMapper agentTaskMapper;

    /** 子任务 Mapper */
    @Resource
    private AgentSubTaskMapper agentSubTaskMapper;

    /** 协调状态 Mapper */
    @Resource
    private CoordinationStateMapper coordinationStateMapper;

    /** Python AI 服务客户端（M4.3 续跑调 Python） */
    @Resource
    private AiServiceClient aiServiceClient;

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    /** Spring 事件发布器（M8.1 任务终态时发布事件，触发大屏缓存失效） */
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    /** 用户 Mapper（用于填充任务触发人姓名） */
    @Resource
    private UserMapper userMapper;

    /** 工作流模板 Mapper（用于填充任务风险等级） */
    @Resource
    private WorkflowTemplateMapper workflowTemplateMapper;

    /** 权限服务（M7.6 三维度 RBAC：任务列表按业务线范围过滤 + 推断默认部门/业务线） */
    @Resource
    private PermissionService permissionService;

    /** 部门 Mapper（M7.6：批量填充任务部门名称） */
    @Resource
    private DepartmentMapper departmentMapper;

    /** 业务线 Mapper（M7.6：批量填充任务业务线名称） */
    @Resource
    private BusinessLineMapper businessLineMapper;

    // region 对外接口

    /**
     * 创建任务
     *
     * @param orgId   组织 ID
     * @param userId  用户 ID
     * @param request 任务创建请求
     * @return 任务实体
     */
    @Override
    public AgentTaskEO createTask(Long orgId, Long userId, TaskCreateRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(orgId == null || userId == null, ErrorCode.PARAMS_ERROR, "组织 ID 和用户 ID 不能为空");
        ThrowUtils.throwIf(request == null || request.getGoal() == null || request.getGoal().isBlank(),
                ErrorCode.PARAMS_ERROR, "任务目标不能为空");

        // 2. 构建任务实体
        AgentTaskEO task = new AgentTaskEO();
        task.setOrgId(orgId);
        task.setUserId(userId);
        task.setGoal(request.getGoal());
        task.setWorkflowId(request.getWorkflowId());
        task.setStatus(TaskStateEnum.PENDING.getValue());
        task.setCurrentStep(0);
        task.setTotalSteps(0);
        task.setMessage("任务已创建，等待执行");

        // 2.1 M7.6 三维度 RBAC：填充部门/业务线（前端传则用，不传则从用户主关联推断）
        Long businessLineId = request.getBusinessLineId();
        Long departmentId = request.getDepartmentId();
        if (businessLineId == null || departmentId == null) {
            UserRoleEO primary = permissionService.getPrimaryUserRole(userId);
            if (primary != null) {
                if (businessLineId == null) {
                    businessLineId = primary.getBusinessLineId();
                }
                if (departmentId == null) {
                    departmentId = primary.getDepartmentId();
                }
            }
        }
        task.setBusinessLineId(businessLineId);
        task.setDepartmentId(departmentId);

        // 3. 序列化参数为 JSON
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            try {
                task.setParams(objectMapper.writeValueAsString(request.getParams()));
            } catch (JsonProcessingException e) {
                log.error("任务参数序列化失败", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务参数序列化失败");
            }
        }

        // 4. 插入数据库
        int rows = agentTaskMapper.insert(task);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "任务创建失败");

        log.info("任务创建成功: taskId={}, orgId={}, userId={}, goal={}",
                task.getTaskId(), orgId, userId, request.getGoal());
        return task;
    }

    /**
     * 分页查询任务列表（自动按租户过滤）
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    @Override
    public IPage<TaskVO> listTasks(TaskQueryRequest queryRequest) {
        // 1. 获取当前租户 orgId
        String orgIdStr = TenantContext.getOrgId();
        ThrowUtils.throwIf(orgIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "租户上下文未设置");

        // 2. 构建查询条件
        LambdaQueryWrapper<AgentTaskEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTaskEO::getOrgId, Long.parseLong(orgIdStr));

        // 3. 状态筛选
        if (queryRequest.getStatus() != null && !queryRequest.getStatus().isBlank()) {
            wrapper.eq(AgentTaskEO::getStatus, queryRequest.getStatus());
        }

        // 4. 关键词搜索
        if (queryRequest.getSearchText() != null && !queryRequest.getSearchText().isBlank()) {
            wrapper.like(AgentTaskEO::getGoal, queryRequest.getSearchText());
        }

        // 5. 工作流模板 ID 筛选（用于查询某个工作流的执行历史）
        if (queryRequest.getWorkflowId() != null) {
            wrapper.eq(AgentTaskEO::getWorkflowId, queryRequest.getWorkflowId());
        }

        // 5.1 M7.6 三维度 RBAC：业务线/部门筛选
        if (queryRequest.getBusinessLineId() != null) {
            wrapper.eq(AgentTaskEO::getBusinessLineId, queryRequest.getBusinessLineId());
        }
        if (queryRequest.getDepartmentId() != null) {
            wrapper.eq(AgentTaskEO::getDepartmentId, queryRequest.getDepartmentId());
        }

        // 5.2 M7.6 三维度 RBAC：普通用户仅看本人关联的业务线/部门范围，org_admin 全组织可见
        String userIdStr = TenantContext.getUserId();
        if (userIdStr != null && !permissionService.isOrgAdmin(userIdStr)) {
            // 5.2.1 业务线范围：用户关联为 null（不限）则不加约束；否则限制 IN 范围
            Set<Long> bizLineIds = permissionService.getUserBusinessLineIds(userIdStr);
            if (bizLineIds != null) {
                if (bizLineIds.isEmpty()) {
                    // 无任何关联 → 仅能看到 business_line_id 为 NULL 的任务（兼容旧数据）
                    wrapper.isNull(AgentTaskEO::getBusinessLineId);
                } else {
                    // 包含 NULL（用户有不限业务线关联）已在 bizLineIds==null 分支处理，此处仅 IN
                    wrapper.in(AgentTaskEO::getBusinessLineId, bizLineIds)
                            .or().isNull(AgentTaskEO::getBusinessLineId);
                }
            }
        }

        // 6. 排序（默认按创建时间倒序）
        wrapper.orderByDesc(AgentTaskEO::getCreateTime);

        // 7. 分页查询
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        // 限制 pageSize 防止爬虫
        ThrowUtils.throwIf(size > 100, ErrorCode.PARAMS_ERROR, "每页数量不能超过 100");
        Page<AgentTaskEO> page = new Page<>(current, size);
        IPage<AgentTaskEO> taskPage = agentTaskMapper.selectPage(page, wrapper);

        // 8. 批量查询触发用户姓名（避免 N+1）
        List<Long> userIds = taskPage.getRecords().stream()
                .map(AgentTaskEO::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> userNameMap = batchResolveUserNames(userIds);

        // 9. 批量查询关联工作流模板的风险等级（避免 N+1）
        List<Long> workflowIds = taskPage.getRecords().stream()
                .map(AgentTaskEO::getWorkflowId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> riskLevelMap = batchResolveRiskLevels(workflowIds);

        // 9.1 M7.6 批量查询部门名称（避免 N+1）
        List<Long> deptIds = taskPage.getRecords().stream()
                .map(AgentTaskEO::getDepartmentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> deptNameMap = batchResolveDepartmentNames(deptIds);

        // 9.2 M7.6 批量查询业务线名称（避免 N+1）
        List<Long> bizLineIds = taskPage.getRecords().stream()
                .map(AgentTaskEO::getBusinessLineId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> bizLineNameMap = batchResolveBusinessLineNames(bizLineIds);

        // 10. 转换为 VO（填充 userName / durationMs / riskLevel / departmentName / businessLineName）
        IPage<TaskVO> voPage = taskPage.convert(task -> {
            TaskVO vo = new TaskVO();
            BeanUtils.copyProperties(task, vo);
            // 10.1 填充触发用户姓名
            vo.setUserName(userNameMap.get(task.getUserId()));
            // 10.2 计算耗时（仅终态任务）
            vo.setDurationMs(calculateDurationMs(task));
            // 10.3 填充风险等级（关联工作流模板）
            vo.setRiskLevel(task.getWorkflowId() != null ? riskLevelMap.get(task.getWorkflowId()) : null);
            // 10.4 M7.6 填充部门名称
            vo.setDepartmentName(task.getDepartmentId() != null ? deptNameMap.get(task.getDepartmentId()) : null);
            // 10.5 M7.6 填充业务线名称
            vo.setBusinessLineName(task.getBusinessLineId() != null ? bizLineNameMap.get(task.getBusinessLineId()) : null);
            return vo;
        });

        return voPage;
    }

    /**
     * 查询任务详情（含子任务列表）
     *
     * @param taskId 任务 ID
     * @return 任务详情视图
     */
    @Override
    public TaskDetailVO getTaskDetail(Long taskId) {
        // 1. 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");

        // 2. 查询任务（租户过滤由调用方保证，此处按 taskId 查询）
        AgentTaskEO task = agentTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");

        // 3. 校验租户权限
        String orgIdStr = TenantContext.getOrgId();
        if (orgIdStr != null) {
            ThrowUtils.throwIf(!task.getOrgId().equals(Long.parseLong(orgIdStr)),
                    ErrorCode.NO_AUTH_ERROR, "无权访问其他组织的任务");
        }

        // 4. 转换为详情 VO
        TaskDetailVO detailVO = new TaskDetailVO();
        BeanUtils.copyProperties(task, detailVO);

        // 4.1 填充触发用户姓名
        if (task.getUserId() != null) {
            UserEO user = userMapper.selectByUserId(task.getUserId());
            if (user != null) {
                detailVO.setUserName(user.getRealName());
            }
        }

        // 4.2 计算耗时（仅终态任务）
        detailVO.setDurationMs(calculateDurationMs(task));

        // 4.3 填充风险等级（关联工作流模板）
        if (task.getWorkflowId() != null) {
            WorkflowTemplateEO workflow = workflowTemplateMapper.selectById(task.getWorkflowId());
            if (workflow != null) {
                detailVO.setRiskLevel(workflow.getRiskLevel());
            }
        }

        // 5. 查询子任务列表
        LambdaQueryWrapper<AgentSubTaskEO> subtaskWrapper = new LambdaQueryWrapper<>();
        subtaskWrapper.eq(AgentSubTaskEO::getTaskId, taskId)
                .orderByAsc(AgentSubTaskEO::getSubtaskIndex);
        List<AgentSubTaskEO> subtasks = agentSubTaskMapper.selectList(subtaskWrapper);

        // 6. 转换子任务 VO
        List<SubTaskVO> subtaskVOs = new ArrayList<>();
        for (AgentSubTaskEO subtask : subtasks) {
            SubTaskVO vo = new SubTaskVO();
            BeanUtils.copyProperties(subtask, vo);
            subtaskVOs.add(vo);
        }
        detailVO.setSubtasks(subtaskVOs);

        return detailVO;
    }

    /**
     * 终止任务
     *
     * @param taskId 任务 ID
     */
    @Override
    public void abortTask(Long taskId) {
        // 1. 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");

        // 2. 查询任务
        AgentTaskEO task = agentTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");

        // 3. 校验租户权限
        String orgIdStr = TenantContext.getOrgId();
        if (orgIdStr != null) {
            ThrowUtils.throwIf(!task.getOrgId().equals(Long.parseLong(orgIdStr)),
                    ErrorCode.NO_AUTH_ERROR, "无权操作其他组织的任务");
        }

        // 4. 校验状态流转
        TaskStateEnum currentState = TaskStateEnum.getEnumByValue(task.getStatus());
        TaskStateEnum targetState = TaskStateEnum.ABORTED;
        if (TaskStateMachine.isTerminal(currentState)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务已结束，无法终止");
        }
        TaskStateMachine.validateTransition(currentState, targetState);

        // 5. 更新状态
        LambdaUpdateWrapper<AgentTaskEO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentTaskEO::getTaskId, taskId)
                .set(AgentTaskEO::getStatus, targetState.getValue())
                .set(AgentTaskEO::getMessage, "任务已被用户终止");
        int rows = agentTaskMapper.update(null, wrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "任务终止失败");

        log.info("任务终止成功: taskId={}", taskId);

        // 6. 任务终止（终态）发布事件，触发大屏缓存失效（M8.1）
        applicationEventPublisher.publishEvent(
                new TaskTerminalEvent(this, taskId, task.getOrgId(),
                        targetState.getValue(), task.getStatus()));
    }

    /**
     * 任务续跑（M4.3：从断点继续执行）
     *
     * <p>流程：
     * <ol>
     *   <li>查询任务 + 校验租户权限 + 校验状态（仅 FAILED/NEEDS_HUMAN 可续跑）</li>
     *   <li>查询协调状态 → 读取 completed_subtasks + navigation_goal + current_plan</li>
     *   <li>重置协调状态：total_replans=0, status=RUNNING, error_message=null</li>
     *   <li>更新任务状态为 EXECUTING</li>
     *   <li>调 Python POST /api/v1/ai/tasks/{taskId}/resume</li>
     * </ol>
     *
     * @param taskId 任务 ID
     */
    @Override
    public void resumeTask(Long taskId) {
        // 1. 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");

        // 2. 查询任务 + 校验租户权限
        AgentTaskEO task = agentTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        String orgIdStr = TenantContext.getOrgId();
        if (orgIdStr != null) {
            ThrowUtils.throwIf(!task.getOrgId().equals(Long.parseLong(orgIdStr)),
                    ErrorCode.NO_AUTH_ERROR, "无权操作其他组织的任务");
        }

        // 3. 校验状态：仅 FAILED / NEEDS_HUMAN 可续跑
        TaskStateEnum currentState = TaskStateEnum.getEnumByValue(task.getStatus());
        if (currentState != TaskStateEnum.FAILED && currentState != TaskStateEnum.NEEDS_HUMAN) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "仅失败或需人工介入的任务可续跑，当前状态: " + task.getStatus());
        }

        // 4. 查询协调状态
        LambdaQueryWrapper<CoordinationStateEO> csQuery = new LambdaQueryWrapper<>();
        csQuery.eq(CoordinationStateEO::getTaskId, taskId);
        CoordinationStateEO cs = coordinationStateMapper.selectOne(csQuery);
        ThrowUtils.throwIf(cs == null, ErrorCode.OPERATION_ERROR,
                "协调状态不存在，无法续跑（任务可能未通过 Coordinator 执行）");
        ThrowUtils.throwIf(cs.getCurrentPlan() == null || cs.getCurrentPlan().isEmpty(),
                ErrorCode.OPERATION_ERROR, "已存计划为空，无法续跑");

        // 5. 解析 completed_subtasks JSON → List<String>
        List<String> completedSubtasks = new ArrayList<>();
        if (cs.getCompletedSubtasks() != null && !cs.getCompletedSubtasks().isEmpty()) {
            try {
                completedSubtasks = objectMapper.readValue(
                        cs.getCompletedSubtasks(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                );
            } catch (Exception e) {
                log.error("解析 completed_subtasks 失败: taskId={}", taskId, e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "已完成子任务列表解析失败");
            }
        }

        log.info("任务续跑准备: taskId={}, completed={}, totalReplans={}, currentPlanLen={}",
                taskId, completedSubtasks.size(), cs.getTotalReplans(), cs.getCurrentPlan().length());

        // 6. 重置协调状态：total_replans=0, status=RUNNING, error_message=null
        LambdaUpdateWrapper<CoordinationStateEO> csUpdate = new LambdaUpdateWrapper<>();
        csUpdate.eq(CoordinationStateEO::getTaskId, taskId)
                .set(CoordinationStateEO::getTotalReplans, 0)
                .set(CoordinationStateEO::getStatus, "RUNNING")
                .set(CoordinationStateEO::getErrorMessage, null);
        coordinationStateMapper.update(null, csUpdate);

        // 7. 更新任务状态为 EXECUTING
        LambdaUpdateWrapper<AgentTaskEO> taskUpdate = new LambdaUpdateWrapper<>();
        taskUpdate.eq(AgentTaskEO::getTaskId, taskId)
                .set(AgentTaskEO::getStatus, TaskStateEnum.EXECUTING.getValue())
                .set(AgentTaskEO::getMessage, "任务续跑中（从断点继续）");
        agentTaskMapper.update(null, taskUpdate);

        // 8. 调 Python 续跑
        TaskResumeRequest resumeRequest = new TaskResumeRequest();
        resumeRequest.setTaskId(taskId.toString());
        resumeRequest.setOrgId(task.getOrgId().toString());
        resumeRequest.setNavigationGoal(cs.getNavigationGoal());
        resumeRequest.setCompletedSubtasks(completedSubtasks);
        resumeRequest.setCurrentPlan(cs.getCurrentPlan());

        try {
            aiServiceClient.resumeTask(taskId.toString(), resumeRequest);
            log.info("任务续跑已触发: taskId={}", taskId);
        } catch (Exception e) {
            log.error("调 Python 续跑失败: taskId={}", taskId, e);
            // 回滚状态为 FAILED
            LambdaUpdateWrapper<AgentTaskEO> rollback = new LambdaUpdateWrapper<>();
            rollback.eq(AgentTaskEO::getTaskId, taskId)
                    .set(AgentTaskEO::getStatus, TaskStateEnum.FAILED.getValue())
                    .set(AgentTaskEO::getMessage, "续跑触发失败: " + e.getMessage());
            agentTaskMapper.update(null, rollback);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "续跑触发失败: " + e.getMessage());
        }
    }

    // endregion

    // region 内部回调接口（Python → Java）

    /**
     * 更新任务状态（Python 回调）
     *
     * @param taskId  任务 ID
     * @param request 状态更新请求
     */
    @Override
    public void updateTaskState(Long taskId, TaskStateUpdateRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        ThrowUtils.throwIf(request == null || request.getState() == null,
                ErrorCode.PARAMS_ERROR, "状态更新请求不能为空");

        // 2. 查询任务（内部回调无租户上下文，按 taskId 直接查询）
        AgentTaskEO task = agentTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");

        // 3. 校验状态流转
        TaskStateEnum currentState = TaskStateEnum.getEnumByValue(task.getStatus());
        TaskStateEnum targetState = TaskStateEnum.getEnumByValue(request.getState());
        ThrowUtils.throwIf(targetState == null, ErrorCode.PARAMS_ERROR, "无效的任务状态: " + request.getState());

        // 终态不允许更新
        if (TaskStateMachine.isTerminal(currentState)) {
            log.warn("任务已处于终态，忽略状态更新: taskId={}, current={}, target={}",
                    taskId, currentState.getValue(), targetState.getValue());
            return;
        }

        // 校验流转合法性（非终态时）
        if (currentState != null && !TaskStateMachine.canTransition(currentState, targetState)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "非法状态流转: " + task.getStatus() + " → " + request.getState());
        }

        // 4. 更新任务状态
        LambdaUpdateWrapper<AgentTaskEO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentTaskEO::getTaskId, taskId)
                .set(AgentTaskEO::getStatus, targetState.getValue());

        // 更新步骤信息
        if (request.getCurrentStep() != null) {
            wrapper.set(AgentTaskEO::getCurrentStep, request.getCurrentStep());
        }
        if (request.getTotalSteps() != null) {
            wrapper.set(AgentTaskEO::getTotalSteps, request.getTotalSteps());
        }
        if (request.getMessage() != null) {
            wrapper.set(AgentTaskEO::getMessage, request.getMessage());
        }
        if (request.getErrorMessage() != null) {
            wrapper.set(AgentTaskEO::getErrorMessage, request.getErrorMessage());
        }

        int rows = agentTaskMapper.update(null, wrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "任务状态更新失败");

        log.info("任务状态更新成功: taskId={}, {} → {}", taskId, task.getStatus(), targetState.getValue());

        // 5. 任务进入终态时发布事件，触发大屏缓存失效（M8.1）
        if (TaskStateMachine.isTerminal(targetState)) {
            applicationEventPublisher.publishEvent(
                    new TaskTerminalEvent(this, taskId, task.getOrgId(),
                            targetState.getValue(), task.getStatus()));
        }
    }

    /**
     * 更新子任务状态（Python 回调）
     *
     * @param taskId  任务 ID
     * @param request 子任务更新请求
     */
    @Override
    public void updateSubTask(Long taskId, SubTaskUpdateRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "子任务更新请求不能为空");
        ThrowUtils.throwIf(request.getSubtaskIndex() == null, ErrorCode.PARAMS_ERROR, "子任务序号不能为空");

        // 2. 查询子任务（按 taskId + subtaskIndex）
        LambdaQueryWrapper<AgentSubTaskEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSubTaskEO::getTaskId, taskId)
                .eq(AgentSubTaskEO::getSubtaskIndex, request.getSubtaskIndex());
        AgentSubTaskEO subtask = agentSubTaskMapper.selectOne(wrapper);
        ThrowUtils.throwIf(subtask == null, ErrorCode.NOT_FOUND_ERROR,
                "子任务不存在: taskId=" + taskId + ", index=" + request.getSubtaskIndex());

        // 3. 更新子任务状态
        LambdaUpdateWrapper<AgentSubTaskEO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AgentSubTaskEO::getSubtaskId, subtask.getSubtaskId())
                .set(AgentSubTaskEO::getStatus, request.getStatus());

        if (request.getErrorMessage() != null) {
            updateWrapper.set(AgentSubTaskEO::getErrorMessage, request.getErrorMessage());
        }

        // 序列化结果数据
        if (request.getResultData() != null && !request.getResultData().isEmpty()) {
            try {
                updateWrapper.set(AgentSubTaskEO::getResultData, objectMapper.writeValueAsString(request.getResultData()));
            } catch (JsonProcessingException e) {
                log.error("子任务结果数据序列化失败", e);
            }
        }

        // 根据状态设置时间
        if ("RUNNING".equals(request.getStatus())) {
            // 子任务开始执行，不设置 completedAt
        } else if ("COMPLETED".equals(request.getStatus()) || "FAILED".equals(request.getStatus())
                || "SKIPPED".equals(request.getStatus()) || "REPLANNED".equals(request.getStatus())) {
            // 子任务结束
        }

        int rows = agentSubTaskMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "子任务状态更新失败");

        log.info("子任务状态更新成功: taskId={}, subtaskIndex={}, status={}",
                taskId, request.getSubtaskIndex(), request.getStatus());
    }

    /**
     * 更新 Skyvern 任务 ID（M3.8 引入，Python 调 Skyvern API 后回传）
     *
     * @param taskId         Java 侧任务 ID
     * @param skyvernTaskId  Skyvern 返回的任务 ID
     */
    @Override
    public void updateSkyvernTaskId(Long taskId, String skyvernTaskId) {
        // 1. 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        ThrowUtils.throwIf(skyvernTaskId == null || skyvernTaskId.isBlank(),
                ErrorCode.PARAMS_ERROR, "Skyvern 任务 ID 不能为空");

        // 2. 更新 skyvern_task_id
        LambdaUpdateWrapper<AgentTaskEO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentTaskEO::getTaskId, taskId)
                .set(AgentTaskEO::getSkyvernTaskId, skyvernTaskId);
        int rows = agentTaskMapper.update(null, wrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "Skyvern 任务 ID 更新失败");

        log.info("Skyvern 任务 ID 更新成功: taskId={}, skyvernTaskId={}", taskId, skyvernTaskId);
    }

    /**
     * 更新协调状态（Python 回调内部接口，M4.2 引入）
     *
     * <p>按 taskId upsert 到 rpa_agent_coordination_state 表。
     * 首次回调时 insert，后续回调时 update 已有记录。</p>
     *
     * @param taskId  任务 ID
     * @param request 协调状态更新请求
     */
    @Override
    public void updateCoordinationState(Long taskId, CoordinationStateUpdateRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "协调状态更新请求不能为空");

        // 2. 查询任务获取 orgId（内部回调无租户上下文）
        AgentTaskEO task = agentTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        Long orgId = task.getOrgId();

        // 3. 查询现有协调状态（按 taskId）
        LambdaQueryWrapper<CoordinationStateEO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CoordinationStateEO::getTaskId, taskId);
        CoordinationStateEO existing = coordinationStateMapper.selectOne(queryWrapper);

        // 4. 序列化 completedSubtasks 列表为 JSON
        String completedSubtasksJson = null;
        if (request.getCompletedSubtasks() != null) {
            try {
                completedSubtasksJson = objectMapper.writeValueAsString(request.getCompletedSubtasks());
            } catch (JsonProcessingException e) {
                log.error("协调状态 completedSubtasks 序列化失败: taskId={}", taskId, e);
            }
        }

        // 5. upsert
        if (existing == null) {
            // insert
            CoordinationStateEO stateEO = new CoordinationStateEO();
            stateEO.setTaskId(taskId);
            stateEO.setOrgId(orgId);
            stateEO.setNavigationGoal(request.getNavigationGoal());
            stateEO.setCurrentPlan(request.getCurrentPlan());
            stateEO.setCompletedSubtasks(completedSubtasksJson);
            stateEO.setTotalReplans(request.getTotalReplans() != null ? request.getTotalReplans() : 0);
            stateEO.setMaxReplans(request.getMaxReplans() != null ? request.getMaxReplans() : 3);
            stateEO.setStatus(request.getStatus() != null ? request.getStatus() : "RUNNING");
            stateEO.setErrorMessage(request.getErrorMessage());
            int rows = coordinationStateMapper.insert(stateEO);
            ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "协调状态创建失败");
            log.info("协调状态创建成功: taskId={}, status={}", taskId, stateEO.getStatus());
        } else {
            // update
            LambdaUpdateWrapper<CoordinationStateEO> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(CoordinationStateEO::getTaskId, taskId);
            if (request.getNavigationGoal() != null) {
                updateWrapper.set(CoordinationStateEO::getNavigationGoal, request.getNavigationGoal());
            }
            if (request.getCurrentPlan() != null) {
                updateWrapper.set(CoordinationStateEO::getCurrentPlan, request.getCurrentPlan());
            }
            if (completedSubtasksJson != null) {
                updateWrapper.set(CoordinationStateEO::getCompletedSubtasks, completedSubtasksJson);
            }
            if (request.getTotalReplans() != null) {
                updateWrapper.set(CoordinationStateEO::getTotalReplans, request.getTotalReplans());
            }
            if (request.getMaxReplans() != null) {
                updateWrapper.set(CoordinationStateEO::getMaxReplans, request.getMaxReplans());
            }
            if (request.getStatus() != null) {
                updateWrapper.set(CoordinationStateEO::getStatus, request.getStatus());
            }
            if (request.getErrorMessage() != null) {
                updateWrapper.set(CoordinationStateEO::getErrorMessage, request.getErrorMessage());
            }
            int rows = coordinationStateMapper.update(null, updateWrapper);
            ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "协调状态更新失败");
            log.info("协调状态更新成功: taskId={}, status={}", taskId, request.getStatus());
        }
    }

    // endregion

    // region 辅助方法

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

    /**
     * 计算任务耗时（仅终态任务计算，进行中任务返回 null）
     *
     * <p>历史数据兜底：V28 迁移前 update_time 不会自动刷新（与 create_time 相同），
     * 此时无法计算真实耗时，返回 null 由前端展示「-」，避免显示「0s」误导用户。</p>
     *
     * @param task 任务实体
     * @return 耗时毫秒数；非终态任务或无有效耗时数据时返回 null
     */
    private Long calculateDurationMs(AgentTaskEO task) {
        if (task == null || task.getStatus() == null || task.getCreateTime() == null || task.getUpdateTime() == null) {
            return null;
        }
        TaskStateEnum state = TaskStateEnum.getEnumByValue(task.getStatus());
        if (state == null || !TaskStateMachine.isTerminal(state)) {
            return null;
        }
        // 历史数据兜底：update_time 与 create_time 相同说明未经历过有效 UPDATE，耗时不可信
        if (task.getUpdateTime().getTime() == task.getCreateTime().getTime()) {
            return null;
        }
        return task.getUpdateTime().getTime() - task.getCreateTime().getTime();
    }

    /**
     * 批量查询工作流模板风险等级（避免 N+1）
     *
     * @param workflowIds 工作流模板 ID 列表
     * @return Map: workflowId → riskLevel
     */
    private Map<Long, String> batchResolveRiskLevels(List<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return new HashMap<>();
        }
        List<WorkflowTemplateEO> workflows = workflowTemplateMapper.selectBatchIds(workflowIds);
        Map<Long, String> map = new HashMap<>();
        if (workflows != null) {
            for (WorkflowTemplateEO wf : workflows) {
                map.put(wf.getWorkflowId(), wf.getRiskLevel());
            }
        }
        return map;
    }

    /**
     * M7.6 批量查询部门名称（避免 N+1）
     *
     * @param deptIds 部门业务 ID 列表
     * @return Map: deptId → deptName
     */
    private Map<Long, String> batchResolveDepartmentNames(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return new HashMap<>();
        }
        List<DepartmentEO> departments = departmentMapper.selectBatchIds(deptIds);
        Map<Long, String> map = new HashMap<>();
        if (departments != null) {
            for (DepartmentEO dept : departments) {
                map.put(dept.getDeptId(), dept.getDeptName());
            }
        }
        return map;
    }

    /**
     * M7.6 批量查询业务线名称（避免 N+1）
     *
     * @param businessLineIds 业务线业务 ID 列表
     * @return Map: businessLineId → businessLineName
     */
    private Map<Long, String> batchResolveBusinessLineNames(List<Long> businessLineIds) {
        if (businessLineIds == null || businessLineIds.isEmpty()) {
            return new HashMap<>();
        }
        List<BusinessLineEO> businessLines = businessLineMapper.selectBatchIds(businessLineIds);
        Map<Long, String> map = new HashMap<>();
        if (businessLines != null) {
            for (BusinessLineEO bl : businessLines) {
                map.put(bl.getBusinessLineId(), bl.getBusinessLineName());
            }
        }
        return map;
    }

    // endregion
}

package com.finrpa.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.agent.dto.request.SubTaskUpdateRequest;
import com.finrpa.agent.dto.request.TaskCreateRequest;
import com.finrpa.agent.dto.request.TaskQueryRequest;
import com.finrpa.agent.dto.request.TaskStateUpdateRequest;
import com.finrpa.agent.dto.response.SubTaskVO;
import com.finrpa.agent.dto.response.TaskDetailVO;
import com.finrpa.agent.dto.response.TaskVO;
import com.finrpa.agent.entity.AgentSubTaskEO;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.enums.TaskStateEnum;
import com.finrpa.agent.mapper.AgentSubTaskMapper;
import com.finrpa.agent.mapper.AgentTaskMapper;
import com.finrpa.agent.service.TaskService;
import com.finrpa.agent.service.TaskStateMachine;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.tenant.context.TenantContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

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

        // 6. 排序（默认按创建时间倒序）
        wrapper.orderByDesc(AgentTaskEO::getCreateTime);

        // 7. 分页查询
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        // 限制 pageSize 防止爬虫
        ThrowUtils.throwIf(size > 100, ErrorCode.PARAMS_ERROR, "每页数量不能超过 100");
        Page<AgentTaskEO> page = new Page<>(current, size);
        IPage<AgentTaskEO> taskPage = agentTaskMapper.selectPage(page, wrapper);

        // 8. 转换为 VO
        IPage<TaskVO> voPage = taskPage.convert(task -> {
            TaskVO vo = new TaskVO();
            BeanUtils.copyProperties(task, vo);
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

    // endregion
}

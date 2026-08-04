package com.finrpa.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.agent.service.TaskService;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.llm.constant.LlmConstant;
import com.finrpa.llm.dto.request.NeedsHumanQueryRequest;
import com.finrpa.llm.dto.request.NeedsHumanReportRequest;
import com.finrpa.llm.dto.request.NeedsHumanResolveRequest;
import com.finrpa.llm.dto.response.NeedsHumanQueueVO;
import com.finrpa.llm.entity.NeedsHumanQueueEO;
import com.finrpa.llm.mapper.NeedsHumanQueueMapper;
import com.finrpa.llm.service.NeedsHumanService;
import com.finrpa.tenant.entity.BusinessLineEO;
import com.finrpa.tenant.mapper.BusinessLineMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * NEEDS_HUMAN 队列服务实现
 *
 * <p>实现 NEEDS_HUMAN 事件的入队、查询与处置。
 * 处置时通过 {@link TaskService} 触发任务续跑或终止。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class NeedsHumanServiceImpl implements NeedsHumanService {

    /** NEEDS_HUMAN 队列 Mapper */
    @Resource
    private NeedsHumanQueueMapper needsHumanQueueMapper;

    /** 任务服务（处置时调 resumeTask / abortTask） */
    @Resource
    private TaskService taskService;

    /** 业务线 Mapper（用于填充队列 VO 的 businessLineName） */
    @Resource
    private BusinessLineMapper businessLineMapper;

    // region 入队

    /**
     * 上报 NEEDS_HUMAN 事件入队（Python 回调）
     *
     * @param request 上报请求
     * @return 是否入队成功
     */
    @Override
    public boolean reportNeedsHuman(NeedsHumanReportRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "NEEDS_HUMAN 上报请求不能为空");
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().isBlank(),
                ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");

        // 2. 构建实体
        NeedsHumanQueueEO queue = new NeedsHumanQueueEO();
        queue.setTaskId(parseLong(request.getTaskId()));
        queue.setOrgId(parseLong(request.getOrgId()));
        queue.setBusinessLineId(parseLong(request.getBusinessLineId()));
        queue.setSubtaskId(request.getSubtaskId());
        queue.setContextName(request.getContextName() != null && !request.getContextName().isBlank()
                ? request.getContextName() : LlmConstant.DEFAULT_CONTEXT);
        queue.setScreenshotUrl(request.getScreenshotUrl());
        queue.setLlmRawOutput(request.getLlmRawOutput());
        queue.setValidationError(request.getValidationError());
        queue.setAttempts(request.getAttempts() != null ? request.getAttempts() : 0);
        queue.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);

        // 3. 保存
        int rows = needsHumanQueueMapper.insert(queue);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "NEEDS_HUMAN 事件入队失败");

        log.info("NEEDS_HUMAN 事件入队成功: queueId={}, taskId={}, context={}, attempts={}",
                queue.getQueueId(), queue.getTaskId(), queue.getContextName(), queue.getAttempts());
        return true;
    }

    // endregion

    // region 查询

    /**
     * 分页查询 NEEDS_HUMAN 队列
     *
     * @param queryRequest 查询请求（含分页参数 + 业务线筛选）
     * @param orgId        组织 ID（租户隔离）
     * @return 分页结果
     */
    @Override
    public IPage<NeedsHumanQueueVO> listNeedsHuman(NeedsHumanQueryRequest queryRequest, Long orgId) {
        // 1. 构建查询条件
        QueryWrapper<NeedsHumanQueueEO> wrapper = new QueryWrapper<>();
        if (orgId != null) {
            wrapper.eq("org_id", orgId);
        }
        if (queryRequest != null) {
            if (queryRequest.getStatus() != null && !queryRequest.getStatus().isBlank()) {
                wrapper.eq("status", queryRequest.getStatus());
            }
            if (queryRequest.getTaskId() != null) {
                wrapper.eq("task_id", queryRequest.getTaskId());
            }
            if (queryRequest.getBusinessLineId() != null) {
                wrapper.eq("business_line_id", queryRequest.getBusinessLineId());
            }
        }
        wrapper.orderByDesc("create_time");

        // 2. 分页查询
        long current = queryRequest != null ? queryRequest.getCurrent() : 1;
        long size = queryRequest != null ? queryRequest.getPageSize() : 10;
        ThrowUtils.throwIf(size > 100, ErrorCode.PARAMS_ERROR, "每页数量不能超过 100");
        Page<NeedsHumanQueueEO> page = new Page<>(current, size);
        IPage<NeedsHumanQueueEO> queuePage = needsHumanQueueMapper.selectPage(page, wrapper);

        // 3. 批量查询业务线名称（避免 N+1）
        Map<Long, String> bizLineNameMap = loadBusinessLineNames(queuePage.getRecords());

        // 4. 转换为 VO
        return queuePage.convert(eo -> {
            NeedsHumanQueueVO vo = new NeedsHumanQueueVO();
            BeanUtils.copyProperties(eo, vo);
            if (eo.getBusinessLineId() != null) {
                vo.setBusinessLineName(bizLineNameMap.get(eo.getBusinessLineId()));
            }
            return vo;
        });
    }

    /**
     * 查询 NEEDS_HUMAN 事件详情
     *
     * @param queueId 队列业务 ID
     * @param orgId   组织 ID（租户隔离）
     * @return 队列详情 VO
     */
    @Override
    public NeedsHumanQueueVO getNeedsHumanDetail(Long queueId, Long orgId) {
        ThrowUtils.throwIf(queueId == null, ErrorCode.PARAMS_ERROR, "队列 ID 不能为空");

        // 1. 查询队列条目
        QueryWrapper<NeedsHumanQueueEO> wrapper = new QueryWrapper<>();
        wrapper.eq("queue_id", queueId);
        if (orgId != null) {
            wrapper.eq("org_id", orgId);
        }
        NeedsHumanQueueEO queue = needsHumanQueueMapper.selectOne(wrapper);
        ThrowUtils.throwIf(queue == null, ErrorCode.NOT_FOUND_ERROR, "NEEDS_HUMAN 事件不存在");

        // 2. 转换为 VO
        NeedsHumanQueueVO vo = new NeedsHumanQueueVO();
        BeanUtils.copyProperties(queue, vo);

        // 3. 填充业务线名称
        if (queue.getBusinessLineId() != null) {
            Map<Long, String> bizLineNameMap = loadBusinessLineNames(Collections.singletonList(queue));
            vo.setBusinessLineName(bizLineNameMap.get(queue.getBusinessLineId()));
        }
        return vo;
    }

    // endregion

    // region 处置

    /**
     * 处置 NEEDS_HUMAN 事件
     *
     * @param queueId        队列业务 ID
     * @param resolveRequest 处置请求（含 action）
     * @param userId         处置人用户 ID
     * @param orgId          组织 ID（租户隔离）
     * @return 是否处置成功
     */
    @Override
    public boolean resolveNeedsHuman(Long queueId, NeedsHumanResolveRequest resolveRequest, Long userId, Long orgId) {
        // 1. 参数校验
        ThrowUtils.throwIf(queueId == null, ErrorCode.PARAMS_ERROR, "队列 ID 不能为空");
        ThrowUtils.throwIf(resolveRequest == null || resolveRequest.getAction() == null,
                ErrorCode.PARAMS_ERROR, "处置动作不能为空");

        String action = resolveRequest.getAction();
        boolean isSkip = LlmConstant.RESOLVE_ACTION_SKIP.equals(action);
        boolean isManual = LlmConstant.RESOLVE_ACTION_MANUAL.equals(action);
        boolean isAbort = LlmConstant.RESOLVE_ACTION_ABORT.equals(action);
        ThrowUtils.throwIf(!isSkip && !isManual && !isAbort,
                ErrorCode.PARAMS_ERROR, "无效的处置动作: " + action);

        // 2. 查询队列条目 + 校验状态
        QueryWrapper<NeedsHumanQueueEO> wrapper = new QueryWrapper<>();
        wrapper.eq("queue_id", queueId);
        if (orgId != null) {
            wrapper.eq("org_id", orgId);
        }
        NeedsHumanQueueEO queue = needsHumanQueueMapper.selectOne(wrapper);
        ThrowUtils.throwIf(queue == null, ErrorCode.NOT_FOUND_ERROR, "NEEDS_HUMAN 事件不存在");
        ThrowUtils.throwIf(!LlmConstant.NEEDS_HUMAN_STATUS_PENDING.equals(queue.getStatus()),
                ErrorCode.OPERATION_ERROR, "该事件已处置，不可重复操作");

        // 3. 执行处置动作（先执行，成功后再标记为 RESOLVED）
        Long taskId = queue.getTaskId();
        if (isAbort) {
            // 终止任务
            ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 为空，无法终止");
            taskService.abortTask(taskId);
            log.info("NEEDS_HUMAN 处置 [abort]: queueId={}, taskId={}, userId={}",
                    queueId, taskId, userId);
        } else {
            // skip / manual → 续跑任务
            ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 为空，无法续跑");
            taskService.resumeTask(taskId);
            log.info("NEEDS_HUMAN 处置 [{}]: queueId={}, taskId={}, userId={}",
                    action, queueId, taskId, userId);
        }

        // 4. 标记为 RESOLVED
        UpdateWrapper<NeedsHumanQueueEO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("queue_id", queueId)
                .set("status", LlmConstant.NEEDS_HUMAN_STATUS_RESOLVED)
                .set("resolve_action", action)
                .set("resolved_by", userId)
                .set("resolved_at", new Timestamp(System.currentTimeMillis()));
        int rows = needsHumanQueueMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "NEEDS_HUMAN 处置状态更新失败");

        log.info("NEEDS_HUMAN 事件已处置: queueId={}, action={}, userId={}", queueId, action, userId);
        return true;
    }

    // endregion

    // region 私有方法

    /**
     * 批量加载业务线名称映射（避免 N+1 查询）
     *
     * @param queues 队列条目列表
     * @return businessLineId → businessLineName 映射
     */
    private Map<Long, String> loadBusinessLineNames(List<NeedsHumanQueueEO> queues) {
        if (queues == null || queues.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<Long> bizLineIds = queues.stream()
                .map(NeedsHumanQueueEO::getBusinessLineId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (bizLineIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        QueryWrapper<BusinessLineEO> bizWrapper = new QueryWrapper<>();
        bizWrapper.in("business_line_id", bizLineIds);
        List<BusinessLineEO> bizLines = businessLineMapper.selectList(bizWrapper);
        Map<Long, String> result = new LinkedHashMap<>();
        for (BusinessLineEO biz : bizLines) {
            result.put(biz.getBusinessLineId(), biz.getBusinessLineName());
        }
        return result;
    }

    /**
     * 解析字符串为 Long（兼容 null / 空串 / 非数字）
     *
     * @param value 字符串值
     * @return Long 值，解析失败返回 null
     */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("解析 Long 失败: {}", value);
            return null;
        }
    }

    // endregion
}

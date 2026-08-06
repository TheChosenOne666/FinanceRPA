package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.agent.service.TaskService;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.TaskTriggerRequest;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.request.ApprovalQueryRequest;
import com.finrpa.approval.dto.response.ApprovalRequestVO;
import com.finrpa.approval.dto.response.ApprovalResultResponse;
import com.finrpa.approval.entity.ApprovalRequestEO;
import com.finrpa.approval.enums.ApprovalStatusEnum;
import com.finrpa.approval.mapper.ApprovalRequestMapper;
import com.finrpa.approval.service.ApprovalPubSubService;
import com.finrpa.approval.service.ApprovalRouteConfigService;
import com.finrpa.approval.service.ApprovalRouteService;
import com.finrpa.approval.service.ApprovalService;
import com.finrpa.approval.service.ApprovalTimeoutConfigService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.approval.enums.ApprovalRouteEnum;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import com.finrpa.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 审批服务实现（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class ApprovalServiceImpl implements ApprovalService {

    /** 审批请求 Mapper */
    @Resource
    private ApprovalRequestMapper approvalRequestMapper;

    /** 审批路由服务 */
    @Resource
    private ApprovalRouteService approvalRouteService;

    /** 审批 Pub/Sub 服务 */
    @Resource
    private ApprovalPubSubService approvalPubSubService;

    /** Python AI 服务客户端（审批通过后触发 Python 执行 / 超时后通知 Python 终止） */
    @Resource
    private AiServiceClient aiServiceClient;

    /** 任务服务（审批超时后更新 Java 任务状态为 ABORTED） */
    @Resource
    private TaskService taskService;

    /** JSON 序列化工具（反序列化审批单中的触发请求） */
    @Resource
    private ObjectMapper objectMapper;

    /** 通知服务（M6.6 审批触发通知：待处理 / 超时告警） */
    @Resource
    private NotificationService notificationService;

    /** 用户 Mapper（批量填充 userName，对齐原型 02-dashboard.html 申请人列） */
    @Resource
    private UserMapper userMapper;

    /** 审批超时阈值配置服务（P1 RSK-1：替代写死的常量超时阈值） */
    @Resource
    private ApprovalTimeoutConfigService approvalTimeoutConfigService;

    /** 审批人映射配置服务（P1 RSK-3：按风险等级 × 业务线路由审批人） */
    @Resource
    private ApprovalRouteConfigService approvalRouteConfigService;

    // region 创建审批

    /**
     * 创建审批请求
     *
     * @param taskId          任务 ID
     * @param orgId           组织 ID
     * @param workflowId      工作流模板 ID
     * @param userId          触发用户 ID
     * @param riskLevel       风险等级（high / critical）
     * @param businessLineId  业务线业务 ID（可空，用于按业务线路由审批人）
     * @param riskReasoning   风险判断理由
     * @param requestPayload  请求负载 JSON
     * @return 审批请求实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRequestEO createApproval(Long taskId, Long orgId, Long workflowId, Long userId,
                                             String riskLevel, Long businessLineId,
                                             String riskReasoning, String requestPayload) {
        // 1. 路由判定
        ApprovalRouteEnum route = approvalRouteService.routeByRiskLevel(riskLevel);
        ThrowUtils.throwIf(!ApprovalRouteEnum.needsHumanApproval(route.getValue()),
                ErrorCode.PARAMS_ERROR, "低风险任务无需审批: riskLevel=" + riskLevel);

        // 2. 构建审批单
        ApprovalRequestEO approval = new ApprovalRequestEO();
        approval.setTaskId(taskId);
        approval.setOrgId(orgId);
        approval.setWorkflowId(workflowId);
        approval.setUserId(userId);
        approval.setRiskLevel(riskLevel);
        approval.setApprovalRoute(route.getValue());
        approval.setStatus(ApprovalConstant.APPROVAL_STATUS_PENDING);
        approval.setRiskReasoning(riskReasoning);
        approval.setRequestPayload(requestPayload);
        // 按风险等级读取超时配置（P1 RSK-1：从配置表读取，回退到常量默认值）
        long timeoutMinutes = getTimeoutMinutesByRiskLevel(riskLevel);
        approval.setTimeoutMinutes((int) timeoutMinutes);

        // 计算超时截止时间
        long timeoutMs = timeoutMinutes * 60 * 1000;
        approval.setTimeoutAt(new Timestamp(Instant.now().toEpochMilli() + timeoutMs));

        // 3. 按风险等级 × 业务线路由审批人（P1 RSK-3）
        // 精确匹配 → 默认路由 fallback → 仍找不到时 approver_id 留空（审批中心手动认领）
        Long approverId = approvalRouteConfigService.getApproverUserId(orgId, riskLevel, businessLineId);
        approval.setApproverId(approverId);
        if (approverId != null) {
            log.info("审批单路由到指定审批人: approverId={}, riskLevel={}, businessLineId={}",
                    approverId, riskLevel, businessLineId);
        } else {
            log.warn("未找到匹配的审批人映射配置，审批单待手动认领: taskId={}, riskLevel={}, businessLineId={}",
                    taskId, riskLevel, businessLineId);
        }

        // 4. 持久化
        approvalRequestMapper.insert(approval);
        log.info("审批单已创建: approvalId={}, taskId={}, riskLevel={}, route={}, timeoutAt={}",
                approval.getApprovalId(), taskId, riskLevel, route.getValue(), approval.getTimeoutAt());

        // 5. 发布 Pub/Sub 通知（新审批单）
        approvalPubSubService.publishRequest(approval);

        // 6. 推送通知（M6.6 APPROVAL_PENDING：企微 → 钉钉 Fallback → 重试队列）
        notifyApprovalPending(approval);

        return approval;
    }

    // endregion

    // region 审批操作

    /**
     * 审批通过
     *
     * @param approvalId 审批单 ID
     * @param approverId 审批人 ID
     * @param reason     通过理由
     * @return 更新后的审批请求实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRequestEO approve(Long approvalId, Long approverId, String reason) {
        ApprovalRequestEO approval = getAndCheckPending(approvalId);

        approval.setStatus(ApprovalConstant.APPROVAL_STATUS_APPROVED);
        approval.setApproverId(approverId);
        approval.setApproveReason(reason);
        approval.setApprovedAt(new Timestamp(Instant.now().toEpochMilli()));

        approvalRequestMapper.updateById(approval);
        log.info("审批通过: approvalId={}, taskId={}, approverId={}", approvalId, approval.getTaskId(), approverId);

        // 发布 Pub/Sub 通知（唤醒等待线程）
        approvalPubSubService.publishResponse(approval);

        // 审批通过后触发 Python 执行（从 requestPayload 反序列化触发请求）
        triggerPythonAfterApproval(approval);

        return approval;
    }

    /**
     * 审批拒绝
     *
     * @param approvalId 审批单 ID
     * @param approverId 审批人 ID
     * @param reason     拒绝理由
     * @return 更新后的审批请求实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRequestEO reject(Long approvalId, Long approverId, String reason) {
        ApprovalRequestEO approval = getAndCheckPending(approvalId);

        approval.setStatus(ApprovalConstant.APPROVAL_STATUS_REJECTED);
        approval.setApproverId(approverId);
        approval.setRejectReason(reason);
        approval.setApprovedAt(new Timestamp(Instant.now().toEpochMilli()));

        approvalRequestMapper.updateById(approval);
        log.info("审批拒绝: approvalId={}, taskId={}, approverId={}", approvalId, approval.getTaskId(), approverId);

        // 发布 Pub/Sub 通知（唤醒等待线程）
        approvalPubSubService.publishResponse(approval);

        return approval;
    }

    // endregion

    // region 查询

    /**
     * 分页查询审批列表
     *
     * @param queryRequest 查询请求
     * @return 审批分页列表
     */
    @Override
    public IPage<ApprovalRequestVO> listApprovals(ApprovalQueryRequest queryRequest) {
        Page<ApprovalRequestEO> page = new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize());
        LambdaQueryWrapper<ApprovalRequestEO> wrapper = new LambdaQueryWrapper<>();

        // 按组织过滤
        if (queryRequest.getOrgId() != null) {
            wrapper.eq(ApprovalRequestEO::getOrgId, queryRequest.getOrgId());
        }
        // 按状态过滤
        if (queryRequest.getStatus() != null && !queryRequest.getStatus().isBlank()) {
            wrapper.eq(ApprovalRequestEO::getStatus, queryRequest.getStatus());
        }
        // 按路由过滤
        if (queryRequest.getApprovalRoute() != null && !queryRequest.getApprovalRoute().isBlank()) {
            wrapper.eq(ApprovalRequestEO::getApprovalRoute, queryRequest.getApprovalRoute());
        }
        // 按风险等级过滤
        if (queryRequest.getRiskLevel() != null && !queryRequest.getRiskLevel().isBlank()) {
            wrapper.eq(ApprovalRequestEO::getRiskLevel, queryRequest.getRiskLevel());
        }
        // 按任务 ID 过滤
        if (queryRequest.getTaskId() != null) {
            wrapper.eq(ApprovalRequestEO::getTaskId, queryRequest.getTaskId());
        }
        // 按触发用户 ID 过滤（"我发起的"Tab）
        if (queryRequest.getUserId() != null) {
            wrapper.eq(ApprovalRequestEO::getUserId, queryRequest.getUserId());
        }

        wrapper.orderByDesc(ApprovalRequestEO::getCreateTime);

        IPage<ApprovalRequestEO> eoPage = approvalRequestMapper.selectPage(page, wrapper);
        IPage<ApprovalRequestVO> voPage = eoPage.convert(this::convertToVO);
        // 批量填充 userName（避免 N+1 查询，对齐原型申请人列显示）
        fillUserNames(voPage.getRecords());
        return voPage;
    }

    /**
     * 查询审批详情
     *
     * @param approvalId 审批单 ID
     * @return 审批请求 VO
     */
    @Override
    public ApprovalRequestVO getApprovalDetail(Long approvalId) {
        ApprovalRequestEO approval = approvalRequestMapper.selectById(approvalId);
        ThrowUtils.throwIf(approval == null, ErrorCode.NOT_FOUND_ERROR, "审批单不存在: " + approvalId);
        ApprovalRequestVO vo = convertToVO(approval);
        // 单条填充 userName
        fillUserNames(List.of(vo));
        return vo;
    }

    /**
     * 根据任务 ID 查询审批结果（Python 回调用）
     *
     * @param taskId 任务 ID
     * @return 审批结果响应
     */
    @Override
    public ApprovalResultResponse getApprovalResultByTaskId(Long taskId) {
        LambdaQueryWrapper<ApprovalRequestEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRequestEO::getTaskId, taskId)
                .orderByDesc(ApprovalRequestEO::getCreateTime)
                .last("LIMIT 1");
        ApprovalRequestEO approval = approvalRequestMapper.selectOne(wrapper);

        if (approval == null) {
            ApprovalResultResponse response = new ApprovalResultResponse();
            response.setTaskId(taskId);
            response.setStatus("NOT_FOUND");
            response.setApproved(false);
            response.setTerminal(false);
            response.setMessage("未找到审批单");
            return response;
        }

        return buildResultResponse(approval);
    }

    /**
     * 根据审批单 ID 查询审批结果
     *
     * @param approvalId 审批单 ID
     * @return 审批结果响应
     */
    @Override
    public ApprovalResultResponse getApprovalResult(Long approvalId) {
        ApprovalRequestEO approval = approvalRequestMapper.selectById(approvalId);
        if (approval == null) {
            ApprovalResultResponse response = new ApprovalResultResponse();
            response.setApprovalId(approvalId);
            response.setStatus("NOT_FOUND");
            response.setApproved(false);
            response.setTerminal(false);
            response.setMessage("未找到审批单");
            return response;
        }
        return buildResultResponse(approval);
    }

    // endregion

    // region 超时处理

    /**
     * 处理超时审批（M6.4 定时任务调用）
     *
     * <p>对每个超时的 PENDING 审批单执行：
     * <ol>
     *   <li>标记状态为 TIMEOUT</li>
     *   <li>更新 Java 任务状态为 ABORTED（审批超时视为任务终止）</li>
     *   <li>发布 Pub/Sub 通知（唤醒等待线程 + 通知前端）</li>
     *   <li>通知 Python 终止任务（防御性调用，审批未通过时 Python 无活跃任务，调用失败忽略）</li>
     * </ol>
     *
     * @return 处理的超时审批单数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int processTimeoutApprovals() {
        LambdaQueryWrapper<ApprovalRequestEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApprovalRequestEO::getStatus, ApprovalConstant.APPROVAL_STATUS_PENDING)
                .le(ApprovalRequestEO::getTimeoutAt, new Timestamp(Instant.now().toEpochMilli()));

        List<ApprovalRequestEO> timeoutApprovals = approvalRequestMapper.selectList(wrapper);
        if (timeoutApprovals.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (ApprovalRequestEO approval : timeoutApprovals) {
            approval.setStatus(ApprovalConstant.APPROVAL_STATUS_TIMEOUT);
            approval.setRejectReason("审批超时自动拒绝");
            approval.setApprovedAt(new Timestamp(Instant.now().toEpochMilli()));
            approvalRequestMapper.updateById(approval);

            // 发布超时通知（Pub/Sub 广播 + 唤醒等待线程）
            approvalPubSubService.publishResponse(approval);

            // 更新 Java 任务状态为 ABORTED（审批超时 → 任务终止）
            updateTaskStateOnTimeout(approval);

            // 通知 Python 终止任务（防御性：审批未通过时 Python 无活跃任务，调用失败仅记录日志）
            notifyPythonAbortOnTimeout(approval);

            // 推送通知（M6.6 APPROVAL_TIMEOUT：企微 → 钉钉 Fallback → 重试队列）
            notifyApprovalTimeout(approval);

            count++;
            log.warn("审批超时已处理: approvalId={}, taskId={}, riskLevel={}, route={}",
                    approval.getApprovalId(), approval.getTaskId(),
                    approval.getRiskLevel(), approval.getApprovalRoute());
        }

        log.info("超时审批处理完成: count={}", count);
        return count;
    }

    // endregion

    // region 私有方法

    /**
     * 查询审批单并校验为 PENDING 状态
     */
    private ApprovalRequestEO getAndCheckPending(Long approvalId) {
        ApprovalRequestEO approval = approvalRequestMapper.selectById(approvalId);
        ThrowUtils.throwIf(approval == null, ErrorCode.NOT_FOUND_ERROR, "审批单不存在: " + approvalId);
        ThrowUtils.throwIf(!ApprovalConstant.APPROVAL_STATUS_PENDING.equals(approval.getStatus()),
                ErrorCode.OPERATION_ERROR, "审批单已处理，无法重复操作: status=" + approval.getStatus());
        return approval;
    }

    /**
     * 实体转 VO
     */
    private ApprovalRequestVO convertToVO(ApprovalRequestEO approval) {
        ApprovalRequestVO vo = new ApprovalRequestVO();
        vo.setApprovalId(approval.getApprovalId());
        vo.setTaskId(approval.getTaskId());
        vo.setOrgId(approval.getOrgId());
        vo.setWorkflowId(approval.getWorkflowId());
        vo.setUserId(approval.getUserId());
        vo.setRiskLevel(approval.getRiskLevel());
        vo.setApprovalRoute(approval.getApprovalRoute());
        vo.setStatus(approval.getStatus());
        vo.setApproverId(approval.getApproverId());
        vo.setApproveReason(approval.getApproveReason());
        vo.setRejectReason(approval.getRejectReason());
        vo.setRiskReasoning(approval.getRiskReasoning());
        vo.setRequestPayload(approval.getRequestPayload());
        vo.setTimeoutAt(approval.getTimeoutAt());
        vo.setApprovedAt(approval.getApprovedAt());
        vo.setCreateTime(approval.getCreateTime());
        return vo;
    }

    /**
     * 批量填充审批列表的 userName 字段（联表 sys_user.real_name）
     *
     * <p>对齐原型 02-dashboard.html 与 05-approval-center.html 申请人列显示。
     * 单次批量查询避免 N+1；缺失 userId 或查无对应用户时 userName 置空。</p>
     *
     * @param records 审批 VO 列表（in-place 填充）
     */
    private void fillUserNames(List<ApprovalRequestVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 1. 收集非空 userId
        List<Long> userIds = records.stream()
                .map(ApprovalRequestVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        // 2. 批量查询用户
        List<UserEO> users = userMapper.selectByUserIds(userIds);
        Map<Long, String> userIdToName = new HashMap<>();
        if (users != null) {
            for (UserEO u : users) {
                if (u.getUserId() != null) {
                    userIdToName.put(u.getUserId(), u.getRealName());
                }
            }
        }
        // 3. 填充 userName
        for (ApprovalRequestVO vo : records) {
            if (vo.getUserId() != null) {
                vo.setUserName(userIdToName.getOrDefault(vo.getUserId(), null));
            }
        }
    }

    /**
     * 构建审批结果响应
     */
    private ApprovalResultResponse buildResultResponse(ApprovalRequestEO approval) {
        ApprovalResultResponse response = new ApprovalResultResponse();
        response.setApprovalId(approval.getApprovalId());
        response.setTaskId(approval.getTaskId());
        response.setStatus(approval.getStatus());
        response.setRiskLevel(approval.getRiskLevel());
        response.setApprovalRoute(approval.getApprovalRoute());
        response.setApproved(ApprovalConstant.APPROVAL_STATUS_APPROVED.equals(approval.getStatus()));
        response.setTerminal(ApprovalStatusEnum.isTerminal(approval.getStatus()));
        response.setApproveReason(approval.getApproveReason());
        response.setRejectReason(approval.getRejectReason());

        switch (approval.getStatus()) {
            case ApprovalConstant.APPROVAL_STATUS_APPROVED ->
                    response.setMessage("审批已通过");
            case ApprovalConstant.APPROVAL_STATUS_REJECTED ->
                    response.setMessage("审批已拒绝");
            case ApprovalConstant.APPROVAL_STATUS_TIMEOUT ->
                    response.setMessage("审批已超时");
            default -> response.setMessage("审批进行中");
        }

        return response;
    }

    /**
     * 审批通过后触发 Python 执行
     *
     * <p>从审批单的 requestPayload 字段反序列化 TaskTriggerRequest，
     * 调用 AiServiceClient 触发 Python 任务执行。
     * 触发失败不影响审批结果（审批已通过），仅记录错误日志。</p>
     *
     * @param approval 审批请求实体
     */
    private void triggerPythonAfterApproval(ApprovalRequestEO approval) {
        if (approval.getRequestPayload() == null || approval.getRequestPayload().isBlank()) {
            log.warn("审批单 requestPayload 为空，无法触发 Python: approvalId={}", approval.getApprovalId());
            return;
        }

        try {
            TaskTriggerRequest triggerRequest = objectMapper.readValue(
                    approval.getRequestPayload(), TaskTriggerRequest.class);
            TaskTriggerResponse response = aiServiceClient.triggerTask(triggerRequest);
            log.info("审批通过后 Python 任务已触发: approvalId={}, taskId={}, status={}",
                    approval.getApprovalId(), approval.getTaskId(), response.getStatus());
        } catch (Exception e) {
            log.error("审批通过后触发 Python 失败: approvalId={}, taskId={}, error={}",
                    approval.getApprovalId(), approval.getTaskId(), e.getMessage(), e);
        }
    }

    /**
     * 根据风险等级获取审批超时时间（分钟）
     *
     * <p>P1 RSK-1 起改为读取 {@link ApprovalTimeoutConfigService#getTimeoutMinutesByRiskLevel(String)}
     * 配置表。配置缺失或被禁用时，回退到 {@link ApprovalConstant} 默认值：high=30 / critical=60 / 其他=30。</p>
     *
     * @param riskLevel 风险等级
     * @return 超时时间（分钟）
     */
    private long getTimeoutMinutesByRiskLevel(String riskLevel) {
        return approvalTimeoutConfigService.getTimeoutMinutesByRiskLevel(riskLevel);
    }

    /**
     * 审批超时后更新 Java 任务状态为 ABORTED
     *
     * <p>审批超时视为任务终止。任务状态更新失败不影响审批超时处理主流程，
     * 仅记录错误日志（避免单个任务异常导致整批超时处理回滚）。</p>
     *
     * @param approval 超时的审批请求实体
     */
    private void updateTaskStateOnTimeout(ApprovalRequestEO approval) {
        Long taskId = approval.getTaskId();
        if (taskId == null) {
            log.warn("审批超时但 taskId 为空，无法更新任务状态: approvalId={}", approval.getApprovalId());
            return;
        }
        try {
            taskService.abortTask(taskId);
            log.info("审批超时后任务已终止: taskId={}, approvalId={}", taskId, approval.getApprovalId());
        } catch (Exception e) {
            log.error("审批超时后更新任务状态失败: approvalId={}, taskId={}, errorType={}, error={}",
                    approval.getApprovalId(), taskId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * 审批超时后通知 Python 终止任务
     *
     * <p>防御性调用：审批未通过时 Python 无活跃任务，调用通常返回 404 或失败，属正常情况。
     * 调用失败仅记录日志，不影响审批超时处理主流程。</p>
     *
     * @param approval 超时的审批请求实体
     */
    private void notifyPythonAbortOnTimeout(ApprovalRequestEO approval) {
        Long taskId = approval.getTaskId();
        if (taskId == null) {
            return;
        }
        try {
            aiServiceClient.abortTask(String.valueOf(taskId));
            log.info("审批超时后已通知 Python 终止任务: taskId={}, approvalId={}",
                    taskId, approval.getApprovalId());
        } catch (Exception e) {
            log.debug("审批超时后通知 Python 终止任务失败（预期行为，审批未通过时 Python 无活跃任务）: "
                            + "taskId={}, approvalId={}, error={}",
                    taskId, approval.getApprovalId(), e.getMessage());
        }
    }

    /**
     * 推送审批待处理通知（M6.6）
     *
     * <p>审批单创建后调用通知服务（含 Fallback + 重试队列），
     * 推送 APPROVAL_PENDING 模板到企微 / 钉钉群。通知失败不影响审批创建主流程。</p>
     *
     * @param approval 审批请求实体
     */
    private void notifyApprovalPending(ApprovalRequestEO approval) {
        try {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("approvalId", String.valueOf(approval.getApprovalId()));
            params.put("taskId", String.valueOf(approval.getTaskId()));
            params.put("riskLevel", approval.getRiskLevel());
            params.put("approvalRoute", approval.getApprovalRoute());
            params.put("timeoutMinutes", String.valueOf(approval.getTimeoutMinutes()));
            params.put("riskReasoning", approval.getRiskReasoning() == null ? "-" : approval.getRiskReasoning());

            boolean ok = notificationService.dispatch(
                    NotificationTemplateEnum.APPROVAL_PENDING, params,
                    approval.getApprovalId(), approval.getTaskId(), approval.getUserId());
            if (ok) {
                log.info("审批待处理通知已发送: approvalId={}", approval.getApprovalId());
            } else {
                log.warn("审批待处理通知发送失败，已入重试队列: approvalId={}", approval.getApprovalId());
            }
        } catch (Exception e) {
            log.error("审批待处理通知触发异常: approvalId={}, error={}",
                    approval.getApprovalId(), e.getMessage(), e);
        }
    }

    /**
     * 推送审批超时告警通知（M6.6）
     *
     * <p>审批超时后调用通知服务（含 Fallback + 重试队列），
     * 推送 APPROVAL_TIMEOUT 模板到企微 / 钉钉群。通知失败不影响超时处理主流程。</p>
     *
     * @param approval 超时的审批请求实体
     */
    private void notifyApprovalTimeout(ApprovalRequestEO approval) {
        try {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("approvalId", String.valueOf(approval.getApprovalId()));
            params.put("taskId", String.valueOf(approval.getTaskId()));
            params.put("riskLevel", approval.getRiskLevel());
            params.put("timeoutMinutes", String.valueOf(approval.getTimeoutMinutes()));

            boolean ok = notificationService.dispatch(
                    NotificationTemplateEnum.APPROVAL_TIMEOUT, params,
                    approval.getApprovalId(), approval.getTaskId(), approval.getUserId());
            if (ok) {
                log.info("审批超时告警已发送: approvalId={}", approval.getApprovalId());
            } else {
                log.warn("审批超时告警发送失败，已入重试队列: approvalId={}", approval.getApprovalId());
            }
        } catch (Exception e) {
            log.error("审批超时告警通知触发异常: approvalId={}, error={}",
                    approval.getApprovalId(), e.getMessage(), e);
        }
    }

    // endregion
}

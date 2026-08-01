package com.finrpa.approval.service.impl;

import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.response.ApprovalResultResponse;
import com.finrpa.approval.entity.ApprovalRequestEO;
import com.finrpa.approval.service.ApprovalPubSubService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 审批 Pub/Sub 服务实现（M6.3 Redisson）
 *
 * <p>使用 Redisson RTopic 发布消息 + RBlockingQueue 实现可靠的阻塞等待。
 * RTopic 用于广播通知（前端/管理端订阅），RBlockingQueue 用于可靠地唤醒等待线程。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class ApprovalPubSubServiceImpl implements ApprovalPubSubService {

    /** Redisson 客户端 */
    @Resource
    private RedissonClient redissonClient;

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    /** 审批响应队列前缀（RBlockingQueue 用） */
    private static final String RESPONSE_QUEUE_PREFIX = "approval:response:queue:";

    /**
     * 发布审批请求消息（新审批单创建时调用）
     *
     * @param approval 审批请求实体
     */
    @Override
    public void publishRequest(ApprovalRequestEO approval) {
        String channel = ApprovalConstant.CHANNEL_APPROVAL_REQUESTS;
        Long approvalId = approval.getApprovalId();
        Long taskId = approval.getTaskId();

        log.info("[PubSub] 开始发布审批请求: channel={}, approvalId={}, taskId={}, riskLevel={}, route={}",
                channel, approvalId, taskId, approval.getRiskLevel(), approval.getApprovalRoute());

        // 1. 序列化
        String message;
        try {
            message = objectMapper.writeValueAsString(approval);
            log.debug("[PubSub] 审批请求序列化成功: approvalId={}, messageLength={}", approvalId, message.length());
        } catch (JsonProcessingException e) {
            log.error("[PubSub] 审批请求序列化失败: approvalId={}, error={}", approvalId, e.getMessage(), e);
            return;
        }

        // 2. 获取 RTopic 并发布
        try {
            RTopic topic = redissonClient.getTopic(channel);
            log.debug("[PubSub] 获取 RTopic 成功: channel={}, listeners={}", channel, topic.countListeners());

            long subscriberCount = topic.publish(message);
            if (subscriberCount > 0) {
                log.info("[PubSub] 审批请求发布成功: channel={}, approvalId={}, taskId={}, subscribers={}",
                        channel, approvalId, taskId, subscriberCount);
            } else {
                // 无订阅者时仍视为成功（前端可能尚未连接），但记录 WARN 便于排查消息"丢失"
                log.warn("[PubSub] 审批请求已发布但无订阅者: channel={}, approvalId={}, taskId={}, " +
                                "subscribers=0（前端/管理端可能尚未订阅，消息不会重发）",
                        channel, approvalId, taskId);
            }
        } catch (Exception e) {
            log.error("[PubSub] 审批请求发布异常: channel={}, approvalId={}, taskId={}, errorType={}, error={}",
                    channel, approvalId, taskId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * 发布审批响应消息（审批通过/拒绝/超时时调用）
     *
     * <p>同时发布到 RTopic（广播通知）和 RBlockingQueue（唤醒等待线程）。</p>
     *
     * @param approval 审批请求实体
     */
    @Override
    public void publishResponse(ApprovalRequestEO approval) {
        Long approvalId = approval.getApprovalId();
        Long taskId = approval.getTaskId();
        String status = approval.getStatus();

        log.info("[PubSub] 开始发布审批响应: approvalId={}, taskId={}, status={}", approvalId, taskId, status);

        ApprovalResultResponse response = buildResultResponse(approval);

        // 1. 发布到 RTopic（广播通知，供前端/Python 订阅）
        String channelName = ApprovalConstant.CHANNEL_APPROVAL_RESPONSES_PREFIX + approvalId;
        try {
            String message = objectMapper.writeValueAsString(response);
            log.debug("[PubSub] 审批响应序列化成功: approvalId={}, messageLength={}", approvalId, message.length());

            RTopic topic = redissonClient.getTopic(channelName);
            log.debug("[PubSub] 获取响应 RTopic 成功: channel={}, listeners={}",
                    channelName, topic.countListeners());

            long subscriberCount = topic.publish(message);
            if (subscriberCount > 0) {
                log.info("[PubSub] 审批响应 RTopic 发布成功: channel={}, approvalId={}, taskId={}, status={}, subscribers={}",
                        channelName, approvalId, taskId, status, subscriberCount);
            } else {
                log.warn("[PubSub] 审批响应 RTopic 已发布但无订阅者: channel={}, approvalId={}, taskId={}, " +
                                "subscribers=0（等待线程通过 RBlockingQueue 唤醒，不影响主流程）",
                        channelName, approvalId, taskId);
            }
        } catch (JsonProcessingException e) {
            log.error("[PubSub] 审批响应序列化失败: approvalId={}, error={}", approvalId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("[PubSub] 审批响应 RTopic 发布异常: channel={}, approvalId={}, taskId={}, errorType={}, error={}",
                    channelName, approvalId, taskId, e.getClass().getSimpleName(), e.getMessage(), e);
        }

        // 2. 推送到 RBlockingQueue（唤醒等待的线程）
        String queueName = RESPONSE_QUEUE_PREFIX + approvalId;
        try {
            RBlockingQueue<ApprovalResultResponse> queue = redissonClient.getBlockingQueue(queueName);
            boolean offered = queue.offer(response);
            if (offered) {
                log.info("[PubSub] 审批响应已推入阻塞队列: queue={}, approvalId={}, taskId={}, status={}, queueSize={}",
                        queueName, approvalId, taskId, status, queue.size());
            } else {
                log.error("[PubSub] 审批响应推入阻塞队列失败（offer 返回 false）: queue={}, approvalId={}, taskId={}",
                        queueName, approvalId, taskId);
            }
        } catch (Exception e) {
            log.error("[PubSub] 审批响应推入阻塞队列异常: queue={}, approvalId={}, taskId={}, errorType={}, error={}",
                    queueName, approvalId, taskId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * 阻塞等待审批结果（带超时）
     *
     * @param approvalId 审批单 ID
     * @param timeoutSeconds 超时时间（秒）
     * @return 审批结果响应
     */
    @Override
    public ApprovalResultResponse waitForResponse(Long approvalId, long timeoutSeconds) {
        String queueName = RESPONSE_QUEUE_PREFIX + approvalId;

        log.info("[PubSub] 开始等待审批结果: queue={}, approvalId={}, timeout={}s", queueName, approvalId, timeoutSeconds);

        RBlockingQueue<ApprovalResultResponse> queue;
        try {
            queue = redissonClient.getBlockingQueue(queueName);
            log.debug("[PubSub] 获取阻塞队列成功: queue={}, approvalId={}, size={}",
                    queueName, approvalId, queue.size());
        } catch (Exception e) {
            log.error("[PubSub] 获取阻塞队列失败: queue={}, approvalId={}, errorType={}, error={}",
                    queueName, approvalId, e.getClass().getSimpleName(), e.getMessage(), e);
            return buildTimeoutResponse(approvalId);
        }

        // 检查队列中是否已有残留消息（避免 poll 前消息已到达）
        try {
            int queueSizeBeforePoll = queue.size();
            if (queueSizeBeforePoll > 0) {
                log.info("[PubSub] poll 前队列已有消息: queue={}, approvalId={}, queueSize={}",
                        queueName, approvalId, queueSizeBeforePoll);
            }
        } catch (Exception e) {
            log.debug("[PubSub] 检查队列大小失败（可忽略）: queue={}, error={}", queueName, e.getMessage());
        }

        try {
            long pollStartMs = System.currentTimeMillis();
            ApprovalResultResponse response = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - pollStartMs;

            if (response == null) {
                log.warn("[PubSub] 审批等待超时: queue={}, approvalId={}, timeout={}s, elapsed={}ms",
                        queueName, approvalId, timeoutSeconds, elapsedMs);
                return buildTimeoutResponse(approvalId);
            }

            log.info("[PubSub] 收到审批结果: queue={}, approvalId={}, status={}, approved={}, elapsed={}ms",
                    queueName, approvalId, response.getStatus(), response.isApproved(), elapsedMs);
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[PubSub] 审批等待被中断: queue={}, approvalId={}, thread={}",
                    queueName, approvalId, Thread.currentThread().getName());
            return buildTimeoutResponse(approvalId);
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
        response.setTerminal(com.finrpa.approval.enums.ApprovalStatusEnum.isTerminal(approval.getStatus()));
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
     * 构建超时响应
     */
    private ApprovalResultResponse buildTimeoutResponse(Long approvalId) {
        ApprovalResultResponse response = new ApprovalResultResponse();
        response.setApprovalId(approvalId);
        response.setStatus(ApprovalConstant.APPROVAL_STATUS_TIMEOUT);
        response.setApproved(false);
        response.setTerminal(true);
        response.setMessage("审批等待超时");
        return response;
    }
}

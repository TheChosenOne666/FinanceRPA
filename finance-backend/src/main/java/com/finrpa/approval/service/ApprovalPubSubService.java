package com.finrpa.approval.service;

import com.finrpa.approval.dto.response.ApprovalResultResponse;
import com.finrpa.approval.entity.ApprovalRequestEO;

/**
 * 审批 Pub/Sub 服务（M6.3 Redisson）
 *
 * <p>基于 Redisson 实现审批消息的发布与等待：
 * <ul>
 *   <li>发布审批请求到 {@code approval:requests} 频道（新审批单创建时）</li>
 *   <li>发布审批响应到 {@code approval:responses:{approvalId}} 频道（审批完成时）</li>
 *   <li>阻塞等待审批结果（带超时，供 WorkflowTriggerService 调用）</li>
 * </ul>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface ApprovalPubSubService {

    /**
     * 发布审批请求消息（新审批单创建时调用）
     *
     * <p>发布到 {@code approval:requests} 频道，供前端/管理端实时订阅。</p>
     *
     * @param approval 审批请求实体
     */
    void publishRequest(ApprovalRequestEO approval);

    /**
     * 发布审批响应消息（审批通过/拒绝/超时时调用）
     *
     * <p>发布到 {@code approval:responses:{approvalId}} 频道，
     * 同时推送响应队列（RBlockingQueue），唤醒等待的线程。</p>
     *
     * @param approval 审批请求实体
     */
    void publishResponse(ApprovalRequestEO approval);

    /**
     * 阻塞等待审批结果（带超时）
     *
     * <p>通过 Redisson RBlockingQueue 实现可靠的分布式阻塞等待。
     * 超时后返回 TIMEOUT 状态。</p>
     *
     * @param approvalId 审批单 ID
     * @param timeoutSeconds 超时时间（秒）
     * @return 审批结果响应
     */
    ApprovalResultResponse waitForResponse(Long approvalId, long timeoutSeconds);
}

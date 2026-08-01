package com.finrpa.approval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.approval.dto.request.ApprovalQueryRequest;
import com.finrpa.approval.dto.response.ApprovalRequestVO;
import com.finrpa.approval.dto.response.ApprovalResultResponse;
import com.finrpa.approval.entity.ApprovalRequestEO;

/**
 * 审批服务（M6.3）
 *
 * <p>审批单全生命周期管理：创建 → 等待 → 审批通过/拒绝/超时。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface ApprovalService {

    /**
     * 创建审批请求
     *
     * <p>高风险/极高风险任务触发时调用，创建审批单并发布 Pub/Sub 通知。</p>
     *
     * @param taskId        任务 ID
     * @param orgId         组织 ID
     * @param workflowId    工作流模板 ID
     * @param userId        触发用户 ID
     * @param riskLevel     风险等级（high / critical）
     * @param riskReasoning 风险判断理由（LLM 判断结果）
     * @param requestPayload 请求负载 JSON（任务目标 + 参数等）
     * @return 审批请求实体
     */
    ApprovalRequestEO createApproval(Long taskId, Long orgId, Long workflowId, Long userId,
                                      String riskLevel, String riskReasoning, String requestPayload);

    /**
     * 审批通过
     *
     * @param approvalId 审批单 ID
     * @param approverId 审批人 ID
     * @param reason     通过理由
     * @return 更新后的审批请求实体
     */
    ApprovalRequestEO approve(Long approvalId, Long approverId, String reason);

    /**
     * 审批拒绝
     *
     * @param approvalId 审批单 ID
     * @param approverId 审批人 ID
     * @param reason     拒绝理由
     * @return 更新后的审批请求实体
     */
    ApprovalRequestEO reject(Long approvalId, Long approverId, String reason);

    /**
     * 分页查询审批列表
     *
     * @param queryRequest 查询请求
     * @return 审批分页列表
     */
    IPage<ApprovalRequestVO> listApprovals(ApprovalQueryRequest queryRequest);

    /**
     * 查询审批详情
     *
     * @param approvalId 审批单 ID
     * @return 审批请求 VO
     */
    ApprovalRequestVO getApprovalDetail(Long approvalId);

    /**
     * 根据任务 ID 查询审批结果（Python 回调用）
     *
     * @param taskId 任务 ID
     * @return 审批结果响应
     */
    ApprovalResultResponse getApprovalResultByTaskId(Long taskId);

    /**
     * 根据审批单 ID 查询审批结果
     *
     * @param approvalId 审批单 ID
     * @return 审批结果响应
     */
    ApprovalResultResponse getApprovalResult(Long approvalId);

    /**
     * 处理超时审批（M6.4 定时任务调用）
     *
     * <p>将超时的 PENDING 审批单标记为 TIMEOUT，发布 Pub/Sub 通知。</p>
     *
     * @return 处理的超时审批单数量
     */
    int processTimeoutApprovals();
}

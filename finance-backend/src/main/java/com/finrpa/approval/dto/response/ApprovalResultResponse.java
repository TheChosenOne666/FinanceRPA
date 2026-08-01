package com.finrpa.approval.dto.response;

import lombok.Data;

/**
 * 审批结果响应（Python 回调查询用）（M6.3）
 *
 * <p>Python Executor 通过内部 API 查询审批结果，
 * 判断是否可以继续执行任务。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalResultResponse {

    /** 审批单业务 ID */
    private Long approvalId;

    /** 关联任务 ID */
    private Long taskId;

    /** 审批状态：PENDING / APPROVED / REJECTED / TIMEOUT */
    private String status;

    /** 风险等级 */
    private String riskLevel;

    /** 审批路由 */
    private String approvalRoute;

    /** 是否已通过 */
    private boolean approved;

    /** 是否为终态（APPROVED / REJECTED / TIMEOUT） */
    private boolean terminal;

    /** 拒绝理由（REJECTED 时填充） */
    private String rejectReason;

    /** 通过理由（APPROVED 时填充） */
    private String approveReason;

    /** 消息说明 */
    private String message;
}

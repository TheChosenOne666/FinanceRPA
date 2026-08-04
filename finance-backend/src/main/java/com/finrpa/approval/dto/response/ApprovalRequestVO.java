package com.finrpa.approval.dto.response;

import lombok.Data;

import java.sql.Timestamp;

/**
 * 审批请求视图对象（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalRequestVO {

    /** 审批单业务 ID */
    private Long approvalId;

    /** 关联任务 ID */
    private Long taskId;

    /** 组织 ID */
    private Long orgId;

    /** 工作流模板 ID */
    private Long workflowId;

    /** 触发用户 ID */
    private Long userId;

    /** 触发用户姓名（联表 sys_user.real_name 填充，对齐原型 02-dashboard.html 申请人列显示） */
    private String userName;

    /** 风险等级：low / medium / high / critical */
    private String riskLevel;

    /** 审批路由：auto / department / compliance */
    private String approvalRoute;

    /** 审批状态：PENDING / APPROVED / REJECTED / TIMEOUT */
    private String status;

    /** 审批人 ID（审批完成后填充） */
    private Long approverId;

    /** 通过理由 */
    private String approveReason;

    /** 拒绝理由 */
    private String rejectReason;

    /** 风险判断理由（LLM 判断结果，供审批人参考） */
    private String riskReasoning;

    /** 请求负载 JSON（任务目标 + 参数等） */
    private String requestPayload;

    /** 超时截止时间 */
    private Timestamp timeoutAt;

    /** 审批完成时间 */
    private Timestamp approvedAt;

    /** 创建时间 */
    private Timestamp createTime;
}

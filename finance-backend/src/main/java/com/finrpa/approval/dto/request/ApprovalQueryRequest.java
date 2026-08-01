package com.finrpa.approval.dto.request;

import lombok.Data;

/**
 * 审批列表查询请求（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalQueryRequest {

    /** 当前页码（默认 1） */
    private long current = 1;

    /** 每页条数（默认 10） */
    private long pageSize = 10;

    /** 审批状态筛选：PENDING / APPROVED / REJECTED / TIMEOUT */
    private String status;

    /** 审批路由筛选：department / compliance */
    private String approvalRoute;

    /** 风险等级筛选：low / medium / high / critical */
    private String riskLevel;

    /** 组织 ID（由 Controller 从登录上下文自动填充） */
    private Long orgId;

    /** 任务 ID（精确查询） */
    private Long taskId;
}

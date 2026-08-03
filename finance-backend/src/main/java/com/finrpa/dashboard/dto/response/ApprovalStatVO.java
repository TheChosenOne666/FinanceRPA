package com.finrpa.dashboard.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批统计 VO（对齐系统设计 6.9.1 审批：平均响应时长 / 超时数）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalStatVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 审批单总数 */
    private Long totalApprovals;

    /** 已通过数 */
    private Long approvedCount;

    /** 已拒绝数 */
    private Long rejectedCount;

    /** 超时数 */
    private Long timeoutCount;

    /** 待处理数（PENDING） */
    private Long pendingCount;

    /** 平均响应时长（分钟，基于已终态审批 approved_at - create_time） */
    private Double avgResponseMinutes;
}

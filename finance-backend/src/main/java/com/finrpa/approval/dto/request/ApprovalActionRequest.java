package com.finrpa.approval.dto.request;

import lombok.Data;

/**
 * 审批操作请求（通过/拒绝）（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalActionRequest {

    /** 审批理由（通过或拒绝的原因说明） */
    private String reason;
}

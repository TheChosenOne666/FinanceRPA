package com.finrpa.approval.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 审批人映射配置查询请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalRouteConfigQueryRequest extends PageRequest {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 风险等级：high / critical（可空） */
    private String riskLevel;

    /** 业务线业务 ID（可空） */
    private Long businessLineId;

    /** 启用状态：0-禁用 1-启用（可空，默认全部） */
    private Integer enabled;
}

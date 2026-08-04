package com.finrpa.approval.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批人映射配置更新请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalRouteConfigUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 审批人用户业务 ID */
    private Long approverUserId;

    /** 审批人所属部门业务 ID（可空） */
    private Long departmentId;

    /** 描述说明（可空） */
    private String description;

    /** 启用状态（0-禁用 1-启用） */
    private Integer enabled;
}

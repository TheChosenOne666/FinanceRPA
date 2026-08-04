package com.finrpa.approval.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 审批人映射配置 VO
 *
 * <p>列表展示时附带 {@code approverName} / {@code businessLineName} 联表字段，
 * 由 Service 层批量填充（避免 N+1 查询）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalRouteConfigVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置业务 ID */
    private Long configId;

    /** 组织业务 ID */
    private Long orgId;

    /** 风险等级：high / critical */
    private String riskLevel;

    /** 业务线业务 ID（NULL 表示该风险等级的默认路由） */
    private Long businessLineId;

    /** 业务线名称（联表 enterprise_business_line.business_line_name，默认路由时为「默认路由」） */
    private String businessLineName;

    /** 审批人用户业务 ID */
    private Long approverUserId;

    /** 审批人姓名（联表 sys_user.real_name） */
    private String approverName;

    /** 审批人所属部门业务 ID */
    private Long departmentId;

    /** 描述说明 */
    private String description;

    /** 启用状态 */
    private Integer enabled;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}

package com.finrpa.approval.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 审批超时阈值配置 VO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ApprovalTimeoutConfigVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置业务 ID */
    private Long configId;

    /** 风险等级：high / critical */
    private String riskLevel;

    /** 超时分钟数 */
    private Integer timeoutMinutes;

    /** 描述说明 */
    private String description;

    /** 启用状态 */
    private Integer enabled;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}

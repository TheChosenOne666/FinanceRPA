package com.finrpa.tenant.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 租户信息响应（GET /tenant/info 返回结构）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TenantInfoResponse implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 组织 ID（雪花算法 ID） */
    private Long orgId;

    /** 组织名称 */
    private String orgName;

    /** 组织编码 */
    private String orgCode;

    /** 描述 */
    private String description;

    /** 状态（0-禁用 1-启用） */
    private Integer status;

    /** 创建时间 */
    private Timestamp createTime;
}

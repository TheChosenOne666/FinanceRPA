package com.finrpa.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 组织实体（对应 enterprise_organization 表）
 *
 * <p>租户主表，本身不参与 TenantLineHandler 过滤（已在忽略清单中）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("enterprise_organization")
public class OrganizationEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 组织业务 ID（UUID） */
    @TableField("org_id")
    private String orgId;

    /** 组织名称 */
    @TableField("org_name")
    private String orgName;

    /** 组织编码 */
    @TableField("org_code")
    private String orgCode;

    /** 描述 */
    @TableField("description")
    private String description;

    /** 状态（0-禁用 1-启用） */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("update_time")
    private Timestamp updateTime;
}

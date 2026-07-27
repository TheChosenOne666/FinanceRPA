package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.sql.Timestamp;

@Data
@TableName("sys_role")
public class RoleEO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("role_id")
    private String roleId;

    @TableField("role_name")
    private String roleName;

    @TableField("role_code")
    private String roleCode;

    @TableField("description")
    private String description;

    @TableField("org_id")
    private String orgId;

    @TableField("is_cross_org_read")
    private Integer isCrossOrgRead;

    @TableField("is_cross_org_approve")
    private Integer isCrossOrgApprove;

    @TableField("status")
    private Integer status;

    @TableField("deleted")
    private Integer deleted;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Timestamp createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Timestamp updateTime;
}
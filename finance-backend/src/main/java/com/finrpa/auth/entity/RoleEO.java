package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 系统角色实体（对应 sys_role 表）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_role")
public class RoleEO {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色业务 ID（UUID） */
    @TableField("role_id")
    private String roleId;

    /** 角色名称 */
    @TableField("role_name")
    private String roleName;

    /** 角色编码（唯一标识） */
    @TableField("role_code")
    private String roleCode;

    /** 角色描述 */
    @TableField("description")
    private String description;

    /** 所属组织 ID */
    @TableField("org_id")
    private String orgId;

    /** 是否允许跨组织读取（0-否 1-是） */
    @TableField("is_cross_org_read")
    private Integer isCrossOrgRead;

    /** 是否允许跨组织审批（0-否 1-是） */
    @TableField("is_cross_org_approve")
    private Integer isCrossOrgApprove;

    /** 状态（0-禁用 1-启用） */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Timestamp createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Timestamp updateTime;
}
package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 用户-角色关联实体（对应 sys_user_role 表）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_user_role")
public class UserRoleEO {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户业务 ID（UUID） */
    @TableField("user_id")
    private String userId;

    /** 角色业务 ID（UUID） */
    @TableField("role_id")
    private String roleId;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Timestamp createTime;
}
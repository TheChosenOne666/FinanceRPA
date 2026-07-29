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

    /** 主键 ID（数据库自增，插入时不设值） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户业务 ID（雪花算法 ID） */
    @TableField("user_id")
    private Long userId;

    /** 角色业务 ID（雪花算法 ID） */
    @TableField("role_id")
    private Long roleId;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;
}
package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 系统用户实体（对应 sys_user 表）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_user")
public class UserEO {

    /** 主键 ID（数据库自增，插入时不设值） */
    @TableField("id")
    private Long id;

    /** 用户业务 ID（雪花算法 ID） */
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId;

    /** 用户名（登录账号） */
    @TableField("username")
    private String username;

    /** 密码（加密存储） */
    @TableField("password")
    private String password;

    /** 真实姓名 */
    @TableField("real_name")
    private String realName;

    /** 头像地址 */
    @TableField("avatar")
    private String avatar;

    /** 邮箱 */
    @TableField("email")
    private String email;

    /** 手机号 */
    @TableField("phone")
    private String phone;

    /** 所属组织 ID（雪花算法 ID） */
    @TableField("org_id")
    private Long orgId;

    /** 所属组织名称 */
    @TableField("org_name")
    private String orgName;

    /** 所属部门名称 */
    @TableField("dept_name")
    private String deptName;

    /** 状态（0-禁用 1-启用） */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableField("deleted")
    private Integer deleted;

    /** 密码最后修改时间（用于密码过期校验，P2 SEC-1） */
    @TableField("pwd_changed_at")
    private Timestamp pwdChangedAt;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("update_time")
    private Timestamp updateTime;
}
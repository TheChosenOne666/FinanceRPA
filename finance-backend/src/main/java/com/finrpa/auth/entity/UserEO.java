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

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户业务 ID（UUID） */
    @TableField("user_id")
    private String userId;

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

    /** 所属组织 ID */
    @TableField("org_id")
    private String orgId;

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

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Timestamp createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Timestamp updateTime;
}
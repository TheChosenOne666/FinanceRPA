package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 用户-角色关联实体（对应 sys_user_role 表）
 *
 * <p>M7.6 起扩展为三维度 RBAC：user × department × business_line × role。
 * departmentId / businessLineId 允许为 NULL，表示该关联不限定部门或业务线
 * （如组织管理员的关联通常不绑定具体部门/业务线）。</p>
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

    /** 部门业务 ID（关联 enterprise_department.dept_id，NULL 表示不限部门） */
    @TableField("department_id")
    private Long departmentId;

    /** 业务线业务 ID（关联 enterprise_business_line.business_line_id，NULL 表示不限业务线） */
    @TableField("business_line_id")
    private Long businessLineId;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;
}
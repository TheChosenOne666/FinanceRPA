package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 角色-权限关联实体（对应 sys_role_permission 表）
 *
 * <p>P3 USR-3 权限矩阵可视化：承载角色与权限点的多对多关联。
 * 保存语义为「全量替换」：先按 roleId 删除全部关联，再批量插入新关联。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_role_permission")
public class RolePermissionEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 角色业务 ID */
    @TableField("role_id")
    private Long roleId;

    /** 权限业务 ID */
    @TableField("permission_id")
    private Long permissionId;

    /** 创建时间 */
    @TableField("create_time")
    private Timestamp createTime;
}

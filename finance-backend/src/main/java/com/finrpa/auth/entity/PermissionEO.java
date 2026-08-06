package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 权限点实体（对应 sys_permission 表）
 *
 * <p>P3 USR-3 权限矩阵可视化：承载权限点定义（如 task:create / workflow:approve）。
 * 复用 V2 迁移已建的 sys_permission 表，P3 通过 V27 迁移初始化 12 个权限点 + 内置角色权限关联。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_permission")
public class PermissionEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 权限业务 ID（雪花算法 ID） */
    @TableId(value = "permission_id", type = IdType.ASSIGN_ID)
    private Long permissionId;

    /** 权限编码（唯一，如 task:create / workflow:approve / *） */
    @TableField("permission_code")
    private String permissionCode;

    /** 权限名称（如 任务创建） */
    @TableField("permission_name")
    private String permissionName;

    /** 资源类型（task / workflow / user / role / org / report / all） */
    @TableField("resource_type")
    private String resourceType;

    /** 资源路径（可空，如 /api/tasks） */
    @TableField("resource_path")
    private String resourcePath;

    /** 父权限 ID（0 表示顶级） */
    @TableField("parent_id")
    private Long parentId;

    /** 排序序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 启用状态（0-禁用 1-启用） */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间 */
    @TableField("update_time")
    private Timestamp updateTime;
}

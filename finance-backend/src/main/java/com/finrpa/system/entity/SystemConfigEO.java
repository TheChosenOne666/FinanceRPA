package com.finrpa.system.entity;

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
 * 系统配置实体（对应 sys_config 表）
 *
 * <p>P3 统一配置中心：承载 AI 服务配置（INT-1）/ MinIO 配置（INT-3）/
 * 定时任务参数（OPS-2）/ 系统开关（OPS-3）等键值对配置。
 * 复用 V2 迁移已建的 sys_config 表（已含 5 条初始数据），P3 通过 V24 迁移补充种子数据。</p>
 *
 * <p>该表全局共享（无 org_id 字段），已在 {@code TenantConstant.IGNORED_TABLES} 忽略清单。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_config")
public class SystemConfigEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 配置键（唯一，如 ai.base_url / minio.endpoint / maintenance.enabled） */
    @TableField("config_key")
    private String configKey;

    /** 配置值（TEXT 类型，按 config_type 解释） */
    @TableField("config_value")
    private String configValue;

    /** 值类型（STRING / INTEGER / BOOLEAN） */
    @TableField("config_type")
    private String configType;

    /** 描述说明 */
    @TableField("description")
    private String description;

    /** 启用状态（0-禁用 1-启用） */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("update_time")
    private Timestamp updateTime;
}

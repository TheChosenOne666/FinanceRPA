package com.finrpa.skills.entity;

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
 * Skill 元数据实体（对应 rpa_skill_meta 表）
 *
 * <p>全局共享的 Skill 档案，存储 7 个内置 Skill 与用户自定义 Skill 的元数据。
 * 供前端展示 Skill 目录、工作流模板校验 Skill 引用合法性、管理员控制启用状态。</p>
 *
 * <p>该表不参与租户隔离（无 org_id 字段），已加入 TenantLineHandler 忽略清单。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_skill_meta")
public class SkillMetaEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** Skill 业务 ID（雪花算法） */
    @TableId(value = "skill_id", type = IdType.ASSIGN_ID)
    private Long skillId;

    /** Skill 唯一标识（对应 Python skill_name，如 login / form_fill） */
    @TableField("name")
    private String name;

    /** 用途描述 */
    @TableField("description")
    private String description;

    /** 分类：auth / interaction / extraction */
    @TableField("category")
    private String category;

    /** 参数 JSON Schema（Pydantic params_model 的 JSON Schema，供前端动态生成参数表单） */
    @TableField("param_schema")
    private String paramSchema;

    /** 失败策略：RETRY / SKIP / ABORT */
    @TableField("error_strategy")
    private String errorStrategy;

    /** 最大重试次数 */
    @TableField("max_retries")
    private Integer maxRetries;

    /** 版本号 */
    @TableField("version")
    private String version;

    /** 启用状态（0-禁用 1-启用） */
    @TableField("enabled")
    private Integer enabled;

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

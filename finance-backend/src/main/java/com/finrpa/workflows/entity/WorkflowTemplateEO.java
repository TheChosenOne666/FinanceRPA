package com.finrpa.workflows.entity;

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
 * 工作流模板实体（对应 rpa_workflow_template 表）
 *
 * <p>全局共享的工作流模板，存储 6 个内置金融场景模板与用户自定义模板。
 * params 定义参数清单（含加密标记），steps 定义 Skill 调用序列与参数映射。</p>
 *
 * <p>该表不参与租户隔离（无 org_id 字段），已加入 TenantLineHandler 忽略清单。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_workflow_template")
public class WorkflowTemplateEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 工作流业务 ID（雪花算法） */
    @TableId(value = "workflow_id", type = IdType.ASSIGN_ID)
    private Long workflowId;

    /** 模板名称（唯一） */
    @TableField("name")
    private String name;

    /** 模板描述 */
    @TableField("description")
    private String description;

    /** 行业：banking / insurance / securities */
    @TableField("industry")
    private String industry;

    /** 风险等级：low / medium / high / critical */
    @TableField("risk_level")
    private String riskLevel;

    /** 参数定义 JSON 数组（[{name, type, required, encrypted}]） */
    @TableField("params")
    private String params;

    /** 步骤 JSON 数组（[{skill, params_mapping}]） */
    @TableField("steps")
    private String steps;

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

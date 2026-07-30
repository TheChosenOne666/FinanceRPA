package com.finrpa.agent.entity;

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
 * 审计日志实体（对应 rpa_audit_log 表）
 *
 * <p>由 Python Executor 在任务执行过程中回调写入，记录每个操作行为，用于安全审计与合规追溯。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_audit_log")
public class AuditLogEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 审计日志业务 ID（雪花算法） */
    @TableId(value = "audit_id", type = IdType.ASSIGN_ID)
    private Long auditId;

    /** 任务 ID */
    @TableField("task_id")
    private Long taskId;

    /** 组织 ID（租户隔离） */
    @TableField("org_id")
    private Long orgId;

    /** 动作类型（NAVIGATE/CLICK/INPUT_TEXT/LOGIN 等） */
    @TableField("action_type")
    private String actionType;

    /** 目标元素描述 */
    @TableField("target_element")
    private String targetElement;

    /** 页面 URL */
    @TableField("page_url")
    private String pageUrl;

    /** 执行结果（success/failed） */
    @TableField("execution_result")
    private String executionResult;

    /** 错误信息（失败时填写） */
    @TableField("error_message")
    private String errorMessage;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;
}

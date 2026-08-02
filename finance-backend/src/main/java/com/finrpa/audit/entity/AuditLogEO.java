package com.finrpa.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 审计日志实体（对应 rpa_audit_log 表，按系统设计 6.4.1 完整结构）
 *
 * <p>由 Python Executor 在任务执行过程中回调写入，记录每个操作行为，用于安全审计与合规追溯。
 * 字段覆盖基本信息 / 操作信息 / 风险信息 / 时间信息 / 截图 / LLM 信息六大维度。</p>
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

    // region 基本信息

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

    /** 部门 ID（多维检索用） */
    @TableField("department_id")
    private Long departmentId;

    /** 业务线 ID（多维检索用） */
    @TableField("business_line_id")
    private Long businessLineId;

    /** 触发用户 ID */
    @TableField("user_id")
    private Long userId;

    // endregion

    // region 操作信息

    /** 动作类型（NAVIGATE/CLICK/INPUT_TEXT/LOGIN 等） */
    @TableField("action_type")
    private String actionType;

    /** 目标元素描述 */
    @TableField("target_element")
    private String targetElement;

    /** 页面 URL */
    @TableField("page_url")
    private String pageUrl;

    /** 操作参数 JSON（经 SanitizeService 脱敏后存储） */
    @TableField("action_params")
    private String actionParams;

    /** 执行结果（success/failed） */
    @TableField("execution_result")
    private String executionResult;

    /** 错误信息（失败时填写） */
    @TableField("error_message")
    private String errorMessage;

    // endregion

    // region 风险信息

    /** 风险等级：low / medium / high / critical */
    @TableField("risk_level")
    private String riskLevel;

    /** 关联审批单 ID（经审批的任务填写） */
    @TableField("approval_id")
    private Long approvalId;

    // endregion

    // region 时间信息

    /** 操作开始时间 */
    @TableField("started_at")
    private Timestamp startedAt;

    /** 操作完成时间 */
    @TableField("completed_at")
    private Timestamp completedAt;

    /** 操作耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    // endregion

    // region 截图信息（M7.2 MinIO 预签名 URL，M7.1 预留字段）

    /** 操作前截图 URL（M7.2 MinIO 预签名，有效期 1 小时） */
    @TableField("before_screenshot_url")
    private String beforeScreenshotUrl;

    /** 操作后截图 URL（M7.2 MinIO 预签名，有效期 1 小时） */
    @TableField("after_screenshot_url")
    private String afterScreenshotUrl;

    // endregion

    // region LLM 信息

    /** LLM 模型名称（如 gpt-4o / claude-sonnet） */
    @TableField("llm_model")
    private String llmModel;

    /** LLM token 用量 */
    @TableField("llm_tokens_used")
    private Integer llmTokensUsed;

    /** LLM 调用成本（美元） */
    @TableField("llm_cost")
    private BigDecimal llmCost;

    // endregion

    // region 通用字段

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;

    // endregion
}

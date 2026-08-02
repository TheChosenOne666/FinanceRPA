package com.finrpa.notification.entity;

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
 * 通知发送尝试记录实体（对应 rpa_notification_attempt 表）
 *
 * <p>记录所有通知发送尝试（成功 / 失败），用于审计追踪与重试统计。
 * 由 {@code NotificationDispatcher} 在每次通道发送后写入。</p>
 *
 * <p>该表无 org_id 字段，由 Java 内部触发流程写入（审批触发 / 重试调度），
 * 已加入 TenantConstant.IGNORED_TABLES 绕过自动租户过滤。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_notification_attempt")
public class NotificationAttemptEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 尝试记录业务 ID（雪花算法） */
    @TableId(value = "attempt_id", type = IdType.ASSIGN_ID)
    private Long attemptId;

    /** 关联审批单 ID（可空，非审批触发的通知无此字段） */
    @TableField("approval_id")
    private Long approvalId;

    /** 关联任务 ID（可空） */
    @TableField("task_id")
    private Long taskId;

    /** 目标用户 ID（可空，M6.6 全局 Webhook 配置下未使用） */
    @TableField("target_user_id")
    private Long targetUserId;

    /** 通道类型：wecom / dingtalk */
    @TableField("channel")
    private String channel;

    /** 模板类型：APPROVAL_PENDING / APPROVAL_TIMEOUT / TASK_FAILED / NEEDS_HUMAN / RISK_ESCALATION */
    @TableField("template")
    private String template;

    /** 消息标题 */
    @TableField("title")
    private String title;

    /** 消息正文（markdown） */
    @TableField("content")
    private String content;

    /** 是否发送成功：1=成功 / 0=失败 */
    @TableField("success")
    private Integer success;

    /** 错误信息（失败时填充） */
    @TableField("error_message")
    private String errorMessage;

    /** 通道原始响应（用于审计 / 调试） */
    @TableField("raw_response")
    private String rawResponse;

    /** 重试次数（首次发送为 0，每次重试 +1） */
    @TableField("retry_count")
    private Integer retryCount;

    /** 入队时间（重试队列任务入队时间） */
    @TableField("enqueued_at")
    private Timestamp enqueuedAt;

    /** 实际发送时间 */
    @TableField("sent_at")
    private Timestamp sentAt;

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

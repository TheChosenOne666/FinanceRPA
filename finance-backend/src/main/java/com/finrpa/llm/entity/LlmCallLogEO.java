package com.finrpa.llm.entity;

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
 * LLM 调用记录实体（对应 rpa_llm_call_log 表）
 *
 * <p>由 Python ResilientCaller 在每次 LLM 调用（含重试）后回调写入，
 * 用于调用统计、成本分析与监控。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_llm_call_log")
public class LlmCallLogEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 调用记录业务 ID（雪花算法） */
    @TableId(value = "call_id", type = IdType.ASSIGN_ID)
    private Long callId;

    /** 任务 ID（可空，非任务上下文调用时为 null） */
    @TableField("task_id")
    private Long taskId;

    /** 组织 ID（租户隔离，可空） */
    @TableField("org_id")
    private Long orgId;

    /** 业务线业务 ID（关联 enterprise_business_line.business_line_id，可空表示未归属业务线） */
    @TableField("business_line_id")
    private Long businessLineId;

    /** LLM 模型名（如 gpt-4o-mini / gpt-4o / gpt-4o-2024-08-06） */
    @TableField("model")
    private String model;

    /** 调用上下文名称（planner / replan / executor 等） */
    @TableField("context_name")
    private String contextName;

    /** 重试次数（0 表示首次调用） */
    @TableField("retry_attempt")
    private Integer retryAttempt;

    /** 调用是否成功 */
    @TableField("success")
    private Boolean success;

    /** 错误信息（失败时填写） */
    @TableField("error_message")
    private String errorMessage;

    /** 调用耗时（毫秒） */
    @TableField("duration_ms")
    private Integer durationMs;

    /** prompt token 数（可空，缓存命中时为 null） */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** completion token 数（可空） */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 总 token 数（可空） */
    @TableField("total_tokens")
    private Integer totalTokens;

    /** 是否命中 Action 缓存（M5.2） */
    @TableField("cache_hit")
    private Boolean cacheHit;

    /** 本次调用成本（美元，按模型 token 单价计算） */
    @TableField("cost")
    private BigDecimal cost;

    /** 调用发生时间（Python 上报的 timestamp，解析为 Timestamp） */
    @TableField("call_time")
    private Timestamp callTime;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;
}

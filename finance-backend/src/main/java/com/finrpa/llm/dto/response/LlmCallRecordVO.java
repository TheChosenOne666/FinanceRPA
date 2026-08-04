package com.finrpa.llm.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * LLM 调用记录 VO（单条记录，对齐 P3 ai-monitoring 原型"调用记录"表）
 *
 * <p>用于前端 LLM 监控页底部的调用记录列表，按时间倒序展示单条 LLM 调用的关键信息。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LlmCallRecordVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 调用记录业务 ID（雪花算法） */
    private Long callId;

    /** 任务 ID（可空，非任务上下文调用时为 null） */
    private Long taskId;

    /** 任务标题（可空，关联 rpa_agent_task.title 用于展示） */
    private String taskTitle;

    /** LLM 模型名 */
    private String model;

    /** 调用上下文名称 */
    private String contextName;

    /** 调用是否成功 */
    private Boolean success;

    /** 是否命中缓存 */
    private Boolean cacheHit;

    /** 本次调用成本（美元） */
    private BigDecimal cost;

    /** 调用耗时（毫秒） */
    private Integer durationMs;

    /** 调用发生时间 */
    private Timestamp callTime;
}

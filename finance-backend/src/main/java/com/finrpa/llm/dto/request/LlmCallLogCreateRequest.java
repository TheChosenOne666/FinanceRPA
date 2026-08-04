package com.finrpa.llm.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * LLM 调用记录创建请求 DTO（Python 回调）
 *
 * <p>Python ResilientCaller 每次调用 LLM（含重试）后通过 {@code POST /internal/llm/calls} 上报。
 * 字段与 Python 侧 {@code LlmCallRecord} Pydantic 模型一一对应。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LlmCallLogCreateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（字符串形式，Python 侧为 str） */
    private String taskId;

    /** 组织 ID（字符串形式） */
    private String orgId;

    /** 业务线 ID（字符串形式，可空，P3 ai-monitoring 原型对齐） */
    private String businessLineId;

    /** LLM 模型名 */
    private String model;

    /** 调用上下文名称（planner / replan / executor 等） */
    private String contextName;

    /** 重试次数（0 表示首次调用） */
    private Integer retryAttempt;

    /** 调用是否成功 */
    private Boolean success;

    /** 错误信息（失败时填写） */
    private String errorMessage;

    /** 调用耗时（毫秒） */
    private Integer durationMs;

    /** prompt token 数 */
    private Integer promptTokens;

    /** completion token 数 */
    private Integer completionTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    /** 是否命中 Action 缓存 */
    private Boolean cacheHit;

    /** 调用发生时间（ISO 8601 字符串，如 2026-08-01T12:34:56.789012） */
    private String timestamp;
}

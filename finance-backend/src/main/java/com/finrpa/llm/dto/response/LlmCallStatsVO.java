package com.finrpa.llm.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * LLM 调用统计 VO
 *
 * <p>按时间范围、模型、任务维度筛选后的聚合统计结果，
 * 用于前端 LLM 监控页（M5.6）展示。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LlmCallStatsVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 总调用次数 */
    private Long totalCalls;

    /** 成功调用次数 */
    private Long successCalls;

    /** 失败调用次数 */
    private Long failedCalls;

    /** 缓存命中次数 */
    private Long cacheHitCalls;

    /** 缓存命中率（0-1） */
    private Double cacheHitRate;

    /** 总 prompt token 数 */
    private Long totalPromptTokens;

    /** 总 completion token 数 */
    private Long totalCompletionTokens;

    /** 总 token 数 */
    private Long totalTokens;

    /** 总成本（美元） */
    private BigDecimal totalCost;

    /** 平均调用耗时（毫秒） */
    private Double avgDurationMs;

    /** 按模型维度的统计列表 */
    private List<ModelStatsVO> modelStats;
}

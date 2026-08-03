package com.finrpa.dashboard.dto.stats;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * LLM 调用聚合统计 DTO（用于概览 LLM 指标）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LlmAggregateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 调用总次数 */
    private Long callCount;

    /** 总成本（美元） */
    private BigDecimal totalCost;

    /** 缓存命中次数 */
    private Long cacheHitCount;
}

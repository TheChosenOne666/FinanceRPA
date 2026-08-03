package com.finrpa.dashboard.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * LLM 成本统计 VO（对齐系统设计 6.9.1 LLM 指标深化，供 costs API 返回）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class CostStatVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** LLM 调用总次数 */
    private Long totalCalls;

    /** LLM 总成本（美元） */
    private BigDecimal totalCost;

    /** 总 token 数 */
    private Long totalTokens;

    /** Action 缓存命中率（0-1） */
    private Double cacheHitRate;

    /** 按模型维度的成本统计列表 */
    private List<ModelCostStatVO> modelCosts;

    /**
     * 按模型维度的成本统计
     */
    @Data
    public static class ModelCostStatVO implements Serializable {

        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /** 模型名称（如 gpt-4o-mini / gpt-4o） */
        private String model;

        /** 调用次数 */
        private Long calls;

        /** 成本（美元） */
        private BigDecimal cost;

        /** token 总数 */
        private Long tokens;
    }
}

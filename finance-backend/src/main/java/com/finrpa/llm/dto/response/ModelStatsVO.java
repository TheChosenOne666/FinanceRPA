package com.finrpa.llm.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单模型统计 VO（按模型维度聚合）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ModelStatsVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型名 */
    private String model;

    /** 调用次数 */
    private Long calls;

    /** 成功调用次数 */
    private Long successCalls;

    /** 总 token 数 */
    private Long totalTokens;

    /** 总成本（美元） */
    private BigDecimal cost;

    /**
     * 全参构造
     *
     * @param model        模型名
     * @param calls        调用次数
     * @param successCalls 成功调用次数
     * @param totalTokens  总 token 数
     * @param cost         总成本
     */
    public ModelStatsVO(String model, Long calls, Long successCalls, Long totalTokens, BigDecimal cost) {
        this.model = model;
        this.calls = calls;
        this.successCalls = successCalls;
        this.totalTokens = totalTokens;
        this.cost = cost;
    }
}

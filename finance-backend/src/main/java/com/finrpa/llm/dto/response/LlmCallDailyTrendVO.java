package com.finrpa.llm.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * LLM 调用按日聚合趋势 VO（对齐 P3 ai-monitoring 原型"成本趋势 7 日折线图"）
 *
 * <p>每个对象表示一天的聚合数据，前端按日期序列绘制折线图。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LlmCallDailyTrendVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 日期（格式 yyyy-MM-dd） */
    private String date;

    /** 当日调用次数 */
    private Long calls;

    /** 当日总成本（美元） */
    private BigDecimal cost;

    /** 当日平均耗时（毫秒） */
    private Double avgDurationMs;
}

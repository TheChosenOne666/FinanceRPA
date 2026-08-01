package com.finrpa.llm.constant;

import java.util.Map;

/**
 * LLM 模块常量
 *
 * <p>定义 LLM 调用记录相关的常量，包括模型 token 单价（用于成本计算）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface LlmConstant {

    /** 模型 token 单价（美元/百万 token），数组格式 [input_price, output_price] */
    Map<String, double[]> MODEL_PRICING = Map.of(
            // gpt-4o-mini: 轻量模型，成本低
            "gpt-4o-mini", new double[]{0.15, 0.60},
            // gpt-4o: 标准模型
            "gpt-4o", new double[]{2.50, 10.00},
            // gpt-4o-2024-08-06: 重型模型
            "gpt-4o-2024-08-06", new double[]{2.50, 10.00}
    );

    /** 默认模型单价（未知模型使用，[0.0, 0.0] 表示不计费） */
    double[] DEFAULT_PRICING = new double[]{0.0, 0.0};

    /** token 单价除数（单价以百万 token 为单位） */
    double PRICING_DIVISOR = 1_000_000.0;

    /** LLM 调用记录表名 */
    String TABLE_NAME = "rpa_llm_call_log";

    /** 默认模型名（与 Python 侧 _DEFAULT_MODEL_NAME 一致） */
    String DEFAULT_MODEL = "gpt-4o-mini";

    /** 默认上下文名称 */
    String DEFAULT_CONTEXT = "unknown";
}

package com.finrpa.approval.constant;

/**
 * 审批模块常量
 *
 * <p>定义风险检测关键词分类、风险类型、金额阈值等常量。
 * 关键词库覆盖银行 / 保险 / 证券 三大金融行业，参考原项目 {@code risk_keywords.py} 设计。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface ApprovalConstant {

    /** 风险关键词表名 */
    String RISK_KEYWORD_TABLE_NAME = "rpa_risk_keyword";

    // region 关键词分类（category）

    /** 高风险操作（如转账、退保、买入等不可逆或资金流动操作） */
    String CATEGORY_HIGH_RISK_OPERATION = "high_risk_operation";

    /** 敏感数据（如银行卡号、身份证号、密码等隐私数据） */
    String CATEGORY_SENSITIVE_DATA = "sensitive_data";

    /** 大额操作关键词（如"5万"、"100万"等金额阈值描述） */
    String CATEGORY_LARGE_AMOUNT = "large_amount";

    // endregion

    // region 风险类型（riskType）—— 用于判定最终风险等级

    /** 高风险类型：命中即触发 LLM 二次判断 */
    String RISK_TYPE_HIGH = "high";

    /** 中风险类型：命中累计达到阈值时触发 LLM 二次判断 */
    String RISK_TYPE_MEDIUM = "medium";

    /** 低风险类型：仅记录不触发 LLM */
    String RISK_TYPE_LOW = "low";

    // endregion

    // region 金额检测阈值（单位：元）

    /** 银行行业大额阈值：5 万元 */
    double BANKING_LARGE_AMOUNT_THRESHOLD = 50_000.0;

    /** 保险行业大额阈值：1 万元 */
    double INSURANCE_LARGE_AMOUNT_THRESHOLD = 10_000.0;

    /** 证券行业大额阈值：10 万元 */
    double SECURITIES_LARGE_AMOUNT_THRESHOLD = 100_000.0;

    /** 默认大额阈值：1 万元（未指定行业时使用） */
    double DEFAULT_LARGE_AMOUNT_THRESHOLD = 10_000.0;

    // endregion

    // region 风险等级判定阈值

    /** 命中敏感数据 + 高风险操作 → critical */
    int CRITICAL_HIT_THRESHOLD = 2;

    /** 命中高风险关键词数 ≥ 此阈值 → high */
    int HIGH_HIT_THRESHOLD = 1;

    /** 命中中风险关键词数 ≥ 此阈值 → medium */
    int MEDIUM_HIT_THRESHOLD = 1;

    // endregion

    // region 预筛结果动作

    /** 预筛结果动作：跳过 LLM 判断，直接执行（低风险） */
    String ACTION_PROCEED = "proceed";

    /** 预筛结果动作：调 Python LLM 二次判断（中高风险） */
    String ACTION_JUDGE = "judge";

    // endregion
}

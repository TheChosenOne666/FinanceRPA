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

    // region 审批状态（M6.3）

    /** 审批状态：待审批 */
    String APPROVAL_STATUS_PENDING = "PENDING";

    /** 审批状态：已通过 */
    String APPROVAL_STATUS_APPROVED = "APPROVED";

    /** 审批状态：已拒绝 */
    String APPROVAL_STATUS_REJECTED = "REJECTED";

    /** 审批状态：已超时 */
    String APPROVAL_STATUS_TIMEOUT = "TIMEOUT";

    // endregion

    // region 审批路由（M6.3）

    /** 审批路由：自动通过（low / medium 风险） */
    String APPROVAL_ROUTE_AUTO = "auto";

    /** 审批路由：部门审批（high 风险） */
    String APPROVAL_ROUTE_DEPARTMENT = "department";

    /** 审批路由：合规审计部审批（critical 风险） */
    String APPROVAL_ROUTE_COMPLIANCE = "compliance";

    // endregion

    // region Redis Pub/Sub 频道（M6.3）

    /** Pub/Sub 频道：审批请求（新审批单创建时发布，前端/管理端订阅） */
    String CHANNEL_APPROVAL_REQUESTS = "approval:requests";

    /** Pub/Sub 频道前缀：审批响应（审批完成时发布，Java 触发 Python + 前端订阅） */
    String CHANNEL_APPROVAL_RESPONSES_PREFIX = "approval:responses:";

    // endregion

    // region 审批超时（M6.3 + M6.4）

    /** 审批默认超时时间（分钟） */
    long DEFAULT_APPROVAL_TIMEOUT_MINUTES = 30;

    /** high 风险审批超时时间（分钟）—— 部门审批 */
    long HIGH_APPROVAL_TIMEOUT_MINUTES = 30;

    /** critical 风险审批超时时间（分钟）—— 合规审计部审批 */
    long CRITICAL_APPROVAL_TIMEOUT_MINUTES = 60;

    // endregion

    // region 审批超时检测调度器（M6.4）

    /** 超时审批扫描 cron 表达式：每分钟执行一次 */
    String TIMEOUT_SCHEDULER_CRON = "0 * * * * ?";

    /** ShedLock 锁名称：审批超时检测（分布式部署下保证仅单节点执行） */
    String TIMEOUT_SCHEDULER_LOCK_NAME = "approval:timeout:scheduler";

    /** ShedLock 锁持有时间（毫秒）：30 秒（小于扫描间隔，避免下一周期阻塞） */
    long TIMEOUT_SCHEDULER_LOCK_AT_MOST = 30_000L;

    /** ShedLock 锁最短持有时间（毫秒）：5 秒 */
    long TIMEOUT_SCHEDULER_LOCK_AT_LEAST = 5_000L;

    // endregion

    // region 审批表名（M6.3）

    /** 审批请求表名 */
    String APPROVAL_REQUEST_TABLE_NAME = "rpa_approval_request";

    // endregion
}

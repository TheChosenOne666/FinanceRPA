package com.finrpa.approval.constant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 风险关键词库常量（内置）
 *
 * <p>硬编码银行 / 保险 / 证券 三大金融行业的关键词，由 {@code RiskKeywordInitializer}
 * 启动时 upsert 到 {@code rpa_risk_keyword} 表。参考原项目 {@code risk_keywords.py} 设计。</p>
 *
 * <p>每个关键词数组格式：{keyword, industry, category, riskType, description}</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public final class RiskKeywordConstant {

    /** 内置关键词记录（不可变列表） */
    public static final List<String[]> BUILTIN_KEYWORDS;

    static {
        List<String[]> keywords = new ArrayList<>();

        // ==================== 银行（banking） ====================

        // 高风险操作（资金流动 / 不可逆操作）
        String[][] bankingHighRisk = {
                {"转账", "banking", "high_risk_operation", "high", "资金转出操作"},
                {"汇款", "banking", "high_risk_operation", "high", "资金汇出操作"},
                {"转出", "banking", "high_risk_operation", "high", "账户资金转出"},
                {"赎回", "banking", "high_risk_operation", "high", "理财产品赎回"},
                {"销户", "banking", "high_risk_operation", "high", "账户销户操作"},
                {"修改限额", "banking", "high_risk_operation", "high", "修改交易限额"},
                {"提升额度", "banking", "high_risk_operation", "high", "提升信用额度"},
                {"解约", "banking", "high_risk_operation", "high", "解除合约"},
                {"冻结", "banking", "high_risk_operation", "high", "账户冻结操作"},
                {"解冻", "banking", "high_risk_operation", "high", "账户解冻操作"},
                {"挂失", "banking", "high_risk_operation", "high", "卡片挂失"},
                {"大额转账", "banking", "high_risk_operation", "high", "大额资金转账"}
        };
        addAll(keywords, bankingHighRisk);

        // 敏感数据
        String[][] bankingSensitive = {
                {"银行卡号", "banking", "sensitive_data", "high", "银行卡号"},
                {"身份证号", "banking", "sensitive_data", "high", "身份证号码"},
                {"密码", "banking", "sensitive_data", "high", "账户密码"},
                {"CVV", "banking", "sensitive_data", "high", "信用卡安全码"},
                {"U盾", "banking", "sensitive_data", "high", "U盾密码"},
                {"验证码", "banking", "sensitive_data", "high", "短信验证码"},
                {"短信码", "banking", "sensitive_data", "high", "短信动态码"},
                {"动态码", "banking", "sensitive_data", "high", "动态口令"}
        };
        addAll(keywords, bankingSensitive);

        // 大额操作关键词
        String[][] bankingLargeAmount = {
                {"5万", "banking", "large_amount", "medium", "5 万元阈值"},
                {"10万", "banking", "large_amount", "medium", "10 万元阈值"},
                {"50万", "banking", "large_amount", "medium", "50 万元阈值"},
                {"100万", "banking", "large_amount", "medium", "100 万元阈值"},
                {"大额", "banking", "large_amount", "medium", "大额操作"}
        };
        addAll(keywords, bankingLargeAmount);

        // ==================== 保险（insurance） ====================

        // 高风险操作
        String[][] insuranceHighRisk = {
                {"退保", "insurance", "high_risk_operation", "high", "保单退保"},
                {"理赔", "insurance", "high_risk_operation", "high", "保险理赔申请"},
                {"保单贷款", "insurance", "high_risk_operation", "high", "保单质押贷款"},
                {"变更受益人", "insurance", "high_risk_operation", "high", "变更保单受益人"},
                {"解除合同", "insurance", "high_risk_operation", "high", "解除保险合同"},
                {"减保", "insurance", "high_risk_operation", "high", "减少保额"},
                {"领取生存金", "insurance", "high_risk_operation", "high", "领取生存金"},
                {"犹豫期退保", "insurance", "high_risk_operation", "high", "犹豫期内退保"}
        };
        addAll(keywords, insuranceHighRisk);

        // 敏感数据
        String[][] insuranceSensitive = {
                {"健康告知", "insurance", "sensitive_data", "high", "健康告知信息"},
                {"病历号", "insurance", "sensitive_data", "high", "病历编号"},
                {"社保号", "insurance", "sensitive_data", "high", "社保账号"}
        };
        addAll(keywords, insuranceSensitive);

        // 大额操作关键词
        String[][] insuranceLargeAmount = {
                {"保额", "insurance", "large_amount", "medium", "保险金额"},
                {"保费", "insurance", "large_amount", "medium", "保险费用"}
        };
        addAll(keywords, insuranceLargeAmount);

        // ==================== 证券（securities） ====================

        // 高风险操作
        String[][] securitiesHighRisk = {
                {"买入", "securities", "high_risk_operation", "high", "证券买入"},
                {"卖出", "securities", "high_risk_operation", "high", "证券卖出"},
                {"撤单", "securities", "high_risk_operation", "high", "撤单操作"},
                {"融资", "securities", "high_risk_operation", "high", "融资买入"},
                {"融券", "securities", "high_risk_operation", "high", "融券卖出"},
                {"转股", "securities", "high_risk_operation", "high", "转股操作"},
                {"转托管", "securities", "high_risk_operation", "high", "证券转托管"},
                {"银证转账", "securities", "high_risk_operation", "high", "银证转账"},
                {"大宗交易", "securities", "high_risk_operation", "high", "大宗交易"},
                {"期权行权", "securities", "high_risk_operation", "high", "期权行权"}
        };
        addAll(keywords, securitiesHighRisk);

        // 敏感数据
        String[][] securitiesSensitive = {
                {"资金账号", "securities", "sensitive_data", "high", "证券资金账号"},
                {"股东账号", "securities", "sensitive_data", "high", "股东账户号"},
                {"交易密码", "securities", "sensitive_data", "high", "交易密码"},
                {"通讯密码", "securities", "sensitive_data", "high", "通讯密码"},
                {"证书密码", "securities", "sensitive_data", "high", "数字证书密码"}
        };
        addAll(keywords, securitiesSensitive);

        // 大额操作关键词
        String[][] securitiesLargeAmount = {
                {"500万", "securities", "large_amount", "medium", "500 万元阈值"},
                {"大宗", "securities", "large_amount", "medium", "大宗交易量"}
        };
        addAll(keywords, securitiesLargeAmount);

        BUILTIN_KEYWORDS = Collections.unmodifiableList(keywords);
    }

    /**
     * 私有构造方法，防止实例化
     */
    private RiskKeywordConstant() {
    }

    /**
     * 批量添加关键词数组到列表
     *
     * @param list     目标列表
     * @param keywords 关键词二维数组
     */
    private static void addAll(List<String[]> list, String[][] keywords) {
        for (String[] keyword : keywords) {
            list.add(keyword);
        }
    }
}

package com.finrpa.approval.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 风险关键词分类枚举
 *
 * <p>定义关键词的类别，用于风险等级判定时加权计算。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Getter
public enum RiskKeywordCategoryEnum {

    /** 高风险操作（资金流动 / 不可逆操作） */
    HIGH_RISK_OPERATION("high_risk_operation", "高风险操作", "high"),
    /** 敏感数据（隐私数据 / 凭证信息） */
    SENSITIVE_DATA("sensitive_data", "敏感数据", "high"),
    /** 大额操作关键词 */
    LARGE_AMOUNT("large_amount", "大额操作", "medium");

    /** 枚举值 */
    private final String value;

    /** 中文名称 */
    private final String label;

    /** 默认风险类型（high / medium / low） */
    private final String riskType;

    RiskKeywordCategoryEnum(String value, String label, String riskType) {
        this.value = value;
        this.label = label;
        this.riskType = riskType;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值
     * @return 枚举实例，不存在返回 null
     */
    public static RiskKeywordCategoryEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        return Arrays.stream(RiskKeywordCategoryEnum.values())
                .filter(e -> e.getValue().equals(value))
                .findFirst()
                .orElse(null);
    }

    /** 获取所有合法枚举值列表 */
    public static List<String> legalValues() {
        return Arrays.stream(RiskKeywordCategoryEnum.values()).map(RiskKeywordCategoryEnum::getValue).toList();
    }
}

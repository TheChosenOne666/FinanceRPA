package com.finrpa.workflows.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 风险等级枚举
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Getter
public enum RiskLevelEnum {

    /** 低风险 */
    LOW("low", "低"),
    /** 中风险 */
    MEDIUM("medium", "中"),
    /** 高风险 */
    HIGH("high", "高"),
    /** 极高风险 */
    CRITICAL("critical", "极高");

    /** 枚举值 */
    private final String value;

    /** 中文名称 */
    private final String label;

    RiskLevelEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值
     * @return 枚举实例，不存在返回 null
     */
    public static RiskLevelEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        return Arrays.stream(RiskLevelEnum.values())
                .filter(e -> e.getValue().equals(value))
                .findFirst()
                .orElse(null);
    }

    /** 获取所有合法枚举值列表 */
    public static List<String> legalValues() {
        return Arrays.stream(RiskLevelEnum.values()).map(RiskLevelEnum::getValue).toList();
    }
}

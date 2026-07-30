package com.finrpa.workflows.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 行业枚举
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Getter
public enum IndustryEnum {

    /** 银行 */
    BANKING("banking", "银行"),
    /** 保险 */
    INSURANCE("insurance", "保险"),
    /** 证券 */
    SECURITIES("securities", "证券");

    /** 枚举值 */
    private final String value;

    /** 中文名称 */
    private final String label;

    IndustryEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值
     * @return 枚举实例，不存在返回 null
     */
    public static IndustryEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        return Arrays.stream(IndustryEnum.values())
                .filter(e -> e.getValue().equals(value))
                .findFirst()
                .orElse(null);
    }

    /** 获取所有合法枚举值列表 */
    public static List<String> legalValues() {
        return Arrays.stream(IndustryEnum.values()).map(IndustryEnum::getValue).toList();
    }
}

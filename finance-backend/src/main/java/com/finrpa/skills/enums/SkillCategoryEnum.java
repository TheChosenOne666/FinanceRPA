package com.finrpa.skills.enums;

/**
 * Skill 分类枚举
 *
 * <p>对应 Python Skill 的 category ClassVar，用于按分类筛选 Skill 元数据。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public enum SkillCategoryEnum {

    /** 认证类（登录、会话保活） */
    AUTH("auth"),

    /** 交互类（表单填充、搜索选择、分页） */
    INTERACTION("interaction"),

    /** 提取类（表格提取、文件下载） */
    EXTRACTION("extraction");

    /** 分类值 */
    private final String value;

    /**
     * 构造分类枚举
     *
     * @param value 分类值
     */
    SkillCategoryEnum(String value) {
        this.value = value;
    }

    /**
     * 获取分类值
     *
     * @return 分类值
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据分类值获取枚举
     *
     * @param value 分类值
     * @return 分类枚举；无匹配时返回 null
     */
    public static SkillCategoryEnum getEnumByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (SkillCategoryEnum category : SkillCategoryEnum.values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        return null;
    }
}

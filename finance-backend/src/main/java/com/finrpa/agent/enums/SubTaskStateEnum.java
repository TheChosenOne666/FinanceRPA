package com.finrpa.agent.enums;

/**
 * 子任务状态枚举
 *
 * <p>状态流转：PENDING → RUNNING → COMPLETED / FAILED / SKIPPED / REPLANNED</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public enum SubTaskStateEnum {

    /** 待执行 */
    PENDING("PENDING"),

    /** 执行中 */
    RUNNING("RUNNING"),

    /** 已完成 */
    COMPLETED("COMPLETED"),

    /** 已失败 */
    FAILED("FAILED"),

    /** 已跳过 */
    SKIPPED("SKIPPED"),

    /** 已重规划 */
    REPLANNED("REPLANNED");

    /** 状态值 */
    private final String value;

    /**
     * 构造子任务状态枚举
     *
     * @param value 状态值
     */
    SubTaskStateEnum(String value) {
        this.value = value;
    }

    /**
     * 获取状态值
     *
     * @return 状态值
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据状态值获取枚举
     *
     * @param value 状态值
     * @return 子任务状态枚举
     */
    public static SubTaskStateEnum getEnumByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (SubTaskStateEnum state : SubTaskStateEnum.values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return null;
    }
}

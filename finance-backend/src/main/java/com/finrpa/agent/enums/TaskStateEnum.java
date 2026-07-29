package com.finrpa.agent.enums;

/**
 * 任务状态枚举
 *
 * <p>状态流转：PENDING → EXECUTING → SUCCESS / FAILED / NEEDS_HUMAN / ABORTED</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public enum TaskStateEnum {

    /** 待执行 */
    PENDING("PENDING"),

    /** 执行中 */
    EXECUTING("EXECUTING"),

    /** 成功 */
    SUCCESS("SUCCESS"),

    /** 失败 */
    FAILED("FAILED"),

    /** 需要人工介入 */
    NEEDS_HUMAN("NEEDS_HUMAN"),

    /** 已终止 */
    ABORTED("ABORTED");

    /** 状态值 */
    private final String value;

    /**
     * 构造任务状态枚举
     *
     * @param value 状态值
     */
    TaskStateEnum(String value) {
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
     * @return 任务状态枚举
     */
    public static TaskStateEnum getEnumByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (TaskStateEnum state : TaskStateEnum.values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return null;
    }
}

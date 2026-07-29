package com.finrpa.agent.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务状态枚举单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class TaskStateEnumTest {

    @Test
    @DisplayName("getEnumByValue - 合法值返回对应枚举")
    void getEnumByValue_ValidValue_ReturnsEnum() {
        assertThat(TaskStateEnum.getEnumByValue("PENDING")).isEqualTo(TaskStateEnum.PENDING);
        assertThat(TaskStateEnum.getEnumByValue("EXECUTING")).isEqualTo(TaskStateEnum.EXECUTING);
        assertThat(TaskStateEnum.getEnumByValue("SUCCESS")).isEqualTo(TaskStateEnum.SUCCESS);
        assertThat(TaskStateEnum.getEnumByValue("FAILED")).isEqualTo(TaskStateEnum.FAILED);
        assertThat(TaskStateEnum.getEnumByValue("NEEDS_HUMAN")).isEqualTo(TaskStateEnum.NEEDS_HUMAN);
        assertThat(TaskStateEnum.getEnumByValue("ABORTED")).isEqualTo(TaskStateEnum.ABORTED);
    }

    @Test
    @DisplayName("getEnumByValue - 非法值返回 null")
    void getEnumByValue_InvalidValue_ReturnsNull() {
        assertThat(TaskStateEnum.getEnumByValue("UNKNOWN")).isNull();
        assertThat(TaskStateEnum.getEnumByValue("pending")).isNull(); // 大小写敏感
        assertThat(TaskStateEnum.getEnumByValue("")).isNull();
    }

    @Test
    @DisplayName("getEnumByValue - null 返回 null")
    void getEnumByValue_NullValue_ReturnsNull() {
        assertThat(TaskStateEnum.getEnumByValue(null)).isNull();
    }

    @Test
    @DisplayName("getValue - 返回状态字符串")
    void getValue_ReturnsCorrectString() {
        assertThat(TaskStateEnum.PENDING.getValue()).isEqualTo("PENDING");
        assertThat(TaskStateEnum.EXECUTING.getValue()).isEqualTo("EXECUTING");
    }
}

package com.finrpa.agent.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 子任务状态枚举单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class SubTaskStateEnumTest {

    @Test
    @DisplayName("getEnumByValue - 合法值返回对应枚举")
    void getEnumByValue_ValidValue_ReturnsEnum() {
        assertThat(SubTaskStateEnum.getEnumByValue("PENDING")).isEqualTo(SubTaskStateEnum.PENDING);
        assertThat(SubTaskStateEnum.getEnumByValue("RUNNING")).isEqualTo(SubTaskStateEnum.RUNNING);
        assertThat(SubTaskStateEnum.getEnumByValue("COMPLETED")).isEqualTo(SubTaskStateEnum.COMPLETED);
        assertThat(SubTaskStateEnum.getEnumByValue("FAILED")).isEqualTo(SubTaskStateEnum.FAILED);
        assertThat(SubTaskStateEnum.getEnumByValue("SKIPPED")).isEqualTo(SubTaskStateEnum.SKIPPED);
        assertThat(SubTaskStateEnum.getEnumByValue("REPLANNED")).isEqualTo(SubTaskStateEnum.REPLANNED);
    }

    @Test
    @DisplayName("getEnumByValue - 非法值返回 null")
    void getEnumByValue_InvalidValue_ReturnsNull() {
        assertThat(SubTaskStateEnum.getEnumByValue("UNKNOWN")).isNull();
        assertThat(SubTaskStateEnum.getEnumByValue("running")).isNull(); // 大小写敏感
        assertThat(SubTaskStateEnum.getEnumByValue("")).isNull();
    }

    @Test
    @DisplayName("getEnumByValue - null 返回 null")
    void getEnumByValue_NullValue_ReturnsNull() {
        assertThat(SubTaskStateEnum.getEnumByValue(null)).isNull();
    }

    @Test
    @DisplayName("getValue - 返回状态字符串")
    void getValue_ReturnsCorrectString() {
        assertThat(SubTaskStateEnum.RUNNING.getValue()).isEqualTo("RUNNING");
        assertThat(SubTaskStateEnum.COMPLETED.getValue()).isEqualTo("COMPLETED");
    }
}

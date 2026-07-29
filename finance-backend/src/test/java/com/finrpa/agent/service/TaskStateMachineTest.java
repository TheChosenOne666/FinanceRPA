package com.finrpa.agent.service;

import com.finrpa.agent.enums.TaskStateEnum;
import com.finrpa.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务状态机单元测试
 *
 * <p>覆盖合法流转、非法流转、终态判定与空参数校验。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class TaskStateMachineTest {

    // region 合法流转

    @Test
    @DisplayName("validateTransition - PENDING → EXECUTING 合法")
    void validateTransition_PendingToExecuting_DoesNotThrow() {
        // 合法流转不应抛异常
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.PENDING, TaskStateEnum.EXECUTING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTransition - PENDING → ABORTED 合法（用户可在执行前终止）")
    void validateTransition_PendingToAborted_DoesNotThrow() {
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.PENDING, TaskStateEnum.ABORTED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTransition - EXECUTING → SUCCESS 合法")
    void validateTransition_ExecutingToSuccess_DoesNotThrow() {
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.EXECUTING, TaskStateEnum.SUCCESS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTransition - EXECUTING → FAILED 合法")
    void validateTransition_ExecutingToFailed_DoesNotThrow() {
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.EXECUTING, TaskStateEnum.FAILED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTransition - EXECUTING → NEEDS_HUMAN 合法")
    void validateTransition_ExecutingToNeedsHuman_DoesNotThrow() {
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.EXECUTING, TaskStateEnum.NEEDS_HUMAN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTransition - EXECUTING → ABORTED 合法")
    void validateTransition_ExecutingToAborted_DoesNotThrow() {
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.EXECUTING, TaskStateEnum.ABORTED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTransition - NEEDS_HUMAN → EXECUTING 合法（人工处置后恢复）")
    void validateTransition_NeedsHumanToExecuting_DoesNotThrow() {
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.NEEDS_HUMAN, TaskStateEnum.EXECUTING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTransition - NEEDS_HUMAN → ABORTED 合法")
    void validateTransition_NeedsHumanToAborted_DoesNotThrow() {
        assertThatCode(() -> TaskStateMachine.validateTransition(TaskStateEnum.NEEDS_HUMAN, TaskStateEnum.ABORTED))
                .doesNotThrowAnyException();
    }

    // endregion

    // region 非法流转

    @Test
    @DisplayName("validateTransition - PENDING → SUCCESS 非法（不能跳过执行直接成功）")
    void validateTransition_PendingToSuccess_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.PENDING, TaskStateEnum.SUCCESS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法状态流转");
    }

    @Test
    @DisplayName("validateTransition - PENDING → FAILED 非法")
    void validateTransition_PendingToFailed_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.PENDING, TaskStateEnum.FAILED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateTransition - PENDING → NEEDS_HUMAN 非法")
    void validateTransition_PendingToNeedsHuman_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.PENDING, TaskStateEnum.NEEDS_HUMAN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateTransition - SUCCESS → EXECUTING 非法（终态不可流转）")
    void validateTransition_SuccessToExecuting_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.SUCCESS, TaskStateEnum.EXECUTING))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法状态流转");
    }

    @Test
    @DisplayName("validateTransition - FAILED → EXECUTING 非法（终态不可流转）")
    void validateTransition_FailedToExecuting_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.FAILED, TaskStateEnum.EXECUTING))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateTransition - ABORTED → EXECUTING 非法（终态不可流转）")
    void validateTransition_AbortedToExecuting_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.ABORTED, TaskStateEnum.EXECUTING))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateTransition - SUCCESS → SUCCESS 自身流转非法")
    void validateTransition_SuccessToSuccess_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.SUCCESS, TaskStateEnum.SUCCESS))
                .isInstanceOf(BusinessException.class);
    }

    // endregion

    // region 空参数校验

    @Test
    @DisplayName("validateTransition - from 为 null 抛参数异常")
    void validateTransition_NullFrom_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(null, TaskStateEnum.EXECUTING))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
    }

    @Test
    @DisplayName("validateTransition - to 为 null 抛参数异常")
    void validateTransition_NullTo_ThrowsBusinessException() {
        assertThatThrownBy(() -> TaskStateMachine.validateTransition(TaskStateEnum.PENDING, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
    }

    // endregion

    // region canTransition

    @Test
    @DisplayName("canTransition - 合法流转返回 true")
    void canTransition_ValidTransition_ReturnsTrue() {
        assertThat(TaskStateMachine.canTransition(TaskStateEnum.PENDING, TaskStateEnum.EXECUTING)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStateEnum.EXECUTING, TaskStateEnum.SUCCESS)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStateEnum.NEEDS_HUMAN, TaskStateEnum.EXECUTING)).isTrue();
    }

    @Test
    @DisplayName("canTransition - 非法流转返回 false")
    void canTransition_InvalidTransition_ReturnsFalse() {
        assertThat(TaskStateMachine.canTransition(TaskStateEnum.PENDING, TaskStateEnum.SUCCESS)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStateEnum.SUCCESS, TaskStateEnum.EXECUTING)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStateEnum.ABORTED, TaskStateEnum.EXECUTING)).isFalse();
    }

    @Test
    @DisplayName("canTransition - null 参数返回 false")
    void canTransition_NullParameters_ReturnsFalse() {
        assertThat(TaskStateMachine.canTransition(null, TaskStateEnum.EXECUTING)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStateEnum.PENDING, null)).isFalse();
        assertThat(TaskStateMachine.canTransition(null, null)).isFalse();
    }

    // endregion

    // region isTerminal

    @Test
    @DisplayName("isTerminal - 终态返回 true")
    void isTerminal_TerminalStates_ReturnsTrue() {
        assertThat(TaskStateMachine.isTerminal(TaskStateEnum.SUCCESS)).isTrue();
        assertThat(TaskStateMachine.isTerminal(TaskStateEnum.FAILED)).isTrue();
        assertThat(TaskStateMachine.isTerminal(TaskStateEnum.ABORTED)).isTrue();
    }

    @Test
    @DisplayName("isTerminal - 非终态返回 false")
    void isTerminal_NonTerminalStates_ReturnsFalse() {
        assertThat(TaskStateMachine.isTerminal(TaskStateEnum.PENDING)).isFalse();
        assertThat(TaskStateMachine.isTerminal(TaskStateEnum.EXECUTING)).isFalse();
        assertThat(TaskStateMachine.isTerminal(TaskStateEnum.NEEDS_HUMAN)).isFalse();
    }

    // endregion
}

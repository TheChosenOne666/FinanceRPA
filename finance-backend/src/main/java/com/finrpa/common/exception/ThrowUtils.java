package com.finrpa.common.exception;

import com.finrpa.common.response.ErrorCode;

/**
 * 断言式抛异常工具
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class ThrowUtils {

    /**
     * 条件成立时抛出指定运行时异常
     *
     * @param condition       断言条件
     * @param runtimeException 待抛出的运行时异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 条件成立时抛出业务异常（基于错误码）
     *
     * @param condition 断言条件
     * @param errorCode 错误码
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 条件成立时抛出业务异常（基于错误码和自定义消息）
     *
     * @param condition 断言条件
     * @param errorCode 错误码
     * @param message   自定义异常消息
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}

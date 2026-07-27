package com.finrpa.common.exception;

import com.finrpa.common.response.ErrorCode;

/**
 * 业务异常
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码
     */
    private final int code;

    /**
     * 根据错误码与消息构造业务异常
     *
     * @param code    错误码
     * @param message 异常消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 根据错误码枚举构造业务异常
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 根据错误码枚举与自定义消息构造业务异常
     *
     * @param errorCode 错误码枚举
     * @param message   自定义异常消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 获取业务错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }
}

package com.finrpa.ai.client.dto;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;

/**
 * AI 服务调用异常
 *
 * <p>封装 Java 调用 Python AI 服务时的各类失败场景：
 * 服务不可用 / 超时 / 业务错误。继承 {@link BusinessException} 复用全局异常处理。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class AiException extends BusinessException {

    /**
     * 根据错误码枚举构造 AI 服务异常
     *
     * @param errorCode 错误码枚举
     */
    public AiException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 根据错误码枚举与自定义消息构造 AI 服务异常
     *
     * @param errorCode 错误码枚举
     * @param message   自定义异常消息
     */
    public AiException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

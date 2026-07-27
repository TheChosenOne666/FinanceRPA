package com.finrpa.common.response;

/**
 * 响应构造工具
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class ResultUtils {

    /**
     * 构造成功响应
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 统一响应封装
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 根据错误码构造失败响应
     *
     * @param errorCode 错误码枚举
     * @return 统一响应封装
     */
    public static BaseResponse error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    /**
     * 根据错误码与消息构造失败响应
     *
     * @param code    错误码
     * @param message 异常消息
     * @return 统一响应封装
     */
    public static BaseResponse error(int code, String message) {
        return new BaseResponse(code, null, message);
    }

    /**
     * 根据错误码枚举与自定义消息构造失败响应
     *
     * @param errorCode 错误码枚举
     * @param message   自定义异常消息
     * @return 统一响应封装
     */
    public static BaseResponse error(ErrorCode errorCode, String message) {
        return new BaseResponse(errorCode.getCode(), null, message);
    }
}

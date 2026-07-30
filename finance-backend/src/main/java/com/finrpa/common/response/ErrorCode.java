package com.finrpa.common.response;

/**
 * 业务错误码
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    // AI 服务相关错误码（M2.2）
    AI_SERVICE_ERROR(50300, "AI 服务调用失败"),
    AI_SERVICE_UNAVAILABLE(50301, "AI 服务不可用"),
    AI_SERVICE_TIMEOUT(50302, "AI 服务调用超时"),
    // Skill 元数据相关错误码（M3.3）
    SKILL_NOT_FOUND(40401, "Skill 不存在"),
    SKILL_ALREADY_EXISTS(40402, "Skill 已存在"),
    SKILL_NOT_ENABLED(40403, "Skill 已禁用");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造错误码枚举
     *
     * @param code    错误码
     * @param message 错误消息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取错误消息
     *
     * @return 错误消息
     */
    public String getMessage() {
        return message;
    }
}

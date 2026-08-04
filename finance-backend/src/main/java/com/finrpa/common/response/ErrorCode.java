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
    SKILL_NOT_ENABLED(40403, "Skill 已禁用"),
    // 工作流模板相关错误码（M3.4）
    WORKFLOW_NOT_FOUND(40410, "工作流模板不存在"),
    WORKFLOW_ALREADY_EXISTS(40411, "工作流模板已存在"),
    WORKFLOW_DISABLED(40412, "工作流模板已禁用"),
    SKILL_REF_INVALID(40413, "工作流步骤引用的 Skill 不合法"),
    PARAM_VALIDATE_FAILED(40414, "工作流参数校验失败"),
    // Fernet 加密相关错误码（M3.4）
    FERNET_KEY_INVALID(50010, "Fernet 密钥不合法"),
    FERNET_ENCRYPT_FAILED(50011, "Fernet 加密失败"),
    FERNET_DECRYPT_FAILED(50012, "Fernet 解密失败"),
    // 密码策略相关错误码（P2 SEC-1）
    PASSWORD_TOO_WEAK(40301, "密码强度不足"),
    PASSWORD_EXPIRED(40302, "密码已过期，请修改密码"),
    PASSWORD_HISTORY_VIOLATION(40303, "新密码不能与最近使用过的密码重复"),
    // 登录安全策略相关错误码（P2 SEC-2）
    ACCOUNT_LOCKED(40304, "账号已被锁定，请稍后再试"),
    IP_FORBIDDEN(40305, "当前 IP 不允许登录"),
    // 会话管理相关错误码（P2 SEC-3）
    SESSION_INVALID(40306, "会话已失效，请重新登录"),
    SESSION_KICKED(40307, "账号已在其他设备登录"),
    SESSION_TIMEOUT(40308, "会话空闲超时，请重新登录"),
    SESSION_NOT_FOUND(40404, "会话不存在"),
    // 系统健康检查相关错误码（P2 OPS-1）
    HEALTH_CHECK_FAILED(50001, "系统健康检查失败");

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

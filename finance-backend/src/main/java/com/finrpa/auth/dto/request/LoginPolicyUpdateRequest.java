package com.finrpa.auth.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录安全策略配置更新请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LoginPolicyUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 最大连续登录失败次数（1-20） */
    private Integer maxLoginAttempts;

    /** 账号锁定时长（分钟，1-1440） */
    private Integer lockMinutes;

    /** IP 白名单（逗号分隔，空表示不限制） */
    private String ipWhitelist;

    /** IP 黑名单（逗号分隔） */
    private String ipBlacklist;

    /** 是否允许多端并发登录（0-不允许 1-允许） */
    private Integer allowMultiLogin;

    /** 会话空闲超时分钟数（1-1440） */
    private Integer sessionTimeoutMinutes;

    /** 启用状态（0-禁用 1-启用） */
    private Integer enabled;
}

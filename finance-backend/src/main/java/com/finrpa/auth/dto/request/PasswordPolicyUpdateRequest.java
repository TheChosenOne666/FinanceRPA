package com.finrpa.auth.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 密码策略配置更新请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class PasswordPolicyUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 密码最小长度（必填，6-128） */
    private Integer minLength;

    /** 是否要求大写字母（0-不要求 1-要求） */
    private Integer requireUppercase;

    /** 是否要求小写字母（0-不要求 1-要求） */
    private Integer requireLowercase;

    /** 是否要求数字（0-不要求 1-要求） */
    private Integer requireDigit;

    /** 是否要求特殊字符（0-不要求 1-要求） */
    private Integer requireSpecial;

    /** 允许的特殊字符集（可空，仅在 requireSpecial=1 时生效） */
    private String specialChars;

    /** 密码过期天数（0-365，0 表示不过期） */
    private Integer expireDays;

    /** 密码历史记录数（0-20，0 表示不检查） */
    private Integer historyCount;

    /** 启用状态（0-禁用 1-启用） */
    private Integer enabled;
}

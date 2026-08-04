package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重置密码请求 DTO（P1 USR-1）
 *
 * <p>不传 {@code newPassword} 时使用 {@code AuthConstant.DEFAULT_PASSWORD}。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class PasswordResetRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户业务 ID（必填） */
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /** 新密码（空时使用默认密码） */
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String newPassword;
}

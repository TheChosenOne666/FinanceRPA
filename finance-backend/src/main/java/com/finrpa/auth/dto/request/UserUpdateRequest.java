package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑用户请求 DTO（P1 USR-1）
 *
 * <p>仅允许修改真实姓名、头像、邮箱、手机号、部门名称、状态；用户名不可修改。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class UserUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户业务 ID（必填） */
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /** 真实姓名 */
    @Size(max = 32, message = "真实姓名长度不能超过 32")
    private String realName;

    /** 头像地址 */
    private String avatar;

    /** 邮箱 */
    @Email(message = "邮箱格式不合法")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不合法")
    private String phone;

    /** 所属部门名称 */
    private String deptName;

    /** 状态（0-禁用 1-启用） */
    private Integer status;
}

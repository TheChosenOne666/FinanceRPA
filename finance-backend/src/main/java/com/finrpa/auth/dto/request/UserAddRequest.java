package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增用户请求 DTO（P1 USR-1）
 *
 * <p>用户名 + 真实姓名 + 组织 ID 必填；密码可省略，省略时使用 {@code AuthConstant.DEFAULT_PASSWORD}。
 * 手机号 / 邮箱可选；状态默认启用。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class UserAddRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名（登录账号，唯一） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母、数字、下划线")
    private String username;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 32, message = "真实姓名长度不能超过 32")
    private String realName;

    /** 密码（可空，空时使用默认密码） */
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;

    /** 头像地址 */
    private String avatar;

    /** 邮箱 */
    @Email(message = "邮箱格式不合法")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不合法")
    private String phone;

    /** 所属组织 ID（必填；org_admin 新增时自动填充为当前组织，super_admin 可指定任意组织） */
    private Long orgId;

    /** 所属组织名称（冗余字段，方便列表展示） */
    private String orgName;

    /** 所属部门名称（冗余字段） */
    private String deptName;

    /** 状态（0-禁用 1-启用），默认 1 */
    private Integer status;
}

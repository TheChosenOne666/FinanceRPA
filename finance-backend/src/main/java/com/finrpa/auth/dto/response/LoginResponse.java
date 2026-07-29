package com.finrpa.auth.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 登录响应 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LoginResponse {

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** 过期时间（秒） */
    private Long expiresIn;

    /** 登录用户信息 */
    private UserInfo user;

    /**
     * 登录用户信息（内部类）
     *
     * @author <a href="https://github.com/TheChosenOne666">小楼</a>
     * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
     */
    @Data
    public static class UserInfo {
        /** 用户业务 ID（雪花算法 ID） */
        private Long userId;
        /** 用户名 */
        private String username;
        /** 真实姓名 */
        private String realName;
        /** 所属组织 ID（雪花算法 ID） */
        private Long orgId;
        /** 所属组织名称 */
        private String orgName;
        /** 所属部门名称 */
        private String deptName;
        /** 角色编码列表 */
        private List<String> roles;
    }
}
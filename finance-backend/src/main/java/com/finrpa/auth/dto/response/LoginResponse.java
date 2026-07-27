package com.finrpa.auth.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    private UserInfo user;

    @Data
    public static class UserInfo {
        private String userId;
        private String username;
        private String realName;
        private String orgId;
        private String orgName;
        private String deptName;
        private List<String> roles;
    }
}
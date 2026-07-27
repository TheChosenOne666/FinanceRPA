package com.finrpa.auth.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class UserInfoResponse {

    private String userId;

    private String username;

    private String realName;

    private String avatar;

    private String email;

    private String phone;

    private String orgId;

    private String orgName;

    private String deptName;

    private List<String> roles;

    private List<String> permissions;
}
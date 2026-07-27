package com.finrpa.auth.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 用户信息响应 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class UserInfoResponse {

    /** 用户业务 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 头像地址 */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 所属组织 ID */
    private String orgId;

    /** 所属组织名称 */
    private String orgName;

    /** 所属部门名称 */
    private String deptName;

    /** 角色编码列表 */
    private List<String> roles;

    /** 权限编码列表 */
    private List<String> permissions;
}
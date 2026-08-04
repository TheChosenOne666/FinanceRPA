package com.finrpa.auth.constant;

import java.util.Set;

/**
 * 认证模块常量
 *
 * <p>定义内置角色编码集合、用户默认值等。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface AuthConstant {

    /** 内置角色编码集合（不可删除、roleCode 不可修改） */
    Set<String> BUILT_IN_ROLE_CODES = Set.of(
            "super_admin",
            "org_admin",
            "operator",
            "approver",
            "viewer"
    );

    /** 新增用户 / 重置密码的默认密码（BCrypt 加密后入库） */
    String DEFAULT_PASSWORD = "Finrpa@2026";

    /** 用户状态：启用 */
    Integer USER_STATUS_ENABLED = 1;

    /** 用户状态：禁用 */
    Integer USER_STATUS_DISABLED = 0;

    /** 角色状态：启用 */
    Integer ROLE_STATUS_ENABLED = 1;

    /** 角色状态：禁用 */
    Integer ROLE_STATUS_DISABLED = 0;
}

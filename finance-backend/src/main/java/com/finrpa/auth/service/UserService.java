package com.finrpa.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.auth.dto.request.PasswordResetRequest;
import com.finrpa.auth.dto.request.UserAddRequest;
import com.finrpa.auth.dto.request.UserQueryRequest;
import com.finrpa.auth.dto.request.UserRoleAssignRequest;
import com.finrpa.auth.dto.request.UserUpdateRequest;
import com.finrpa.auth.dto.response.UserVO;

/**
 * 用户管理服务接口（P1 USR-1）
 *
 * <p>提供用户的 CRUD、启停、重置密码、分配角色（三维度 RBAC）能力。
 * 调用方（org_admin / super_admin）的权限校验由 {@link com.finrpa.auth.annotation.RequirePermission}
 * 注解 + {@link com.finrpa.auth.aspect.PermissionAspect} 拦截完成。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface UserService {

    /**
     * 分页查询用户列表（按当前组织过滤；super_admin 可跨组织）
     *
     * @param queryRequest 查询条件
     * @param currentOrgId 当前请求组织 ID（用于 org_admin 自动限定本组织）
     * @param isSuperAdmin 是否为超级管理员（true 时允许按 queryRequest.orgId 跨组织查询）
     * @return 分页结果
     */
    IPage<UserVO> listUsers(UserQueryRequest queryRequest, Long currentOrgId, boolean isSuperAdmin);

    /**
     * 根据用户业务 ID 查询用户详情
     *
     * @param userId 用户业务 ID
     * @return 用户视图对象
     */
    UserVO getUserById(Long userId);

    /**
     * 新增用户
     *
     * @param request        新增请求
     * @param currentOrgId   当前请求组织 ID（org_admin 时强制覆盖 request.orgId）
     * @param isSuperAdmin   是否为超级管理员（true 时允许指定任意 orgId）
     * @return 新建用户业务 ID
     */
    Long addUser(UserAddRequest request, Long currentOrgId, boolean isSuperAdmin);

    /**
     * 编辑用户（仅允许修改真实姓名、头像、邮箱、手机号、部门、状态；用户名不可改）
     *
     * @param request 编辑请求
     * @return 是否更新成功
     */
    boolean updateUser(UserUpdateRequest request);

    /**
     * 启用 / 禁用用户
     *
     * @param userId 用户业务 ID
     * @param status 目标状态（0-禁用 1-启用）
     * @return 是否操作成功
     */
    boolean toggleUserStatus(Long userId, Integer status);

    /**
     * 重置密码（不传时使用默认密码）
     *
     * @param request 重置请求
     * @return 是否重置成功
     */
    boolean resetPassword(PasswordResetRequest request);

    /**
     * 逻辑删除用户（deleted = 1）
     *
     * @param userId 用户业务 ID
     * @return 是否删除成功
     */
    boolean deleteUser(Long userId);

    /**
     * 分配角色（三维度 RBAC，全量替换语义）
     *
     * @param request 分配请求
     * @return 是否分配成功
     */
    boolean assignRoles(UserRoleAssignRequest request);
}

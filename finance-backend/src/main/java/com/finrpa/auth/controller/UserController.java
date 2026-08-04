package com.finrpa.auth.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.auth.dto.request.PasswordResetRequest;
import com.finrpa.auth.dto.request.UserAddRequest;
import com.finrpa.auth.dto.request.UserQueryRequest;
import com.finrpa.auth.dto.request.UserRoleAssignRequest;
import com.finrpa.auth.dto.request.UserUpdateRequest;
import com.finrpa.auth.dto.response.UserVO;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.auth.service.UserService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理控制器（P1 USR-1）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/users}）：
 * <ul>
 *   <li>GET /users —— 分页查询用户列表</li>
 *   <li>GET /users/{userId} —— 查询用户详情</li>
 *   <li>POST /users —— 新增用户</li>
 *   <li>PUT /users —— 编辑用户（用户名不可改）</li>
 *   <li>PUT /users/{userId}/status —— 启停用户</li>
 *   <li>PUT /users/reset-password —— 重置密码</li>
 *   <li>DELETE /users/{userId} —— 逻辑删除用户</li>
 *   <li>POST /users/roles —— 分配角色（三维度 RBAC）</li>
 * </ul>
 * </p>
 *
 * <p>权限要求：调用方须为 org_admin / super_admin（由 Controller 内显式校验，
 * 因 @RequirePermission 注解当前实现耦合于 resourceId 提取，用户管理为多资源场景，统一在此校验）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "用户 CRUD / 启停 / 重置密码 / 分配角色（设置页用户管理）")
public class UserController {

    /** 用户管理服务 */
    @Resource
    private UserService userService;

    /** 权限服务（用于判断调用方是否为 org_admin / super_admin） */
    @Resource
    private PermissionService permissionService;

    // region 查询

    /**
     * 分页查询用户列表
     *
     * @param queryRequest 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "用户列表", description = "分页查询用户列表，org_admin 仅能查本组织；super_admin 可跨组织")
    public BaseResponse<IPage<UserVO>> listUsers(UserQueryRequest queryRequest) {
        // 1. 权限校验：仅 org_admin / super_admin 可访问
        UserContext ctx = currentUserContext();
        // 2. 委托 Service
        IPage<UserVO> page = userService.listUsers(queryRequest, ctx.orgId(), ctx.isSuperAdmin());
        return ResultUtils.success(page);
    }

    /**
     * 查询用户详情
     *
     * @param userId 用户业务 ID
     * @return 用户视图对象
     */
    @GetMapping("/{userId}")
    @Operation(summary = "用户详情", description = "根据用户业务 ID 查询用户详情")
    public BaseResponse<UserVO> getUser(@PathVariable Long userId) {
        currentUserContext();
        UserVO vo = userService.getUserById(userId);
        return ResultUtils.success(vo);
    }

    // endregion

    // region 新增

    /**
     * 新增用户
     *
     * @param request 新增请求
     * @return 新建用户业务 ID
     */
    @PostMapping
    @Operation(summary = "新增用户", description = "新增用户（用户名 + 真实姓名必填；密码可省略，默认 Finrpa@2026）")
    public BaseResponse<Long> addUser(@Valid @RequestBody UserAddRequest request) {
        UserContext ctx = currentUserContext();
        Long userId = userService.addUser(request, ctx.orgId(), ctx.isSuperAdmin());
        return ResultUtils.success(userId);
    }

    // endregion

    // region 编辑

    /**
     * 编辑用户
     *
     * @param request 编辑请求
     * @return 操作结果
     */
    @PutMapping
    @Operation(summary = "编辑用户", description = "编辑用户信息（用户名不可改）")
    public BaseResponse<Boolean> updateUser(@Valid @RequestBody UserUpdateRequest request) {
        currentUserContext();
        boolean success = userService.updateUser(request);
        return ResultUtils.success(success);
    }

    /**
     * 启停用户
     *
     * @param userId 用户业务 ID
     * @param status 目标状态（0-禁用 1-启用）
     * @return 操作结果
     */
    @PutMapping("/{userId}/status")
    @Operation(summary = "启停用户", description = "切换用户启用状态（0-禁用 1-启用）")
    public BaseResponse<Boolean> toggleStatus(@PathVariable Long userId,
                                                @RequestParam Integer status) {
        currentUserContext();
        boolean success = userService.toggleUserStatus(userId, status);
        return ResultUtils.success(success);
    }

    /**
     * 重置密码
     *
     * @param request 重置请求
     * @return 操作结果
     */
    @PutMapping("/reset-password")
    @Operation(summary = "重置密码", description = "重置用户密码（不传时使用默认密码 Finrpa@2026）")
    public BaseResponse<Boolean> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        currentUserContext();
        boolean success = userService.resetPassword(request);
        return ResultUtils.success(success);
    }

    // endregion

    // region 删除

    /**
     * 逻辑删除用户
     *
     * @param userId 用户业务 ID
     * @return 操作结果
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "删除用户", description = "逻辑删除用户（deleted=1），同时清理用户-角色关联")
    public BaseResponse<Boolean> deleteUser(@PathVariable Long userId) {
        currentUserContext();
        boolean success = userService.deleteUser(userId);
        return ResultUtils.success(success);
    }

    // endregion

    // region 分配角色

    /**
     * 分配角色（三维度 RBAC，全量替换语义）
     *
     * @param request 分配请求
     * @return 操作结果
     */
    @PostMapping("/roles")
    @Operation(summary = "分配角色", description = "为用户分配角色（三维度 RBAC，全量替换语义）")
    public BaseResponse<Boolean> assignRoles(@Valid @RequestBody UserRoleAssignRequest request) {
        currentUserContext();
        boolean success = userService.assignRoles(request);
        return ResultUtils.success(success);
    }

    // endregion

    // region 私有方法

    /**
     * 获取当前请求的用户上下文（userId / orgId / isSuperAdmin），并校验调用方须为 org_admin / super_admin
     *
     * @return 用户上下文
     */
    private UserContext currentUserContext() {
        // 1. 从 TenantContext 获取 userId / orgId（JwtAuthenticationFilter 已注入）
        String userIdStr = TenantContext.getUserId();
        String orgIdStr = TenantContext.getOrgId();
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带用户信息");
        }
        Long userId = Long.parseLong(userIdStr);
        Long orgId = orgIdStr != null && !orgIdStr.isEmpty() ? Long.parseLong(orgIdStr) : null;

        // 2. 权限校验：仅 org_admin / super_admin 可访问用户管理
        boolean isOrgAdmin = permissionService.isOrgAdmin(userIdStr);
        if (!isOrgAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限：仅组织管理员可管理用户");
        }
        // 3. 区分 super_admin（可跨组织）与 org_admin（仅本组织）
        boolean isSuperAdmin = permissionService.isSuperAdmin(userIdStr);

        return new UserContext(userId, orgId, isSuperAdmin);
    }

    /**
     * 用户上下文记录
     *
     * @param userId       当前用户业务 ID
     * @param orgId        当前请求组织业务 ID
     * @param isSuperAdmin 是否为 super_admin（true 时允许跨组织操作）
     */
    private record UserContext(Long userId, Long orgId, boolean isSuperAdmin) {
    }

    // endregion
}

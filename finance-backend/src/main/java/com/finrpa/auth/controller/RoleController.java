package com.finrpa.auth.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.auth.dto.request.RoleAddRequest;
import com.finrpa.auth.dto.request.RoleQueryRequest;
import com.finrpa.auth.dto.request.RoleUpdateRequest;
import com.finrpa.auth.dto.response.RoleVO;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.auth.service.RoleService;
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

import java.util.List;

/**
 * 角色管理控制器（P1 USR-2）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/roles}）：
 * <ul>
 *   <li>GET /roles —— 分页查询角色列表</li>
 *   <li>GET /roles/all —— 查询全部角色（不分页，用于分配角色下拉选项）</li>
 *   <li>GET /roles/{roleId} —— 查询角色详情</li>
 *   <li>POST /roles —— 新增角色（内置角色编码受保护）</li>
 *   <li>PUT /roles —— 编辑角色（roleCode 不可改）</li>
 *   <li>PUT /roles/{roleId}/status —— 启停角色</li>
 *   <li>DELETE /roles/{roleId} —— 逻辑删除角色（内置角色 + 有用户关联的角色禁止删除）</li>
 * </ul>
 * </p>
 *
 * <p>权限要求：调用方须为 org_admin / super_admin。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/roles")
@Tag(name = "角色管理", description = "角色 CRUD / 启停 / 内置角色保护（设置页角色管理）")
public class RoleController {

    /** 角色管理服务 */
    @Resource
    private RoleService roleService;

    /** 权限服务 */
    @Resource
    private PermissionService permissionService;

    // region 查询

    /**
     * 分页查询角色列表
     *
     * @param queryRequest 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "角色列表", description = "分页查询角色列表，org_admin 仅能查本组织 + 全局内置；super_admin 可跨组织")
    public BaseResponse<IPage<RoleVO>> listRoles(RoleQueryRequest queryRequest) {
        RoleContext ctx = currentRoleContext();
        IPage<RoleVO> page = roleService.listRoles(queryRequest, ctx.orgId(), ctx.isSuperAdmin());
        return ResultUtils.success(page);
    }

    /**
     * 查询全部角色（不分页，用于分配角色下拉选项）
     *
     * @return 角色列表
     */
    @GetMapping("/all")
    @Operation(summary = "全部角色", description = "查询全部启用角色，用于分配角色下拉选项")
    public BaseResponse<List<RoleVO>> listAllRoles() {
        RoleContext ctx = currentRoleContext();
        List<RoleVO> list = roleService.listAllRoles(ctx.orgId(), ctx.isSuperAdmin());
        return ResultUtils.success(list);
    }

    /**
     * 查询角色详情
     *
     * @param roleId 角色业务 ID
     * @return 角色 VO
     */
    @GetMapping("/{roleId}")
    @Operation(summary = "角色详情", description = "根据角色业务 ID 查询角色详情")
    public BaseResponse<RoleVO> getRole(@PathVariable Long roleId) {
        currentRoleContext();
        RoleVO vo = roleService.getRoleById(roleId);
        return ResultUtils.success(vo);
    }

    // endregion

    // region 新增

    /**
     * 新增角色
     *
     * @param request 新增请求
     * @return 新建角色业务 ID
     */
    @PostMapping
    @Operation(summary = "新增角色", description = "新增角色（内置角色编码 super_admin / org_admin / operator / approver / viewer 受保护，禁止新增）")
    public BaseResponse<Long> addRole(@Valid @RequestBody RoleAddRequest request) {
        RoleContext ctx = currentRoleContext();
        Long roleId = roleService.addRole(request, ctx.orgId(), ctx.isSuperAdmin());
        return ResultUtils.success(roleId);
    }

    // endregion

    // region 编辑

    /**
     * 编辑角色
     *
     * @param request 编辑请求
     * @return 操作结果
     */
    @PutMapping
    @Operation(summary = "编辑角色", description = "编辑角色（roleCode 不可改；内置角色仅可改状态/描述）")
    public BaseResponse<Boolean> updateRole(@Valid @RequestBody RoleUpdateRequest request) {
        currentRoleContext();
        boolean success = roleService.updateRole(request);
        return ResultUtils.success(success);
    }

    /**
     * 启停角色
     *
     * @param roleId 角色业务 ID
     * @param status 目标状态
     * @return 操作结果
     */
    @PutMapping("/{roleId}/status")
    @Operation(summary = "启停角色", description = "切换角色启用状态（super_admin / org_admin 内置角色禁止禁用）")
    public BaseResponse<Boolean> toggleStatus(@PathVariable Long roleId,
                                                @RequestParam Integer status) {
        currentRoleContext();
        boolean success = roleService.toggleRoleStatus(roleId, status);
        return ResultUtils.success(success);
    }

    // endregion

    // region 删除

    /**
     * 逻辑删除角色
     *
     * @param roleId 角色业务 ID
     * @return 操作结果
     */
    @DeleteMapping("/{roleId}")
    @Operation(summary = "删除角色", description = "逻辑删除角色（内置角色 + 有用户关联的角色禁止删除）")
    public BaseResponse<Boolean> deleteRole(@PathVariable Long roleId) {
        currentRoleContext();
        boolean success = roleService.deleteRole(roleId);
        return ResultUtils.success(success);
    }

    // endregion

    // region 私有方法

    /**
     * 获取当前请求的角色上下文（orgId / isSuperAdmin），并校验调用方须为 org_admin / super_admin
     *
     * @return 角色上下文
     */
    private RoleContext currentRoleContext() {
        String userIdStr = TenantContext.getUserId();
        String orgIdStr = TenantContext.getOrgId();
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带用户信息");
        }
        Long orgId = orgIdStr != null && !orgIdStr.isEmpty() ? Long.parseLong(orgIdStr) : null;

        boolean isOrgAdmin = permissionService.isOrgAdmin(userIdStr);
        if (!isOrgAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限：仅组织管理员可管理角色");
        }
        boolean isSuperAdmin = permissionService.isSuperAdmin(userIdStr);
        return new RoleContext(orgId, isSuperAdmin);
    }

    /**
     * 角色上下文记录
     *
     * @param orgId        当前请求组织业务 ID
     * @param isSuperAdmin 是否为 super_admin
     */
    private record RoleContext(Long orgId, boolean isSuperAdmin) {
    }

    // endregion
}

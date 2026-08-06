package com.finrpa.auth.controller;

import com.finrpa.auth.dto.request.RolePermissionSaveRequest;
import com.finrpa.auth.dto.response.PermissionVO;
import com.finrpa.auth.dto.response.RolePermissionMatrixVO;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限矩阵控制器（P3 USR-3 权限矩阵可视化）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/permissions}）：
 * <ul>
 *   <li>GET /permissions —— 查询全部权限点（矩阵列定义）</li>
 *   <li>GET /permissions/matrix —— 查询角色权限矩阵（角色列表 + 每个角色已勾选权限 ID 集合）</li>
 *   <li>PUT /permissions/roles/{roleId} —— 保存角色权限（全量替换语义）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/permissions")
@Tag(name = "权限矩阵管理", description = "权限点定义与角色权限矩阵可视化（P3 USR-3）")
public class PermissionController {

    /** 权限服务 */
    @Resource
    private PermissionService permissionService;

    // region 查询

    /**
     * 查询全部权限点（矩阵列定义）
     *
     * @return 权限点列表
     */
    @GetMapping
    @Operation(summary = "权限点列表", description = "查询全部启用的权限点（设置页权限矩阵列定义）")
    public BaseResponse<List<PermissionVO>> listAllPermissions() {
        return ResultUtils.success(permissionService.listAllPermissions());
    }

    /**
     * 查询角色权限矩阵
     *
     * @return 角色权限矩阵行列表
     */
    @GetMapping("/matrix")
    @Operation(summary = "角色权限矩阵", description = "查询角色列表 + 每个角色已勾选的权限 ID 集合")
    public BaseResponse<List<RolePermissionMatrixVO>> getPermissionMatrix() {
        return ResultUtils.success(permissionService.getPermissionMatrix());
    }

    // endregion

    // region 保存

    /**
     * 保存角色权限（全量替换语义）
     *
     * @param roleId  角色业务 ID
     * @param request 保存请求（含权限 ID 集合）
     * @return 操作结果
     */
    @PutMapping("/roles/{roleId}")
    @Operation(summary = "保存角色权限", description = "全量替换该角色的权限关联（先删后插）")
    public BaseResponse<Boolean> saveRolePermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody RolePermissionSaveRequest request) {
        return ResultUtils.success(permissionService.saveRolePermissions(roleId, request.getPermissionIds()));
    }

    // endregion
}

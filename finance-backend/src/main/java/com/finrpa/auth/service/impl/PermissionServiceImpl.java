package com.finrpa.auth.service.impl;

import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    /** 用户 Mapper */
    private final UserMapper userMapper;
    /** 角色 Mapper */
    private final RoleMapper roleMapper;

    /** 互斥角色集合：operator 与 approver 不可同时持有 */
    private static final Set<String> MUTUALLY_EXCLUSIVE_ROLES = Set.of("operator", "approver");

    /**
     * 判断用户是否拥有对指定组织资源的操作权限
     *
     * @param userId        用户 ID
     * @param resourceOrgId 资源所属组织 ID
     * @param action        操作类型（read/view/create/update/delete/approve）
     * @return 是否有权限
     */
    @Override
    public boolean hasPermission(String userId, String resourceOrgId, String action) {
        // 1. 查询用户
        UserEO user = userMapper.selectByUserId(userId);
        if (user == null) {
            return false;
        }

        // 2. 查询角色
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        if (roles.isEmpty()) {
            return false;
        }

        // 3. 同组织判断
        String userOrgId = user.getOrgId();
        boolean isSameOrg = userOrgId != null && userOrgId.equals(resourceOrgId);

        // 4. 角色权限判断
        for (RoleEO role : roles) {
            String roleCode = role.getRoleCode();
            Integer crossRead = role.getIsCrossOrgRead();
            Integer crossApprove = role.getIsCrossOrgApprove();

            // 超级管理员直接放行
            if ("super_admin".equals(roleCode)) {
                return true;
            }

            // 组织管理员仅本组织
            if ("org_admin".equals(roleCode)) {
                return isSameOrg;
            }

            // 查看者读操作
            if ("viewer".equals(roleCode)) {
                if ("read".equals(action) || "view".equals(action)) {
                    return isSameOrg || (crossRead != null && crossRead == 1);
                }
                return false;
            }

            // 操作员写操作需非互斥
            if ("operator".equals(roleCode)) {
                if (isSameOrg && ("create".equals(action) || "update".equals(action) || "delete".equals(action))) {
                    return !hasMutuallyExclusiveRole(roles);
                }
                return false;
            }

            // 审批员审批操作
            if ("approver".equals(roleCode)) {
                if ("approve".equals(action)) {
                    if (isSameOrg) {
                        return !hasMutuallyExclusiveRole(roles);
                    }
                    return crossApprove != null && crossApprove == 1;
                }
                return false;
            }
        }

        return false;
    }

    /**
     * 检查用户是否有权限访问指定资源，将 resourceId 作为资源所属组织 ID 委托给 {@link #hasPermission}
     *
     * @param userId       用户 ID
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param action       操作类型（read/view/create/update/delete/approve）
     * @return 是否有权限
     */
    @Override
    public boolean checkPermission(String userId, String resourceType, String resourceId, String action) {
        // 资源 ID 作为资源所属组织 ID 进行权限判断
        return hasPermission(userId, resourceId, action);
    }

    /**
     * 获取用户所拥有的角色编码列表
     *
     * @param userId 用户 ID
     * @return 角色编码集合
     */
    @Override
    public List<String> getUserRoles(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .map(RoleEO::getRoleCode)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户所拥有的权限标识列表
     *
     * @param userId 用户 ID
     * @return 权限标识集合（去重）
     */
    @Override
    public List<String> getUserPermissions(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .flatMap(role -> getPermissionsByRole(role.getRoleCode()).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 根据角色编码返回该角色对应的权限标识列表
     *
     * @param roleCode 角色编码
     * @return 权限标识列表
     */
    private List<String> getPermissionsByRole(String roleCode) {
        return switch (roleCode) {
            case "super_admin" -> List.of("*");
            case "org_admin" -> List.of("user:manage", "role:manage", "org:manage");
            case "operator" -> List.of("task:create", "task:update", "task:delete", "task:execute");
            case "approver" -> List.of("task:approve", "workflow:approve");
            case "viewer" -> List.of("task:view", "report:view");
            default -> List.of();
        };
    }

    /**
     * 判断用户是否同时持有互斥角色（operator 与 approver）
     *
     * @param roles 用户角色列表
     * @return 是否同时持有互斥角色
     */
    private boolean hasMutuallyExclusiveRole(List<RoleEO> roles) {
        Set<String> userRoleCodes = roles.stream()
                .map(RoleEO::getRoleCode)
                .collect(Collectors.toSet());

        // 统计互斥角色命中数量
        long count = userRoleCodes.stream()
                .filter(MUTUALLY_EXCLUSIVE_ROLES::contains)
                .count();

        return count > 1;
    }

    /**
     * 判断用户是否允许跨组织读操作
     *
     * @param userId 用户 ID
     * @return 是否允许跨组织读
     */
    @Override
    public boolean isCrossOrgReadAllowed(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .anyMatch(role -> role.getIsCrossOrgRead() != null && role.getIsCrossOrgRead() == 1);
    }

    /**
     * 判断用户是否允许跨组织审批操作
     *
     * @param userId 用户 ID
     * @return 是否允许跨组织审批
     */
    @Override
    public boolean isCrossOrgApproveAllowed(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .anyMatch(role -> role.getIsCrossOrgApprove() != null && role.getIsCrossOrgApprove() == 1);
    }
}

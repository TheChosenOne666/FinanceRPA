package com.finrpa.auth.service;

import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    private static final Set<String> MUTUALLY_EXCLUSIVE_ROLES = Set.of("operator", "approver");

    public boolean hasPermission(String userId, String resourceOrgId, String action) {
        UserEO user = userMapper.selectByUserId(userId);
        if (user == null) {
            return false;
        }

        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        if (roles.isEmpty()) {
            return false;
        }

        String userOrgId = user.getOrgId();
        boolean isSameOrg = userOrgId != null && userOrgId.equals(resourceOrgId);

        for (RoleEO role : roles) {
            String roleCode = role.getRoleCode();
            Integer crossRead = role.getIsCrossOrgRead();
            Integer crossApprove = role.getIsCrossOrgApprove();

            if ("super_admin".equals(roleCode)) {
                return true;
            }

            if ("org_admin".equals(roleCode)) {
                return isSameOrg;
            }

            if ("viewer".equals(roleCode)) {
                if ("read".equals(action) || "view".equals(action)) {
                    return isSameOrg || (crossRead != null && crossRead == 1);
                }
                return false;
            }

            if ("operator".equals(roleCode)) {
                if (isSameOrg && ("create".equals(action) || "update".equals(action) || "delete".equals(action))) {
                    return !hasMutuallyExclusiveRole(roles);
                }
                return false;
            }

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

    public boolean checkPermission(String userId, String resourceType, String resourceId, String action) {
        return hasPermission(userId, resourceId, action);
    }

    public List<String> getUserRoles(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .map(RoleEO::getRoleCode)
                .collect(Collectors.toList());
    }

    public List<String> getUserPermissions(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .flatMap(role -> getPermissionsByRole(role.getRoleCode()).stream())
                .distinct()
                .collect(Collectors.toList());
    }

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

    private boolean hasMutuallyExclusiveRole(List<RoleEO> roles) {
        Set<String> userRoleCodes = roles.stream()
                .map(RoleEO::getRoleCode)
                .collect(Collectors.toSet());

        long count = userRoleCodes.stream()
                .filter(MUTUALLY_EXCLUSIVE_ROLES::contains)
                .count();

        return count > 1;
    }

    public boolean isCrossOrgReadAllowed(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .anyMatch(role -> role.getIsCrossOrgRead() != null && role.getIsCrossOrgRead() == 1);
    }

    public boolean isCrossOrgApproveAllowed(String userId) {
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .anyMatch(role -> role.getIsCrossOrgApprove() != null && role.getIsCrossOrgApprove() == 1);
    }
}
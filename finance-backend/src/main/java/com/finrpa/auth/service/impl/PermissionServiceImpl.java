package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.finrpa.auth.dto.response.PermissionVO;
import com.finrpa.auth.dto.response.RolePermissionMatrixVO;
import com.finrpa.auth.entity.PermissionEO;
import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.RolePermissionEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.entity.UserRoleEO;
import com.finrpa.auth.mapper.PermissionMapper;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.RolePermissionMapper;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.mapper.UserRoleMapper;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 *
 * <p>P3 USR-3 权限矩阵可视化：{@code getPermissionsByRole} 改为优先查 sys_role_permission 关联表，
 * DB 无记录时回退硬编码默认值（兼容初始化场景）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    /** 用户 Mapper */
    private final UserMapper userMapper;
    /** 角色 Mapper */
    private final RoleMapper roleMapper;
    /** 用户-角色关联 Mapper（M7.6 三维度 RBAC） */
    private final UserRoleMapper userRoleMapper;
    /** 权限点 Mapper（P3 USR-3） */
    private final PermissionMapper permissionMapper;
    /** 角色-权限关联 Mapper（P3 USR-3） */
    private final RolePermissionMapper rolePermissionMapper;

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
        // 1. 查询用户（JWT 中的 userId 为 String，需转为 Long 查询）
        UserEO user = userMapper.selectByUserId(Long.parseLong(userId));
        if (user == null) {
            return false;
        }

        // 2. 查询角色
        List<RoleEO> roles = roleMapper.selectByUserId(Long.parseLong(userId));
        if (roles.isEmpty()) {
            return false;
        }

        // 3. 同组织判断（user.orgId 为 Long，与 resourceOrgId(String) 比较需统一为字符串）
        Long userOrgId = user.getOrgId();
        boolean isSameOrg = userOrgId != null && userOrgId.toString().equals(resourceOrgId);

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
        List<RoleEO> roles = roleMapper.selectByUserId(Long.parseLong(userId));
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
        List<RoleEO> roles = roleMapper.selectByUserId(Long.parseLong(userId));
        return roles.stream()
                .flatMap(role -> getPermissionsByRole(role.getRoleCode()).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 根据角色编码返回该角色对应的权限标识列表
     *
     * <p>P3 USR-3：优先查 sys_role_permission 关联表，DB 无记录时回退硬编码默认值
     * （兼容 V27 迁移未执行或自定义角色未配置权限的场景）。</p>
     *
     * @param roleCode 角色编码
     * @return 权限标识列表
     */
    private List<String> getPermissionsByRole(String roleCode) {
        // 1. 优先查 DB（P3 USR-3 权限矩阵配置）
        List<String> dbPerms = permissionMapper.selectPermissionCodesByRoleCode(roleCode);
        if (dbPerms != null && !dbPerms.isEmpty()) {
            return dbPerms;
        }
        // 2. DB 无记录时回退硬编码默认值
        return getDefaultPermissionsByRole(roleCode);
    }

    /**
     * 内置角色权限硬编码默认值（DB 配置缺失时的兜底）
     *
     * @param roleCode 角色编码
     * @return 权限标识列表
     */
    private List<String> getDefaultPermissionsByRole(String roleCode) {
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
        List<RoleEO> roles = roleMapper.selectByUserId(Long.parseLong(userId));
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
        List<RoleEO> roles = roleMapper.selectByUserId(Long.parseLong(userId));
        return roles.stream()
                .anyMatch(role -> role.getIsCrossOrgApprove() != null && role.getIsCrossOrgApprove() == 1);
    }

    /**
     * 判断用户是否为组织管理员（org_admin 或 super_admin），可查看整个组织的数据
     *
     * @param userId 用户 ID
     * @return 是否为组织管理员
     */
    @Override
    public boolean isOrgAdmin(String userId) {
        List<String> roleCodes = getUserRoles(userId);
        return roleCodes.contains("super_admin") || roleCodes.contains("org_admin");
    }

    /**
     * 判断用户是否为超级管理员（super_admin）
     *
     * @param userId 用户 ID
     * @return 是否为超级管理员
     */
    @Override
    public boolean isSuperAdmin(String userId) {
        List<String> roleCodes = getUserRoles(userId);
        return roleCodes.contains("super_admin");
    }

    /**
     * 获取用户关联的业务线 ID 集合（M7.6 三维度 RBAC）
     *
     * <p>从 sys_user_role 关联中提取该用户所有非 NULL 的 business_line_id。
     * 若用户存在 business_line_id 为 NULL 的关联，表示不限业务线，返回 null 表示"全部可见"。</p>
     *
     * @param userId 用户 ID
     * @return 业务线 ID 集合；null 表示全部可见（用户有不限业务线的关联）；空集合表示无任何关联
     */
    @Override
    public Set<Long> getUserBusinessLineIds(String userId) {
        List<UserRoleEO> relations = userRoleMapper.selectByUserId(Long.parseLong(userId));
        if (relations.isEmpty()) {
            return new HashSet<>();
        }
        // 1. 存在 business_line_id 为 NULL 的关联 → 不限业务线
        boolean hasUnbounded = relations.stream().anyMatch(r -> r.getBusinessLineId() == null);
        if (hasUnbounded) {
            return null;
        }
        // 2. 收集所有非 NULL 的业务线 ID
        return relations.stream()
                .map(UserRoleEO::getBusinessLineId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户关联的部门 ID 集合（M7.6 三维度 RBAC）
     *
     * <p>从 sys_user_role 关联中提取该用户所有非 NULL 的 department_id。
     * 若用户存在 department_id 为 NULL 的关联，表示不限部门，返回 null 表示"全部可见"。</p>
     *
     * @param userId 用户 ID
     * @return 部门 ID 集合；null 表示全部可见；空集合表示无任何关联
     */
    @Override
    public Set<Long> getUserDepartmentIds(String userId) {
        List<UserRoleEO> relations = userRoleMapper.selectByUserId(Long.parseLong(userId));
        if (relations.isEmpty()) {
            return new HashSet<>();
        }
        // 1. 存在 department_id 为 NULL 的关联 → 不限部门
        boolean hasUnbounded = relations.stream().anyMatch(r -> r.getDepartmentId() == null);
        if (hasUnbounded) {
            return null;
        }
        // 2. 收集所有非 NULL 的部门 ID
        return relations.stream()
                .map(UserRoleEO::getDepartmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户的主关联（用于任务触发时推断默认部门/业务线）
     *
     * <p>主关联定义：用户的第一条 sys_user_role 记录（按 id 升序）。
     * 任务创建时若未显式传入 departmentId/businessLineId，则从此关联中推断。</p>
     *
     * @param userId 用户 ID
     * @return 主关联实体；无关联时返回 null
     */
    @Override
    public UserRoleEO getPrimaryUserRole(Long userId) {
        List<UserRoleEO> relations = userRoleMapper.selectByUserId(userId);
        return relations.isEmpty() ? null : relations.get(0);
    }

    // region P3 USR-3 权限矩阵可视化

    /**
     * 查询全部权限点（设置页权限矩阵列定义）
     */
    @Override
    public List<PermissionVO> listAllPermissions() {
        // 1. 查询全部启用状态的权限点，按 sort_order 升序
        QueryWrapper<PermissionEO> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)
                .orderByAsc("sort_order");
        List<PermissionEO> list = permissionMapper.selectList(wrapper);

        // 2. 转换为 VO
        return list.stream().map(eo -> {
            PermissionVO vo = new PermissionVO();
            BeanUtils.copyProperties(eo, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 查询角色权限矩阵
     */
    @Override
    public List<RolePermissionMatrixVO> getPermissionMatrix() {
        // 1. 查询全部未删除角色，按 role_id 升序
        QueryWrapper<RoleEO> roleWrapper = new QueryWrapper<>();
        roleWrapper.orderByAsc("role_id");
        List<RoleEO> roles = roleMapper.selectList(roleWrapper);

        // 2. 内置角色编码集合（用于前端提示是否可修改）
        Set<String> builtInCodes = Set.of(
                "super_admin", "org_admin", "operator", "approver", "viewer",
                "admin", "ops", "user"
        );

        // 3. 逐角色查询已勾选权限 ID 集合
        return roles.stream().map(role -> {
            RolePermissionMatrixVO vo = new RolePermissionMatrixVO();
            vo.setRoleId(role.getRoleId());
            vo.setRoleCode(role.getRoleCode());
            vo.setRoleName(role.getRoleName());
            vo.setBuiltIn(builtInCodes.contains(role.getRoleCode()));
            List<Long> permIds = rolePermissionMapper.selectPermissionIdsByRoleId(role.getRoleId());
            vo.setPermissionIds(permIds != null ? permIds : new ArrayList<>());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 保存角色权限（全量替换语义：先删后插）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveRolePermissions(Long roleId, List<Long> permissionIds) {
        // 1. 参数校验
        ThrowUtils.throwIf(roleId == null, ErrorCode.PARAMS_ERROR, "角色 ID 不能为空");
        ThrowUtils.throwIf(permissionIds == null, ErrorCode.PARAMS_ERROR, "权限 ID 集合不能为空");

        // 2. 校验角色存在
        QueryWrapper<RoleEO> roleWrapper = new QueryWrapper<>();
        roleWrapper.eq("role_id", roleId);
        RoleEO role = roleMapper.selectOne(roleWrapper);
        ThrowUtils.throwIf(role == null, ErrorCode.NOT_FOUND_ERROR, "角色不存在: " + roleId);

        // 3. 删除该角色的全部权限关联
        QueryWrapper<RolePermissionEO> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("role_id", roleId);
        rolePermissionMapper.delete(deleteWrapper);

        // 4. 批量插入新关联（permissionIds 为空则仅清空）
        if (!permissionIds.isEmpty()) {
            for (Long permId : permissionIds) {
                RolePermissionEO eo = new RolePermissionEO();
                eo.setRoleId(roleId);
                eo.setPermissionId(permId);
                rolePermissionMapper.insert(eo);
            }
        }

        log.info("保存角色权限: roleId={}, roleCode={}, permissionIds={}",
                roleId, role.getRoleCode(), permissionIds);
        return true;
    }

    // endregion
}

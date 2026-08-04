package com.finrpa.auth.service;

import java.util.List;

/**
 * 权限服务接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface PermissionService {

    /**
     * 判断用户是否拥有对指定组织资源的操作权限
     *
     * @param userId        用户 ID
     * @param resourceOrgId 资源所属组织 ID
     * @param action        操作类型（read/view/create/update/delete/approve）
     * @return 是否有权限
     */
    boolean hasPermission(String userId, String resourceOrgId, String action);

    /**
     * 检查用户是否有权限访问指定资源
     *
     * @param userId       用户 ID
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param action       操作类型（read/view/create/update/delete/approve）
     * @return 是否有权限
     */
    boolean checkPermission(String userId, String resourceType, String resourceId, String action);

    /**
     * 获取用户所拥有的角色编码列表
     *
     * @param userId 用户 ID
     * @return 角色编码集合
     */
    List<String> getUserRoles(String userId);

    /**
     * 获取用户所拥有的权限标识列表
     *
     * @param userId 用户 ID
     * @return 权限标识集合
     */
    List<String> getUserPermissions(String userId);

    /**
     * 判断用户是否允许跨组织读操作
     *
     * @param userId 用户 ID
     * @return 是否允许跨组织读
     */
    boolean isCrossOrgReadAllowed(String userId);

    /**
     * 判断用户是否允许跨组织审批操作
     *
     * @param userId 用户 ID
     * @return 是否允许跨组织审批
     */
    boolean isCrossOrgApproveAllowed(String userId);

    /**
     * 判断用户是否为组织管理员（org_admin 或 super_admin），可查看整个组织的数据
     *
     * @param userId 用户 ID
     * @return 是否为组织管理员
     */
    boolean isOrgAdmin(String userId);

    /**
     * 判断用户是否为超级管理员（super_admin）
     *
     * <p>超级管理员可跨组织操作（如用户/角色管理时指定任意 orgId）。</p>
     *
     * @param userId 用户 ID
     * @return 是否为超级管理员
     */
    boolean isSuperAdmin(String userId);

    /**
     * 获取用户关联的业务线 ID 集合（M7.6 三维度 RBAC）
     *
     * <p>从 sys_user_role 关联中提取该用户所有非 NULL 的 business_line_id。
     * 若用户存在 business_line_id 为 NULL 的关联，表示不限业务线，返回 null 表示"全部可见"。</p>
     *
     * @param userId 用户 ID
     * @return 业务线 ID 集合；null 表示全部可见（用户有不限业务线的关联）；空集合表示无任何关联
     */
    java.util.Set<Long> getUserBusinessLineIds(String userId);

    /**
     * 获取用户关联的部门 ID 集合（M7.6 三维度 RBAC）
     *
     * <p>从 sys_user_role 关联中提取该用户所有非 NULL 的 department_id。
     * 若用户存在 department_id 为 NULL 的关联，表示不限部门，返回 null 表示"全部可见"。</p>
     *
     * @param userId 用户 ID
     * @return 部门 ID 集合；null 表示全部可见；空集合表示无任何关联
     */
    java.util.Set<Long> getUserDepartmentIds(String userId);

    /**
     * 获取用户的主关联（用于任务触发时推断默认部门/业务线）
     *
     * <p>主关联定义：用户的第一条 sys_user_role 记录（按 id 升序）。
     * 任务创建时若未显式传入 departmentId/businessLineId，则从此关联中推断。</p>
     *
     * @param userId 用户 ID
     * @return 主关联实体；无关联时返回 null
     */
    com.finrpa.auth.entity.UserRoleEO getPrimaryUserRole(Long userId);
}

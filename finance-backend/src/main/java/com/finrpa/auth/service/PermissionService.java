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
}

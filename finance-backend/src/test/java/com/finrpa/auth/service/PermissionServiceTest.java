package com.finrpa.auth.service;

import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.service.impl.PermissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private UserEO createUser(Long userId, Long orgId) {
        UserEO user = new UserEO();
        user.setUserId(userId);
        user.setOrgId(orgId);
        return user;
    }

    private RoleEO createRole(String roleCode, Integer crossRead, Integer crossApprove) {
        RoleEO role = new RoleEO();
        role.setRoleCode(roleCode);
        role.setIsCrossOrgRead(crossRead);
        role.setIsCrossOrgApprove(crossApprove);
        return role;
    }

    @Test
    @DisplayName("超级管理员 - 拥有所有权限")
    void hasPermission_SuperAdmin_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("super_admin", 0, 0)));

        boolean result = permissionService.hasPermission("1", "any", "any-action");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("组织管理员 - 同组织有访问权限")
    void hasPermission_OrgAdmin_SameOrg_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("org_admin", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "any-action");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("组织管理员 - 跨组织无访问权限")
    void hasPermission_OrgAdmin_CrossOrg_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("org_admin", 0, 0)));

        boolean result = permissionService.hasPermission("1", "2", "any-action");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("查看者 - 同组织可读")
    void hasPermission_Viewer_SameOrg_Read_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "read");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("查看者 - 跨组织且允许跨组织阅读")
    void hasPermission_Viewer_CrossOrgWithCrossRead_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 1, 0)));

        boolean result = permissionService.hasPermission("1", "2", "read");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("查看者 - 跨组织但不允许跨组织阅读")
    void hasPermission_Viewer_CrossOrgWithoutCrossRead_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 0, 0)));

        boolean result = permissionService.hasPermission("1", "2", "read");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("操作员 - 同组织可创建")
    void hasPermission_Operator_SameOrg_Create_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("operator", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "create");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("操作员 - 跨组织无权限")
    void hasPermission_Operator_CrossOrg_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("operator", 0, 0)));

        boolean result = permissionService.hasPermission("1", "2", "create");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("审批员 - 同组织可审批")
    void hasPermission_Approver_SameOrg_Approve_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "approve");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("审批员 - 跨组织且允许跨组织审批")
    void hasPermission_Approver_CrossOrgWithCrossApprove_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 1)));

        boolean result = permissionService.hasPermission("1", "2", "approve");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("审批员 - 跨组织但不允许跨组织审批")
    void hasPermission_Approver_CrossOrgWithoutCrossApprove_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 0)));

        boolean result = permissionService.hasPermission("1", "2", "approve");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("操作员和审批员互斥 - 同时拥有两个角色时无权限")
    void hasPermission_MutuallyExclusiveRoles_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(
                createRole("operator", 0, 0),
                createRole("approver", 0, 0)
        ));

        boolean result = permissionService.hasPermission("1", "1", "create");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("获取用户角色列表")
    void getUserRoles_ShouldReturnRoleCodes() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(
                createRole("operator", 0, 0),
                createRole("viewer", 0, 0)
        ));

        List<String> roles = permissionService.getUserRoles("1");

        assertThat(roles).containsExactlyInAnyOrder("operator", "viewer");
    }

    @Test
    @DisplayName("获取用户权限列表")
    void getUserPermissions_ShouldReturnPermissions() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("operator", 0, 0)));

        List<String> permissions = permissionService.getUserPermissions("1");

        assertThat(permissions).containsExactlyInAnyOrder("task:create", "task:update", "task:delete", "task:execute");
    }

    @Test
    @DisplayName("用户不存在 - 无权限")
    void hasPermission_UserNotExist_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(null);

        boolean result = permissionService.hasPermission("1", "1", "read");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("用户无角色 - 无权限")
    void hasPermission_NoRoles_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of());

        boolean result = permissionService.hasPermission("1", "1", "read");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("跨组织阅读权限检查")
    void isCrossOrgReadAllowed_ShouldReturnTrue() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 1, 0)));

        boolean result = permissionService.isCrossOrgReadAllowed("1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("跨组织审批权限检查")
    void isCrossOrgApproveAllowed_ShouldReturnTrue() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 1)));

        boolean result = permissionService.isCrossOrgApproveAllowed("1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("角色互斥 - operator和approver互斥时审批操作也无权限")
    void hasPermission_MutuallyExclusiveRoles_Approve_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(
                createRole("operator", 0, 0),
                createRole("approver", 0, 0)
        ));

        boolean result = permissionService.hasPermission("1", "1", "approve");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("角色互斥 - 只有operator角色时不受互斥限制")
    void hasPermission_OnlyOperator_NoMutualExclusion() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("operator", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "create");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("角色互斥 - 只有approver角色时不受互斥限制")
    void hasPermission_OnlyApprover_NoMutualExclusion() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "approve");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("查看者 - 尝试写操作无权限")
    void hasPermission_Viewer_WriteAction_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 0, 0)));

        boolean createResult = permissionService.hasPermission("1", "1", "create");
        boolean updateResult = permissionService.hasPermission("1", "1", "update");
        boolean deleteResult = permissionService.hasPermission("1", "1", "delete");

        assertThat(createResult).isFalse();
        assertThat(updateResult).isFalse();
        assertThat(deleteResult).isFalse();
    }

    @Test
    @DisplayName("操作员 - 同组织更新操作")
    void hasPermission_Operator_SameOrg_Update_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("operator", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "update");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("操作员 - 同组织删除操作")
    void hasPermission_Operator_SameOrg_Delete_ShouldReturnTrue() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("operator", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "delete");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("审批员 - 尝试写操作无权限")
    void hasPermission_Approver_WriteAction_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 0)));

        boolean createResult = permissionService.hasPermission("1", "1", "create");
        boolean updateResult = permissionService.hasPermission("1", "1", "update");

        assertThat(createResult).isFalse();
        assertThat(updateResult).isFalse();
    }

    @Test
    @DisplayName("未知角色代码 - 无权限")
    void hasPermission_UnknownRole_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("unknown_role", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "read");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("未知操作类型 - 无权限")
    void hasPermission_UnknownAction_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "unknown_action");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("用户orgId为null - 跨组织判断为false")
    void hasPermission_UserOrgIdNull_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, null));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("org_admin", 0, 0)));

        boolean result = permissionService.hasPermission("1", "1", "read");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("资源orgId为null - 同组织判断为false")
    void hasPermission_ResourceOrgIdNull_ShouldReturnFalse() {
        when(userMapper.selectByUserId(1L)).thenReturn(createUser(1L, 1L));
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("org_admin", 0, 0)));

        boolean result = permissionService.hasPermission("1", null, "read");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isCrossOrgReadAllowed - 无角色时返回false")
    void isCrossOrgReadAllowed_NoRoles_ShouldReturnFalse() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of());

        boolean result = permissionService.isCrossOrgReadAllowed("1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isCrossOrgReadAllowed - 无跨组织阅读权限返回false")
    void isCrossOrgReadAllowed_NoCrossReadPermission_ShouldReturnFalse() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 0, 0)));

        boolean result = permissionService.isCrossOrgReadAllowed("1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isCrossOrgApproveAllowed - 无角色时返回false")
    void isCrossOrgApproveAllowed_NoRoles_ShouldReturnFalse() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of());

        boolean result = permissionService.isCrossOrgApproveAllowed("1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isCrossOrgApproveAllowed - 无跨组织审批权限返回false")
    void isCrossOrgApproveAllowed_NoCrossApprovePermission_ShouldReturnFalse() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 0)));

        boolean result = permissionService.isCrossOrgApproveAllowed("1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getUserRoles - 无角色时返回空列表")
    void getUserRoles_NoRoles_ShouldReturnEmptyList() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of());

        List<String> roles = permissionService.getUserRoles("1");

        assertThat(roles).isEmpty();
    }

    @Test
    @DisplayName("getUserPermissions - 无角色时返回空列表")
    void getUserPermissions_NoRoles_ShouldReturnEmptyList() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of());

        List<String> permissions = permissionService.getUserPermissions("1");

        assertThat(permissions).isEmpty();
    }

    @Test
    @DisplayName("getUserPermissions - 超级管理员返回通配符")
    void getUserPermissions_SuperAdmin_ShouldReturnWildcard() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("super_admin", 0, 0)));

        List<String> permissions = permissionService.getUserPermissions("1");

        assertThat(permissions).containsExactly("*");
    }

    @Test
    @DisplayName("getUserPermissions - 组织管理员返回管理权限")
    void getUserPermissions_OrgAdmin_ShouldReturnManagePermissions() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("org_admin", 0, 0)));

        List<String> permissions = permissionService.getUserPermissions("1");

        assertThat(permissions).containsExactlyInAnyOrder("user:manage", "role:manage", "org:manage");
    }

    @Test
    @DisplayName("getUserPermissions - 审批员返回审批权限")
    void getUserPermissions_Approver_ShouldReturnApprovePermissions() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("approver", 0, 0)));

        List<String> permissions = permissionService.getUserPermissions("1");

        assertThat(permissions).containsExactlyInAnyOrder("task:approve", "workflow:approve");
    }

    @Test
    @DisplayName("getUserPermissions - 查看者返回查看权限")
    void getUserPermissions_Viewer_ShouldReturnViewPermissions() {
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(createRole("viewer", 0, 0)));

        List<String> permissions = permissionService.getUserPermissions("1");

        assertThat(permissions).containsExactlyInAnyOrder("task:view", "report:view");
    }
}

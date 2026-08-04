package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.auth.constant.AuthConstant;
import com.finrpa.auth.dto.request.PasswordResetRequest;
import com.finrpa.auth.dto.request.UserAddRequest;
import com.finrpa.auth.dto.request.UserQueryRequest;
import com.finrpa.auth.dto.request.UserRoleAssignRequest;
import com.finrpa.auth.dto.request.UserUpdateRequest;
import com.finrpa.auth.dto.response.UserVO;
import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.mapper.UserRoleMapper;
import com.finrpa.auth.service.PasswordPolicyService;
import com.finrpa.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 用户管理服务实现单元测试（P1 USR-1）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @InjectMocks
    private UserServiceImpl userService;

    private static final Long ORG_ID = 100L;
    private static final Long USER_ID = 200L;

    // region listUsers

    @Test
    @DisplayName("listUsers - org_admin 查询本组织用户成功")
    void listUsers_OrgAdmin_Success() {
        UserEO user = buildEo(USER_ID, "testuser", "测试用户", ORG_ID, 1);
        Page<UserEO> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(user));
        when(userMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(Collections.emptyList());

        UserQueryRequest queryRequest = new UserQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);

        var result = userService.listUsers(queryRequest, ORG_ID, false);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("listUsers - org_admin 无 orgId 抛出异常")
    void listUsers_OrgAdminNullOrgId_ThrowsException() {
        UserQueryRequest queryRequest = new UserQueryRequest();
        assertThrows(BusinessException.class,
                () -> userService.listUsers(queryRequest, null, false));
    }

    @Test
    @DisplayName("listUsers - 每页数量超过 200 抛出异常")
    void listUsers_PageSizeExceedsLimit_ThrowsException() {
        UserQueryRequest queryRequest = new UserQueryRequest();
        queryRequest.setPageSize(201);

        assertThrows(BusinessException.class,
                () -> userService.listUsers(queryRequest, ORG_ID, false));
    }

    // endregion

    // region getUserById

    @Test
    @DisplayName("getUserById - 查询成功并填充角色编码")
    void getUserById_Success() {
        UserEO user = buildEo(USER_ID, "testuser", "测试用户", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(user);

        RoleEO role = new RoleEO();
        role.setRoleId(1L);
        role.setRoleCode("operator");
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(Collections.singletonList(role));

        UserVO vo = userService.getUserById(USER_ID);

        assertThat(vo).isNotNull();
        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        assertThat(vo.getUsername()).isEqualTo("testuser");
        assertThat(vo.getRoles()).containsExactly("operator");
    }

    @Test
    @DisplayName("getUserById - ID 为空抛出异常")
    void getUserById_NullId_ThrowsException() {
        assertThrows(BusinessException.class, () -> userService.getUserById(null));
    }

    @Test
    @DisplayName("getUserById - 用户不存在抛出异常")
    void getUserById_NotFound_ThrowsException() {
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.getUserById(USER_ID));
    }

    // endregion

    // region addUser

    @Test
    @DisplayName("addUser - 携带密码新增成功")
    void addUser_WithPassword_Success() {
        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-pwd");
        when(userMapper.insert(any(UserEO.class))).thenAnswer(invocation -> {
            UserEO eo = invocation.getArgument(0);
            eo.setUserId(USER_ID);
            return 1;
        });

        UserAddRequest request = new UserAddRequest();
        request.setUsername("newuser");
        request.setRealName("新用户");
        request.setPassword("Secret123");

        Long userId = userService.addUser(request, ORG_ID, false);

        assertThat(userId).isEqualTo(USER_ID);
        ArgumentCaptor<UserEO> captor = ArgumentCaptor.forClass(UserEO.class);
        verify(userMapper).insert(captor.capture());
        UserEO inserted = captor.getValue();
        assertThat(inserted.getPassword()).isEqualTo("encoded-pwd");
        assertThat(inserted.getOrgId()).isEqualTo(ORG_ID);
        assertThat(inserted.getStatus()).isEqualTo(AuthConstant.USER_STATUS_ENABLED);
    }

    @Test
    @DisplayName("addUser - 不传密码时使用默认密码")
    void addUser_DefaultPassword_Success() {
        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode(AuthConstant.DEFAULT_PASSWORD)).thenReturn("encoded-default");
        when(userMapper.insert(any(UserEO.class))).thenReturn(1);

        UserAddRequest request = new UserAddRequest();
        request.setUsername("newuser");
        request.setRealName("新用户");

        userService.addUser(request, ORG_ID, false);

        ArgumentCaptor<UserEO> captor = ArgumentCaptor.forClass(UserEO.class);
        verify(userMapper).insert(captor.capture());
        verify(passwordEncoder).encode(AuthConstant.DEFAULT_PASSWORD);
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-default");
    }

    @Test
    @DisplayName("addUser - org_admin 强制使用 currentOrgId（忽略 request.orgId）")
    void addUser_OrgAdmin_ForcesCurrentOrgId() {
        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userMapper.insert(any(UserEO.class))).thenReturn(1);

        UserAddRequest request = new UserAddRequest();
        request.setUsername("newuser");
        request.setRealName("新用户");
        request.setOrgId(999L); // 应被忽略

        userService.addUser(request, ORG_ID, false);

        ArgumentCaptor<UserEO> captor = ArgumentCaptor.forClass(UserEO.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("addUser - super_admin 可指定任意 orgId")
    void addUser_SuperAdmin_SpecifiesOrgId() {
        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userMapper.insert(any(UserEO.class))).thenReturn(1);

        UserAddRequest request = new UserAddRequest();
        request.setUsername("newuser");
        request.setRealName("新用户");
        request.setOrgId(888L);

        userService.addUser(request, ORG_ID, true);

        ArgumentCaptor<UserEO> captor = ArgumentCaptor.forClass(UserEO.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(888L);
    }

    @Test
    @DisplayName("addUser - super_admin 未指定 orgId 时使用 currentOrgId")
    void addUser_SuperAdmin_NoOrgId_UsesCurrent() {
        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userMapper.insert(any(UserEO.class))).thenReturn(1);

        UserAddRequest request = new UserAddRequest();
        request.setUsername("newuser");
        request.setRealName("新用户");
        // orgId 为 null

        userService.addUser(request, ORG_ID, true);

        ArgumentCaptor<UserEO> captor = ArgumentCaptor.forClass(UserEO.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("addUser - super_admin 无 orgId 且未指定 request.orgId 抛出异常")
    void addUser_SuperAdmin_NoOrgId_ThrowsException() {
        UserAddRequest request = new UserAddRequest();
        request.setUsername("newuser");
        request.setRealName("新用户");

        assertThrows(BusinessException.class,
                () -> userService.addUser(request, null, true));
    }

    @Test
    @DisplayName("addUser - 用户名已存在抛出异常")
    void addUser_DuplicateUsername_ThrowsException() {
        UserEO existing = buildEo(USER_ID, "existinguser", "已存在", ORG_ID, 1);
        when(userMapper.selectByUsername("existinguser")).thenReturn(existing);

        UserAddRequest request = new UserAddRequest();
        request.setUsername("existinguser");
        request.setRealName("新用户");

        assertThrows(BusinessException.class,
                () -> userService.addUser(request, ORG_ID, false));
    }

    @Test
    @DisplayName("addUser - 用户名为空抛出异常")
    void addUser_BlankUsername_ThrowsException() {
        UserAddRequest request = new UserAddRequest();
        request.setUsername("");
        request.setRealName("新用户");

        assertThrows(BusinessException.class,
                () -> userService.addUser(request, ORG_ID, false));
    }

    @Test
    @DisplayName("addUser - 真实姓名为空抛出异常")
    void addUser_BlankRealName_ThrowsException() {
        UserAddRequest request = new UserAddRequest();
        request.setUsername("newuser");
        request.setRealName("");

        assertThrows(BusinessException.class,
                () -> userService.addUser(request, ORG_ID, false));
    }

    // endregion

    // region updateUser

    @Test
    @DisplayName("updateUser - 更新真实姓名和状态成功")
    void updateUser_Success() {
        UserEO existing = buildEo(USER_ID, "testuser", "原姓名", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);
        when(userMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setUserId(USER_ID);
        request.setRealName("新姓名");
        request.setStatus(0);

        boolean success = userService.updateUser(request);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("updateUser - userId 为空抛出异常")
    void updateUser_NullUserId_ThrowsException() {
        UserUpdateRequest request = new UserUpdateRequest();
        assertThrows(BusinessException.class, () -> userService.updateUser(request));
    }

    @Test
    @DisplayName("updateUser - 用户不存在抛出异常")
    void updateUser_NotFound_ThrowsException() {
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setUserId(USER_ID);

        assertThrows(BusinessException.class, () -> userService.updateUser(request));
    }

    @Test
    @DisplayName("updateUser - 非法状态值抛出异常")
    void updateUser_InvalidStatus_ThrowsException() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setUserId(USER_ID);
        request.setStatus(5);

        assertThrows(BusinessException.class, () -> userService.updateUser(request));
    }

    // endregion

    // region toggleUserStatus

    @Test
    @DisplayName("toggleUserStatus - 启用用户成功")
    void toggleUserStatus_Success() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 0);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);
        when(userMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        boolean success = userService.toggleUserStatus(USER_ID, 1);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("toggleUserStatus - userId 为空抛出异常")
    void toggleUserStatus_NullUserId_ThrowsException() {
        assertThrows(BusinessException.class,
                () -> userService.toggleUserStatus(null, 1));
    }

    @Test
    @DisplayName("toggleUserStatus - 非法状态值抛出异常")
    void toggleUserStatus_InvalidStatus_ThrowsException() {
        assertThrows(BusinessException.class,
                () -> userService.toggleUserStatus(USER_ID, 5));
    }

    @Test
    @DisplayName("toggleUserStatus - 用户不存在抛出异常")
    void toggleUserStatus_NotFound_ThrowsException() {
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> userService.toggleUserStatus(USER_ID, 1));
    }

    // endregion

    // region resetPassword

    @Test
    @DisplayName("resetPassword - 携带新密码重置成功")
    void resetPassword_WithNewPassword_Success() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded-new");
        when(userMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        PasswordResetRequest request = new PasswordResetRequest();
        request.setUserId(USER_ID);
        request.setNewPassword("NewPass123");

        boolean success = userService.resetPassword(request);

        assertThat(success).isTrue();
        verify(passwordEncoder).encode("NewPass123");
    }

    @Test
    @DisplayName("resetPassword - 不传新密码时使用默认密码")
    void resetPassword_DefaultPassword_Success() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);
        when(passwordEncoder.encode(AuthConstant.DEFAULT_PASSWORD)).thenReturn("encoded-default");
        when(userMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        PasswordResetRequest request = new PasswordResetRequest();
        request.setUserId(USER_ID);

        userService.resetPassword(request);

        verify(passwordEncoder).encode(AuthConstant.DEFAULT_PASSWORD);
    }

    @Test
    @DisplayName("resetPassword - userId 为空抛出异常")
    void resetPassword_NullUserId_ThrowsException() {
        PasswordResetRequest request = new PasswordResetRequest();
        assertThrows(BusinessException.class, () -> userService.resetPassword(request));
    }

    @Test
    @DisplayName("resetPassword - 用户不存在抛出异常")
    void resetPassword_NotFound_ThrowsException() {
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);

        PasswordResetRequest request = new PasswordResetRequest();
        request.setUserId(USER_ID);

        assertThrows(BusinessException.class, () -> userService.resetPassword(request));
    }

    // endregion

    // region deleteUser

    @Test
    @DisplayName("deleteUser - 逻辑删除用户并清理角色关联")
    void deleteUser_Success() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);
        when(userMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(userRoleMapper.deleteByUserId(USER_ID)).thenReturn(2);

        boolean success = userService.deleteUser(USER_ID);

        assertThat(success).isTrue();
        // 验证同时清理了角色关联
        verify(userRoleMapper).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("deleteUser - userId 为空抛出异常")
    void deleteUser_NullUserId_ThrowsException() {
        assertThrows(BusinessException.class, () -> userService.deleteUser(null));
    }

    @Test
    @DisplayName("deleteUser - 用户不存在抛出异常")
    void deleteUser_NotFound_ThrowsException() {
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.deleteUser(USER_ID));
    }

    // endregion

    // region assignRoles

    @Test
    @DisplayName("assignRoles - 全量替换角色成功")
    void assignRoles_Success() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);

        RoleEO role1 = new RoleEO();
        role1.setRoleId(1L);
        role1.setRoleCode("operator");
        RoleEO role2 = new RoleEO();
        role2.setRoleId(2L);
        role2.setRoleCode("viewer");
        when(roleMapper.selectByRoleIds(any())).thenReturn(List.of(role1, role2));

        when(userRoleMapper.deleteByUserId(USER_ID)).thenReturn(1);
        when(userRoleMapper.insertBatch(any())).thenReturn(2);

        UserRoleAssignRequest request = new UserRoleAssignRequest();
        request.setUserId(USER_ID);
        UserRoleAssignRequest.UserRoleRelation rel1 = new UserRoleAssignRequest.UserRoleRelation();
        rel1.setRoleId(1L);
        rel1.setDepartmentId(10L);
        rel1.setBusinessLineId(100L);
        UserRoleAssignRequest.UserRoleRelation rel2 = new UserRoleAssignRequest.UserRoleRelation();
        rel2.setRoleId(2L);
        request.setRelations(List.of(rel1, rel2));

        boolean success = userService.assignRoles(request);

        assertThat(success).isTrue();
        verify(userRoleMapper).deleteByUserId(USER_ID);
        verify(userRoleMapper).insertBatch(any());
    }

    @Test
    @DisplayName("assignRoles - 空关联列表清空所有角色")
    void assignRoles_EmptyRelations_ClearsAll() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);
        when(userRoleMapper.deleteByUserId(USER_ID)).thenReturn(1);

        UserRoleAssignRequest request = new UserRoleAssignRequest();
        request.setUserId(USER_ID);
        request.setRelations(Collections.emptyList());

        boolean success = userService.assignRoles(request);

        assertThat(success).isTrue();
        verify(userRoleMapper).deleteByUserId(USER_ID);
        verify(userRoleMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("assignRoles - userId 为空抛出异常")
    void assignRoles_NullUserId_ThrowsException() {
        UserRoleAssignRequest request = new UserRoleAssignRequest();
        request.setRelations(Collections.emptyList());
        assertThrows(BusinessException.class, () -> userService.assignRoles(request));
    }

    @Test
    @DisplayName("assignRoles - relations 为 null 抛出异常")
    void assignRoles_NullRelations_ThrowsException() {
        UserRoleAssignRequest request = new UserRoleAssignRequest();
        request.setUserId(USER_ID);
        request.setRelations(null);
        assertThrows(BusinessException.class, () -> userService.assignRoles(request));
    }

    @Test
    @DisplayName("assignRoles - 用户不存在抛出异常")
    void assignRoles_UserNotFound_ThrowsException() {
        when(userMapper.selectByUserId(USER_ID)).thenReturn(null);

        UserRoleAssignRequest request = new UserRoleAssignRequest();
        request.setUserId(USER_ID);
        UserRoleAssignRequest.UserRoleRelation rel = new UserRoleAssignRequest.UserRoleRelation();
        rel.setRoleId(1L);
        request.setRelations(List.of(rel));

        assertThrows(BusinessException.class, () -> userService.assignRoles(request));
    }

    @Test
    @DisplayName("assignRoles - 角色不存在抛出异常")
    void assignRoles_RoleNotFound_ThrowsException() {
        UserEO existing = buildEo(USER_ID, "testuser", "测试", ORG_ID, 1);
        when(userMapper.selectByUserId(USER_ID)).thenReturn(existing);
        // 仅返回 1 个角色，但请求了 2 个
        RoleEO role1 = new RoleEO();
        role1.setRoleId(1L);
        when(roleMapper.selectByRoleIds(any())).thenReturn(Collections.singletonList(role1));

        UserRoleAssignRequest request = new UserRoleAssignRequest();
        request.setUserId(USER_ID);
        UserRoleAssignRequest.UserRoleRelation rel1 = new UserRoleAssignRequest.UserRoleRelation();
        rel1.setRoleId(1L);
        UserRoleAssignRequest.UserRoleRelation rel2 = new UserRoleAssignRequest.UserRoleRelation();
        rel2.setRoleId(999L); // 不存在
        request.setRelations(List.of(rel1, rel2));

        assertThrows(BusinessException.class, () -> userService.assignRoles(request));
    }

    // endregion

    // region 测试辅助方法

    /**
     * 构建用户 EO
     */
    private UserEO buildEo(Long userId, String username, String realName, Long orgId, Integer status) {
        UserEO eo = new UserEO();
        eo.setUserId(userId);
        eo.setUsername(username);
        eo.setRealName(realName);
        eo.setOrgId(orgId);
        eo.setStatus(status);
        eo.setDeleted(0);
        return eo;
    }

    // endregion
}

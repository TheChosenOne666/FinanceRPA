package com.finrpa.auth.service;

import com.finrpa.auth.dto.response.LoginResponse;
import com.finrpa.auth.dto.response.UserInfoResponse;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.service.impl.AuthServiceImpl;
import com.finrpa.auth.util.JwtUtil;
import com.finrpa.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PermissionService permissionService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private LoginPolicyService loginPolicyService;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserEO createUser(Long userId, String username, String password, Integer status, Long orgId) {
        UserEO user = new UserEO();
        user.setUserId(userId);
        user.setUsername(username);
        user.setPassword(password);
        user.setRealName("张三");
        user.setOrgId(orgId);
        user.setOrgName("测试组织");
        user.setDeptName("技术部");
        user.setStatus(status);
        return user;
    }

    @Test
    @DisplayName("登录 - 成功")
    void login_Success() {
        UserEO user = createUser(1L, "admin", "encoded-password", 1, 1L);

        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateAccessToken("1", "admin", "1", "技术部")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("1", "admin")).thenReturn("refresh-token");
        when(jwtUtil.getExpiresIn()).thenReturn(3600L);
        when(permissionService.getUserRoles("1")).thenReturn(List.of("super_admin"));

        LoginResponse response = authService.login("admin", "password", null, null);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUser().getUserId()).isEqualTo(1L);
        assertThat(response.getUser().getUsername()).isEqualTo("admin");
        assertThat(response.getUser().getRealName()).isEqualTo("张三");
        assertThat(response.getUser().getRoles()).containsExactly("super_admin");
    }

    @Test
    @DisplayName("登录 - 用户不存在")
    void login_UserNotExist_ShouldThrowException() {
        when(userMapper.selectByUsername("nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> authService.login("nonexistent", "password", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("登录 - 用户已禁用")
    void login_UserDisabled_ShouldThrowException() {
        UserEO user = createUser(1L, "admin", "encoded-password", 0, 1L);

        when(userMapper.selectByUsername("admin")).thenReturn(user);

        assertThatThrownBy(() -> authService.login("admin", "password", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户已禁用");
    }

    @Test
    @DisplayName("登录 - 密码错误")
    void login_WrongPassword_ShouldThrowException() {
        UserEO user = createUser(1L, "admin", "encoded-password", 1, 1L);

        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin", "wrong-password", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("刷新令牌 - 成功")
    void refresh_Success() {
        UserEO user = createUser(1L, "admin", "encoded-password", 1, 1L);

        when(jwtUtil.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.getTokenTypeFromToken("valid-refresh-token")).thenReturn("refresh");
        when(jwtUtil.getUserIdFromToken("valid-refresh-token")).thenReturn("1");
        when(userMapper.selectByUserId(1L)).thenReturn(user);
        when(jwtUtil.generateAccessToken("1", "admin", "1", "技术部")).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken("1", "admin")).thenReturn("new-refresh-token");
        when(jwtUtil.getExpiresIn()).thenReturn(3600L);
        when(permissionService.getUserRoles("1")).thenReturn(List.of("super_admin"));

        LoginResponse response = authService.refresh("valid-refresh-token", null, null);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.getUser().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("刷新令牌 - 无效的令牌")
    void refresh_InvalidToken_ShouldThrowException() {
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("invalid-token", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("refreshToken无效或已过期");
    }

    @Test
    @DisplayName("刷新令牌 - 不是刷新令牌类型")
    void refresh_NotRefreshTokenType_ShouldThrowException() {
        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.getTokenTypeFromToken("access-token")).thenReturn("access");

        assertThatThrownBy(() -> authService.refresh("access-token", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的refreshToken");
    }

    @Test
    @DisplayName("刷新令牌 - 用户不存在")
    void refresh_UserNotExist_ShouldThrowException() {
        when(jwtUtil.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.getTokenTypeFromToken("valid-refresh-token")).thenReturn("refresh");
        when(jwtUtil.getUserIdFromToken("valid-refresh-token")).thenReturn("1");
        when(userMapper.selectByUserId(1L)).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh("valid-refresh-token", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("获取当前用户信息 - 成功")
    void getCurrentUser_Success() {
        UserEO user = createUser(1L, "admin", "encoded-password", 1, 1L);
        user.setAvatar("avatar-url");
        user.setEmail("admin@test.com");
        user.setPhone("13800138000");

        when(userMapper.selectByUserId(1L)).thenReturn(user);
        when(permissionService.getUserRoles("1")).thenReturn(List.of("super_admin"));
        when(permissionService.getUserPermissions("1")).thenReturn(List.of("*"));

        UserInfoResponse response = authService.getCurrentUser("1");

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getRealName()).isEqualTo("张三");
        assertThat(response.getAvatar()).isEqualTo("avatar-url");
        assertThat(response.getEmail()).isEqualTo("admin@test.com");
        assertThat(response.getPhone()).isEqualTo("13800138000");
        assertThat(response.getRoles()).containsExactly("super_admin");
        assertThat(response.getPermissions()).containsExactly("*");
    }

    @Test
    @DisplayName("获取当前用户信息 - 用户不存在")
    void getCurrentUser_UserNotExist_ShouldThrowException() {
        when(userMapper.selectByUserId(1L)).thenReturn(null);

        assertThatThrownBy(() -> authService.getCurrentUser("1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("检查权限 - 委托给PermissionService")
    void checkPermission_ShouldDelegateToPermissionService() {
        when(permissionService.checkPermission("1", "task", "1", "create")).thenReturn(true);

        boolean result = authService.checkPermission("1", "task", "1", "create");

        assertThat(result).isTrue();
        verify(permissionService, times(1)).checkPermission("1", "task", "1", "create");
    }
}

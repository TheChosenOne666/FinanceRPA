package com.finrpa.auth.controller;

import com.finrpa.auth.dto.response.LoginResponse;
import com.finrpa.auth.dto.response.UserInfoResponse;
import com.finrpa.auth.service.AuthService;
import com.finrpa.auth.util.JwtUtil;
import com.finrpa.common.response.ResultUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtUtil = mock(JwtUtil.class);
        AuthController controller = new AuthController(authService, jwtUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("登录 - 成功")
    void login_Success() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("access-token");
        response.setRefreshToken("refresh-token");
        response.setExpiresIn(3600L);
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(1L);
        userInfo.setUsername("admin");
        userInfo.setRealName("张三");
        response.setUser(userInfo);

        when(authService.login(eq("admin"), eq("password"), any(), any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.userId").value(1))
                .andExpect(jsonPath("$.data.user.username").value("admin"));

        verify(authService, times(1)).login(eq("admin"), eq("password"), any(), any());
    }

    @Test
    @DisplayName("刷新token - 成功")
    void refresh_Success() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("new-access-token");
        response.setRefreshToken("new-refresh-token");

        when(authService.refresh(eq("old-refresh-token"), any(), any())).thenReturn(response);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));

        verify(authService, times(1)).refresh(eq("old-refresh-token"), any(), any());
    }

    @Test
    @DisplayName("获取当前用户信息 - 成功")
    void getCurrentUser_Success() throws Exception {
        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(1L);
        response.setUsername("admin");
        response.setRealName("张三");
        response.setRoles(List.of("super_admin"));

        when(jwtUtil.getUserIdFromToken("valid-token")).thenReturn("1");
        when(authService.getCurrentUser("1")).thenReturn(response);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("super_admin"));

        verify(jwtUtil, times(1)).getUserIdFromToken("valid-token");
        verify(authService, times(1)).getCurrentUser("1");
    }

    @Test
    @DisplayName("权限检查 - 成功")
    void checkPermission_Success() throws Exception {
        when(jwtUtil.getUserIdFromToken("valid-token")).thenReturn("1");
        when(authService.checkPermission("1", "task", "1", "create")).thenReturn(true);

        mockMvc.perform(post("/auth/permissions/check")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"task\",\"resourceId\":\"1\",\"action\":\"create\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasPermission").value(true));

        verify(authService, times(1)).checkPermission("1", "task", "1", "create");
    }
}

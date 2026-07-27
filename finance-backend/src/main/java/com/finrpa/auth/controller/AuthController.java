package com.finrpa.auth.controller;

import com.finrpa.auth.dto.request.LoginRequest;
import com.finrpa.auth.dto.request.PermissionCheckRequest;
import com.finrpa.auth.dto.request.RefreshRequest;
import com.finrpa.auth.dto.response.LoginResponse;
import com.finrpa.auth.dto.response.UserInfoResponse;
import com.finrpa.auth.service.AuthService;
import com.finrpa.auth.util.JwtUtil;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "认证相关接口")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    @Operation(summary = "登录", description = "用户登录获取token")
    public BaseResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getUsername(), request.getPassword());
        return ResultUtils.success(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新token", description = "使用refreshToken获取新的accessToken")
    public BaseResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return ResultUtils.success(response);
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "根据token获取当前用户信息")
    public BaseResponse<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        String token = extractToken(request);
        String userId = jwtUtil.getUserIdFromToken(token);
        UserInfoResponse response = authService.getCurrentUser(userId);
        return ResultUtils.success(response);
    }

    @PostMapping("/permissions/check")
    @Operation(summary = "权限检查", description = "检查当前用户是否有权限访问指定资源")
    public BaseResponse<Map<String, Object>> checkPermission(
            HttpServletRequest request,
            @Valid @RequestBody PermissionCheckRequest permissionRequest) {
        String token = extractToken(request);
        String userId = jwtUtil.getUserIdFromToken(token);

        boolean hasPermission = authService.checkPermission(
                userId,
                permissionRequest.getResourceType(),
                permissionRequest.getResourceId(),
                permissionRequest.getAction()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("hasPermission", hasPermission);
        return ResultUtils.success(result);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new RuntimeException("未提供token");
    }
}
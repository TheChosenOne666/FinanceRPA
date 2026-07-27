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

/**
 * 认证控制器
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "认证相关接口")
public class AuthController {

    /** 认证服务 */
    private final AuthService authService;
    /** JWT 工具 */
    private final JwtUtil jwtUtil;

    // region 登录相关

    /**
     * 用户登录
     *
     * @param request 登录请求（含用户名和密码）
     * @return 登录响应（含 token 和用户信息）
     */
    @PostMapping("/login")
    @Operation(summary = "登录", description = "用户登录获取token")
    public BaseResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getUsername(), request.getPassword());
        return ResultUtils.success(response);
    }

    // endregion

    // region token 刷新

    /**
     * 刷新 token
     *
     * @param request 刷新请求（含 refreshToken）
     * @return 登录响应（含新的 token 和用户信息）
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新token", description = "使用refreshToken获取新的accessToken")
    public BaseResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return ResultUtils.success(response);
    }

    // endregion

    // region 用户信息

    /**
     * 获取当前用户信息
     *
     * @param request HTTP 请求（用于提取 token）
     * @return 用户信息响应
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "根据token获取当前用户信息")
    public BaseResponse<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        // 从请求头提取 token 并解析用户 ID
        String token = extractToken(request);
        String userId = jwtUtil.getUserIdFromToken(token);
        UserInfoResponse response = authService.getCurrentUser(userId);
        return ResultUtils.success(response);
    }

    // endregion

    // region 权限校验

    /**
     * 权限检查
     *
     * @param request          HTTP 请求（用于提取 token）
     * @param permissionRequest 权限检查请求（含资源类型、资源 ID、操作类型）
     * @return 检查结果（含 hasPermission 字段）
     */
    @PostMapping("/permissions/check")
    @Operation(summary = "权限检查", description = "检查当前用户是否有权限访问指定资源")
    public BaseResponse<Map<String, Object>> checkPermission(
            HttpServletRequest request,
            @Valid @RequestBody PermissionCheckRequest permissionRequest) {
        // 1. 提取 token 并解析用户 ID
        String token = extractToken(request);
        String userId = jwtUtil.getUserIdFromToken(token);

        // 2. 校验权限
        boolean hasPermission = authService.checkPermission(
                userId,
                permissionRequest.getResourceType(),
                permissionRequest.getResourceId(),
                permissionRequest.getAction()
        );

        // 3. 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("hasPermission", hasPermission);
        return ResultUtils.success(result);
    }

    // endregion

    /**
     * 从请求头中提取 Bearer token
     *
     * @param request HTTP 请求
     * @return token 字符串
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new RuntimeException("未提供token");
    }
}
package com.finrpa.auth.service;

import com.finrpa.auth.dto.response.LoginResponse;
import com.finrpa.auth.dto.response.UserInfoResponse;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.util.JwtUtil;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(String username, String password) {
        UserEO user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户已禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getUserId(),
                user.getUsername(),
                user.getOrgId(),
                user.getDeptName()
        );

        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(jwtUtil.getExpiresIn());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setOrgId(user.getOrgId());
        userInfo.setOrgName(user.getOrgName());
        userInfo.setDeptName(user.getDeptName());
        userInfo.setRoles(permissionService.getUserRoles(user.getUserId()));
        response.setUser(userInfo);

        return response;
    }

    public LoginResponse refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "refreshToken无效或已过期");
        }

        String tokenType = jwtUtil.getTokenTypeFromToken(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "无效的refreshToken");
        }

        String userId = jwtUtil.getUserIdFromToken(refreshToken);
        UserEO user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getUserId(),
                user.getUsername(),
                user.getOrgId(),
                user.getDeptName()
        );

        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(jwtUtil.getExpiresIn());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setOrgId(user.getOrgId());
        userInfo.setOrgName(user.getOrgName());
        userInfo.setDeptName(user.getDeptName());
        userInfo.setRoles(permissionService.getUserRoles(user.getUserId()));
        response.setUser(userInfo);

        return response;
    }

    public UserInfoResponse getCurrentUser(String userId) {
        UserEO user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        }

        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setAvatar(user.getAvatar());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setOrgId(user.getOrgId());
        response.setOrgName(user.getOrgName());
        response.setDeptName(user.getDeptName());
        response.setRoles(permissionService.getUserRoles(userId));
        response.setPermissions(permissionService.getUserPermissions(userId));

        return response;
    }

    public boolean checkPermission(String userId, String resourceType, String resourceId, String action) {
        return permissionService.checkPermission(userId, resourceType, resourceId, action);
    }
}
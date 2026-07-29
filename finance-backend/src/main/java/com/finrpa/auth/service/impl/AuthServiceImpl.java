package com.finrpa.auth.service.impl;

import com.finrpa.auth.dto.response.LoginResponse;
import com.finrpa.auth.dto.response.UserInfoResponse;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.service.AuthService;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.auth.util.JwtUtil;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthServiceImpl implements AuthService {

    /** 用户 Mapper */
    private final UserMapper userMapper;
    /** JWT 工具 */
    private final JwtUtil jwtUtil;
    /** 权限服务 */
    private final PermissionService permissionService;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录响应（含 token 和用户信息）
     */
    @Override
    public LoginResponse login(String username, String password) {
        // 1. 查询用户
        UserEO user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误");
        }

        // 2. 校验状态
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户已禁用");
        }

        // 3. 校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误");
        }

        // 4. 生成 token（userId/orgId 为 Long，JWT claim 序列化为 String）
        String accessToken = jwtUtil.generateAccessToken(
                user.getUserId().toString(),
                user.getUsername(),
                user.getOrgId() == null ? null : user.getOrgId().toString(),
                user.getDeptName()
        );

        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId().toString(), user.getUsername());

        // 5. 构建响应
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
        userInfo.setRoles(permissionService.getUserRoles(user.getUserId().toString()));
        response.setUser(userInfo);

        return response;
    }

    /**
     * 刷新 token
     *
     * @param refreshToken 刷新令牌
     * @return 登录响应（含新的 token 和用户信息）
     */
    @Override
    public LoginResponse refresh(String refreshToken) {
        // 1. 校验 token
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "refreshToken无效或已过期");
        }

        // 2. 校验 token 类型
        String tokenType = jwtUtil.getTokenTypeFromToken(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "无效的refreshToken");
        }

        // 3. 查询用户（JWT 中的 userId 为 String，需转为 Long 查询）
        String userId = jwtUtil.getUserIdFromToken(refreshToken);
        UserEO user = userMapper.selectByUserId(Long.parseLong(userId));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        }

        // 4. 生成 token（userId/orgId 为 Long，JWT claim 序列化为 String）
        String accessToken = jwtUtil.generateAccessToken(
                user.getUserId().toString(),
                user.getUsername(),
                user.getOrgId() == null ? null : user.getOrgId().toString(),
                user.getDeptName()
        );

        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId().toString(), user.getUsername());

        // 5. 构建响应
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
        userInfo.setRoles(permissionService.getUserRoles(user.getUserId().toString()));
        response.setUser(userInfo);

        return response;
    }

    /**
     * 获取当前用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息响应
     */
    @Override
    public UserInfoResponse getCurrentUser(String userId) {
        // 1. 查询用户（JWT 中的 userId 为 String，需转为 Long 查询）
        UserEO user = userMapper.selectByUserId(Long.parseLong(userId));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        }

        // 2. 构建响应
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

    /**
     * 检查用户是否有权限访问指定资源，委托给 {@link PermissionService#checkPermission}
     *
     * @param userId       用户 ID
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param action       操作类型（read/view/create/update/delete/approve）
     * @return 是否有权限
     */
    @Override
    public boolean checkPermission(String userId, String resourceType, String resourceId, String action) {
        // 委托给权限服务校验
        return permissionService.checkPermission(userId, resourceType, resourceId, action);
    }

    /**
     * 用户登出
     *
     * @param userId 用户 ID
     */
    @Override
    public void logout(String userId) {
        // 当前版本为无状态 JWT 认证，服务端无需存储 session
        // 登出主要由前端清除 token 完成，此接口预留用于后续扩展（如黑名单机制）
        log.info("用户登出: {}", userId);
    }
}

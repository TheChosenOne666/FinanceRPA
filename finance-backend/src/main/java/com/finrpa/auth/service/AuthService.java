package com.finrpa.auth.service;

import com.finrpa.auth.dto.response.LoginResponse;
import com.finrpa.auth.dto.response.UserInfoResponse;

/**
 * 认证服务接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录响应（含 token 和用户信息）
     */
    LoginResponse login(String username, String password);

    /**
     * 刷新 token
     *
     * @param refreshToken 刷新令牌
     * @return 登录响应（含新的 token 和用户信息）
     */
    LoginResponse refresh(String refreshToken);

    /**
     * 获取当前用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息响应
     */
    UserInfoResponse getCurrentUser(String userId);

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
}

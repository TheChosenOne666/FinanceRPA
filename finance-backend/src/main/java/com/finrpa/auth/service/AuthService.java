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
     * @param username  用户名
     * @param password  密码
     * @param clientIp  客户端 IP（用于登录策略 IP 白/黑名单校验，null 时跳过 IP 校验）
     * @param userAgent 客户端 User-Agent（用于 SEC-3 会话信息记录）
     * @return 登录响应（含 token 和用户信息）
     */
    LoginResponse login(String username, String password, String clientIp, String userAgent);

    /**
     * 刷新 token
     *
     * @param refreshToken 刷新令牌
     * @param clientIp     客户端 IP（用于 SEC-3 会话信息记录）
     * @param userAgent    客户端 User-Agent（用于 SEC-3 会话信息记录）
     * @return 登录响应（含新的 token 和用户信息）
     */
    LoginResponse refresh(String refreshToken, String clientIp, String userAgent);

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

    /**
     * 用户登出（P2 SEC-3：拉黑当前 token + 移除会话集合）
     *
     * @param token JWT 访问令牌
     */
    void logout(String token);
}

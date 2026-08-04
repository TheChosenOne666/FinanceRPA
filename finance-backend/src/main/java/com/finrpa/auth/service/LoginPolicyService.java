package com.finrpa.auth.service;

import com.finrpa.auth.dto.request.LoginPolicyUpdateRequest;
import com.finrpa.auth.dto.response.LoginPolicyVO;

/**
 * 登录安全策略服务接口（P2 SEC-2）
 *
 * <p>提供登录策略配置的读取 / 更新，以及账号锁定校验、登录失败计数、IP 白/黑名单校验能力。
 * {@link com.finrpa.auth.service.impl.AuthServiceImpl} 在登录时调用
 * {@link #checkIpAllowed} 进行 IP 限制校验，调用 {@link #checkAccountLocked} 进行账号锁定校验，
 * 登录失败时调用 {@link #recordLoginFailure}，登录成功时调用 {@link #resetLoginFailure}。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface LoginPolicyService {

    /**
     * 获取当前启用的登录策略（设置页展示用）
     *
     * <p>策略被禁用时返回 null，调用方应跳过校验。</p>
     *
     * @return 登录策略 VO，禁用时返回 null
     */
    LoginPolicyVO getActivePolicy();

    /**
     * 更新登录策略配置
     *
     * @param request 更新请求
     * @return 更新后的策略 VO
     */
    LoginPolicyVO updatePolicy(LoginPolicyUpdateRequest request);

    /**
     * 校验客户端 IP 是否允许登录
     *
     * <p>策略被禁用时直接通过；IP 命中黑名单抛 FORBIDDEN_ERROR；
     * 配置了白名单时，IP 不在白名单也抛 FORBIDDEN_ERROR。</p>
     *
     * @param clientIp 客户端 IP
     * @throws com.finrpa.common.exception.BusinessException IP 不允许时抛出
     */
    void checkIpAllowed(String clientIp);

    /**
     * 检查账号是否被锁定（连续登录失败次数超限）
     *
     * <p>策略被禁用时返回 false；账号未锁定返回 false；
     * 账号已锁定且未到解锁时间抛 ACCOUNT_LOCKED。</p>
     *
     * @param username 用户名
     * @throws com.finrpa.common.exception.BusinessException 账号被锁定时抛出
     */
    void checkAccountLocked(String username);

    /**
     * 记录登录失败次数 + 1，达到阈值时写入锁定到期时间
     *
     * <p>策略被禁用时直接返回。</p>
     *
     * @param username 用户名
     */
    void recordLoginFailure(String username);

    /**
     * 重置登录失败次数（登录成功时调用）
     *
     * <p>策略被禁用时直接返回。</p>
     *
     * @param username 用户名
     */
    void resetLoginFailure(String username);
}

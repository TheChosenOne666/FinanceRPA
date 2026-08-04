package com.finrpa.auth.service;

import com.finrpa.auth.dto.request.PasswordPolicyUpdateRequest;
import com.finrpa.auth.dto.response.PasswordPolicyVO;
import com.finrpa.auth.entity.UserEO;

/**
 * 密码策略服务接口（P2 SEC-1）
 *
 * <p>提供密码策略配置的读取 / 更新，以及密码强度校验、历史密码校验、密码过期校验能力。
 * {@link com.finrpa.auth.service.impl.UserServiceImpl} 在新增用户 / 重置密码时调用
 * {@link #validatePassword} 和 {@link #validatePasswordHistory} 进行校验；
 * {@link com.finrpa.auth.service.impl.AuthServiceImpl} 在登录时调用
 * {@link #isPasswordExpired} 进行密码过期校验。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface PasswordPolicyService {

    /**
     * 获取当前启用的密码策略（设置页展示用）
     *
     * <p>策略被禁用时返回 null，调用方应走默认值。</p>
     *
     * @return 密码策略 VO，禁用时返回 null
     */
    PasswordPolicyVO getActivePolicy();

    /**
     * 校验密码强度（长度 + 大小写 + 数字 + 特殊字符）
     *
     * <p>策略被禁用时不校验，直接通过。</p>
     *
     * @param rawPassword 明文密码
     * @throws com.finrpa.common.exception.BusinessException 密码强度不足时抛出
     */
    void validatePassword(String rawPassword);

    /**
     * 校验新密码是否与历史密码重复
     *
     * <p>策略被禁用或 historyCount=0 时不校验，直接通过。</p>
     *
     * @param userId      用户业务 ID
     * @param rawPassword 明文新密码
     * @throws com.finrpa.common.exception.BusinessException 与历史密码重复时抛出
     */
    void validatePasswordHistory(Long userId, String rawPassword);

    /**
     * 记录密码历史（在密码设置 / 修改后调用）
     *
     * <p>策略被禁用或 historyCount=0 时不记录。
     * 超过 historyCount 条时自动清理最旧记录。</p>
     *
     * @param userId      用户业务 ID
     * @param rawPassword 明文密码
     */
    void recordPasswordHistory(Long userId, String rawPassword);

    /**
     * 检查用户密码是否已过期
     *
     * <p>策略被禁用或 expireDays=0 时不检查，返回 false。</p>
     *
     * @param user 用户实体（需含 pwdChangedAt 字段）
     * @return true-已过期 false-未过期或不过期
     */
    boolean isPasswordExpired(UserEO user);

    /**
     * 更新密码策略配置
     *
     * @param request 更新请求
     * @return 更新后的策略 VO
     */
    PasswordPolicyVO updatePolicy(PasswordPolicyUpdateRequest request);
}

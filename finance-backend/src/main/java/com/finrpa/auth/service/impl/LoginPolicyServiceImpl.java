package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.finrpa.auth.dto.request.LoginPolicyUpdateRequest;
import com.finrpa.auth.dto.response.LoginPolicyVO;
import com.finrpa.auth.entity.LoginPolicyEO;
import com.finrpa.auth.mapper.LoginPolicyMapper;
import com.finrpa.auth.service.LoginPolicyService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录安全策略服务实现（P2 SEC-2）
 *
 * <p>使用 Redisson RAtomicLong 记录连续登录失败次数，RBucket 记录账号锁定到期时间戳。
 * 策略被禁用时所有校验自动跳过。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class LoginPolicyServiceImpl implements LoginPolicyService {

    /** 登录失败计数 Redis Key 前缀 */
    private static final String FAIL_COUNT_KEY_PREFIX = "login:fail:";

    /** 账号锁定到期时间 Redis Key 前缀 */
    private static final String LOCK_UNTIL_KEY_PREFIX = "login:lock:";

    /** 登录策略配置 Mapper */
    @Resource
    private LoginPolicyMapper loginPolicyMapper;

    /** Redisson 客户端（用于登录失败计数 / 锁定到期时间存储） */
    @Resource
    private RedissonClient redissonClient;

    // region 查询 / 更新

    /**
     * 获取当前启用的登录策略
     *
     * @return 登录策略 VO，禁用时返回 null
     */
    @Override
    public LoginPolicyVO getActivePolicy() {
        // 1. 查询单行配置（id=1）
        LoginPolicyEO eo = loginPolicyMapper.selectById(1L);
        if (eo == null || eo.getEnabled() == null || eo.getEnabled() != 1) {
            return null;
        }

        // 2. 转换为 VO
        LoginPolicyVO vo = new LoginPolicyVO();
        BeanUtils.copyProperties(eo, vo);
        return vo;
    }

    /**
     * 更新登录策略配置
     *
     * @param request 更新请求
     * @return 更新后的策略 VO
     */
    @Override
    public LoginPolicyVO updatePolicy(LoginPolicyUpdateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "更新请求不能为空");

        // 1. 参数校验
        if (request.getMaxLoginAttempts() != null) {
            ThrowUtils.throwIf(request.getMaxLoginAttempts() < 1 || request.getMaxLoginAttempts() > 20,
                    ErrorCode.PARAMS_ERROR, "最大登录失败次数应在 1-20 之间");
        }
        if (request.getLockMinutes() != null) {
            ThrowUtils.throwIf(request.getLockMinutes() < 1 || request.getLockMinutes() > 1440,
                    ErrorCode.PARAMS_ERROR, "账号锁定时长应在 1-1440 分钟之间");
        }
        if (request.getSessionTimeoutMinutes() != null) {
            ThrowUtils.throwIf(
                    request.getSessionTimeoutMinutes() < 1 || request.getSessionTimeoutMinutes() > 1440,
                    ErrorCode.PARAMS_ERROR, "会话空闲超时应在 1-1440 分钟之间");
        }
        if (request.getAllowMultiLogin() != null) {
            ThrowUtils.throwIf(request.getAllowMultiLogin() != 0 && request.getAllowMultiLogin() != 1,
                    ErrorCode.PARAMS_ERROR, "allowMultiLogin 仅支持 0 或 1");
        }
        if (request.getEnabled() != null) {
            ThrowUtils.throwIf(request.getEnabled() != 0 && request.getEnabled() != 1,
                    ErrorCode.PARAMS_ERROR, "enabled 仅支持 0 或 1");
        }

        // 2. 查询原记录
        LoginPolicyEO existing = loginPolicyMapper.selectById(1L);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "登录策略配置不存在");

        // 3. 构建更新字段
        UpdateWrapper<LoginPolicyEO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", 1L);
        if (request.getMaxLoginAttempts() != null) {
            updateWrapper.set("max_login_attempts", request.getMaxLoginAttempts());
        }
        if (request.getLockMinutes() != null) {
            updateWrapper.set("lock_minutes", request.getLockMinutes());
        }
        if (request.getIpWhitelist() != null) {
            updateWrapper.set("ip_whitelist", request.getIpWhitelist());
        }
        if (request.getIpBlacklist() != null) {
            updateWrapper.set("ip_blacklist", request.getIpBlacklist());
        }
        if (request.getAllowMultiLogin() != null) {
            updateWrapper.set("allow_multi_login", request.getAllowMultiLogin());
        }
        if (request.getSessionTimeoutMinutes() != null) {
            updateWrapper.set("session_timeout_minutes", request.getSessionTimeoutMinutes());
        }
        if (request.getEnabled() != null) {
            updateWrapper.set("enabled", request.getEnabled());
        }

        // 4. 执行更新
        int rows = loginPolicyMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "登录策略更新失败");

        // 5. 重新查询返回
        LoginPolicyEO updated = loginPolicyMapper.selectById(1L);
        LoginPolicyVO vo = new LoginPolicyVO();
        BeanUtils.copyProperties(updated, vo);

        log.info("更新登录策略: maxAttempts={}, lockMinutes={}, allowMultiLogin={}, sessionTimeout={}, enabled={}",
                updated.getMaxLoginAttempts(), updated.getLockMinutes(),
                updated.getAllowMultiLogin(), updated.getSessionTimeoutMinutes(), updated.getEnabled());
        return vo;
    }

    // endregion

    // region IP 校验

    /**
     * 校验客户端 IP 是否允许登录
     *
     * @param clientIp 客户端 IP
     */
    @Override
    public void checkIpAllowed(String clientIp) {
        // 1. 获取策略，禁用时直接通过
        LoginPolicyEO policy = loginPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }

        // 2. 解析 IP 列表（逗号分隔，去空白，去空串）
        Set<String> blacklist = parseIpList(policy.getIpBlacklist());
        Set<String> whitelist = parseIpList(policy.getIpWhitelist());

        // 3. 黑名单优先：命中即拒绝
        if (!blacklist.isEmpty() && blacklist.contains(clientIp)) {
            log.warn("IP 命中黑名单拒绝登录: ip={}", clientIp);
            throw new BusinessException(ErrorCode.IP_FORBIDDEN,
                    "当前 IP 不允许登录，请联系管理员");
        }

        // 4. 白名单：配置了白名单时，IP 必须在白名单内
        if (!whitelist.isEmpty() && !whitelist.contains(clientIp)) {
            log.warn("IP 不在白名单拒绝登录: ip={}", clientIp);
            throw new BusinessException(ErrorCode.IP_FORBIDDEN,
                    "当前 IP 不允许登录，请联系管理员");
        }
    }

    /**
     * 解析逗号分隔的 IP 列表为 Set
     *
     * @param raw 原始字符串（如 "192.168.1.1,10.0.0.0/24"）
     * @return 去重去空白后的 IP 集合，空字符串返回空集合
     */
    private Set<String> parseIpList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new HashSet<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    // endregion

    // region 账号锁定

    /**
     * 检查账号是否被锁定
     *
     * @param username 用户名
     */
    @Override
    public void checkAccountLocked(String username) {
        ThrowUtils.throwIf(!StringUtils.hasText(username),
                ErrorCode.PARAMS_ERROR, "用户名不能为空");

        // 1. 获取策略，禁用时直接通过
        LoginPolicyEO policy = loginPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }

        // 2. 读取锁定到期时间
        RBucket<Long> lockUntilBucket = redissonClient.getBucket(LOCK_UNTIL_KEY_PREFIX + username);
        Long lockUntil = lockUntilBucket.get();
        if (lockUntil == null) {
            return;
        }

        // 3. 已到期则自动解锁（清除锁定 + 失败计数）
        long now = Instant.now().toEpochMilli();
        if (lockUntil <= now) {
            lockUntilBucket.delete();
            redissonClient.getAtomicLong(FAIL_COUNT_KEY_PREFIX + username).delete();
            log.info("账号锁定已到期自动解锁: username={}", username);
            return;
        }

        // 4. 仍在锁定窗口内，抛异常
        long remainingMinutes = (long) Math.ceil((lockUntil - now) / 60000.0);
        throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                "账号已被锁定，请 " + remainingMinutes + " 分钟后再试");
    }

    /**
     * 记录登录失败次数 + 1，达到阈值时写入锁定到期时间
     *
     * @param username 用户名
     */
    @Override
    public void recordLoginFailure(String username) {
        ThrowUtils.throwIf(!StringUtils.hasText(username),
                ErrorCode.PARAMS_ERROR, "用户名不能为空");

        // 1. 获取策略，禁用时不计数
        LoginPolicyEO policy = loginPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }

        Integer maxAttempts = policy.getMaxLoginAttempts();
        Integer lockMinutes = policy.getLockMinutes();
        if (maxAttempts == null || maxAttempts <= 0 || lockMinutes == null || lockMinutes <= 0) {
            return;
        }

        // 2. 失败次数 +1
        RAtomicLong counter = redissonClient.getAtomicLong(FAIL_COUNT_KEY_PREFIX + username);
        long current = counter.incrementAndGet();

        // 3. 计数 key TTL 设为 lockMinutes，超时后自动清零
        counter.expire(Duration.of(lockMinutes, ChronoUnit.MINUTES));

        // 4. 未达阈值，仅记录
        if (current < maxAttempts) {
            log.info("登录失败计数: username={}, count={}, threshold={}",
                    username, current, maxAttempts);
            return;
        }

        // 5. 达到阈值，写入锁定到期时间（同时刷新 TTL）
        long lockUntil = Instant.now().plus(lockMinutes, ChronoUnit.MINUTES).toEpochMilli();
        RBucket<Long> lockUntilBucket = redissonClient.getBucket(LOCK_UNTIL_KEY_PREFIX + username);
        lockUntilBucket.set(lockUntil, Duration.of(lockMinutes, ChronoUnit.MINUTES));

        log.warn("账号触发登录失败锁定: username={}, count={}, lockMinutes={}",
                username, current, lockMinutes);
    }

    /**
     * 重置登录失败次数（登录成功时调用）
     *
     * @param username 用户名
     */
    @Override
    public void resetLoginFailure(String username) {
        ThrowUtils.throwIf(!StringUtils.hasText(username),
                ErrorCode.PARAMS_ERROR, "用户名不能为空");

        // 1. 获取策略，禁用时直接返回
        LoginPolicyEO policy = loginPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }

        // 2. 清除失败计数 + 锁定状态
        redissonClient.getAtomicLong(FAIL_COUNT_KEY_PREFIX + username).delete();
        redissonClient.getBucket(LOCK_UNTIL_KEY_PREFIX + username).delete();
        log.debug("登录成功重置失败计数: username={}", username);
    }

    // endregion

    // region 单测辅助（包级可见，便于测试断言）

    /**
     * 获取当前失败计数（仅用于单元测试断言）
     *
     * @param username 用户名
     * @return 当前失败次数，无记录返回 0
     */
    long getCurrentFailCount(String username) {
        RAtomicLong counter = redissonClient.getAtomicLong(FAIL_COUNT_KEY_PREFIX + username);
        return counter.get();
    }

    /**
     * 获取当前锁定到期时间（仅用于单元测试断言）
     *
     * @param username 用户名
     * @return 锁定到期 epoch 毫秒，无记录返回 null
     */
    Long getLockUntil(String username) {
        RBucket<Long> bucket = redissonClient.getBucket(LOCK_UNTIL_KEY_PREFIX + username);
        return bucket.get();
    }

    // endregion
}

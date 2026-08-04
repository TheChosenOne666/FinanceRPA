package com.finrpa.auth.service.impl;

import com.finrpa.auth.dto.request.LoginPolicyUpdateRequest;
import com.finrpa.auth.dto.response.LoginPolicyVO;
import com.finrpa.auth.entity.LoginPolicyEO;
import com.finrpa.auth.mapper.LoginPolicyMapper;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 登录安全策略服务实现单元测试（P2 SEC-2）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class LoginPolicyServiceImplTest {

    @Mock
    private LoginPolicyMapper loginPolicyMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RAtomicLong rAtomicLong;

    @Mock
    private RBucket<Long> rBucket;

    @InjectMocks
    private LoginPolicyServiceImpl loginPolicyService;

    /** 构造启用的登录策略（5 次失败 / 锁 30 分钟） */
    private LoginPolicyEO buildEnabledPolicy() {
        LoginPolicyEO eo = new LoginPolicyEO();
        eo.setId(1L);
        eo.setPolicyId(1751000000000000011L);
        eo.setMaxLoginAttempts(5);
        eo.setLockMinutes(30);
        eo.setIpWhitelist(null);
        eo.setIpBlacklist(null);
        eo.setAllowMultiLogin(0);
        eo.setSessionTimeoutMinutes(30);
        eo.setEnabled(1);
        return eo;
    }

    /** 构造禁用的登录策略 */
    private LoginPolicyEO buildDisabledPolicy() {
        LoginPolicyEO eo = buildEnabledPolicy();
        eo.setEnabled(0);
        return eo;
    }

    @BeforeEach
    void setUp() {
        // 默认 stubbing：Redisson 返回 mock 对象
        lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(rAtomicLong);
        lenient().when(redissonClient.<Long>getBucket(anyString())).thenReturn(rBucket);
    }

    // region getActivePolicy

    @Test
    @DisplayName("getActivePolicy - 策略启用时返回 VO")
    void getActivePolicy_Enabled_ReturnsVO() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());

        LoginPolicyVO result = loginPolicyService.getActivePolicy();

        assertThat(result).isNotNull();
        assertThat(result.getMaxLoginAttempts()).isEqualTo(5);
        assertThat(result.getLockMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("getActivePolicy - 策略禁用时返回 null")
    void getActivePolicy_Disabled_ReturnsNull() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildDisabledPolicy());

        LoginPolicyVO result = loginPolicyService.getActivePolicy();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getActivePolicy - 配置不存在时返回 null")
    void getActivePolicy_NotFound_ReturnsNull() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(null);

        LoginPolicyVO result = loginPolicyService.getActivePolicy();

        assertThat(result).isNull();
    }

    // endregion

    // region checkIpAllowed

    @Test
    @DisplayName("checkIpAllowed - 策略禁用时直接通过")
    void checkIpAllowed_DisabledPolicy_Passes() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildDisabledPolicy());

        assertDoesNotThrow(() -> loginPolicyService.checkIpAllowed("1.2.3.4"));
    }

    @Test
    @DisplayName("checkIpAllowed - 黑名单命中抛 IP_FORBIDDEN")
    void checkIpAllowed_InBlacklist_ThrowsException() {
        LoginPolicyEO eo = buildEnabledPolicy();
        eo.setIpBlacklist("1.2.3.4,10.0.0.1");
        when(loginPolicyMapper.selectById(1L)).thenReturn(eo);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> loginPolicyService.checkIpAllowed("1.2.3.4"));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.IP_FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("checkIpAllowed - 配置白名单时不在白名单抛 IP_FORBIDDEN")
    void checkIpAllowed_NotInWhitelist_ThrowsException() {
        LoginPolicyEO eo = buildEnabledPolicy();
        eo.setIpWhitelist("192.168.1.1");
        when(loginPolicyMapper.selectById(1L)).thenReturn(eo);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> loginPolicyService.checkIpAllowed("1.2.3.4"));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.IP_FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("checkIpAllowed - 在白名单内通过")
    void checkIpAllowed_InWhitelist_Passes() {
        LoginPolicyEO eo = buildEnabledPolicy();
        eo.setIpWhitelist("192.168.1.1,10.0.0.1");
        when(loginPolicyMapper.selectById(1L)).thenReturn(eo);

        assertDoesNotThrow(() -> loginPolicyService.checkIpAllowed("10.0.0.1"));
    }

    @Test
    @DisplayName("checkIpAllowed - 未配置白/黑名单时通过")
    void checkIpAllowed_NoList_Passes() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());

        assertDoesNotThrow(() -> loginPolicyService.checkIpAllowed("1.2.3.4"));
    }

    // endregion

    // region checkAccountLocked

    @Test
    @DisplayName("checkAccountLocked - 策略禁用时直接通过")
    void checkAccountLocked_DisabledPolicy_Passes() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildDisabledPolicy());

        assertDoesNotThrow(() -> loginPolicyService.checkAccountLocked("alice"));
    }

    @Test
    @DisplayName("checkAccountLocked - 无锁定记录通过")
    void checkAccountLocked_NoLockRecord_Passes() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());
        when(rBucket.get()).thenReturn(null);

        assertDoesNotThrow(() -> loginPolicyService.checkAccountLocked("alice"));
    }

    @Test
    @DisplayName("checkAccountLocked - 锁定已过期自动解锁并通过")
    void checkAccountLocked_ExpiredLock_AutoUnlocks() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());
        // 锁定到期时间为 1 分钟前
        long expiredLock = Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli();
        when(rBucket.get()).thenReturn(expiredLock);

        assertDoesNotThrow(() -> loginPolicyService.checkAccountLocked("alice"));

        // 验证清理调用
        verify(rBucket).delete();
        verify(rAtomicLong).delete();
    }

    @Test
    @DisplayName("checkAccountLocked - 锁定未到期抛 ACCOUNT_LOCKED")
    void checkAccountLocked_StillLocked_ThrowsException() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());
        // 锁定到期时间为 10 分钟后
        long futureLock = Instant.now().plus(10, ChronoUnit.MINUTES).toEpochMilli();
        when(rBucket.get()).thenReturn(futureLock);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> loginPolicyService.checkAccountLocked("alice"));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());
    }

    // endregion

    // region recordLoginFailure

    @Test
    @DisplayName("recordLoginFailure - 策略禁用时不计数")
    void recordLoginFailure_DisabledPolicy_SkipsCount() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildDisabledPolicy());

        loginPolicyService.recordLoginFailure("alice");

        verifyNoInteractions(rAtomicLong);
    }

    @Test
    @DisplayName("recordLoginFailure - 未达阈值仅计数")
    void recordLoginFailure_BelowThreshold_OnlyCounts() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());
        when(rAtomicLong.incrementAndGet()).thenReturn(2L);

        loginPolicyService.recordLoginFailure("alice");

        verify(rAtomicLong).incrementAndGet();
        verify(rAtomicLong).expire(any(Duration.class));
        verify(rBucket, never()).set(anyLong(), any(Duration.class));
    }

    @Test
    @DisplayName("recordLoginFailure - 达到阈值写入锁定到期时间")
    void recordLoginFailure_AtThreshold_WritesLockUntil() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());
        when(rAtomicLong.incrementAndGet()).thenReturn(5L);

        loginPolicyService.recordLoginFailure("alice");

        verify(rAtomicLong).incrementAndGet();
        verify(rBucket).set(anyLong(), any(Duration.class));
    }

    // endregion

    // region resetLoginFailure

    @Test
    @DisplayName("resetLoginFailure - 策略禁用时不操作")
    void resetLoginFailure_DisabledPolicy_SkipsReset() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildDisabledPolicy());

        loginPolicyService.resetLoginFailure("alice");

        verify(rAtomicLong, never()).delete();
        verify(rBucket, never()).delete();
    }

    @Test
    @DisplayName("resetLoginFailure - 启用时清除计数 + 锁定")
    void resetLoginFailure_Enabled_DeletesBoth() {
        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());

        loginPolicyService.resetLoginFailure("alice");

        verify(rAtomicLong).delete();
        verify(rBucket).delete();
    }

    // endregion

    // region updatePolicy

    @Test
    @DisplayName("updatePolicy - 参数校验失败抛异常")
    void updatePolicy_InvalidParams_ThrowsException() {
        LoginPolicyUpdateRequest req = new LoginPolicyUpdateRequest();
        req.setMaxLoginAttempts(100); // 超出 1-20 范围

        // 参数校验在查询前抛异常，无需 stub mapper
        assertThrows(BusinessException.class, () -> loginPolicyService.updatePolicy(req));
    }

    @Test
    @DisplayName("updatePolicy - 配置不存在抛 NOT_FOUND_ERROR")
    void updatePolicy_NotFound_ThrowsException() {
        LoginPolicyUpdateRequest req = new LoginPolicyUpdateRequest();
        req.setMaxLoginAttempts(3);
        when(loginPolicyMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> loginPolicyService.updatePolicy(req));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND_ERROR.getCode());
    }

    @Test
    @DisplayName("updatePolicy - 正常更新返回 VO")
    void updatePolicy_Valid_ReturnsVO() {
        LoginPolicyUpdateRequest req = new LoginPolicyUpdateRequest();
        req.setMaxLoginAttempts(3);
        req.setLockMinutes(15);
        req.setEnabled(1);

        when(loginPolicyMapper.selectById(1L)).thenReturn(buildEnabledPolicy());
        when(loginPolicyMapper.update(any(), any())).thenReturn(1);

        LoginPolicyVO result = loginPolicyService.updatePolicy(req);

        assertThat(result).isNotNull();
        verify(loginPolicyMapper).update(any(), any());
    }

    // endregion
}

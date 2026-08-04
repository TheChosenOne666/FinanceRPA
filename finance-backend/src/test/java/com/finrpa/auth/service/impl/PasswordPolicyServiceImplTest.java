package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.finrpa.auth.dto.request.PasswordPolicyUpdateRequest;
import com.finrpa.auth.dto.response.PasswordPolicyVO;
import com.finrpa.auth.entity.PasswordHistoryEO;
import com.finrpa.auth.entity.PasswordPolicyEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.PasswordHistoryMapper;
import com.finrpa.auth.mapper.PasswordPolicyMapper;
import com.finrpa.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 密码策略服务实现单元测试（P2 SEC-1）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceImplTest {

    @Mock
    private PasswordPolicyMapper passwordPolicyMapper;

    @Mock
    private PasswordHistoryMapper passwordHistoryMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordPolicyServiceImpl passwordPolicyService;

    // region getActivePolicy

    @Test
    @DisplayName("getActivePolicy - 策略启用时返回 VO")
    void getActivePolicy_Enabled_ReturnsVO() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        PasswordPolicyVO result = passwordPolicyService.getActivePolicy();

        assertThat(result).isNotNull();
        assertThat(result.getMinLength()).isEqualTo(8);
        assertThat(result.getExpireDays()).isEqualTo(90);
        assertThat(result.getHistoryCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getActivePolicy - 策略禁用时返回 null")
    void getActivePolicy_Disabled_ReturnsNull() {
        PasswordPolicyEO eo = buildPolicy(0, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        PasswordPolicyVO result = passwordPolicyService.getActivePolicy();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getActivePolicy - 配置不存在时返回 null")
    void getActivePolicy_NotFound_ReturnsNull() {
        when(passwordPolicyMapper.selectById(1L)).thenReturn(null);

        PasswordPolicyVO result = passwordPolicyService.getActivePolicy();

        assertThat(result).isNull();
    }

    // endregion

    // region validatePassword

    @Test
    @DisplayName("validatePassword - 密码符合要求时通过")
    void validatePassword_ValidPassword_Passes() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        assertDoesNotThrow(() -> passwordPolicyService.validatePassword("Abc1234!"));
    }

    @Test
    @DisplayName("validatePassword - 密码太短抛出异常")
    void validatePassword_TooShort_ThrowsException() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        assertThrows(BusinessException.class,
                () -> passwordPolicyService.validatePassword("Ab1!"));
    }

    @Test
    @DisplayName("validatePassword - 缺少大写字母抛出异常")
    void validatePassword_NoUppercase_ThrowsException() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        assertThrows(BusinessException.class,
                () -> passwordPolicyService.validatePassword("abc1234!"));
    }

    @Test
    @DisplayName("validatePassword - 缺少特殊字符抛出异常")
    void validatePassword_NoSpecial_ThrowsException() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        assertThrows(BusinessException.class,
                () -> passwordPolicyService.validatePassword("Abc12345"));
    }

    @Test
    @DisplayName("validatePassword - 策略禁用时不校验")
    void validatePassword_DisabledPolicy_SkipsValidation() {
        PasswordPolicyEO eo = buildPolicy(0, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        assertDoesNotThrow(() -> passwordPolicyService.validatePassword("weak"));
    }

    @Test
    @DisplayName("validatePassword - 空密码抛出异常")
    void validatePassword_EmptyPassword_ThrowsException() {
        assertThrows(BusinessException.class,
                () -> passwordPolicyService.validatePassword(""));
    }

    // endregion

    // region validatePasswordHistory

    @Test
    @DisplayName("validatePasswordHistory - 策略禁用时不校验")
    void validatePasswordHistory_DisabledPolicy_SkipsCheck() {
        PasswordPolicyEO eo = buildPolicy(0, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        assertDoesNotThrow(() ->
                passwordPolicyService.validatePasswordHistory(100001L, "newPassword"));
    }

    @Test
    @DisplayName("validatePasswordHistory - historyCount=0 时不校验")
    void validatePasswordHistory_ZeroHistoryCount_SkipsCheck() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 0);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        assertDoesNotThrow(() ->
                passwordPolicyService.validatePasswordHistory(100001L, "newPassword"));
    }

    @Test
    @DisplayName("validatePasswordHistory - 与历史密码重复抛出异常")
    void validatePasswordHistory_DuplicatePassword_ThrowsException() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 3);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        PasswordHistoryEO history = new PasswordHistoryEO();
        history.setPasswordHash("hashedOldPassword");
        when(passwordHistoryMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(history));
        when(passwordEncoder.matches("oldPassword", "hashedOldPassword")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> passwordPolicyService.validatePasswordHistory(100001L, "oldPassword"));
    }

    @Test
    @DisplayName("validatePasswordHistory - 不重复时通过")
    void validatePasswordHistory_NoDuplicate_Passes() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 3);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        PasswordHistoryEO history = new PasswordHistoryEO();
        history.setPasswordHash("hashedOldPassword");
        when(passwordHistoryMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(history));
        when(passwordEncoder.matches("newPassword", "hashedOldPassword")).thenReturn(false);

        assertDoesNotThrow(() ->
                passwordPolicyService.validatePasswordHistory(100001L, "newPassword"));
    }

    // endregion

    // region recordPasswordHistory

    @Test
    @DisplayName("recordPasswordHistory - 策略禁用时不记录")
    void recordPasswordHistory_DisabledPolicy_SkipsRecord() {
        PasswordPolicyEO eo = buildPolicy(0, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);

        passwordPolicyService.recordPasswordHistory(100001L, "password");

        verify(passwordHistoryMapper, never()).insert(any(PasswordHistoryEO.class));
    }

    @Test
    @DisplayName("recordPasswordHistory - 正常记录")
    void recordPasswordHistory_NormalRecord() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        when(passwordEncoder.encode("password")).thenReturn("hashedPassword");
        when(passwordHistoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);

        passwordPolicyService.recordPasswordHistory(100001L, "password");

        verify(passwordHistoryMapper).insert(any(PasswordHistoryEO.class));
    }

    @Test
    @DisplayName("recordPasswordHistory - 超量时清理最旧记录")
    void recordPasswordHistory_ExceedsLimit_CleansOldRecords() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 2);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        when(passwordEncoder.encode("password")).thenReturn("hashedPassword");
        when(passwordHistoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(4L);

        PasswordHistoryEO old1 = new PasswordHistoryEO();
        old1.setId(10L);
        PasswordHistoryEO old2 = new PasswordHistoryEO();
        old2.setId(11L);
        when(passwordHistoryMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(old1, old2));

        passwordPolicyService.recordPasswordHistory(100001L, "password");

        verify(passwordHistoryMapper).insert(any(PasswordHistoryEO.class));
        verify(passwordHistoryMapper).deleteById(10L);
        verify(passwordHistoryMapper).deleteById(11L);
    }

    // endregion

    // region isPasswordExpired

    @Test
    @DisplayName("isPasswordExpired - 策略禁用时返回 false")
    void isPasswordExpired_DisabledPolicy_ReturnsFalse() {
        PasswordPolicyEO eo = buildPolicy(0, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        UserEO user = new UserEO();
        user.setPwdChangedAt(Timestamp.from(Instant.now().minus(100, ChronoUnit.DAYS)));

        boolean result = passwordPolicyService.isPasswordExpired(user);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isPasswordExpired - expireDays=0 时返回 false")
    void isPasswordExpired_ZeroExpireDays_ReturnsFalse() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 0, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        UserEO user = new UserEO();
        user.setPwdChangedAt(Timestamp.from(Instant.now().minus(365, ChronoUnit.DAYS)));

        boolean result = passwordPolicyService.isPasswordExpired(user);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isPasswordExpired - 密码未过期返回 false")
    void isPasswordExpired_NotExpired_ReturnsFalse() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 90, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        UserEO user = new UserEO();
        user.setPwdChangedAt(Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)));

        boolean result = passwordPolicyService.isPasswordExpired(user);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isPasswordExpired - 密码已过期返回 true")
    void isPasswordExpired_Expired_ReturnsTrue() {
        PasswordPolicyEO eo = buildPolicy(1, 8, 30, 5);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(eo);
        UserEO user = new UserEO();
        user.setPwdChangedAt(Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)));

        boolean result = passwordPolicyService.isPasswordExpired(user);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isPasswordExpired - pwdChangedAt 为 null 返回 false")
    void isPasswordExpired_NullPwdChangedAt_ReturnsFalse() {
        UserEO user = new UserEO();
        user.setPwdChangedAt(null);

        boolean result = passwordPolicyService.isPasswordExpired(user);

        assertThat(result).isFalse();
    }

    // endregion

    // region updatePolicy

    @Test
    @DisplayName("updatePolicy - 成功更新策略")
    void updatePolicy_Success() {
        PasswordPolicyEO existing = buildPolicy(1, 8, 90, 5);
        PasswordPolicyEO updated = buildPolicy(1, 12, 60, 10);
        when(passwordPolicyMapper.selectById(1L)).thenReturn(existing).thenReturn(updated);
        when(passwordPolicyMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        PasswordPolicyUpdateRequest request = new PasswordPolicyUpdateRequest();
        request.setMinLength(12);
        request.setExpireDays(60);
        request.setHistoryCount(10);

        PasswordPolicyVO result = passwordPolicyService.updatePolicy(request);

        assertThat(result).isNotNull();
        assertThat(result.getMinLength()).isEqualTo(12);
        assertThat(result.getExpireDays()).isEqualTo(60);
        assertThat(result.getHistoryCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("updatePolicy - minLength 超范围抛出异常")
    void updatePolicy_MinLengthOutOfRange_ThrowsException() {
        PasswordPolicyUpdateRequest request = new PasswordPolicyUpdateRequest();
        request.setMinLength(3);

        assertThrows(BusinessException.class,
                () -> passwordPolicyService.updatePolicy(request));
    }

    @Test
    @DisplayName("updatePolicy - expireDays 超范围抛出异常")
    void updatePolicy_ExpireDaysOutOfRange_ThrowsException() {
        PasswordPolicyUpdateRequest request = new PasswordPolicyUpdateRequest();
        request.setExpireDays(400);

        assertThrows(BusinessException.class,
                () -> passwordPolicyService.updatePolicy(request));
    }

    @Test
    @DisplayName("updatePolicy - 请求为空抛出异常")
    void updatePolicy_NullRequest_ThrowsException() {
        assertThrows(BusinessException.class,
                () -> passwordPolicyService.updatePolicy(null));
    }

    @Test
    @DisplayName("updatePolicy - 配置不存在抛出异常")
    void updatePolicy_NotFound_ThrowsException() {
        when(passwordPolicyMapper.selectById(1L)).thenReturn(null);

        PasswordPolicyUpdateRequest request = new PasswordPolicyUpdateRequest();
        request.setMinLength(10);

        assertThrows(BusinessException.class,
                () -> passwordPolicyService.updatePolicy(request));
    }

    // endregion

    // region 测试辅助方法

    /**
     * 构建密码策略 EO
     *
     * @param enabled      启用状态
     * @param minLength    最小长度
     * @param expireDays   过期天数
     * @param historyCount 历史记录数
     * @return 策略实体
     */
    private PasswordPolicyEO buildPolicy(int enabled, int minLength, int expireDays, int historyCount) {
        PasswordPolicyEO eo = new PasswordPolicyEO();
        eo.setId(1L);
        eo.setPolicyId(1751000000000000010L);
        eo.setMinLength(minLength);
        eo.setRequireUppercase(1);
        eo.setRequireLowercase(1);
        eo.setRequireDigit(1);
        eo.setRequireSpecial(1);
        eo.setSpecialChars("!@#$%^&*()_+-=[]{}|;:,.<>?");
        eo.setExpireDays(expireDays);
        eo.setHistoryCount(historyCount);
        eo.setEnabled(enabled);
        return eo;
    }

    // endregion
}

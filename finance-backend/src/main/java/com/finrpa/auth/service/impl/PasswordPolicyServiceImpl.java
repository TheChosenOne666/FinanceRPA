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
import com.finrpa.auth.service.PasswordPolicyService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 密码策略服务实现（P2 SEC-1）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    /** 密码策略配置 Mapper */
    @Resource
    private PasswordPolicyMapper passwordPolicyMapper;

    /** 密码历史 Mapper */
    @Resource
    private PasswordHistoryMapper passwordHistoryMapper;

    /** 密码编码器（用于校验历史密码） */
    @Resource
    private PasswordEncoder passwordEncoder;

    // region 查询

    /**
     * 获取当前启用的密码策略
     *
     * @return 密码策略 VO，禁用时返回 null
     */
    @Override
    public PasswordPolicyVO getActivePolicy() {
        // 1. 查询单行配置（id=1）
        PasswordPolicyEO eo = passwordPolicyMapper.selectById(1L);
        if (eo == null || eo.getEnabled() == null || eo.getEnabled() != 1) {
            return null;
        }

        // 2. 转换为 VO
        PasswordPolicyVO vo = new PasswordPolicyVO();
        BeanUtils.copyProperties(eo, vo);
        return vo;
    }

    // endregion

    // region 密码校验

    /**
     * 校验密码强度
     *
     * @param rawPassword 明文密码
     */
    @Override
    public void validatePassword(String rawPassword) {
        ThrowUtils.throwIf(!StringUtils.hasText(rawPassword),
                ErrorCode.PARAMS_ERROR, "密码不能为空");

        // 1. 获取策略，禁用时不校验
        PasswordPolicyEO policy = passwordPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }

        // 2. 长度校验
        if (policy.getMinLength() != null && rawPassword.length() < policy.getMinLength()) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK,
                    "密码长度不足，至少需要 " + policy.getMinLength() + " 个字符");
        }

        // 3. 大写字母校验
        if (policy.getRequireUppercase() != null && policy.getRequireUppercase() == 1) {
            if (!rawPassword.matches(".*[A-Z].*")) {
                throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, "密码必须包含大写字母");
            }
        }

        // 4. 小写字母校验
        if (policy.getRequireLowercase() != null && policy.getRequireLowercase() == 1) {
            if (!rawPassword.matches(".*[a-z].*")) {
                throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, "密码必须包含小写字母");
            }
        }

        // 5. 数字校验
        if (policy.getRequireDigit() != null && policy.getRequireDigit() == 1) {
            if (!rawPassword.matches(".*[0-9].*")) {
                throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, "密码必须包含数字");
            }
        }

        // 6. 特殊字符校验
        if (policy.getRequireSpecial() != null && policy.getRequireSpecial() == 1) {
            String specialChars = policy.getSpecialChars() != null
                    ? policy.getSpecialChars() : "!@#$%^&*";
            boolean hasSpecial = false;
            for (char c : rawPassword.toCharArray()) {
                if (specialChars.indexOf(c) >= 0) {
                    hasSpecial = true;
                    break;
                }
            }
            if (!hasSpecial) {
                throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, "密码必须包含特殊字符");
            }
        }
    }

    /**
     * 校验新密码是否与历史密码重复
     *
     * @param userId      用户业务 ID
     * @param rawPassword 明文新密码
     */
    @Override
    public void validatePasswordHistory(Long userId, String rawPassword) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");

        // 1. 获取策略，禁用或 historyCount=0 时不校验
        PasswordPolicyEO policy = passwordPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }
        if (policy.getHistoryCount() == null || policy.getHistoryCount() <= 0) {
            return;
        }

        // 2. 查询最近 N 条历史密码
        QueryWrapper<PasswordHistoryEO> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .orderByDesc("create_time")
                .last("LIMIT " + policy.getHistoryCount());
        List<PasswordHistoryEO> histories = passwordHistoryMapper.selectList(wrapper);

        // 3. 逐条比对
        for (PasswordHistoryEO history : histories) {
            if (passwordEncoder.matches(rawPassword, history.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_HISTORY_VIOLATION,
                        "新密码不能与最近 " + policy.getHistoryCount() + " 次使用过的密码重复");
            }
        }
    }

    /**
     * 记录密码历史
     *
     * @param userId      用户业务 ID
     * @param rawPassword 明文密码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPasswordHistory(Long userId, String rawPassword) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");

        // 1. 获取策略，禁用或 historyCount=0 时不记录
        PasswordPolicyEO policy = passwordPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }
        if (policy.getHistoryCount() == null || policy.getHistoryCount() <= 0) {
            return;
        }

        // 2. 写入新历史记录
        PasswordHistoryEO history = new PasswordHistoryEO();
        history.setUserId(userId);
        history.setPasswordHash(passwordEncoder.encode(rawPassword));
        passwordHistoryMapper.insert(history);

        // 3. 清理超量历史记录（保留最近 historyCount 条）
        QueryWrapper<PasswordHistoryEO> countWrapper = new QueryWrapper<>();
        countWrapper.eq("user_id", userId);
        long total = passwordHistoryMapper.selectCount(countWrapper);
        if (total > policy.getHistoryCount()) {
            // 查询需删除的最旧记录 ID
            QueryWrapper<PasswordHistoryEO> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("user_id", userId)
                    .orderByDesc("create_time")
                    .last("OFFSET " + policy.getHistoryCount() + " ROWS");
            List<PasswordHistoryEO> toDelete = passwordHistoryMapper.selectList(deleteWrapper);
            for (PasswordHistoryEO eo : toDelete) {
                passwordHistoryMapper.deleteById(eo.getId());
            }
            log.debug("清理用户密码历史超量记录: userId={}, deleted={}", userId, toDelete.size());
        }
    }

    /**
     * 检查用户密码是否已过期
     *
     * @param user 用户实体
     * @return true-已过期 false-未过期或不过期
     */
    @Override
    public boolean isPasswordExpired(UserEO user) {
        if (user == null || user.getPwdChangedAt() == null) {
            return false;
        }

        // 1. 获取策略，禁用或 expireDays=0 时不检查
        PasswordPolicyEO policy = passwordPolicyMapper.selectById(1L);
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return false;
        }
        if (policy.getExpireDays() == null || policy.getExpireDays() <= 0) {
            return false;
        }

        // 2. 计算密码是否过期
        Instant expireAt = user.getPwdChangedAt().toInstant()
                .plus(policy.getExpireDays(), ChronoUnit.DAYS);
        return Instant.now().isAfter(expireAt);
    }

    // endregion

    // region 更新策略

    /**
     * 更新密码策略配置
     *
     * @param request 更新请求
     * @return 更新后的策略 VO
     */
    @Override
    public PasswordPolicyVO updatePolicy(PasswordPolicyUpdateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "更新请求不能为空");

        // 1. 参数校验
        if (request.getMinLength() != null) {
            ThrowUtils.throwIf(request.getMinLength() < 6 || request.getMinLength() > 128,
                    ErrorCode.PARAMS_ERROR, "密码最小长度应在 6-128 之间");
        }
        if (request.getExpireDays() != null) {
            ThrowUtils.throwIf(request.getExpireDays() < 0 || request.getExpireDays() > 365,
                    ErrorCode.PARAMS_ERROR, "密码过期天数应在 0-365 之间（0 表示不过期）");
        }
        if (request.getHistoryCount() != null) {
            ThrowUtils.throwIf(request.getHistoryCount() < 0 || request.getHistoryCount() > 20,
                    ErrorCode.PARAMS_ERROR, "密码历史记录数应在 0-20 之间（0 表示不检查）");
        }

        // 2. 查询原记录
        PasswordPolicyEO existing = passwordPolicyMapper.selectById(1L);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "密码策略配置不存在");

        // 3. 构建更新字段
        UpdateWrapper<PasswordPolicyEO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", 1L);
        if (request.getMinLength() != null) {
            updateWrapper.set("min_length", request.getMinLength());
        }
        if (request.getRequireUppercase() != null) {
            updateWrapper.set("require_uppercase", request.getRequireUppercase());
        }
        if (request.getRequireLowercase() != null) {
            updateWrapper.set("require_lowercase", request.getRequireLowercase());
        }
        if (request.getRequireDigit() != null) {
            updateWrapper.set("require_digit", request.getRequireDigit());
        }
        if (request.getRequireSpecial() != null) {
            updateWrapper.set("require_special", request.getRequireSpecial());
        }
        if (request.getSpecialChars() != null) {
            updateWrapper.set("special_chars", request.getSpecialChars());
        }
        if (request.getExpireDays() != null) {
            updateWrapper.set("expire_days", request.getExpireDays());
        }
        if (request.getHistoryCount() != null) {
            updateWrapper.set("history_count", request.getHistoryCount());
        }
        if (request.getEnabled() != null) {
            updateWrapper.set("enabled", request.getEnabled());
        }

        // 4. 执行更新
        int rows = passwordPolicyMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "密码策略更新失败");

        // 5. 重新查询返回
        PasswordPolicyEO updated = passwordPolicyMapper.selectById(1L);
        PasswordPolicyVO vo = new PasswordPolicyVO();
        BeanUtils.copyProperties(updated, vo);

        log.info("更新密码策略: minLength={}, expireDays={}, historyCount={}, enabled={}",
                updated.getMinLength(), updated.getExpireDays(),
                updated.getHistoryCount(), updated.getEnabled());
        return vo;
    }

    // endregion
}

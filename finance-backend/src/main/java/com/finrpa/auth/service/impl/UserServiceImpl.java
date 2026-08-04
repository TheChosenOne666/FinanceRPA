package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.auth.constant.AuthConstant;
import com.finrpa.auth.dto.request.PasswordResetRequest;
import com.finrpa.auth.dto.request.UserAddRequest;
import com.finrpa.auth.dto.request.UserQueryRequest;
import com.finrpa.auth.dto.request.UserRoleAssignRequest;
import com.finrpa.auth.dto.request.UserUpdateRequest;
import com.finrpa.auth.dto.response.UserVO;
import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.entity.UserRoleEO;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.mapper.UserRoleMapper;
import com.finrpa.auth.service.PasswordPolicyService;
import com.finrpa.auth.service.UserService;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现（P1 USR-1）
 *
 * <p>提供用户的 CRUD、启停、重置密码、分配角色（三维度 RBAC）能力。
 * sys_user / sys_role / sys_user_role 表均在 TenantConstant.IGNORED_TABLES 中，
 * 本服务在 Service 层手动按 {@code currentOrgId} 过滤。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /** 用户 Mapper */
    @Resource
    private UserMapper userMapper;

    /** 角色 Mapper（用于填充角色编码 + 分配角色时校验存在性） */
    @Resource
    private RoleMapper roleMapper;

    /** 用户-角色关联 Mapper */
    @Resource
    private UserRoleMapper userRoleMapper;

    /** 密码编码器（BCrypt） */
    @Resource
    private PasswordEncoder passwordEncoder;

    /** 密码策略服务（P2 SEC-1，密码强度校验 + 历史密码校验） */
    @Resource
    private PasswordPolicyService passwordPolicyService;

    // region 查询

    /**
     * 分页查询用户列表
     *
     * @param queryRequest   查询条件
     * @param currentOrgId   当前请求组织 ID
     * @param isSuperAdmin   是否为超级管理员
     * @return 分页结果
     */
    @Override
    public IPage<UserVO> listUsers(UserQueryRequest queryRequest, Long currentOrgId, boolean isSuperAdmin) {
        ThrowUtils.throwIf(currentOrgId == null && !isSuperAdmin,
                ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带组织信息");

        // 1. 构建查询条件（sys_user 在忽略清单，手动按 org_id 过滤）
        QueryWrapper<UserEO> wrapper = new QueryWrapper<>();
        // 1.1 super_admin 可指定任意 orgId；org_admin 强制限定为 currentOrgId
        Long effectiveOrgId = isSuperAdmin && queryRequest != null && queryRequest.getOrgId() != null
                ? queryRequest.getOrgId() : currentOrgId;
        if (effectiveOrgId != null) {
            wrapper.eq("org_id", effectiveOrgId);
        }
        wrapper.eq("deleted", 0);
        if (queryRequest != null) {
            if (StringUtils.hasText(queryRequest.getKeyword())) {
                String kw = queryRequest.getKeyword().trim();
                wrapper.and(w -> w.like("username", kw).or().like("real_name", kw));
            }
            if (queryRequest.getStatus() != null) {
                wrapper.eq("status", queryRequest.getStatus());
            }
        }
        wrapper.orderByDesc("create_time");

        // 2. 分页查询
        long current = queryRequest != null ? queryRequest.getCurrent() : 1;
        long size = queryRequest != null ? queryRequest.getPageSize() : 10;
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR, "每页数量不能超过 200");
        Page<UserEO> page = new Page<>(current, size);
        IPage<UserEO> eoPage = userMapper.selectPage(page, wrapper);

        // 3. 转换为 VO + 批量填充角色编码
        IPage<UserVO> voPage = eoPage.convert(this::convertToVO);
        fillRoleCodes(voPage.getRecords());
        return voPage;
    }

    /**
     * 根据用户业务 ID 查询用户详情
     *
     * @param userId 用户业务 ID
     * @return 用户视图对象
     */
    @Override
    public UserVO getUserById(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        UserEO user = userMapper.selectByUserId(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        UserVO vo = convertToVO(user);
        // 填充角色编码
        List<RoleEO> roles = roleMapper.selectByUserId(userId);
        vo.setRoles(roles.stream().map(RoleEO::getRoleCode).collect(Collectors.toList()));
        return vo;
    }

    // endregion

    // region 新增

    /**
     * 新增用户
     *
     * @param request      新增请求
     * @param currentOrgId 当前请求组织 ID
     * @param isSuperAdmin 是否为超级管理员
     * @return 新建用户业务 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addUser(UserAddRequest request, Long currentOrgId, boolean isSuperAdmin) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "新增请求不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(request.getUsername()),
                ErrorCode.PARAMS_ERROR, "用户名不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(request.getRealName()),
                ErrorCode.PARAMS_ERROR, "真实姓名不能为空");

        // 1. 用户名唯一性校验
        UserEO existing = userMapper.selectByUsername(request.getUsername());
        ThrowUtils.throwIf(existing != null, ErrorCode.OPERATION_ERROR,
                "用户名已存在: " + request.getUsername());

        // 2. 组织 ID 决策：super_admin 可指定；org_admin 强制为 currentOrgId
        Long orgId = isSuperAdmin && request.getOrgId() != null
                ? request.getOrgId() : currentOrgId;
        ThrowUtils.throwIf(orgId == null, ErrorCode.PARAMS_ERROR,
                "组织 ID 不能为空（super_admin 需显式指定 orgId）");

        // 3. 密码决策：未传时使用默认密码
        String rawPassword = StringUtils.hasText(request.getPassword())
                ? request.getPassword() : AuthConstant.DEFAULT_PASSWORD;

        // 3.1 密码策略校验（P2 SEC-1，策略禁用时自动跳过）
        passwordPolicyService.validatePassword(rawPassword);

        // 4. 构建实体
        UserEO user = new UserEO();
        BeanUtils.copyProperties(request, user);
        user.setOrgId(orgId);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(request.getStatus() != null ? request.getStatus()
                : AuthConstant.USER_STATUS_ENABLED);
        user.setDeleted(0);

        // 5. 持久化
        int rows = userMapper.insert(user);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "用户保存失败");

        // 6. 记录密码历史（P2 SEC-1，策略禁用时自动跳过）
        passwordPolicyService.recordPasswordHistory(user.getUserId(), rawPassword);

        log.info("新增用户: userId={}, username={}, orgId={}",
                user.getUserId(), user.getUsername(), orgId);
        return user.getUserId();
    }

    // endregion

    // region 编辑

    /**
     * 编辑用户（用户名不可改）
     *
     * @param request 编辑请求
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(UserUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getUserId() == null,
                ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");

        // 1. 查询原记录
        UserEO existing = userMapper.selectByUserId(request.getUserId());
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 2. 构建更新字段（仅允许修改真实姓名 / 头像 / 邮箱 / 手机号 / 部门 / 状态）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("user_id", request.getUserId());
        if (StringUtils.hasText(request.getRealName())) {
            updateWrapper.set("real_name", request.getRealName());
        }
        if (request.getAvatar() != null) {
            updateWrapper.set("avatar", request.getAvatar());
        }
        if (request.getEmail() != null) {
            updateWrapper.set("email", request.getEmail());
        }
        if (request.getPhone() != null) {
            updateWrapper.set("phone", request.getPhone());
        }
        if (request.getDeptName() != null) {
            updateWrapper.set("dept_name", request.getDeptName());
        }
        if (request.getStatus() != null) {
            ThrowUtils.throwIf(request.getStatus() != 0 && request.getStatus() != 1,
                    ErrorCode.PARAMS_ERROR, "状态值不合法（0-禁用 1-启用）");
            updateWrapper.set("status", request.getStatus());
        }

        int rows = userMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "用户更新失败");

        log.info("更新用户: userId={}, status={}", request.getUserId(), request.getStatus());
        return true;
    }

    // endregion

    // region 启停

    /**
     * 启用 / 禁用用户
     *
     * @param userId 用户业务 ID
     * @param status 目标状态（0-禁用 1-启用）
     * @return 是否操作成功
     */
    @Override
    public boolean toggleUserStatus(Long userId, Integer status) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(status == null || (status != 0 && status != 1),
                ErrorCode.PARAMS_ERROR, "状态值不合法（0-禁用 1-启用）");

        UserEO existing = userMapper.selectByUserId(userId);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("user_id", userId).set("status", status);
        int rows = userMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "用户状态切换失败");

        log.info("切换用户状态: userId={}, status={}", userId, status);
        return true;
    }

    // endregion

    // region 重置密码

    /**
     * 重置密码（不传时使用默认密码）
     *
     * @param request 重置请求
     * @return 是否重置成功
     */
    @Override
    public boolean resetPassword(PasswordResetRequest request) {
        ThrowUtils.throwIf(request == null || request.getUserId() == null,
                ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");

        UserEO existing = userMapper.selectByUserId(request.getUserId());
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 1. 密码决策：未传时使用默认密码
        String rawPassword = StringUtils.hasText(request.getNewPassword())
                ? request.getNewPassword() : AuthConstant.DEFAULT_PASSWORD;

        // 1.1 密码策略校验 + 历史密码校验（P2 SEC-1，策略禁用时自动跳过）
        passwordPolicyService.validatePassword(rawPassword);
        passwordPolicyService.validatePasswordHistory(request.getUserId(), rawPassword);

        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("user_id", request.getUserId())
                .set("password", passwordEncoder.encode(rawPassword))
                .set("pwd_changed_at", new java.sql.Timestamp(System.currentTimeMillis()));
        int rows = userMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "密码重置失败");

        // 2. 记录密码历史（P2 SEC-1，策略禁用时自动跳过）
        passwordPolicyService.recordPasswordHistory(request.getUserId(), rawPassword);

        log.info("重置用户密码: userId={}", request.getUserId());
        return true;
    }

    // endregion

    // region 删除

    /**
     * 逻辑删除用户（deleted = 1）
     *
     * @param userId 用户业务 ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");

        UserEO existing = userMapper.selectByUserId(userId);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 1. 逻辑删除用户（deleted=1）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("user_id", userId).set("deleted", 1);
        int rows = userMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "用户删除失败");

        // 2. 同时清理用户-角色关联（保持一致性，避免脏数据）
        userRoleMapper.deleteByUserId(userId);

        log.info("逻辑删除用户: userId={}, username={}", userId, existing.getUsername());
        return true;
    }

    // endregion

    // region 分配角色

    /**
     * 分配角色（三维度 RBAC，全量替换语义）
     *
     * @param request 分配请求
     * @return 是否分配成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignRoles(UserRoleAssignRequest request) {
        ThrowUtils.throwIf(request == null || request.getUserId() == null,
                ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(request.getRelations() == null,
                ErrorCode.PARAMS_ERROR, "角色关联列表不能为空");

        // 1. 校验用户存在
        UserEO existing = userMapper.selectByUserId(request.getUserId());
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 2. 校验所有 roleId 存在
        List<Long> roleIds = request.getRelations().stream()
                .map(UserRoleAssignRequest.UserRoleRelation::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        if (!roleIds.isEmpty()) {
            List<RoleEO> roles = roleMapper.selectByRoleIds(roleIds);
            Set<Long> foundIds = roles.stream().map(RoleEO::getRoleId).collect(Collectors.toSet());
            for (Long rid : roleIds) {
                ThrowUtils.throwIf(!foundIds.contains(rid), ErrorCode.NOT_FOUND_ERROR,
                        "角色不存在: " + rid);
            }
        }

        // 3. 全量替换：先删后插
        userRoleMapper.deleteByUserId(request.getUserId());
        if (!request.getRelations().isEmpty()) {
            List<UserRoleEO> relations = request.getRelations().stream()
                    .map(r -> {
                        UserRoleEO eo = new UserRoleEO();
                        eo.setUserId(request.getUserId());
                        eo.setRoleId(r.getRoleId());
                        eo.setDepartmentId(r.getDepartmentId());
                        eo.setBusinessLineId(r.getBusinessLineId());
                        return eo;
                    })
                    .collect(Collectors.toList());
            userRoleMapper.insertBatch(relations);
        }

        log.info("分配角色: userId={}, relationCount={}",
                request.getUserId(), request.getRelations().size());
        return true;
    }

    // endregion

    // region 私有工具方法

    /**
     * 将用户实体转换为用户 VO（不含角色编码，由 fillRoleCodes 统一填充）
     *
     * @param eo 用户实体
     * @return 用户 VO
     */
    private UserVO convertToVO(UserEO eo) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(eo, vo);
        return vo;
    }

    /**
     * 批量填充用户 VO 的角色编码列表（避免 N+1 查询）
     *
     * @param records 用户 VO 列表（in-place 填充）
     */
    private void fillRoleCodes(List<UserVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 1. 批量查询每个用户的角色关联
        // 由于 sys_user_role 仅按 user_id 索引，这里逐用户查询；数据量通常较小（每页 ≤ 200）
        for (UserVO vo : records) {
            if (vo.getUserId() == null) {
                vo.setRoles(new ArrayList<>());
                continue;
            }
            List<RoleEO> roles = roleMapper.selectByUserId(vo.getUserId());
            vo.setRoles(roles.stream().map(RoleEO::getRoleCode).collect(Collectors.toList()));
        }
    }

    // endregion
}

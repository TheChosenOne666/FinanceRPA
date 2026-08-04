package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.auth.constant.AuthConstant;
import com.finrpa.auth.dto.request.RoleAddRequest;
import com.finrpa.auth.dto.request.RoleQueryRequest;
import com.finrpa.auth.dto.request.RoleUpdateRequest;
import com.finrpa.auth.dto.response.RoleVO;
import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.UserRoleEO;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.UserRoleMapper;
import com.finrpa.auth.service.RoleService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现（P1 USR-2）
 *
 * <p>sys_role 表在 TenantConstant.IGNORED_TABLES 中，本服务在 Service 层手动按 org_id 过滤。
 * 内置角色编码（{@link AuthConstant#BUILT_IN_ROLE_CODES}）受保护：禁止新增同编码角色、禁止删除、禁止修改编码。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    /** 角色 Mapper */
    @Resource
    private RoleMapper roleMapper;

    /** 用户-角色关联 Mapper（用于删除前校验是否仍有用户关联） */
    @Resource
    private UserRoleMapper userRoleMapper;

    // region 查询

    /**
     * 分页查询角色列表
     *
     * @param queryRequest  查询条件
     * @param currentOrgId  当前请求组织 ID
     * @param isSuperAdmin  是否为超级管理员
     * @return 分页结果
     */
    @Override
    public IPage<RoleVO> listRoles(RoleQueryRequest queryRequest, Long currentOrgId, boolean isSuperAdmin) {
        // 1. 构建查询条件
        QueryWrapper<RoleEO> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        // 1.1 super_admin 可看所有；org_admin 仅看本组织 + 全局内置角色（org_id IS NULL）
        if (!isSuperAdmin) {
            wrapper.and(w -> w.eq("org_id", currentOrgId).or().isNull("org_id"));
        } else if (queryRequest != null && queryRequest.getOrgId() != null) {
            // super_admin 显式指定 orgId 时按该组织 + 全局内置过滤
            Long qOrgId = queryRequest.getOrgId();
            wrapper.and(w -> w.eq("org_id", qOrgId).or().isNull("org_id"));
        }
        if (queryRequest != null) {
            if (StringUtils.hasText(queryRequest.getKeyword())) {
                String kw = queryRequest.getKeyword().trim();
                wrapper.and(w -> w.like("role_name", kw).or().like("role_code", kw));
            }
            if (queryRequest.getStatus() != null) {
                wrapper.eq("status", queryRequest.getStatus());
            }
        }
        wrapper.orderByAsc("id");

        // 2. 分页查询
        long current = queryRequest != null ? queryRequest.getCurrent() : 1;
        long size = queryRequest != null ? queryRequest.getPageSize() : 10;
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR, "每页数量不能超过 200");
        Page<RoleEO> page = new Page<>(current, size);
        IPage<RoleEO> eoPage = roleMapper.selectPage(page, wrapper);

        // 3. 转换为 VO（填充 builtIn 标识）
        return eoPage.convert(this::convertToVO);
    }

    /**
     * 查询全部角色（不分页，用于分配角色下拉选项）
     *
     * @param currentOrgId 当前请求组织 ID
     * @param isSuperAdmin 是否为超级管理员
     * @return 角色列表
     */
    @Override
    public List<RoleVO> listAllRoles(Long currentOrgId, boolean isSuperAdmin) {
        List<RoleEO> roles = roleMapper.selectAll();
        // 1. org_admin 仅看本组织 + 全局内置
        if (!isSuperAdmin) {
            roles = roles.stream()
                    .filter(r -> r.getOrgId() == null
                            || (currentOrgId != null && currentOrgId.equals(r.getOrgId())))
                    .collect(Collectors.toList());
        }
        return roles.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 根据角色业务 ID 查询角色详情
     *
     * @param roleId 角色业务 ID
     * @return 角色 VO
     */
    @Override
    public RoleVO getRoleById(Long roleId) {
        ThrowUtils.throwIf(roleId == null, ErrorCode.PARAMS_ERROR, "角色 ID 不能为空");
        QueryWrapper<RoleEO> wrapper = new QueryWrapper<>();
        wrapper.eq("role_id", roleId).eq("deleted", 0);
        RoleEO role = roleMapper.selectOne(wrapper);
        ThrowUtils.throwIf(role == null, ErrorCode.NOT_FOUND_ERROR, "角色不存在");
        return convertToVO(role);
    }

    // endregion

    // region 新增

    /**
     * 新增角色
     *
     * @param request      新增请求
     * @param currentOrgId 当前请求组织 ID
     * @param isSuperAdmin 是否为超级管理员
     * @return 新建角色业务 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addRole(RoleAddRequest request, Long currentOrgId, boolean isSuperAdmin) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "新增请求不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(request.getRoleName()),
                ErrorCode.PARAMS_ERROR, "角色名称不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(request.getRoleCode()),
                ErrorCode.PARAMS_ERROR, "角色编码不能为空");

        // 1. 内置角色编码保护：禁止新增 super_admin / org_admin / operator / approver / viewer
        ThrowUtils.throwIf(AuthConstant.BUILT_IN_ROLE_CODES.contains(request.getRoleCode()),
                ErrorCode.OPERATION_ERROR, "禁止新增内置角色编码: " + request.getRoleCode());

        // 2. 角色编码唯一性校验
        RoleEO existing = roleMapper.selectByRoleCode(request.getRoleCode());
        ThrowUtils.throwIf(existing != null, ErrorCode.OPERATION_ERROR,
                "角色编码已存在: " + request.getRoleCode());

        // 3. 组织 ID 决策：super_admin 可指定 orgId（含 null 全局）；org_admin 强制为 currentOrgId
        Long orgId = isSuperAdmin ? request.getOrgId() : currentOrgId;

        // 4. 构建实体
        RoleEO role = new RoleEO();
        BeanUtils.copyProperties(request, role);
        role.setOrgId(orgId);
        role.setIsCrossOrgRead(request.getIsCrossOrgRead() != null ? request.getIsCrossOrgRead() : 0);
        role.setIsCrossOrgApprove(request.getIsCrossOrgApprove() != null ? request.getIsCrossOrgApprove() : 0);
        role.setStatus(request.getStatus() != null ? request.getStatus() : AuthConstant.ROLE_STATUS_ENABLED);
        role.setDeleted(0);

        int rows = roleMapper.insert(role);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "角色保存失败");

        log.info("新增角色: roleId={}, roleCode={}, orgId={}",
                role.getRoleId(), role.getRoleCode(), orgId);
        return role.getRoleId();
    }

    // endregion

    // region 编辑

    /**
     * 编辑角色（roleCode 不可改）
     *
     * @param request 编辑请求
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRole(RoleUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getRoleId() == null,
                ErrorCode.PARAMS_ERROR, "角色 ID 不能为空");

        // 1. 查询原记录
        QueryWrapper<RoleEO> wrapper = new QueryWrapper<>();
        wrapper.eq("role_id", request.getRoleId()).eq("deleted", 0);
        RoleEO existing = roleMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "角色不存在");

        // 2. 内置角色保护：仅允许改 description / status，其他字段忽略
        boolean isBuiltIn = AuthConstant.BUILT_IN_ROLE_CODES.contains(existing.getRoleCode());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RoleEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("role_id", request.getRoleId());
        if (request.getDescription() != null) {
            updateWrapper.set("description", request.getDescription());
        }
        if (request.getStatus() != null) {
            ThrowUtils.throwIf(request.getStatus() != 0 && request.getStatus() != 1,
                    ErrorCode.PARAMS_ERROR, "状态值不合法（0-禁用 1-启用）");
            // 2.1 super_admin / org_admin 内置角色禁止禁用（避免锁死系统）
            if (isBuiltIn && ("super_admin".equals(existing.getRoleCode())
                    || "org_admin".equals(existing.getRoleCode()))
                    && request.getStatus() == 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "内置管理员角色不允许禁用: " + existing.getRoleCode());
            }
            updateWrapper.set("status", request.getStatus());
        }
        if (!isBuiltIn) {
            // 3. 非内置角色才允许修改名称 / 跨组织读 / 跨组织审批
            if (StringUtils.hasText(request.getRoleName())) {
                updateWrapper.set("role_name", request.getRoleName());
            }
            if (request.getIsCrossOrgRead() != null) {
                updateWrapper.set("is_cross_org_read", request.getIsCrossOrgRead());
            }
            if (request.getIsCrossOrgApprove() != null) {
                updateWrapper.set("is_cross_org_approve", request.getIsCrossOrgApprove());
            }
        }

        int rows = roleMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "角色更新失败");

        log.info("更新角色: roleId={}, builtIn={}, status={}",
                request.getRoleId(), isBuiltIn, request.getStatus());
        return true;
    }

    // endregion

    // region 启停

    /**
     * 启停角色
     *
     * @param roleId 角色业务 ID
     * @param status 目标状态
     * @return 是否操作成功
     */
    @Override
    public boolean toggleRoleStatus(Long roleId, Integer status) {
        ThrowUtils.throwIf(roleId == null, ErrorCode.PARAMS_ERROR, "角色 ID 不能为空");
        ThrowUtils.throwIf(status == null || (status != 0 && status != 1),
                ErrorCode.PARAMS_ERROR, "状态值不合法（0-禁用 1-启用）");

        QueryWrapper<RoleEO> wrapper = new QueryWrapper<>();
        wrapper.eq("role_id", roleId).eq("deleted", 0);
        RoleEO existing = roleMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "角色不存在");

        // 1. super_admin / org_admin 内置角色禁止禁用
        if (status == 0 && ("super_admin".equals(existing.getRoleCode())
                || "org_admin".equals(existing.getRoleCode()))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "内置管理员角色不允许禁用: " + existing.getRoleCode());
        }

        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RoleEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("role_id", roleId).set("status", status);
        int rows = roleMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "角色状态切换失败");

        log.info("切换角色状态: roleId={}, status={}", roleId, status);
        return true;
    }

    // endregion

    // region 删除

    /**
     * 逻辑删除角色（内置角色禁止删除；有用户关联的角色禁止删除）
     *
     * @param roleId 角色业务 ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Long roleId) {
        ThrowUtils.throwIf(roleId == null, ErrorCode.PARAMS_ERROR, "角色 ID 不能为空");

        QueryWrapper<RoleEO> wrapper = new QueryWrapper<>();
        wrapper.eq("role_id", roleId).eq("deleted", 0);
        RoleEO existing = roleMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "角色不存在");

        // 1. 内置角色保护
        ThrowUtils.throwIf(AuthConstant.BUILT_IN_ROLE_CODES.contains(existing.getRoleCode()),
                ErrorCode.OPERATION_ERROR, "内置角色不允许删除: " + existing.getRoleCode());

        // 2. 有用户关联的角色禁止删除
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRoleEO> relWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        relWrapper.eq(UserRoleEO::getRoleId, roleId);
        Long relCount = userRoleMapper.selectCount(relWrapper);
        ThrowUtils.throwIf(relCount > 0, ErrorCode.OPERATION_ERROR,
                "该角色仍有 " + relCount + " 个用户关联，请先解除关联再删除");

        // 3. 逻辑删除
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<RoleEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("role_id", roleId).set("deleted", 1);
        int rows = roleMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "角色删除失败");

        log.info("逻辑删除角色: roleId={}, roleCode={}", roleId, existing.getRoleCode());
        return true;
    }

    // endregion

    // region 私有工具方法

    /**
     * 将角色实体转换为角色 VO（填充 builtIn 标识）
     *
     * @param eo 角色实体
     * @return 角色 VO
     */
    private RoleVO convertToVO(RoleEO eo) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(eo, vo);
        vo.setBuiltIn(AuthConstant.BUILT_IN_ROLE_CODES.contains(eo.getRoleCode()));
        return vo;
    }

    // endregion
}

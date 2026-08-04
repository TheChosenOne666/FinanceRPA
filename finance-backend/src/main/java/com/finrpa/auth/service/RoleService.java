package com.finrpa.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.auth.dto.request.RoleAddRequest;
import com.finrpa.auth.dto.request.RoleQueryRequest;
import com.finrpa.auth.dto.request.RoleUpdateRequest;
import com.finrpa.auth.dto.response.RoleVO;

import java.util.List;

/**
 * 角色管理服务接口（P1 USR-2）
 *
 * <p>提供角色 CRUD、启停能力。内置角色（super_admin / org_admin / operator / approver / viewer）
 * 受保护：仅可改状态/描述，不可删除、不可改 roleCode。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface RoleService {

    /**
     * 分页查询角色列表
     *
     * @param queryRequest 查询条件
     * @param currentOrgId 当前请求组织 ID
     * @param isSuperAdmin 是否为超级管理员（true 时返回所有组织角色；false 时返回本组织角色 + 全局内置角色）
     * @return 分页结果
     */
    IPage<RoleVO> listRoles(RoleQueryRequest queryRequest, Long currentOrgId, boolean isSuperAdmin);

    /**
     * 查询全部角色（不分页，用于分配角色下拉选项）
     *
     * @param currentOrgId 当前请求组织 ID
     * @param isSuperAdmin 是否为超级管理员
     * @return 角色列表
     */
    List<RoleVO> listAllRoles(Long currentOrgId, boolean isSuperAdmin);

    /**
     * 根据角色业务 ID 查询角色详情
     *
     * @param roleId 角色业务 ID
     * @return 角色 VO
     */
    RoleVO getRoleById(Long roleId);

    /**
     * 新增角色（内置角色编码受保护，禁止新增）
     *
     * @param request       新增请求
     * @param currentOrgId  当前请求组织 ID
     * @param isSuperAdmin  是否为超级管理员
     * @return 新建角色业务 ID
     */
    Long addRole(RoleAddRequest request, Long currentOrgId, boolean isSuperAdmin);

    /**
     * 编辑角色（roleCode 不可改；内置角色仅可改状态/描述）
     *
     * @param request 编辑请求
     * @return 是否更新成功
     */
    boolean updateRole(RoleUpdateRequest request);

    /**
     * 启停角色
     *
     * @param roleId 角色业务 ID
     * @param status 目标状态（0-禁用 1-启用）
     * @return 是否操作成功
     */
    boolean toggleRoleStatus(Long roleId, Integer status);

    /**
     * 逻辑删除角色（内置角色禁止删除；有用户关联的角色禁止删除）
     *
     * @param roleId 角色业务 ID
     * @return 是否删除成功
     */
    boolean deleteRole(Long roleId);
}

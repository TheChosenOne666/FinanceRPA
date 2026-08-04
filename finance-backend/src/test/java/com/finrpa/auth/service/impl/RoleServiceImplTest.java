package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.finrpa.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 角色管理服务实现单元测试（P1 USR-2）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    private static final Long ORG_ID = 100L;

    // region listRoles

    @Test
    @DisplayName("listRoles - org_admin 查询本组织 + 全局内置角色")
    void listRoles_OrgAdmin_ReturnsOrgAndBuiltinRoles() {
        // 1. 本组织角色 + 全局内置角色（org_id IS NULL）
        RoleEO orgRole = buildEo(1L, "custom_role", "自定义角色", ORG_ID, 1);
        RoleEO builtinRole = buildEo(2L, "operator", "操作员", null, 1);
        Page<RoleEO> page = new Page<>(1, 10, 2);
        page.setRecords(Arrays.asList(orgRole, builtinRole));
        when(roleMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        RoleQueryRequest queryRequest = new RoleQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);

        var result = roleService.listRoles(queryRequest, ORG_ID, false);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
        // 内置角色 builtIn 标识应为 true
        assertThat(result.getRecords().get(0).getBuiltIn()).isFalse();
        assertThat(result.getRecords().get(1).getBuiltIn()).isTrue();
    }

    @Test
    @DisplayName("listRoles - super_admin 查询全部角色")
    void listRoles_SuperAdmin_ReturnsAllRoles() {
        RoleEO role1 = buildEo(1L, "custom_role", "角色1", ORG_ID, 1);
        RoleEO role2 = buildEo(2L, "custom_role2", "角色2", 200L, 1);
        Page<RoleEO> page = new Page<>(1, 10, 2);
        page.setRecords(Arrays.asList(role1, role2));
        when(roleMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        RoleQueryRequest queryRequest = new RoleQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);

        var result = roleService.listRoles(queryRequest, ORG_ID, true);

        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("listRoles - 每页数量超过 200 抛出异常")
    void listRoles_PageSizeExceedsLimit_ThrowsException() {
        RoleQueryRequest queryRequest = new RoleQueryRequest();
        queryRequest.setPageSize(201);

        assertThrows(BusinessException.class,
                () -> roleService.listRoles(queryRequest, ORG_ID, false));
    }

    // endregion

    // region listAllRoles

    @Test
    @DisplayName("listAllRoles - org_admin 仅返回本组织 + 全局内置角色")
    void listAllRoles_OrgAdmin_FiltersCorrectly() {
        RoleEO orgRole = buildEo(1L, "custom_role", "本组织角色", ORG_ID, 1);
        RoleEO otherOrgRole = buildEo(2L, "other_role", "其他组织角色", 999L, 1);
        RoleEO builtinRole = buildEo(3L, "viewer", "查看者", null, 1);
        when(roleMapper.selectAll()).thenReturn(Arrays.asList(orgRole, otherOrgRole, builtinRole));

        List<RoleVO> result = roleService.listAllRoles(ORG_ID, false);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RoleVO::getRoleCode)
                .containsExactlyInAnyOrder("custom_role", "viewer");
    }

    @Test
    @DisplayName("listAllRoles - super_admin 返回全部角色")
    void listAllRoles_SuperAdmin_ReturnsAll() {
        RoleEO role1 = buildEo(1L, "role1", "角色1", ORG_ID, 1);
        RoleEO role2 = buildEo(2L, "role2", "角色2", 999L, 1);
        when(roleMapper.selectAll()).thenReturn(Arrays.asList(role1, role2));

        List<RoleVO> result = roleService.listAllRoles(ORG_ID, true);

        assertThat(result).hasSize(2);
    }

    // endregion

    // region getRoleById

    @Test
    @DisplayName("getRoleById - 查询成功")
    void getRoleById_Success() {
        RoleEO role = buildEo(1L, "custom_role", "自定义角色", ORG_ID, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(role);

        RoleVO vo = roleService.getRoleById(1L);

        assertThat(vo).isNotNull();
        assertThat(vo.getRoleId()).isEqualTo(1L);
        assertThat(vo.getRoleCode()).isEqualTo("custom_role");
        assertThat(vo.getBuiltIn()).isFalse();
    }

    @Test
    @DisplayName("getRoleById - 内置角色 builtIn 标识为 true")
    void getRoleById_BuiltInRole_MarksBuiltIn() {
        RoleEO role = buildEo(1L, "operator", "操作员", null, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(role);

        RoleVO vo = roleService.getRoleById(1L);

        assertThat(vo.getBuiltIn()).isTrue();
    }

    @Test
    @DisplayName("getRoleById - ID 为空抛出异常")
    void getRoleById_NullId_ThrowsException() {
        assertThrows(BusinessException.class, () -> roleService.getRoleById(null));
    }

    @Test
    @DisplayName("getRoleById - 角色不存在抛出异常")
    void getRoleById_NotFound_ThrowsException() {
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> roleService.getRoleById(999L));
    }

    // endregion

    // region addRole

    @Test
    @DisplayName("addRole - 新增自定义角色成功")
    void addRole_Success() {
        when(roleMapper.selectByRoleCode("custom_role")).thenReturn(null);
        when(roleMapper.insert(any(RoleEO.class))).thenAnswer(invocation -> {
            RoleEO eo = invocation.getArgument(0);
            eo.setRoleId(1L);
            return 1;
        });

        RoleAddRequest request = new RoleAddRequest();
        request.setRoleName("自定义角色");
        request.setRoleCode("custom_role");

        Long roleId = roleService.addRole(request, ORG_ID, false);

        assertThat(roleId).isEqualTo(1L);
        ArgumentCaptor<RoleEO> captor = ArgumentCaptor.forClass(RoleEO.class);
        verify(roleMapper).insert(captor.capture());
        RoleEO inserted = captor.getValue();
        assertThat(inserted.getOrgId()).isEqualTo(ORG_ID);
        assertThat(inserted.getStatus()).isEqualTo(AuthConstant.ROLE_STATUS_ENABLED);
        assertThat(inserted.getDeleted()).isEqualTo(0);
    }

    @Test
    @DisplayName("addRole - super_admin 新增全局角色（orgId 为 null）")
    void addRole_SuperAdmin_GlobalRole_Success() {
        when(roleMapper.selectByRoleCode("global_role")).thenReturn(null);
        when(roleMapper.insert(any(RoleEO.class))).thenReturn(1);

        RoleAddRequest request = new RoleAddRequest();
        request.setRoleName("全局角色");
        request.setRoleCode("global_role");
        request.setOrgId(null); // 全局角色

        roleService.addRole(request, ORG_ID, true);

        ArgumentCaptor<RoleEO> captor = ArgumentCaptor.forClass(RoleEO.class);
        verify(roleMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrgId()).isNull();
    }

    @Test
    @DisplayName("addRole - 内置角色编码保护：禁止新增 super_admin")
    void addRole_BuiltInCode_SuperAdmin_ThrowsException() {
        RoleAddRequest request = new RoleAddRequest();
        request.setRoleName("超管");
        request.setRoleCode("super_admin");

        assertThrows(BusinessException.class,
                () -> roleService.addRole(request, ORG_ID, true));
    }

    @Test
    @DisplayName("addRole - 内置角色编码保护：禁止新增 operator")
    void addRole_BuiltInCode_Operator_ThrowsException() {
        RoleAddRequest request = new RoleAddRequest();
        request.setRoleName("操作员");
        request.setRoleCode("operator");

        assertThrows(BusinessException.class,
                () -> roleService.addRole(request, ORG_ID, false));
    }

    @Test
    @DisplayName("addRole - 角色编码已存在抛出异常")
    void addRole_DuplicateCode_ThrowsException() {
        RoleEO existing = buildEo(1L, "custom_role", "已存在", ORG_ID, 1);
        when(roleMapper.selectByRoleCode("custom_role")).thenReturn(existing);

        RoleAddRequest request = new RoleAddRequest();
        request.setRoleName("新角色");
        request.setRoleCode("custom_role");

        assertThrows(BusinessException.class,
                () -> roleService.addRole(request, ORG_ID, false));
    }

    @Test
    @DisplayName("addRole - 角色名称为空抛出异常")
    void addRole_BlankRoleName_ThrowsException() {
        RoleAddRequest request = new RoleAddRequest();
        request.setRoleName("");
        request.setRoleCode("custom_role");

        assertThrows(BusinessException.class,
                () -> roleService.addRole(request, ORG_ID, false));
    }

    @Test
    @DisplayName("addRole - 角色编码为空抛出异常")
    void addRole_BlankRoleCode_ThrowsException() {
        RoleAddRequest request = new RoleAddRequest();
        request.setRoleName("新角色");
        request.setRoleCode("");

        assertThrows(BusinessException.class,
                () -> roleService.addRole(request, ORG_ID, false));
    }

    // endregion

    // region updateRole

    @Test
    @DisplayName("updateRole - 非内置角色全字段更新成功")
    void updateRole_CustomRole_Success() {
        RoleEO existing = buildEo(1L, "custom_role", "原名称", ORG_ID, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(roleMapper.update(any(), any())).thenReturn(1);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleId(1L);
        request.setRoleName("新名称");
        request.setDescription("新描述");
        request.setIsCrossOrgRead(1);
        request.setStatus(1);

        boolean success = roleService.updateRole(request);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("updateRole - 内置角色仅可改状态和描述")
    void updateRole_BuiltInRole_OnlyStatusAndDescription() {
        RoleEO existing = buildEo(1L, "operator", "操作员", null, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(roleMapper.update(any(), any())).thenReturn(1);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleId(1L);
        request.setRoleName("尝试修改名称"); // 应被忽略
        request.setDescription("可修改描述");
        request.setStatus(0); // 可禁用 operator
        request.setIsCrossOrgRead(1); // 应被忽略

        boolean success = roleService.updateRole(request);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("updateRole - 内置管理员角色 super_admin 禁止禁用")
    void updateRole_BuiltInSuperAdmin_ForbidDisable() {
        RoleEO existing = buildEo(1L, "super_admin", "超管", null, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleId(1L);
        request.setStatus(0); // 尝试禁用

        assertThrows(BusinessException.class, () -> roleService.updateRole(request));
    }

    @Test
    @DisplayName("updateRole - 内置管理员角色 org_admin 禁止禁用")
    void updateRole_BuiltInOrgAdmin_ForbidDisable() {
        RoleEO existing = buildEo(1L, "org_admin", "组织管理员", ORG_ID, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleId(1L);
        request.setStatus(0);

        assertThrows(BusinessException.class, () -> roleService.updateRole(request));
    }

    @Test
    @DisplayName("updateRole - roleId 为空抛出异常")
    void updateRole_NullRoleId_ThrowsException() {
        RoleUpdateRequest request = new RoleUpdateRequest();
        assertThrows(BusinessException.class, () -> roleService.updateRole(request));
    }

    @Test
    @DisplayName("updateRole - 角色不存在抛出异常")
    void updateRole_NotFound_ThrowsException() {
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleId(999L);

        assertThrows(BusinessException.class, () -> roleService.updateRole(request));
    }

    // endregion

    // region toggleRoleStatus

    @Test
    @DisplayName("toggleRoleStatus - 切换自定义角色状态成功")
    void toggleRoleStatus_CustomRole_Success() {
        RoleEO existing = buildEo(1L, "custom_role", "自定义", ORG_ID, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(roleMapper.update(any(), any())).thenReturn(1);

        boolean success = roleService.toggleRoleStatus(1L, 0);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("toggleRoleStatus - 内置管理员角色禁止禁用")
    void toggleRoleStatus_BuiltInAdmin_ForbidDisable() {
        RoleEO existing = buildEo(1L, "super_admin", "超管", null, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        assertThrows(BusinessException.class, () -> roleService.toggleRoleStatus(1L, 0));
    }

    @Test
    @DisplayName("toggleRoleStatus - roleId 为空抛出异常")
    void toggleRoleStatus_NullRoleId_ThrowsException() {
        assertThrows(BusinessException.class, () -> roleService.toggleRoleStatus(null, 1));
    }

    @Test
    @DisplayName("toggleRoleStatus - 非法状态值抛出异常")
    void toggleRoleStatus_InvalidStatus_ThrowsException() {
        assertThrows(BusinessException.class, () -> roleService.toggleRoleStatus(1L, 5));
    }

    @Test
    @DisplayName("toggleRoleStatus - 角色不存在抛出异常")
    void toggleRoleStatus_NotFound_ThrowsException() {
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> roleService.toggleRoleStatus(999L, 1));
    }

    // endregion

    // region deleteRole

    @Test
    @DisplayName("deleteRole - 自定义角色无用户关联时删除成功")
    void deleteRole_CustomRoleNoAssociation_Success() {
        RoleEO existing = buildEo(1L, "custom_role", "自定义", ORG_ID, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(roleMapper.update(any(), any())).thenReturn(1);

        boolean success = roleService.deleteRole(1L);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("deleteRole - 内置角色禁止删除")
    void deleteRole_BuiltInRole_ThrowsException() {
        RoleEO existing = buildEo(1L, "operator", "操作员", null, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        assertThrows(BusinessException.class, () -> roleService.deleteRole(1L));
    }

    @Test
    @DisplayName("deleteRole - 有用户关联的角色禁止删除")
    void deleteRole_WithUserAssociation_ThrowsException() {
        RoleEO existing = buildEo(1L, "custom_role", "自定义", ORG_ID, 1);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThrows(BusinessException.class, () -> roleService.deleteRole(1L));
    }

    @Test
    @DisplayName("deleteRole - roleId 为空抛出异常")
    void deleteRole_NullRoleId_ThrowsException() {
        assertThrows(BusinessException.class, () -> roleService.deleteRole(null));
    }

    @Test
    @DisplayName("deleteRole - 角色不存在抛出异常")
    void deleteRole_NotFound_ThrowsException() {
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> roleService.deleteRole(999L));
    }

    // endregion

    // region 测试辅助方法

    /**
     * 构建角色 EO
     */
    private RoleEO buildEo(Long roleId, String roleCode, String roleName, Long orgId, Integer status) {
        RoleEO eo = new RoleEO();
        eo.setRoleId(roleId);
        eo.setRoleCode(roleCode);
        eo.setRoleName(roleName);
        eo.setOrgId(orgId);
        eo.setStatus(status);
        eo.setIsCrossOrgRead(0);
        eo.setIsCrossOrgApprove(0);
        eo.setDeleted(0);
        return eo;
    }

    // endregion
}

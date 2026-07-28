package com.finrpa.tenant.service;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.tenant.context.TenantContext;
import com.finrpa.tenant.dto.response.BusinessLineVO;
import com.finrpa.tenant.dto.response.DepartmentVO;
import com.finrpa.tenant.dto.response.TenantInfoResponse;
import com.finrpa.tenant.entity.BusinessLineEO;
import com.finrpa.tenant.entity.DepartmentEO;
import com.finrpa.tenant.entity.OrganizationEO;
import com.finrpa.tenant.mapper.BusinessLineMapper;
import com.finrpa.tenant.mapper.DepartmentMapper;
import com.finrpa.tenant.mapper.OrganizationMapper;
import com.finrpa.tenant.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TenantServiceImpl 单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock
    private OrganizationMapper organizationMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private BusinessLineMapper businessLineMapper;

    @InjectMocks
    private TenantServiceImpl tenantService;

    @AfterEach
    void tearDown() {
        // 清理 TenantContext
        TenantContext.clear();
    }

    private OrganizationEO createOrganization(String orgId, String orgName, String orgCode) {
        OrganizationEO org = new OrganizationEO();
        org.setOrgId(orgId);
        org.setOrgName(orgName);
        org.setOrgCode(orgCode);
        org.setDescription("测试组织");
        org.setStatus(1);
        org.setCreateTime(new Timestamp(System.currentTimeMillis()));
        return org;
    }

    private DepartmentEO createDepartment(String deptId, String orgId, String deptName, String deptCode, Integer sortOrder) {
        DepartmentEO dept = new DepartmentEO();
        dept.setDeptId(deptId);
        dept.setOrgId(orgId);
        dept.setDeptName(deptName);
        dept.setDeptCode(deptCode);
        dept.setSortOrder(sortOrder);
        dept.setStatus(1);
        dept.setDeleted(0);
        return dept;
    }

    private BusinessLineEO createBusinessLine(String businessLineId, String orgId, String name, String code, Integer sortOrder) {
        BusinessLineEO bl = new BusinessLineEO();
        bl.setBusinessLineId(businessLineId);
        bl.setOrgId(orgId);
        bl.setBusinessLineName(name);
        bl.setBusinessLineCode(code);
        bl.setSortOrder(sortOrder);
        bl.setStatus(1);
        bl.setDeleted(0);
        return bl;
    }

    @Test
    @DisplayName("getTenantInfo - 组织存在时返回信息")
    void getTenantInfo_OrganizationExists_ReturnsInfo() {
        // 1. 设置 TenantContext
        String orgId = "org-001";
        TenantContext.setOrgId(orgId);

        // 2. mock 查询返回组织
        OrganizationEO org = createOrganization(orgId, "测试组织", "TEST_ORG");
        when(organizationMapper.selectByOrgId(orgId)).thenReturn(org);

        // 3. 调用服务
        TenantInfoResponse response = tenantService.getTenantInfo();

        // 4. 验证
        assertThat(response.getOrgId()).isEqualTo(orgId);
        assertThat(response.getOrgName()).isEqualTo("测试组织");
        assertThat(response.getOrgCode()).isEqualTo("TEST_ORG");
        assertThat(response.getDescription()).isEqualTo("测试组织");
        assertThat(response.getStatus()).isEqualTo(1);
        assertThat(response.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("getTenantInfo - 组织不存在时抛异常")
    void getTenantInfo_OrganizationNotExists_ThrowsException() {
        // 1. 设置 TenantContext
        TenantContext.setOrgId("org-unknown");

        // 2. mock 返回 null
        when(organizationMapper.selectByOrgId("org-unknown")).thenReturn(null);

        // 3. 验证抛 NOT_FOUND_ERROR
        assertThatThrownBy(() -> tenantService.getTenantInfo())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织不存在");
    }

    @Test
    @DisplayName("getTenantInfo - 上下文未设置 orgId 时抛 NOT_LOGIN_ERROR")
    void getTenantInfo_NoOrgId_ThrowsException() {
        // 1. 不设置 TenantContext

        // 2. 验证抛 NOT_LOGIN_ERROR
        assertThatThrownBy(() -> tenantService.getTenantInfo())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未携带组织信息");
    }

    @Test
    @DisplayName("listDepartments - 返回部门列表（按 sortOrder 升序）")
    void listDepartments_ReturnsSortedList() {
        // 1. 设置 TenantContext
        String orgId = "org-001";
        TenantContext.setOrgId(orgId);

        // 2. mock 返回（mock 按顺序返回，由 SQL 排序保证，这里仅验证转换逻辑）
        List<DepartmentEO> mockList = List.of(
                createDepartment("dept-1", orgId, "对公信贷部", "CREDIT", 1),
                createDepartment("dept-2", orgId, "风险管理部", "RISK", 2)
        );
        when(departmentMapper.selectList(any())).thenReturn(mockList);

        // 3. 调用
        List<DepartmentVO> result = tenantService.listDepartments();

        // 4. 验证
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDeptId()).isEqualTo("dept-1");
        assertThat(result.get(0).getDeptName()).isEqualTo("对公信贷部");
        assertThat(result.get(0).getDeptCode()).isEqualTo("CREDIT");
        assertThat(result.get(0).getSortOrder()).isEqualTo(1);
        assertThat(result.get(1).getDeptId()).isEqualTo("dept-2");
    }

    @Test
    @DisplayName("listDepartments - 无部门时返回空列表")
    void listDepartments_EmptyList_ReturnsEmpty() {
        // 1. 设置 TenantContext
        TenantContext.setOrgId("org-empty");

        // 2. mock 返回空列表
        when(departmentMapper.selectList(any())).thenReturn(List.of());

        // 3. 调用
        List<DepartmentVO> result = tenantService.listDepartments();

        // 4. 验证
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listDepartments - 上下文未设置 orgId 时抛异常")
    void listDepartments_NoOrgId_ThrowsException() {
        // 不设置 TenantContext
        assertThatThrownBy(() -> tenantService.listDepartments())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未携带组织信息");
    }

    @Test
    @DisplayName("listBusinessLines - 返回业务线列表（按 sortOrder 升序）")
    void listBusinessLines_ReturnsSortedList() {
        // 1. 设置 TenantContext
        String orgId = "org-001";
        TenantContext.setOrgId(orgId);

        // 2. mock 返回
        List<BusinessLineEO> mockList = List.of(
                createBusinessLine("bl-1", orgId, "对公信贷", "CREDIT", 1),
                createBusinessLine("bl-2", orgId, "个人金融", "RETAIL", 2)
        );
        when(businessLineMapper.selectList(any())).thenReturn(mockList);

        // 3. 调用
        List<BusinessLineVO> result = tenantService.listBusinessLines();

        // 4. 验证
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBusinessLineId()).isEqualTo("bl-1");
        assertThat(result.get(0).getBusinessLineName()).isEqualTo("对公信贷");
        assertThat(result.get(0).getBusinessLineCode()).isEqualTo("CREDIT");
        assertThat(result.get(1).getBusinessLineId()).isEqualTo("bl-2");
    }

    @Test
    @DisplayName("listBusinessLines - 无业务线时返回空列表")
    void listBusinessLines_EmptyList_ReturnsEmpty() {
        // 1. 设置 TenantContext
        TenantContext.setOrgId("org-empty");

        // 2. mock 返回空列表
        when(businessLineMapper.selectList(any())).thenReturn(List.of());

        // 3. 调用
        List<BusinessLineVO> result = tenantService.listBusinessLines();

        // 4. 验证
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listBusinessLines - 上下文未设置 orgId 时抛异常")
    void listBusinessLines_NoOrgId_ThrowsException() {
        // 不设置 TenantContext
        assertThatThrownBy(() -> tenantService.listBusinessLines())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未携带组织信息");
    }
}

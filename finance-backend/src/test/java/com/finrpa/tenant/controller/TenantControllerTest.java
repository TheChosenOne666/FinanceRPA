package com.finrpa.tenant.controller;

import com.finrpa.tenant.dto.response.BusinessLineVO;
import com.finrpa.tenant.dto.response.DepartmentVO;
import com.finrpa.tenant.dto.response.TenantInfoResponse;
import com.finrpa.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TenantController 单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class TenantControllerTest {

    private MockMvc mockMvc;
    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        // 1. mock 依赖
        tenantService = mock(TenantService.class);

        // 2. 创建控制器并反射注入 tenantService（@Resource 字段注入场景）
        TenantController controller = new TenantController();
        ReflectionTestUtils.setField(controller, "tenantService", tenantService);

        // 3. 构建 MockMvc
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /tenant/info - 成功")
    void getTenantInfo_Success() throws Exception {
        // 1. 准备响应
        TenantInfoResponse response = new TenantInfoResponse();
        response.setOrgId("org-001");
        response.setOrgName("测试组织");
        response.setOrgCode("TEST");
        response.setDescription("描述");
        response.setStatus(1);
        response.setCreateTime(new Timestamp(System.currentTimeMillis()));

        when(tenantService.getTenantInfo()).thenReturn(response);

        // 2. 调用并验证
        mockMvc.perform(get("/tenant/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orgId").value("org-001"))
                .andExpect(jsonPath("$.data.orgName").value("测试组织"))
                .andExpect(jsonPath("$.data.orgCode").value("TEST"))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(tenantService, times(1)).getTenantInfo();
    }

    @Test
    @DisplayName("GET /tenant/departments - 返回部门列表")
    void listDepartments_Success() throws Exception {
        // 1. 准备响应
        DepartmentVO dept1 = new DepartmentVO();
        dept1.setDeptId("dept-1");
        dept1.setDeptName("对公信贷部");
        dept1.setDeptCode("CREDIT");
        dept1.setSortOrder(1);
        dept1.setStatus(1);

        DepartmentVO dept2 = new DepartmentVO();
        dept2.setDeptId("dept-2");
        dept2.setDeptName("风险管理部");
        dept2.setDeptCode("RISK");
        dept2.setSortOrder(2);
        dept2.setStatus(1);

        when(tenantService.listDepartments()).thenReturn(List.of(dept1, dept2));

        // 2. 调用并验证
        mockMvc.perform(get("/tenant/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].deptId").value("dept-1"))
                .andExpect(jsonPath("$.data[0].deptName").value("对公信贷部"))
                .andExpect(jsonPath("$.data[1].deptId").value("dept-2"));

        verify(tenantService, times(1)).listDepartments();
    }

    @Test
    @DisplayName("GET /tenant/departments - 空列表")
    void listDepartments_EmptyList() throws Exception {
        when(tenantService.listDepartments()).thenReturn(List.of());

        mockMvc.perform(get("/tenant/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /tenant/business-lines - 返回业务线列表")
    void listBusinessLines_Success() throws Exception {
        // 1. 准备响应
        BusinessLineVO bl1 = new BusinessLineVO();
        bl1.setBusinessLineId("bl-1");
        bl1.setBusinessLineName("对公信贷");
        bl1.setBusinessLineCode("CREDIT");
        bl1.setSortOrder(1);
        bl1.setStatus(1);

        BusinessLineVO bl2 = new BusinessLineVO();
        bl2.setBusinessLineId("bl-2");
        bl2.setBusinessLineName("个人金融");
        bl2.setBusinessLineCode("RETAIL");
        bl2.setSortOrder(2);
        bl2.setStatus(1);

        when(tenantService.listBusinessLines()).thenReturn(List.of(bl1, bl2));

        // 2. 调用并验证
        mockMvc.perform(get("/tenant/business-lines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].businessLineId").value("bl-1"))
                .andExpect(jsonPath("$.data[0].businessLineName").value("对公信贷"))
                .andExpect(jsonPath("$.data[1].businessLineId").value("bl-2"));

        verify(tenantService, times(1)).listBusinessLines();
    }

    @Test
    @DisplayName("GET /tenant/business-lines - 空列表")
    void listBusinessLines_EmptyList() throws Exception {
        when(tenantService.listBusinessLines()).thenReturn(List.of());

        mockMvc.perform(get("/tenant/business-lines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}

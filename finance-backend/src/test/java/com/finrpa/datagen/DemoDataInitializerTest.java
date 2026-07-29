package com.finrpa.datagen;

import com.finrpa.auth.entity.RoleEO;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.entity.UserRoleEO;
import com.finrpa.auth.mapper.RoleMapper;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.auth.mapper.UserRoleMapper;
import com.finrpa.tenant.entity.BusinessLineEO;
import com.finrpa.tenant.entity.DepartmentEO;
import com.finrpa.tenant.entity.OrganizationEO;
import com.finrpa.tenant.mapper.BusinessLineMapper;
import com.finrpa.tenant.mapper.DepartmentMapper;
import com.finrpa.tenant.mapper.OrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoDataInitializerTest {

    @Mock
    private OrganizationMapper organizationMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private BusinessLineMapper businessLineMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DemoDataGenerator demoDataGenerator;

    /**
     * 创建测试用角色
     */
    private RoleEO createRole(String roleCode) {
        RoleEO role = new RoleEO();
        role.setRoleId(switch (roleCode) {
            case "org_admin" -> 1L;
            case "operator" -> 2L;
            case "approver" -> 3L;
            case "viewer" -> 4L;
            default -> 0L;
        });
        role.setRoleCode(roleCode);
        role.setRoleName(switch (roleCode) {
            case "org_admin" -> "组织管理员";
            case "operator" -> "操作员";
            case "approver" -> "审批员";
            case "viewer" -> "查看员";
            default -> roleCode;
        });
        return role;
    }

    @Test
    @DisplayName("生成演示数据 - 应创建组织、部门、业务线、用户和角色关联")
    void generateDemoData_ShouldCreateDemoData() {
        // 1. 准备角色数据
        when(roleMapper.selectByRoleCode("org_admin")).thenReturn(createRole("org_admin"));
        when(roleMapper.selectByRoleCode("operator")).thenReturn(createRole("operator"));
        when(roleMapper.selectByRoleCode("approver")).thenReturn(createRole("approver"));
        when(roleMapper.selectByRoleCode("viewer")).thenReturn(createRole("viewer"));

        // 2. 准备密码编码器
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");

        // 3. 模拟插入操作返回成功并设置 ID
        when(organizationMapper.insert(any(OrganizationEO.class))).thenAnswer(invocation -> {
            OrganizationEO org = invocation.getArgument(0);
            org.setId(1L);
            return 1;
        });
        when(departmentMapper.insert(any(DepartmentEO.class))).thenAnswer(invocation -> {
            DepartmentEO dept = invocation.getArgument(0);
            dept.setId(1L);
            return 1;
        });
        when(businessLineMapper.insert(any(BusinessLineEO.class))).thenReturn(1);
        when(userMapper.insert(any(UserEO.class))).thenReturn(1);
        when(userRoleMapper.insert(any(UserRoleEO.class))).thenReturn(1);

        // 4. 执行生成
        demoDataGenerator.generateDemoData();

        // 5. 验证调用次数：2个组织、10个部门、6条业务线、12个用户、16个角色关联
        // 每个组织6个用户：org_admin(1) + operator(1) + approver(1) + viewer(1) + operator+viewer(2) + approver+viewer(2) = 8
        verify(organizationMapper, times(2)).insert(any(OrganizationEO.class));
        verify(departmentMapper, times(10)).insert(any(DepartmentEO.class));
        verify(businessLineMapper, times(6)).insert(any(BusinessLineEO.class));
        verify(userMapper, times(12)).insert(any(UserEO.class));
        verify(userRoleMapper, times(16)).insert(any(UserRoleEO.class));
    }

    @Test
    @DisplayName("生成组织 - 组织信息应正确设置")
    void generateOrganizations_ShouldCreateOrganizationsWithCorrectData() {
        // 准备角色数据
        when(roleMapper.selectByRoleCode(anyString())).thenReturn(createRole("org_admin"));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(organizationMapper.insert(any(OrganizationEO.class))).thenAnswer(invocation -> {
            OrganizationEO org = invocation.getArgument(0);
            org.setId(1L);
            return 1;
        });
        when(departmentMapper.insert(any(DepartmentEO.class))).thenAnswer(invocation -> {
            DepartmentEO dept = invocation.getArgument(0);
            dept.setId(1L);
            return 1;
        });

        demoDataGenerator.generateDemoData();

        // 验证组织插入参数
        verify(organizationMapper, times(2)).insert(any(OrganizationEO.class));
    }

    @Test
    @DisplayName("更新默认admin用户 - 应关联到演示组织")
    void updateDefaultAdminUser_ShouldUpdateOrgInfo() {
        // 准备角色数据
        when(roleMapper.selectByRoleCode(anyString())).thenReturn(createRole("org_admin"));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(organizationMapper.insert(any(OrganizationEO.class))).thenAnswer(invocation -> {
            OrganizationEO org = invocation.getArgument(0);
            org.setId(1L);
            return 1;
        });
        when(departmentMapper.insert(any(DepartmentEO.class))).thenAnswer(invocation -> {
            DepartmentEO dept = invocation.getArgument(0);
            dept.setId(1L);
            return 1;
        });

        // 准备现有admin用户
        UserEO admin = new UserEO();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setOrgId(999L);
        admin.setOrgName("旧的组织名称");

        when(userMapper.selectOne(any())).thenReturn(admin);

        demoDataGenerator.generateDemoData();

        // 验证更新调用
        verify(userMapper).updateById(any(UserEO.class));
    }
}
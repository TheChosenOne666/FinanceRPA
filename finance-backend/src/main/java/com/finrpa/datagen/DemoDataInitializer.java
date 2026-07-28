package com.finrpa.datagen;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 演示数据初始化器
 *
 * <p>在应用启动时生成演示数据，包括组织、部门、业务线、用户和角色关联。
 * 仅在 dev 环境运行，且确保幂等性（不会重复生成）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
@Profile("dev")
public class DemoDataInitializer implements ApplicationRunner {

    /** 组织 Mapper */
    private final OrganizationMapper organizationMapper;

    /** 部门 Mapper */
    private final DepartmentMapper departmentMapper;

    /** 业务线 Mapper */
    private final BusinessLineMapper businessLineMapper;

    /** 用户 Mapper */
    private final UserMapper userMapper;

    /** 角色 Mapper */
    private final RoleMapper roleMapper;

    /** 用户-角色关联 Mapper */
    private final UserRoleMapper userRoleMapper;

    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

    /** 是否启用演示数据生成 */
    @Value("${demo.data.enabled:true}")
    private boolean demoDataEnabled;

    /** 演示数据组织编码前缀 */
    private static final String DEMO_ORG_CODE_PREFIX = "DEMO_";

    public DemoDataInitializer(
            OrganizationMapper organizationMapper,
            DepartmentMapper departmentMapper,
            BusinessLineMapper businessLineMapper,
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder) {
        this.organizationMapper = organizationMapper;
        this.departmentMapper = departmentMapper;
        this.businessLineMapper = businessLineMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 应用启动时执行演示数据初始化
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!demoDataEnabled) {
            log.info("演示数据生成已禁用");
            return;
        }

        // 检查是否已存在演示数据
        if (isDemoDataExists()) {
            log.info("演示数据已存在，跳过生成");
            return;
        }

        try {
            generateDemoData();
            log.info("演示数据生成完成");
        } catch (Exception e) {
            log.error("演示数据生成失败", e);
            throw new RuntimeException("演示数据生成失败", e);
        }
    }

    /**
     * 检查演示数据是否已存在
     *
     * @return 是否已存在演示数据
     */
    private boolean isDemoDataExists() {
        LambdaQueryWrapper<OrganizationEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(OrganizationEO::getOrgCode, DEMO_ORG_CODE_PREFIX)
                .eq(OrganizationEO::getDeleted, 0);
        return organizationMapper.selectCount(wrapper) > 0;
    }

    /**
     * 生成所有演示数据（事务保护）
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateDemoData() {
        // 1. 生成组织
        List<OrganizationEO> organizations = generateOrganizations();

        // 2. 为每个组织生成部门、业务线和用户
        for (OrganizationEO org : organizations) {
            generateDepartments(org);
            generateBusinessLines(org);
            generateUsersAndRoles(org);
        }

        // 3. 更新默认 admin 用户关联到演示组织
        updateDefaultAdminUser(organizations.get(0));

        log.info("演示数据生成完成：组织={}, 部门={}, 业务线={}, 用户={}",
                organizations.size(),
                departmentMapper.selectCount(null),
                businessLineMapper.selectCount(null),
                userMapper.selectCount(null));
    }

    /**
     * 生成演示组织
     *
     * @return 组织列表
     */
    private List<OrganizationEO> generateOrganizations() {
        List<OrganizationEO> organizations = new ArrayList<>();

        // 组织1：银河证券
        OrganizationEO org1 = new OrganizationEO();
        org1.setOrgId(UUID.randomUUID().toString());
        org1.setOrgName("银河证券");
        org1.setOrgCode(DEMO_ORG_CODE_PREFIX + "YHSEC");
        org1.setDescription("银河证券有限责任公司 - 演示组织");
        org1.setStatus(1);
        org1.setDeleted(0);
        org1.setCreateTime(Timestamp.valueOf(LocalDateTime.now()));
        org1.setUpdateTime(Timestamp.valueOf(LocalDateTime.now()));
        organizationMapper.insert(org1);
        organizations.add(org1);
        log.info("创建演示组织: {}", org1.getOrgName());

        // 组织2：星辰银行
        OrganizationEO org2 = new OrganizationEO();
        org2.setOrgId(UUID.randomUUID().toString());
        org2.setOrgName("星辰银行");
        org2.setOrgCode(DEMO_ORG_CODE_PREFIX + "XCBA");
        org2.setDescription("星辰银行股份有限公司 - 演示组织");
        org2.setStatus(1);
        org2.setDeleted(0);
        org2.setCreateTime(Timestamp.valueOf(LocalDateTime.now()));
        org2.setUpdateTime(Timestamp.valueOf(LocalDateTime.now()));
        organizationMapper.insert(org2);
        organizations.add(org2);
        log.info("创建演示组织: {}", org2.getOrgName());

        return organizations;
    }

    /**
     * 为指定组织生成部门（含层级关系）
     *
     * @param org 所属组织
     */
    private void generateDepartments(OrganizationEO org) {
        // 顶级部门：总行/总公司
        DepartmentEO topDept = new DepartmentEO();
        topDept.setDeptId(UUID.randomUUID().toString());
        topDept.setOrgId(org.getOrgId());
        topDept.setDeptName("总行");
        topDept.setDeptCode("HQ");
        topDept.setParentId(0L);
        topDept.setSortOrder(1);
        topDept.setStatus(1);
        topDept.setDeleted(0);
        departmentMapper.insert(topDept);

        // 二级部门：财务部
        DepartmentEO financeDept = new DepartmentEO();
        financeDept.setDeptId(UUID.randomUUID().toString());
        financeDept.setOrgId(org.getOrgId());
        financeDept.setDeptName("财务部");
        financeDept.setDeptCode("FIN");
        financeDept.setParentId(topDept.getId());
        financeDept.setSortOrder(2);
        financeDept.setStatus(1);
        financeDept.setDeleted(0);
        departmentMapper.insert(financeDept);

        // 二级部门：业务部
        DepartmentEO businessDept = new DepartmentEO();
        businessDept.setDeptId(UUID.randomUUID().toString());
        businessDept.setOrgId(org.getOrgId());
        businessDept.setDeptName("业务部");
        businessDept.setDeptCode("BUS");
        businessDept.setParentId(topDept.getId());
        businessDept.setSortOrder(3);
        businessDept.setStatus(1);
        businessDept.setDeleted(0);
        departmentMapper.insert(businessDept);

        // 二级部门：审批部
        DepartmentEO approvalDept = new DepartmentEO();
        approvalDept.setDeptId(UUID.randomUUID().toString());
        approvalDept.setOrgId(org.getOrgId());
        approvalDept.setDeptName("审批部");
        approvalDept.setDeptCode("APP");
        approvalDept.setParentId(topDept.getId());
        approvalDept.setSortOrder(4);
        approvalDept.setStatus(1);
        approvalDept.setDeleted(0);
        departmentMapper.insert(approvalDept);

        // 三级部门：财务结算科（属于财务部）
        DepartmentEO settleDept = new DepartmentEO();
        settleDept.setDeptId(UUID.randomUUID().toString());
        settleDept.setOrgId(org.getOrgId());
        settleDept.setDeptName("财务结算科");
        settleDept.setDeptCode("FIN_SETTLE");
        settleDept.setParentId(financeDept.getId());
        settleDept.setSortOrder(5);
        settleDept.setStatus(1);
        settleDept.setDeleted(0);
        departmentMapper.insert(settleDept);

        log.info("为组织 {} 创建了 {} 个部门", org.getOrgName(), 5);
    }

    /**
     * 为指定组织生成业务线
     *
     * @param org 所属组织
     */
    private void generateBusinessLines(OrganizationEO org) {
        BusinessLineEO bl1 = new BusinessLineEO();
        bl1.setBusinessLineId(UUID.randomUUID().toString());
        bl1.setOrgId(org.getOrgId());
        bl1.setBusinessLineName("证券交易");
        bl1.setBusinessLineCode("SEC_TRADING");
        bl1.setDescription("股票、基金等证券交易业务");
        bl1.setSortOrder(1);
        bl1.setStatus(1);
        bl1.setDeleted(0);
        businessLineMapper.insert(bl1);

        BusinessLineEO bl2 = new BusinessLineEO();
        bl2.setBusinessLineId(UUID.randomUUID().toString());
        bl2.setOrgId(org.getOrgId());
        bl2.setBusinessLineName("资金清算");
        bl2.setBusinessLineCode("FUND_SETTLE");
        bl2.setDescription("资金结算与清算业务");
        bl2.setSortOrder(2);
        bl2.setStatus(1);
        bl2.setDeleted(0);
        businessLineMapper.insert(bl2);

        BusinessLineEO bl3 = new BusinessLineEO();
        bl3.setBusinessLineId(UUID.randomUUID().toString());
        bl3.setOrgId(org.getOrgId());
        bl3.setBusinessLineName("合规审批");
        bl3.setBusinessLineCode("COMPLIANCE");
        bl3.setDescription("合规审查与业务审批");
        bl3.setSortOrder(3);
        bl3.setStatus(1);
        bl3.setDeleted(0);
        businessLineMapper.insert(bl3);

        log.info("为组织 {} 创建了 {} 条业务线", org.getOrgName(), 3);
    }

    /**
     * 为指定组织生成用户并关联角色
     *
     * @param org 所属组织
     */
    private void generateUsersAndRoles(OrganizationEO org) {
        // 查询所需角色
        RoleEO orgAdminRole = roleMapper.selectByRoleCode("org_admin");
        RoleEO operatorRole = roleMapper.selectByRoleCode("operator");
        RoleEO approverRole = roleMapper.selectByRoleCode("approver");
        RoleEO viewerRole = roleMapper.selectByRoleCode("viewer");

        // 用户1：组织管理员
        createUserWithRoles(org, "admin_" + org.getOrgCode().toLowerCase(), "张三", "财务部",
                "zhangsan@demo.com", "13800138001",
                List.of(orgAdminRole));

        // 用户2：操作员（财务结算科）
        createUserWithRoles(org, "operator_" + org.getOrgCode().toLowerCase(), "李四", "财务结算科",
                "lisi@demo.com", "13800138002",
                List.of(operatorRole));

        // 用户3：审批员（审批部）
        createUserWithRoles(org, "approver_" + org.getOrgCode().toLowerCase(), "王五", "审批部",
                "wangwu@demo.com", "13800138003",
                List.of(approverRole));

        // 用户4：查看员（业务部）
        createUserWithRoles(org, "viewer_" + org.getOrgCode().toLowerCase(), "赵六", "业务部",
                "zhaoliu@demo.com", "13800138004",
                List.of(viewerRole));

        // 用户5：操作员+查看员（业务部）
        createUserWithRoles(org, "operator_viewer_" + org.getOrgCode().toLowerCase(), "孙七", "业务部",
                "sunqi@demo.com", "13800138005",
                List.of(operatorRole, viewerRole));

        // 用户6：审批员+查看员（审批部）
        createUserWithRoles(org, "approver_viewer_" + org.getOrgCode().toLowerCase(), "周八", "审批部",
                "zhouba@demo.com", "13800138006",
                List.of(approverRole, viewerRole));

        log.info("为组织 {} 创建了 {} 个用户", org.getOrgName(), 6);
    }

    /**
     * 创建用户并关联角色
     *
     * @param org      所属组织
     * @param username 用户名
     * @param realName 真实姓名
     * @param deptName 部门名称
     * @param email    邮箱
     * @param phone    手机号
     * @param roles    角色列表
     */
    private void createUserWithRoles(OrganizationEO org, String username, String realName, String deptName,
                                     String email, String phone, List<RoleEO> roles) {
        // 创建用户
        UserEO user = new UserEO();
        user.setUserId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456")); // 默认密码
        user.setRealName(realName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setOrgId(org.getOrgId());
        user.setOrgName(org.getOrgName());
        user.setDeptName(deptName);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        // 关联角色
        for (RoleEO role : roles) {
            UserRoleEO userRole = new UserRoleEO();
            userRole.setUserId(user.getUserId());
            userRole.setRoleId(role.getRoleId());
            userRole.setDeleted(0);
            userRoleMapper.insert(userRole);
        }
    }

    /**
     * 更新默认 admin 用户关联到演示组织
     *
     * @param org 演示组织
     */
    private void updateDefaultAdminUser(OrganizationEO org) {
        LambdaQueryWrapper<UserEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEO::getUsername, "admin")
                .eq(UserEO::getDeleted, 0);
        UserEO adminUser = userMapper.selectOne(wrapper);

        if (adminUser != null) {
            adminUser.setOrgId(org.getOrgId());
            adminUser.setOrgName(org.getOrgName());
            userMapper.updateById(adminUser);
            log.info("更新默认 admin 用户关联到演示组织: {}", org.getOrgName());
        }
    }
}
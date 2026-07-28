package com.finrpa.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.finrpa.tenant.service.TenantService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户服务实现
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class TenantServiceImpl implements TenantService {

    /** 组织 Mapper */
    @Resource
    private OrganizationMapper organizationMapper;

    /** 部门 Mapper */
    @Resource
    private DepartmentMapper departmentMapper;

    /** 业务线 Mapper */
    @Resource
    private BusinessLineMapper businessLineMapper;

    /**
     * 获取当前请求所属组织信息
     *
     * @return 租户信息响应
     */
    @Override
    public TenantInfoResponse getTenantInfo() {
        // 1. 从 TenantContext 获取当前组织 ID
        String orgId = getCurrentOrgId();

        // 2. 查询组织信息（enterprise_organization 表已在忽略清单中，手动按 org_id 查询）
        OrganizationEO organization = organizationMapper.selectByOrgId(orgId);
        if (organization == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "组织不存在");
        }

        // 3. 构建响应
        TenantInfoResponse response = new TenantInfoResponse();
        response.setOrgId(organization.getOrgId());
        response.setOrgName(organization.getOrgName());
        response.setOrgCode(organization.getOrgCode());
        response.setDescription(organization.getDescription());
        response.setStatus(organization.getStatus());
        response.setCreateTime(organization.getCreateTime());
        return response;
    }

    /**
     * 获取当前请求所属组织下的部门列表
     *
     * @return 部门视图列表
     */
    @Override
    public List<DepartmentVO> listDepartments() {
        // 1. 从 TenantContext 获取当前组织 ID
        String orgId = getCurrentOrgId();

        // 2. 构造查询条件：仅查未删除且启用的部门，按 sortOrder 升序
        LambdaQueryWrapper<DepartmentEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartmentEO::getOrgId, orgId)
                .eq(DepartmentEO::getDeleted, 0)
                .eq(DepartmentEO::getStatus, 1)
                .orderByAsc(DepartmentEO::getSortOrder);

        // 3. 查询（TenantLineHandler 会自动追加 WHERE org_id = ?，此处显式条件用于双重保险）
        List<DepartmentEO> departments = departmentMapper.selectList(wrapper);

        // 4. 转换为 VO
        return departments.stream()
                .map(this::convertToDepartmentVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取当前请求所属组织下的业务线列表
     *
     * @return 业务线视图列表
     */
    @Override
    public List<BusinessLineVO> listBusinessLines() {
        // 1. 从 TenantContext 获取当前组织 ID
        String orgId = getCurrentOrgId();

        // 2. 构造查询条件：仅查未删除且启用的业务线，按 sortOrder 升序
        LambdaQueryWrapper<BusinessLineEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessLineEO::getOrgId, orgId)
                .eq(BusinessLineEO::getDeleted, 0)
                .eq(BusinessLineEO::getStatus, 1)
                .orderByAsc(BusinessLineEO::getSortOrder);

        // 3. 查询
        List<BusinessLineEO> businessLines = businessLineMapper.selectList(wrapper);

        // 4. 转换为 VO
        return businessLines.stream()
                .map(this::convertToBusinessLineVO)
                .collect(Collectors.toList());
    }

    // region 私有工具方法

    /**
     * 从 TenantContext 获取当前组织 ID；为空时抛异常
     *
     * @return 当前组织 ID
     */
    private String getCurrentOrgId() {
        String orgId = TenantContext.getOrgId();
        if (orgId == null || orgId.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带组织信息");
        }
        return orgId;
    }

    /**
     * 将部门实体转换为部门 VO
     *
     * @param entity 部门实体
     * @return 部门 VO
     */
    private DepartmentVO convertToDepartmentVO(DepartmentEO entity) {
        DepartmentVO vo = new DepartmentVO();
        vo.setDeptId(entity.getDeptId());
        vo.setDeptName(entity.getDeptName());
        vo.setDeptCode(entity.getDeptCode());
        vo.setParentId(entity.getParentId());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    /**
     * 将业务线实体转换为业务线 VO
     *
     * @param entity 业务线实体
     * @return 业务线 VO
     */
    private BusinessLineVO convertToBusinessLineVO(BusinessLineEO entity) {
        BusinessLineVO vo = new BusinessLineVO();
        vo.setBusinessLineId(entity.getBusinessLineId());
        vo.setBusinessLineName(entity.getBusinessLineName());
        vo.setBusinessLineCode(entity.getBusinessLineCode());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    // endregion
}

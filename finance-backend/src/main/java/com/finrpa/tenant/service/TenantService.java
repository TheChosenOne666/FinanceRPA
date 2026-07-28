package com.finrpa.tenant.service;

import com.finrpa.tenant.dto.response.BusinessLineVO;
import com.finrpa.tenant.dto.response.DepartmentVO;
import com.finrpa.tenant.dto.response.TenantInfoResponse;

import java.util.List;

/**
 * 租户服务接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface TenantService {

    /**
     * 获取当前请求所属组织信息
     *
     * @return 租户信息响应
     */
    TenantInfoResponse getTenantInfo();

    /**
     * 获取当前请求所属组织下的部门列表
     *
     * @return 部门视图列表
     */
    List<DepartmentVO> listDepartments();

    /**
     * 获取当前请求所属组织下的业务线列表
     *
     * @return 业务线视图列表
     */
    List<BusinessLineVO> listBusinessLines();
}

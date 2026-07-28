package com.finrpa.tenant.controller;

import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.dto.response.BusinessLineVO;
import com.finrpa.tenant.dto.response.DepartmentVO;
import com.finrpa.tenant.dto.response.TenantInfoResponse;
import com.finrpa.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/tenant")
@Tag(name = "租户", description = "组织信息、部门、业务线相关接口")
public class TenantController {

    /** 租户服务 */
    @Resource
    private TenantService tenantService;

    // region 租户信息

    /**
     * 获取当前请求所属组织信息
     *
     * @return 租户信息响应
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前组织信息", description = "从 JWT 上下文解析 orgId 后查询组织详情")
    public BaseResponse<TenantInfoResponse> getTenantInfo() {
        TenantInfoResponse response = tenantService.getTenantInfo();
        return ResultUtils.success(response);
    }

    // endregion

    // region 部门

    /**
     * 获取当前组织下的部门列表
     *
     * @return 部门视图列表
     */
    @GetMapping("/departments")
    @Operation(summary = "获取部门列表", description = "按 sortOrder 升序返回当前组织下启用的部门列表")
    public BaseResponse<List<DepartmentVO>> listDepartments() {
        List<DepartmentVO> list = tenantService.listDepartments();
        return ResultUtils.success(list);
    }

    // endregion

    // region 业务线

    /**
     * 获取当前组织下的业务线列表
     *
     * @return 业务线视图列表
     */
    @GetMapping("/business-lines")
    @Operation(summary = "获取业务线列表", description = "按 sortOrder 升序返回当前组织下启用的业务线列表")
    public BaseResponse<List<BusinessLineVO>> listBusinessLines() {
        List<BusinessLineVO> list = tenantService.listBusinessLines();
        return ResultUtils.success(list);
    }

    // endregion
}

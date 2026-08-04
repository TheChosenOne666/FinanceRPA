package com.finrpa.approval.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.approval.dto.request.ApprovalRouteConfigAddRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigQueryRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalRouteConfigVO;
import com.finrpa.approval.service.ApprovalRouteConfigService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批人映射配置控制器（P1 RSK-3）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/approval-routes}）：
 * <ul>
 *   <li>GET /approval-routes —— 分页查询审批人映射列表（按当前请求组织过滤）</li>
 *   <li>POST /approval-routes —— 新增审批人映射（风险等级 × 业务线 → 审批人）</li>
 *   <li>PUT /approval-routes/{configId} —— 更新映射配置（审批人 / 启用状态等）</li>
 *   <li>DELETE /approval-routes/{configId} —— 删除映射配置</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/approval-routes")
@Tag(name = "审批人映射配置", description = "按 风险等级 × 业务线 → 审批人 路由（设置页风控配置）")
public class ApprovalRouteConfigController {

    /** 审批人映射配置服务 */
    @Resource
    private ApprovalRouteConfigService approvalRouteConfigService;

    // region 查询

    /**
     * 分页查询审批人映射列表
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "审批人映射列表", description = "分页查询审批人映射，按当前请求组织过滤")
    public BaseResponse<IPage<ApprovalRouteConfigVO>> listConfigs(
            ApprovalRouteConfigQueryRequest queryRequest) {
        Long orgId = getCurrentOrgId();
        IPage<ApprovalRouteConfigVO> page = approvalRouteConfigService.listConfigs(queryRequest, orgId);
        return ResultUtils.success(page);
    }

    // endregion

    // region 新增

    /**
     * 新增审批人映射
     *
     * @param request 新增请求
     * @return 新建的配置业务 ID
     */
    @PostMapping
    @Operation(summary = "新增审批人映射", description = "新增「风险等级 × 业务线 → 审批人」映射规则")
    public BaseResponse<Long> addConfig(@RequestBody ApprovalRouteConfigAddRequest request) {
        Long orgId = getCurrentOrgId();
        Long configId = approvalRouteConfigService.addConfig(orgId, request);
        return ResultUtils.success(configId);
    }

    // endregion

    // region 更新

    /**
     * 更新审批人映射配置
     *
     * @param configId 配置业务 ID
     * @param request  更新请求
     * @return 操作结果
     */
    @PutMapping("/{configId}")
    @Operation(summary = "更新审批人映射", description = "更新审批人 / 启用状态等")
    public BaseResponse<Boolean> updateConfig(@PathVariable Long configId,
                                                @RequestBody ApprovalRouteConfigUpdateRequest request) {
        boolean success = approvalRouteConfigService.updateConfig(configId, request);
        return ResultUtils.success(success);
    }

    // endregion

    // region 删除

    /**
     * 删除审批人映射配置
     *
     * @param configId 配置业务 ID
     * @return 操作结果
     */
    @DeleteMapping("/{configId}")
    @Operation(summary = "删除审批人映射", description = "逻辑删除审批人映射配置")
    public BaseResponse<Boolean> deleteConfig(@PathVariable Long configId) {
        boolean success = approvalRouteConfigService.deleteConfig(configId);
        return ResultUtils.success(success);
    }

    // endregion

    /**
     * 从 TenantContext 获取当前组织 ID
     *
     * @return 当前组织 ID
     */
    private Long getCurrentOrgId() {
        String orgId = TenantContext.getOrgId();
        if (orgId == null || orgId.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带组织信息");
        }
        return Long.parseLong(orgId);
    }
}

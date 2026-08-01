package com.finrpa.approval.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.agent.constant.AgentConstant;
import com.finrpa.approval.dto.request.ApprovalActionRequest;
import com.finrpa.approval.dto.request.ApprovalQueryRequest;
import com.finrpa.approval.dto.response.ApprovalRequestVO;
import com.finrpa.approval.service.ApprovalService;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批管理控制器（对外 API）（M6.3）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/approvals}）：
 * <ul>
 *   <li>GET /approvals —— 分页查询审批列表（支持状态/路由/风险等级筛选）</li>
 *   <li>GET /approvals/{approvalId} —— 查询审批详情</li>
 *   <li>POST /approvals/{approvalId}/approve —— 审批通过</li>
 *   <li>POST /approvals/{approvalId}/reject —— 审批拒绝</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/approvals")
@Tag(name = "审批管理", description = "审批列表查询 + 通过/拒绝操作")
public class ApprovalController {

    /** 审批服务 */
    @Resource
    private ApprovalService approvalService;

    // region 查询

    /**
     * 分页查询审批列表
     *
     * @param queryRequest 查询请求
     * @param httpRequest  HTTP 请求（用于获取组织 ID）
     * @return 审批分页列表
     */
    @GetMapping
    @Operation(summary = "审批列表", description = "分页查询审批列表，支持状态/路由/风险等级筛选")
    public BaseResponse<IPage<ApprovalRequestVO>> listApprovals(ApprovalQueryRequest queryRequest,
                                                                  HttpServletRequest httpRequest) {
        // 从登录上下文获取 orgId
        String orgIdStr = TenantContext.getOrgId();
        if (orgIdStr != null) {
            queryRequest.setOrgId(Long.parseLong(orgIdStr));
        }
        return ResultUtils.success(approvalService.listApprovals(queryRequest));
    }

    /**
     * 查询审批详情
     *
     * @param approvalId 审批单 ID
     * @return 审批详情
     */
    @GetMapping("/{approvalId}")
    @Operation(summary = "审批详情", description = "按 ID 查询审批详情")
    public BaseResponse<ApprovalRequestVO> getApprovalDetail(@PathVariable Long approvalId) {
        return ResultUtils.success(approvalService.getApprovalDetail(approvalId));
    }

    // endregion

    // region 审批操作

    /**
     * 审批通过
     *
     * @param approvalId 审批单 ID
     * @param request    审批操作请求（含通过理由）
     * @param httpRequest HTTP 请求（用于获取审批人 ID）
     * @return 更新后的审批详情
     */
    @PostMapping("/{approvalId}/approve")
    @Operation(summary = "审批通过", description = "审批通过，唤醒等待中的任务触发流程")
    public BaseResponse<ApprovalRequestVO> approve(@PathVariable Long approvalId,
                                                     @RequestBody(required = false) ApprovalActionRequest request,
                                                     HttpServletRequest httpRequest) {
        Long approverId = getUserIdFromRequest(httpRequest);
        String reason = request != null ? request.getReason() : null;
        approvalService.approve(approvalId, approverId, reason);
        return ResultUtils.success(approvalService.getApprovalDetail(approvalId));
    }

    /**
     * 审批拒绝
     *
     * @param approvalId 审批单 ID
     * @param request    审批操作请求（含拒绝理由）
     * @param httpRequest HTTP 请求（用于获取审批人 ID）
     * @return 更新后的审批详情
     */
    @PostMapping("/{approvalId}/reject")
    @Operation(summary = "审批拒绝", description = "审批拒绝，终止任务触发流程")
    public BaseResponse<ApprovalRequestVO> reject(@PathVariable Long approvalId,
                                                    @RequestBody(required = false) ApprovalActionRequest request,
                                                    HttpServletRequest httpRequest) {
        Long approverId = getUserIdFromRequest(httpRequest);
        String reason = request != null ? request.getReason() : null;
        approvalService.reject(approvalId, approverId, reason);
        return ResultUtils.success(approvalService.getApprovalDetail(approvalId));
    }

    // endregion

    // region 私有方法

    /**
     * 从 HTTP 请求属性获取当前用户 ID
     *
     * @param httpRequest HTTP 请求
     * @return 用户 ID
     */
    private Long getUserIdFromRequest(HttpServletRequest httpRequest) {
        String userIdStr = (String) httpRequest.getAttribute(AgentConstant.USER_ID_REQUEST_ATTR);
        ThrowUtils.throwIf(userIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "无法获取当前用户信息");
        return Long.parseLong(userIdStr);
    }

    // endregion
}

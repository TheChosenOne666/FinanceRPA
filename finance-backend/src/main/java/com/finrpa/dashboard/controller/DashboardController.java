package com.finrpa.dashboard.controller;

import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.dashboard.dto.response.ApprovalStatVO;
import com.finrpa.dashboard.dto.response.BusinessLineStatVO;
import com.finrpa.dashboard.dto.response.CostStatVO;
import com.finrpa.dashboard.dto.response.ErrorTypeStatVO;
import com.finrpa.dashboard.dto.response.OverviewVO;
import com.finrpa.dashboard.dto.response.TrendsVO;
import com.finrpa.dashboard.service.DashboardService;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营大屏控制器（M8.1）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api}）：
 * <ul>
 *   <li>GET /v1/dashboard/overview —— 概览（任务/性能/LLM/人工/风险 五类汇总）</li>
 *   <li>GET /v1/dashboard/trends —— 趋势（任务量 + 成本按日聚合）</li>
 *   <li>GET /v1/dashboard/business-lines —— 业务线分布 + 成功率</li>
 *   <li>GET /v1/dashboard/errors —— 错误类型分布 Top 10</li>
 *   <li>GET /v1/dashboard/costs —— LLM 成本统计（按模型）</li>
 *   <li>GET /v1/dashboard/approvals —— 审批统计（响应时长/超时数）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/v1/dashboard")
@Tag(name = "运营大屏", description = "运营大屏统计指标查询（Redis 缓存，任务终态主动刷新）")
public class DashboardController {

    /** 大屏服务 */
    @Resource
    private DashboardService dashboardService;

    // region 查询端点

    /**
     * 获取概览指标（任务 / 性能 / LLM / 人工 / 风险 五类汇总）
     *
     * @return 概览 VO
     */
    @GetMapping("/overview")
    @Operation(summary = "概览指标", description = "任务总数/成功率/性能/LLM 成本/接管队列/风险等级分布")
    public BaseResponse<OverviewVO> getOverview() {
        Long orgId = currentOrgId();
        return ResultUtils.success(dashboardService.getOverview(orgId));
    }

    /**
     * 获取趋势指标（任务量 + 成本按日聚合）
     *
     * @param days 天数（最近 N 天，默认 7，最大 90）
     * @return 趋势 VO
     */
    @GetMapping("/trends")
    @Operation(summary = "趋势指标", description = "按日聚合的任务量与 LLM 成本趋势，供折线图展示")
    public BaseResponse<TrendsVO> getTrends(
            @Parameter(description = "最近 N 天，默认 7，最大 90")
            @RequestParam(required = false) Integer days) {
        Long orgId = currentOrgId();
        return ResultUtils.success(dashboardService.getTrends(orgId, days));
    }

    /**
     * 获取各业务线任务分布 + 成功率
     *
     * @return 业务线统计列表
     */
    @GetMapping("/business-lines")
    @Operation(summary = "业务线分布", description = "各业务线任务数 + 成功率对比")
    public BaseResponse<List<BusinessLineStatVO>> getBusinessLines() {
        Long orgId = currentOrgId();
        return ResultUtils.success(dashboardService.getBusinessLineStats(orgId));
    }

    /**
     * 获取错误类型分布 Top 10
     *
     * @return 错误类型统计列表
     */
    @GetMapping("/errors")
    @Operation(summary = "错误类型分布", description = "失败操作类型分布 Top 10，供饼图展示")
    public BaseResponse<List<ErrorTypeStatVO>> getErrors() {
        Long orgId = currentOrgId();
        return ResultUtils.success(dashboardService.getErrorTypeStats(orgId));
    }

    /**
     * 获取 LLM 成本统计（含按模型维度）
     *
     * @return 成本统计 VO
     */
    @GetMapping("/costs")
    @Operation(summary = "LLM 成本统计", description = "LLM 调用次数/总成本/token/缓存命中率 + 按模型维度成本")
    public BaseResponse<CostStatVO> getCosts() {
        Long orgId = currentOrgId();
        return ResultUtils.success(dashboardService.getCosts(orgId));
    }

    /**
     * 获取审批统计（响应时长 / 超时数）
     *
     * @return 审批统计 VO
     */
    @GetMapping("/approvals")
    @Operation(summary = "审批统计", description = "审批总数/通过/拒绝/超时/待处理 + 平均响应时长")
    public BaseResponse<ApprovalStatVO> getApprovals() {
        Long orgId = currentOrgId();
        return ResultUtils.success(dashboardService.getApprovals(orgId));
    }

    // endregion

    // region 私有方法

    /**
     * 从登录上下文获取当前组织 ID
     *
     * @return 组织 ID
     */
    private Long currentOrgId() {
        String orgIdStr = TenantContext.getOrgId();
        ThrowUtils.throwIf(orgIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "租户上下文未设置");
        return Long.parseLong(orgIdStr);
    }

    // endregion
}

package com.finrpa.llm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.llm.dto.request.LlmCallRecordQueryRequest;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
import com.finrpa.llm.dto.response.LlmCallDailyTrendVO;
import com.finrpa.llm.dto.response.LlmCallRecordVO;
import com.finrpa.llm.dto.response.LlmCallStatsVO;
import com.finrpa.llm.service.LlmCallLogService;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * LLM 调用统计控制器（对外 API）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/llm}）：
 * <ul>
 *   <li>GET /llm/calls/stats —— 查询 LLM 调用统计（按时间/模型/任务/业务线维度，含环比趋势）</li>
 *   <li>GET /llm/calls —— 分页查询 LLM 调用记录（P3 ai-monitoring 原型对齐）</li>
 *   <li>GET /llm/calls/daily-trend —— 查询按日聚合趋势（7 日折线图）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/llm")
@Tag(name = "LLM 调用统计", description = "LLM 调用记录统计与成本分析")
public class LlmCallLogController {

    /** LLM 调用记录服务 */
    @Resource
    private LlmCallLogService llmCallLogService;

    /**
     * 查询 LLM 调用统计
     *
     * <p>支持按时间范围、模型、任务、业务线维度筛选。组织 ID 从 JWT 上下文自动获取。
     * 若提供完整时间范围，自动计算与等长上一周期的环比趋势。</p>
     *
     * @param queryRequest 统计查询请求（startTime / endTime / model / taskId / businessLineId 均可选）
     * @return 聚合统计结果（含趋势字段）
     */
    @GetMapping("/calls/stats")
    @Operation(summary = "LLM 调用统计", description = "按时间/模型/任务/业务线维度查询 LLM 调用次数、成本、缓存命中率等，含环比趋势")
    public BaseResponse<LlmCallStatsVO> getCallStats(LlmCallStatsQueryRequest queryRequest) {
        // 1. 从租户上下文获取 orgId
        Long orgId = getCurrentOrgId();

        // 2. 查询统计
        LlmCallStatsVO stats = llmCallLogService.getStats(queryRequest, orgId);
        return ResultUtils.success(stats);
    }

    /**
     * 分页查询 LLM 调用记录（P3 ai-monitoring 原型对齐：调用记录列表）
     *
     * <p>按 call_time 倒序返回单条调用记录，支持按时间/模型/任务/业务线/缓存命中筛选。</p>
     *
     * @param queryRequest 分页查询请求
     * @return 分页结果
     */
    @GetMapping("/calls")
    @Operation(summary = "LLM 调用记录列表", description = "分页查询 LLM 调用记录，按时间倒序，用于监控页底部调用记录表")
    public BaseResponse<IPage<LlmCallRecordVO>> listCallRecords(LlmCallRecordQueryRequest queryRequest) {
        Long orgId = getCurrentOrgId();
        IPage<LlmCallRecordVO> page = llmCallLogService.listCallRecords(queryRequest, orgId);
        return ResultUtils.success(page);
    }

    /**
     * 查询按日聚合趋势（P3 ai-monitoring 原型对齐：成本趋势 7 日折线图）
     *
     * <p>若未提供时间范围，默认查最近 7 天。返回按日期升序的每日聚合数据。</p>
     *
     * @param queryRequest 统计查询请求（用 startTime/endTime/businessLineId 筛选）
     * @return 按日期升序的每日聚合数据列表
     */
    @GetMapping("/calls/daily-trend")
    @Operation(summary = "LLM 调用按日趋势", description = "按日聚合 LLM 调用次数、成本、平均耗时，用于成本趋势折线图")
    public BaseResponse<List<LlmCallDailyTrendVO>> getDailyTrend(LlmCallStatsQueryRequest queryRequest) {
        Long orgId = getCurrentOrgId();
        List<LlmCallDailyTrendVO> trend = llmCallLogService.getDailyTrend(queryRequest, orgId);
        return ResultUtils.success(trend);
    }

    // region 私有方法

    /**
     * 从租户上下文获取当前组织 ID
     *
     * @return 组织 ID
     */
    private Long getCurrentOrgId() {
        String orgIdStr = TenantContext.getOrgId();
        ThrowUtils.throwIf(orgIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "无法获取当前组织信息");
        return Long.parseLong(orgIdStr);
    }

    // endregion
}

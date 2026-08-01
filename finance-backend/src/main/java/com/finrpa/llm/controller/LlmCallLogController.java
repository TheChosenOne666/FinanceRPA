package com.finrpa.llm.controller;

import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
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

/**
 * LLM 调用统计控制器（对外 API）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/llm}）：
 * <ul>
 *   <li>GET /llm/calls/stats —— 查询 LLM 调用统计（按时间/模型/任务维度）</li>
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
     * <p>支持按时间范围、模型、任务维度筛选。组织 ID 从 JWT 上下文自动获取。</p>
     *
     * @param queryRequest 统计查询请求（startTime / endTime / model / taskId 均可选）
     * @return 聚合统计结果
     */
    @GetMapping("/calls/stats")
    @Operation(summary = "LLM 调用统计", description = "按时间/模型/任务维度查询 LLM 调用次数、成本、缓存命中率等")
    public BaseResponse<LlmCallStatsVO> getCallStats(LlmCallStatsQueryRequest queryRequest) {
        // 1. 从租户上下文获取 orgId
        String orgIdStr = TenantContext.getOrgId();
        ThrowUtils.throwIf(orgIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "无法获取当前组织信息");
        Long orgId = Long.parseLong(orgIdStr);

        // 2. 查询统计
        LlmCallStatsVO stats = llmCallLogService.getStats(queryRequest, orgId);
        return ResultUtils.success(stats);
    }
}

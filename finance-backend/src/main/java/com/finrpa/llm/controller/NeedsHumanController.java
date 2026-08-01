package com.finrpa.llm.controller;

import com.finrpa.agent.constant.AgentConstant;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.llm.dto.request.NeedsHumanQueryRequest;
import com.finrpa.llm.dto.request.NeedsHumanResolveRequest;
import com.finrpa.llm.dto.response.NeedsHumanQueueVO;
import com.finrpa.llm.service.NeedsHumanService;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * NEEDS_HUMAN 队列控制器（对外 API）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/llm}）：
 * <ul>
 *   <li>GET /llm/needs-human —— 分页查询 NEEDS_HUMAN 队列</li>
 *   <li>GET /llm/needs-human/{queueId} —— 查询事件详情</li>
 *   <li>POST /llm/needs-human/{queueId}/resolve —— 处置事件（skip/manual/abort）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/llm/needs-human")
@Tag(name = "NEEDS_HUMAN 队列", description = "LLM 调用失败后的人工介入队列管理")
public class NeedsHumanController {

    /** NEEDS_HUMAN 队列服务 */
    @Resource
    private NeedsHumanService needsHumanService;

    /**
     * 分页查询 NEEDS_HUMAN 队列
     *
     * @param queryRequest 查询请求（含分页参数 + 可选 status / taskId 筛选）
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询 NEEDS_HUMAN 队列", description = "按状态、任务 ID 筛选 NEEDS_HUMAN 事件")
    public BaseResponse<java.util.List<NeedsHumanQueueVO>> listNeedsHuman(NeedsHumanQueryRequest queryRequest) {
        // 1. 从租户上下文获取 orgId
        String orgIdStr = TenantContext.getOrgId();
        ThrowUtils.throwIf(orgIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "无法获取当前组织信息");
        Long orgId = Long.parseLong(orgIdStr);

        // 2. 查询
        var page = needsHumanService.listNeedsHuman(queryRequest, orgId);
        return ResultUtils.success(page.getRecords());
    }

    /**
     * 查询 NEEDS_HUMAN 事件详情
     *
     * @param queueId 队列业务 ID
     * @return 事件详情
     */
    @GetMapping("/{queueId}")
    @Operation(summary = "查询 NEEDS_HUMAN 事件详情", description = "查看事件详情（含 LLM 原始输出、校验错误）")
    public BaseResponse<NeedsHumanQueueVO> getNeedsHumanDetail(@PathVariable Long queueId) {
        // 1. 从租户上下文获取 orgId
        String orgIdStr = TenantContext.getOrgId();
        ThrowUtils.throwIf(orgIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "无法获取当前组织信息");
        Long orgId = Long.parseLong(orgIdStr);

        // 2. 查询详情
        NeedsHumanQueueVO vo = needsHumanService.getNeedsHumanDetail(queueId, orgId);
        return ResultUtils.success(vo);
    }

    /**
     * 处置 NEEDS_HUMAN 事件
     *
     * <p>操作员选择处置动作：
     * <ul>
     *   <li>skip —— 跳过当前子任务，续跑任务</li>
     *   <li>manual —— 人工已处理，续跑任务</li>
     *   <li>abort —— 终止任务</li>
     * </ul>
     * </p>
     *
     * @param queueId        队列业务 ID
     * @param resolveRequest 处置请求
     * @param httpRequest    HTTP 请求（用于获取当前用户 ID）
     * @return 操作结果
     */
    @PostMapping("/{queueId}/resolve")
    @Operation(summary = "处置 NEEDS_HUMAN 事件", description = "操作员选择 skip/manual/abort 处置事件")
    public BaseResponse<Boolean> resolveNeedsHuman(
            @PathVariable Long queueId,
            @RequestBody NeedsHumanResolveRequest resolveRequest,
            HttpServletRequest httpRequest) {
        // 1. 从租户上下文获取 orgId
        String orgIdStr = TenantContext.getOrgId();
        ThrowUtils.throwIf(orgIdStr == null, ErrorCode.NOT_LOGIN_ERROR, "无法获取当前组织信息");
        Long orgId = Long.parseLong(orgIdStr);

        // 2. 从 request attribute 获取当前用户 ID
        String userIdStr = (String) httpRequest.getAttribute(AgentConstant.USER_ID_REQUEST_ATTR);
        Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;

        // 3. 处置
        boolean success = needsHumanService.resolveNeedsHuman(queueId, resolveRequest, userId, orgId);
        return ResultUtils.success(success);
    }
}

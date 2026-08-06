package com.finrpa.batch.controller;

import com.finrpa.agent.constant.AgentConstant;
import com.finrpa.batch.dto.BatchTaskRequest;
import com.finrpa.batch.dto.BatchTaskResultVO;
import com.finrpa.batch.service.BatchTaskService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量任务控制器（数据驱动入口）
 *
 * <p>对外端点（访问前缀 {@code /api/batch-tasks}）：
 * <ul>
 *   <li>POST /batch-tasks —— 根据 CSV/粘贴数据或外部业务系统表，批量生成工作流任务</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/batch-tasks")
@Tag(name = "批量任务", description = "数据驱动批量生成工作流任务")
public class BatchTaskController {

    @Resource
    private BatchTaskService batchTaskService;

    /**
     * 创建批量任务
     *
     * @param request     批量请求（workflowId + columnMapping + rows 或 externalQuery）
     * @param httpRequest HTTP 请求（获取当前用户上下文）
     * @return 批量执行结果（含每条明细）
     */
    @PostMapping
    @Operation(summary = "批量创建任务", description = "将一组用户数据映射为同一模板参数，逐条生成任务")
    public BaseResponse<BatchTaskResultVO> createBatch(@RequestBody BatchTaskRequest request,
                                                       HttpServletRequest httpRequest) {
        String orgIdStr = TenantContext.getOrgId();
        String userIdStr = (String) httpRequest.getAttribute(AgentConstant.USER_ID_REQUEST_ATTR);
        ThrowUtils.throwIf(orgIdStr == null || userIdStr == null,
                ErrorCode.NOT_LOGIN_ERROR, "无法获取当前用户信息");

        BatchTaskResultVO result = batchTaskService.createBatch(
                request, Long.parseLong(orgIdStr), Long.parseLong(userIdStr));
        return ResultUtils.success(result);
    }
}

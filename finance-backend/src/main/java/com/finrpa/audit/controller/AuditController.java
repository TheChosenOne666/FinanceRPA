package com.finrpa.audit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.audit.dto.request.AuditLogQueryRequest;
import com.finrpa.audit.dto.response.AuditLogVO;
import com.finrpa.audit.service.AuditLogService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志控制器（对外 API）（M7.1）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api}）：
 * <ul>
 *   <li>GET /v1/audit/logs —— 分页多维检索审计日志（时间范围/任务/用户/部门/业务线/风险等级/操作类型）</li>
 *   <li>GET /v1/audit/logs/{auditId} —— 查询审计日志详情</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/v1/audit/logs")
@Tag(name = "审计日志", description = "全链路审计日志多维检索与详情查询")
public class AuditController {

    /** 审计日志服务 */
    @Resource
    private AuditLogService auditLogService;

    // region 查询

    /**
     * 分页多维检索审计日志
     *
     * @param queryRequest 检索请求
     * @return 审计日志分页列表
     */
    @GetMapping
    @Operation(summary = "审计日志列表", description = "分页多维检索审计日志，支持时间范围/任务/用户/部门/业务线/风险等级/操作类型筛选")
    public BaseResponse<IPage<AuditLogVO>> listAuditLogs(AuditLogQueryRequest queryRequest) {
        // 从登录上下文获取 orgId（租户隔离）
        String orgIdStr = TenantContext.getOrgId();
        if (orgIdStr != null) {
            queryRequest.setOrgId(Long.parseLong(orgIdStr));
        }
        return ResultUtils.success(auditLogService.listAuditLogs(queryRequest));
    }

    /**
     * 查询审计日志详情
     *
     * @param auditId 审计日志业务 ID
     * @return 审计日志详情
     */
    @GetMapping("/{auditId}")
    @Operation(summary = "审计日志详情", description = "按 auditId 查询审计日志详情")
    public BaseResponse<AuditLogVO> getAuditLogDetail(@PathVariable Long auditId) {
        return ResultUtils.success(auditLogService.getAuditLogDetail(auditId));
    }

    // endregion
}

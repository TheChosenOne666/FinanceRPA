package com.finrpa.audit.controller;

import com.finrpa.audit.dto.request.AuditLogCreateRequest;
import com.finrpa.audit.service.AuditLogService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志内部回调控制器（Python → Java）（M7.1）
 *
 * <p>Python Executor 执行任务时通过此控制器回调 Java 上报审计日志。
 * 鉴权由 {@link com.finrpa.agent.interceptor.InternalTokenInterceptor} 拦截 {@code X-Internal-Token} Header 完成。</p>
 *
 * <p>内部端点（实际访问路径前缀 {@code /api}）：
 * <ul>
 *   <li>POST /internal/audit/logs —— 上报审计日志</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/internal/audit")
@Tag(name = "审计日志内部回调", description = "Python AI 服务上报审计日志接口（X-Internal-Token 鉴权）")
public class InternalAuditController {

    /** 审计日志服务 */
    @Resource
    private AuditLogService auditLogService;

    /**
     * 上报审计日志（Python 回调）
     *
     * @param request 审计日志创建请求
     * @return 操作结果
     */
    @PostMapping("/logs")
    @Operation(summary = "上报审计日志", description = "Python Executor 回调记录任务执行的操作行为，actionParams 经 SanitizeService 脱敏后持久化")
    public BaseResponse<Boolean> createAuditLog(@RequestBody AuditLogCreateRequest request) {
        log.info("收到审计日志回调: taskId={}, actionType={}, result={}",
                request.getTaskId(), request.getActionType(), request.getExecutionResult());
        // 1. 保存审计日志
        boolean success = auditLogService.createAuditLog(request);
        return ResultUtils.success(success);
    }
}

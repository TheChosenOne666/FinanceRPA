package com.finrpa.llm.controller;

import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.llm.dto.request.LlmCallLogCreateRequest;
import com.finrpa.llm.service.LlmCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 内部回调控制器（Python → Java）
 *
 * <p>Python ResilientCaller 执行 LLM 调用后通过此控制器回调 Java 上报调用记录。
 * 鉴权由 {@link com.finrpa.agent.interceptor.InternalTokenInterceptor} 拦截 {@code X-Internal-Token} Header 完成。</p>
 *
 * <p>内部端点（实际访问路径前缀 {@code /api/internal}）：
 * <ul>
 *   <li>POST /internal/llm/calls —— 上报 LLM 调用记录（M5.4）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/internal/llm")
@Tag(name = "LLM 内部回调", description = "Python AI 服务 LLM 调用上报接口（X-Internal-Token 鉴权）")
public class InternalLlmController {

    /** LLM 调用记录服务 */
    @Resource
    private LlmCallLogService llmCallLogService;

    /**
     * 上报 LLM 调用记录（Python 回调）
     *
     * @param request 调用记录创建请求
     * @return 操作结果
     */
    @PostMapping("/calls")
    @Operation(summary = "上报 LLM 调用记录", description = "Python ResilientCaller 每次调用 LLM 后回调记录调用详情")
    public BaseResponse<Boolean> createCallLog(@RequestBody LlmCallLogCreateRequest request) {
        log.info("收到 LLM 调用记录回调: taskId={}, model={}, context={}, retry={}, success={}",
                request.getTaskId(), request.getModel(), request.getContextName(),
                request.getRetryAttempt(), request.getSuccess());
        // 1. 保存调用记录
        boolean success = llmCallLogService.createCallLog(request);
        return ResultUtils.success(success);
    }
}

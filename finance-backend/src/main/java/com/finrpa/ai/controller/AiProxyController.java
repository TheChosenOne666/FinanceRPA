package com.finrpa.ai.controller;

import com.finrpa.agent.constant.AgentConstant;
import com.finrpa.agent.dto.request.TaskCreateRequest;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.service.TaskService;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.AiException;
import com.finrpa.ai.client.dto.TaskAbortResponse;
import com.finrpa.ai.client.dto.TaskStateResponse;
import com.finrpa.ai.client.dto.TaskTriggerRequest;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import com.finrpa.ai.sse.AiSseProxy;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 服务代理控制器
 *
 * <p>作为前端与 Python AI 服务之间的中间层，承担鉴权、路由、SSE 透传职责。
 * 前端不直接调用 Python，全部经 Java 转发。</p>
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/ai}）：
 * <ul>
 *   <li>POST /ai/tasks —— 触发任务执行（前端 → Java → Python）</li>
 *   <li>GET /ai/tasks/{taskId}/state —— 查询任务状态</li>
 *   <li>POST /ai/tasks/{taskId}/abort —— 终止任务</li>
 *   <li>GET /ai/sse/tasks/{taskId} —— SSE 订阅任务进度（透传 Python 流）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 服务代理", description = "Python AI 服务调用代理与 SSE 透传")
public class AiProxyController {

    /** AI 服务 HTTP Interface 客户端 */
    @Resource
    private AiServiceClient aiServiceClient;

    /** SSE 透传服务 */
    @Resource
    private AiSseProxy aiSseProxy;

    /** 任务服务（M2.3 任务状态持久化） */
    @Resource
    private TaskService taskService;

    /** HTTP 请求上下文（用于读取 JWT 解析后的 userId） */
    @Resource
    private HttpServletRequest httpServletRequest;

    // region 任务触发与查询

    /**
     * 触发任务执行
     *
     * <p>M2.3 增强：先持久化任务到数据库（生成雪花 taskId），再转发 Python 执行。</p>
     *
     * @param request 任务触发请求（前端仅需提供 goal / params / workflowId）
     * @return 任务触发响应
     */
    @PostMapping("/tasks")
    @Operation(summary = "触发任务", description = "前端触发 AI 任务执行（Java 持久化任务 → 转发 Python）")
    public BaseResponse<TaskTriggerResponse> triggerTask(@RequestBody TaskTriggerRequest request) {
        // 1. 从 JWT 上下文获取 orgId 和 userId
        String orgIdStr = TenantContext.getOrgId();
        String userIdStr = (String) httpServletRequest.getAttribute(AgentConstant.USER_ID_REQUEST_ATTR);
        ThrowUtils.throwIf(orgIdStr == null || userIdStr == null,
                ErrorCode.NOT_LOGIN_ERROR, "无法获取当前用户信息");

        // 2. 构建任务创建请求
        TaskCreateRequest createRequest = new TaskCreateRequest();
        createRequest.setGoal(request.getGoal());
        createRequest.setParams(request.getParams());
        createRequest.setWorkflowId(request.getWorkflowId() != null
                ? Long.parseLong(request.getWorkflowId()) : null);

        // 3. 持久化任务到数据库（生成雪花 taskId）
        AgentTaskEO task = taskService.createTask(Long.parseLong(orgIdStr), Long.parseLong(userIdStr), createRequest);
        log.info("任务已持久化: taskId={}, orgId={}, userId={}, goal={}",
                task.getTaskId(), orgIdStr, userIdStr, request.getGoal());

        // 4. 填充 taskId / orgId / userId 后转发 Python
        request.setTaskId(String.valueOf(task.getTaskId()));
        request.setOrgId(orgIdStr);
        request.setUserId(userIdStr);

        try {
            // 5. 调用 Python AI 服务触发任务
            TaskTriggerResponse response = aiServiceClient.triggerTask(request);
            return ResultUtils.success(response);
        } catch (Exception e) {
            log.error("触发任务失败: taskId={}", task.getTaskId(), e);
            throw new AiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 服务不可用: " + e.getMessage());
        }
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态响应
     */
    @GetMapping("/tasks/{taskId}/state")
    @Operation(summary = "查询任务状态", description = "代理查询 Python AI 服务的任务状态")
    public BaseResponse<TaskStateResponse> getTaskState(@PathVariable String taskId) {
        log.info("查询任务状态: taskId={}", taskId);
        try {
            // 1. 调用 Python AI 服务查询状态
            TaskStateResponse response = aiServiceClient.getTaskState(taskId);
            return ResultUtils.success(response);
        } catch (Exception e) {
            log.error("查询任务状态失败: taskId={}", taskId, e);
            throw new AiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 服务不可用: " + e.getMessage());
        }
    }

    /**
     * 终止任务
     *
     * @param taskId 任务 ID
     * @return 任务终止响应
     */
    @PostMapping("/tasks/{taskId}/abort")
    @Operation(summary = "终止任务", description = "代理终止 Python AI 服务的任务执行")
    public BaseResponse<TaskAbortResponse> abortTask(@PathVariable String taskId) {
        log.info("终止任务: taskId={}", taskId);
        try {
            // 1. 调用 Python AI 服务终止任务
            TaskAbortResponse response = aiServiceClient.abortTask(taskId);
            return ResultUtils.success(response);
        } catch (Exception e) {
            log.error("终止任务失败: taskId={}", taskId, e);
            throw new AiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 服务不可用: " + e.getMessage());
        }
    }

    // endregion

    // region SSE 透传

    /**
     * SSE 订阅任务执行进度
     *
     * <p>前端使用 {@code EventSource} 连接此端点，Java 透传 Python SSE 流。
     * 事件类型：progress / complete / error / step_start / step_end / replan。</p>
     *
     * @param taskId 任务 ID
     * @return SseEmitter
     */
    @GetMapping(value = "/sse/tasks/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 订阅任务进度", description = "透传 Python AI 服务的 SSE 事件流给前端")
    public SseEmitter subscribeTaskSse(@PathVariable String taskId) {
        log.info("前端订阅 SSE: taskId={}", taskId);
        return aiSseProxy.proxySse(taskId);
    }

    // endregion
}

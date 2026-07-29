package com.finrpa.agent.controller;

import com.finrpa.agent.dto.request.SubTaskUpdateRequest;
import com.finrpa.agent.dto.request.TaskStateUpdateRequest;
import com.finrpa.agent.service.TaskService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部任务回调控制器（Python → Java）
 *
 * <p>Python Executor 执行任务时通过此控制器回调 Java 更新任务/子任务状态。
 * 鉴权由 {@link com.finrpa.agent.interceptor.InternalTokenInterceptor} 拦截 {@code X-Internal-Token} Header 完成。</p>
 *
 * <p>内部端点（实际访问路径前缀 {@code /api/internal}）：
 * <ul>
 *   <li>POST /internal/tasks/{taskId}/state —— 更新任务状态</li>
 *   <li>POST /internal/tasks/{taskId}/subtasks —— 更新子任务状态</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@Tag(name = "内部任务回调", description = "Python AI 服务回调接口（X-Internal-Token 鉴权）")
public class InternalTaskController {

    /** 任务服务 */
    @Resource
    private TaskService taskService;

    // region 任务状态回调

    /**
     * 更新任务状态（Python 回调）
     *
     * @param taskId  任务 ID
     * @param request 状态更新请求
     * @return 操作结果
     */
    @PostMapping("/tasks/{taskId}/state")
    @Operation(summary = "更新任务状态", description = "Python Executor 回调更新任务状态")
    public BaseResponse<Boolean> updateTaskState(@PathVariable Long taskId,
                                                  @RequestBody TaskStateUpdateRequest request) {
        log.info("收到任务状态回调: taskId={}, state={}", taskId, request.getState());
        // 1. 更新任务状态
        taskService.updateTaskState(taskId, request);
        return ResultUtils.success(true);
    }

    /**
     * 更新子任务状态（Python 回调）
     *
     * @param taskId  任务 ID
     * @param request 子任务更新请求
     * @return 操作结果
     */
    @PostMapping("/tasks/{taskId}/subtasks")
    @Operation(summary = "更新子任务状态", description = "Python Executor 回调更新子任务状态")
    public BaseResponse<Boolean> updateSubTask(@PathVariable Long taskId,
                                                @RequestBody SubTaskUpdateRequest request) {
        log.info("收到子任务状态回调: taskId={}, subtaskIndex={}, status={}",
                taskId, request.getSubtaskIndex(), request.getStatus());
        // 1. 更新子任务状态
        taskService.updateSubTask(taskId, request);
        return ResultUtils.success(true);
    }

    // endregion
}

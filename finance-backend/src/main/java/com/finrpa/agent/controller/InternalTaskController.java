package com.finrpa.agent.controller;

import com.finrpa.agent.dto.request.CoordinationStateUpdateRequest;
import com.finrpa.agent.dto.request.SubTaskUpdateRequest;
import com.finrpa.agent.dto.request.TaskStateUpdateRequest;
import com.finrpa.agent.service.TaskService;
import com.finrpa.approval.dto.response.ApprovalResultResponse;
import com.finrpa.approval.service.ApprovalService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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
 *   <li>POST /internal/tasks/{taskId}/coordination-state —— 更新协调状态（M4.2）</li>
 *   <li>POST /internal/audit/logs —— 上报审计日志（M7.1 已迁移至 audit/controller/InternalAuditController）</li>
 *   <li>GET /internal/approvals/{taskId}/result —— 查询审批结果（M6.3，Python 回调用）</li>
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

    /** 审批服务（M6.3 Python 查询审批结果用） */
    @Resource
    private ApprovalService approvalService;

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

    // region 协调状态回调

    /**
     * 更新协调状态（Python 回调，M4.2 引入）
     *
     * @param taskId  任务 ID
     * @param request 协调状态更新请求
     * @return 操作结果
     */
    @PostMapping("/tasks/{taskId}/coordination-state")
    @Operation(summary = "更新协调状态", description = "Python Coordinator 每步执行后回调持久化 CoordinationState，用于断点续跑和 replan 追踪")
    public BaseResponse<Boolean> updateCoordinationState(@PathVariable Long taskId,
                                                          @RequestBody CoordinationStateUpdateRequest request) {
        log.info("收到协调状态回调: taskId={}, status={}, totalReplans={}, completed={}",
                taskId, request.getStatus(), request.getTotalReplans(),
                request.getCompletedSubtasks() != null ? request.getCompletedSubtasks().size() : 0);
        // 1. 更新协调状态
        taskService.updateCoordinationState(taskId, request);
        return ResultUtils.success(true);
    }

    // endregion

    // region 审批结果查询（M6.3）

    /**
     * 查询审批结果（Python 回调）
     *
     * <p>Python Executor 通过此接口查询任务关联的审批结果，
     * 判断是否可以继续执行（APPROVED）或需要终止（REJECTED / TIMEOUT）。</p>
     *
     * @param taskId 任务 ID
     * @return 审批结果响应
     */
    @GetMapping("/approvals/{taskId}/result")
    @Operation(summary = "查询审批结果", description = "Python 查询任务关联的审批结果（PENDING / APPROVED / REJECTED / TIMEOUT）")
    public BaseResponse<ApprovalResultResponse> getApprovalResult(@PathVariable Long taskId) {
        log.info("收到审批结果查询: taskId={}", taskId);
        return ResultUtils.success(approvalService.getApprovalResultByTaskId(taskId));
    }

    // endregion
}

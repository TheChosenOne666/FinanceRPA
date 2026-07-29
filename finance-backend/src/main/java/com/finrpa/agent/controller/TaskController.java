package com.finrpa.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.agent.dto.request.TaskQueryRequest;
import com.finrpa.agent.dto.response.TaskDetailVO;
import com.finrpa.agent.dto.response.TaskVO;
import com.finrpa.agent.service.TaskService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务管理控制器（对外 API）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/tasks}）：
 * <ul>
 *   <li>GET /tasks —— 分页查询任务列表</li>
 *   <li>GET /tasks/{taskId} —— 查询任务详情（含子任务列表）</li>
 *   <li>POST /tasks/{taskId}/abort —— 终止任务</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
@Tag(name = "任务管理", description = "任务列表、详情与终止")
public class TaskController {

    /** 任务服务 */
    @Resource
    private TaskService taskService;

    // region 任务查询

    /**
     * 分页查询任务列表
     *
     * @param queryRequest 查询请求（含分页、状态筛选、关键词搜索）
     * @return 任务分页列表
     */
    @GetMapping
    @Operation(summary = "任务列表", description = "分页查询当前组织下的任务列表")
    public BaseResponse<IPage<TaskVO>> listTasks(TaskQueryRequest queryRequest) {
        // 1. 查询任务列表（自动按租户过滤）
        IPage<TaskVO> page = taskService.listTasks(queryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 查询任务详情（含子任务列表）
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "任务详情", description = "查询任务详情，含子任务时间线")
    public BaseResponse<TaskDetailVO> getTaskDetail(@PathVariable Long taskId) {
        // 1. 查询任务详情
        TaskDetailVO detail = taskService.getTaskDetail(taskId);
        return ResultUtils.success(detail);
    }

    // endregion

    // region 任务操作

    /**
     * 终止任务
     *
     * @param taskId 任务 ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/abort")
    @Operation(summary = "终止任务", description = "终止指定任务，状态流转为 ABORTED")
    public BaseResponse<Boolean> abortTask(@PathVariable Long taskId) {
        // 1. 终止任务
        taskService.abortTask(taskId);
        return ResultUtils.success(true);
    }

    // endregion
}

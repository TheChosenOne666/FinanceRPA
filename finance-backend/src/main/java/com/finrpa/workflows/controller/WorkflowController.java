package com.finrpa.workflows.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.agent.constant.AgentConstant;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import com.finrpa.workflows.dto.request.WorkflowAddRequest;
import com.finrpa.workflows.dto.request.WorkflowQueryRequest;
import com.finrpa.workflows.dto.request.WorkflowRunRequest;
import com.finrpa.workflows.dto.request.WorkflowUpdateRequest;
import com.finrpa.workflows.dto.response.WorkflowRunVO;
import com.finrpa.workflows.dto.response.WorkflowVO;
import com.finrpa.workflows.service.WorkflowService;
import com.finrpa.workflows.service.WorkflowTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流模板管理控制器
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/workflows}）：
 * <ul>
 *   <li>GET /workflows —— 分页查询模板列表（支持行业/风险等级筛选与名称搜索）</li>
 *   <li>GET /workflows/{workflowId} —— 查询模板详情</li>
 *   <li>POST /workflows —— 创建工作流模板</li>
 *   <li>PUT /workflows/{workflowId} —— 更新模板</li>
 *   <li>DELETE /workflows/{workflowId} —— 删除模板</li>
 *   <li>POST /workflows/{workflowId}/run —— 触发执行（M3.4 阶段 3 实现）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/workflows")
@Tag(name = "工作流模板管理", description = "模板 CRUD + 触发执行")
public class WorkflowController {

    /** 工作流模板服务 */
    @Resource
    private WorkflowService workflowService;

    /** 工作流触发执行服务 */
    @Resource
    private WorkflowTriggerService workflowTriggerService;

    // region 查询

    /**
     * 分页查询工作流模板列表
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    @Operation(summary = "分页查询工作流模板列表")
    @GetMapping
    public BaseResponse<IPage<WorkflowVO>> listWorkflows(WorkflowQueryRequest queryRequest) {
        return ResultUtils.success(workflowService.listWorkflows(queryRequest));
    }

    /**
     * 查询工作流模板详情
     *
     * @param workflowId 工作流业务 ID
     * @return 模板视图
     */
    @Operation(summary = "查询工作流模板详情")
    @GetMapping("/{workflowId}")
    public BaseResponse<WorkflowVO> getWorkflow(@PathVariable Long workflowId) {
        return ResultUtils.success(workflowService.getWorkflow(workflowId));
    }

    // endregion

    // region 增删改

    /**
     * 创建工作流模板
     *
     * @param request 创建请求
     * @return 创建后的模板视图
     */
    @Operation(summary = "创建工作流模板")
    @PostMapping
    public BaseResponse<WorkflowVO> createWorkflow(@RequestBody WorkflowAddRequest request) {
        return ResultUtils.success(workflowService.createWorkflow(request));
    }

    /**
     * 更新工作流模板
     *
     * @param workflowId 工作流业务 ID
     * @param request    更新请求
     * @return 是否更新成功
     */
    @Operation(summary = "更新工作流模板")
    @PutMapping("/{workflowId}")
    public BaseResponse<Boolean> updateWorkflow(@PathVariable Long workflowId,
                                                 @RequestBody WorkflowUpdateRequest request) {
        return ResultUtils.success(workflowService.updateWorkflow(workflowId, request));
    }

    /**
     * 删除工作流模板（逻辑删除）
     *
     * @param workflowId 工作流业务 ID
     * @return 是否删除成功
     */
    @Operation(summary = "删除工作流模板")
    @DeleteMapping("/{workflowId}")
    public BaseResponse<Boolean> deleteWorkflow(@PathVariable Long workflowId) {
        return ResultUtils.success(workflowService.deleteWorkflow(workflowId));
    }

    // endregion

    // region 触发执行

    /**
     * 触发工作流执行
     *
     * @param workflowId 工作流业务 ID
     * @param request    运行参数（键值对）
     * @param httpRequest HTTP 请求（用于获取当前用户信息）
     * @return 执行结果（含 taskId 与初始状态）
     */
    @Operation(summary = "触发工作流执行", description = "加载模板 → 参数映射 → 创建任务 → 调 Python 执行")
    @PostMapping("/{workflowId}/run")
    public BaseResponse<WorkflowRunVO> runWorkflow(@PathVariable Long workflowId,
                                                    @RequestBody WorkflowRunRequest request,
                                                    HttpServletRequest httpRequest) {
        // 1. 从 JWT 上下文获取 orgId 和 userId
        String orgIdStr = TenantContext.getOrgId();
        String userIdStr = (String) httpRequest.getAttribute(AgentConstant.USER_ID_REQUEST_ATTR);
        ThrowUtils.throwIf(orgIdStr == null || userIdStr == null,
                ErrorCode.NOT_LOGIN_ERROR, "无法获取当前用户信息");

        // 2. 触发工作流执行
        return ResultUtils.success(
                workflowTriggerService.triggerWorkflow(
                        workflowId, request, Long.parseLong(orgIdStr), Long.parseLong(userIdStr)
                )
        );
    }

    // endregion
}

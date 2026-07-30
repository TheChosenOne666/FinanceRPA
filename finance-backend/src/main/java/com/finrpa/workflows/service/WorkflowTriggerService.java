package com.finrpa.workflows.service;

import com.finrpa.workflows.dto.request.WorkflowRunRequest;
import com.finrpa.workflows.dto.response.WorkflowRunVO;

/**
 * 工作流触发执行服务接口
 *
 * <p>负责加载模板 → 参数映射 → 创建任务 → 触发 Python 执行。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface WorkflowTriggerService {

    /**
     * 触发工作流执行
     *
     * @param workflowId 工作流模板业务 ID
     * @param request    运行参数（键值对）
     * @param orgId      组织 ID（租户隔离）
     * @param userId     操作用户 ID
     * @return 执行结果（含 taskId 与初始状态）
     */
    WorkflowRunVO triggerWorkflow(Long workflowId, WorkflowRunRequest request, Long orgId, Long userId);
}

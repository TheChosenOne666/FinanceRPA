package com.finrpa.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.agent.dto.request.TaskCreateRequest;
import com.finrpa.agent.dto.request.TaskQueryRequest;
import com.finrpa.agent.dto.request.TaskStateUpdateRequest;
import com.finrpa.agent.dto.request.SubTaskUpdateRequest;
import com.finrpa.agent.dto.response.TaskDetailVO;
import com.finrpa.agent.dto.response.TaskVO;
import com.finrpa.agent.entity.AgentTaskEO;

/**
 * 任务服务接口
 *
 * <p>负责任务的创建、查询、状态更新与终止，同时为 Python 回调提供内部接口。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface TaskService {

    /**
     * 创建任务（对外接口调用）
     *
     * @param orgId     组织 ID
     * @param userId    用户 ID
     * @param request   任务创建请求
     * @return 任务实体
     */
    AgentTaskEO createTask(Long orgId, Long userId, TaskCreateRequest request);

    /**
     * 分页查询任务列表（自动按租户过滤）
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    IPage<TaskVO> listTasks(TaskQueryRequest queryRequest);

    /**
     * 查询任务详情（含子任务列表）
     *
     * @param taskId 任务 ID
     * @return 任务详情视图
     */
    TaskDetailVO getTaskDetail(Long taskId);

    /**
     * 终止任务
     *
     * @param taskId 任务 ID
     */
    void abortTask(Long taskId);

    /**
     * 更新任务状态（Python 回调内部接口）
     *
     * @param taskId  任务 ID
     * @param request 状态更新请求
     */
    void updateTaskState(Long taskId, TaskStateUpdateRequest request);

    /**
     * 更新子任务状态（Python 回调内部接口）
     *
     * @param taskId  任务 ID
     * @param request 子任务更新请求
     */
    void updateSubTask(Long taskId, SubTaskUpdateRequest request);

    /**
     * 更新 Skyvern 任务 ID（M3.8 引入，Python 调 Skyvern API 后回传）
     *
     * @param taskId         Java 侧任务 ID
     * @param skyvernTaskId  Skyvern 返回的任务 ID
     */
    void updateSkyvernTaskId(Long taskId, String skyvernTaskId);
}

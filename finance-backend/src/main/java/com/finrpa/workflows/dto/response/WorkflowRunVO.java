package com.finrpa.workflows.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作流触发执行结果视图
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class WorkflowRunVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（Java agent_task 表主键） */
    private Long taskId;

    /** 工作流模板 ID */
    private Long workflowId;

    /** 任务状态 */
    private String state;
}

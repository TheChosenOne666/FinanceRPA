package com.finrpa.agent.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 任务详情视图对象（返回前端，含子任务列表）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDetailVO extends TaskVO {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务参数（JSON 字符串） */
    private String params;

    /** 关联工作流模板 ID */
    private Long workflowId;

    /** 子任务列表 */
    private List<SubTaskVO> subtasks;
}

package com.finrpa.ai.client.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 任务续跑请求 DTO（Java → Python，M4.3）
 *
 * <p>与 Python {@code app/schemas.py::TaskResumeRequest} 字段对齐（camelCase）。
 * Java 侧从 rpa_agent_coordination_state 读取已存计划 + completed_subtasks，
 * 通过此 DTO 传给 Python Coordinator 从断点继续执行。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskResumeRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 组织 ID（租户隔离） */
    private String orgId;

    /** 导航目标 */
    private String navigationGoal;

    /** 已完成子任务 ID 列表（断点续跑跳过这些子任务） */
    private List<String> completedSubtasks;

    /** 已存计划 JSON 字符串（TaskPlan.model_dump_json()） */
    private String currentPlan;

    /** 工作流模板参数（上下文） */
    private Map<String, Object> params;
}

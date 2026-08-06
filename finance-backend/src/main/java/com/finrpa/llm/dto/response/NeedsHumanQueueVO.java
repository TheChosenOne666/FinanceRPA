package com.finrpa.llm.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * NEEDS_HUMAN 队列 VO（前端展示）
 *
 * <p>包含队列条目的完整信息，供操作员查看详情并处置。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class NeedsHumanQueueVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 队列业务 ID */
    private Long queueId;

    /** 任务 ID */
    private Long taskId;

    /** 组织 ID */
    private Long orgId;

    /** 业务线业务 ID（P3 ai-monitoring 原型对齐） */
    private Long businessLineId;

    /** 业务线名称（关联 enterprise_business_line.business_line_name，用于队列卡片展示，可空） */
    private String businessLineName;

    /** 子任务 ID */
    private String subtaskId;

    /** 调用上下文名称 */
    private String contextName;

    /** 截图 URL */
    private String screenshotUrl;

    /** LLM 最后一次原始输出 */
    private String llmRawOutput;

    /** 校验错误信息 */
    private String validationError;

    /** 总尝试次数 */
    private Integer attempts;

    /** 队列状态：PENDING / RESOLVED */
    private String status;

    /** 处置动作：skip / manual / abort */
    private String resolveAction;

    /** 处置人用户 ID */
    private Long resolvedBy;

    /** 处置时间 */
    private Timestamp resolvedAt;

    /** 任务目标（关联 rpa_agent_task.goal，用于队列卡片"子任务"展示） */
    private String taskTitle;

    /** 子任务目标（关联 rpa_agent_subtask.goal，用于队列卡片"子任务"展示） */
    private String subtaskGoal;

    /** 创建时间 */
    private Timestamp createTime;
}

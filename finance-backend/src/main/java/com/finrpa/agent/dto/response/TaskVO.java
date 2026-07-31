package com.finrpa.agent.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 任务视图对象（返回前端）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private Long taskId;

    /** 组织 ID */
    private Long orgId;

    /** 触发用户 ID */
    private Long userId;

    /** 任务目标 */
    private String goal;

    /** 任务状态 */
    private String status;

    /** 当前步骤序号 */
    private Integer currentStep;

    /** 总步骤数 */
    private Integer totalSteps;

    /** 状态消息 */
    private String message;

    /** 错误信息 */
    private String errorMessage;

    /** Skyvern 任务 ID（M3.8 引入，关联 Skyvern 原生任务） */
    private String skyvernTaskId;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}

package com.finrpa.ai.client.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 任务状态响应 DTO（Python → Java）
 *
 * <p>与 Python {@code app/schemas.py::TaskStateResponse} 字段对齐。
 * state 取值：pending / executing / success / failed / needs_human。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskStateResponse implements Serializable {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 任务状态：pending / executing / success / failed / needs_human */
    private String state;

    /** 当前步骤序号（从 0 开始） */
    private int currentStep;

    /** 总步骤数 */
    private int totalSteps;

    /** 状态消息 */
    private String message;
}

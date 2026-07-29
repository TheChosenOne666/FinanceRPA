package com.finrpa.ai.client.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 任务触发响应 DTO（Python → Java）
 *
 * <p>与 Python {@code app/schemas.py::TaskTriggerResponse} 字段对齐。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskTriggerResponse implements Serializable {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 初始状态（默认 running） */
    private String status;

    /** 响应消息 */
    private String message;
}

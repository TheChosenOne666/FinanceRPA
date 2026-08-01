package com.finrpa.ai.client.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务续跑响应 DTO（Python → Java，M4.3）
 *
 * <p>与 Python {@code app/schemas.py::TaskResumeResponse} 字段对齐（camelCase）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskResumeResponse implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 状态（running） */
    private String status;

    /** 消息 */
    private String message;
}

package com.finrpa.ai.sse;

import lombok.Data;

import java.io.Serializable;

/**
 * SSE 事件 DTO
 *
 * <p>与 Python {@code app/schemas.py::SseEvent} 字段对齐，
 * 描述 Python 推送给 Java 的事件结构。Java 透传时直接转发原始 JSON 字符串，
 * 此 DTO 用于需要解析事件 data 字段的场景（如后续 M2.4 状态同步）。</p>
 *
 * <p>event_type 取值：step_start / step_end / progress / error / complete / replan。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SseEventDto implements Serializable {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 事件类型：step_start / step_end / progress / error / complete / replan */
    private String eventType;

    /** 事件数据（业务自定义） */
    private Object data;

    /** 时间戳（ISO 8601 字符串） */
    private String timestamp;
}

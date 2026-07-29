package com.finrpa.ai.client.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务触发请求 DTO（Java → Python）
 *
 * <p>与 Python {@code app/schemas.py::TaskTriggerRequest} 字段对齐，
 * 通过 Spring HTTP Interface 序列化为 JSON 发送给 Python AI 服务。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskTriggerRequest implements Serializable {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 任务 ID（UUID，由 Java 侧生成） */
    private String taskId;

    /** 组织 ID（租户隔离） */
    private String orgId;

    /** 操作用户 ID */
    private String userId;

    /** 任务目标（如 "下载银行流水"） */
    private String goal;

    /** 任务参数（业务自定义） */
    private Map<String, Object> params = new HashMap<>();

    /** 关联工作流模板 ID（可选） */
    private String workflowId;
}

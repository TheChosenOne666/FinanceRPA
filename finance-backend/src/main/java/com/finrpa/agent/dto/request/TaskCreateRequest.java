package com.finrpa.agent.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务创建请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskCreateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务目标（如"下载银行流水"） */
    private String goal;

    /** 任务参数（业务自定义） */
    private Map<String, Object> params = new HashMap<>();

    /** 关联工作流模板 ID（可选） */
    private Long workflowId;
}

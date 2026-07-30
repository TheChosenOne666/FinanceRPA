package com.finrpa.workflows.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作流触发执行请求
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class WorkflowRunRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 运行参数键值对（key 对应模板 params 中的 name，value 为实际值） */
    private Map<String, Object> params = new HashMap<>();
}

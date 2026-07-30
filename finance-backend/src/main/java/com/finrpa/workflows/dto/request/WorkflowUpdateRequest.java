package com.finrpa.workflows.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作流模板更新请求
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class WorkflowUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板描述 */
    private String description;

    /** 风险等级：low / medium / high / critical */
    private String riskLevel;

    /** 参数定义 JSON 数组 */
    private String params;

    /** 步骤 JSON 数组 */
    private String steps;

    /** 启用状态（0-禁用 1-启用） */
    private Integer enabled;
}

package com.finrpa.workflows.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 工作流模板分页查询请求
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WorkflowQueryRequest extends PageRequest {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板名称（模糊搜索） */
    private String name;

    /** 行业筛选 */
    private String industry;

    /** 风险等级筛选 */
    private String riskLevel;

    /** 启用状态筛选（null-全部 0-禁用 1-启用） */
    private Integer enabled;
}

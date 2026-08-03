package com.finrpa.workflows.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 工作流模板视图（返回前端，脱敏：不返回加密参数的明文）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class WorkflowVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 工作流业务 ID */
    private Long workflowId;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 行业 */
    private String industry;

    /** 风险等级 */
    private String riskLevel;

    /** 参数定义 JSON 数组 */
    private String params;

    /** 步骤 JSON 数组 */
    private String steps;

    /** 版本号 */
    private String version;

    /** 启用状态 */
    private Integer enabled;

    /** 创建人姓名（内置模板为 NULL，前端显示"系统"） */
    private String createUser;

    /** 执行次数（统计 rpa_agent_task 表中 workflow_id = 此模板的记录数） */
    private Long runCount;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}

package com.finrpa.agent.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 任务视图对象（返回前端）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private Long taskId;

    /** 组织 ID */
    private Long orgId;

    /** 触发用户 ID */
    private Long userId;

    /** 任务目标 */
    private String goal;

    /** 任务状态 */
    private String status;

    /** 当前步骤序号 */
    private Integer currentStep;

    /** 总步骤数 */
    private Integer totalSteps;

    /** 状态消息 */
    private String message;

    /** 错误信息 */
    private String errorMessage;

    /** Skyvern 任务 ID（M3.8 引入，关联 Skyvern 原生任务） */
    private String skyvernTaskId;

    /** 触发用户姓名（关联 sys_user.real_name，前端展示用） */
    private String userName;

    /** 任务耗时（毫秒，仅终态任务计算；进行中任务为 null） */
    private Long durationMs;

    /** 风险等级（关联工作流模板的 risk_level：low / medium / high / critical；无关联工作流时为 null） */
    private String riskLevel;

    /** 部门业务 ID（M7.6 三维度 RBAC） */
    private Long departmentId;

    /** 部门名称（关联 enterprise_department.dept_name，前端展示用） */
    private String departmentName;

    /** 业务线业务 ID（M7.6 三维度 RBAC） */
    private Long businessLineId;

    /** 业务线名称（关联 enterprise_business_line.business_line_name，前端展示用） */
    private String businessLineName;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}

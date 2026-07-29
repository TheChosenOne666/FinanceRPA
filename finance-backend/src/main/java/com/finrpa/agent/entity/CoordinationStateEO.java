package com.finrpa.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Agent 协调状态实体（对应 rpa_agent_coordination_state 表）
 *
 * <p>Coordinator 维护的全局状态，用于断点续跑和 replan 追踪。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_agent_coordination_state")
public class CoordinationStateEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 所属任务 ID */
    @TableId(value = "task_id", type = IdType.ASSIGN_ID)
    private Long taskId;

    /** 组织 ID（租户隔离） */
    @TableField("org_id")
    private Long orgId;

    /** 导航目标 */
    @TableField("navigation_goal")
    private String navigationGoal;

    /** 当前计划（JSON，含子任务列表） */
    @TableField("current_plan")
    private String currentPlan;

    /** 已完成子任务 ID 列表（JSON 数组） */
    @TableField("completed_subtasks")
    private String completedSubtasks;

    /** 总重规划次数 */
    @TableField("total_replans")
    private Integer totalReplans;

    /** 最大重规划次数 */
    @TableField("max_replans")
    private Integer maxReplans;

    /** 协调状态：RUNNING / COMPLETED / FAILED / NEEDS_HUMAN */
    @TableField("status")
    private String status;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("update_time")
    private Timestamp updateTime;
}

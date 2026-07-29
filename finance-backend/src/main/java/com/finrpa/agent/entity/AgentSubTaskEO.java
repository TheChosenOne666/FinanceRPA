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
 * Agent 子任务实体（对应 rpa_agent_subtask 表）
 *
 * <p>Planner 拆解的子任务记录，由 Executor 逐个执行。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_agent_subtask")
public class AgentSubTaskEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 子任务业务 ID（雪花算法） */
    @TableId(value = "subtask_id", type = IdType.ASSIGN_ID)
    private Long subtaskId;

    /** 所属任务 ID */
    @TableField("task_id")
    private Long taskId;

    /** 组织 ID（租户隔离） */
    @TableField("org_id")
    private Long orgId;

    /** 子任务序号（从 0 开始） */
    @TableField("subtask_index")
    private Integer subtaskIndex;

    /** 子任务目标 */
    @TableField("goal")
    private String goal;

    /** 完成条件 */
    @TableField("completion_condition")
    private String completionCondition;

    /** 最大重试次数 */
    @TableField("max_retries")
    private Integer maxRetries;

    /** 失败策略：RETRY / SKIP / ABORT / REPLAN */
    @TableField("failure_strategy")
    private String failureStrategy;

    /** 子任务状态：PENDING / RUNNING / COMPLETED / FAILED / SKIPPED / REPLANNED */
    @TableField("status")
    private String status;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 执行结果数据（JSON） */
    @TableField("result_data")
    private String resultData;

    /** 开始执行时间 */
    @TableField("started_at")
    private Timestamp startedAt;

    /** 完成时间 */
    @TableField("completed_at")
    private Timestamp completedAt;

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

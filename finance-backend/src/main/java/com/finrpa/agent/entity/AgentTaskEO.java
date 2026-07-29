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
 * Agent 任务实体（对应 rpa_agent_task 表）
 *
 * <p>任务执行实例，由前端触发、Python AI 服务执行、Java 侧持久化状态。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_agent_task")
public class AgentTaskEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 任务业务 ID（雪花算法） */
    @TableId(value = "task_id", type = IdType.ASSIGN_ID)
    private Long taskId;

    /** 组织 ID（租户隔离） */
    @TableField("org_id")
    private Long orgId;

    /** 触发用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 任务目标（如"下载银行流水"） */
    @TableField("goal")
    private String goal;

    /** 任务参数（JSON） */
    @TableField("params")
    private String params;

    /** 关联工作流模板 ID（可选） */
    @TableField("workflow_id")
    private Long workflowId;

    /** 任务状态：PENDING / EXECUTING / SUCCESS / FAILED / NEEDS_HUMAN / ABORTED */
    @TableField("status")
    private String status;

    /** 当前步骤序号 */
    @TableField("current_step")
    private Integer currentStep;

    /** 总步骤数 */
    @TableField("total_steps")
    private Integer totalSteps;

    /** 状态消息 */
    @TableField("message")
    private String message;

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

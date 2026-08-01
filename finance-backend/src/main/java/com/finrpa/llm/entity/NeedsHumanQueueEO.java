package com.finrpa.llm.entity;

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
 * NEEDS_HUMAN 队列实体（对应 rpa_needs_human_queue 表）
 *
 * <p>当 LLM 调用重试耗尽（ResilientCaller 层 3 兜底）时，Python 上报此事件入队，
 * 等待操作员查看详情并处置（skip / manual / abort）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_needs_human_queue")
public class NeedsHumanQueueEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 队列业务 ID（雪花算法） */
    @TableId(value = "queue_id", type = IdType.ASSIGN_ID)
    private Long queueId;

    /** 任务 ID */
    @TableField("task_id")
    private Long taskId;

    /** 组织 ID（租户隔离） */
    @TableField("org_id")
    private Long orgId;

    /** 子任务 ID（可空，标记哪个子任务触发了 NEEDS_HUMAN） */
    @TableField("subtask_id")
    private String subtaskId;

    /** 调用上下文名称（planner / replan / executor 等） */
    @TableField("context_name")
    private String contextName;

    /** 截图 URL（可空，出错时的页面截图） */
    @TableField("screenshot_url")
    private String screenshotUrl;

    /** LLM 最后一次原始输出（用于操作员诊断） */
    @TableField("llm_raw_output")
    private String llmRawOutput;

    /** 校验错误信息（Pydantic ValidationError 内容） */
    @TableField("validation_error")
    private String validationError;

    /** 总尝试次数（含首次） */
    @TableField("attempts")
    private Integer attempts;

    /** 队列状态：PENDING / RESOLVED */
    @TableField("status")
    private String status;

    /** 处置动作：skip / manual / abort（PENDING 时为 null） */
    @TableField("resolve_action")
    private String resolveAction;

    /** 处置人用户 ID（PENDING 时为 null） */
    @TableField("resolved_by")
    private Long resolvedBy;

    /** 处置时间（PENDING 时为 null） */
    @TableField("resolved_at")
    private Timestamp resolvedAt;

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

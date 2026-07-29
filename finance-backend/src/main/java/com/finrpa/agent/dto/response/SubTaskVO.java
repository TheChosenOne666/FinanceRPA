package com.finrpa.agent.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 子任务视图对象（返回前端）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SubTaskVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 子任务 ID */
    private Long subtaskId;

    /** 所属任务 ID */
    private Long taskId;

    /** 子任务序号 */
    private Integer subtaskIndex;

    /** 子任务目标 */
    private String goal;

    /** 完成条件 */
    private String completionCondition;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 失败策略 */
    private String failureStrategy;

    /** 子任务状态 */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 执行结果数据 */
    private String resultData;

    /** 开始执行时间 */
    private Timestamp startedAt;

    /** 完成时间 */
    private Timestamp completedAt;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}

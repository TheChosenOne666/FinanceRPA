package com.finrpa.agent.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务状态更新请求 DTO（Python 回调）
 *
 * <p>Python Executor 每步执行后回调此接口更新任务状态。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskStateUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务状态：PENDING / EXECUTING / SUCCESS / FAILED / NEEDS_HUMAN / ABORTED */
    private String state;

    /** 当前步骤序号 */
    private Integer currentStep;

    /** 总步骤数 */
    private Integer totalSteps;

    /** 状态消息 */
    private String message;

    /** 错误信息（失败时填写） */
    private String errorMessage;
}

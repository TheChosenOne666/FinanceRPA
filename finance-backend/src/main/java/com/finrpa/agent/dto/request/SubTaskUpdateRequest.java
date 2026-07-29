package com.finrpa.agent.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 子任务状态更新请求 DTO（Python 回调）
 *
 * <p>Python Executor 执行子任务后回调此接口更新子任务状态。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SubTaskUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 子任务序号 */
    private Integer subtaskIndex;

    /** 子任务状态：PENDING / RUNNING / COMPLETED / FAILED / SKIPPED / REPLANNED */
    private String status;

    /** 错误信息（失败时填写） */
    private String errorMessage;

    /** 执行结果数据 */
    private Map<String, Object> resultData;
}

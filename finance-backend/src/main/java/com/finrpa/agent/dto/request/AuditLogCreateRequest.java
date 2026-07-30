package com.finrpa.agent.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审计日志创建请求 DTO（Python 回调）
 *
 * <p>Python Executor 执行任务时，每完成一个操作行为即回调此接口记录审计日志。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class AuditLogCreateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private Long taskId;

    /** 组织 ID */
    private Long orgId;

    /** 动作类型（NAVIGATE/CLICK/INPUT_TEXT/LOGIN 等） */
    private String actionType;

    /** 目标元素描述（可选） */
    private String targetElement;

    /** 页面 URL（可选） */
    private String pageUrl;

    /** 执行结果（success/failed） */
    private String executionResult;

    /** 错误信息（失败时填写，可选） */
    private String errorMessage;
}

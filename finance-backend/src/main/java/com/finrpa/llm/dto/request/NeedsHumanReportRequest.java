package com.finrpa.llm.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * NEEDS_HUMAN 事件上报请求 DTO（Python 回调）
 *
 * <p>Python ResilientCaller 重试耗尽后，通过 {@code POST /internal/llm/needs-human} 上报详情，
 * 供操作员查看并处置。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class NeedsHumanReportRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（字符串形式） */
    private String taskId;

    /** 组织 ID（字符串形式） */
    private String orgId;

    /** 业务线 ID（字符串形式，可空，P3 ai-monitoring 原型对齐） */
    private String businessLineId;

    /** 子任务 ID（可空） */
    private String subtaskId;

    /** 调用上下文名称（planner / replan / executor 等） */
    private String contextName;

    /** 截图 URL（可空） */
    private String screenshotUrl;

    /** LLM 最后一次原始输出 */
    private String llmRawOutput;

    /** 校验错误信息 */
    private String validationError;

    /** 总尝试次数（含首次） */
    private Integer attempts;
}

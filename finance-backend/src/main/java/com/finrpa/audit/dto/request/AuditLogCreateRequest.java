package com.finrpa.audit.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 审计日志创建请求 DTO（Python 回调，按系统设计 6.4.1 完整结构）
 *
 * <p>Python Executor 执行任务时，每完成一个操作行为即回调此接口记录审计日志。
 * actionParams 由 Java SanitizeService 脱敏后持久化。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class AuditLogCreateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    // region 基本信息

    /** 任务 ID */
    private Long taskId;

    /** 组织 ID */
    private Long orgId;

    /** 部门 ID（可选） */
    private Long departmentId;

    /** 业务线 ID（可选） */
    private Long businessLineId;

    /** 触发用户 ID（可选） */
    private Long userId;

    // endregion

    // region 操作信息

    /** 动作类型（NAVIGATE/CLICK/INPUT_TEXT/LOGIN 等） */
    private String actionType;

    /** 目标元素描述（可选） */
    private String targetElement;

    /** 页面 URL（可选） */
    private String pageUrl;

    /** 操作参数 JSON（原始值，Java 端脱敏后存储） */
    private String actionParams;

    /** 执行结果（success/failed） */
    private String executionResult;

    /** 错误信息（失败时填写，可选） */
    private String errorMessage;

    // endregion

    // region 风险信息

    /** 风险等级：low / medium / high / critical（可选） */
    private String riskLevel;

    /** 关联审批单 ID（可选） */
    private Long approvalId;

    // endregion

    // region 时间信息

    /** 操作开始时间（可选） */
    private Timestamp startedAt;

    /** 操作完成时间（可选） */
    private Timestamp completedAt;

    /** 操作耗时（毫秒，可选） */
    private Long durationMs;

    // endregion

    // region 截图信息（M7.2 填充，M7.1 预留）

    /** 操作前截图 URL（可选） */
    private String beforeScreenshotUrl;

    /** 操作后截图 URL（可选） */
    private String afterScreenshotUrl;

    // endregion

    // region LLM 信息

    /** LLM 模型名称（可选） */
    private String llmModel;

    /** LLM token 用量（可选） */
    private Integer llmTokensUsed;

    /** LLM 调用成本（美元，可选） */
    private BigDecimal llmCost;

    // endregion
}

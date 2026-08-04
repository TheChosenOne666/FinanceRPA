package com.finrpa.audit.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 审计日志视图对象（对外 API 返回，M7.1）
 *
 * <p>actionParams 在持久化前已由 SanitizeService 脱敏，此处直接返回脱敏后的值。
 * 截图 URL 为 MinIO 预签名地址（M7.2 填充，有效期 1 小时）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class AuditLogVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    // region 基本信息

    /** 审计日志业务 ID */
    private Long auditId;

    /** 任务 ID */
    private Long taskId;

    /** 组织 ID */
    private Long orgId;

    /** 部门 ID */
    private Long departmentId;

    /** 业务线 ID */
    private Long businessLineId;

    /** 触发用户 ID */
    private Long userId;

    /** 触发用户姓名（联表 sys_user.real_name 填充，对齐原型 06-audit-logs.html 列表显示） */
    private String userName;

    /** 部门名称（联表 sys_department.name 填充，对齐原型列表显示） */
    private String departmentName;

    /** 业务线名称（联表 sys_business_line.name 填充，对齐原型列表显示） */
    private String businessLineName;

    // endregion

    // region 操作信息

    /** 动作类型 */
    private String actionType;

    /** 目标元素描述 */
    private String targetElement;

    /** 页面 URL */
    private String pageUrl;

    /** 操作参数 JSON（已脱敏） */
    private String actionParams;

    /** 执行结果 */
    private String executionResult;

    /** 错误信息 */
    private String errorMessage;

    // endregion

    // region 风险信息

    /** 风险等级 */
    private String riskLevel;

    /** 关联审批单 ID */
    private Long approvalId;

    // endregion

    // region 时间信息

    /** 操作开始时间 */
    private Timestamp startedAt;

    /** 操作完成时间 */
    private Timestamp completedAt;

    /** 操作耗时（毫秒） */
    private Long durationMs;

    // endregion

    // region 截图信息

    /** 操作前截图 URL（MinIO 预签名，有效期 1 小时） */
    private String beforeScreenshotUrl;

    /** 操作后截图 URL（MinIO 预签名，有效期 1 小时） */
    private String afterScreenshotUrl;

    // endregion

    // region LLM 信息

    /** LLM 模型名称 */
    private String llmModel;

    /** LLM token 用量 */
    private Integer llmTokensUsed;

    /** LLM 调用成本（美元） */
    private BigDecimal llmCost;

    // endregion

    /** 创建时间 */
    private Timestamp createTime;
}

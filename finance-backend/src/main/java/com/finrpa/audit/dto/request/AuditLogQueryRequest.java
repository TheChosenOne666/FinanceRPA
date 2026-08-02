package com.finrpa.audit.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

/**
 * 审计日志多维检索请求（M7.1）
 *
 * <p>检索维度：时间范围 / 任务 / 用户 / 部门 / 业务线 / 风险等级 / 操作类型。
 * orgId 由 Controller 从登录上下文自动填充。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQueryRequest extends PageRequest {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 组织 ID（由 Controller 从登录上下文自动填充） */
    private Long orgId;

    /** 任务 ID（精确查询） */
    private Long taskId;

    /** 触发用户 ID */
    private Long userId;

    /** 部门 ID */
    private Long departmentId;

    /** 业务线 ID */
    private Long businessLineId;

    /** 风险等级：low / medium / high / critical */
    private String riskLevel;

    /** 操作类型：NAVIGATE / CLICK / INPUT_TEXT / LOGIN 等 */
    private String actionType;

    /** 执行结果：success / failed */
    private String executionResult;

    /** 起始时间（包含） */
    private Timestamp startTime;

    /** 截止时间（包含） */
    private Timestamp endTime;
}

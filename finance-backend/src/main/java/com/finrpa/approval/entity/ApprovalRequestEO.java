package com.finrpa.approval.entity;

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
 * 审批请求实体（对应 rpa_approval_request 表）
 *
 * <p>高风险（high）或极高风险（critical）任务触发时创建审批单，
 * 等待部门审批（department）或合规审计部审批（compliance）通过后再执行。</p>
 *
 * <p>该表有 org_id 字段但由 Java 内部触发流程写入（无 Python 回调），
 * 已加入 TenantLineHandler 忽略清单，对外 API 在 Service 层手动按 orgId 过滤。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_approval_request")
public class ApprovalRequestEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 审批单业务 ID（雪花算法） */
    @TableId(value = "approval_id", type = IdType.ASSIGN_ID)
    private Long approvalId;

    /** 关联任务 ID */
    @TableField("task_id")
    private Long taskId;

    /** 组织 ID */
    @TableField("org_id")
    private Long orgId;

    /** 工作流模板 ID */
    @TableField("workflow_id")
    private Long workflowId;

    /** 触发用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 风险等级：low / medium / high / critical */
    @TableField("risk_level")
    private String riskLevel;

    /** 审批路由：auto / department / compliance */
    @TableField("approval_route")
    private String approvalRoute;

    /** 审批状态：PENDING / APPROVED / REJECTED / TIMEOUT */
    @TableField("status")
    private String status;

    /** 审批人 ID（审批完成后填充） */
    @TableField("approver_id")
    private Long approverId;

    /** 通过理由（审批通过时填充） */
    @TableField("approve_reason")
    private String approveReason;

    /** 拒绝理由（审批拒绝时填充） */
    @TableField("reject_reason")
    private String rejectReason;

    /** 风险判断理由（LLM 判断结果，用于审批人参考） */
    @TableField("risk_reasoning")
    private String riskReasoning;

    /** 请求负载 JSON（任务目标 + 参数等，用于审批人查看） */
    @TableField("request_payload")
    private String requestPayload;

    /** 超时时间（分钟） */
    @TableField("timeout_minutes")
    private Integer timeoutMinutes;

    /** 超时截止时间 */
    @TableField("timeout_at")
    private Timestamp timeoutAt;

    /** 审批完成时间 */
    @TableField("approved_at")
    private Timestamp approvedAt;

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

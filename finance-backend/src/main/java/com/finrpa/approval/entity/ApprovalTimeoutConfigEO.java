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
 * 审批超时阈值配置实体（对应 rpa_approval_timeout_config 表）
 *
 * <p>全局共享的审批超时配置，按风险等级（high / critical）配置超时分钟数。
 * 替代写死在 {@code ApprovalConstant.HIGH_APPROVAL_TIMEOUT_MINUTES} 与
 * {@code ApprovalConstant.CRITICAL_APPROVAL_TIMEOUT_MINUTES} 的 30 / 60 分钟常量，
 * 支持运维通过设置页在线修改并热生效。</p>
 *
 * <p>该表不参与租户隔离（无 org_id 字段），已加入 TenantLineHandler 忽略清单。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_approval_timeout_config")
public class ApprovalTimeoutConfigEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 配置业务 ID（雪花算法） */
    @TableId(value = "config_id", type = IdType.ASSIGN_ID)
    private Long configId;

    /** 风险等级：high / critical */
    @TableField("risk_level")
    private String riskLevel;

    /** 超时分钟数 */
    @TableField("timeout_minutes")
    private Integer timeoutMinutes;

    /** 描述说明（可空） */
    @TableField("description")
    private String description;

    /** 启用状态（0-禁用 1-启用） */
    @TableField("enabled")
    private Integer enabled;

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

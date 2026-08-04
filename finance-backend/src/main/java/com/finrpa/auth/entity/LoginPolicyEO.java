package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 登录安全策略配置实体（对应 sys_login_policy 表）
 *
 * <p>全局共享的单行配置（id=1），定义账号锁定规则、IP 白/黑名单、并发登录与空闲超时。
 * 由运维通过设置页统一管理，不参与租户隔离（无 org_id 字段）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_login_policy")
public class LoginPolicyEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增，固定为 1） */
    @TableField("id")
    private Long id;

    /** 策略业务 ID（雪花算法） */
    @TableId(value = "policy_id", type = IdType.ASSIGN_ID)
    private Long policyId;

    /** 最大连续登录失败次数（超过后锁定账号） */
    @TableField("max_login_attempts")
    private Integer maxLoginAttempts;

    /** 账号锁定时长（分钟） */
    @TableField("lock_minutes")
    private Integer lockMinutes;

    /** IP 白名单（逗号分隔，空表示不限制；同时配置时白名单优先） */
    @TableField("ip_whitelist")
    private String ipWhitelist;

    /** IP 黑名单（逗号分隔，命中即拒绝） */
    @TableField("ip_blacklist")
    private String ipBlacklist;

    /** 是否允许多端并发登录（0-不允许 1-允许，配合 SEC-3 会话管理） */
    @TableField("allow_multi_login")
    private Integer allowMultiLogin;

    /** 会话空闲超时分钟数（配合 SEC-3 会话管理） */
    @TableField("session_timeout_minutes")
    private Integer sessionTimeoutMinutes;

    /** 启用状态（0-禁用 1-启用） */
    @TableField("enabled")
    private Integer enabled;

    /** 创建时间 */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间 */
    @TableField("update_time")
    private Timestamp updateTime;
}

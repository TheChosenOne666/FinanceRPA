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
 * 密码策略配置实体（对应 sys_password_policy 表）
 *
 * <p>全局共享的单行配置（id=1），定义密码强度规则、过期天数、历史密码检查次数。
 * 由运维通过设置页统一管理，不参与租户隔离（无 org_id 字段）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_password_policy")
public class PasswordPolicyEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增，固定为 1） */
    @TableField("id")
    private Long id;

    /** 策略业务 ID（雪花算法） */
    @TableId(value = "policy_id", type = IdType.ASSIGN_ID)
    private Long policyId;

    /** 密码最小长度 */
    @TableField("min_length")
    private Integer minLength;

    /** 是否要求大写字母（0-不要求 1-要求） */
    @TableField("require_uppercase")
    private Integer requireUppercase;

    /** 是否要求小写字母（0-不要求 1-要求） */
    @TableField("require_lowercase")
    private Integer requireLowercase;

    /** 是否要求数字（0-不要求 1-要求） */
    @TableField("require_digit")
    private Integer requireDigit;

    /** 是否要求特殊字符（0-不要求 1-要求） */
    @TableField("require_special")
    private Integer requireSpecial;

    /** 允许的特殊字符集 */
    @TableField("special_chars")
    private String specialChars;

    /** 密码过期天数（0 表示不过期） */
    @TableField("expire_days")
    private Integer expireDays;

    /** 密码历史记录数（禁止重复使用最近 N 次密码，0 表示不检查） */
    @TableField("history_count")
    private Integer historyCount;

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

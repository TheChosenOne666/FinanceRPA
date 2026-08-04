package com.finrpa.notification.entity;

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
 * 通知通道 Webhook 配置实体（对应 rpa_notification_channel_config 表）
 *
 * <p>持久化企业微信 / 钉钉群机器人 Webhook URL + 加签密钥 + 启用状态。
 * 该表无 org_id 字段（全局共享配置），已加入 TenantConstant.IGNORED_TABLES。</p>
 *
 * <p>应用启动时由 {@code NotificationChannelConfigServiceImpl#init()} 从数据库加载配置，
 * 覆盖 {@code NotificationProperties} 内存值，实现运行时热生效。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_notification_channel_config")
public class NotificationChannelConfigEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 通道类型：wecom / dingtalk（唯一键） */
    @TableId(value = "channel", type = IdType.INPUT)
    private String channel;

    /** Webhook URL（空表示未配置） */
    @TableField("webhook_url")
    private String webhookUrl;

    /** 加签密钥（仅 dingtalk 使用，空表示不加签） */
    @TableField("secret")
    private String secret;

    /** 启用状态：1=启用 / 0=禁用（禁用后通道不发送通知） */
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

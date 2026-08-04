package com.finrpa.notification.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知通道信息 VO（M6.6 + P0-4 扩展）
 *
 * <p>对应 GET /api/v1/notification/channels 返回元素，
 * 展示通道类型、中文名、Webhook URL 配置状态、脱敏 Webhook URL、启用状态。</p>
 *
 * <p><b>脱敏规则</b>：webhookUrl 字段对查询参数中的敏感 token 进行掩码：
 * <ul>
 *   <li>企业微信 {@code ?key=xxx} → {@code ?key=***}</li>
 *   <li>钉钉 {@code ?access_token=xxx} → {@code ?access_token=***}</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ChannelVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 通道类型：wecom / dingtalk */
    private String channel;

    /** 通道中文名 */
    private String label;

    /** 是否已配置 Webhook URL（true=已配置 / false=未配置） */
    private Boolean configured;

    /** 脱敏后的 Webhook URL（未配置时为空串） */
    private String webhookUrl;

    /** 是否启用：true=启用 / false=禁用（禁用后通道不发送通知） */
    private Boolean enabled;
}

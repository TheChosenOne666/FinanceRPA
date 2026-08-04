package com.finrpa.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知通道 Webhook 配置保存请求（P0-4）
 *
 * <p>对应 PUT /api/v1/notification/channels/{channel} 接口入参：
 * 保存通道的 Webhook URL、加签密钥（仅钉钉）、启用状态。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ChannelConfigSaveRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Webhook URL（必填，可传空串表示清除配置） */
    @NotBlank(message = "Webhook URL 不能为 null（空串表示清除配置）")
    private String webhookUrl;

    /** 加签密钥（仅 dingtalk 使用，可选；空表示不加签） */
    private String secret;

    /** 启用状态：true=启用 / false=禁用（必填） */
    private Boolean enabled;
}

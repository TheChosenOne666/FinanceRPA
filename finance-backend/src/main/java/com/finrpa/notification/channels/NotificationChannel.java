package com.finrpa.notification.channels;

import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.enums.NotificationChannelEnum;

/**
 * 通知通道接口（M6.6）
 *
 * <p>抽象不同通道（企业微信 / 钉钉）的统一发送能力。
 * 通道实现类负责拼接 Webhook 请求体 + 调用 HTTP + 解析响应。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface NotificationChannel {

    /**
     * 获取通道枚举
     *
     * @return 通道枚举
     */
    NotificationChannelEnum getChannel();

    /**
     * 通道是否已配置（Webhook URL 非空）
     *
     * @return true=已配置 / false=未配置
     */
    boolean isConfigured();

    /**
     * 发送通知
     *
     * @param message 通知消息（含标题与 markdown 正文）
     * @return 发送结果（success / errorMessage / rawResponse）
     */
    NotificationSendResultVO send(NotificationMessage message);
}

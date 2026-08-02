package com.finrpa.notification.enums;

import com.finrpa.notification.constant.NotificationConstant;
import lombok.Getter;

/**
 * 通知通道枚举（M6.6）
 *
 * <p>支持的通知通道：企业微信群机器人、钉钉群机器人。
 * 通道启用与否由 {@code application.yml} 配置的 Webhook URL 决定，
 * URL 为空视为未配置该通道。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Getter
public enum NotificationChannelEnum {

    /** 企业微信群机器人 */
    WECOM(NotificationConstant.CHANNEL_WECOM, "企业微信群机器人"),

    /** 钉钉群机器人 */
    DINGTALK(NotificationConstant.CHANNEL_DINGTALK, "钉钉群机器人");

    /** 通道值（与 NotificationConstant 对齐） */
    private final String value;

    /** 通道中文名 */
    private final String label;

    /**
     * 构造通道枚举
     *
     * @param value 通道值
     * @param label 通道中文名
     */
    NotificationChannelEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据 value 解析通道枚举
     *
     * @param value 通道值
     * @return 通道枚举；未匹配返回 null
     */
    public static NotificationChannelEnum getEnumByValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (NotificationChannelEnum channel : NotificationChannelEnum.values()) {
            if (channel.value.equals(value)) {
                return channel;
            }
        }
        return null;
    }
}

package com.finrpa.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通知模块配置属性（M6.6）
 *
 * <p>映射 {@code application.yml} 中 {@code notification.*} 配置项，
 * Webhook URL 通过环境变量注入，支持企业微信群机器人 + 钉钉群机器人加签模式。</p>
 *
 * <p>典型配置示例：
 * <pre>{@code
 * notification:
 *   wecom:
 *     webhook-url: ${WECOM_WEBHOOK_URL:}
 *   dingtalk:
 *     webhook-url: ${DINGTALK_WEBHOOK_URL:}
 *     secret: ${DINGTALK_SECRET:}
 * }</pre>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /** 企业微信群机器人配置 */
    private WeComConfig wecom = new WeComConfig();

    /** 钉钉群机器人配置 */
    private DingTalkConfig dingtalk = new DingTalkConfig();

    /**
     * 企业微信群机器人配置
     */
    @Data
    public static class WeComConfig {

        /** 企业微信群机器人 Webhook URL（空表示未配置） */
        private String webhookUrl = "";
    }

    /**
     * 钉钉群机器人配置
     */
    @Data
    public static class DingTalkConfig {

        /** 钉钉群机器人 Webhook URL（空表示未配置） */
        private String webhookUrl = "";

        /** 钉钉加签密钥（启用加签模式时必填，空表示不加签） */
        private String secret = "";
    }
}

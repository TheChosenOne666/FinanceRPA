package com.finrpa.notification.dto;

import com.finrpa.notification.enums.NotificationTemplateEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 通知消息内部 DTO（M6.6）
 *
 * <p>由 {@code NotificationTemplateRenderer} 渲染后封装，传递给 {@code NotificationChannel} 发送。
 * title 用于钉钉 markdown 标题，content 用于企业微信 / 钉钉 markdown 正文。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板类型 */
    private NotificationTemplateEnum template;

    /** 消息标题（钉钉 markdown.title） */
    private String title;

    /** 消息正文（markdown 格式） */
    private String content;

    /** 原始模板参数（用于审计 / 日志） */
    private Map<String, Object> params;
}

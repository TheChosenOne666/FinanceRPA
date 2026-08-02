package com.finrpa.notification.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知通道信息 VO（M6.6）
 *
 * <p>对应 GET /api/v1/notification/channels 返回元素，
 * 展示通道类型、中文名、Webhook URL 配置状态（URL 不返回明文，仅返回是否已配置）。</p>
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
}

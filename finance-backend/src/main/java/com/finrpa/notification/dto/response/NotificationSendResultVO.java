package com.finrpa.notification.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知发送结果 VO（M6.6）
 *
 * <p>通道发送结果统一封装，用于测试接口返回 + 内部发送日志。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSendResultVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 通道类型：wecom / dingtalk */
    private String channel;

    /** 是否发送成功 */
    private Boolean success;

    /** 错误信息（失败时填充；成功为空） */
    private String errorMessage;

    /** 通道原始响应（用于审计 / 调试） */
    private String rawResponse;

    /**
     * 快速构建成功结果
     *
     * @param channel     通道类型
     * @param rawResponse 通道原始响应
     * @return 成功结果 VO
     */
    public static NotificationSendResultVO success(String channel, String rawResponse) {
        return NotificationSendResultVO.builder()
                .channel(channel)
                .success(true)
                .rawResponse(rawResponse)
                .build();
    }

    /**
     * 快速构建失败结果
     *
     * @param channel      通道类型
     * @param errorMessage 错误信息
     * @return 失败结果 VO
     */
    public static NotificationSendResultVO failure(String channel, String errorMessage) {
        return NotificationSendResultVO.builder()
                .channel(channel)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}

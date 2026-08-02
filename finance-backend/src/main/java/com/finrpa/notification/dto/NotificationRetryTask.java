package com.finrpa.notification.dto;

import com.finrpa.notification.enums.NotificationTemplateEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Map;

/**
 * 通知重试队列任务（M6.6 扩展）
 *
 * <p>主通道 + Fallback 全部失败时入队，Redis List {@code notification:retry_queue} 存储。
 * 重试调度器每 5 分钟扫描，最多重试 {@link com.finrpa.notification.constant.NotificationConstant#MAX_RETRY_COUNT} 次。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRetryTask implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板类型 */
    private NotificationTemplateEnum template;

    /** 模板参数 */
    private Map<String, Object> params;

    /** 关联审批单 ID（可空） */
    private Long approvalId;

    /** 关联任务 ID（可空） */
    private Long taskId;

    /** 目标用户 ID（可空，M6.6 全局 Webhook 配置下未使用） */
    private Long targetUserId;

    /** 已重试次数（首次入队为 0，每次重试 +1） */
    private int retryCount;

    /** 入队时间 */
    private Timestamp enqueuedAt;
}

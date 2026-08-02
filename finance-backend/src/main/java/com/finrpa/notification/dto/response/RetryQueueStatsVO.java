package com.finrpa.notification.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知重试队列统计 VO（M6.6 扩展）
 *
 * <p>对应 GET /api/notification/retry/stats 返回结构。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryQueueStatsVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前队列待重试任务数 */
    private long queueSize;

    /** 总尝试次数（含首次发送与所有重试） */
    private long totalAttempts;

    /** 成功次数 */
    private long successCount;

    /** 失败次数 */
    private long failureCount;

    /** 成功率（0.0 ~ 1.0） */
    private double successRate;

    /** 超过最大重试次数的告警数（待人工介入） */
    private long alertCount;
}

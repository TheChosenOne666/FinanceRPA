package com.finrpa.notification.service;

import com.finrpa.notification.entity.NotificationAttemptEO;

/**
 * 通知发送尝试记录服务（M6.6）
 *
 * <p>封装 {@code rpa_notification_attempt} 表的写入与统计查询。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface NotificationAttemptService {

    /**
     * 记录一次发送尝试
     *
     * @param attempt 尝试记录实体
     * @return 持久化后的实体（含 attempt_id）
     */
    NotificationAttemptEO record(NotificationAttemptEO attempt);

    /**
     * 统计指定时间范围内的成功率
     *
     * @param startTimestamp 开始时间戳（毫秒，可空表示不限制）
     * @param endTimestamp   结束时间戳（毫秒，可空表示不限制）
     * @return 成功率（0.0 ~ 1.0）；无记录返回 0.0
     */
    double calculateSuccessRate(Long startTimestamp, Long endTimestamp);

    /**
     * 统计指定时间范围内的总尝试次数
     *
     * @param startTimestamp 开始时间戳（毫秒，可空表示不限制）
     * @param endTimestamp   结束时间戳（毫秒，可空表示不限制）
     * @return 总尝试次数
     */
    long countAttempts(Long startTimestamp, Long endTimestamp);
}

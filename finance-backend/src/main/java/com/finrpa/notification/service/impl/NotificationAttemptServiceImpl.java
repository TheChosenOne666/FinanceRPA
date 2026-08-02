package com.finrpa.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finrpa.notification.entity.NotificationAttemptEO;
import com.finrpa.notification.mapper.NotificationAttemptMapper;
import com.finrpa.notification.service.NotificationAttemptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

/**
 * 通知发送尝试记录服务实现（M6.6）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class NotificationAttemptServiceImpl implements NotificationAttemptService {

    /** 通知尝试记录 Mapper */
    @Resource
    private NotificationAttemptMapper notificationAttemptMapper;

    /**
     * 记录一次发送尝试
     *
     * @param attempt 尝试记录实体
     * @return 持久化后的实体
     */
    @Override
    public NotificationAttemptEO record(NotificationAttemptEO attempt) {
        notificationAttemptMapper.insert(attempt);
        log.debug("通知尝试已记录: attemptId={}, channel={}, template={}, success={}",
                attempt.getAttemptId(), attempt.getChannel(), attempt.getTemplate(), attempt.getSuccess());
        return attempt;
    }

    /**
     * 统计指定时间范围内的成功率
     *
     * @param startTimestamp 开始时间戳（毫秒，可空）
     * @param endTimestamp   结束时间戳（毫秒，可空）
     * @return 成功率（0.0 ~ 1.0）；无记录返回 0.0
     */
    @Override
    public double calculateSuccessRate(Long startTimestamp, Long endTimestamp) {
        Long total = countAttempts(startTimestamp, endTimestamp);
        if (total == null || total == 0L) {
            return 0.0;
        }

        LambdaQueryWrapper<NotificationAttemptEO> wrapper = buildTimeRangeWrapper(startTimestamp, endTimestamp);
        wrapper.eq(NotificationAttemptEO::getSuccess, 1);
        Long success = notificationAttemptMapper.selectCount(wrapper);
        if (success == null || success == 0L) {
            return 0.0;
        }
        return (double) success / total;
    }

    /**
     * 统计指定时间范围内的总尝试次数
     *
     * @param startTimestamp 开始时间戳（毫秒，可空）
     * @param endTimestamp   结束时间戳（毫秒，可空）
     * @return 总尝试次数
     */
    @Override
    public long countAttempts(Long startTimestamp, Long endTimestamp) {
        LambdaQueryWrapper<NotificationAttemptEO> wrapper = buildTimeRangeWrapper(startTimestamp, endTimestamp);
        Long count = notificationAttemptMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 构建时间范围查询 Wrapper
     *
     * @param startTimestamp 开始时间戳（毫秒，可空）
     * @param endTimestamp   结束时间戳（毫秒，可空）
     * @return LambdaQueryWrapper
     */
    private LambdaQueryWrapper<NotificationAttemptEO> buildTimeRangeWrapper(Long startTimestamp, Long endTimestamp) {
        LambdaQueryWrapper<NotificationAttemptEO> wrapper = new LambdaQueryWrapper<>();
        if (startTimestamp != null) {
            wrapper.ge(NotificationAttemptEO::getCreateTime, new Timestamp(startTimestamp));
        }
        if (endTimestamp != null) {
            wrapper.le(NotificationAttemptEO::getCreateTime, new Timestamp(endTimestamp));
        }
        return wrapper;
    }
}

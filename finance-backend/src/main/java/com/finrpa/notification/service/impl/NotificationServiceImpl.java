package com.finrpa.notification.service.impl;

import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.notification.channels.NotificationChannel;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dispatcher.NotificationDispatcher;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.dto.request.NotificationTestRequest;
import com.finrpa.notification.dto.response.ChannelVO;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.dto.response.RetryQueueStatsVO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import com.finrpa.notification.service.NotificationAttemptService;
import com.finrpa.notification.service.NotificationService;
import com.finrpa.notification.templates.NotificationTemplateRenderer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知服务实现（M6.6）
 *
 * <p>持有所有 {@link NotificationChannel} 实现（Spring 自动注入 List），
 * 提供：
 * <ul>
 *   <li>{@link #listChannels()} —— 查询所有通道及其配置状态</li>
 *   <li>{@link #send(NotificationChannelEnum, NotificationTemplateEnum, Map)} —— 单通道直发（测试 API 用）</li>
 *   <li>{@link #test(NotificationTestRequest)} —— API 入口的测试发送</li>
 *   <li>{@link #dispatch(NotificationTemplateEnum, Map, Long, Long, Long)} —— 调度发送（Fallback + 重试队列）</li>
 *   <li>{@link #getRetryQueueSize()} / {@link #getRetryStats()} —— 重试队列查询</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    /** 所有通知通道实现（Spring 自动收集） */
    @Resource
    private List<NotificationChannel> channels;

    /** 通知模板渲染器 */
    @Resource
    private NotificationTemplateRenderer templateRenderer;

    /** 通知调度器（Fallback + 重试队列） */
    @Resource
    private NotificationDispatcher dispatcher;

    /** 通知尝试记录服务（用于统计查询） */
    @Resource
    private NotificationAttemptService attemptService;

    /** Redisson 客户端（查询重试队列） */
    @Resource
    private RedissonClient redissonClient;

    // region 查询通道

    /**
     * 查询所有通道及其配置状态
     *
     * @return 通道信息列表
     */
    @Override
    public List<ChannelVO> listChannels() {
        List<ChannelVO> result = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            ChannelVO vo = new ChannelVO();
            vo.setChannel(channel.getChannel().getValue());
            vo.setLabel(channel.getChannel().getLabel());
            vo.setConfigured(channel.isConfigured());
            result.add(vo);
        }
        return result;
    }

    // endregion

    // region 单通道直发（测试 API 使用）

    /**
     * 按通道 + 模板类型 + 参数发送通知（单通道直发，不触发 Fallback / 不入重试队列）
     *
     * @param channel  通道枚举
     * @param template 模板枚举
     * @param params   模板参数（可为 null）
     * @return 发送结果
     */
    @Override
    public NotificationSendResultVO send(NotificationChannelEnum channel, NotificationTemplateEnum template,
                                          Map<String, Object> params) {
        // 1. 查找通道实现
        NotificationChannel channelImpl = findChannel(channel);
        ThrowUtils.throwIf(channelImpl == null, ErrorCode.PARAMS_ERROR, "不支持的通道类型: " + channel);

        // 2. 校验通道已配置
        ThrowUtils.throwIf(!channelImpl.isConfigured(), ErrorCode.OPERATION_ERROR,
                "通道未配置 Webhook URL: " + channel);

        // 3. 渲染模板
        NotificationMessage message = templateRenderer.render(template, params);
        log.info("通知待发送: channel={}, template={}", channel.getValue(), template.getValue());

        // 4. 发送
        return channelImpl.send(message);
    }

    // endregion

    // region 测试发送

    /**
     * 测试发送（API 入口）
     *
     * @param request 测试发送请求（含通道 + 模板类型 + 参数）
     * @return 发送结果
     */
    @Override
    public NotificationSendResultVO test(NotificationTestRequest request) {
        // 1. 解析通道枚举
        NotificationChannelEnum channel = NotificationChannelEnum.getEnumByValue(request.getChannel());
        ThrowUtils.throwIf(channel == null, ErrorCode.PARAMS_ERROR,
                "无效的通道类型: " + request.getChannel());

        // 2. 解析模板枚举
        NotificationTemplateEnum template = NotificationTemplateEnum.getEnumByValue(request.getTemplateType());
        ThrowUtils.throwIf(template == null, ErrorCode.PARAMS_ERROR,
                "无效的模板类型: " + request.getTemplateType());

        // 3. 调用 send 统一发送
        log.info("通知测试发送: channel={}, template={}", request.getChannel(), request.getTemplateType());
        return send(channel, template, request.getParams());
    }

    // endregion

    // region 调度发送（Fallback + 重试队列）

    /**
     * 调度发送通知（业务流程入口）
     *
     * @param template    模板枚举
     * @param params      模板参数
     * @param approvalId  关联审批单 ID（可空）
     * @param taskId      关联任务 ID（可空）
     * @param targetUserId 目标用户 ID（可空）
     * @return 是否最终发送成功
     */
    @Override
    public boolean dispatch(NotificationTemplateEnum template, Map<String, Object> params,
                              Long approvalId, Long taskId, Long targetUserId) {
        return dispatcher.dispatch(template, params, approvalId, taskId, targetUserId, 0);
    }

    // endregion

    // region 重试队列查询

    /**
     * 查询重试队列待处理任务数
     *
     * @return 待处理任务数
     */
    @Override
    public long getRetryQueueSize() {
        RList<String> queue = redissonClient.getList(NotificationConstant.RETRY_QUEUE_KEY);
        return queue.size();
    }

    /**
     * 查询重试队列统计
     *
     * @return 统计 VO
     */
    @Override
    public RetryQueueStatsVO getRetryStats() {
        // 1. 队列长度
        long queueSize = getRetryQueueSize();

        // 2. 总尝试次数（不限时间范围，全量统计）
        long totalAttempts = attemptService.countAttempts(null, null);

        // 3. 成功率 + 成功次数
        double successRate = totalAttempts > 0
                ? attemptService.calculateSuccessRate(null, null)
                : 0.0;
        long successCount = totalAttempts > 0 ? Math.round(successRate * totalAttempts) : 0L;

        // 4. 失败次数
        long failureCount = totalAttempts - successCount;

        // 5. 告警数（待人工介入）：队列中所有任务视为待告警
        // （调度器会按 retryCount 判定是否真正超过 MAX_RETRY_COUNT 阈值）
        long alertCount = queueSize;

        return RetryQueueStatsVO.builder()
                .queueSize(queueSize)
                .totalAttempts(totalAttempts)
                .successCount(successCount)
                .failureCount(failureCount)
                .successRate(successRate)
                .alertCount(alertCount)
                .build();
    }

    // endregion

    // region 私有方法

    /**
     * 根据通道枚举查找通道实现
     *
     * @param channel 通道枚举
     * @return 通道实现；未找到返回 null
     */
    private NotificationChannel findChannel(NotificationChannelEnum channel) {
        for (NotificationChannel impl : channels) {
            if (impl.getChannel() == channel) {
                return impl;
            }
        }
        return null;
    }

    // endregion
}

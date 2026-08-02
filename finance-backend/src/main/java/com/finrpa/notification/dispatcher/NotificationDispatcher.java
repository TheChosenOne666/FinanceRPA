package com.finrpa.notification.dispatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.notification.channels.NotificationChannel;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.dto.NotificationRetryTask;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.entity.NotificationAttemptEO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import com.finrpa.notification.service.NotificationAttemptService;
import com.finrpa.notification.templates.NotificationTemplateRenderer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 通知调度器（M6.6 扩展 Fallback + Redis 重试队列）
 *
 * <p>核心调度流程：
 * <ol>
 *   <li>渲染模板 → 生成 {@link NotificationMessage}</li>
 *   <li>遍历已配置通道列表（企微 → 钉钉），逐个发送；任一通道成功即终止</li>
 *   <li>所有通道尝试均失败 / 无通道配置 → 入 Redis 重试队列</li>
 *   <li>每次通道发送尝试均写入 {@code rpa_notification_attempt} 表（审计追踪）</li>
 * </ol>
 * </p>
 *
 * <p>重试调度由 {@code NotificationRetryScheduler} 每 5 分钟扫描队列，
 * 调用 {@link #processRetryTask(NotificationRetryTask)} 处理。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class NotificationDispatcher {

    /** 所有通知通道实现（Spring 自动收集，顺序由 @Order 或 Bean 加载顺序决定） */
    @Resource
    private List<NotificationChannel> channels;

    /** 模板渲染器 */
    @Resource
    private NotificationTemplateRenderer templateRenderer;

    /** 通知尝试记录服务（持久化每次发送尝试） */
    @Resource
    private NotificationAttemptService attemptService;

    /** Redisson 客户端（操作重试队列） */
    @Resource
    private RedissonClient redissonClient;

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    // region 对外调度入口

    /**
     * 调度发送通知（含 Fallback 机制）
     *
     * <p>业务流程入口（审批触发 / 调度器重试）统一调用此方法。</p>
     *
     * @param template 模板类型
     * @param params    模板参数
     * @param approvalId 关联审批单 ID（可空）
     * @param taskId    关联任务 ID（可空）
     * @param targetUserId 目标用户 ID（可空）
     * @param retryCount 已重试次数（首次发送为 0）
     * @return 是否最终发送成功（true=任一通道成功 / false=全部失败已入队）
     */
    public boolean dispatch(NotificationTemplateEnum template, Map<String, Object> params,
                              Long approvalId, Long taskId, Long targetUserId, int retryCount) {
        log.info("[Dispatcher] 开始调度通知: template={}, approvalId={}, taskId={}, retryCount={}",
                template.getValue(), approvalId, taskId, retryCount);

        // 1. 渲染模板
        NotificationMessage message;
        try {
            message = templateRenderer.render(template, params);
        } catch (Exception e) {
            log.error("[Dispatcher] 模板渲染失败，跳过发送: template={}, error={}",
                    template.getValue(), e.getMessage(), e);
            return false;
        }

        // 2. 逐个通道发送（Fallback：任一成功即终止）
        for (NotificationChannel channel : channels) {
            if (!channel.isConfigured()) {
                log.debug("[Dispatcher] 通道未配置，跳过: channel={}", channel.getChannel().getValue());
                continue;
            }

            NotificationSendResultVO result = channel.send(message);
            // 3. 写尝试记录（审计）
            recordAttempt(channel.getChannel(), template, message, result, approvalId, taskId,
                    targetUserId, retryCount, retryCount == 0 ? null : new Timestamp(System.currentTimeMillis()));

            if (Boolean.TRUE.equals(result.getSuccess())) {
                log.info("[Dispatcher] 通知发送成功: channel={}, template={}",
                        channel.getChannel().getValue(), template.getValue());
                return true;
            }
            log.warn("[Dispatcher] 通道发送失败，尝试 Fallback: channel={}, error={}",
                    channel.getChannel().getValue(), result.getErrorMessage());
        }

        // 4. 所有通道均失败 / 无通道配置 → 入重试队列
        enqueueRetryTask(template, params, approvalId, taskId, targetUserId, retryCount);
        return false;
    }

    // endregion

    // region 重试队列消费

    /**
     * 处理重试任务（由调度器调用）
     *
     * @param retryTask 重试任务
     * @return 是否最终成功
     */
    public boolean processRetryTask(NotificationRetryTask retryTask) {
        int newRetryCount = retryTask.getRetryCount() + 1;

        // 1. 超过最大重试次数 → 告警人工介入，不再重试
        if (newRetryCount > NotificationConstant.MAX_RETRY_COUNT) {
            log.error("[Dispatcher] 重试次数超过阈值，告警人工介入: template={}, retryCount={}, threshold={}",
                    retryTask.getTemplate().getValue(), newRetryCount, NotificationConstant.MAX_RETRY_COUNT);
            return false;
        }

        log.info("[Dispatcher] 开始处理重试任务: template={}, retryCount={}",
                retryTask.getTemplate().getValue(), newRetryCount);

        // 2. 调用 dispatch 执行实际发送
        return dispatch(retryTask.getTemplate(), retryTask.getParams(),
                retryTask.getApprovalId(), retryTask.getTaskId(), retryTask.getTargetUserId(),
                newRetryCount);
    }

    // endregion

    // region 私有方法

    /**
     * 写入通知尝试记录
     *
     * @param channel       通道枚举
     * @param template      模板枚举
     * @param message       渲染后的消息（含 title + content）
     * @param result        发送结果
     * @param approvalId    关联审批单 ID（可空）
     * @param taskId        关联任务 ID（可空）
     * @param targetUserId  目标用户 ID（可空）
     * @param retryCount    重试次数
     * @param enqueuedAt    入队时间（首次发送为 null）
     */
    private void recordAttempt(NotificationChannelEnum channel, NotificationTemplateEnum template,
                                 NotificationMessage message, NotificationSendResultVO result,
                                 Long approvalId, Long taskId, Long targetUserId,
                                 int retryCount, Timestamp enqueuedAt) {
        try {
            NotificationAttemptEO attempt = new NotificationAttemptEO();
            attempt.setApprovalId(approvalId);
            attempt.setTaskId(taskId);
            attempt.setTargetUserId(targetUserId);
            attempt.setChannel(channel.getValue());
            attempt.setTemplate(template.getValue());
            attempt.setTitle(message.getTitle());
            attempt.setContent(message.getContent());
            attempt.setSuccess(Boolean.TRUE.equals(result.getSuccess()) ? 1 : 0);
            attempt.setErrorMessage(result.getErrorMessage());
            attempt.setRawResponse(result.getRawResponse());
            attempt.setRetryCount(retryCount);
            attempt.setEnqueuedAt(enqueuedAt);
            attempt.setSentAt(new Timestamp(System.currentTimeMillis()));
            attemptService.record(attempt);
        } catch (Exception e) {
            // 审计记录失败不影响主流程，仅记录日志
            log.error("[Dispatcher] 通知尝试记录写入失败: channel={}, template={}, error={}",
                    channel.getValue(), template.getValue(), e.getMessage(), e);
        }
    }

    /**
     * 入队重试任务
     *
     * @param template     模板类型
     * @param params       模板参数
     * @param approvalId   关联审批单 ID
     * @param taskId       关联任务 ID
     * @param targetUserId 目标用户 ID
     * @param retryCount   当前已重试次数
     */
    private void enqueueRetryTask(NotificationTemplateEnum template, Map<String, Object> params,
                                    Long approvalId, Long taskId, Long targetUserId, int retryCount) {
        try {
            NotificationRetryTask retryTask = NotificationRetryTask.builder()
                    .template(template)
                    .params(params)
                    .approvalId(approvalId)
                    .taskId(taskId)
                    .targetUserId(targetUserId)
                    .retryCount(retryCount)
                    .enqueuedAt(new Timestamp(System.currentTimeMillis()))
                    .build();

            String json = objectMapper.writeValueAsString(retryTask);
            RList<String> queue = redissonClient.getList(NotificationConstant.RETRY_QUEUE_KEY);
            queue.add(json);

            log.warn("[Dispatcher] 通知已入重试队列: template={}, retryCount={}, queueSize={}",
                    template.getValue(), retryCount, queue.size());
        } catch (JsonProcessingException e) {
            log.error("[Dispatcher] 重试任务序列化失败: template={}, error={}",
                    template.getValue(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("[Dispatcher] 入重试队列失败: template={}, error={}",
                    template.getValue(), e.getMessage(), e);
        }
    }

    // endregion
}

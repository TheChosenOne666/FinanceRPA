package com.finrpa.notification.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dispatcher.NotificationDispatcher;
import com.finrpa.notification.dto.NotificationRetryTask;
import com.finrpa.system.service.SystemConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知重试调度器（M6.6 扩展 system-design.md 6.10.3）
 *
 * <p>基于 Spring {@link Scheduled} + ShedLock {@link SchedulerLock} 实现分布式定时任务，
 * 每 5 分钟扫描一次 Redis List {@code notification:retry_queue}，逐个出队并重试。</p>
 *
 * <p>重试策略：
 * <ul>
 *   <li>最多重试 {@link NotificationConstant#MAX_RETRY_COUNT} 次（含首次发送）</li>
 *   <li>超过阈值则告警人工介入，不再重试</li>
 *   <li>重试成功后任务不再入队</li>
 *   <li>重试失败且未超阈值：dispatch 内部会再次入队（retryCount + 1）</li>
 * </ul>
 * </p>
 *
 * <p>ShedLock 保证集群部署下同一时刻仅单节点执行扫描，
 * 锁持有时间 4 分钟（避免下次扫描周期触发时锁未释放），
 * 最短持有 10 秒（避免任务快速失败后立刻释放锁导致其他节点抢锁）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class NotificationRetryScheduler {

    /** 通知调度器（重试处理委托） */
    @Resource
    private NotificationDispatcher dispatcher;

    /** Redisson 客户端 */
    @Resource
    private RedissonClient redissonClient;

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    /** 系统配置服务（P3 OPS-2 启停开关 + 重试次数读取） */
    @Resource
    private SystemConfigService systemConfigService;

    /**
     * 扫描并处理重试队列
     *
     * <p>cron 表达式 {@link NotificationConstant#RETRY_SCHEDULER_CRON}（每 5 分钟第 0 秒执行一次）。
     * ShedLock 锁名称 {@link NotificationConstant#RETRY_SCHEDULER_LOCK_NAME}，
     * 锁最大持有时间 4 分钟，最短持有 10 秒。</p>
     */
    @Scheduled(cron = NotificationConstant.RETRY_SCHEDULER_CRON)
    @SchedulerLock(
            name = NotificationConstant.RETRY_SCHEDULER_LOCK_NAME,
            lockAtMostFor = "PT4M",
            lockAtLeastFor = "PT10S"
    )
    public void scanRetryQueue() {
        // P3 OPS-2：检查启停开关（关闭时跳过本次扫描）
        boolean enabled = systemConfigService.getBoolean("scheduler.notification_retry.enabled", true);
        if (!enabled) {
            log.debug("[RetryScheduler] 通知重试扫描已禁用，跳过本次执行");
            return;
        }

        long startMs = System.currentTimeMillis();
        RList<String> queue = redissonClient.getList(NotificationConstant.RETRY_QUEUE_KEY);
        int queueSize = queue.size();

        if (queueSize == 0) {
            log.debug("[RetryScheduler] 重试队列为空，跳过扫描");
            return;
        }

        log.info("[RetryScheduler] 开始扫描重试队列: queueSize={}", queueSize);

        // 1. 取出当前所有任务（快照），避免遍历时新任务入队导致 ConcurrentModification
        List<String> snapshot = queue.readAll();
        int processed = 0;
        int success = 0;
        int overLimit = 0;

        for (String json : snapshot) {
            try {
                // 2. 反序列化重试任务
                NotificationRetryTask retryTask = objectMapper.readValue(json, NotificationRetryTask.class);

                // 3. 出队（删除当前任务，避免重复处理；dispatch 失败时会重新入队）
                queue.remove(json);

                // 4. 处理重试任务
                boolean ok = dispatcher.processRetryTask(retryTask);
                processed++;
                if (ok) {
                    success++;
                } else if (retryTask.getRetryCount() + 1 >= systemConfigService.getInteger("scheduler.notification_retry.max_count", NotificationConstant.MAX_RETRY_COUNT)) {
                    // 超过最大重试次数阈值，告警人工介入
                    overLimit++;
                    log.error("[RetryScheduler] 任务超过最大重试次数，需人工介入: template={}, retryCount={}",
                            retryTask.getTemplate().getValue(), retryTask.getRetryCount() + 1);
                }
            } catch (JsonProcessingException e) {
                // 反序列化失败：从队列移除，记录错误日志
                queue.remove(json);
                log.error("[RetryScheduler] 重试任务反序列化失败，已丢弃: json={}, error={}",
                        json, e.getMessage(), e);
            } catch (Exception e) {
                log.error("[RetryScheduler] 重试任务处理异常: errorType={}, error={}",
                        e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("[RetryScheduler] 重试队列扫描完成: processed={}, success={}, overLimit={}, elapsed={}ms",
                processed, success, overLimit, elapsedMs);
    }
}

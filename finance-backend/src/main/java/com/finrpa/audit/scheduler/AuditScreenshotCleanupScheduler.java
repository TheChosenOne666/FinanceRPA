package com.finrpa.audit.scheduler;

import com.finrpa.audit.constant.AuditConstant;
import com.finrpa.audit.storage.MinioStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计截图过期清理调度器（M7.2，系统设计 6.4.4）
 *
 * <p>基于 Spring {@link Scheduled} + ShedLock {@link SchedulerLock} 实现分布式定时任务，
 * 每日凌晨 3 点扫描所有审计 bucket，删除超过保留期（默认 90 天）的截图对象。</p>
 *
 * <p>ShedLock 保证集群部署下同一时刻仅单节点执行清理，
 * 锁最大持有时间 30 分钟（清理大量对象可能较慢），最短持有 10 秒。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class AuditScreenshotCleanupScheduler {

    /** MinIO 截图存储服务 */
    @Resource
    private MinioStorageService minioStorageService;

    /**
     * 清理过期截图
     *
     * <p>cron 表达式 {@link AuditConstant#SCREENSHOT_CLEANUP_CRON}：每日凌晨 3 点执行。
     * ShedLock 锁名称 {@link AuditConstant#SCREENSHOT_CLEANUP_LOCK_NAME}。</p>
     */
    @Scheduled(cron = AuditConstant.SCREENSHOT_CLEANUP_CRON)
    @SchedulerLock(
            name = AuditConstant.SCREENSHOT_CLEANUP_LOCK_NAME,
            lockAtMostFor = AuditConstant.SCREENSHOT_CLEANUP_LOCK_AT_MOST,
            lockAtLeastFor = AuditConstant.SCREENSHOT_CLEANUP_LOCK_AT_LEAST
    )
    public void cleanupExpiredScreenshots() {
        long startMs = System.currentTimeMillis();
        try {
            int deleted = minioStorageService.deleteExpiredObjects();
            long elapsedMs = System.currentTimeMillis() - startMs;
            if (deleted > 0) {
                log.info("[AuditScreenshotCleanup] 过期截图清理完成: deleted={}, elapsed={}ms", deleted, elapsedMs);
            } else {
                log.debug("[AuditScreenshotCleanup] 过期截图清理完成: 无过期对象, elapsed={}ms", elapsedMs);
            }
        } catch (Exception e) {
            log.error("[AuditScreenshotCleanup] 过期截图清理异常: errorType={}, error={}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}

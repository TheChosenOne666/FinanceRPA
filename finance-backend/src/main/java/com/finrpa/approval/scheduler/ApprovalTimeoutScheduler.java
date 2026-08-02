package com.finrpa.approval.scheduler;

import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.service.ApprovalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批超时检测调度器（M6.4）
 *
 * <p>基于 Spring {@link Scheduled} + ShedLock {@link SchedulerLock} 实现分布式定时任务，
 * 每分钟扫描一次 PENDING 状态的审批单，将已超时的审批单标记为 TIMEOUT 并触发后续处理。</p>
 *
 * <p>超时阈值（M6.4）：
 * <ul>
 *   <li>high 风险（部门审批）：30 分钟</li>
 *   <li>critical 风险（合规审计部审批）：60 分钟</li>
 * </ul>
 * </p>
 *
 * <p>超时处理流程（委托 {@link ApprovalService#processTimeoutApprovals()}）：
 * <ol>
 *   <li>标记审批单状态为 TIMEOUT</li>
 *   <li>更新 Java 任务状态为 ABORTED</li>
 *   <li>发布 Pub/Sub 通知（唤醒等待线程 + 通知前端）</li>
 *   <li>通知 Python 终止任务（防御性调用）</li>
 * </ol>
 * </p>
 *
 * <p>ShedLock 保证集群部署下同一时刻仅单节点执行扫描，
 * 锁持有时间 30 秒（{@link ApprovalConstant#TIMEOUT_SCHEDULER_LOCK_AT_MOST}），
 * 最短持有 5 秒（{@link ApprovalConstant#TIMEOUT_SCHEDULER_LOCK_AT_LEAST}）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class ApprovalTimeoutScheduler {

    /** 审批服务（超时处理委托） */
    @Resource
    private ApprovalService approvalService;

    /**
     * 扫描并处理超时审批单
     *
     * <p>cron 表达式 {@code 0 * * * * ?}：每分钟第 0 秒执行一次。
     * ShedLock 锁名称 {@link ApprovalConstant#TIMEOUT_SCHEDULER_LOCK_NAME}，
     * 锁最大持有时间 30 秒，最短持有 5 秒。</p>
     */
    @Scheduled(cron = ApprovalConstant.TIMEOUT_SCHEDULER_CRON)
    @SchedulerLock(
            name = ApprovalConstant.TIMEOUT_SCHEDULER_LOCK_NAME,
            lockAtMostFor = "PT30S",
            lockAtLeastFor = "PT5S"
    )
    public void scanTimeoutApprovals() {
        long startMs = System.currentTimeMillis();
        try {
            int count = approvalService.processTimeoutApprovals();
            long elapsedMs = System.currentTimeMillis() - startMs;
            if (count > 0) {
                log.info("[ApprovalTimeoutScheduler] 超时审批扫描完成: processed={}, elapsed={}ms",
                        count, elapsedMs);
            } else {
                log.debug("[ApprovalTimeoutScheduler] 超时审批扫描完成: 无超时审批, elapsed={}ms", elapsedMs);
            }
        } catch (Exception e) {
            log.error("[ApprovalTimeoutScheduler] 超时审批扫描异常: errorType={}, error={}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}

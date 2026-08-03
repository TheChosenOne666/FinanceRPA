package com.finrpa.dashboard.event;

import com.finrpa.agent.event.TaskTerminalEvent;
import com.finrpa.dashboard.service.DashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 大屏缓存失效监听器（M8.1，对齐系统设计 6.9.2 缓存失效策略）
 *
 * <p>监听 {@link TaskTerminalEvent}（任务进入终态时由 TaskServiceImpl 发布），
 * 主动失效该组织的全部大屏统计缓存，确保下次查询拿到最新数据。</p>
 *
 * <p>同步执行：deleteByPattern 仅删除少量缓存 key（毫秒级），不影响 Python 回调主流程。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class DashboardCacheRefreshListener {

    /** 大屏服务 */
    @Resource
    private DashboardService dashboardService;

    /**
     * 任务终态事件处理：失效该组织的大屏缓存
     *
     * @param event 任务终态事件
     */
    @EventListener
    public void onTaskTerminal(TaskTerminalEvent event) {
        Long orgId = event.getOrgId();
        if (orgId == null) {
            log.warn("[Dashboard] 任务终态事件缺少 orgId，跳过缓存失效: taskId={}", event.getTaskId());
            return;
        }
        log.info("[Dashboard] 收到任务终态事件，失效缓存: taskId={}, orgId={}, status={}",
                event.getTaskId(), orgId, event.getNewStatus());
        dashboardService.invalidateCache(orgId);
    }
}

package com.finrpa.agent.service;

import com.finrpa.agent.dto.request.AuditLogCreateRequest;

/**
 * 审计日志服务接口
 *
 * <p>负责任务执行过程中的审计日志记录，由 Python Executor 回调触发。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface AuditLogService {

    /**
     * 创建审计日志（Python 回调）
     *
     * @param request 审计日志创建请求
     * @return 是否创建成功
     */
    boolean createAuditLog(AuditLogCreateRequest request);
}

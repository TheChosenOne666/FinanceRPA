package com.finrpa.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.audit.dto.request.AuditLogCreateRequest;
import com.finrpa.audit.dto.request.AuditLogQueryRequest;
import com.finrpa.audit.dto.response.AuditLogVO;

/**
 * 审计日志服务接口（M7.1）
 *
 * <p>负责任务执行过程中的审计日志记录（Python 回调）与对外多维检索。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface AuditLogService {

    /**
     * 创建审计日志（Python 回调）
     *
     * <p>actionParams 经 SanitizeService 脱敏后持久化。</p>
     *
     * @param request 审计日志创建请求
     * @return 是否创建成功
     */
    boolean createAuditLog(AuditLogCreateRequest request);

    /**
     * 分页多维检索审计日志
     *
     * @param queryRequest 检索请求
     * @return 审计日志分页列表
     */
    IPage<AuditLogVO> listAuditLogs(AuditLogQueryRequest queryRequest);

    /**
     * 按 auditId 查询审计日志详情
     *
     * @param auditId 审计日志业务 ID
     * @return 审计日志视图对象
     */
    AuditLogVO getAuditLogDetail(Long auditId);
}

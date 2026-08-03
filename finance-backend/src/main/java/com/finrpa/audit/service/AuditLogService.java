package com.finrpa.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.audit.dto.request.AuditLogCreateRequest;
import com.finrpa.audit.dto.request.AuditLogQueryRequest;
import com.finrpa.audit.dto.response.AuditLogVO;

import java.util.List;

/**
 * 审计日志服务接口（M7.1）
 *
 * <p>负责任务执行过程中的审计日志记录（Python 回调）与对外多维检索。</p>
 *
 * <p>M7.4 扩展：新增 {@link #exportAuditLogs} 用于 CSV 导出不分页查询，
 * {@link #listAuditLogs} 增强 sortField/sortOrder 动态排序支持。</p>
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
     * 分页多维检索审计日志（M7.4 增强排序）
     *
     * <p>支持 sortField/sortOrder 动态排序，字段必须命中白名单
     * （{@link com.finrpa.audit.constant.AuditConstant#ALLOWED_SORT_FIELDS}），
     * 默认按创建时间倒序。</p>
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

    /**
     * 导出审计日志列表（不分页，M7.4）
     *
     * <p>用于 CSV 导出场景，按查询条件全量检索（最多 {@link com.finrpa.audit.constant.AuditConstant#EXPORT_MAX_ROWS} 条）。
     * 排序规则与 {@link #listAuditLogs} 一致。actionParams 已脱敏。</p>
     *
     * @param queryRequest 检索请求
     * @return 审计日志视图对象列表（按排序字段有序）
     */
    List<AuditLogVO> exportAuditLogs(AuditLogQueryRequest queryRequest);
}

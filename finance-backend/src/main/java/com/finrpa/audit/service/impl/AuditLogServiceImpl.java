package com.finrpa.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.audit.constant.AuditConstant;
import com.finrpa.audit.dto.request.AuditLogCreateRequest;
import com.finrpa.audit.dto.request.AuditLogQueryRequest;
import com.finrpa.audit.dto.response.AuditLogVO;
import com.finrpa.audit.entity.AuditLogEO;
import com.finrpa.audit.mapper.AuditLogMapper;
import com.finrpa.audit.service.AuditLogService;
import com.finrpa.audit.service.SanitizeService;
import com.finrpa.common.constant.CommonConstant;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 审计日志服务实现（M7.1，M7.4 增强）
 *
 * <p>M7.4 扩展：
 * <ul>
 *   <li>抽取 {@link #buildQueryWrapper} / {@link #applySort} 私有方法，分页与导出复用</li>
 *   <li>{@link #listAuditLogs} 支持 sortField/sortOrder 动态排序（白名单校验）</li>
 *   <li>新增 {@link #exportAuditLogs} 不分页查询（用于 CSV 导出，限制最大条数）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    /** 审计日志 Mapper */
    @Resource
    private AuditLogMapper auditLogMapper;

    /** 脱敏服务 */
    @Resource
    private SanitizeService sanitizeService;

    // region 创建审计日志

    /**
     * 创建审计日志（Python 回调）
     *
     * @param request 审计日志创建请求
     * @return 是否创建成功
     */
    @Override
    public boolean createAuditLog(AuditLogCreateRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "审计日志请求不能为空");
        ThrowUtils.throwIf(request.getTaskId() == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        ThrowUtils.throwIf(request.getOrgId() == null, ErrorCode.PARAMS_ERROR, "组织 ID 不能为空");
        ThrowUtils.throwIf(request.getActionType() == null || request.getActionType().isBlank(),
                ErrorCode.PARAMS_ERROR, "动作类型不能为空");

        // 2. 构建审计日志实体
        AuditLogEO auditLog = new AuditLogEO();
        BeanUtils.copyProperties(request, auditLog);
        // executionResult 默认值处理
        if (auditLog.getExecutionResult() == null || auditLog.getExecutionResult().isBlank()) {
            auditLog.setExecutionResult(AuditConstant.RESULT_SUCCESS);
        }
        // actionParams 脱敏后存储
        if (auditLog.getActionParams() != null && !auditLog.getActionParams().isBlank()) {
            auditLog.setActionParams(sanitizeService.sanitizeActionParams(auditLog.getActionParams()));
        }

        // 3. 保存审计日志
        int rows = auditLogMapper.insert(auditLog);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "审计日志保存失败");

        log.info("审计日志保存成功: auditId={}, taskId={}, actionType={}, result={}",
                auditLog.getAuditId(), auditLog.getTaskId(),
                auditLog.getActionType(), auditLog.getExecutionResult());
        return true;
    }

    // endregion

    // region 多维检索

    /**
     * 分页多维检索审计日志（M7.4 增强排序）
     *
     * @param queryRequest 检索请求
     * @return 审计日志分页列表
     */
    @Override
    public IPage<AuditLogVO> listAuditLogs(AuditLogQueryRequest queryRequest) {
        Page<AuditLogEO> page = new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize());
        LambdaQueryWrapper<AuditLogEO> wrapper = buildQueryWrapper(queryRequest);
        applySort(wrapper, queryRequest.getSortField(), queryRequest.getSortOrder());

        IPage<AuditLogEO> eoPage = auditLogMapper.selectPage(page, wrapper);
        return eoPage.convert(this::convertToVO);
    }

    // endregion

    // region 导出查询（M7.4）

    /**
     * 导出审计日志列表（不分页，M7.4）
     *
     * <p>按查询条件全量检索，最多 {@link AuditConstant#EXPORT_MAX_ROWS} 条，
     * 防止 OOM。排序规则同 {@link #listAuditLogs}。</p>
     *
     * @param queryRequest 检索请求
     * @return 审计日志视图对象列表
     */
    @Override
    public List<AuditLogVO> exportAuditLogs(AuditLogQueryRequest queryRequest) {
        LambdaQueryWrapper<AuditLogEO> wrapper = buildQueryWrapper(queryRequest);
        applySort(wrapper, queryRequest.getSortField(), queryRequest.getSortOrder());

        // 限制最大条数，防止 OOM（使用 last 拼接 LIMIT）
        wrapper.last("LIMIT " + AuditConstant.EXPORT_MAX_ROWS);

        List<AuditLogEO> eoList = auditLogMapper.selectList(wrapper);
        if (eoList == null || eoList.isEmpty()) {
            return Collections.emptyList();
        }
        log.info("导出审计日志: 条数={}, orgId={}, taskId={}",
                eoList.size(), queryRequest.getOrgId(), queryRequest.getTaskId());
        return eoList.stream().map(this::convertToVO).toList();
    }

    // endregion

    // region 详情查询

    /**
     * 按 auditId 查询审计日志详情
     *
     * @param auditId 审计日志业务 ID
     * @return 审计日志视图对象
     */
    @Override
    public AuditLogVO getAuditLogDetail(Long auditId) {
        AuditLogEO auditLog = auditLogMapper.selectById(auditId);
        ThrowUtils.throwIf(auditLog == null, ErrorCode.NOT_FOUND_ERROR, "审计日志不存在: " + auditId);
        return convertToVO(auditLog);
    }

    // endregion

    // region 私有方法

    /**
     * 构建多维检索查询条件（M7.4 抽取复用）
     *
     * <p>过滤维度：组织 / 任务 / 用户 / 部门 / 业务线 / 风险等级 / 操作类型 / 执行结果 / 时间范围</p>
     *
     * @param queryRequest 检索请求
     * @return LambdaQueryWrapper
     */
    private LambdaQueryWrapper<AuditLogEO> buildQueryWrapper(AuditLogQueryRequest queryRequest) {
        LambdaQueryWrapper<AuditLogEO> wrapper = new LambdaQueryWrapper<>();

        // 1. 按组织过滤（必填，租户隔离）
        if (queryRequest.getOrgId() != null) {
            wrapper.eq(AuditLogEO::getOrgId, queryRequest.getOrgId());
        }
        // 2. 按任务 ID 过滤
        if (queryRequest.getTaskId() != null) {
            wrapper.eq(AuditLogEO::getTaskId, queryRequest.getTaskId());
        }
        // 3. 按用户 ID 过滤
        if (queryRequest.getUserId() != null) {
            wrapper.eq(AuditLogEO::getUserId, queryRequest.getUserId());
        }
        // 4. 按部门 ID 过滤
        if (queryRequest.getDepartmentId() != null) {
            wrapper.eq(AuditLogEO::getDepartmentId, queryRequest.getDepartmentId());
        }
        // 5. 按业务线 ID 过滤
        if (queryRequest.getBusinessLineId() != null) {
            wrapper.eq(AuditLogEO::getBusinessLineId, queryRequest.getBusinessLineId());
        }
        // 6. 按风险等级过滤
        if (queryRequest.getRiskLevel() != null && !queryRequest.getRiskLevel().isBlank()) {
            wrapper.eq(AuditLogEO::getRiskLevel, queryRequest.getRiskLevel());
        }
        // 7. 按操作类型过滤
        if (queryRequest.getActionType() != null && !queryRequest.getActionType().isBlank()) {
            wrapper.eq(AuditLogEO::getActionType, queryRequest.getActionType());
        }
        // 8. 按执行结果过滤
        if (queryRequest.getExecutionResult() != null && !queryRequest.getExecutionResult().isBlank()) {
            wrapper.eq(AuditLogEO::getExecutionResult, queryRequest.getExecutionResult());
        }
        // 9. 按时间范围过滤（started_at 区间）
        if (queryRequest.getStartTime() != null) {
            wrapper.ge(AuditLogEO::getStartedAt, queryRequest.getStartTime());
        }
        if (queryRequest.getEndTime() != null) {
            wrapper.le(AuditLogEO::getStartedAt, queryRequest.getEndTime());
        }
        return wrapper;
    }

    /**
     * 应用动态排序（M7.4）
     *
     * <p>字段必须命中 {@link AuditConstant#ALLOWED_SORT_FIELDS} 白名单，
     * 非法字段或为空时默认按创建时间倒序（与 M7.1 行为保持一致）。</p>
     *
     * @param wrapper    查询条件
     * @param sortField  排序字段
     * @param sortOrder  排序顺序（ascend/descend）
     */
    private void applySort(LambdaQueryWrapper<AuditLogEO> wrapper, String sortField, String sortOrder) {
        boolean isDesc = !CommonConstant.SORT_ORDER_ASC.equalsIgnoreCase(sortOrder);
        // 排序字段为空或不在白名单内，默认按创建时间倒序
        if (sortField == null || sortField.isBlank() || !AuditConstant.ALLOWED_SORT_FIELDS.contains(sortField)) {
            wrapper.orderByDesc(AuditLogEO::getCreateTime);
            return;
        }
        // 按白名单字段动态排序
        switch (sortField) {
            case AuditConstant.SORT_FIELD_AUDIT_ID -> {
                if (isDesc) wrapper.orderByDesc(AuditLogEO::getAuditId);
                else wrapper.orderByAsc(AuditLogEO::getAuditId);
            }
            case AuditConstant.SORT_FIELD_TASK_ID -> {
                if (isDesc) wrapper.orderByDesc(AuditLogEO::getTaskId);
                else wrapper.orderByAsc(AuditLogEO::getTaskId);
            }
            case AuditConstant.SORT_FIELD_RISK_LEVEL -> {
                if (isDesc) wrapper.orderByDesc(AuditLogEO::getRiskLevel);
                else wrapper.orderByAsc(AuditLogEO::getRiskLevel);
            }
            case AuditConstant.SORT_FIELD_STARTED_AT -> {
                if (isDesc) wrapper.orderByDesc(AuditLogEO::getStartedAt);
                else wrapper.orderByAsc(AuditLogEO::getStartedAt);
            }
            case AuditConstant.SORT_FIELD_DURATION_MS -> {
                if (isDesc) wrapper.orderByDesc(AuditLogEO::getDurationMs);
                else wrapper.orderByAsc(AuditLogEO::getDurationMs);
            }
            case AuditConstant.SORT_FIELD_CREATE_TIME -> {
                if (isDesc) wrapper.orderByDesc(AuditLogEO::getCreateTime);
                else wrapper.orderByAsc(AuditLogEO::getCreateTime);
            }
            default -> wrapper.orderByDesc(AuditLogEO::getCreateTime);
        }
    }

    /**
     * 实体转 VO
     *
     * @param auditLog 审计日志实体
     * @return 审计日志视图对象
     */
    private AuditLogVO convertToVO(AuditLogEO auditLog) {
        if (auditLog == null) {
            return null;
        }
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(auditLog, vo);
        return vo;
    }

    // endregion
}

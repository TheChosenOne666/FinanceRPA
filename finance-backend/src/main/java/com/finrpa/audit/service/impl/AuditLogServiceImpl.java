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
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务实现（M7.1）
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
     * 分页多维检索审计日志
     *
     * @param queryRequest 检索请求
     * @return 审计日志分页列表
     */
    @Override
    public IPage<AuditLogVO> listAuditLogs(AuditLogQueryRequest queryRequest) {
        Page<AuditLogEO> page = new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize());
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

        // 10. 按创建时间倒序
        wrapper.orderByDesc(AuditLogEO::getCreateTime);

        IPage<AuditLogEO> eoPage = auditLogMapper.selectPage(page, wrapper);
        return eoPage.convert(this::convertToVO);
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

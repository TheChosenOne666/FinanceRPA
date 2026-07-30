package com.finrpa.agent.service.impl;

import com.finrpa.agent.dto.request.AuditLogCreateRequest;
import com.finrpa.agent.entity.AuditLogEO;
import com.finrpa.agent.mapper.AuditLogMapper;
import com.finrpa.agent.service.AuditLogService;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务实现
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
            auditLog.setExecutionResult("success");
        }

        // 3. 保存审计日志
        int rows = auditLogMapper.insert(auditLog);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "审计日志保存失败");

        log.info("审计日志保存成功: auditId={}, taskId={}, actionType={}, result={}",
                auditLog.getAuditId(), auditLog.getTaskId(),
                auditLog.getActionType(), auditLog.getExecutionResult());
        return true;
    }
}

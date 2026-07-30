package com.finrpa.agent.service.impl;

import com.finrpa.agent.dto.request.AuditLogCreateRequest;
import com.finrpa.agent.entity.AuditLogEO;
import com.finrpa.agent.mapper.AuditLogMapper;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务实现单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082342545947660289L;

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    // region createAuditLog 成功场景

    @Test
    @DisplayName("创建审计日志 - 成功（完整参数）")
    void createAuditLog_SuccessWithFullParams() {
        // 1. 构建请求
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("LOGIN");
        request.setExecutionResult("success");
        request.setTargetElement("登录按钮");
        request.setPageUrl("https://bank.example.com/login");
        request.setErrorMessage(null);

        // 2. mock
        when(auditLogMapper.insert(any(AuditLogEO.class))).thenReturn(1);

        // 3. 执行
        boolean result = auditLogService.createAuditLog(request);

        // 4. 验证
        assertTrue(result);
        verify(auditLogMapper, times(1)).insert(any(AuditLogEO.class));
    }

    @Test
    @DisplayName("创建审计日志 - executionResult 为空时默认 success")
    void createAuditLog_DefaultExecutionResult() {
        // 1. 构建请求（不设置 executionResult）
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("CLICK");

        // 2. mock
        when(auditLogMapper.insert(any(AuditLogEO.class))).thenAnswer(invocation -> {
            AuditLogEO eo = invocation.getArgument(0);
            // 验证 executionResult 被设为默认值
            assertEquals("success", eo.getExecutionResult());
            return 1;
        });

        // 3. 执行
        boolean result = auditLogService.createAuditLog(request);

        // 4. 验证
        assertTrue(result);
    }

    // endregion

    // region createAuditLog 参数校验

    @Test
    @DisplayName("创建审计日志 - 请求为空抛异常")
    void createAuditLog_NullRequest_ThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - taskId 为空抛异常")
    void createAuditLog_NullTaskId_ThrowsException() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("CLICK");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - orgId 为空抛异常")
    void createAuditLog_NullOrgId_ThrowsException() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setActionType("CLICK");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - actionType 为空抛异常")
    void createAuditLog_BlankActionType_ThrowsException() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - actionType 为 null 抛异常")
    void createAuditLog_NullActionType_ThrowsException() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // endregion

    // region createAuditLog 数据库异常

    @Test
    @DisplayName("创建审计日志 - insert 失败抛异常")
    void createAuditLog_InsertFailed_ThrowsException() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("NAVIGATE");
        request.setExecutionResult("failed");
        request.setErrorMessage("页面加载超时");

        // mock insert 返回 0（失败）
        when(auditLogMapper.insert(any(AuditLogEO.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    // endregion
}

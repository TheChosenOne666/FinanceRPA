package com.finrpa.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.audit.dto.request.AuditLogCreateRequest;
import com.finrpa.audit.dto.request.AuditLogQueryRequest;
import com.finrpa.audit.dto.response.AuditLogVO;
import com.finrpa.audit.entity.AuditLogEO;
import com.finrpa.audit.mapper.AuditLogMapper;
import com.finrpa.audit.service.SanitizeService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务实现单元测试（M7.1）
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

    @Mock
    private SanitizeService sanitizeService;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    // region createAuditLog 成功场景

    @Test
    @DisplayName("创建审计日志 - 成功（完整参数 + actionParams 脱敏）")
    void createAuditLog_SuccessWithSanitize() {
        // 1. 构建请求
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("INPUT_TEXT");
        request.setExecutionResult("success");
        request.setActionParams("{\"password\":\"secret\"}");

        // 2. mock 脱敏服务
        when(sanitizeService.sanitizeActionParams("{\"password\":\"secret\"}"))
                .thenReturn("{\"password\":\"***\"}");
        when(auditLogMapper.insert(any(AuditLogEO.class))).thenReturn(1);

        // 3. 执行
        boolean result = auditLogService.createAuditLog(request);

        // 4. 验证
        assertTrue(result);
        verify(sanitizeService, times(1)).sanitizeActionParams("{\"password\":\"secret\"}");
        verify(auditLogMapper, times(1)).insert(any(AuditLogEO.class));
    }

    @Test
    @DisplayName("创建审计日志 - actionParams 为空时不调脱敏")
    void createAuditLog_EmptyActionParams_NoSanitize() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("CLICK");

        when(auditLogMapper.insert(any(AuditLogEO.class))).thenReturn(1);

        boolean result = auditLogService.createAuditLog(request);

        assertTrue(result);
        verify(sanitizeService, never()).sanitizeActionParams(any());
    }

    @Test
    @DisplayName("创建审计日志 - executionResult 为空时默认 success")
    void createAuditLog_DefaultExecutionResult() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("CLICK");

        when(auditLogMapper.insert(any(AuditLogEO.class))).thenAnswer(invocation -> {
            AuditLogEO eo = invocation.getArgument(0);
            assertEquals("success", eo.getExecutionResult());
            return 1;
        });

        assertTrue(auditLogService.createAuditLog(request));
    }

    // endregion

    // region createAuditLog 参数校验

    @Test
    @DisplayName("创建审计日志 - 请求为空抛异常")
    void createAuditLog_NullRequest_Throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - taskId 为空抛异常")
    void createAuditLog_NullTaskId_Throws() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("CLICK");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - orgId 为空抛异常")
    void createAuditLog_NullOrgId_Throws() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setActionType("CLICK");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - actionType 为空抛异常")
    void createAuditLog_BlankActionType_Throws() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建审计日志 - insert 失败抛异常")
    void createAuditLog_InsertFailed_Throws() {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("NAVIGATE");
        request.setExecutionResult("failed");
        request.setErrorMessage("页面加载超时");

        when(auditLogMapper.insert(any(AuditLogEO.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.createAuditLog(request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    // endregion

    // region listAuditLogs 多维检索

    @Test
    @DisplayName("多维检索 - 按条件分页查询返回 VO")
    @SuppressWarnings("unchecked")
    void listAuditLogs_Success() {
        // 1. 构建查询请求
        AuditLogQueryRequest queryRequest = new AuditLogQueryRequest();
        queryRequest.setOrgId(TEST_ORG_ID);
        queryRequest.setTaskId(TEST_TASK_ID);
        queryRequest.setRiskLevel("high");
        queryRequest.setActionType("INPUT_TEXT");

        // 2. mock mapper 返回分页结果
        AuditLogEO eo = new AuditLogEO();
        eo.setAuditId(1L);
        eo.setTaskId(TEST_TASK_ID);
        eo.setOrgId(TEST_ORG_ID);
        eo.setActionType("INPUT_TEXT");
        eo.setRiskLevel("high");
        Page<AuditLogEO> page = new Page<>(1, 10);
        page.setRecords(List.of(eo));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 3. 执行
        IPage<AuditLogVO> result = auditLogService.listAuditLogs(queryRequest);

        // 4. 验证
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        AuditLogVO vo = result.getRecords().get(0);
        assertEquals(TEST_TASK_ID, vo.getTaskId());
        assertEquals("INPUT_TEXT", vo.getActionType());
        assertEquals("high", vo.getRiskLevel());
    }

    @Test
    @DisplayName("多维检索 - 空条件查询不报错")
    @SuppressWarnings("unchecked")
    void listAuditLogs_EmptyCondition() {
        AuditLogQueryRequest queryRequest = new AuditLogQueryRequest();

        Page<AuditLogEO> emptyPage = new Page<>(1, 10);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        IPage<AuditLogVO> result = auditLogService.listAuditLogs(queryRequest);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // endregion

    // region getAuditLogDetail 详情查询

    @Test
    @DisplayName("详情查询 - 存在时返回 VO")
    void getAuditLogDetail_Found() {
        AuditLogEO eo = new AuditLogEO();
        eo.setAuditId(1001L);
        eo.setTaskId(TEST_TASK_ID);
        eo.setOrgId(TEST_ORG_ID);
        eo.setActionType("LOGIN");
        when(auditLogMapper.selectById(1001L)).thenReturn(eo);

        AuditLogVO vo = auditLogService.getAuditLogDetail(1001L);

        assertNotNull(vo);
        assertEquals(1001L, vo.getAuditId());
        assertEquals("LOGIN", vo.getActionType());
    }

    @Test
    @DisplayName("详情查询 - 不存在抛 NOT_FOUND_ERROR")
    void getAuditLogDetail_NotFound_Throws() {
        when(auditLogMapper.selectById(9999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.getAuditLogDetail(9999L));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    // endregion
}

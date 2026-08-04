package com.finrpa.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.agent.service.TaskService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.llm.constant.LlmConstant;
import com.finrpa.llm.dto.request.NeedsHumanQueryRequest;
import com.finrpa.llm.dto.request.NeedsHumanReportRequest;
import com.finrpa.llm.dto.request.NeedsHumanResolveRequest;
import com.finrpa.llm.dto.response.NeedsHumanQueueVO;
import com.finrpa.llm.entity.NeedsHumanQueueEO;
import com.finrpa.llm.mapper.NeedsHumanQueueMapper;
import com.finrpa.tenant.entity.BusinessLineEO;
import com.finrpa.tenant.mapper.BusinessLineMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * NEEDS_HUMAN 队列服务实现单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class NeedsHumanServiceImplTest {

    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082342545947660289L;

    /** 测试用队列 ID */
    private static final Long TEST_QUEUE_ID = 2082350000000000001L;

    /** 测试用用户 ID */
    private static final Long TEST_USER_ID = 2082360000000000002L;

    /** 测试用业务线 ID */
    private static final Long TEST_BIZ_LINE_ID = 2082380000000000003L;

    @Mock
    private NeedsHumanQueueMapper needsHumanQueueMapper;

    @Mock
    private TaskService taskService;

    @Mock
    private BusinessLineMapper businessLineMapper;

    @InjectMocks
    private NeedsHumanServiceImpl needsHumanService;

    // region reportNeedsHuman 入队

    @Test
    @DisplayName("入队 - 成功（完整参数）")
    void reportNeedsHuman_Success() {
        NeedsHumanReportRequest request = buildReportRequest();

        when(needsHumanQueueMapper.insert(any(NeedsHumanQueueEO.class))).thenReturn(1);

        boolean result = needsHumanService.reportNeedsHuman(request);

        assertTrue(result);
        ArgumentCaptor<NeedsHumanQueueEO> captor = ArgumentCaptor.forClass(NeedsHumanQueueEO.class);
        verify(needsHumanQueueMapper).insert(captor.capture());

        NeedsHumanQueueEO eo = captor.getValue();
        assertEquals(TEST_TASK_ID, eo.getTaskId());
        assertEquals(TEST_ORG_ID, eo.getOrgId());
        assertEquals(TEST_BIZ_LINE_ID, eo.getBusinessLineId());
        assertEquals("planner", eo.getContextName());
        assertEquals("raw-llm-output", eo.getLlmRawOutput());
        assertEquals("validation failed", eo.getValidationError());
        assertEquals(3, eo.getAttempts());
        assertEquals(LlmConstant.NEEDS_HUMAN_STATUS_PENDING, eo.getStatus());
    }

    @Test
    @DisplayName("入队 - contextName 为空时使用默认值")
    void reportNeedsHuman_DefaultContextName() {
        NeedsHumanReportRequest request = new NeedsHumanReportRequest();
        request.setTaskId("123");
        request.setContextName("");

        when(needsHumanQueueMapper.insert(any(NeedsHumanQueueEO.class))).thenAnswer(invocation -> {
            NeedsHumanQueueEO eo = invocation.getArgument(0);
            assertEquals(LlmConstant.DEFAULT_CONTEXT, eo.getContextName());
            return 1;
        });

        assertTrue(needsHumanService.reportNeedsHuman(request));
    }

    @Test
    @DisplayName("入队 - 请求为空抛异常")
    void reportNeedsHuman_NullRequest_Throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.reportNeedsHuman(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("入队 - taskId 为空抛异常")
    void reportNeedsHuman_BlankTaskId_Throws() {
        NeedsHumanReportRequest request = new NeedsHumanReportRequest();
        request.setTaskId("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.reportNeedsHuman(request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("入队 - insert 失败抛异常")
    void reportNeedsHuman_InsertFailed_Throws() {
        NeedsHumanReportRequest request = buildReportRequest();
        when(needsHumanQueueMapper.insert(any(NeedsHumanQueueEO.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.reportNeedsHuman(request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    // endregion

    // region listNeedsHuman 列表查询

    @Test
    @DisplayName("列表查询 - 返回分页结果")
    @SuppressWarnings("unchecked")
    void listNeedsHuman_ReturnsPage() {
        NeedsHumanQueryRequest queryRequest = new NeedsHumanQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        queryRequest.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);

        NeedsHumanQueueEO eo1 = buildQueueEO(1L);
        eo1.setBusinessLineId(TEST_BIZ_LINE_ID);
        List<NeedsHumanQueueEO> records = List.of(eo1, buildQueueEO(2L));
        Page<NeedsHumanQueueEO> page = new Page<>(1, 10, 2);
        page.setRecords(records);

        when(needsHumanQueueMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        // mock 业务线名称查询
        BusinessLineEO bizLine = new BusinessLineEO();
        bizLine.setBusinessLineId(TEST_BIZ_LINE_ID);
        bizLine.setBusinessLineName("对公业务");
        when(businessLineMapper.selectList(any())).thenReturn(List.of(bizLine));

        IPage<NeedsHumanQueueVO> result = needsHumanService.listNeedsHuman(queryRequest, TEST_ORG_ID);

        assertEquals(2, result.getRecords().size());
        assertEquals(2L, result.getTotal());
        // 验证业务线名称填充
        NeedsHumanQueueVO vo0 = result.getRecords().get(0);
        assertEquals(TEST_BIZ_LINE_ID, vo0.getBusinessLineId());
        assertEquals("对公业务", vo0.getBusinessLineName());
        verify(needsHumanQueueMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("列表查询 - 按业务线筛选")
    @SuppressWarnings("unchecked")
    void listNeedsHuman_FilterByBusinessLine() {
        NeedsHumanQueryRequest queryRequest = new NeedsHumanQueryRequest();
        queryRequest.setBusinessLineId(TEST_BIZ_LINE_ID);

        Page<NeedsHumanQueueEO> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(Collections.emptyList());
        when(needsHumanQueueMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        IPage<NeedsHumanQueueVO> result = needsHumanService.listNeedsHuman(queryRequest, TEST_ORG_ID);

        assertEquals(0, result.getRecords().size());
        verify(needsHumanQueueMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("列表查询 - 空结果")
    @SuppressWarnings("unchecked")
    void listNeedsHuman_EmptyResult() {
        NeedsHumanQueryRequest queryRequest = new NeedsHumanQueryRequest();
        Page<NeedsHumanQueueEO> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(Collections.emptyList());

        when(needsHumanQueueMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        IPage<NeedsHumanQueueVO> result = needsHumanService.listNeedsHuman(queryRequest, TEST_ORG_ID);

        assertTrue(result.getRecords().isEmpty());
    }

    // endregion

    // region getNeedsHumanDetail 详情查询

    @Test
    @DisplayName("详情查询 - 成功")
    void getNeedsHumanDetail_Success() {
        NeedsHumanQueueEO eo = buildQueueEO(TEST_QUEUE_ID);
        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(eo);

        NeedsHumanQueueVO vo = needsHumanService.getNeedsHumanDetail(TEST_QUEUE_ID, TEST_ORG_ID);

        assertNotNull(vo);
        assertEquals(TEST_QUEUE_ID, vo.getQueueId());
        assertEquals(TEST_TASK_ID, vo.getTaskId());
        assertEquals("planner", vo.getContextName());
    }

    @Test
    @DisplayName("详情查询 - 不存在抛异常")
    void getNeedsHumanDetail_NotFound_Throws() {
        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.getNeedsHumanDetail(TEST_QUEUE_ID, TEST_ORG_ID));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("详情查询 - queueId 为空抛异常")
    void getNeedsHumanDetail_NullQueueId_Throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.getNeedsHumanDetail(null, TEST_ORG_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // endregion

    // region resolveNeedsHuman 处置

    @Test
    @DisplayName("处置 - skip 动作触发 resumeTask")
    void resolve_Skip_TriggersResume() {
        NeedsHumanQueueEO eo = buildQueueEO(TEST_QUEUE_ID);
        eo.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);

        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(eo);
        when(needsHumanQueueMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(LlmConstant.RESOLVE_ACTION_SKIP);

        boolean result = needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID);

        assertTrue(result);
        verify(taskService).resumeTask(TEST_TASK_ID);
        verify(taskService, never()).abortTask(anyLong());
    }

    @Test
    @DisplayName("处置 - manual 动作触发 resumeTask")
    void resolve_Manual_TriggersResume() {
        NeedsHumanQueueEO eo = buildQueueEO(TEST_QUEUE_ID);
        eo.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);

        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(eo);
        when(needsHumanQueueMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(LlmConstant.RESOLVE_ACTION_MANUAL);

        boolean result = needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID);

        assertTrue(result);
        verify(taskService).resumeTask(TEST_TASK_ID);
        verify(taskService, never()).abortTask(anyLong());
    }

    @Test
    @DisplayName("处置 - abort 动作触发 abortTask")
    void resolve_Abort_TriggersAbort() {
        NeedsHumanQueueEO eo = buildQueueEO(TEST_QUEUE_ID);
        eo.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);

        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(eo);
        when(needsHumanQueueMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(LlmConstant.RESOLVE_ACTION_ABORT);

        boolean result = needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID);

        assertTrue(result);
        verify(taskService).abortTask(TEST_TASK_ID);
        verify(taskService, never()).resumeTask(anyLong());
    }

    @Test
    @DisplayName("处置 - 无效动作抛异常")
    void resolve_InvalidAction_Throws() {
        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction("invalid");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("处置 - 已处置的事件抛异常")
    void resolve_AlreadyResolved_Throws() {
        NeedsHumanQueueEO eo = buildQueueEO(TEST_QUEUE_ID);
        eo.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_RESOLVED);

        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(eo);

        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(LlmConstant.RESOLVE_ACTION_SKIP);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("处置 - 事件不存在抛异常")
    void resolve_NotFound_Throws() {
        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(LlmConstant.RESOLVE_ACTION_SKIP);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("处置 - queueId 为空抛异常")
    void resolve_NullQueueId_Throws() {
        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(LlmConstant.RESOLVE_ACTION_SKIP);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.resolveNeedsHuman(null, resolveRequest, TEST_USER_ID, TEST_ORG_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("处置 - action 为空抛异常")
    void resolve_NullAction_Throws() {
        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("处置 - resumeTask 失败时异常传播，队列不改状态")
    void resolve_ResumeFails_ExceptionPropagates() {
        NeedsHumanQueueEO eo = buildQueueEO(TEST_QUEUE_ID);
        eo.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);

        when(needsHumanQueueMapper.selectOne(any(Wrapper.class))).thenReturn(eo);
        doThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "续跑失败"))
                .when(taskService).resumeTask(TEST_TASK_ID);

        NeedsHumanResolveRequest resolveRequest = new NeedsHumanResolveRequest();
        resolveRequest.setAction(LlmConstant.RESOLVE_ACTION_SKIP);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> needsHumanService.resolveNeedsHuman(TEST_QUEUE_ID, resolveRequest, TEST_USER_ID, TEST_ORG_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());

        // 验证队列状态未更新
        verify(needsHumanQueueMapper, never()).update(any(), any(Wrapper.class));
    }

    // endregion

    // region 辅助方法

    /**
     * 构建测试用上报请求
     */
    private NeedsHumanReportRequest buildReportRequest() {
        NeedsHumanReportRequest request = new NeedsHumanReportRequest();
        request.setTaskId(String.valueOf(TEST_TASK_ID));
        request.setOrgId(String.valueOf(TEST_ORG_ID));
        request.setBusinessLineId(String.valueOf(TEST_BIZ_LINE_ID));
        request.setSubtaskId("subtask-001");
        request.setContextName("planner");
        request.setScreenshotUrl("https://example.com/screenshot.png");
        request.setLlmRawOutput("raw-llm-output");
        request.setValidationError("validation failed");
        request.setAttempts(3);
        return request;
    }

    /**
     * 构建测试用队列实体
     */
    private NeedsHumanQueueEO buildQueueEO(Long queueId) {
        NeedsHumanQueueEO eo = new NeedsHumanQueueEO();
        eo.setQueueId(queueId);
        eo.setTaskId(TEST_TASK_ID);
        eo.setOrgId(TEST_ORG_ID);
        eo.setSubtaskId("subtask-001");
        eo.setContextName("planner");
        eo.setLlmRawOutput("raw-llm-output");
        eo.setValidationError("validation failed");
        eo.setAttempts(3);
        eo.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);
        return eo;
    }

    // endregion
}

package com.finrpa.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.llm.constant.LlmConstant;
import com.finrpa.llm.dto.request.NeedsHumanResolveRequest;
import com.finrpa.llm.dto.response.NeedsHumanQueueVO;
import com.finrpa.llm.service.NeedsHumanService;
import com.finrpa.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NEEDS_HUMAN 队列控制器单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class NeedsHumanControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082342545947660289L;

    /** 测试用队列 ID */
    private static final Long TEST_QUEUE_ID = 2082350000000000001L;

    private MockMvc mockMvc;
    private NeedsHumanService needsHumanService;

    @BeforeEach
    void setUp() {
        needsHumanService = mock(NeedsHumanService.class);

        NeedsHumanController controller = new NeedsHumanController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "needsHumanService", needsHumanService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        TenantContext.setOrgId(String.valueOf(TEST_ORG_ID));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("列表查询 - 成功")
    void listNeedsHuman_Success() throws Exception {
        List<NeedsHumanQueueVO> records = Arrays.asList(buildVO(1L), buildVO(2L));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<NeedsHumanQueueVO> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 2);
        page.setRecords(records);

        when(needsHumanService.listNeedsHuman(any(), eq(TEST_ORG_ID))).thenReturn(page);

        mockMvc.perform(get("/llm/needs-human")
                        .param("status", LlmConstant.NEEDS_HUMAN_STATUS_PENDING)
                        .param("current", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].queueId").value(1))
                .andExpect(jsonPath("$.data[1].queueId").value(2));

        verify(needsHumanService).listNeedsHuman(any(), eq(TEST_ORG_ID));
    }

    @Test
    @DisplayName("详情查询 - 成功")
    void getNeedsHumanDetail_Success() throws Exception {
        NeedsHumanQueueVO vo = buildVO(TEST_QUEUE_ID);
        when(needsHumanService.getNeedsHumanDetail(eq(TEST_QUEUE_ID), eq(TEST_ORG_ID))).thenReturn(vo);

        mockMvc.perform(get("/llm/needs-human/{queueId}", TEST_QUEUE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.queueId").value(TEST_QUEUE_ID))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andExpect(jsonPath("$.data.contextName").value("planner"))
                .andExpect(jsonPath("$.data.status").value(LlmConstant.NEEDS_HUMAN_STATUS_PENDING));

        verify(needsHumanService).getNeedsHumanDetail(eq(TEST_QUEUE_ID), eq(TEST_ORG_ID));
    }

    @Test
    @DisplayName("处置 - skip 动作")
    void resolve_Skip() throws Exception {
        NeedsHumanResolveRequest request = new NeedsHumanResolveRequest();
        request.setAction(LlmConstant.RESOLVE_ACTION_SKIP);

        when(needsHumanService.resolveNeedsHuman(eq(TEST_QUEUE_ID), any(NeedsHumanResolveRequest.class),
                any(), eq(TEST_ORG_ID))).thenReturn(true);

        mockMvc.perform(post("/llm/needs-human/{queueId}/resolve", TEST_QUEUE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(needsHumanService).resolveNeedsHuman(eq(TEST_QUEUE_ID), any(NeedsHumanResolveRequest.class),
                any(), eq(TEST_ORG_ID));
    }

    @Test
    @DisplayName("处置 - abort 动作")
    void resolve_Abort() throws Exception {
        NeedsHumanResolveRequest request = new NeedsHumanResolveRequest();
        request.setAction(LlmConstant.RESOLVE_ACTION_ABORT);

        when(needsHumanService.resolveNeedsHuman(eq(TEST_QUEUE_ID), any(NeedsHumanResolveRequest.class),
                any(), eq(TEST_ORG_ID))).thenReturn(true);

        mockMvc.perform(post("/llm/needs-human/{queueId}/resolve", TEST_QUEUE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(needsHumanService).resolveNeedsHuman(eq(TEST_QUEUE_ID), any(NeedsHumanResolveRequest.class),
                any(), eq(TEST_ORG_ID));
    }

    // region 辅助方法

    /**
     * 构建测试用 VO
     */
    private NeedsHumanQueueVO buildVO(Long queueId) {
        NeedsHumanQueueVO vo = new NeedsHumanQueueVO();
        vo.setQueueId(queueId);
        vo.setTaskId(2082333099000000099L);
        vo.setOrgId(TEST_ORG_ID);
        vo.setSubtaskId("subtask-001");
        vo.setContextName("planner");
        vo.setLlmRawOutput("raw-llm-output");
        vo.setValidationError("validation failed");
        vo.setAttempts(3);
        vo.setStatus(LlmConstant.NEEDS_HUMAN_STATUS_PENDING);
        vo.setCreateTime(new Timestamp(System.currentTimeMillis()));
        return vo;
    }

    // endregion
}

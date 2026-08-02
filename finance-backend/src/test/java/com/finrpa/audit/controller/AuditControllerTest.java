package com.finrpa.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.audit.dto.request.AuditLogQueryRequest;
import com.finrpa.audit.dto.response.AuditLogVO;
import com.finrpa.audit.service.AuditLogService;
import com.finrpa.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计日志对外控制器单元测试（M7.1）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class AuditControllerTest {

    /** 测试用组织 ID */
    private static final String TEST_ORG_ID = "2082342545947660289";

    private MockMvc mockMvc;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        AuditController controller = new AuditController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        // 清理 ThreadLocal，防止线程复用污染
        TenantContext.clear();
    }

    @Test
    @DisplayName("审计日志列表 - 从登录上下文填充 orgId 并返回分页")
    void listAuditLogs_Success() throws Exception {
        // 1. 模拟登录态
        TenantContext.setOrgId(TEST_ORG_ID);

        // 2. mock service 返回分页
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1L);
        vo.setActionType("LOGIN");
        Page<AuditLogVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);
        when(auditLogService.listAuditLogs(any(AuditLogQueryRequest.class))).thenReturn(page);

        // 3. 执行并验证
        mockMvc.perform(get("/v1/audit/logs")
                        .param("taskId", "2082333099000000099")
                        .param("riskLevel", "high"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].actionType").value("LOGIN"));

        // 4. 验证 orgId 被填充到查询请求
        verify(auditLogService, times(1)).listAuditLogs(argThat(req ->
                TEST_ORG_ID.equals(String.valueOf(req.getOrgId()))));
    }

    @Test
    @DisplayName("审计日志列表 - 无登录态时 orgId 不填充但不报错")
    void listAuditLogs_NoTenantContext() throws Exception {
        Page<AuditLogVO> emptyPage = new Page<>(1, 10);
        when(auditLogService.listAuditLogs(any(AuditLogQueryRequest.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/v1/audit/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("审计日志详情 - 返回 VO")
    void getAuditLogDetail_Success() throws Exception {
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1001L);
        vo.setActionType("CLICK");
        vo.setExecutionResult("success");
        when(auditLogService.getAuditLogDetail(1001L)).thenReturn(vo);

        mockMvc.perform(get("/v1/audit/logs/{auditId}", 1001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.auditId").value(1001))
                .andExpect(jsonPath("$.data.actionType").value("CLICK"));
    }
}

package com.finrpa.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.audit.dto.request.AuditLogCreateRequest;
import com.finrpa.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计日志内部回调控制器单元测试（M7.1）
 *
 * <p>验证 Python 上报审计日志端点的请求转发与响应格式。
 * 鉴权拦截器在集成测试中验证，此处 standaloneSetup 不注册拦截器。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class InternalAuditControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082342545947660289L;

    private MockMvc mockMvc;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        InternalAuditController controller = new InternalAuditController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("上报审计日志 - 成功")
    void createAuditLog_Success() throws Exception {
        // 1. 构建请求
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("LOGIN");
        request.setExecutionResult("success");
        request.setPageUrl("https://bank.example.com/login");
        request.setActionParams("{\"username\":\"admin\",\"password\":\"secret\"}");

        // 2. mock
        when(auditLogService.createAuditLog(any(AuditLogCreateRequest.class))).thenReturn(true);

        // 3. 执行请求并验证
        mockMvc.perform(post("/internal/audit/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        // 4. 验证 service 被调用
        verify(auditLogService, times(1)).createAuditLog(any(AuditLogCreateRequest.class));
    }

    @Test
    @DisplayName("上报审计日志 - 最小必填参数")
    void createAuditLog_MinimalParams() throws Exception {
        AuditLogCreateRequest request = new AuditLogCreateRequest();
        request.setTaskId(TEST_TASK_ID);
        request.setOrgId(TEST_ORG_ID);
        request.setActionType("NAVIGATE");

        when(auditLogService.createAuditLog(any(AuditLogCreateRequest.class))).thenReturn(true);

        mockMvc.perform(post("/internal/audit/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}

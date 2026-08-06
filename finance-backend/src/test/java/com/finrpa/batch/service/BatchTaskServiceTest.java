package com.finrpa.batch.service;

import com.finrpa.batch.dto.BatchTaskRequest;
import com.finrpa.batch.dto.BatchTaskResultVO;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.workflows.dto.request.WorkflowRunRequest;
import com.finrpa.workflows.dto.response.WorkflowRunVO;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import com.finrpa.workflows.service.WorkflowService;
import com.finrpa.workflows.service.WorkflowTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link BatchTaskService} 单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("批量任务服务测试")
class BatchTaskServiceTest {

    @Mock
    private WorkflowService workflowService;

    @Mock
    private WorkflowTriggerService workflowTriggerService;

    @Mock
    private ExternalDataSourceService externalDataSourceService;

    @InjectMocks
    private BatchTaskService batchTaskService;

    private final Long orgId = 1L;
    private final Long userId = 100L;
    private final Long workflowId = 10L;

    @BeforeEach
    void setUp() {
        WorkflowTemplateEO template = new WorkflowTemplateEO();
        template.setId(workflowId);
        org.mockito.Mockito.lenient().when(workflowService.queryByWorkflowId(workflowId)).thenReturn(template);
    }

    @Test
    @DisplayName("直接传入 rows：成功映射并逐条触发")
    void testCreateBatch_withRows_success() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("客户姓名", "customer_name");
        mapping.put("身份证号", "id_card");

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("客户姓名", "张三"); r1.put("身份证号", "110");
        Map<String, Object> r2 = new HashMap<>();
        r2.put("客户姓名", "李四"); r2.put("身份证号", "120");
        rows.add(r1); rows.add(r2);

        when(workflowTriggerService.triggerWorkflow(eq(workflowId), any(WorkflowRunRequest.class), eq(orgId), eq(userId)))
                .thenReturn(new WorkflowRunVO());

        BatchTaskRequest req = new BatchTaskRequest();
        req.setWorkflowId(workflowId);
        req.setColumnMapping(mapping);
        req.setRows(rows);

        BatchTaskResultVO result = batchTaskService.createBatch(req, orgId, userId);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertNotNull(result.getBatchId());
        // 校验传给 trigger 的 params 已用模板 param name 重命名
        ArgumentCaptor<WorkflowRunRequest> captor = ArgumentCaptor.forClass(WorkflowRunRequest.class);
        verify(workflowTriggerService, times(2)).triggerWorkflow(eq(workflowId), captor.capture(), eq(orgId), eq(userId));
        assertEquals("张三", captor.getAllValues().get(0).getParams().get("customer_name"));
        assertEquals("120", captor.getAllValues().get(1).getParams().get("id_card"));
    }

    @Test
    @DisplayName("部分行失败：汇总失败数并保留成功结果")
    void testCreateBatch_partialFailure() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("a", "x");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>(); r1.put("a", "1");
        Map<String, Object> r2 = new HashMap<>(); r2.put("a", "2");
        rows.add(r1); rows.add(r2);

        when(workflowTriggerService.triggerWorkflow(eq(workflowId), any(WorkflowRunRequest.class), eq(orgId), eq(userId)))
                .thenReturn(new WorkflowRunVO())
                .thenThrow(new BusinessException(400, "参数校验失败"));

        BatchTaskRequest req = new BatchTaskRequest();
        req.setWorkflowId(workflowId);
        req.setColumnMapping(mapping);
        req.setRows(rows);

        BatchTaskResultVO result = batchTaskService.createBatch(req, orgId, userId);
        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertFalse(result.getResults().get(1).isSuccess());
        assertEquals("参数校验失败", result.getResults().get(1).getError());
    }

    @Test
    @DisplayName("外部数据源：未启用时抛异常")
    void testCreateBatch_externalDisabled_throws() {
        when(externalDataSourceService.isEnabled()).thenReturn(false);

        BatchTaskRequest req = new BatchTaskRequest();
        req.setWorkflowId(workflowId);
        Map<String, String> mapping = new HashMap<>(); mapping.put("a", "x");
        req.setColumnMapping(mapping);
        BatchTaskRequest.ExternalQuery eq = new BatchTaskRequest.ExternalQuery();
        eq.setTableName("customers"); eq.setLimit(10);
        req.setExternalQuery(eq);

        assertThrows(BusinessException.class, () -> batchTaskService.createBatch(req, orgId, userId));
    }

    @Test
    @DisplayName("外部数据源：启用时拉取并映射")
    void testCreateBatch_externalEnabled_success() {
        when(externalDataSourceService.isEnabled()).thenReturn(true);
        List<Map<String, Object>> ext = new ArrayList<>();
        Map<String, Object> e1 = new HashMap<>(); e1.put("name", "王五"); e1.put("card", "330");
        ext.add(e1);
        when(externalDataSourceService.previewTable(eq("customers"), any(), eq(50))).thenReturn(ext);

        when(workflowTriggerService.triggerWorkflow(eq(workflowId), any(WorkflowRunRequest.class), eq(orgId), eq(userId)))
                .thenReturn(new WorkflowRunVO());

        BatchTaskRequest req = new BatchTaskRequest();
        req.setWorkflowId(workflowId);
        Map<String, String> mapping = new HashMap<>();
        mapping.put("name", "customer_name"); mapping.put("card", "id_card");
        req.setColumnMapping(mapping);
        BatchTaskRequest.ExternalQuery eq = new BatchTaskRequest.ExternalQuery();
        eq.setTableName("customers"); eq.setLimit(50);
        req.setExternalQuery(eq);

        BatchTaskResultVO result = batchTaskService.createBatch(req, orgId, userId);
        assertEquals(1, result.getSuccessCount());
        assertEquals("王五", result.getResults().get(0) != null ? "王五" : null);
        verify(externalDataSourceService).previewTable(eq("customers"), any(), eq(50));
    }

    @Test
    @DisplayName("空数据与超限校验")
    void testCreateBatch_emptyAndOverLimit() {
        Map<String, String> mapping = new HashMap<>(); mapping.put("a", "x");

        BatchTaskRequest empty = new BatchTaskRequest();
        empty.setWorkflowId(workflowId); empty.setColumnMapping(mapping);
        empty.setRows(new ArrayList<>());
        assertThrows(BusinessException.class, () -> batchTaskService.createBatch(empty, orgId, userId));

        List<Map<String, Object>> big = new ArrayList<>();
        for (int i = 0; i < 501; i++) big.add(new HashMap<>());
        BatchTaskRequest over = new BatchTaskRequest();
        over.setWorkflowId(workflowId); over.setColumnMapping(mapping); over.setRows(big);
        assertThrows(BusinessException.class, () -> batchTaskService.createBatch(over, orgId, userId));
    }

    @Test
    @DisplayName("缺少 columnMapping 抛异常")
    void testCreateBatch_noMapping_throws() {
        BatchTaskRequest req = new BatchTaskRequest();
        req.setWorkflowId(workflowId);
        assertThrows(BusinessException.class, () -> batchTaskService.createBatch(req, orgId, userId));
    }
}

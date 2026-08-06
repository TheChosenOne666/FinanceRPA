package com.finrpa.batch.service;

import com.finrpa.batch.dto.BatchTaskRequest;
import com.finrpa.batch.dto.BatchTaskResultVO;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.workflows.dto.request.WorkflowRunRequest;
import com.finrpa.workflows.dto.response.WorkflowRunVO;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import com.finrpa.workflows.service.WorkflowService;
import com.finrpa.workflows.service.WorkflowTriggerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 批量任务服务（数据驱动）
 *
 * <p>将一批用户数据（CSV/粘贴多行，或外部业务系统表）按 {@code columnMapping} 映射为
 * 同一工作流模板的参数，逐条调用 {@code WorkflowTriggerService.triggerWorkflow} 生成任务，
 * 消灭"每次手动填参数"的重复操作。</p>
 *
 * <p>为保持可观测与实现简洁，采用<b>同步</b>执行并将每条结果汇总返回；批量上限由
 * {@link #MAX_ROWS} 控制。后续可演进为异步队列（@Async + 批次状态表）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class BatchTaskService {

    /** 单次批量最大行数（防止一次性提交过多拖垮执行器） */
    private static final int MAX_ROWS = 500;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowTriggerService workflowTriggerService;

    @Resource
    private ExternalDataSourceService externalDataSourceService;

    /**
     * 创建批量任务
     *
     * @param request 批量请求（含 workflowId、columnMapping、rows 或 externalQuery）
     * @param orgId   组织 ID（租户隔离）
     * @param userId  操作人 ID
     * @return 批量结果（含每条明细）
     */
    public BatchTaskResultVO createBatch(BatchTaskRequest request, Long orgId, Long userId) {
        ThrowUtils.throwIf(request == null || request.getWorkflowId() == null,
                ErrorCode.PARAMS_ERROR, "工作流模板 ID 不能为空");
        ThrowUtils.throwIf(request.getColumnMapping() == null || request.getColumnMapping().isEmpty(),
                ErrorCode.PARAMS_ERROR, "参数映射 columnMapping 不能为空");

        // 1. 取模板参数定义（用于透传原始值，triggerWorkflow 内部已做必填校验）
        WorkflowTemplateEO template = workflowService.queryByWorkflowId(request.getWorkflowId());
        ThrowUtils.throwIf(template == null, ErrorCode.WORKFLOW_NOT_FOUND,
                "工作流模板不存在: " + request.getWorkflowId());

        // 2. 解析出待处理的行数据（已用模板 param name 作为 key）
        List<Map<String, Object>> mappedRows = resolveRows(request);
        ThrowUtils.throwIf(mappedRows.isEmpty(), ErrorCode.PARAMS_ERROR, "未解析到任何数据行");
        ThrowUtils.throwIf(mappedRows.size() > MAX_ROWS,
                ErrorCode.PARAMS_ERROR, "批量行数超过上限: " + MAX_ROWS);

        // 3. 逐行触发
        BatchTaskResultVO result = new BatchTaskResultVO();
        result.setBatchId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        result.setTotal(mappedRows.size());
        List<BatchTaskResultVO.ItemResult> items = new ArrayList<>();

        int success = 0;
        int failed = 0;
        for (int i = 0; i < mappedRows.size(); i++) {
            BatchTaskResultVO.ItemResult item = new BatchTaskResultVO.ItemResult();
            item.setRowIndex(i + 1);
            try {
                WorkflowRunRequest runReq = new WorkflowRunRequest();
                runReq.setParams(mappedRows.get(i));
                WorkflowRunVO vo = workflowTriggerService.triggerWorkflow(
                        request.getWorkflowId(), runReq, orgId, userId);
                item.setSuccess(true);
                item.setTaskId(vo.getTaskId());
                item.setState(vo.getState());
                success++;
            } catch (Exception e) {
                item.setSuccess(false);
                item.setError(e.getMessage());
                failed++;
                log.warn("批量任务第 {} 行创建失败: {}", i + 1, e.getMessage());
            }
            items.add(item);
        }
        result.setResults(items);
        result.setSuccessCount(success);
        result.setFailedCount(failed);
        log.info("批量任务完成: batchId={}, total={}, success={}, failed={}",
                result.getBatchId(), result.getTotal(), success, failed);
        return result;
    }

    /**
     * 将数据源（直接 rows 或外部表）解析为已按模板 param name 映射的行
     */
    private List<Map<String, Object>> resolveRows(BatchTaskRequest request) {
        List<Map<String, Object>> source = request.getRows();
        if (source == null || source.isEmpty()) {
            BatchTaskRequest.ExternalQuery eq = request.getExternalQuery();
            ThrowUtils.throwIf(eq == null || eq.getTableName() == null || eq.getTableName().isBlank(),
                    ErrorCode.PARAMS_ERROR, "rows 与 externalQuery 不能同时为空");
            ThrowUtils.throwIf(!externalDataSourceService.isEnabled(),
                    ErrorCode.PARAMS_ERROR, "外部数据源未启用，无法从业务系统拉取数据");
            source = externalDataSourceService.previewTable(
                    eq.getTableName(), eq.getWhereClause(), eq.getLimit() == null ? 100 : eq.getLimit());
        }

        // 将源行按 columnMapping 重命名为模板 param name
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Map<String, Object> row : source) {
            Map<String, Object> target = new HashMap<>();
            for (Map.Entry<String, String> mapping : request.getColumnMapping().entrySet()) {
                Object val = row.get(mapping.getKey());
                target.put(mapping.getValue(), val);
            }
            mapped.add(target);
        }
        return mapped;
    }
}

package com.finrpa.workflows.service.impl;

import com.finrpa.agent.dto.request.TaskCreateRequest;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.service.TaskService;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.TaskTriggerRequest;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.request.RiskDetectRequest;
import com.finrpa.approval.dto.response.RiskDetectResultVO;
import com.finrpa.approval.dto.response.RiskJudgeResponse;
import com.finrpa.approval.entity.ApprovalRequestEO;
import com.finrpa.approval.service.ApprovalRouteService;
import com.finrpa.approval.service.ApprovalService;
import com.finrpa.approval.service.RiskDetectService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.workflows.dto.request.WorkflowRunRequest;
import com.finrpa.workflows.dto.response.WorkflowRunVO;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import com.finrpa.workflows.service.WorkflowService;
import com.finrpa.workflows.service.WorkflowTriggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流触发执行服务实现
 *
 * <p>触发流程：
 * <ol>
 *   <li>加载模板并校验启用状态</li>
 *   <li>校验必填参数</li>
 *   <li>参数映射：将 steps 中的 {{param}} 替换为用户提供的实际值</li>
 *   <li>创建 Java 任务（持久化）</li>
 *   <li>调用 Python AI 服务触发执行</li>
 * </ol>
 *
 * <p>M6.1 已接入关键词预筛 + LLM 二次判断；
 * M6.3 实现 high/critical 风险阻塞：创建审批单后返回 PENDING_APPROVAL，
 * 审批通过后由 ApprovalService 触发 Python 执行。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class WorkflowTriggerServiceImpl implements WorkflowTriggerService {

    /** 参数引用模板语法正则：{{param_name}} */
    private static final Pattern PARAM_REF_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    @Resource
    private WorkflowService workflowService;

    @Resource
    private TaskService taskService;

    @Resource
    private AiServiceClient aiServiceClient;

    @Resource
    private RiskDetectService riskDetectService;

    /** 审批服务（M6.3 high/critical 风险审批） */
    @Resource
    private ApprovalService approvalService;

    /** 审批路由服务（M6.3 按风险等级路由） */
    @Resource
    private ApprovalRouteService approvalRouteService;

    /** JSON 序列化工具（M6.3 存储触发请求到审批单） */
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public WorkflowRunVO triggerWorkflow(Long workflowId, WorkflowRunRequest request,
                                          Long orgId, Long userId) {
        log.info("触发工作流: workflowId={}, orgId={}, userId={}", workflowId, orgId, userId);

        // 1. 加载模板
        WorkflowTemplateEO template = workflowService.queryByWorkflowId(workflowId);
        ThrowUtils.throwIf(template == null, ErrorCode.WORKFLOW_NOT_FOUND,
                "工作流模板不存在: " + workflowId);
        ThrowUtils.throwIf(template.getEnabled() == 0, ErrorCode.WORKFLOW_DISABLED,
                "工作流模板已禁用: " + template.getName());

        // 2. 校验必填参数
        Map<String, Object> userParams = request.getParams() != null
                ? request.getParams() : new HashMap<>();
        validateRequiredParams(template.getParams(), userParams, template.getName());

        // 3. 参数映射：将 steps 中的 {{param}} 替换为实际值
        String resolvedSteps = resolveParams(template.getSteps(), userParams);
        log.info("参数映射完成: workflow={}, steps={}", template.getName(), resolvedSteps);

        // 4. 构建 TaskCreateRequest
        TaskCreateRequest createRequest = new TaskCreateRequest();
        createRequest.setGoal(template.getName() + ": " + template.getDescription());
        Map<String, Object> taskParams = new HashMap<>();
        taskParams.put("steps", resolvedSteps);
        taskParams.put("industry", template.getIndustry());
        taskParams.put("riskLevel", template.getRiskLevel());
        createRequest.setParams(taskParams);
        createRequest.setWorkflowId(template.getWorkflowId());

        // 5. 创建 Java 任务（持久化）
        AgentTaskEO task = taskService.createTask(orgId, userId, createRequest);
        log.info("任务已创建: taskId={}, workflow={}", task.getTaskId(), template.getName());

        // 6. 构建 Python 触发请求
        TaskTriggerRequest triggerRequest = new TaskTriggerRequest();
        triggerRequest.setTaskId(String.valueOf(task.getTaskId()));
        triggerRequest.setOrgId(String.valueOf(orgId));
        triggerRequest.setUserId(String.valueOf(userId));
        triggerRequest.setGoal(createRequest.getGoal());
        triggerRequest.setParams(taskParams);
        triggerRequest.setWorkflowId(String.valueOf(workflowId));

        // 7. 风险检测（M6.1 关键词预筛 + LLM 二次判断）
        String finalRiskLevel = performRiskDetection(template, createRequest, task.getTaskId());

        // 8. 审批路由判定（M6.3）
        boolean needsApproval = approvalRouteService.needsHumanApproval(finalRiskLevel);

        if (needsApproval) {
            // high/critical 风险：创建审批单，返回 PENDING_APPROVAL（非阻塞）
            log.info("任务需审批: taskId={}, riskLevel={}", task.getTaskId(), finalRiskLevel);
            String requestPayload = serializeTriggerRequest(triggerRequest);
            String riskReasoning = buildRiskReasoning(createRequest, finalRiskLevel);

            ApprovalRequestEO approval = approvalService.createApproval(
                    task.getTaskId(), orgId, workflowId, userId,
                    finalRiskLevel, riskReasoning, requestPayload);

            WorkflowRunVO runVO = new WorkflowRunVO();
            runVO.setTaskId(task.getTaskId());
            runVO.setWorkflowId(workflowId);
            runVO.setState("PENDING_APPROVAL");
            runVO.setApprovalId(approval.getApprovalId());
            return runVO;
        }

        // 9. low/medium 风险：直接调用 Python AI 服务触发执行
        triggerPythonTask(triggerRequest, finalRiskLevel);

        // 10. 返回执行结果
        WorkflowRunVO runVO = new WorkflowRunVO();
        runVO.setTaskId(task.getTaskId());
        runVO.setWorkflowId(workflowId);
        runVO.setState("EXECUTING");
        return runVO;
    }

    // region 内部方法

    /**
     * 序列化触发请求为 JSON（存入审批单 requestPayload 字段，审批通过后反序列化触发 Python）
     *
     * @param triggerRequest Python 触发请求
     * @return JSON 字符串
     */
    private String serializeTriggerRequest(TaskTriggerRequest triggerRequest) {
        try {
            return objectMapper.writeValueAsString(triggerRequest);
        } catch (Exception e) {
            log.warn("序列化触发请求失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 构建风险判断理由（供审批人参考）
     *
     * @param createRequest 任务创建请求
     * @param riskLevel     风险等级
     * @return 风险判断理由
     */
    private String buildRiskReasoning(TaskCreateRequest createRequest, String riskLevel) {
        return String.format("任务目标: %s, 风险等级: %s", createRequest.getGoal(), riskLevel);
    }

    /**
     * 调用 Python AI 服务触发任务执行
     *
     * @param triggerRequest Python 触发请求
     * @param riskLevel      风险等级（用于日志）
     */
    private void triggerPythonTask(TaskTriggerRequest triggerRequest, String riskLevel) {
        try {
            TaskTriggerResponse response = aiServiceClient.triggerTask(triggerRequest);
            log.info("Python 任务已触发: taskId={}, status={}, riskLevel={}",
                    triggerRequest.getTaskId(), response.getStatus(), riskLevel);
        } catch (Exception e) {
            log.error("触发 Python 任务失败: taskId={}, error={}", triggerRequest.getTaskId(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "AI 服务不可用: " + e.getMessage());
        }
    }

    /**
     * 校验必填参数是否已提供。
     * 简单解析 params JSON 中的 "required":true 标记。
     *
     * @param paramsJson 模板参数定义 JSON
     * @param userParams 用户提供的参数
     * @param templateName 模板名称（用于错误消息）
     */
    private void validateRequiredParams(String paramsJson, Map<String, Object> userParams,
                                         String templateName) {
        if (paramsJson == null || paramsJson.isEmpty() || "[]".equals(paramsJson.trim())) {
            return;
        }
        // 简单解析：匹配 "name":"xxx" 后紧跟 "required":true 的参数
        Pattern pattern = Pattern.compile("\"name\"\\s*:\\s*\"(\\w+)\"[^}]*?\"required\"\\s*:\\s*true");
        Matcher matcher = pattern.matcher(paramsJson);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = userParams.get(paramName);
            ThrowUtils.throwIf(value == null || value.toString().isEmpty(),
                    ErrorCode.PARAM_VALIDATE_FAILED,
                    String.format("缺少必填参数: %s（模板: %s）", paramName, templateName));
        }
    }

    /**
     * 参数映射：将 steps JSON 中的 {{param}} 替换为用户提供的实际值。
     *
     * <p>注意：必须使用 {@link Matcher#quoteReplacement(String)} 包裹 replacement，
     * 否则 {@link Matcher#appendReplacement(StringBuilder, String)} 会将 replacement 中的
     * {@code \} 与 {@code $} 作为元字符解释，破坏 {@link #escapeJson(String)} 已完成的 JSON 转义。</p>
     *
     * @param stepsJson  步骤 JSON 字符串
     * @param userParams 用户提供的参数键值对
     * @return 映射后的步骤 JSON 字符串
     */
    private String resolveParams(String stepsJson, Map<String, Object> userParams) {
        if (stepsJson == null || stepsJson.isEmpty()) {
            return stepsJson;
        }
        Matcher matcher = PARAM_REF_PATTERN.matcher(stepsJson);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = userParams.get(paramName);
            String replacement = value != null ? escapeJson(value.toString()) : "";
            // quoteReplacement 防止 appendReplacement 二次解释 \ 与 $
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * JSON 字符串转义（防止参数值中的特殊字符破坏 JSON 结构）。
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 执行风险检测（M6.1 关键词预筛 + LLM 二次判断）
     *
     * <p>M6.1 阶段：仅记录风险等级到日志，不阻塞任务执行。
     * M6.3 将实现 high/critical 风险等级阻塞任务，等待审批通过后再触发。</p>
     *
     * @param template      工作流模板（含 industry / riskLevel 元数据）
     * @param createRequest 任务创建请求（含 goal / params）
     * @param taskId        任务 ID
     * @return 最终风险等级（low / medium / high / critical），检测失败返回模板配置的 riskLevel
     */
    private String performRiskDetection(WorkflowTemplateEO template, TaskCreateRequest createRequest, Long taskId) {
        String fallbackRiskLevel = template.getRiskLevel() != null ? template.getRiskLevel() : "low";
        try {
            RiskDetectRequest detectRequest = new RiskDetectRequest();
            detectRequest.setGoal(createRequest.getGoal());
            detectRequest.setParams(createRequest.getParams());
            detectRequest.setIndustry(template.getIndustry());
            detectRequest.setTaskId(taskId);

            // 调用预筛 + LLM 二次判断（M6.2 Python 端未实现时回退使用预筛结果）
            RiskJudgeResponse judgeResponse = riskDetectService.detectAndJudge(detectRequest);

            String finalRiskLevel;
            if (judgeResponse != null && judgeResponse.getFinalRiskLevel() != null) {
                finalRiskLevel = judgeResponse.getFinalRiskLevel();
                log.info("风险检测完成（LLM 二次判断）: taskId={}, riskLevel={}, reasoning={}, route={}",
                        taskId, finalRiskLevel, judgeResponse.getReasoning(), judgeResponse.getApprovalRoute());
            } else {
                // M6.1 阶段：LLM 未调用或失败，使用预筛结果
                RiskDetectResultVO detectResult = riskDetectService.detect(detectRequest);
                finalRiskLevel = detectResult.getSuggestedRiskLevel();
                log.info("风险检测完成（仅预筛）: taskId={}, riskLevel={}, hitKeywords={}, largeAmountHit={}",
                        taskId, finalRiskLevel, detectResult.getHighRiskHitCount(), detectResult.isLargeAmountHit());
            }
            return finalRiskLevel;
        } catch (Exception e) {
            log.warn("风险检测失败，使用模板配置的风险等级: taskId={}, fallback={}, error={}",
                    taskId, fallbackRiskLevel, e.getMessage());
            return fallbackRiskLevel;
        }
    }

    // endregion
}

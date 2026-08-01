package com.finrpa.ai.client;

import com.finrpa.ai.client.dto.SkillInfoResponse;
import com.finrpa.ai.client.dto.TaskAbortResponse;
import com.finrpa.ai.client.dto.TaskResumeRequest;
import com.finrpa.ai.client.dto.TaskResumeResponse;
import com.finrpa.ai.client.dto.TaskStateResponse;
import com.finrpa.ai.client.dto.TaskTriggerRequest;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import com.finrpa.approval.dto.request.RiskJudgeRequest;
import com.finrpa.approval.dto.response.RiskJudgeResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * Python AI 服务声明式 HTTP 客户端
 *
 * <p>使用 Spring 6 HTTP Interface（{@code @HttpExchange}）声明式调用 Python finance-ai 服务。
 * 客户端代理由 {@link com.finrpa.ai.config.AiWebClientConfig} 注入，BaseURL 与 X-Internal-Token Header
 * 由 WebClient 默认配置提供。</p>
 *
 * <p>接口契约对齐 Python {@code app/api/tasks.py} 与 {@code app/api/skills.py}：
 * <ul>
 *   <li>POST /api/v1/ai/tasks —— 触发任务执行</li>
 *   <li>GET /api/v1/ai/tasks/{taskId}/state —— 查询任务状态</li>
 *   <li>POST /api/v1/ai/tasks/{taskId}/abort —— 终止任务</li>
 *   <li>POST /api/v1/ai/tasks/{taskId}/resume —— 任务续跑（M4.3）</li>
 *   <li>GET /api/v1/ai/skills —— 查询所有 Skill 元数据（M3.3 用于校验 Skill 存在性）</li>
 *   <li>POST /api/v1/ai/risk/judge —— LLM 风险二次判断（M6.2，M6.1 预留接口）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@HttpExchange(url = "/api/v1/ai", accept = "application/json")
public interface AiServiceClient {

    /**
     * 触发任务执行
     *
     * @param request 任务触发请求
     * @return 任务触发响应
     */
    @PostExchange("/tasks")
    TaskTriggerResponse triggerTask(@RequestBody TaskTriggerRequest request);

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态响应
     */
    @GetExchange("/tasks/{taskId}/state")
    TaskStateResponse getTaskState(@PathVariable("taskId") String taskId);

    /**
     * 终止任务
     *
     * @param taskId 任务 ID
     * @return 任务终止响应
     */
    @PostExchange("/tasks/{taskId}/abort")
    TaskAbortResponse abortTask(@PathVariable("taskId") String taskId);

    /**
     * 查询所有 Skill 元数据
     *
     * <p>M3.3：Java 注册自定义 Skill 时调用此接口校验 name 在 Python 侧真实存在。</p>
     *
     * @return Skill 元数据列表
     */
    @GetExchange("/skills")
    List<SkillInfoResponse> getSkills();

    /**
     * 任务续跑（M4.3：从断点继续执行）
     *
     * <p>Java 侧从 rpa_agent_coordination_state 读取已存计划 + completed_subtasks，
     * 调此接口让 Python Coordinator 从断点继续执行，不重做已完成子任务。</p>
     *
     * @param taskId  任务 ID
     * @param request 续跑请求（含 completedSubtasks + currentPlan + navigationGoal）
     * @return 续跑响应
     */
    @PostExchange("/tasks/{taskId}/resume")
    TaskResumeResponse resumeTask(@PathVariable("taskId") String taskId, @RequestBody TaskResumeRequest request);

    /**
     * LLM 风险二次判断（M6.2 Python 端实现，M6.1 预留接口）
     *
     * <p>Java 关键词预筛命中后调用此接口，由 Python 走三层容错（M5.1 ResilientCaller）调 LLM，
     * 输入任务目标 + 参数 + 预筛结果 → 输出 final_risk_level（low / medium / high / critical）。</p>
     *
     * <p><b>注意</b>：M6.1 阶段 Python 端尚未实现此接口，调用会返回 404 或失败。
     * RiskDetectService 中已做兼容处理：M6.1 仅返回预筛结果，不调用此接口；
     * M6.2 实现 Python 端后由 RiskDetectService 自动接入。</p>
     *
     * @param request 风险判断请求
     * @return 风险判断响应
     */
    @PostExchange("/risk/judge")
    RiskJudgeResponse judgeRisk(@RequestBody RiskJudgeRequest request);
}

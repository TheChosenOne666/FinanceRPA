package com.finrpa.ai.client;

import com.finrpa.ai.client.dto.TaskAbortResponse;
import com.finrpa.ai.client.dto.TaskStateResponse;
import com.finrpa.ai.client.dto.TaskTriggerRequest;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Python AI 服务声明式 HTTP 客户端
 *
 * <p>使用 Spring 6 HTTP Interface（{@code @HttpExchange}）声明式调用 Python finance-ai 服务。
 * 客户端代理由 {@link com.finrpa.ai.config.AiWebClientConfig} 注入，BaseURL 与 X-Internal-Token Header
 * 由 WebClient 默认配置提供。</p>
 *
 * <p>接口契约对齐 Python {@code app/api/tasks.py}：
 * <ul>
 *   <li>POST /api/v1/ai/tasks —— 触发任务执行</li>
 *   <li>GET /api/v1/ai/tasks/{taskId}/state —— 查询任务状态</li>
 *   <li>POST /api/v1/ai/tasks/{taskId}/abort —— 终止任务</li>
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
}

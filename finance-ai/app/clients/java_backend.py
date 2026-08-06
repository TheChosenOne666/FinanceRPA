"""Java 后端回调客户端。

Python AI 服务执行任务后，通过此客户端回调 Java 后端的内部 API，
上报任务状态、子任务状态、截图和审计日志。

URL 约定：Java 后端 context-path 为 /api，内部控制器前缀 /internal，
完整路径如 /api/internal/tasks/{taskId}/state。

鉴权：所有请求携带 X-Internal-Token Header。

@author FinanceRPA
"""

import asyncio
import logging

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)

# 重试配置
_MAX_RETRIES = 3
_RETRY_DELAYS = [1.0, 2.0, 4.0]  # 指数退避（秒）


class JavaBackendClient:
    """回调 Java 后端的 HTTP 客户端。

    职责：
    - 上报任务状态变更 → POST /api/internal/tasks/{taskId}/state
    - 上报子任务状态 → POST /api/internal/tasks/{taskId}/subtasks
    - 上传截图 → POST /api/internal/screenshots
    - 上报审计日志 → POST /api/internal/audit/logs
    - 鉴权：X-Internal-Token Header 校验
    - 重试：指数退避（最多 3 次，1s→2s→4s）
    """

    def __init__(self, base_url: str | None = None, internal_token: str | None = None):
        """
        @param base_url: Java 后端地址（默认从配置读取）
        @param internal_token: 服务间鉴权 token（默认从配置读取）
        """
        settings = get_settings()
        self.base_url = (base_url or settings.backend_base_url).rstrip("/")
        self.internal_token = internal_token or settings.internal_api_token
        self._client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        """获取或创建 httpx 异步客户端。"""
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                headers={"X-Internal-Token": self.internal_token},
                timeout=httpx.Timeout(connect=5.0, read=30.0, write=10.0, pool=5.0),
            )
        return self._client

    async def close(self) -> None:
        """关闭客户端连接。"""
        if self._client and not self._client.is_closed:
            await self._client.aclose()
            self._client = None

    async def _request_with_retry(
        self,
        method: str,
        url: str,
        **kwargs,
    ) -> httpx.Response | None:
        """带重试的 HTTP 请求。

        @param method: HTTP 方法（POST/GET）
        @param url: 请求路径
        @param kwargs: 传递给 httpx 的额外参数
        @return: 响应对象，失败返回 None
        """
        client = await self._get_client()
        last_exc = None
        for attempt in range(_MAX_RETRIES):
            try:
                logger.info(
                    "JavaBackendClient: HTTP %s %s（第 %d/%d 次尝试）",
                    method, url, attempt + 1, _MAX_RETRIES,
                )
                resp = await client.request(method, url, **kwargs)
                resp.raise_for_status()
                logger.info(
                    "JavaBackendClient: HTTP %s %s 成功（status=%d）",
                    method, url, resp.status_code,
                )
                return resp
            except (httpx.HTTPStatusError, httpx.RequestError) as e:
                last_exc = e
                if attempt < _MAX_RETRIES - 1:
                    delay = _RETRY_DELAYS[min(attempt, len(_RETRY_DELAYS) - 1)]
                    logger.warning(
                        "JavaBackendClient: HTTP %s %s 第 %d 次失败: %s，%ds 后重试",
                        method, url, attempt + 1, e, delay,
                    )
                    await asyncio.sleep(delay)
                else:
                    logger.error(
                        "JavaBackendClient: HTTP %s %s 重试 %d 次后全部失败: %s",
                        method, url, _MAX_RETRIES, e,
                    )
        return None

    async def update_task_state(
        self,
        task_id: str,
        state: str,
        current_step: int = 0,
        total_steps: int = 0,
        message: str = "",
        error_message: str | None = None,
    ) -> bool:
        """更新任务状态。

        @param task_id: 任务 ID
        @param state: 新状态（大写枚举：PENDING/EXECUTING/SUCCESS/FAILED/NEEDS_HUMAN/ABORTED）
        @param current_step: 当前步骤
        @param total_steps: 总步骤数
        @param message: 附加消息
        @param error_message: 错误信息（失败时填写）
        @return: 是否成功
        """
        payload = {
            "state": state,
            "currentStep": current_step,
            "totalSteps": total_steps,
            "message": message,
        }
        if error_message:
            payload["errorMessage"] = error_message

        logger.info(
            "JavaBackendClient: 更新任务状态 [task=%s, state=%s, step=%d/%d, msg=%s]",
            task_id, state, current_step, total_steps, message,
        )
        resp = await self._request_with_retry(
            "POST", f"/api/internal/tasks/{task_id}/state", json=payload,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 更新任务状态失败 [task=%s, state=%s]", task_id, state,
            )
            return False
        logger.info("JavaBackendClient: 更新任务状态成功 [task=%s, state=%s]", task_id, state)
        return True

    async def update_subtask(
        self,
        task_id: str,
        subtask_index: int,
        status: str,
        error_message: str | None = None,
        result_data: dict | None = None,
    ) -> bool:
        """更新子任务状态。

        @param task_id: 任务 ID
        @param subtask_index: 子任务序号
        @param status: 子任务状态（大写：PENDING/RUNNING/COMPLETED/FAILED/SKIPPED/REPLANNED）
        @param error_message: 错误信息（失败时填写）
        @param result_data: 执行结果数据
        @return: 是否成功
        """
        payload = {
            "subtaskIndex": subtask_index,
            "status": status,
        }
        if error_message:
            payload["errorMessage"] = error_message
        if result_data:
            payload["resultData"] = result_data

        logger.info(
            "JavaBackendClient: 更新子任务状态 [task=%s, index=%d, status=%s]",
            task_id, subtask_index, status,
        )
        resp = await self._request_with_retry(
            "POST", f"/api/internal/tasks/{task_id}/subtasks", json=payload,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 更新子任务状态失败 [task=%s, index=%d, status=%s]",
                task_id, subtask_index, status,
            )
            return False
        logger.info(
            "JavaBackendClient: 更新子任务状态成功 [task=%s, index=%d, status=%s]",
            task_id, subtask_index, status,
        )
        return True

    async def update_coordination_state(
        self,
        task_id: str,
        navigation_goal: str | None = None,
        current_plan: str | None = None,
        completed_subtasks: list[str] | None = None,
        total_replans: int | None = None,
        max_replans: int | None = None,
        status: str | None = None,
        error_message: str | None = None,
    ) -> bool:
        """更新协调状态（M4.2 引入，持久化 CoordinationState 到 Java）。

        Python Coordinator 每步执行后回调此接口，将 CoordinationState 持久化到
        Java 侧 rpa_agent_coordination_state 表，用于断点续跑和 replan 追踪。

        @param task_id: 任务 ID
        @param navigation_goal: 导航目标
        @param current_plan: 当前计划 JSON 字符串
        @param completed_subtasks: 已完成子任务 ID 列表
        @param total_replans: 总重规划次数
        @param max_replans: 最大重规划次数
        @param status: 协调状态（RUNNING/COMPLETED/FAILED/NEEDS_HUMAN）
        @param error_message: 错误信息
        @return: 是否成功
        """
        payload: dict = {}
        if navigation_goal is not None:
            payload["navigationGoal"] = navigation_goal
        if current_plan is not None:
            payload["currentPlan"] = current_plan
        if completed_subtasks is not None:
            payload["completedSubtasks"] = completed_subtasks
        if total_replans is not None:
            payload["totalReplans"] = total_replans
        if max_replans is not None:
            payload["maxReplans"] = max_replans
        if status is not None:
            payload["status"] = status
        if error_message is not None:
            payload["errorMessage"] = error_message

        logger.info(
            "JavaBackendClient: 更新协调状态 [task=%s, status=%s, replans=%s, completed=%d]",
            task_id, status, total_replans,
            len(completed_subtasks) if completed_subtasks else 0,
        )
        resp = await self._request_with_retry(
            "POST", f"/api/internal/tasks/{task_id}/coordination-state", json=payload,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 更新协调状态失败 [task=%s, status=%s]", task_id, status,
            )
            return False
        logger.info(
            "JavaBackendClient: 更新协调状态成功 [task=%s, status=%s]", task_id, status,
        )
        return True

    async def upload_screenshot(
        self,
        org_id: str,
        task_id: str,
        step_index: int,
        phase: str,
        image_data: bytes,
    ) -> str | None:
        """上传截图到 Java 后端（M7.2 转发 MinIO）。

        对齐 Java InternalScreenshotController 参数：
        multipart/form-data，file + orgId + taskId + stepIndex + phase。

        @param org_id: 组织 ID（决定 bucket 名 finrpa-audit-{org_id}）
        @param task_id: 任务 ID
        @param step_index: 步骤序号
        @param phase: 阶段（before / after）
        @param image_data: PNG 二进制数据
        @return: 预签名 URL，失败返回 None
        """
        logger.info(
            "JavaBackendClient: 上传截图 [org=%s, task=%s, step=%d, phase=%s, size=%d bytes]",
            org_id, task_id, step_index, phase, len(image_data),
        )
        resp = await self._request_with_retry(
            "POST",
            "/api/internal/screenshots",
            files={"file": (f"{task_id}_step{step_index}_{phase}.png", image_data, "image/png")},
            data={
                "orgId": org_id,
                "taskId": task_id,
                "stepIndex": str(step_index),
                "phase": phase,
            },
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 上传截图失败 [task=%s, step=%d, phase=%s]",
                task_id, step_index, phase,
            )
            return None
        data = resp.json()
        # Java BaseResponse 封装：{ code: 0, data: { objectPath, presignUrl } }
        inner = data.get("data") or {}
        url = inner.get("presignUrl") or data.get("presignUrl")
        logger.info(
            "JavaBackendClient: 上传截图成功 [task=%s, step=%d, phase=%s, url=%s]",
            task_id, step_index, phase, url,
        )
        return url

    async def report_audit_log(self, payload) -> bool:
        """上报审计日志（M7.3，对齐 Java AuditLogCreateRequest 完整结构）。

        接受 AuditLogPayload 或 dict，统一序列化为 Java 侧期望的 JSON。

        @param payload: AuditLogPayload 对象或 dict（含完整审计字段）
        @return: 是否成功
        """
        # 统一转为 dict（AuditLogPayload → by_alias 序列化）
        # mode='json'：将 datetime 等非 JSON 原生类型转为 ISO 字符串，避免 httpx json 序列化失败
        if hasattr(payload, "model_dump"):
            data = payload.model_dump(by_alias=True, exclude_none=True, mode="json")
        else:
            data = dict(payload)

        task_id = data.get("taskId", "unknown")
        action_type = data.get("actionType", "unknown")
        logger.info(
            "JavaBackendClient: 上报审计日志 [task=%s, action=%s, result=%s]",
            task_id, action_type, data.get("executionResult", "success"),
        )
        resp = await self._request_with_retry(
            "POST", "/api/internal/audit/logs", json=data,
        )
        if resp is None:
            logger.error(
                "JavaBackendClient: 上报审计日志失败 [task=%s, action=%s]",
                task_id, action_type,
            )
            return False
        logger.info(
            "JavaBackendClient: 上报审计日志成功 [task=%s, action=%s]",
            task_id, action_type,
        )
        return True

    async def report_llm_call(self, record: dict) -> bool:
        """上报 LLM 调用记录（M5.1 引入，M5.4 实现 Java 侧持久化）。

        每次 LLM 调用（含重试）均通过此接口上报，Java 侧持久化到 rpa_llm_call_log 表
        用于调用统计与成本分析。

        @param record: LLM 调用记录字典（LlmCallRecord.model_dump()）
        @return: 是否成功
        """
        logger.info(
            "JavaBackendClient: 上报 LLM 调用记录 [task=%s, context=%s, retry=%d, success=%s]",
            record.get("task_id"), record.get("context_name"),
            record.get("retry_attempt", 0), record.get("success"),
        )
        resp = await self._request_with_retry(
            "POST", "/api/internal/llm/calls", json=record,
        )
        if resp is None:
            logger.warning(
                "JavaBackendClient: 上报 LLM 调用记录失败 [task=%s, context=%s]（Java 侧端点可能未实现）",
                record.get("task_id"), record.get("context_name"),
            )
            return False
        logger.info(
            "JavaBackendClient: 上报 LLM 调用记录成功 [task=%s, context=%s]",
            record.get("task_id"), record.get("context_name"),
        )
        return True

    async def report_needs_human(
        self,
        task_id: str,
        org_id: str | None = None,
        subtask_id: str | None = None,
        context_name: str = "unknown",
        screenshot_url: str | None = None,
        llm_raw_output: str | None = None,
        validation_error: str | None = None,
        attempts: int = 0,
    ) -> bool:
        """上报 NEEDS_HUMAN 事件入队（M5.5，Java 侧持久化到 rpa_needs_human_queue 表）。

        Python ResilientCaller 重试耗尽后调用此接口，将 LLM 原始输出、校验错误
        等详情上报入队，供操作员查看并处置（skip / manual / abort）。

        @param task_id: 任务 ID
        @param org_id: 组织 ID（租户隔离）
        @param subtask_id: 子任务 ID（可空）
        @param context_name: 调用上下文名称（planner / replan / executor 等）
        @param screenshot_url: 截图 URL（可空）
        @param llm_raw_output: LLM 最后一次原始输出
        @param validation_error: 校验错误信息
        @param attempts: 总尝试次数（含首次）
        @return: 是否成功
        """
        payload = {
            "task_id": task_id,
            "org_id": org_id,
            "subtask_id": subtask_id,
            "context_name": context_name,
            "screenshot_url": screenshot_url,
            "llm_raw_output": llm_raw_output,
            "validation_error": validation_error,
            "attempts": attempts,
        }
        logger.info(
            "JavaBackendClient: 上报 NEEDS_HUMAN 事件 [task=%s, context=%s, attempts=%d]",
            task_id, context_name, attempts,
        )
        resp = await self._request_with_retry(
            "POST", "/api/internal/llm/needs-human", json=payload,
        )
        if resp is None:
            logger.warning(
                "JavaBackendClient: 上报 NEEDS_HUMAN 事件失败 [task=%s, context=%s]",
                task_id, context_name,
            )
            return False
        logger.info(
            "JavaBackendClient: 上报 NEEDS_HUMAN 事件成功 [task=%s, context=%s]",
            task_id, context_name,
        )
        return True

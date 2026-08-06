"""ResilientCaller：三层容错 LLM 调用器。

M5.1 实现，提供 LLM 调用的三层容错机制：

层 1（Prompt 约束）：
  - 自动从 Pydantic 输出模型提取 JSON Schema 注入 prompt
  - 强制要求 LLM 仅输出合法 JSON（无多余文本、无 markdown 代码块）
  - 内置 few-shot 示例引导 LLM 输出格式

层 2（Pydantic 校验 + 失败反馈重试）：
  - LLM 返回后解析 JSON → Pydantic 校验
  - 校验失败时将错误信息 + LLM 上次输出反馈给 LLM，要求修正
  - 默认重试 2 次（可配置），每次反馈包含具体校验错误字段

层 3（NEEDS_HUMAN 兜底）：
  - 重试耗尽仍失败时抛出 NeedsHumanError
  - 调用方（Planner/Coordinator）捕获后转 NEEDS_HUMAN 状态
  - 自动上报 Java：LLM 调用记录 + NEEDS_HUMAN 事件

调用示例：
    caller = ResilientCaller(llm_callable, max_retries=2)
    result = await caller.call(
        prompt="分解任务...",
        output_model=PlannerOutput,
        task_id="700000000000000001",
        context_name="planner",
    )

@author FinanceRPA
"""

import json
import logging
import time
from datetime import datetime, timezone
from typing import Any, Awaitable, Callable, TypeVar

from pydantic import BaseModel, ValidationError

from app.clients.java_backend import JavaBackendClient
from app.llm.action_cache import ActionCache

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)

# 默认配置
_DEFAULT_MAX_RETRIES = 2
_DEFAULT_MODEL_NAME = "gpt-4o-mini"


class NeedsHumanError(Exception):
    """LLM 调用重试耗尽，需转 NEEDS_HUMAN 状态。

    @attribute last_raw: LLM 最后一次返回的原始文本
    @attribute last_error: 最后一次校验错误信息
    @attribute attempts: 总尝试次数（含首次）
    """

    def __init__(
        self,
        message: str,
        last_raw: str | None = None,
        last_error: str | None = None,
        attempts: int = 0,
    ):
        super().__init__(message)
        self.last_raw = last_raw
        self.last_error = last_error
        self.attempts = attempts


class LlmCallRecord(BaseModel):
    """LLM 调用记录（上报 Java 持久化，M5.4 实现 Java 侧）。

    每次 LLM 调用（含重试）均生成一条记录，通过 JavaBackendClient.report_llm_call 上报。
    """

    task_id: str | None = None
    org_id: str | None = None
    model: str = _DEFAULT_MODEL_NAME
    context_name: str = "unknown"
    retry_attempt: int = 0
    success: bool = False
    error_message: str | None = None
    duration_ms: int = 0
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None
    cache_hit: bool = False
    timestamp: str = ""


class ResilientCaller:
    """三层容错 LLM 调用器。

    职责：
    - 层 1：Prompt 注入 JSON Schema + few-shot 约束 LLM 输出格式
    - 层 2：Pydantic 校验 + 失败反馈重试（默认 2 次）
    - 层 3：重试耗尽抛 NeedsHumanError + 上报 Java NEEDS_HUMAN 事件
    - 每次调用上报 Java LLM 调用记录（M5.4 持久化）
    """

    def __init__(
        self,
        llm_callable: Callable[[str], Awaitable[str]],
        max_retries: int = _DEFAULT_MAX_RETRIES,
        java_client: JavaBackendClient | None = None,
        model_name: str = _DEFAULT_MODEL_NAME,
        action_cache: ActionCache | None = None,
    ):
        """
        @param llm_callable: 异步函数 prompt -> str（原始 LLM 调用）
        @param max_retries: 最大重试次数（默认 2，即首次 + 2 次重试 = 3 次尝试）
        @param java_client: Java 后端回调客户端（上报调用记录 + NEEDS_HUMAN 事件）
        @param model_name: LLM 模型名称（上报调用记录用）
        @param action_cache: LLM Action 缓存（M5.2，传入则启用缓存）
        """
        self.llm_callable = llm_callable
        self.max_retries = max_retries
        self.java_client = java_client
        self.model_name = model_name
        self.action_cache = action_cache

    async def call(
        self,
        prompt: str,
        output_model: type[T],
        task_id: str | None = None,
        org_id: str | None = None,
        context_name: str = "unknown",
        cache_key_dom: str | None = None,
        cache_key_goal: str | None = None,
    ) -> T:
        """三层容错调用 LLM，返回 Pydantic 校验后的结果。

        @param prompt: 用户原始 prompt（不含 JSON Schema 约束）
        @param output_model: 期望的 Pydantic 输出模型类型
        @param task_id: 任务 ID（上报调用记录 + NEEDS_HUMAN 用）
        @param org_id: 组织 ID（上报用）
        @param context_name: 调用上下文名称（"planner" / "replan" / "executor" 等，日志用）
        @param cache_key_dom: 缓存 Key 的 DOM 结构（M5.2，传入则启用缓存查询）
        @param cache_key_goal: 缓存 Key 的导航目标（M5.2，需与 cache_key_dom 同时传入）
        @return: Pydantic 校验后的结果
        @raises NeedsHumanError: 重试耗尽仍失败
        """
        # M5.2：Action 缓存查询（cache_key_dom + cache_key_goal 同时传入时启用）
        if self.action_cache and cache_key_dom and cache_key_goal:
            cached = await self.action_cache.get(cache_key_dom, cache_key_goal)
            if cached is not None:
                logger.info(
                    "ResilientCaller: 缓存命中，跳过 LLM 调用 [task=%s, context=%s]",
                    task_id, context_name,
                )
                result = output_model.model_validate(cached)
                # 上报缓存命中记录
                await self._report_call(
                    task_id=task_id, org_id=org_id, context_name=context_name,
                    retry_attempt=0, success=True, duration_ms=0, cache_hit=True,
                )
                return result

        # 层 1：Prompt 注入 JSON Schema 约束
        enhanced_prompt = self._enhance_prompt(prompt, output_model)

        last_raw: str | None = None
        last_error: str | None = None
        total_attempts = self.max_retries + 1

        for attempt in range(total_attempts):
            logger.info(
                "ResilientCaller: LLM 调用 [task=%s, context=%s, attempt=%d/%d, model=%s]",
                task_id, context_name, attempt + 1, total_attempts, self.model_name,
            )

            start = time.monotonic()
            try:
                # 调用 LLM
                raw = await self.llm_callable(enhanced_prompt)
                elapsed_ms = int((time.monotonic() - start) * 1000)
                last_raw = raw

                # 解析 JSON + Pydantic 校验
                data = self._parse_json(raw)
                result = output_model.model_validate(data)

                logger.info(
                    "ResilientCaller: LLM 调用成功 [task=%s, context=%s, attempt=%d, elapsed=%dms]",
                    task_id, context_name, attempt + 1, elapsed_ms,
                )

                # 上报成功记录
                await self._report_call(
                    task_id=task_id, org_id=org_id, context_name=context_name,
                    retry_attempt=attempt, success=True, duration_ms=elapsed_ms,
                )

                # M5.2：写入 Action 缓存（cache_key_dom + cache_key_goal 同时传入时）
                if self.action_cache and cache_key_dom and cache_key_goal:
                    await self.action_cache.set(
                        cache_key_dom, cache_key_goal, data,
                    )

                return result

            except (json.JSONDecodeError, ValidationError) as e:
                elapsed_ms = int((time.monotonic() - start) * 1000)
                last_error = str(e)
                logger.warning(
                    "ResilientCaller: LLM 校验失败 [task=%s, context=%s, attempt=%d/%d, error=%s]",
                    task_id, context_name, attempt + 1, total_attempts, last_error[:200],
                )

                # 上报失败记录
                await self._report_call(
                    task_id=task_id, org_id=org_id, context_name=context_name,
                    retry_attempt=attempt, success=False, duration_ms=elapsed_ms,
                    error_message=last_error,
                )

                # 层 2：还有重试机会 → 反馈错误给 LLM
                if attempt < self.max_retries:
                    enhanced_prompt = self._add_error_feedback(
                        enhanced_prompt, raw, last_error,
                    )
                    logger.info(
                        "ResilientCaller: 准备重试 [task=%s, context=%s, next_attempt=%d]",
                        task_id, context_name, attempt + 2,
                    )
                    continue

        # 层 3：重试耗尽 → NEEDS_HUMAN
        logger.error(
            "ResilientCaller: 重试耗尽，转 NEEDS_HUMAN [task=%s, context=%s, attempts=%d, last_error=%s]",
            task_id, context_name, total_attempts, (last_error or "")[:200],
        )

        # 上报 NEEDS_HUMAN 事件到 Java
        await self._report_needs_human(
            task_id=task_id, org_id=org_id, context_name=context_name,
            last_raw=last_raw, last_error=last_error, attempts=total_attempts,
        )

        raise NeedsHumanError(
            message=f"LLM 调用重试 {self.max_retries} 次后仍失败: {last_error}",
            last_raw=last_raw,
            last_error=last_error,
            attempts=total_attempts,
        )

    def _enhance_prompt(self, prompt: str, output_model: type[BaseModel]) -> str:
        """层 1：注入 JSON Schema 约束 + few-shot 示例。

        @param prompt: 用户原始 prompt
        @param output_model: Pydantic 输出模型类型
        @return: 增强后的 prompt（含 JSON Schema + 格式约束）
        """
        try:
            schema = output_model.model_json_schema()
            schema_str = json.dumps(schema, ensure_ascii=False, indent=2)
        except Exception:
            schema_str = "{}"

        return (
            f"{prompt}\n\n"
            f"--- OUTPUT FORMAT ---\n"
            f"You MUST respond with ONLY a valid JSON object. No markdown, no code blocks, no extra text.\n"
            f"The JSON must conform to this schema:\n{schema_str}\n"
            f"--- END OUTPUT FORMAT ---"
        )

    def _add_error_feedback(self, prompt: str, raw_output: str, error: str) -> str:
        """层 2：将校验错误反馈给 LLM，要求修正。

        @param prompt: 上次使用的 prompt
        @param raw_output: LLM 上次的原始输出
        @param error: 校验错误信息
        @return: 追加了错误反馈的新 prompt
        """
        return (
            f"{prompt}\n\n"
            f"--- PREVIOUS ATTEMPT FAILED ---\n"
            f"Your previous output was:\n{raw_output[:500]}\n\n"
            f"Validation error:\n{error[:500]}\n\n"
            f"Please fix the issue and output ONLY a valid JSON object conforming to the schema."
        )

    def _parse_json(self, raw: str) -> dict[str, Any]:
        """解析 LLM 返回的 JSON（兼容 markdown 代码块包裹）。

        @param raw: LLM 原始返回
        @return: 解析后的字典
        @raises json.JSONDecodeError: JSON 解析失败
        """
        cleaned = raw.strip()

        # 兼容 ```json ... ``` 代码块包裹
        if cleaned.startswith("```"):
            lines = cleaned.split("\n")
            # 去掉首行 ``` 和末行 ```
            if len(lines) >= 3:
                cleaned = "\n".join(lines[1:-1])
            elif len(lines) == 2:
                cleaned = lines[1]

        return json.loads(cleaned)

    async def _report_call(
        self,
        task_id: str | None,
        org_id: str | None,
        context_name: str,
        retry_attempt: int,
        success: bool,
        duration_ms: int,
        error_message: str | None = None,
        cache_hit: bool = False,
    ) -> None:
        """上报单次 LLM 调用记录到 Java（M5.4 实现 Java 侧持久化）。

        失败不阻断主流程（仅 warning 日志）。
        """
        if not self.java_client:
            return

        record = LlmCallRecord(
            task_id=task_id,
            org_id=org_id,
            model=self.model_name,
            context_name=context_name,
            retry_attempt=retry_attempt,
            success=success,
            error_message=error_message,
            duration_ms=duration_ms,
            cache_hit=cache_hit,
            timestamp=datetime.now(timezone.utc).isoformat(),
        )

        try:
            await self.java_client.report_llm_call(record.model_dump())
        except Exception as e:
            logger.warning(
                "ResilientCaller: 上报 LLM 调用记录失败 [task=%s, context=%s]: %s",
                task_id, context_name, e,
            )

    async def _report_needs_human(
        self,
        task_id: str | None,
        org_id: str | None,
        context_name: str,
        last_raw: str | None,
        last_error: str | None,
        attempts: int,
    ) -> None:
        """上报 NEEDS_HUMAN 事件到 Java（转人工介入状态 + 详情入队）。

        两步上报：
        1. update_task_state(state=NEEDS_HUMAN) —— 更新任务状态为 NEEDS_HUMAN
        2. report_needs_human() —— 上报详情入队（M5.5，供操作员查看处置）

        失败不阻断主流程（仅 warning 日志）。
        """
        if not self.java_client or not task_id:
            return

        message = f"LLM 调用重试耗尽（{context_name}，{attempts} 次尝试）: {last_error or '未知错误'}"
        try:
            # 步骤 1：更新任务状态
            await self.java_client.update_task_state(
                task_id=task_id,
                state="NEEDS_HUMAN",
                message=message,
                error_message=last_error,
            )
            logger.info(
                "ResilientCaller: 已更新任务状态为 NEEDS_HUMAN [task=%s, context=%s]",
                task_id, context_name,
            )
        except Exception as e:
            logger.warning(
                "ResilientCaller: 更新任务状态为 NEEDS_HUMAN 失败 [task=%s, context=%s]: %s",
                task_id, context_name, e,
            )

        # 步骤 2：上报详情入队（M5.5）
        try:
            await self.java_client.report_needs_human(
                task_id=task_id,
                org_id=org_id,
                context_name=context_name,
                llm_raw_output=last_raw,
                validation_error=last_error,
                attempts=attempts,
            )
            logger.info(
                "ResilientCaller: 已上报 NEEDS_HUMAN 详情入队 [task=%s, context=%s]",
                task_id, context_name,
            )
        except Exception as e:
            logger.warning(
                "ResilientCaller: 上报 NEEDS_HUMAN 详情入队失败 [task=%s, context=%s]: %s",
                task_id, context_name, e,
            )

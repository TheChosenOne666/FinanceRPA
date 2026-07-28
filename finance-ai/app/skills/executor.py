"""Skill 执行引擎。

按顺序执行一组 Skill（Skill 管线），包含错误处理、重试逻辑和审计日志集成。

@from enterprise/skills/executor.py
@author FinanceRPA
"""

import logging
import time
from dataclasses import dataclass, field
from typing import Any

from .base import (
    BaseSkill,
    ErrorStrategy,
    SkillResult,
    SkillStatus,
    get_skill,
)

logger = logging.getLogger(__name__)


@dataclass
class SkillStep:
    """Skill 管线中的单个步骤。"""

    skill_name: str
    params: dict[str, Any]
    description: str = ""
    error_strategy_override: str | None = None  # 覆盖 Skill 默认策略


@dataclass
class PipelineResult:
    """执行完整 Skill 管线的结果。"""

    success: bool = True
    steps_completed: int = 0
    steps_total: int = 0
    step_results: list[dict[str, Any]] = field(default_factory=list)
    total_duration_ms: int = 0
    aborted_at_step: int | None = None
    error_message: str | None = None


async def execute_pipeline(
    steps: list[SkillStep],
    context: dict[str, Any] | None = None,
    audit_callback=None,
) -> PipelineResult:
    """执行一组 Skill 步骤。

    @param steps: 按顺序执行的 SkillStep 列表
    @param context: 共享执行上下文（浏览器页面、会话等）
    @param audit_callback: 可选异步回调(step_index, skill_name, params_dict, result) 用于审计日志
    @return: 包含每步结果和整体状态的 PipelineResult
    """
    pipeline_start = time.monotonic()
    result = PipelineResult(steps_total=len(steps))

    for i, step in enumerate(steps):
        # 1. 查找 Skill
        skill_cls = get_skill(step.skill_name)
        if skill_cls is None:
            logger.error("未知 Skill: %s（步骤 %d）", step.skill_name, i)
            result.step_results.append({
                "step": i,
                "skill": step.skill_name,
                "status": "failed",
                "error": f"Unknown skill: {step.skill_name}",
            })
            result.success = False
            result.aborted_at_step = i
            result.error_message = f"Unknown skill: {step.skill_name}"
            break

        skill: BaseSkill = skill_cls()
        error_strategy = (
            ErrorStrategy(step.error_strategy_override)
            if step.error_strategy_override
            else skill.error_strategy
        )

        # 2. 校验参数
        try:
            validated_params = skill.validate_params(step.params)
        except Exception as e:
            logger.error("Skill %s 参数校验失败: %s", step.skill_name, e)
            result.step_results.append({
                "step": i,
                "skill": step.skill_name,
                "status": "failed",
                "error": f"Invalid params: {e}",
            })
            if error_strategy == ErrorStrategy.ABORT:
                result.success = False
                result.aborted_at_step = i
                result.error_message = f"参数校验失败于步骤 {i}"
                break
            continue

        # 3. 执行（带重试）
        max_attempts = skill.max_retries + 1 if error_strategy == ErrorStrategy.RETRY else 1
        skill_result: SkillResult | None = None

        for attempt in range(max_attempts):
            skill_result = await skill.execute(validated_params, context)

            if skill_result.status == SkillStatus.COMPLETED:
                break

            if attempt < max_attempts - 1:
                logger.info(
                    "重试 %s（第 %d/%d 次）",
                    step.skill_name, attempt + 2, max_attempts,
                )

        assert skill_result is not None

        # 4. 记录步骤结果
        step_record = {
            "step": i,
            "skill": step.skill_name,
            "status": skill_result.status.value,
            "duration_ms": skill_result.duration_ms,
            "data": skill_result.data,
        }
        if skill_result.error_message:
            step_record["error"] = skill_result.error_message
        result.step_results.append(step_record)

        # 5. 审计回调
        if audit_callback:
            try:
                audit_dict = skill.to_audit_dict(validated_params)
                await audit_callback(i, step.skill_name, audit_dict, skill_result)
            except Exception as e:
                logger.warning("步骤 %d 审计回调失败: %s", i, e)

        # 6. 按错误策略处理失败
        if skill_result.status in (SkillStatus.FAILED, SkillStatus.SKIPPED):
            if skill_result.status == SkillStatus.FAILED:
                if error_strategy == ErrorStrategy.ABORT:
                    result.success = False
                    result.aborted_at_step = i
                    result.error_message = (
                        f"步骤 {i}（{step.skill_name}）失败: {skill_result.error_message}"
                    )
                    break
                elif error_strategy == ErrorStrategy.SKIP:
                    logger.info("跳过失败步骤 %d（%s）", i, step.skill_name)
                    continue
            # RETRY 耗尽
            if error_strategy == ErrorStrategy.RETRY and skill_result.status == SkillStatus.FAILED:
                result.success = False
                result.aborted_at_step = i
                result.error_message = (
                    f"步骤 {i}（{step.skill_name}）重试耗尽: {skill_result.error_message}"
                )
                break

        result.steps_completed += 1

    result.total_duration_ms = int((time.monotonic() - pipeline_start) * 1000)
    return result

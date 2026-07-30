"""Skill 执行引擎。

按顺序执行一组 Skill（Skill 管线），包含错误处理、重试逻辑和审计日志集成。

M3.2 增强：
- 参数映射：支持 param_mapping（引用模式 + 字面量模式 + 上下文引用）
- 上下文传递：前一个 Skill 输出存入 context["step_results"]，后续步骤可引用
- 失败处理：调用 skill.get_failure_strategy(error) 替代直接读 ClassVar
- 审计回调：增强回调签名，携带 workflow_params 和 step_results

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
from .param_resolver import resolve_param_mapping

logger = logging.getLogger(__name__)


@dataclass
class SkillStep:
    """Skill 管线中的单个步骤。

    支持两种参数传入方式：
    1. params：直接传入完整参数字典（简单场景）
    2. param_mapping：参数映射字典，执行时通过 resolve_param_mapping() 解析为实际值
       （工作流模板场景，引用工作流参数或前序步骤输出）

    若 param_mapping 存在，将覆盖 params 中对应的键。
    """

    skill_name: str
    params: dict[str, Any]
    description: str = ""
    error_strategy_override: str | None = None  # 覆盖 Skill 默认策略
    param_mapping: dict[str, str] | None = None  # M3.2：参数映射（引用/字面量/上下文引用）


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
    workflow_params: dict[str, Any] | None = None,
    audit_callback=None,
) -> PipelineResult:
    """执行一组 Skill 步骤。

    @param steps: 按顺序执行的 SkillStep 列表
    @param context: 共享执行上下文（浏览器页面、会话等）
    @param workflow_params: 工作流参数字典（供 param_mapping 引用解析）
    @param audit_callback: 可选异步回调(step_index, skill_name, params_dict, result)
                           用于审计日志
    @return: 包含每步结果和整体状态的 PipelineResult
    """
    pipeline_start = time.monotonic()
    result = PipelineResult(steps_total=len(steps))

    # 初始化上下文（确保 context 存在，并初始化 step_results 列表）
    if context is None:
        context = {}
    if "step_results" not in context:
        context["step_results"] = []

    logger.info(
        "Pipeline 开始执行，共 %d 个步骤，workflow_params keys=%s",
        len(steps),
        list(workflow_params.keys()) if workflow_params else [],
    )

    for i, step in enumerate(steps):
        # 1. 查找 Skill
        skill_cls = get_skill(step.skill_name)
        if skill_cls is None:
            logger.error("Pipeline 步骤 %d: 未知 Skill '%s'", i, step.skill_name)
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

        # 2. 参数映射解析（M3.2 新增）
        # 若 step.param_mapping 存在，解析映射并合并到 step.params
        if step.param_mapping:
            logger.info(
                "Pipeline 步骤 %d: 解析参数映射 param_mapping=%s",
                i,
                step.param_mapping,
            )
            resolved = resolve_param_mapping(
                step.param_mapping,
                workflow_params,
                context["step_results"],
            )
            # 合并：resolved 覆盖 step.params 中的同名键
            step.params = {**step.params, **resolved}
            logger.info(
                "Pipeline 步骤 %d: 参数映射解析完成，最终 params keys=%s",
                i,
                list(step.params.keys()),
            )

        # 3. 确定错误策略
        # M3.2：优先使用 error_strategy_override，否则用 skill.get_failure_strategy()
        if step.error_strategy_override:
            error_strategy = ErrorStrategy(step.error_strategy_override)
        else:
            error_strategy = skill.get_failure_strategy()

        # 4. 校验参数
        try:
            validated_params = skill.validate_params(step.params)
        except Exception as e:
            logger.error("Pipeline 步骤 %d: Skill '%s' 参数校验失败: %s", i, step.skill_name, e)
            result.step_results.append({
                "step": i,
                "skill": step.skill_name,
                "status": "failed",
                "error": f"Invalid params: {e}",
            })
            # 参数校验失败时，动态获取失败策略
            fail_strategy = skill.get_failure_strategy(str(e))
            if fail_strategy == ErrorStrategy.ABORT:
                result.success = False
                result.aborted_at_step = i
                result.error_message = f"参数校验失败于步骤 {i}"
                break
            continue

        # 5. 执行（带重试）
        max_attempts = skill.max_retries + 1 if error_strategy == ErrorStrategy.RETRY else 1
        skill_result: SkillResult | None = None

        logger.info(
            "Pipeline 步骤 %d: 开始执行 Skill '%s'，error_strategy=%s，max_attempts=%d",
            i,
            step.skill_name,
            error_strategy.value,
            max_attempts,
        )

        for attempt in range(max_attempts):
            skill_result = await skill.execute(validated_params, context)

            if skill_result.status == SkillStatus.COMPLETED:
                break

            if attempt < max_attempts - 1:
                logger.info(
                    "Pipeline 步骤 %d: 重试 Skill '%s'（第 %d/%d 次）",
                    i,
                    step.skill_name,
                    attempt + 2,
                    max_attempts,
                )

        assert skill_result is not None

        # 6. 记录步骤结果
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

        # 7. 上下文传递：将步骤结果存入 context["step_results"]（M3.2 新增）
        context["step_results"].append(step_record)
        logger.info(
            "Pipeline 步骤 %d: 完成，status=%s，duration=%dms，step_results 长度=%d",
            i,
            skill_result.status.value,
            skill_result.duration_ms or 0,
            len(context["step_results"]),
        )

        # 8. 审计回调
        if audit_callback:
            try:
                audit_dict = skill.to_audit_dict(validated_params)
                await audit_callback(i, step.skill_name, audit_dict, skill_result)
            except Exception as e:
                logger.warning("Pipeline 步骤 %d: 审计回调失败: %s", i, e)

        # 9. 按错误策略处理失败（M3.2：使用 get_failure_strategy 动态决策）
        if skill_result.status == SkillStatus.FAILED:
            # 确定失败策略：error_strategy_override 优先，否则动态获取
            if step.error_strategy_override:
                fail_strategy = ErrorStrategy(step.error_strategy_override)
            else:
                fail_strategy = skill.get_failure_strategy(skill_result.error_message)

            if fail_strategy == ErrorStrategy.ABORT:
                logger.error(
                    "Pipeline 步骤 %d: Skill '%s' 失败，策略=ABORT，终止管线",
                    i,
                    step.skill_name,
                )
                result.success = False
                result.aborted_at_step = i
                result.error_message = (
                    f"步骤 {i}（{step.skill_name}）失败: {skill_result.error_message}"
                )
                break
            elif fail_strategy == ErrorStrategy.SKIP:
                logger.info(
                    "Pipeline 步骤 %d: Skill '%s' 失败，策略=SKIP，跳过继续",
                    i,
                    step.skill_name,
                )
                continue
            # RETRY 耗尽
            if fail_strategy == ErrorStrategy.RETRY:
                logger.error(
                    "Pipeline 步骤 %d: Skill '%s' 重试耗尽（%d 次），终止管线",
                    i,
                    step.skill_name,
                    max_attempts,
                )
                result.success = False
                result.aborted_at_step = i
                result.error_message = (
                    f"步骤 {i}（{step.skill_name}）重试耗尽: {skill_result.error_message}"
                )
                break

        result.steps_completed += 1

    result.total_duration_ms = int((time.monotonic() - pipeline_start) * 1000)
    logger.info(
        "Pipeline 执行结束，success=%s，steps_completed=%d/%d，total_duration=%dms",
        result.success,
        result.steps_completed,
        result.steps_total,
        result.total_duration_ms,
    )
    return result

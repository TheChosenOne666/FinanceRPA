"""RiskJudgeService：LLM 风险二次判断服务（M6.2）。

接收 Java 关键词预筛结果，通过三层容错（M5.1 ResilientCaller）调 LLM，
输出最终风险等级（low / medium / high / critical）+ 审批路由建议。

三层容错策略：
  - 层 1（Prompt 约束）：ResilientCaller 注入 JSON Schema + few-shot
  - 层 2（Pydantic 校验 + 重试）：LLM 返回解析失败时反馈重试（默认 2 次）
  - 层 3（NEEDS_HUMAN 兜底）：重试耗尽时回退使用预筛风险等级，不阻塞 Java 流程

调用示例：
    service = RiskJudgeService(resilient_caller=caller)
    response = await service.judge(request)

@author FinanceRPA
"""

import json
import logging
from typing import Any

from app.llm.resilient_caller import NeedsHumanError, ResilientCaller

from .schemas import RiskJudgeOutput, RiskJudgeRequest, RiskJudgeResponse

logger = logging.getLogger(__name__)


# region LLM Prompt 常量

RISK_JUDGE_SYSTEM_PROMPT = """\
You are a financial RPA risk assessment expert. Your job is to evaluate the risk \
level of a task based on its goal, parameters, and pre-screening results (keyword \
hits + amount detection).

You must output a JSON object with exactly three fields:
- "final_risk_level": one of "low", "medium", "high", "critical"
- "reasoning": a brief explanation of why you chose this risk level
- "approval_route": one of "auto", "department", "compliance"

Risk level guidelines:
- "low": Routine operations with no sensitive data or large amounts. Auto-approve.
- "medium": Operations involving moderate amounts or non-critical keywords. \
Auto-approve but log for audit.
- "high": Operations involving large amounts, high-risk operations (transfers, \
redemptions, trades), or sensitive data. Requires department manager approval.
- "critical": Operations combining high-risk actions WITH sensitive data (e.g., \
transfer + bank card number + password). Requires compliance department approval.

Approval route guidelines:
- "auto": low or medium risk, proceed without human approval
- "department": high risk, requires department manager approval
- "compliance": critical risk, requires compliance/audit department approval

Output ONLY a valid JSON object. No markdown, no code blocks, no extra text.

Example:
{
  "final_risk_level": "high",
  "reasoning": "Task involves transfer operation with amount exceeding 50,000 CNY threshold",
  "approval_route": "department"
}
"""

# endregion


# region 审批路由默认映射

_ROUTE_BY_RISK_LEVEL = {
    "low": "auto",
    "medium": "auto",
    "high": "department",
    "critical": "compliance",
}

# endregion


class RiskJudgeService:
    """LLM 风险二次判断服务。

    职责：
    - 构造风险判断 prompt（goal + params + 预筛结果）
    - 调 ResilientCaller 三层容错调 LLM
    - 解析 LLM 输出为 RiskJudgeResponse
    - LLM 失败时回退使用预筛风险等级（不阻塞 Java 流程）
    """

    def __init__(
        self,
        resilient_caller: ResilientCaller,
    ):
        """
        @param resilient_caller: ResilientCaller 实例（M5.1 三层容错）
        """
        self.resilient_caller = resilient_caller

    async def judge(self, request: RiskJudgeRequest) -> RiskJudgeResponse:
        """执行 LLM 风险二次判断。

        流程：
          1. 构造 prompt（system prompt + goal + params + 预筛结果）
          2. 调 ResilientCaller.call()（层 1 Schema 约束 + 层 2 Pydantic 校验重试）
          3. 解析 LLM 输出 → RiskJudgeResponse
          4. LLM 失败（NeedsHumanError / 其他异常）→ 回退使用预筛风险等级

        @param request: 风险判断请求（含 goal / params / 预筛结果）
        @return: 风险判断响应（含 final_risk_level / reasoning / approval_route）
        """
        task_id = request.task_id or "unknown"

        # 1. 构造 prompt
        prompt = self._build_prompt(request)

        # 2. 调 ResilientCaller（三层容错）
        try:
            logger.info(
                "RiskJudgeService: 调用 LLM 风险判断 [task=%s, industry=%s, preScreen=%s, hitKeywords=%d]",
                task_id, request.industry, request.pre_screen_risk_level,
                len(request.hit_keywords),
            )

            output: RiskJudgeOutput = await self.resilient_caller.call(
                prompt=prompt,
                output_model=RiskJudgeOutput,
                task_id=task_id,
                org_id=request.org_id,
                context_name="risk_judge",
            )

            # 3. 解析 LLM 输出 → Response
            response = RiskJudgeResponse(
                task_id=request.task_id,
                final_risk_level=output.final_risk_level,
                reasoning=output.reasoning,
                approval_route=output.approval_route,
                message="LLM 风险判断完成",
            )

            # 校验 LLM 输出的风险等级合法
            response = self._validate_response(response, request)

            logger.info(
                "RiskJudgeService: LLM 风险判断完成 [task=%s, final=%s, route=%s]",
                task_id, response.final_risk_level, response.approval_route,
            )
            return response

        except NeedsHumanError as e:
            # 层 3：重试耗尽 → 回退使用预筛风险等级（不阻塞 Java 流程）
            logger.warning(
                "RiskJudgeService: LLM 重试耗尽，回退使用预筛风险等级 [task=%s, preScreen=%s, error=%s]",
                task_id, request.pre_screen_risk_level, str(e)[:200],
            )
            return self._fallback_response(request, f"LLM 判断失败，使用预筛结果: {e}")

        except Exception as e:
            # 网络异常等非校验错误 → 回退使用预筛风险等级
            logger.warning(
                "RiskJudgeService: LLM 调用异常，回退使用预筛风险等级 [task=%s, preScreen=%s, error=%s]",
                task_id, request.pre_screen_risk_level, str(e)[:200],
            )
            return self._fallback_response(request, f"LLM 调用异常，使用预筛结果: {e}")

    def _build_prompt(self, request: RiskJudgeRequest) -> str:
        """构造风险判断 prompt。

        @param request: 风险判断请求
        @return: 完整 prompt（system prompt + 用户输入）
        """
        # 格式化命中关键词
        keywords_text = self._format_hit_keywords(request.hit_keywords)

        # 格式化金额匹配
        amounts_text = self._format_amounts(request.amount_matches, request.max_amount)

        # 格式化参数（排除 steps 字段：steps 是 Skyvern 内部执行 JSON，
        # 含 field_mapping 的 key 如"身份证号"，LLM 会误读为实际处理的敏感数据 → 误判 critical）
        filtered_params = (
            {k: v for k, v in request.params.items() if k != "steps"}
            if request.params else None
        )
        params_text = json.dumps(filtered_params, ensure_ascii=False, indent=2) if filtered_params else "无"

        return (
            f"{RISK_JUDGE_SYSTEM_PROMPT}\n\n"
            f"## Task Goal\n{request.goal}\n\n"
            f"## Industry\n{request.industry or '未指定'}\n\n"
            f"## Task Parameters\n{params_text}\n\n"
            f"## Pre-Screen Risk Level\n{request.pre_screen_risk_level}\n\n"
            f"## Hit Keywords (from pre-screen)\n{keywords_text}\n\n"
            f"## Amount Detection (from pre-screen)\n{amounts_text}\n"
        )

    def _format_hit_keywords(self, hit_keywords: list[Any]) -> str:
        """格式化命中关键词列表为可读文本。

        @param hit_keywords: 命中关键词列表（HitKeywordItem 或 dict）
        @return: 格式化的文本
        """
        if not hit_keywords:
            return "无命中关键词"

        lines = []
        for i, kw in enumerate(hit_keywords, 1):
            if hasattr(kw, "keyword"):
                # HitKeywordItem 实例
                keyword = kw.keyword
                category = kw.category
                risk_type = kw.risk_type
                description = kw.description or ""
            else:
                # dict
                keyword = kw.get("keyword", "")
                category = kw.get("category", "")
                risk_type = kw.get("riskType") or kw.get("risk_type", "")
                description = kw.get("description", "") or ""
            lines.append(
                f"  {i}. \"{keyword}\" [category={category}, riskType={risk_type}] {description}",
            )
        return "\n".join(lines)

    def _format_amounts(self, amount_matches: list[Any], max_amount: float | None) -> str:
        """格式化金额匹配列表为可读文本。

        @param amount_matches: 金额匹配列表（AmountMatchItem 或 dict）
        @param max_amount: 最大金额
        @return: 格式化的文本
        """
        if not amount_matches:
            return "未检测到金额"

        lines = []
        for i, amt in enumerate(amount_matches, 1):
            if hasattr(amt, "raw_text"):
                raw = amt.raw_text
                amount = amt.amount
                currency = amt.currency
            else:
                raw = amt.get("rawText") or amt.get("raw_text", "")
                amount = amt.get("amount", 0)
                currency = amt.get("currency", "CNY")
            lines.append(f"  {i}. {raw} → {amount:,.2f} {currency}")

        if max_amount is not None and max_amount > 0:
            lines.append(f"  最大金额: {max_amount:,.2f} 元")

        return "\n".join(lines)

    def _validate_response(
        self,
        response: RiskJudgeResponse,
        request: RiskJudgeRequest,
    ) -> RiskJudgeResponse:
        """校验 LLM 输出的风险等级与审批路由合法。

        非法值回退使用预筛风险等级 + 默认路由。

        @param response: LLM 输出的响应
        @param request: 原始请求（用于回退）
        @return: 校验后的响应
        """
        valid_levels = {"low", "medium", "high", "critical"}
        valid_routes = {"auto", "department", "compliance"}

        # 校验风险等级
        if response.final_risk_level not in valid_levels:
            logger.warning(
                "RiskJudgeService: LLM 输出非法风险等级 '%s'，回退使用预筛结果 '%s'",
                response.final_risk_level, request.pre_screen_risk_level,
            )
            response.final_risk_level = request.pre_screen_risk_level

        # 校验审批路由
        if response.approval_route not in valid_routes:
            response.approval_route = _ROUTE_BY_RISK_LEVEL.get(
                response.final_risk_level, "auto",
            )

        return response

    def _fallback_response(
        self,
        request: RiskJudgeRequest,
        message: str,
    ) -> RiskJudgeResponse:
        """构造回退响应（使用预筛风险等级）。

        @param request: 原始请求
        @param message: 回退原因消息
        @return: 回退响应
        """
        risk_level = request.pre_screen_risk_level
        route = _ROUTE_BY_RISK_LEVEL.get(risk_level, "auto")

        return RiskJudgeResponse(
            task_id=request.task_id,
            final_risk_level=risk_level,
            reasoning=f"回退使用预筛风险等级: {risk_level}",
            approval_route=route,
            message=message,
        )

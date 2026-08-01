"""风险判断 API（M6.2）。

提供 POST /api/v1/ai/risk/judge 端点，供 Java 后端关键词预筛命中后调用。
通过三层容错（M5.1 ResilientCaller）调 LLM 输出最终风险等级。

@endpoint POST /api/v1/ai/risk/judge —— LLM 风险二次判断

@author FinanceRPA
"""

import logging

from fastapi import APIRouter

from app.approval.risk_judge import RiskJudgeService
from app.approval.schemas import RiskJudgeRequest, RiskJudgeResponse
from app.clients.java_backend import JavaBackendClient
from app.config import get_settings
from app.llm.resilient_caller import ResilientCaller

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/ai/risk", tags=["risk"])


def _create_llm_callable():
    """构造 litellm 异步调用函数（prompt -> str）。

    从配置读取 LLM provider / api_key / base_url / model，
    返回 async callable 供 ResilientCaller 使用。

    @return: 异步函数 async def(prompt: str) -> str
    """

    settings = get_settings()

    # 延迟导入 litellm（避免未安装时影响其他模块启动）
    try:
        import litellm

        # 关闭 litellm 不必要的日志
        litellm.verbose = False
        litellm.drop_params = True
    except ImportError:
        logger.error("litellm 未安装，LLM 风险判断将不可用")
        raise

    model = settings.llm_model or "gpt-4o-mini"
    api_key = settings.llm_api_key or None
    base_url = settings.llm_base_url or None

    async def _llm_callable(prompt: str) -> str:
        """调 litellm acompletion，返回文本内容。

        @param prompt: 完整 prompt（含 system prompt + 用户输入）
        @return: LLM 返回的文本
        """
        messages = [{"role": "user", "content": prompt}]
        kwargs = {
            "model": model,
            "messages": messages,
            "temperature": 0.1,
            "max_tokens": 1024,
        }
        if api_key:
            kwargs["api_key"] = api_key
        if base_url:
            kwargs["api_base"] = base_url

        response = await litellm.acompletion(**kwargs)
        return response.choices[0].message.content or ""

    return _llm_callable


def _create_risk_judge_service() -> RiskJudgeService:
    """构造 RiskJudgeService 实例（含 ResilientCaller + JavaBackendClient）。

    @return: RiskJudgeService 实例
    """
    settings = get_settings()

    # 构造 llm_callable
    llm_callable = _create_llm_callable()

    # 构造 JavaBackendClient（上报 LLM 调用记录 + NEEDS_HUMAN 事件）
    java_client = JavaBackendClient()

    # 构造 ResilientCaller
    resilient_caller = ResilientCaller(
        llm_callable=llm_callable,
        max_retries=2,
        java_client=java_client,
        model_name=settings.llm_model or "gpt-4o-mini",
    )

    return RiskJudgeService(resilient_caller=resilient_caller)


@router.post("/judge", response_model=RiskJudgeResponse)
async def judge_risk(request: RiskJudgeRequest) -> RiskJudgeResponse:
    """LLM 风险二次判断（M6.2）。

    Java 关键词预筛命中中高风险后调用此接口，由 Python 走三层容错调 LLM，
    输入任务目标 + 参数 + 预筛结果 → 输出 final_risk_level（low / medium / high / critical）。

    流程：
      1. 构造 RiskJudgeService（ResilientCaller + JavaBackendClient）
      2. 调 service.judge(request)
      3. 返回 RiskJudgeResponse

    容错策略：
      - LLM 调用失败（重试耗尽 / 网络异常）→ 回退使用预筛风险等级，不阻塞 Java 流程
      - LLM 输出非法值 → 回退使用预筛风险等级 + 默认审批路由
    """
    logger.info(
        "API 风险判断: task_id=%s, industry=%s, preScreenRiskLevel=%s, hitKeywords=%d, maxAmount=%s",
        request.task_id, request.industry, request.pre_screen_risk_level,
        len(request.hit_keywords), request.max_amount,
    )

    service = _create_risk_judge_service()
    response = await service.judge(request)

    logger.info(
        "API 风险判断完成: task_id=%s, finalRiskLevel=%s, approvalRoute=%s",
        response.task_id, response.final_risk_level, response.approval_route,
    )

    return response

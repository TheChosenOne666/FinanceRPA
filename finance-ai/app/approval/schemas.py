"""风险判断 Pydantic 模型。

定义 Java → Python 的请求/响应模型和 LLM 输出模型。
字段命名使用 snake_case，JSON 收发统一驼峰（camelCase），与 Java 侧对齐。

@author FinanceRPA
"""

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

# 全局驼峰命名配置：内部 snake_case，JSON IO 驼峰
_CAMEL_CONFIG = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class HitKeywordItem(BaseModel):
    """命中的关键词项（Java 预筛结果）。"""

    model_config = _CAMEL_CONFIG

    keyword: str = Field(description="关键词文本")
    industry: str = Field(description="所属行业")
    category: str = Field(description="分类：high_risk_operation / sensitive_data / large_amount")
    risk_type: str = Field(description="风险类型：high / medium / low")
    description: str | None = Field(default=None, description="描述说明")


class AmountMatchItem(BaseModel):
    """命中的金额项（Java 预筛结果）。"""

    model_config = _CAMEL_CONFIG

    raw_text: str = Field(description="匹配到的原始文本")
    amount: float = Field(description="解析后的金额（元）")
    currency: str = Field(default="CNY", description="币种：CNY / USD")


class RiskJudgeRequest(BaseModel):
    """风险二次判断请求（Java → Python）。

    Java 关键词预筛命中中高风险后调用此接口，由 Python 走三层容错调 LLM，
    输入任务目标 + 参数 + 预筛结果 → 输出 final_risk_level。
    """

    model_config = _CAMEL_CONFIG

    task_id: str | None = Field(default=None, description="任务 ID")
    org_id: str | None = Field(default=None, description="组织 ID")
    goal: str = Field(description="任务目标")
    params: dict | None = Field(default=None, description="任务参数")
    industry: str | None = Field(default=None, description="所属行业：banking / insurance / securities")
    pre_screen_risk_level: str = Field(description="预筛判定的风险等级：low / medium / high / critical")
    hit_keywords: list[HitKeywordItem] = Field(default_factory=list, description="命中的关键词列表")
    amount_matches: list[AmountMatchItem] = Field(default_factory=list, description="命中的金额列表")
    max_amount: float | None = Field(default=None, description="最大金额（元）")


class RiskJudgeResponse(BaseModel):
    """风险二次判断响应（Python → Java）。

    LLM 风险判断结果，输出 final_risk_level：low / medium / high / critical。
    """

    model_config = _CAMEL_CONFIG

    task_id: str | None = Field(default=None, description="任务 ID")
    final_risk_level: str = Field(description="最终风险等级：low / medium / high / critical")
    reasoning: str = Field(default="", description="LLM 判断理由")
    approval_route: str = Field(
        default="auto",
        description="建议审批路由：auto（自动通过）/ department（部门审批）/ compliance（合规审计部审批）",
    )
    message: str = Field(default="", description="响应消息")


class RiskJudgeOutput(BaseModel):
    """LLM 产出的风险判断 JSON 结构（ResilientCaller 用）。

    此模型注入 ResilientCaller 的 JSON Schema 约束，要求 LLM 仅输出合法 JSON。
    """

    final_risk_level: str = Field(
        description="最终风险等级，必须是以下之一：low / medium / high / critical",
    )
    reasoning: str = Field(description="判断理由，简明扼要说明为什么选择该风险等级")
    approval_route: str = Field(
        description="建议审批路由：auto（低风险自动通过）/ department（中高风险需部门审批）/ compliance（极高风险需合规审计部审批）",
    )

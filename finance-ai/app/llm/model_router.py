"""LLM 模型路由执行（M5.3）。

根据页面复杂度评分，动态路由到不同档位的 LLM 模型，优化成本与性能。

页面复杂度评分维度：
  - DOM 节点数（权重 40%）：页面结构复杂度
  - 表单字段数（权重 30%）：交互复杂度
  - 动态元素数（权重 20%）：动态行为复杂度
  - 截图熵（权重 10%）：视觉复杂度

路由规则（可从 Java 配置更新）：
  - score < 30 → 轻量模型（gpt-4o-mini）
  - 30 ≤ score < 70 → 标准模型（gpt-4o）
  - score ≥ 70 → 重型模型（gpt-4o-2024-08-06）

@author FinanceRPA
"""

import logging
import re
import time
from typing import Any

from pydantic import BaseModel, Field

from app.clients.java_backend import JavaBackendClient

logger = logging.getLogger(__name__)

# 默认路由配置
_DEFAULT_LIGHT_MODEL = "gpt-4o-mini"
_DEFAULT_STANDARD_MODEL = "gpt-4o"
_DEFAULT_HEAVY_MODEL = "gpt-4o-2024-08-06"
_DEFAULT_LIGHT_THRESHOLD = 30.0
_DEFAULT_HEAVY_THRESHOLD = 70.0

# 配置缓存 TTL（秒）
_CONFIG_CACHE_TTL = 300  # 5 分钟

# 评分权重
_WEIGHT_DOM_NODES = 0.4
_WEIGHT_FORM_FIELDS = 0.3
_WEIGHT_DYNAMIC_ELEMENTS = 0.2
_WEIGHT_SCREENSHOT_ENTROPY = 0.1

# 评分归一化常数
_DOM_NODES_DIVISOR = 5.0  # 每 5 个节点 1 分，200 节点 = 40 分
_FORM_FIELDS_MULTIPLIER = 2.0  # 每个表单字段 2 分，15 字段 = 30 分
_DYNAMIC_ELEMENTS_CAP = 20.0  # 动态元素最高 20 分
_ENTROPY_MULTIPLIER = 10.0  # 熵 0-1 → 0-10 分


class ComplexityScore(BaseModel):
    """页面复杂度评分结果。

    @attribute dom_nodes: DOM 节点总数
    @attribute form_fields: 表单字段数（input / select / textarea）
    @attribute dynamic_elements: 动态元素数（button / a / onclick 等）
    @attribute screenshot_entropy: 截图熵（0-1，默认 0 表示未计算）
    @attribute total_score: 综合评分（0-100）
    @attribute level: 复杂度档位（"light" / "standard" / "heavy"）
    """

    dom_nodes: int = 0
    form_fields: int = 0
    dynamic_elements: int = 0
    screenshot_entropy: float = 0.0
    total_score: float = 0.0
    level: str = "light"


class RoutingConfig(BaseModel):
    """模型路由配置（可从 Java 动态更新）。

    @attribute light_model: 轻量档位模型名
    @attribute standard_model: 标准档位模型名
    @attribute heavy_model: 重型档位模型名
    @attribute light_threshold: 轻量档位上限阈值（< 此值用轻量模型）
    @attribute heavy_threshold: 重型档位下限阈值（≥ 此值用重型模型）
    """

    light_model: str = _DEFAULT_LIGHT_MODEL
    standard_model: str = _DEFAULT_STANDARD_MODEL
    heavy_model: str = _DEFAULT_HEAVY_MODEL
    light_threshold: float = _DEFAULT_LIGHT_THRESHOLD
    heavy_threshold: float = _DEFAULT_HEAVY_THRESHOLD


class ModelRouter:
    """LLM 模型路由器。

    职责：
    - 页面复杂度评分：DOM 节点数 / 表单字段数 / 动态元素数 / 截图熵
    - 模型路由：根据评分选择轻量 / 标准 / 重型模型
    - 配置热更新：从 Java 读取路由策略配置（带 5 分钟缓存）

    使用示例：
        router = ModelRouter(java_client=java_client)
        score = router.score_complexity(dom_structure="<html>...</html>")
        model = router.get_model(score)
        # model = "gpt-4o-mini"
    """

    def __init__(
        self,
        java_client: JavaBackendClient | None = None,
        config: RoutingConfig | None = None,
    ):
        """
        @param java_client: Java 后端客户端（读取路由配置，可选）
        @param config: 静态路由配置（不传则使用默认值或从 Java 读取）
        """
        self.java_client = java_client
        self._config = config or RoutingConfig()
        self._config_fetched_at: float = 0  # 上次从 Java 拉取配置的时间戳

    def score_complexity(
        self,
        dom_structure: str,
        screenshot_entropy: float = 0.0,
    ) -> ComplexityScore:
        """计算页面复杂度评分。

        @param dom_structure: 页面 DOM 结构字符串（HTML）
        @param screenshot_entropy: 截图熵（0-1，默认 0 表示未计算）
        @return: ComplexityScore 评分结果
        """
        dom_nodes, form_fields, dynamic_elements = self._parse_dom_stats(dom_structure)

        # 各维度评分
        dom_score = min(dom_nodes / _DOM_NODES_DIVISOR, 100 * _WEIGHT_DOM_NODES)
        form_score = min(form_fields * _FORM_FIELDS_MULTIPLIER, 100 * _WEIGHT_FORM_FIELDS)
        dynamic_score = min(dynamic_elements, _DYNAMIC_ELEMENTS_CAP)
        entropy_score = max(0.0, min(screenshot_entropy, 1.0)) * _ENTROPY_MULTIPLIER

        total = dom_score + form_score + dynamic_score + entropy_score

        # 档位判定
        if total < self._config.light_threshold:
            level = "light"
        elif total < self._config.heavy_threshold:
            level = "standard"
        else:
            level = "heavy"

        return ComplexityScore(
            dom_nodes=dom_nodes,
            form_fields=form_fields,
            dynamic_elements=dynamic_elements,
            screenshot_entropy=screenshot_entropy,
            total_score=round(total, 2),
            level=level,
        )

    def get_model(self, score: ComplexityScore) -> str:
        """根据复杂度评分获取模型名。

        @param score: ComplexityScore 评分结果
        @return: 模型名（如 "gpt-4o-mini"）
        """
        if score.level == "light":
            return self._config.light_model
        elif score.level == "standard":
            return self._config.standard_model
        else:
            return self._config.heavy_model

    def route(
        self,
        dom_structure: str,
        screenshot_entropy: float = 0.0,
    ) -> tuple[ComplexityScore, str]:
        """一步路由：评分 + 选模型。

        @param dom_structure: 页面 DOM 结构字符串
        @param screenshot_entropy: 截图熵（0-1）
        @return: (评分结果, 模型名)
        """
        score = self.score_complexity(dom_structure, screenshot_entropy)
        model = self.get_model(score)
        logger.info(
            "ModelRouter: 路由结果 [score=%.2f, level=%s, model=%s, dom=%d, forms=%d, dynamic=%d]",
            score.total_score, score.level, model,
            score.dom_nodes, score.form_fields, score.dynamic_elements,
        )
        return score, model

    async def get_routing_config(self) -> RoutingConfig:
        """从 Java 读取路由策略配置（带缓存）。

        缓存 TTL 5 分钟，过期后重新拉取。Java 不可用时回退到本地配置。

        @return: 当前生效的 RoutingConfig
        """
        now = time.monotonic()
        if now - self._config_fetched_at < _CONFIG_CACHE_TTL:
            return self._config

        if not self.java_client:
            return self._config

        try:
            client = await self.java_client._get_client()
            resp = await client.get("/api/v1/ai/llm/routing-config")
            resp.raise_for_status()

            data = resp.json()
            # 兼容 BaseResponse 包装
            if isinstance(data, dict) and "data" in data:
                data = data["data"]

            self._config = RoutingConfig(**data)
            self._config_fetched_at = now
            logger.info(
                "ModelRouter: 路由配置已更新 [light=%s, standard=%s, heavy=%s, thresholds=%.0f/%.0f]",
                self._config.light_model, self._config.standard_model,
                self._config.heavy_model,
                self._config.light_threshold, self._config.heavy_threshold,
            )
        except Exception as e:
            logger.warning("ModelRouter: 读取路由配置失败，使用本地默认: %s", e)

        return self._config

    async def refresh_config(self) -> RoutingConfig:
        """强制刷新路由配置（忽略缓存）。"""
        self._config_fetched_at = 0
        return await self.get_routing_config()

    def _parse_dom_stats(self, dom: str) -> tuple[int, int, int]:
        """解析 DOM 统计节点数 / 表单字段数 / 动态元素数。

        @param dom: HTML 字符串
        @return: (dom_nodes, form_fields, dynamic_elements)
        """
        # DOM 节点数：统计所有 <tag 模式（不含闭合标签）
        dom_nodes = len(re.findall(r"<[a-zA-Z][^/>]*>", dom))

        # 表单字段数：input / select / textarea
        form_fields = len(re.findall(r"<(?:input|select|textarea)\b", dom, re.IGNORECASE))

        # 动态元素数：button / a / 带 onclick/onchange 的元素
        buttons = len(re.findall(r"<button\b", dom, re.IGNORECASE))
        links = len(re.findall(r"<a\b[^>]*href", dom, re.IGNORECASE))
        event_handlers = len(re.findall(r"\bon(?:click|change|submit|load|mouse)\w*\s*=", dom, re.IGNORECASE))
        dynamic_elements = buttons + links + event_handlers

        return dom_nodes, form_fields, dynamic_elements

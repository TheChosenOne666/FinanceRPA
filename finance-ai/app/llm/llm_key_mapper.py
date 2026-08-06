"""LLM 模型名 → Skyvern llm_key 映射（M9.7）。

ModelRouter 返回模型名（如 "gpt-4o-mini"），Skyvern 用 llm_key（如 "OPENAI_GPT4O_MINI"）。
本模块提供映射，让 ModelRouter 的路由结果能被 Skyvern 的 LLMAPIHandlerFactory 使用。

映射依据：skyvern/forge/sdk/api/llm/config_registry.py 中注册的 llm_key → model_name 对照表。

@author FinanceRPA
@from M9.7 Skyvern LLM 优化接入
"""

import logging

logger = logging.getLogger(__name__)

# 模型名 → Skyvern llm_key 映射表（与 config_registry.py 注册一致）
_MODEL_TO_LLM_KEY: dict[str, str] = {
    "gpt-4o-mini": "OPENAI_GPT4O_MINI",
    "gpt-4o": "OPENAI_GPT4O",
    "gpt-4o-2024-08-06": "OPENAI_GPT-4O-2024-08-06",
}

# 各 provider 按复杂度档位的 llm_key 路由表（同 provider 内选型，避免跨 provider 错误覆盖）
_PROVIDER_COMPLEXITY_ROUTING: dict[str, dict[str, str]] = {
    # OpenAI 系列：light → mini，standard → gpt-4o，heavy → gpt-4o-2024-08-06
    "OPENAI": {
        "light": "OPENAI_GPT4O_MINI",
        "standard": "OPENAI_GPT4O",
        "heavy": "OPENAI_GPT-4O-2024-08-06",
    },
    # Volcengine 系列：light → flash（更快更便宜），standard/heavy → 保持原始
    "VOLCENGINE": {
        "light": "VOLCENGINE_DOUBAO_SEED_1_6_FLASH",
    },
}


def map_model_to_llm_key(
    model_name: str,
    default_llm_key: str | None = None,
) -> str | None:
    """将 ModelRouter 返回的模型名映射为 Skyvern llm_key。

    @param model_name: ModelRouter 返回的模型名（如 "gpt-4o-mini"）
    @param default_llm_key: 映射失败时的默认 llm_key（通常是 task.llm_key）
    @return: Skyvern llm_key（如 "OPENAI_GPT4O_MINI"），映射失败返回 default_llm_key
    """
    mapped = _MODEL_TO_LLM_KEY.get(model_name)
    if mapped:
        logger.debug(
            "llm_key_mapper: 模型映射 [model=%s → llm_key=%s]",
            model_name, mapped,
        )
        return mapped

    logger.debug(
        "llm_key_mapper: 模型未找到映射，使用默认 [model=%s, default_llm_key=%s]",
        model_name, default_llm_key,
    )
    return default_llm_key


def route_by_complexity(
    level: str,
    original_llm_key: str,
) -> str:
    """根据复杂度档位和原始 llm_key 的 provider 路由到同 provider 的合适模型。

    确保 ModelRouter 的路由结果不会跨 provider 错误覆盖（如把 Volcengine 的 key
    替换成 OpenAI 的 key 导致 API Key 不匹配）。

    @param level: 复杂度档位（"light" / "standard" / "heavy"）
    @param original_llm_key: 原始 llm_key（如 "VOLCENGINE_DOUBAO_SEED_EVOLVING"）
    @return: 路由后的 llm_key（同 provider 内选型；未配置路由则返回原始 key）
    """
    # 按 provider 前缀匹配路由表
    for provider_prefix, routing in _PROVIDER_COMPLEXITY_ROUTING.items():
        if original_llm_key.startswith(provider_prefix):
            routed = routing.get(level)
            if routed and routed != original_llm_key:
                logger.info(
                    "llm_key_mapper: provider 感知路由 [provider=%s, level=%s, %s → %s]",
                    provider_prefix, level, original_llm_key, routed,
                )
                return routed
            # 该 provider 未配置此档位路由，保持原始
            return original_llm_key

    # 未知 provider：不路由，返回原始
    logger.debug(
        "llm_key_mapper: 未知 provider，不路由 [llm_key=%s, level=%s]",
        original_llm_key, level,
    )
    return original_llm_key

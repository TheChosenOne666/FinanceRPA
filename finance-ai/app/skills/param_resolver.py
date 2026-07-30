"""参数映射解析器。

将工作流模板中的 param_mapping 解析为实际参数值，复刻参考项目
enterprise/workflows/schemas.py 的 SkillStepDefinition.param_mapping 语法。

支持 5 种映射语法：

1. **字面量模式**：`=csv` → `"csv"`，`=500` → `500`（自动 JSON 解析）
2. **引用模式**：`bank_url` → `workflow_params["bank_url"]`（直接引用工作流参数名）
3. **嵌入引用**：`={"key": "${param_name}"}` → 解析 `${}` 内的工作流参数引用
4. **上下文引用**：`{{steps.0.data.filename}}` → 从前序步骤输出取值
5. **上下文嵌入**：`=prefix_{{steps.0.data.filename}}_suffix` → 字符串替换

@from enterprise/workflows/schemas.py (SkillStepDefinition.param_mapping)
@author FinanceRPA
"""

import json
import logging
import re
from typing import Any

logger = logging.getLogger(__name__)

# ${param_name} 嵌入引用正则（工作流参数）
_EMBEDDED_PARAM_PATTERN = re.compile(r"\$\{(\w+)\}")

# {{steps.N.data.key}} 上下文引用正则
_CONTEXT_REF_PATTERN = re.compile(r"\{\{steps\.(\d+)\.data\.(\w+)\}\}")


def _get_step_data(
    step_results: list[dict[str, Any]],
    step_index: int,
    data_key: str,
) -> Any:
    """从步骤结果列表中获取指定步骤的输出数据。

    @param step_results: 前序步骤的执行结果列表，每项含 {"data": dict, ...}
    @param step_index: 步骤索引（从 0 开始）
    @param data_key: 数据键名（SkillResult.data 中的 key）
    @return: 数据值；步骤或键不存在时返回 None
    """
    if step_index >= len(step_results):
        logger.warning(
            "参数映射：步骤 %d 不在 step_results 中（当前长度 %d）",
            step_index,
            len(step_results),
        )
        return None

    step = step_results[step_index]
    data = step.get("data") or {}
    if data_key not in data:
        logger.warning(
            "参数映射：步骤 %d 的 data 中不存在键 '%s'",
            step_index,
            data_key,
        )
        return None

    return data[data_key]


def _resolve_embedded_refs(
    text: str,
    workflow_params: dict[str, Any],
    step_results: list[dict[str, Any]],
) -> str:
    """解析字符串中的所有嵌入引用（${param} 和 {{steps.N.data.key}}）。

    @param text: 包含嵌入引用的原始字符串
    @param workflow_params: 工作流参数字典
    @param step_results: 前序步骤的执行结果列表
    @return: 所有引用替换后的字符串
    """
    # 1. 替换 ${param_name} → workflow_params[param_name]
    def _param_replacer(m: re.Match) -> str:
        param_name = m.group(1)
        val = workflow_params.get(param_name)
        if val is None:
            logger.warning("参数映射：工作流参数 '%s' 不存在", param_name)
            return m.group(0)  # 原样保留
        return str(val)

    text = _EMBEDDED_PARAM_PATTERN.sub(_param_replacer, text)

    # 2. 替换 {{steps.N.data.key}} → step_results[N]["data"][key]
    def _context_replacer(m: re.Match) -> str:
        step_idx = int(m.group(1))
        data_key = m.group(2)
        val = _get_step_data(step_results, step_idx, data_key)
        if val is None:
            return m.group(0)  # 原样保留
        return str(val)

    text = _CONTEXT_REF_PATTERN.sub(_context_replacer, text)

    return text


def resolve_param_value(
    mapping_value: str,
    workflow_params: dict[str, Any] | None = None,
    step_results: list[dict[str, Any]] | None = None,
) -> Any:
    """解析单个参数映射值。

    语法规则（按优先级匹配）：
    - `{{steps.N.data.key}}`：上下文引用，整个值就是引用 → 直接返回步骤输出数据
    - `=xxx`：字面量模式，`=` 后为字面量值（支持嵌入引用和 JSON 解析）
    - `param_name`：引用模式，返回 workflow_params[param_name]
    - 其他：无法解析，原样返回

    @param mapping_value: param_mapping 中的值字符串
    @param workflow_params: 工作流参数字典（可选）
    @param step_results: 前序步骤的执行结果列表（可选）
    @return: 解析后的参数值（类型取决于映射语法）
    """
    workflow_params = workflow_params or {}
    step_results = step_results or []

    # 1. 上下文引用：整个值是 {{steps.N.data.key}}
    full_context_match = _CONTEXT_REF_PATTERN.fullmatch(mapping_value)
    if full_context_match:
        step_idx = int(full_context_match.group(1))
        data_key = full_context_match.group(2)
        logger.info(
            "参数映射：上下文引用 steps.%d.data.%s",
            step_idx,
            data_key,
        )
        return _get_step_data(step_results, step_idx, data_key)

    # 2. 字面量模式：=xxx
    if mapping_value.startswith("="):
        literal = mapping_value[1:]

        # 检查是否有嵌入引用
        has_embedded = (
            _EMBEDDED_PARAM_PATTERN.search(literal) is not None
            or _CONTEXT_REF_PATTERN.search(literal) is not None
        )

        if has_embedded:
            # 先解析嵌入引用，再尝试 JSON 解析
            resolved = _resolve_embedded_refs(literal, workflow_params, step_results)
            try:
                return json.loads(resolved)
            except (json.JSONDecodeError, ValueError):
                return resolved

        # 无嵌入引用，尝试 JSON 解析（支持数字、布尔、对象等）
        try:
            return json.loads(literal)
        except (json.JSONDecodeError, ValueError):
            return literal

    # 3. 引用模式：直接引用工作流参数名
    if mapping_value in workflow_params:
        logger.info(
            "参数映射：引用工作流参数 '%s'",
            mapping_value,
        )
        return workflow_params[mapping_value]

    # 4. 无法解析，原样返回
    logger.warning(
        "参数映射：无法解析值 '%s'（不是字面量、引用或上下文引用），原样返回",
        mapping_value,
    )
    return mapping_value


def resolve_param_mapping(
    param_mapping: dict[str, str],
    workflow_params: dict[str, Any] | None = None,
    step_results: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """解析完整的参数映射字典。

    将 param_mapping（Skill 参数名 → 映射值字符串）解析为
    Skill 参数名 → 实际值。

    @param param_mapping: Skill 参数名 → 映射值字符串
    @param workflow_params: 工作流参数字典（可选）
    @param step_results: 前序步骤的执行结果列表（可选）
    @return: Skill 参数名 → 解析后的实际值
    """
    resolved = {}
    for key, value in param_mapping.items():
        resolved[key] = resolve_param_value(value, workflow_params, step_results)
    return resolved

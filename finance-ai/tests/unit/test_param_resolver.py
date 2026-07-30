"""参数映射解析器单元测试。

覆盖 5 种映射语法：
1. 字面量模式：=csv → "csv"
2. 引用模式：bank_url → workflow_params["bank_url"]
3. 嵌入引用：={"key": "${param}"} → 解析 ${} 内工作流参数
4. 上下文引用：{{steps.0.data.filename}} → 从前序步骤输出取值
5. 上下文嵌入：=prefix_{{steps.0.data.filename}}_suffix → 字符串替换
"""

from app.skills.param_resolver import (
    resolve_param_mapping,
    resolve_param_value,
)

# ---------------------------------------------------------------------------
# 字面量模式测试
# ---------------------------------------------------------------------------

def test_literal_string():
    """=csv 应解析为字符串 "csv"。"""
    assert resolve_param_value("=csv") == "csv"


def test_literal_integer():
    """=500 应解析为整数 500（JSON 解析）。"""
    assert resolve_param_value("=500") == 500


def test_literal_boolean():
    """=true 应解析为布尔 True（JSON 解析）。"""
    assert resolve_param_value("=true") is True


def test_literal_json_object():
    """={"a": 1} 应解析为字典 {"a": 1}。"""
    result = resolve_param_value('={"a": 1}')
    assert result == {"a": 1}


def test_literal_json_array():
    """=[1,2,3] 应解析为列表 [1, 2, 3]。"""
    result = resolve_param_value("=[1,2,3]")
    assert result == [1, 2, 3]


def test_literal_chinese_text():
    """=导出 应解析为中文字符串 "导出"。"""
    assert resolve_param_value("=导出") == "导出"


# ---------------------------------------------------------------------------
# 引用模式测试
# ---------------------------------------------------------------------------

def test_reference_simple():
    """bank_url 应引用 workflow_params["bank_url"]。"""
    workflow_params = {"bank_url": "https://bank.example.com", "username": "admin"}
    assert resolve_param_value("bank_url", workflow_params) == "https://bank.example.com"


def test_reference_integer_value():
    """引用的值可以是整数。"""
    workflow_params = {"max_rows": 500}
    assert resolve_param_value("max_rows", workflow_params) == 500


def test_reference_not_found():
    """引用不存在的参数名时，原样返回字符串。"""
    assert resolve_param_value("nonexistent", {}) == "nonexistent"


def test_reference_none_workflow_params():
    """workflow_params 为 None 时，引用模式原样返回。"""
    assert resolve_param_value("bank_url", None) == "bank_url"


# ---------------------------------------------------------------------------
# 嵌入引用测试（${param_name}）
# ---------------------------------------------------------------------------

def test_embedded_reference_in_json():
    """={"key": "${param}"} 应解析 ${} 内引用，返回字典。"""
    workflow_params = {"account_number": "6228480012345678"}
    result = resolve_param_value(
        '={"account": "${account_number}"}',
        workflow_params,
    )
    assert result == {"account": "6228480012345678"}


def test_embedded_reference_multiple():
    """一个字面量中嵌入多个 ${} 引用。"""
    workflow_params = {"start_date": "2026-01-01", "end_date": "2026-06-30"}
    result = resolve_param_value(
        '={"start": "${start_date}", "end": "${end_date}"}',
        workflow_params,
    )
    assert result == {"start": "2026-01-01", "end": "2026-06-30"}


def test_embedded_reference_in_plain_string():
    """嵌入引用在非 JSON 字面量中。"""
    workflow_params = {"name": "张三"}
    result = resolve_param_value("=Hello ${name}", workflow_params)
    assert result == "Hello 张三"


def test_embedded_reference_not_found():
    """嵌入引用的参数不存在时，保留原始 ${} 占位符。"""
    result = resolve_param_value("=Hello ${missing}", {})
    assert result == "Hello ${missing}"


# ---------------------------------------------------------------------------
# 上下文引用测试（{{steps.N.data.key}}）
# ---------------------------------------------------------------------------

def test_context_reference_full():
    """整个值是 {{steps.0.data.filename}} → 返回步骤 0 的 data.filename。"""
    step_results = [
        {"data": {"filename": "report.csv"}},
    ]
    result = resolve_param_value(
        "{{steps.0.data.filename}}",
        step_results=step_results,
    )
    assert result == "report.csv"


def test_context_reference_second_step():
    """引用步骤 1 的输出。"""
    step_results = [
        {"data": {"filename": "first.csv"}},
        {"data": {"filename": "second.csv", "rows": 100}},
    ]
    result = resolve_param_value(
        "{{steps.1.data.rows}}",
        step_results=step_results,
    )
    assert result == 100


def test_context_reference_step_not_found():
    """引用不存在的步骤索引时返回 None。"""
    step_results = [{"data": {"filename": "a.csv"}}]
    result = resolve_param_value(
        "{{steps.5.data.filename}}",
        step_results=step_results,
    )
    assert result is None


def test_context_reference_key_not_found():
    """引用步骤中不存在的 data 键时返回 None。"""
    step_results = [{"data": {"filename": "a.csv"}}]
    result = resolve_param_value(
        "{{steps.0.data.nonexistent}}",
        step_results=step_results,
    )
    assert result is None


def test_context_reference_empty_step_results():
    """step_results 为空时返回 None。"""
    result = resolve_param_value("{{steps.0.data.filename}}")
    assert result is None


# ---------------------------------------------------------------------------
# 上下文嵌入测试（字面量中嵌入 {{steps.N.data.key}}）
# ---------------------------------------------------------------------------

def test_context_embedded_in_literal():
    """=prefix_{{steps.0.data.filename}}_suffix 应替换上下文引用。"""
    step_results = [{"data": {"filename": "data.csv"}}]
    result = resolve_param_value(
        "=file_{{steps.0.data.filename}}.bak",
        step_results=step_results,
    )
    assert result == "file_data.csv.bak"


def test_context_embedded_with_param_ref():
    """字面量中同时嵌入 ${param} 和 {{steps.N.data.key}}。"""
    workflow_params = {"prefix": "export"}
    step_results = [{"data": {"filename": "data.csv"}}]
    result = resolve_param_value(
        '={"name": "${prefix}_{{steps.0.data.filename}}"}',
        workflow_params,
        step_results,
    )
    assert result == {"name": "export_data.csv"}


# ---------------------------------------------------------------------------
# resolve_param_mapping 批量解析测试
# ---------------------------------------------------------------------------

def test_resolve_param_mapping_mixed():
    """批量解析混合映射。"""
    workflow_params = {
        "bank_url": "https://bank.example.com",
        "username": "admin",
        "password": "secret",
        "max_rows": 500,
    }
    param_mapping = {
        "url": "bank_url",                    # 引用模式
        "username": "username",               # 引用模式
        "password": "password",               # 引用模式
        "output_format": "=csv",              # 字面量模式
        "max_rows": "max_rows",               # 引用模式（整数）
    }

    result = resolve_param_mapping(param_mapping, workflow_params)

    assert result["url"] == "https://bank.example.com"
    assert result["username"] == "admin"
    assert result["password"] == "secret"
    assert result["output_format"] == "csv"
    assert result["max_rows"] == 500


def test_resolve_param_mapping_with_context():
    """批量解析含上下文引用的映射。"""
    workflow_params = {"prefix": "export"}
    step_results = [{"data": {"filename": "data.csv"}}]
    param_mapping = {
        "output_format": "=json",
        "source_file": "{{steps.0.data.filename}}",
        "label": "=${prefix}_{{steps.0.data.filename}}",
    }

    result = resolve_param_mapping(param_mapping, workflow_params, step_results)

    assert result["output_format"] == "json"
    assert result["source_file"] == "data.csv"
    assert result["label"] == "export_data.csv"


def test_resolve_param_mapping_empty():
    """空映射返回空字典。"""
    assert resolve_param_mapping({}) == {}


def test_resolve_param_mapping_none_workflow_params():
    """workflow_params 为 None 时仍可解析字面量和上下文引用。"""
    step_results = [{"data": {"filename": "a.csv"}}]
    param_mapping = {
        "format": "=csv",
        "source": "{{steps.0.data.filename}}",
    }

    result = resolve_param_mapping(param_mapping, None, step_results)

    assert result["format"] == "csv"
    assert result["source"] == "a.csv"

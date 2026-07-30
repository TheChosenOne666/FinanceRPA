"""Skill 元数据 API 与 list_skills() 增强字段单元测试。

M3.3：验证 list_skills() 返回完整元数据（含 category / params_schema / max_retries），
供 Java 后端注册 Skill 时校验存在性与前端动态生成参数表单。

@author FinanceRPA
"""

import app.skills  # noqa: F401 — 导入触发 7 个 Skill 自动注册
from app.skills import list_skills

# 7 个内置 Skill 的期望元数据（name → category）
EXPECTED_SKILLS = {
    "login": "auth",
    "session_keep_alive": "auth",
    "form_fill": "interaction",
    "search_and_select": "interaction",
    "pagination": "interaction",
    "table_extract": "extraction",
    "file_download": "extraction",
}


def test_list_skills_returns_all_builtins():
    """list_skills() 应包含全部 7 个内置 Skill。"""
    skills = list_skills()
    names = {s["name"] for s in skills}
    missing = set(EXPECTED_SKILLS.keys()) - names
    assert not missing, f"缺少内置 Skill: {missing}"


def test_list_skills_count():
    """已注册 Skill 数量应为 7（内置）+ 测试注册的（dummy/fail）。"""
    skills = list_skills()
    # test_skills.py 注册了 dummy_skill 和 fail_skill，此处至少包含 7 个内置
    assert len(skills) >= 7


def test_list_skills_fields_complete():
    """每条 Skill 元数据应包含全部 6 个字段。"""
    required_fields = {
        "name", "description", "category",
        "error_strategy", "max_retries", "params_schema",
    }
    skills = list_skills()
    for s in skills:
        missing = required_fields - set(s.keys())
        assert not missing, f"Skill {s.get('name')} 缺少字段: {missing}"


def test_builtin_skills_category_correct():
    """7 个内置 Skill 的 category 应与期望一致。"""
    skills = {s["name"]: s for s in list_skills()}
    for name, expected_category in EXPECTED_SKILLS.items():
        assert name in skills, f"缺少内置 Skill: {name}"
        assert skills[name]["category"] == expected_category, (
            f"{name} category 应为 {expected_category}，实际为 {skills[name]['category']}"
        )


def test_builtin_skills_params_schema_valid():
    """每个内置 Skill 的 params_schema 应是含 properties 的 JSON Schema dict。"""
    skills = {s["name"]: s for s in list_skills()}
    for name in EXPECTED_SKILLS:
        schema = skills[name]["params_schema"]
        assert isinstance(schema, dict), f"{name} params_schema 应为 dict"
        # Pydantic model_json_schema() 一定含 type 和 properties
        assert schema.get("type") == "object", f"{name} schema type 应为 object"
        assert "properties" in schema, f"{name} schema 应含 properties"


def test_builtin_skills_max_retries_correct():
    """验证关键 Skill 的 max_retries 值与 Python 声明一致。"""
    skills = {s["name"]: s for s in list_skills()}
    assert skills["login"]["max_retries"] == 3
    assert skills["pagination"]["max_retries"] == 1
    assert skills["form_fill"]["max_retries"] == 2


def test_builtin_skills_error_strategy_correct():
    """验证关键 Skill 的 error_strategy 值与 Python 声明一致。"""
    skills = {s["name"]: s for s in list_skills()}
    assert skills["login"]["error_strategy"] == "abort"
    assert skills["pagination"]["error_strategy"] == "skip"
    assert skills["form_fill"]["error_strategy"] == "retry"

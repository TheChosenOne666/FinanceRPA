"""Skill 系统入口。

导入所有 Skill 模块触发自动注册到 SKILL_REGISTRY。
7 个 Skill：
- auth_skills.py: LoginSkill, SessionKeepAliveSkill
- interaction_skills.py: FormFillSkill, SearchAndSelectSkill, PaginationSkill
- extraction_skills.py: TableExtractSkill, FileDownloadSkill

@author FinanceRPA
"""

# 导入三个 Skill 模块触发自动注册（@register_skill 装饰器生效）
from . import (
    auth_skills,  # noqa: F401
    extraction_skills,  # noqa: F401
    interaction_skills,  # noqa: F401
)

# 重导出具体 Skill 类与参数模型，方便上层直接 import
from .auth_skills import (
    LoginParams,
    LoginSkill,
    SessionKeepAliveParams,
    SessionKeepAliveSkill,
)
from .base import (
    SKILL_REGISTRY,
    BaseSkill,
    ErrorStrategy,
    SkillResult,
    SkillStatus,
    get_skill,
    list_skills,
    register_skill,
)
from .executor import (
    PipelineResult,
    SkillStep,
    execute_pipeline,
)
from .extraction_skills import (
    FileDownloadParams,
    FileDownloadSkill,
    TableExtractParams,
    TableExtractSkill,
)
from .interaction_skills import (
    FormFillParams,
    FormFillSkill,
    PaginationParams,
    PaginationSkill,
    SearchAndSelectParams,
    SearchAndSelectSkill,
)
from .param_resolver import resolve_param_mapping, resolve_param_value

__all__ = [
    # base
    "BaseSkill",
    "ErrorStrategy",
    "SkillResult",
    "SkillStatus",
    "SKILL_REGISTRY",
    "register_skill",
    "get_skill",
    "list_skills",
    # executor
    "SkillStep",
    "PipelineResult",
    "execute_pipeline",
    # auth skills
    "LoginParams",
    "LoginSkill",
    "SessionKeepAliveParams",
    "SessionKeepAliveSkill",
    # interaction skills
    "FormFillParams",
    "FormFillSkill",
    "SearchAndSelectParams",
    "SearchAndSelectSkill",
    "PaginationParams",
    "PaginationSkill",
    # extraction skills
    "TableExtractParams",
    "TableExtractSkill",
    "FileDownloadParams",
    "FileDownloadSkill",
    # param resolver
    "resolve_param_mapping",
    "resolve_param_value",
]

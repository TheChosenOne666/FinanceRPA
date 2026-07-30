"""Skill 元数据查询 API。

提供已注册 Skill 的完整元数据，供 Java 后端校验 Skill 存在性与前端动态生成参数表单。

@endpoint GET /api/v1/ai/skills —— 返回所有已注册 Skill 的元数据列表

@author FinanceRPA
"""

import logging

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from app.skills import list_skills

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/ai/skills", tags=["skills"])


class SkillMetaItem(BaseModel):
    """单个 Skill 元数据项。

    字段统一输出 camelCase（alias_generator=to_camel），
    与 Java WebClient 默认 camelCase 反序列化对齐。
    """

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    name: str
    description: str
    category: str
    error_strategy: str
    max_retries: int
    params_schema: dict


@router.get("", response_model=list[SkillMetaItem])
async def get_skills() -> list[SkillMetaItem]:
    """查询所有已注册 Skill 的元数据。

    返回 Python SKILL_REGISTRY 中全部 Skill 的：
    - name: 唯一标识
    - description: 用途描述
    - category: 分类（auth / interaction / extraction）
    - error_strategy: 失败处理策略
    - max_retries: 最大重试次数
    - params_schema: 参数 JSON Schema

    Java 后端在注册自定义 Skill 时调用此端点校验 name 是否存在。
    """
    # 1. 从全局注册表读取所有 Skill 元数据
    raw = list_skills()
    logger.info("查询 Skill 元数据: count=%d", len(raw))
    # 2. 转换为响应模型（params_schema 已是 dict，直接透传）
    return [SkillMetaItem(**item) for item in raw]

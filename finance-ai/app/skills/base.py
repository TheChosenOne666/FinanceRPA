"""Skill 基类接口与注册表。

所有 Skill 继承 BaseSkill 并通过 @register_skill 装饰器注册。
每个 Skill 包含：
- Pydantic params_model 定义输入参数
- Pydantic SkillResult 定义输出
- async execute() 执行方法
- error_strategy 定义失败行为（retry/skip/abort）

@from enterprise/skills/base.py
@author FinanceRPA
"""

import enum
import logging
from abc import ABC, abstractmethod
from typing import Any, ClassVar

from pydantic import BaseModel

logger = logging.getLogger(__name__)


class ErrorStrategy(str, enum.Enum):  # noqa: UP042
    """Skill 执行失败时的处理策略。"""

    RETRY = "retry"      # 重试最多 max_retries 次
    SKIP = "skip"        # 标记为跳过，继续执行
    ABORT = "abort"      # 终止整个管线


class SkillStatus(str, enum.Enum):  # noqa: UP042
    """Skill 调用的执行状态。"""

    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"


class SkillResult(BaseModel):
    """任何 Skill 执行的标准化结果。"""

    status: SkillStatus = SkillStatus.COMPLETED
    data: dict[str, Any] | None = None
    error_message: str | None = None
    screenshots: list[str] | None = None  # MinIO 截图 key 列表
    duration_ms: int | None = None


class BaseSkill(ABC):
    """所有可组合 Skill 的抽象基类。

    子类必须定义：
    - skill_name: 唯一标识符
    - description: 人类可读的用途描述
    - category: 分类（auth / interaction / extraction）
    - params_model: 输入参数的 Pydantic 模型类
    - error_strategy: 失败处理策略
    - execute(): 执行 Skill 的异步方法
    """

    skill_name: ClassVar[str]
    description: ClassVar[str]
    category: ClassVar[str] = "misc"
    params_model: ClassVar[type[BaseModel]]
    error_strategy: ClassVar[ErrorStrategy] = ErrorStrategy.RETRY
    max_retries: ClassVar[int] = 2

    @abstractmethod
    async def execute(
        self,
        params: BaseModel,
        context: dict[str, Any] | None = None,
    ) -> SkillResult:
        """执行 Skill。

        @param params: 已验证的 self.params_model 实例
        @param context: 可选执行上下文（浏览器页面、会话等）
        @return: 包含状态和输出数据的 SkillResult
        """

    def get_failure_strategy(self, error: str | None = None) -> ErrorStrategy:
        """根据错误信息返回失败处理策略。

        默认返回 self.error_strategy（ClassVar 声明的默认策略）。
        子类可覆写此方法，根据 error 内容动态决策，例如：
        - LoginSkill 区分 captcha 错误 vs 网络错误
        - FormFillSkill 区分 字段未找到 vs 提交超时

        @param error: Skill 执行失败时的错误信息（可空）
        @return: 失败处理策略（RETRY / SKIP / ABORT）
        """
        return self.error_strategy

    def validate_params(self, raw_params: dict[str, Any]) -> BaseModel:
        """校验原始参数。"""
        return self.params_model.model_validate(raw_params)

    def to_audit_dict(self, params: BaseModel) -> dict[str, Any]:
        """生成审计安全表示（敏感字段脱敏）。"""
        data = params.model_dump()
        # 脱敏包含 password/secret/token/key 的字段
        for key in data:
            lower_key = key.lower()
            if any(word in lower_key for word in ("password", "secret", "token", "key")):
                val = str(data[key])
                if len(val) > 4:
                    data[key] = val[0] + "*" * (len(val) - 2) + val[-1]
                else:
                    data[key] = "****"
        return {"skill": self.skill_name, "params": data}


# 全局 Skill 注册表
SKILL_REGISTRY: dict[str, type[BaseSkill]] = {}


def register_skill(cls: type[BaseSkill]) -> type[BaseSkill]:
    """装饰器：注册 Skill 类到全局注册表。"""
    if not hasattr(cls, "skill_name") or not cls.skill_name:
        raise ValueError(f"Skill 类 {cls.__name__} 必须定义 skill_name")
    SKILL_REGISTRY[cls.skill_name] = cls
    logger.debug("已注册 Skill: %s (%s)", cls.skill_name, cls.__name__)
    return cls


def get_skill(name: str) -> type[BaseSkill] | None:
    """按名称查找 Skill 类。"""
    return SKILL_REGISTRY.get(name)


def list_skills() -> list[dict[str, Any]]:
    """返回所有已注册 Skill 的完整元数据。

    每条记录包含：
    - name: Skill 唯一标识
    - description: 用途描述
    - category: 分类（auth / interaction / extraction）
    - error_strategy: 失败处理策略（retry / skip / abort）
    - max_retries: 最大重试次数
    - params_schema: Pydantic params_model 的 JSON Schema（供前端动态生成参数表单）
    """
    return [
        {
            "name": cls.skill_name,
            "description": cls.description,
            "category": cls.category,
            "error_strategy": cls.error_strategy.value,
            "max_retries": cls.max_retries,
            "params_schema": cls.params_model.model_json_schema(),
        }
        for cls in SKILL_REGISTRY.values()
    ]

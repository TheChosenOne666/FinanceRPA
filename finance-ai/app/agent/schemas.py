"""Agent 三层架构数据模型。

定义 SubTask、TaskPlan、ExecutionResult 和 CoordinationState，
供 PlannerAgent、ExecutorAgent 和 AgentCoordinator 使用。

@from enterprise/agent/schemas.py
@author FinanceRPA
"""

import enum
import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class FailureStrategy(str, enum.Enum):
    """子任务失败策略。"""

    RETRY = "retry"       # 重试（由 Executor 内部处理）
    SKIP = "skip"         # 跳过，继续下一个
    ABORT = "abort"       # 终止整个任务
    REPLAN = "replan"     # 让 Planner 重新规划剩余步骤


class SubTaskStatus(str, enum.Enum):
    """单个子任务的执行状态。"""

    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"
    REPLANNED = "replanned"  # 被新计划替换


class SubTask(BaseModel):
    """Planner 产出的单个子任务。"""

    subtask_id: str = Field(default_factory=lambda: f"sub_{uuid.uuid4().hex[:12]}")
    index: int = Field(description="执行顺序（0-based）")
    goal: str = Field(description="该子任务要做什么")
    completion_condition: str = Field(description="如何验证成功")
    max_retries: int = Field(default=2, description="最大重试次数")
    failure_strategy: FailureStrategy = Field(
        default=FailureStrategy.REPLAN,
        description="失败时的处理策略",
    )
    status: SubTaskStatus = Field(default=SubTaskStatus.PENDING)
    error_message: str | None = None
    result_data: dict | None = None
    started_at: datetime | None = None
    completed_at: datetime | None = None


class TaskPlan(BaseModel):
    """Planner 产出的完整计划。"""

    plan_id: str = Field(default_factory=lambda: f"plan_{uuid.uuid4().hex[:12]}")
    navigation_goal: str = Field(description="原始用户目标")
    subtasks: list[SubTask] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.utcnow)
    is_replan: bool = Field(default=False, description="是否为失败后重新规划的计划")
    replan_reason: str | None = None
    version: int = Field(default=1, description="计划版本号（replan 时递增）")


class ExecutionResult(BaseModel):
    """Executor 执行子任务的结果。"""

    subtask_id: str
    success: bool
    result_data: dict | None = None
    error_message: str | None = None
    screenshot_key: str | None = None  # MinIO 截图 key
    page_url: str | None = None
    duration_ms: int | None = None


class CoordinationState(BaseModel):
    """Coordinator 维护的全局状态。"""

    task_id: str
    org_id: str
    navigation_goal: str
    current_plan: TaskPlan | None = None
    completed_subtasks: list[str] = Field(
        default_factory=list,
        description="已完成的子任务 ID 列表（用于断点续跑）",
    )
    total_replans: int = 0
    max_replans: int = Field(default=3)
    status: str = "running"  # running / completed / failed / needs_human
    error_message: str | None = None

"""全局数据模型。

定义 Java → Python 的请求/响应模型和 SSE 事件结构。

@author FinanceRPA
"""

from datetime import datetime

from pydantic import BaseModel, Field


class TaskTriggerRequest(BaseModel):
    """任务触发请求（Java → Python）。"""

    task_id: str = Field(description="任务 ID（UUID）")
    org_id: str = Field(description="组织 ID（租户隔离）")
    user_id: str = Field(description="操作用户 ID")
    goal: str = Field(description="任务目标（如 '下载银行流水'）")
    params: dict = Field(default_factory=dict, description="任务参数")
    workflow_id: str | None = Field(default=None, description="关联工作流模板")


class TaskTriggerResponse(BaseModel):
    """任务触发响应。"""

    task_id: str = Field(description="任务 ID")
    status: str = Field(default="running", description="初始状态")
    message: str = Field(default="Task triggered successfully")


class TaskStateResponse(BaseModel):
    """任务状态响应。"""

    task_id: str
    state: str  # pending / executing / success / failed / needs_human
    current_step: int = 0
    total_steps: int = 0
    message: str = ""


class TaskAbortResponse(BaseModel):
    """任务终止响应。"""

    task_id: str
    aborted: bool = True
    message: str = "Task aborted"


class SseEvent(BaseModel):
    """SSE 事件。"""

    task_id: str
    event_type: str  # step_start / step_end / progress / error / complete / replan
    data: dict
    timestamp: datetime = Field(default_factory=datetime.utcnow)

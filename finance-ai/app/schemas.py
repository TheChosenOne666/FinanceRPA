"""全局数据模型。

定义 Java → Python 的请求/响应模型和 SSE 事件结构。

命名策略：内部字段使用 Python 惯例 snake_case，JSON 收发统一驼峰（camelCase），
通过 Pydantic alias_generator 实现，与 Java/前端全链路驼峰对齐。

@author FinanceRPA
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

# 全局驼峰命名配置：内部 snake_case，JSON IO 驼峰
_CAMEL_CONFIG = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class TaskTriggerRequest(BaseModel):
    """任务触发请求（Java → Python）。"""

    model_config = _CAMEL_CONFIG

    task_id: str = Field(description="任务 ID（雪花算法）")
    org_id: str = Field(description="组织 ID（租户隔离）")
    user_id: str = Field(description="操作用户 ID")
    goal: str = Field(description="任务目标（如 '下载银行流水'）")
    params: dict = Field(default_factory=dict, description="任务参数")
    workflow_id: str | None = Field(default=None, description="关联工作流模板")


class TaskTriggerResponse(BaseModel):
    """任务触发响应。"""

    model_config = _CAMEL_CONFIG

    task_id: str = Field(description="任务 ID")
    skyvern_task_id: str | None = Field(default=None, description="Skyvern 任务 ID（M3.8 引入）")
    status: str = Field(default="running", description="初始状态")
    message: str = Field(default="Task triggered successfully")


class TaskStateResponse(BaseModel):
    """任务状态响应。"""

    model_config = _CAMEL_CONFIG

    task_id: str
    state: str  # pending / executing / success / failed / needs_human
    current_step: int = 0
    total_steps: int = 0
    message: str = ""


class TaskAbortResponse(BaseModel):
    """任务终止响应。"""

    model_config = _CAMEL_CONFIG

    task_id: str
    aborted: bool = True
    message: str = "Task aborted"


class TaskResumeRequest(BaseModel):
    """任务续跑请求（M4.3：Java → Python，从断点继续执行）。

    Java 侧从 rpa_agent_coordination_state 读取已存计划 + completed_subtasks，
    传入此接口让 Python Coordinator 从断点继续执行，不重做已完成子任务。
    """

    model_config = _CAMEL_CONFIG

    task_id: str
    org_id: str
    navigation_goal: str
    completed_subtasks: list[str] = Field(default_factory=list, description="已完成子任务 ID 列表")
    current_plan: str = Field(description="已存计划 JSON 字符串（TaskPlan.model_dump_json()）")
    params: dict | None = Field(default=None, description="工作流模板参数（上下文）")


class TaskResumeResponse(BaseModel):
    """任务续跑响应。"""

    model_config = _CAMEL_CONFIG

    task_id: str
    status: str = "running"
    message: str = "任务续跑已触发"


class SseEvent(BaseModel):
    """SSE 事件。"""

    model_config = _CAMEL_CONFIG

    task_id: str
    event_type: str  # step_start / step_end / progress / error / complete / replan
    data: dict
    timestamp: datetime = Field(default_factory=datetime.utcnow)

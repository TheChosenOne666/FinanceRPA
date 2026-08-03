"""审计日志数据结构（M7.3）。

对齐 Java 侧 AuditLogCreateRequest（系统设计 6.4.1 完整结构）。
Python 上报元数据前填充此结构，序列化后 POST /api/internal/audit/logs。

@from enterprise/audit/schemas.py
@author FinanceRPA
"""

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class AuditLogPayload(BaseModel):
    """审计日志上报载荷（对齐 Java AuditLogCreateRequest）。

    字段覆盖系统设计 6.4.1 六大维度：基本信息 / 操作信息 / 风险信息 /
    时间信息 / 截图信息 / LLM 信息。除 taskId/orgId/actionType 外均可选。
    """

    # region 基本信息
    task_id: str = Field(..., alias="taskId", description="任务 ID")
    org_id: str = Field(..., alias="orgId", description="组织 ID")
    department_id: str | None = Field(None, alias="departmentId", description="部门 ID")
    business_line_id: str | None = Field(None, alias="businessLineId", description="业务线 ID")
    user_id: str | None = Field(None, alias="userId", description="触发用户 ID")
    # endregion

    # region 操作信息
    action_type: str = Field(..., alias="actionType", description="动作类型")
    target_element: str | None = Field(None, alias="targetElement", description="目标元素")
    page_url: str | None = Field(None, alias="pageUrl", description="页面 URL")
    action_params: str | None = Field(None, alias="actionParams", description="操作参数 JSON（Java 侧脱敏）")
    execution_result: str = Field("success", alias="executionResult", description="执行结果")
    error_message: str | None = Field(None, alias="errorMessage", description="错误信息")
    # endregion

    # region 风险信息
    risk_level: str | None = Field(None, alias="riskLevel", description="风险等级")
    approval_id: str | None = Field(None, alias="approvalId", description="审批单 ID")
    # endregion

    # region 时间信息
    started_at: datetime | None = Field(None, alias="startedAt", description="开始时间")
    completed_at: datetime | None = Field(None, alias="completedAt", description="完成时间")
    duration_ms: int | None = Field(None, alias="durationMs", description="耗时毫秒")
    # endregion

    # region 截图信息（M7.2 MinIO 预签名 URL）
    before_screenshot_url: str | None = Field(None, alias="beforeScreenshotUrl", description="操作前截图 URL")
    after_screenshot_url: str | None = Field(None, alias="afterScreenshotUrl", description="操作后截图 URL")
    # endregion

    # region LLM 信息
    llm_model: str | None = Field(None, alias="llmModel", description="LLM 模型")
    llm_tokens_used: int | None = Field(None, alias="llmTokensUsed", description="token 用量")
    llm_cost: float | None = Field(None, alias="llmCost", description="调用成本")
    # endregion

    model_config = {"populate_by_name": True, "extra": "ignore"}


class ScreenshotUploadResult(BaseModel):
    """截图上传结果（对齐 Java ScreenshotUploadVO）。"""

    object_path: str = Field(..., alias="objectPath", description="对象路径")
    presign_url: str = Field(..., alias="presignUrl", description="预签名 URL")

    model_config = {"populate_by_name": True, "extra": "ignore"}

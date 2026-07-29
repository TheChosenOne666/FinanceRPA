"""SSE 流推送端点。

通过 Server-Sent Events 向前端推送任务执行进度。

@author FinanceRPA
"""

import asyncio
import json
import logging
from datetime import datetime

from fastapi import APIRouter, HTTPException
from sse_starlette.sse import EventSourceResponse

from app.api.tasks import _task_states

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/ai/sse", tags=["sse"])


@router.get("/tasks/{task_id}")
async def subscribe_task_sse(task_id: str):
    """SSE 订阅任务执行进度。

    前端使用 EventSource 连接此端点，接收实时执行事件。
    """
    if task_id not in _task_states:
        raise HTTPException(status_code=404, detail=f"Task not found: {task_id}")

    async def event_generator():
        """生成 SSE 事件流。"""
        last_step = -1
        while True:
            state = _task_states.get(task_id)
            if state is None:
                yield {"event": "error", "data": json.dumps({"error": "Task not found"})}
                break

            current_step = state["current_step"]
            task_state = state["state"]

            # 步骤变化时推送进度
            if current_step != last_step:
                last_step = current_step
                yield {
                    "event": "progress",
                    "data": json.dumps({
                        "taskId": task_id,
                        "state": task_state,
                        "currentStep": current_step,
                        "totalSteps": state["total_steps"],
                        "message": state["message"],
                        "timestamp": datetime.utcnow().isoformat(),
                    }),
                }

            # 任务结束
            if task_state in ("success", "completed", "failed", "needs_human"):
                yield {
                    "event": "complete",
                    "data": json.dumps({
                        "taskId": task_id,
                        "state": task_state,
                        "message": state["message"],
                        "timestamp": datetime.utcnow().isoformat(),
                    }),
                }
                break

            await asyncio.sleep(0.5)

    return EventSourceResponse(event_generator())

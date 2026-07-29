"""SSE 流推送端点。

M2.4：基于事件总线（TaskEventBus）的发布-订阅模型，
Executor 发布事件 → SSE 端点订阅推送，取代 M2.1 的轮询模式。

@author FinanceRPA
"""

import json
import logging

from fastapi import APIRouter
from sse_starlette.sse import EventSourceResponse

from app.agent.event_bus import get_event_bus

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/ai/sse", tags=["sse"])


@router.get("/tasks/{task_id}")
async def subscribe_task_sse(task_id: str):
    """SSE 订阅任务执行进度。

    前端使用 EventSource 连接此端点，接收实时执行事件。
    事件类型：step_start / step_end / progress / replan / screenshot / error / complete
    """
    event_bus = get_event_bus()

    async def event_generator():
        """从事件总线订阅事件流。"""
        async for event in event_bus.subscribe(task_id):
            yield {
                "event": event["event"],
                "data": json.dumps(event["data"], default=str),
            }

    return EventSourceResponse(event_generator())

"""ExecutorAgent 单元测试。"""

import asyncio
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.agent.event_bus import TaskEventBus
from app.agent.executor import ExecutorAgent
from app.agent.schemas import SubTask, SubTaskStatus


@pytest.mark.asyncio
async def test_simulate_execution_success():
    """无 action_handler 时应使用模拟执行并成功。"""
    executor = ExecutorAgent()
    subtask = SubTask(index=0, goal="测试目标", completion_condition="成功")

    result = await executor.execute_subtask(subtask)

    assert result.success is True
    assert result.result_data is not None
    assert result.result_data.get("simulated") is True
    assert subtask.status == SubTaskStatus.COMPLETED


@pytest.mark.asyncio
async def test_action_handler_success():
    """action_handler 返回成功时应标记完成。"""
    async def mock_handler(goal, context):
        return {"success": True, "data": {"url": "https://result.com"}, "page_url": "https://result.com"}

    executor = ExecutorAgent(action_handler=mock_handler)
    subtask = SubTask(index=0, goal="导航", completion_condition="URL 匹配")

    result = await executor.execute_subtask(subtask)

    assert result.success is True
    assert result.page_url == "https://result.com"
    assert subtask.status == SubTaskStatus.COMPLETED


@pytest.mark.asyncio
async def test_action_handler_retry_then_success():
    """失败后应重试，最终成功。"""
    call_count = 0

    async def flaky_handler(goal, context):
        nonlocal call_count
        call_count += 1
        if call_count < 3:
            return {"success": False, "error": "暂时失败"}
        return {"success": True, "data": {"attempt": call_count}}

    executor = ExecutorAgent(action_handler=flaky_handler)
    subtask = SubTask(index=0, goal="重试测试", completion_condition="成功", max_retries=3)

    result = await executor.execute_subtask(subtask)

    assert result.success is True
    assert call_count == 3
    assert subtask.status == SubTaskStatus.COMPLETED


@pytest.mark.asyncio
async def test_all_retries_exhausted():
    """所有重试耗尽后应标记失败。"""
    async def always_fail(goal, context):
        return {"success": False, "error": "永久失败"}

    executor = ExecutorAgent(action_handler=always_fail)
    subtask = SubTask(index=0, goal="失败测试", completion_condition="不可能", max_retries=2)

    result = await executor.execute_subtask(subtask)

    assert result.success is False
    assert result.error_message == "永久失败"
    assert subtask.status == SubTaskStatus.FAILED
    assert subtask.error_message == "永久失败"


@pytest.mark.asyncio
async def test_action_handler_exception():
    """action_handler 抛异常时应捕获并重试。"""
    call_count = 0

    async def crashing_handler(goal, context):
        nonlocal call_count
        call_count += 1
        raise RuntimeError("崩溃错误")

    executor = ExecutorAgent(action_handler=crashing_handler)
    subtask = SubTask(index=0, goal="异常测试", completion_condition="成功", max_retries=1)

    result = await executor.execute_subtask(subtask)

    assert result.success is False
    assert "崩溃错误" in result.error_message
    assert subtask.status == SubTaskStatus.FAILED


# ---------------------------------------------------------------------------
# M2.4 集成测试：JavaBackendClient 回调 + 事件总线
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_executor_with_java_callback_success():
    """成功执行时应回调 Java 更新子任务状态 + 发布 SSE 事件。"""
    # 模拟 JavaBackendClient
    mock_java = MagicMock()
    mock_java.update_subtask = AsyncMock(return_value=True)
    mock_java.report_audit_log = AsyncMock(return_value=True)

    # 事件总线
    bus = TaskEventBus()
    bus.register("task-cb-1")

    # 收集 SSE 事件
    events = []

    async def collect():
        async for event in bus.subscribe("task-cb-1"):
            events.append(event)

    collector_task = asyncio.create_task(collect())
    await asyncio.sleep(0.05)

    executor = ExecutorAgent(
        java_client=mock_java,
        event_bus=bus,
        task_id="task-cb-1",
        org_id="org-1",
    )
    subtask = SubTask(index=0, goal="测试回调", completion_condition="成功")

    result = await executor.execute_subtask(subtask)

    assert result.success is True

    # 等待 collector 处理事件
    await asyncio.sleep(0.1)

    # 验证 Java 回调：RUNNING → COMPLETED
    assert mock_java.update_subtask.call_count == 2
    first_call = mock_java.update_subtask.call_args_list[0]
    assert first_call[1]["status"] == "RUNNING"
    second_call = mock_java.update_subtask.call_args_list[1]
    assert second_call[1]["status"] == "COMPLETED"

    # 验证审计日志
    mock_java.report_audit_log.assert_called_once()

    # 验证 SSE 事件
    assert len(events) == 2
    assert events[0]["event"] == "step_start"
    assert events[1]["event"] == "step_end"
    assert events[1]["data"]["success"] is True

    collector_task.cancel()


@pytest.mark.asyncio
async def test_executor_with_java_callback_failure():
    """失败执行时应回调 Java 更新子任务状态为 FAILED。"""
    async def fail_handler(goal, context):
        return {"success": False, "error": "执行失败"}

    mock_java = MagicMock()
    mock_java.update_subtask = AsyncMock(return_value=True)
    mock_java.report_audit_log = AsyncMock(return_value=True)

    bus = TaskEventBus()
    bus.register("task-cb-2")

    executor = ExecutorAgent(
        action_handler=fail_handler,
        java_client=mock_java,
        event_bus=bus,
        task_id="task-cb-2",
        org_id="org-1",
    )
    subtask = SubTask(index=0, goal="失败回调", completion_condition="不可能", max_retries=1)

    result = await executor.execute_subtask(subtask)

    assert result.success is False

    # 验证 Java 回调：最终状态为 FAILED
    last_call = mock_java.update_subtask.call_args_list[-1]
    assert last_call[1]["status"] == "FAILED"
    assert last_call[1]["error_message"] == "执行失败"

    # 验证审计日志包含失败信息
    audit_call = mock_java.report_audit_log.call_args
    assert audit_call[1]["execution_result"] == "failed"


@pytest.mark.asyncio
async def test_executor_without_java_client():
    """无 JavaBackendClient 时应正常执行（向后兼容）。"""
    executor = ExecutorAgent()  # 无 java_client, event_bus
    subtask = SubTask(index=0, goal="兼容测试", completion_condition="成功")

    result = await executor.execute_subtask(subtask)

    assert result.success is True
    assert subtask.status == SubTaskStatus.COMPLETED

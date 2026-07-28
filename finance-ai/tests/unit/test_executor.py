"""ExecutorAgent 单元测试。"""

import pytest

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

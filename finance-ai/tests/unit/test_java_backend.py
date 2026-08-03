"""JavaBackendClient 单元测试。"""

from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from app.clients.java_backend import JavaBackendClient


@pytest.fixture
def mock_response():
    """构造模拟 httpx 响应。"""
    resp = MagicMock()
    resp.raise_for_status = MagicMock()
    resp.json.return_value = {"data": {"url": "http://screenshot.url/test.png"}}
    resp.status_code = 200
    return resp


def _make_mock_client(client_obj, response):
    """构造模拟 httpx 客户端，request 方法返回指定响应。"""
    client_obj.request = AsyncMock(return_value=response)
    return client_obj


@pytest.mark.asyncio
async def test_update_task_state_success(mock_response):
    """成功更新任务状态应返回 True。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")
    mock_http = MagicMock()
    mock_http.is_closed = False
    _make_mock_client(mock_http, mock_response)
    client._client = mock_http

    result = await client.update_task_state(
        task_id="123",
        state="EXECUTING",
        current_step=1,
        total_steps=3,
        message="执行中",
    )

    assert result is True
    mock_http.request.assert_called_once()
    call_args = mock_http.request.call_args
    assert call_args[0][0] == "POST"
    assert "/api/internal/tasks/123/state" in call_args[0][1]
    assert call_args[1]["json"]["state"] == "EXECUTING"
    assert call_args[1]["json"]["currentStep"] == 1
    assert call_args[1]["json"]["totalSteps"] == 3


@pytest.mark.asyncio
async def test_update_task_state_with_error():
    """带错误信息的状态更新应包含 errorMessage。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_resp = MagicMock()
    mock_resp.raise_for_status = MagicMock()
    mock_http = MagicMock()
    mock_http.is_closed = False
    _make_mock_client(mock_http, mock_resp)
    client._client = mock_http

    result = await client.update_task_state(
        task_id="456",
        state="FAILED",
        error_message="元素未找到",
    )

    assert result is True
    call_args = mock_http.request.call_args
    assert call_args[1]["json"]["errorMessage"] == "元素未找到"


@pytest.mark.asyncio
async def test_update_task_state_failure():
    """HTTP 错误应返回 False（重试后仍失败）。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_http = MagicMock()
    mock_http.is_closed = False
    mock_http.request = AsyncMock(side_effect=httpx.RequestError("连接失败"))
    client._client = mock_http

    # Mock asyncio.sleep to avoid real delays
    import app.clients.java_backend as jb_module
    original_sleep = jb_module.asyncio.sleep
    jb_module.asyncio.sleep = AsyncMock(return_value=None)

    try:
        result = await client.update_task_state(
            task_id="789",
            state="EXECUTING",
        )
    finally:
        jb_module.asyncio.sleep = original_sleep

    assert result is False


@pytest.mark.asyncio
async def test_update_subtask_success(mock_response):
    """成功更新子任务状态应返回 True。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_http = MagicMock()
    mock_http.is_closed = False
    _make_mock_client(mock_http, mock_response)
    client._client = mock_http

    result = await client.update_subtask(
        task_id="123",
        subtask_index=0,
        status="COMPLETED",
        result_data={"key": "value"},
    )

    assert result is True
    call_args = mock_http.request.call_args
    assert call_args[0][0] == "POST"
    assert "/api/internal/tasks/123/subtasks" in call_args[0][1]
    assert call_args[1]["json"]["subtaskIndex"] == 0
    assert call_args[1]["json"]["status"] == "COMPLETED"
    assert call_args[1]["json"]["resultData"] == {"key": "value"}


@pytest.mark.asyncio
async def test_update_subtask_with_error():
    """带错误信息的子任务更新应包含 errorMessage。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_resp = MagicMock()
    mock_resp.raise_for_status = MagicMock()
    mock_http = MagicMock()
    mock_http.is_closed = False
    _make_mock_client(mock_http, mock_resp)
    client._client = mock_http

    result = await client.update_subtask(
        task_id="123",
        subtask_index=1,
        status="FAILED",
        error_message="超时",
    )

    assert result is True
    call_args = mock_http.request.call_args
    assert call_args[1]["json"]["errorMessage"] == "超时"


@pytest.mark.asyncio
async def test_upload_screenshot_success():
    """成功上传截图应返回预签名 URL（M7.2 对齐）。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_resp = MagicMock()
    mock_resp.raise_for_status = MagicMock()
    # Java BaseResponse 封装：{ code: 0, data: { objectPath, presignUrl } }
    mock_resp.json.return_value = {
        "code": 0,
        "data": {"objectPath": "2026-08-03/123/1_before.png", "presignUrl": "http://minio/presigned"},
    }
    mock_http = MagicMock()
    mock_http.is_closed = False
    _make_mock_client(mock_http, mock_resp)
    client._client = mock_http

    url = await client.upload_screenshot(
        org_id="org-1",
        task_id="123",
        step_index=1,
        phase="before",
        image_data=b"fake-png-data",
    )

    assert url == "http://minio/presigned"
    call_args = mock_http.request.call_args
    assert call_args[0][0] == "POST"
    assert "/api/internal/screenshots" in call_args[0][1]


@pytest.mark.asyncio
async def test_upload_screenshot_failure():
    """上传截图失败应返回 None。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_http = MagicMock()
    mock_http.is_closed = False
    mock_http.request = AsyncMock(side_effect=httpx.RequestError("连接失败"))
    client._client = mock_http

    # Mock asyncio.sleep to avoid real delays
    import app.clients.java_backend as jb_module
    original_sleep = jb_module.asyncio.sleep
    jb_module.asyncio.sleep = AsyncMock(return_value=None)

    try:
        url = await client.upload_screenshot(
            org_id="org-1",
            task_id="123",
            step_index=1,
            phase="before",
            image_data=b"fake-png-data",
        )
    finally:
        jb_module.asyncio.sleep = original_sleep

    assert url is None


@pytest.mark.asyncio
async def test_report_audit_log_success():
    """成功上报审计日志应返回 True（M7.3 支持AuditLogPayload/dict）。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_resp = MagicMock()
    mock_resp.raise_for_status = MagicMock()
    mock_http = MagicMock()
    mock_http.is_closed = False
    _make_mock_client(mock_http, mock_resp)
    client._client = mock_http

    # M7.3：传 AuditLogPayload 对象
    from app.audit.schemas import AuditLogPayload
    payload = AuditLogPayload(
        taskId="123",
        orgId="org-1",
        actionType="CLICK",
        pageUrl="https://example.com",
        executionResult="success",
    )

    result = await client.report_audit_log(payload)

    assert result is True
    call_args = mock_http.request.call_args
    assert call_args[0][0] == "POST"
    assert "/api/internal/audit/logs" in call_args[0][1]
    assert call_args[1]["json"]["actionType"] == "CLICK"
    assert call_args[1]["json"]["pageUrl"] == "https://example.com"


@pytest.mark.asyncio
async def test_close_client():
    """close 应关闭 httpx 客户端。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="test-token")

    mock_http = MagicMock()
    mock_http.is_closed = False
    mock_http.aclose = AsyncMock()
    client._client = mock_http

    await client.close()

    mock_http.aclose.assert_called_once()
    assert client._client is None


@pytest.mark.asyncio
async def test_auth_header():
    """请求应携带 X-Internal-Token Header。"""
    client = JavaBackendClient(base_url="http://localhost:8080", internal_token="my-secret")

    # 验证客户端创建时设置了正确的 header
    http_client = await client._get_client()
    assert http_client.headers["X-Internal-Token"] == "my-secret"

    await client.close()

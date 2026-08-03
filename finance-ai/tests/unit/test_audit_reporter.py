"""AuditReporter 单元测试（M7.3）。

@from enterprise/tests/unit/test_audit_reporter.py
@author FinanceRPA
"""

from datetime import datetime
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.audit.cache import AuditCache
from app.audit.reporter import AuditReporter
from app.audit.schemas import AuditLogPayload


@pytest.fixture
def mock_java_client():
    """Mock JavaBackendClient。"""
    client = MagicMock()
    client.upload_screenshot = AsyncMock(return_value=None)
    client.report_audit_log = AsyncMock(return_value=False)
    return client


@pytest.fixture
def mock_cache():
    """Mock AuditCache。"""
    cache = MagicMock(spec=AuditCache)
    cache.save = MagicMock(return_value="/tmp/fake.json")
    cache.load_all = MagicMock(return_value=[])
    cache.remove = MagicMock()
    cache.count = MagicMock(return_value=0)
    return cache


@pytest.fixture
def reporter(mock_java_client, mock_cache):
    """构造带 mock 依赖的 AuditReporter。"""
    return AuditReporter(java_client=mock_java_client, cache=mock_cache)


# region upload_screenshot

@pytest.mark.asyncio
async def test_upload_screenshot_success(reporter, mock_java_client):
    """Java 上传成功时返回预签名 URL，不缓存。"""
    mock_java_client.upload_screenshot.return_value = "http://minio/presigned"

    url = await reporter.upload_screenshot(
        org_id="org-1", task_id="123", step_index=1, phase="before",
        image_data=b"fake-png",
    )

    assert url == "http://minio/presigned"
    mock_java_client.upload_screenshot.assert_called_once_with(
        org_id="org-1", task_id="123", step_index=1, phase="before",
        image_data=b"fake-png",
    )


@pytest.mark.asyncio
async def test_upload_screenshot_failure_caches(reporter, mock_java_client, mock_cache):
    """Java 返回 None 时缓存待重试。"""
    mock_java_client.upload_screenshot.return_value = None

    url = await reporter.upload_screenshot(
        org_id="org-1", task_id="123", step_index=1, phase="before",
        image_data=b"fake-png",
    )

    assert url is None
    mock_cache.save.assert_called_once()
    args = mock_cache.save.call_args
    assert args[0][0] == "screenshot"
    assert args[0][1]["task_id"] == "123"
    assert args[0][1]["phase"] == "before"
    assert "image_b64" in args[0][1]


@pytest.mark.asyncio
async def test_upload_screenshot_exception_caches(reporter, mock_java_client, mock_cache):
    """Java 抛异常时缓存待重试，不向外抛出。"""
    mock_java_client.upload_screenshot.side_effect = RuntimeError("连接失败")

    url = await reporter.upload_screenshot(
        org_id="org-1", task_id="123", step_index=1, phase="after",
        image_data=b"fake-png",
    )

    assert url is None
    mock_cache.save.assert_called_once()
    args = mock_cache.save.call_args
    assert args[0][1]["phase"] == "after"


# endregion

# region report_audit_log

@pytest.mark.asyncio
async def test_report_audit_log_success(reporter, mock_java_client):
    """Java 上报成功时返回 True，不缓存。"""
    mock_java_client.report_audit_log.return_value = True
    payload = AuditLogPayload(taskId="123", orgId="org-1", actionType="CLICK")

    result = await reporter.report_audit_log(payload)

    assert result is True
    mock_java_client.report_audit_log.assert_called_once_with(payload)


@pytest.mark.asyncio
async def test_report_audit_log_failure_caches(reporter, mock_java_client, mock_cache):
    """Java 返回 False 时缓存待重试。"""
    mock_java_client.report_audit_log.return_value = False
    payload = AuditLogPayload(taskId="123", orgId="org-1", actionType="CLICK")

    result = await reporter.report_audit_log(payload)

    assert result is False
    mock_cache.save.assert_called_once()
    args = mock_cache.save.call_args
    assert args[0][0] == "audit_log"
    assert args[0][1]["taskId"] == "123"
    assert args[0][1]["actionType"] == "CLICK"


@pytest.mark.asyncio
async def test_report_audit_log_exception_caches(reporter, mock_java_client, mock_cache):
    """Java 抛异常时缓存待重试，不向外抛出。"""
    mock_java_client.report_audit_log.side_effect = RuntimeError("连接失败")
    payload = AuditLogPayload(taskId="123", orgId="org-1", actionType="CLICK")

    result = await reporter.report_audit_log(payload)

    assert result is False
    mock_cache.save.assert_called_once()


# endregion

# region report_step（完整闭环）

@pytest.mark.asyncio
async def test_report_step_full_flow(reporter, mock_java_client):
    """完整闭环：前后截图上传 + 元数据上报全部成功。"""
    mock_java_client.upload_screenshot.return_value = "http://minio/presigned"
    mock_java_client.report_audit_log.return_value = True

    result = await reporter.report_step(
        org_id="org-1", task_id="123", step_index=1, action_type="CLICK",
        before_screenshot=b"before-png",
        after_screenshot=b"after-png",
        duration_ms=500,
        execution_result="success",
    )

    assert result is True
    # 截图上传调用 2 次（before + after）
    assert mock_java_client.upload_screenshot.call_count == 2
    # 元数据上报调用 1 次
    mock_java_client.report_audit_log.assert_called_once()

    # 验证上报的 payload 含 before/after URL
    payload_arg = mock_java_client.report_audit_log.call_args[0][0]
    assert payload_arg.before_screenshot_url == "http://minio/presigned"
    assert payload_arg.after_screenshot_url == "http://minio/presigned"
    assert payload_arg.duration_ms == 500


@pytest.mark.asyncio
async def test_report_step_screenshot_fails_but_audit_continues(reporter, mock_java_client, mock_cache):
    """截图上传失败时元数据仍上报（URL 为 None），不阻断主流程。"""
    mock_java_client.upload_screenshot.return_value = None
    mock_java_client.report_audit_log.return_value = True

    result = await reporter.report_step(
        org_id="org-1", task_id="123", step_index=1, action_type="CLICK",
        before_screenshot=b"before-png",
        after_screenshot=b"after-png",
    )

    # 元数据上报成功
    assert result is True
    # 截图失败被缓存（2 次：before + after）
    assert mock_cache.save.call_count == 2

    # 验证上报的 payload URL 为 None
    payload_arg = mock_java_client.report_audit_log.call_args[0][0]
    assert payload_arg.before_screenshot_url is None
    assert payload_arg.after_screenshot_url is None


@pytest.mark.asyncio
async def test_report_step_without_screenshots(reporter, mock_java_client):
    """无截图时跳过上传，直接上报元数据。"""
    mock_java_client.report_audit_log.return_value = True

    result = await reporter.report_step(
        org_id="org-1", task_id="123", step_index=1, action_type="NAVIGATE",
        before_screenshot=None,
        after_screenshot=None,
    )

    assert result is True
    mock_java_client.upload_screenshot.assert_not_called()
    mock_java_client.report_audit_log.assert_called_once()


# endregion

# region flush_cache

@pytest.mark.asyncio
async def test_flush_cache_empty(reporter, mock_cache):
    """无缓存项时返回 0。"""
    mock_cache.load_all.return_value = []

    count = await reporter.flush_cache()

    assert count == 0


@pytest.mark.asyncio
async def test_flush_cache_audit_log_success(reporter, mock_java_client, mock_cache):
    """批量重试 audit_log 缓存项，成功时删除文件。"""
    mock_cache.load_all.return_value = [
        {
            "type": "audit_log",
            "payload": {"taskId": "123", "orgId": "org-1", "actionType": "CLICK"},
            "filepath": "/tmp/001.json",
        }
    ]
    mock_java_client.report_audit_log.return_value = True

    count = await reporter.flush_cache()

    assert count == 1
    mock_cache.remove.assert_called_once_with("/tmp/001.json")


@pytest.mark.asyncio
async def test_flush_cache_screenshot_success(reporter, mock_java_client, mock_cache):
    """批量重试 screenshot 缓存项，成功时删除文件。"""
    import base64
    image_b64 = base64.b64encode(b"fake-png").decode("ascii")

    mock_cache.load_all.return_value = [
        {
            "type": "screenshot",
            "payload": {
                "org_id": "org-1", "task_id": "123",
                "step_index": 1, "phase": "before",
                "image_b64": image_b64,
            },
            "filepath": "/tmp/002.json",
        }
    ]
    mock_java_client.upload_screenshot.return_value = "http://minio/presigned"

    count = await reporter.flush_cache()

    assert count == 1
    mock_java_client.upload_screenshot.assert_called_once()
    mock_cache.remove.assert_called_once_with("/tmp/002.json")


@pytest.mark.asyncio
async def test_flush_cache_failure_keeps_file(reporter, mock_java_client, mock_cache):
    """批量重试失败时保留缓存文件待下次 flush。"""
    mock_cache.load_all.return_value = [
        {
            "type": "audit_log",
            "payload": {"taskId": "123", "orgId": "org-1", "actionType": "CLICK"},
            "filepath": "/tmp/003.json",
        }
    ]
    mock_java_client.report_audit_log.return_value = False

    count = await reporter.flush_cache()

    assert count == 0
    mock_cache.remove.assert_not_called()


@pytest.mark.asyncio
async def test_flush_cache_mixed_types(reporter, mock_java_client, mock_cache):
    """混合类型缓存项：1 个截图成功 + 1 个审计日志失败。"""
    import base64
    image_b64 = base64.b64encode(b"fake-png").decode("ascii")

    mock_cache.load_all.return_value = [
        {
            "type": "screenshot",
            "payload": {
                "org_id": "org-1", "task_id": "123",
                "step_index": 1, "phase": "before",
                "image_b64": image_b64,
            },
            "filepath": "/tmp/screenshot.json",
        },
        {
            "type": "audit_log",
            "payload": {"taskId": "456", "orgId": "org-1", "actionType": "CLICK"},
            "filepath": "/tmp/audit.json",
        },
    ]
    mock_java_client.upload_screenshot.return_value = "http://minio/presigned"
    mock_java_client.report_audit_log.return_value = False

    count = await reporter.flush_cache()

    # 仅截图成功
    assert count == 1
    mock_cache.remove.assert_called_once_with("/tmp/screenshot.json")


# endregion

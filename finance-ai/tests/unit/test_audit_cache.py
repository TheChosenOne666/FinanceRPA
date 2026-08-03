"""AuditCache 本地缓存单元测试（M7.3）。

@from enterprise/tests/unit/test_audit_cache.py
@author FinanceRPA
"""

import json
import os

from app.audit.cache import AuditCache


def test_save_and_load_roundtrip(tmp_path):
    """save + load_all 往返：写入的数据能完整读回。"""
    cache = AuditCache(cache_dir=str(tmp_path))

    payload = {"task_id": "123", "action_type": "CLICK"}
    filepath = cache.save("audit_log", payload)

    assert filepath is not None
    assert os.path.exists(filepath)

    items = cache.load_all()
    assert len(items) == 1
    assert items[0]["type"] == "audit_log"
    assert items[0]["payload"] == payload
    assert items[0]["filepath"] == filepath


def test_save_creates_directory_if_not_exists(tmp_path):
    """缓存目录不存在时自动创建。"""
    cache_dir = tmp_path / "nested" / "cache"
    cache = AuditCache(cache_dir=str(cache_dir))

    filepath = cache.save("audit_log", {"task_id": "1"})

    assert filepath is not None
    assert cache_dir.exists()


def test_save_multiple_files_sorted_by_time(tmp_path):
    """多个缓存项按文件名时间序加载。"""
    cache = AuditCache(cache_dir=str(tmp_path))

    cache.save("audit_log", {"seq": 1})
    cache.save("audit_log", {"seq": 2})
    cache.save("audit_log", {"seq": 3})

    items = cache.load_all()
    assert len(items) == 3
    # 文件名带时间戳，按字典序即为时间序
    seqs = [item["payload"]["seq"] for item in items]
    assert seqs == [1, 2, 3]


def test_remove_deletes_file(tmp_path):
    """remove 删除指定缓存文件。"""
    cache = AuditCache(cache_dir=str(tmp_path))
    filepath = cache.save("audit_log", {"task_id": "1"})

    assert os.path.exists(filepath)
    cache.remove(filepath)
    assert not os.path.exists(filepath)


def test_remove_nonexistent_file_no_error(tmp_path):
    """remove 不存在的文件不抛异常。"""
    cache = AuditCache(cache_dir=str(tmp_path))
    cache.remove(str(tmp_path / "nonexistent.json"))  # 应静默处理


def test_load_all_empty_directory(tmp_path):
    """空目录返回空列表。"""
    cache = AuditCache(cache_dir=str(tmp_path))
    items = cache.load_all()
    assert items == []


def test_load_all_directory_not_exists(tmp_path):
    """目录不存在返回空列表。"""
    cache = AuditCache(cache_dir=str(tmp_path / "nonexistent"))
    items = cache.load_all()
    assert items == []


def test_count_returns_file_count(tmp_path):
    """count 返回缓存文件数。"""
    cache = AuditCache(cache_dir=str(tmp_path))
    assert cache.count() == 0

    cache.save("audit_log", {"1": 1})
    cache.save("audit_log", {"2": 2})
    assert cache.count() == 2


def test_count_ignores_non_json_files(tmp_path):
    """count 仅统计 .json 文件。"""
    cache = AuditCache(cache_dir=str(tmp_path))
    cache.save("audit_log", {"1": 1})

    # 写一个非 JSON 文件
    (tmp_path / "readme.txt").write_text("hello")

    assert cache.count() == 1


def test_save_screenshot_with_binary_data(tmp_path):
    """缓存截图（含 base64 编码的二进制数据）。"""
    import base64

    cache = AuditCache(cache_dir=str(tmp_path))
    image_data = b"\x89PNG\r\n\x1a\n"  # PNG 文件头
    encoded = base64.b64encode(image_data).decode("ascii")

    filepath = cache.save("screenshot", {
        "org_id": "org-1",
        "task_id": "123",
        "step_index": 1,
        "phase": "before",
        "image_b64": encoded,
    })

    assert filepath is not None
    items = cache.load_all()
    assert len(items) == 1

    # 验证 base64 可解码回原始二进制
    loaded_b64 = items[0]["payload"]["image_b64"]
    assert base64.b64decode(loaded_b64) == image_data


def test_load_all_skips_corrupt_json(tmp_path):
    """损坏的 JSON 文件被跳过不抛异常。"""
    cache = AuditCache(cache_dir=str(tmp_path))
    cache.save("audit_log", {"valid": True})

    # 写入损坏文件
    corrupt_file = tmp_path / "corrupt.json"
    corrupt_file.write_text("{ invalid json content")

    items = cache.load_all()
    assert len(items) == 1
    assert items[0]["payload"]["valid"] is True

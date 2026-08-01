"""M5.2 ActionCache + ResilientCaller 缓存集成单元测试。

测试覆盖：
1. ActionCache 缓存 Key 生成：DOM 结构哈希 + 导航目标哈希
2. ActionCache 动态内容剥除：script/style/文本/时间戳/data-* 属性
3. ActionCache Redis 读写：命中 / 未命中 / 写入 / TTL
4. ResilientCaller 缓存集成：命中跳过 LLM / 未命中调 LLM 后写入 / cache_hit 标记上报

使用 fakeredis 替代真实 Redis，无需外部依赖。

@author FinanceRPA
"""

import asyncio
import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.llm.action_cache import ActionCache
from app.llm.resilient_caller import LlmCallRecord, ResilientCaller
from pydantic import BaseModel


# ============================================================
# 测试用 Pydantic 模型
# ============================================================

class SimpleOutput(BaseModel):
    """测试用输出模型。"""
    action: str
    target: str


# ============================================================
# ActionCache 缓存 Key 生成测试
# ============================================================

class TestActionCacheKey:
    """ActionCache 缓存 Key 生成测试。"""

    def test_make_key_format(self):
        """测试 Key 格式：llm:action:{dom_hash}:{goal_hash}。"""
        cache = ActionCache(redis_client=MagicMock())
        key = cache._make_key("<html><body></body></html>", "下载流水")
        assert key.startswith("llm:action:")
        # 格式：llm:action:{16位dom_hash}:{16位goal_hash}
        parts = key.split(":")
        assert len(parts) == 4  # ["llm", "action", dom_hash, goal_hash]
        assert len(parts[2]) == 16  # dom_hash 16 位
        assert len(parts[3]) == 16  # goal_hash 16 位

    def test_same_dom_same_goal_same_key(self):
        """相同 DOM + 相同目标 → 相同 Key。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<html><body><div id='main'></div></body></html>"
        goal = "下载 Q1 流水"
        key1 = cache._make_key(dom, goal)
        key2 = cache._make_key(dom, goal)
        assert key1 == key2

    def test_different_goal_different_key(self):
        """相同 DOM + 不同目标 → 不同 Key。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<html><body></body></html>"
        key1 = cache._make_key(dom, "下载 Q1 流水")
        key2 = cache._make_key(dom, "下载 Q2 流水")
        assert key1 != key2

    def test_different_dom_different_key(self):
        """不同 DOM + 相同目标 → 不同 Key。"""
        cache = ActionCache(redis_client=MagicMock())
        goal = "下载流水"
        key1 = cache._make_key("<html><body><div id='a'></div></body></html>", goal)
        key2 = cache._make_key("<html><body><div id='b'></div></body></html>", goal)
        assert key1 != key2

    def test_goal_hash_case_insensitive(self):
        """导航目标大小写不敏感（normalize 为 lower）。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<html></html>"
        key1 = cache._make_key(dom, "Download Statement")
        key2 = cache._make_key(dom, "download statement")
        assert key1 == key2

    def test_goal_hash_strips_whitespace(self):
        """导航目标首尾空白不影响哈希。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<html></html>"
        key1 = cache._make_key(dom, "  下载流水  ")
        key2 = cache._make_key(dom, "下载流水")
        assert key1 == key2


# ============================================================
# ActionCache 动态内容剥除测试
# ============================================================

class TestStripDynamicContent:
    """ActionCache DOM 动态内容剥除测试。"""

    def test_strip_script_tags(self):
        """script 标签及内容被移除。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<html><body><script>alert('x')</script><div>content</div></body></html>"
        cleaned = cache._strip_dynamic_content(dom)
        assert "<script>" not in cleaned
        assert "alert" not in cleaned
        assert "<div>" in cleaned

    def test_strip_style_tags(self):
        """style 标签及内容被移除。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<html><head><style>.a{color:red}</style></head><body></body></html>"
        cleaned = cache._strip_dynamic_content(dom)
        assert "<style>" not in cleaned
        assert "color:red" not in cleaned

    def test_strip_text_content(self):
        """标签间文本内容被移除（保留标签结构）。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<div>动态文本内容</div><span>用户名</span>"
        cleaned = cache._strip_dynamic_content(dom)
        assert "动态文本内容" not in cleaned
        assert "用户名" not in cleaned
        assert "<div>" in cleaned
        assert "<span>" in cleaned

    def test_strip_data_attributes(self):
        """data-* 属性被移除。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = '<div data-id="12345" data-token="abc">content</div>'
        cleaned = cache._strip_dynamic_content(dom)
        assert "data-id" not in cleaned
        assert "data-token" not in cleaned
        assert "12345" not in cleaned

    def test_strip_timestamps(self):
        """时间戳被移除（ISO 8601 / 日期时间）。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = '<span>2024-01-15T14:30:25Z</span><span>2024/01/15 14:30:25</span>'
        cleaned = cache._strip_dynamic_content(dom)
        assert "2024-01-15T14:30:25Z" not in cleaned
        assert "2024/01/15 14:30:25" not in cleaned

    def test_strip_csrf_tokens(self):
        """CSRF token 属性被移除。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = '<input name="csrf_token" value="abc123def456">'
        cleaned = cache._strip_dynamic_content(dom)
        assert "csrf_token" not in cleaned
        assert "abc123def456" not in cleaned

    def test_strip_html_comments(self):
        """HTML 注释被移除。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = "<div><!-- 动态注释 --><span>text</span></div>"
        cleaned = cache._strip_dynamic_content(dom)
        assert "<!--" not in cleaned
        assert "动态注释" not in cleaned

    def test_strip_nonce_attribute(self):
        """nonce 属性被移除。"""
        cache = ActionCache(redis_client=MagicMock())
        dom = '<script nonce="abc123">code</script><div>text</div>'
        cleaned = cache._strip_dynamic_content(dom)
        assert "nonce" not in cleaned
        assert "abc123" not in cleaned

    def test_same_structure_different_content_same_hash(self):
        """相同结构 + 不同文本内容 → 相同哈希（核心需求）。"""
        cache = ActionCache(redis_client=MagicMock())
        dom1 = '<html><body><div>用户A的流水</div><span>余额: 1000</span></body></html>'
        dom2 = '<html><body><div>用户B的流水</div><span>余额: 5000</span></body></html>'
        hash1 = cache._hash_dom(dom1)
        hash2 = cache._hash_dom(dom2)
        assert hash1 == hash2

    def test_different_structure_different_hash(self):
        """不同结构 → 不同哈希。"""
        cache = ActionCache(redis_client=MagicMock())
        dom1 = '<html><body><div><span>text</span></div></body></html>'
        dom2 = '<html><body><div><div>text</div></div></body></html>'
        hash1 = cache._hash_dom(dom1)
        hash2 = cache._hash_dom(dom2)
        assert hash1 != hash2


# ============================================================
# ActionCache Redis 读写测试（使用 Mock）
# ============================================================

class TestActionCacheRedis:
    """ActionCache Redis 读写测试。"""

    @pytest.mark.asyncio
    async def test_cache_miss(self):
        """缓存未命中返回 None。"""
        mock_redis = AsyncMock()
        mock_redis.get = AsyncMock(return_value=None)
        cache = ActionCache(redis_client=mock_redis)

        result = await cache.get("<html></html>", "下载流水")
        assert result is None
        mock_redis.get.assert_called_once()

    @pytest.mark.asyncio
    async def test_cache_hit(self):
        """缓存命中返回反序列化后的 dict。"""
        cached_data = {"action": "click", "target": "#download-btn"}
        mock_redis = AsyncMock()
        mock_redis.get = AsyncMock(return_value=json.dumps(cached_data, ensure_ascii=False))
        cache = ActionCache(redis_client=mock_redis)

        result = await cache.get("<html></html>", "下载流水")
        assert result == cached_data

    @pytest.mark.asyncio
    async def test_cache_set(self):
        """写缓存调用 Redis SET，带 TTL。"""
        mock_redis = AsyncMock()
        cache = ActionCache(redis_client=mock_redis, ttl=3600)

        await cache.set("<html></html>", "下载流水", {"action": "click"})
        mock_redis.set.assert_called_once()
        args = mock_redis.set.call_args
        assert args.kwargs.get("ex") == 3600  # TTL 传递正确

    @pytest.mark.asyncio
    async def test_cache_set_then_get(self):
        """写入后读取应命中（端到端流程）。"""
        mock_redis = AsyncMock()
        stored = {}

        async def mock_set(key, value, ex=None):
            stored[key] = value

        async def mock_get(key):
            return stored.get(key)

        mock_redis.set = mock_set
        mock_redis.get = mock_get
        cache = ActionCache(redis_client=mock_redis, ttl=86400)

        # 首次查询：未命中
        result1 = await cache.get("<html><body></body></html>", "下载流水")
        assert result1 is None

        # 写入缓存
        await cache.set("<html><body></body></html>", "下载流水", {"action": "click"})

        # 再次查询：命中
        result2 = await cache.get("<html><body></body></html>", "下载流水")
        assert result2 == {"action": "click"}

    @pytest.mark.asyncio
    async def test_cache_get_error_returns_none(self):
        """Redis 异常时 get 返回 None（不阻断主流程）。"""
        mock_redis = AsyncMock()
        mock_redis.get = AsyncMock(side_effect=Exception("Redis connection error"))
        cache = ActionCache(redis_client=mock_redis)

        result = await cache.get("<html></html>", "下载流水")
        assert result is None

    @pytest.mark.asyncio
    async def test_cache_set_error_does_not_raise(self):
        """Redis 异常时 set 不抛异常（仅 warning）。"""
        mock_redis = AsyncMock()
        mock_redis.set = AsyncMock(side_effect=Exception("Redis write error"))
        cache = ActionCache(redis_client=mock_redis)

        # 不应抛异常
        await cache.set("<html></html>", "下载流水", {"action": "click"})

    @pytest.mark.asyncio
    async def test_cache_delete(self):
        """删除缓存调用 Redis DELETE。"""
        mock_redis = AsyncMock()
        cache = ActionCache(redis_client=mock_redis)

        await cache.delete("<html></html>", "下载流水")
        mock_redis.delete.assert_called_once()

    @pytest.mark.asyncio
    async def test_cache_clear_pattern(self):
        """批量清除按模式匹配。"""
        mock_redis = AsyncMock()

        # 模拟 scan_iter 返回 3 个 key
        async def mock_scan_iter(match=None, count=100):
            for key in [b"llm:action:k1", b"llm:action:k2", b"llm:action:k3"]:
                yield key

        mock_redis.scan_iter = mock_scan_iter
        cache = ActionCache(redis_client=mock_redis)

        count = await cache.clear_pattern("llm:action:*")
        assert count == 3
        assert mock_redis.delete.call_count == 3


# ============================================================
# ResilientCaller 缓存集成测试
# ============================================================

class TestResilientCallerCacheIntegration:
    """ResilientCaller + ActionCache 集成测试。"""

    @pytest.mark.asyncio
    async def test_cache_hit_skips_llm_call(self):
        """缓存命中时跳过 LLM 调用。"""
        # Mock LLM（不应被调用）
        mock_llm = AsyncMock()
        # Mock ActionCache（命中）
        mock_cache = AsyncMock()
        mock_cache.get = AsyncMock(return_value={"action": "click", "target": "#btn"})
        # Mock Java client
        mock_java = AsyncMock()

        caller = ResilientCaller(
            llm_callable=mock_llm,
            max_retries=2,
            java_client=mock_java,
            action_cache=mock_cache,
        )

        result = await caller.call(
            prompt="点击下载按钮",
            output_model=SimpleOutput,
            task_id="task-001",
            context_name="executor",
            cache_key_dom="<html><body><button id='download'>下载</button></body></html>",
            cache_key_goal="点击下载按钮",
        )

        assert result.action == "click"
        assert result.target == "#btn"
        # LLM 不应被调用
        mock_llm.assert_not_called()
        # 缓存查询被调用
        mock_cache.get.assert_called_once()
        # 缓存写入不应被调用（命中时不写入）
        mock_cache.set.assert_not_called()
        # Java 上报应标记 cache_hit=True
        mock_java.report_llm_call.assert_called_once()
        call_args = mock_java.report_llm_call.call_args[0][0]
        assert call_args["cache_hit"] is True
        assert call_args["success"] is True

    @pytest.mark.asyncio
    async def test_cache_miss_calls_llm_and_writes_cache(self):
        """缓存未命中 → 调 LLM → 写缓存。"""
        valid_json = json.dumps({"action": "click", "target": "#download"})
        mock_llm = AsyncMock(return_value=valid_json)
        mock_cache = AsyncMock()
        mock_cache.get = AsyncMock(return_value=None)  # 未命中
        mock_java = AsyncMock()

        caller = ResilientCaller(
            llm_callable=mock_llm,
            max_retries=2,
            java_client=mock_java,
            action_cache=mock_cache,
        )

        result = await caller.call(
            prompt="点击下载按钮",
            output_model=SimpleOutput,
            task_id="task-002",
            context_name="executor",
            cache_key_dom="<html><body></body></html>",
            cache_key_goal="点击下载按钮",
        )

        assert result.action == "click"
        # LLM 被调用 1 次
        mock_llm.assert_called_once()
        # 缓存查询被调用
        mock_cache.get.assert_called_once()
        # 缓存写入被调用
        mock_cache.set.assert_called_once()
        # Java 上报 cache_hit=False
        mock_java.report_llm_call.assert_called_once()
        call_args = mock_java.report_llm_call.call_args[0][0]
        assert call_args["cache_hit"] is False

    @pytest.mark.asyncio
    async def test_no_cache_when_cache_key_not_provided(self):
        """未传 cache_key_dom/goal 时不查缓存（向后兼容）。"""
        valid_json = json.dumps({"action": "click", "target": "#btn"})
        mock_llm = AsyncMock(return_value=valid_json)
        mock_cache = AsyncMock()
        mock_java = AsyncMock()

        caller = ResilientCaller(
            llm_callable=mock_llm,
            max_retries=2,
            java_client=mock_java,
            action_cache=mock_cache,
        )

        # 不传 cache_key_dom / cache_key_goal
        result = await caller.call(
            prompt="点击按钮",
            output_model=SimpleOutput,
            task_id="task-003",
        )

        assert result.action == "click"
        # 缓存不应被查询
        mock_cache.get.assert_not_called()
        # 缓存不应被写入
        mock_cache.set.assert_not_called()
        # LLM 被调用
        mock_llm.assert_called_once()

    @pytest.mark.asyncio
    async def test_no_cache_when_action_cache_is_none(self):
        """未注入 ActionCache 时不查缓存（向后兼容）。"""
        valid_json = json.dumps({"action": "click", "target": "#btn"})
        mock_llm = AsyncMock(return_value=valid_json)
        mock_java = AsyncMock()

        caller = ResilientCaller(
            llm_callable=mock_llm,
            max_retries=2,
            java_client=mock_java,
            action_cache=None,  # 不注入缓存
        )

        result = await caller.call(
            prompt="点击按钮",
            output_model=SimpleOutput,
            task_id="task-004",
            cache_key_dom="<html></html>",
            cache_key_goal="点击按钮",
        )

        assert result.action == "click"
        mock_llm.assert_called_once()

    @pytest.mark.asyncio
    async def test_cache_hit_with_partial_key_not_cached(self):
        """只传 cache_key_dom 不传 cache_key_goal → 不查缓存。"""
        mock_llm = AsyncMock(return_value=json.dumps({"action": "a", "target": "t"}))
        mock_cache = AsyncMock()

        caller = ResilientCaller(
            llm_callable=mock_llm,
            action_cache=mock_cache,
        )

        await caller.call(
            prompt="test",
            output_model=SimpleOutput,
            cache_key_dom="<html></html>",
            # cache_key_goal 未传
        )

        mock_cache.get.assert_not_called()

    @pytest.mark.asyncio
    async def test_cache_write_failure_does_not_block(self):
        """缓存写入失败不阻断主流程（仅 warning）。"""
        valid_json = json.dumps({"action": "click", "target": "#btn"})
        mock_llm = AsyncMock(return_value=valid_json)
        # 使用真实 ActionCache + Mock Redis（set 异常）
        mock_redis = AsyncMock()
        mock_redis.get = AsyncMock(return_value=None)
        mock_redis.set = AsyncMock(side_effect=Exception("Redis write error"))
        real_cache = ActionCache(redis_client=mock_redis)

        caller = ResilientCaller(
            llm_callable=mock_llm,
            action_cache=real_cache,
        )

        # 不应抛异常（ActionCache.set 内部 catch 异常）
        result = await caller.call(
            prompt="点击按钮",
            output_model=SimpleOutput,
            task_id="task-005",
            cache_key_dom="<html></html>",
            cache_key_goal="点击按钮",
        )

        assert result.action == "click"

    @pytest.mark.asyncio
    async def test_cache_hit_does_not_write_cache(self):
        """缓存命中后不再写入缓存（避免覆盖）。"""
        mock_llm = AsyncMock()
        mock_cache = AsyncMock()
        mock_cache.get = AsyncMock(return_value={"action": "a", "target": "t"})

        caller = ResilientCaller(
            llm_callable=mock_llm,
            action_cache=mock_cache,
        )

        await caller.call(
            prompt="test",
            output_model=SimpleOutput,
            cache_key_dom="<html></html>",
            cache_key_goal="test",
        )

        mock_cache.get.assert_called_once()
        mock_cache.set.assert_not_called()

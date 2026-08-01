"""LLM Action 缓存（M5.2）。

基于 Redis 的 LLM 调用结果缓存，避免相同页面结构 + 相同导航目标的重复 LLM 调用。

缓存 Key 设计：
  - DOM 结构哈希：剥除动态内容（script/style/文本内容/时间戳/随机 ID）后取 SHA256 前 16 位
  - 导航目标哈希：navigation_goal 取 SHA256 前 16 位
  - 最终 Key：llm:action:{dom_hash}:{goal_hash}

缓存流程：
  1. 查缓存 → 命中直接返回（标记 cache_hit=True）
  2. 未命中 → 调 LLM → 写缓存（TTL 24 小时）
  3. TTL 过期自动失效

@author FinanceRPA
"""

import hashlib
import logging
import re
from typing import Any

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

# 缓存 Key 前缀
_CACHE_PREFIX = "llm:action:"
# 缓存 TTL（秒）：24 小时
_CACHE_TTL = 86400
# 哈希截取长度
_HASH_LENGTH = 16


class ActionCache:
    """LLM Action 缓存读写器。

    职责：
    - 基于 DOM 结构 + 导航目标生成缓存 Key
    - Redis 读写（TTL 24 小时）
    - DOM 动态内容剥除（时间戳、随机 ID、文本内容等）

    使用示例：
        cache = ActionCache()
        # 查缓存
        cached = await cache.get(dom_structure="<html>...</html>", navigation_goal="下载流水")
        if cached:
            return cached  # 命中，跳过 LLM 调用
        # 未命中 → 调 LLM → 写缓存
        result = await llm_call(...)
        await cache.set(dom_structure, navigation_goal, result)
    """

    def __init__(
        self,
        redis_client: aioredis.Redis | None = None,
        ttl: int = _CACHE_TTL,
    ):
        """
        @param redis_client: redis.asyncio.Redis 实例（可选，延迟创建）
        @param ttl: 缓存 TTL（秒），默认 24 小时
        """
        self._redis = redis_client
        self._owns_redis = False
        self._ttl = ttl

    async def _get_redis(self) -> aioredis.Redis:
        """获取或创建 Redis 客户端。"""
        if self._redis is None:
            from app.config import get_settings
            settings = get_settings()
            self._redis = aioredis.from_url(
                settings.redis_url,
                decode_responses=True,
            )
            self._owns_redis = True
            logger.info("ActionCache: 创建 Redis 客户端, url=%s", settings.redis_url)
        return self._redis

    async def close(self) -> None:
        """关闭自行创建的 Redis 客户端。"""
        if self._redis and self._owns_redis:
            await self._redis.aclose()
            self._redis = None
            self._owns_redis = False
            logger.info("ActionCache: 关闭 Redis 客户端")

    async def get(
        self,
        dom_structure: str,
        navigation_goal: str,
    ) -> dict[str, Any] | None:
        """查缓存。

        @param dom_structure: 页面 DOM 结构字符串（HTML / accessibility tree）
        @param navigation_goal: 导航目标（用户意图）
        @return: 命中时返回缓存的 LLM 结果 dict；未命中返回 None
        """
        key = self._make_key(dom_structure, navigation_goal)
        try:
            redis = await self._get_redis()
            cached = await redis.get(key)
            if cached:
                import json
                result = json.loads(cached)
                logger.info(
                    "ActionCache: 缓存命中 [key=%s, goal=%s]",
                    key, navigation_goal[:50],
                )
                return result
            logger.debug("ActionCache: 缓存未命中 [key=%s]", key)
            return None
        except Exception as e:
            logger.warning("ActionCache: 查缓存失败 [key=%s]: %s", key, e)
            return None

    async def set(
        self,
        dom_structure: str,
        navigation_goal: str,
        value: dict[str, Any],
    ) -> None:
        """写缓存。

        @param dom_structure: 页面 DOM 结构字符串
        @param navigation_goal: 导航目标
        @param value: LLM 返回结果（已校验的 dict）
        """
        key = self._make_key(dom_structure, navigation_goal)
        try:
            import json
            redis = await self._get_redis()
            serialized = json.dumps(value, ensure_ascii=False, default=str)
            await redis.set(key, serialized, ex=self._ttl)
            logger.info(
                "ActionCache: 缓存已写入 [key=%s, goal=%s, ttl=%ds, size=%d bytes]",
                key, navigation_goal[:50], self._ttl, len(serialized),
            )
        except Exception as e:
            logger.warning("ActionCache: 写缓存失败 [key=%s]: %s", key, e)

    async def delete(self, dom_structure: str, navigation_goal: str) -> None:
        """删除缓存（手动失效）。"""
        key = self._make_key(dom_structure, navigation_goal)
        try:
            redis = await self._get_redis()
            await redis.delete(key)
            logger.info("ActionCache: 缓存已删除 [key=%s]", key)
        except Exception as e:
            logger.warning("ActionCache: 删缓存失败 [key=%s]: %s", key, e)

    async def clear_pattern(self, pattern: str = "llm:action:*") -> int:
        """批量清除缓存（按模式匹配）。

        @param pattern: Redis key 匹配模式
        @return: 删除的 key 数量
        """
        try:
            redis = await self._get_redis()
            count = 0
            async for key in redis.scan_iter(match=pattern, count=100):
                await redis.delete(key)
                count += 1
            logger.info("ActionCache: 批量清除缓存 %d 条 [pattern=%s]", count, pattern)
            return count
        except Exception as e:
            logger.warning("ActionCache: 批量清除失败 [pattern=%s]: %s", pattern, e)
            return 0

    def _make_key(self, dom_structure: str, navigation_goal: str) -> str:
        """生成缓存 Key：DOM 结构哈希 + 导航目标哈希。

        @param dom_structure: 页面 DOM 结构字符串
        @param navigation_goal: 导航目标
        @return: Redis 缓存 Key
        """
        dom_hash = self._hash_dom(dom_structure)
        goal_hash = self._hash_goal(navigation_goal)
        return f"{_CACHE_PREFIX}{dom_hash}:{goal_hash}"

    def _hash_dom(self, dom_structure: str) -> str:
        """DOM 结构哈希（剥除动态内容后取 SHA256 前 16 位）。

        @param dom_structure: 原始 DOM 字符串
        @return: 16 字符哈希
        """
        cleaned = self._strip_dynamic_content(dom_structure)
        return hashlib.sha256(cleaned.encode("utf-8")).hexdigest()[:_HASH_LENGTH]

    def _hash_goal(self, navigation_goal: str) -> str:
        """导航目标哈希（SHA256 前 16 位）。"""
        normalized = navigation_goal.strip().lower()
        return hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:_HASH_LENGTH]

    def _strip_dynamic_content(self, dom: str) -> str:
        """剥除 DOM 中的动态内容，保留结构骨架。

        移除内容：
        - <script> / <style> / <noscript> 标签及内容
        - HTML 注释
        - 标签间文本内容（保留标签结构 + 属性）
        - data-* 属性值
        - CSRF token
        - 时间戳（多种格式）
        - 随机 nonce 值
        - 多余空白

        @param dom: 原始 DOM 字符串
        @return: 剥除动态内容后的结构骨架
        """
        cleaned = dom

        # 移除 <script>...</script>、<style>...</style>、<noscript>...</noscript>
        cleaned = re.sub(
            r"<(script|style|noscript)[^>]*>.*?</\1>",
            "",
            cleaned,
            flags=re.DOTALL | re.IGNORECASE,
        )

        # 移除 HTML 注释
        cleaned = re.sub(r"<!--.*?-->", "", cleaned, flags=re.DOTALL)

        # 移除标签间文本内容（保留标签结构 + 属性）
        # 匹配 >文本内容< → ><
        cleaned = re.sub(r">[^<]+<", "><", cleaned)

        # 移除 data-* 属性（含值）
        cleaned = re.sub(r'\s+data-[a-zA-Z-]+="[^"]*"', "", cleaned)

        # 移除 CSRF token 相关属性
        cleaned = re.sub(
            r'\s+name="csrf[_-]token"\s+value="[^"]*"',
            "",
            cleaned,
            flags=re.IGNORECASE,
        )
        cleaned = re.sub(
            r'\s+name="_token"\s+value="[^"]*"',
            "",
            cleaned,
            flags=re.IGNORECASE,
        )

        # 移除 nonce 属性
        cleaned = re.sub(r'\s+nonce="[^"]*"', "", cleaned)

        # 移除时间戳（ISO 8601、日期时间、Unix 时间戳）
        cleaned = re.sub(
            r"\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?",
            "",
            cleaned,
        )
        cleaned = re.sub(r"\d{4}/\d{2}/\d{2}\s+\d{2}:\d{2}:\d{2}", "", cleaned)
        cleaned = re.sub(r"\b\d{10}\b", "", cleaned)  # Unix 时间戳

        # 规范化空白
        cleaned = re.sub(r"\s+", " ", cleaned).strip()

        return cleaned

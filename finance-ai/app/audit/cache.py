"""审计日志本地缓存（M7.3）。

Java 后端不可用时，将待上报的审计日志与截图元数据序列化到本地 JSON 文件，
进程重启后不丢失。Java 恢复后由 AuditReporter.flush_cache() 批量上报。

存储格式：每个待上报项一个 JSON 文件，文件名 {timestamp}_{uuid}.json，
内容包含 type（audit_log / screenshot）与 payload。

@from enterprise/audit/cache.py
@author FinanceRPA
"""

import json
import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Any

from app.config import get_settings

logger = logging.getLogger(__name__)


class AuditCache:
    """审计日志本地文件缓存。

    失败的审计日志与截图上传请求序列化为 JSON 文件存入缓存目录，
    支持 save（写入）与 load_all + remove（批量读取 + 清理）。
    """

    def __init__(self, cache_dir: str | None = None):
        """
        @param cache_dir: 缓存目录路径（默认从配置读取）
        """
        self.cache_dir = cache_dir or get_settings().audit_cache_dir

    def save(self, item_type: str, payload: dict[str, Any]) -> str | None:
        """将一个待上报项写入缓存文件。

        @param item_type: 项类型（audit_log / screenshot）
        @param payload: 载荷数据
        @return: 缓存文件路径，失败返回 None
        """
        try:
            os.makedirs(self.cache_dir, exist_ok=True)
            # 文件名：{timestamp}_{uuid}.json，保证唯一与时间序
            ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
            filename = f"{ts}_{uuid.uuid4().hex[:8]}.json"
            filepath = os.path.join(self.cache_dir, filename)

            entry = {
                "type": item_type,
                "payload": payload,
                "cached_at": datetime.now(timezone.utc).isoformat(),
            }
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(entry, f, ensure_ascii=False, default=str)

            logger.info(
                "AuditCache: 缓存写入成功 [type=%s, file=%s]", item_type, filename,
            )
            return filepath
        except Exception as e:
            logger.error(
                "AuditCache: 缓存写入失败 [type=%s, error=%s]", item_type, e,
                exc_info=True,
            )
            return None

    def load_all(self) -> list[dict[str, Any]]:
        """加载所有缓存项（按文件名时间序）。

        @return: 缓存项列表，每项含 type/payload/cached_at/filepath 字段
        """
        items: list[dict[str, Any]] = []
        if not os.path.exists(self.cache_dir):
            return items

        try:
            files = sorted(os.listdir(self.cache_dir))
            for filename in files:
                if not filename.endswith(".json"):
                    continue
                filepath = os.path.join(self.cache_dir, filename)
                try:
                    with open(filepath, "r", encoding="utf-8") as f:
                        entry = json.load(f)
                    entry["filepath"] = filepath
                    items.append(entry)
                except (json.JSONDecodeError, OSError) as e:
                    logger.warning(
                        "AuditCache: 缓存文件读取失败 [file=%s, error=%s]",
                        filename, e,
                    )
        except OSError as e:
            logger.error("AuditCache: 缓存目录读取失败 [dir=%s, error=%s]",
                         self.cache_dir, e)

        logger.info("AuditCache: 加载缓存项 %d 个", len(items))
        return items

    def remove(self, filepath: str) -> None:
        """删除已成功上报的缓存文件。

        @param filepath: 缓存文件路径
        """
        try:
            os.remove(filepath)
            logger.debug("AuditCache: 缓存文件已删除 [file=%s]", filepath)
        except OSError as e:
            logger.warning(
                "AuditCache: 缓存文件删除失败 [file=%s, error=%s]", filepath, e,
            )

    def count(self) -> int:
        """返回当前缓存项数量。

        @return: 缓存文件数
        """
        if not os.path.exists(self.cache_dir):
            return 0
        try:
            return sum(
                1 for f in os.listdir(self.cache_dir) if f.endswith(".json")
            )
        except OSError:
            return 0

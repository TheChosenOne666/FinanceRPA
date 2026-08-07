#!/usr/bin/env python3
"""FinanceRPA 演示数据导入脚本。

将 `finance-backend/src/main/resources/db/seed/demo_data.sql`
导入到 .env 中配置的 PostgreSQL 库（默认 finrpa）。

SQL 内容使用 `WHERE NOT EXISTS` 做幂等插入，重复执行不会冲突。

导入策略（按可用性自动选择）：
  1. 优先使用 `psql` 命令行客户端（需安装 PostgreSQL 客户端）；
  2. 回退到 `psycopg2`（pip install psycopg2-binary）。

环境变量（读取仓库根目录 .env，缺失时使用默认值）：
  POSTGRES_HOST / POSTGRES_PORT / POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD

用法：
  python scripts/seed_demo_data.py
  POSTGRES_DB=other python scripts/seed_demo_data.py
"""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

# ---- 加载仓库根目录 .env（极简解析，仅取 KEY=VALUE，忽略注释与空行）----
ROOT = Path(__file__).resolve().parent.parent


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        key, val = key.strip(), val.strip()
        # 不覆盖已存在于环境中的变量
        os.environ.setdefault(key, val)


load_dotenv(ROOT / ".env")

SQL_PATH = ROOT / "finance-backend" / "src" / "main" / "resources" / "db" / "seed" / "demo_data.sql"

PG_HOST = os.environ.get("POSTGRES_HOST", "localhost")
PG_PORT = os.environ.get("POSTGRES_PORT", "5432")
PG_DB = os.environ.get("POSTGRES_DB", "finrpa")
PG_USER = os.environ.get("POSTGRES_USER", "finrpa")
PG_PASSWORD = os.environ.get("POSTGRES_PASSWORD", "finrpa")


def fail(msg: str) -> None:
    print(f"[ERROR] {msg}", file=sys.stderr)
    sys.exit(1)


def seed_via_psql() -> None:
    if not SQL_PATH.exists():
        fail(f"未找到 seed SQL: {SQL_PATH}")
    env = dict(os.environ, PGPASSWORD=PG_PASSWORD, PGSSLMODE="prefer")
    cmd = [
        "psql",
        "-h", PG_HOST,
        "-p", str(PG_PORT),
        "-U", PG_USER,
        "-d", PG_DB,
        "-f", str(SQL_PATH),
        "-v", "ON_ERROR_STOP=1",
    ]
    print(f"[INFO] 使用 psql 导入: {SQL_PATH}")
    try:
        subprocess.run(cmd, env=env, check=True)
    except FileNotFoundError:
        return False  # psql 不可用，交由调用方回退
    except subprocess.CalledProcessError as e:
        fail(f"psql 执行失败（退出码 {e.returncode}），请检查 SQL 与数据库连接。")
    return True


def seed_via_psycopg2() -> None:
    try:
        import psycopg2  # type: ignore
    except ImportError:
        fail("未安装 psql 且未安装 psycopg2，请执行 `pip install psycopg2-binary` 后重试。")
    if not SQL_PATH.exists():
        fail(f"未找到 seed SQL: {SQL_PATH}")
    sql = SQL_PATH.read_text(encoding="utf-8")
    print(f"[INFO] 使用 psycopg2 导入: {SQL_PATH}")
    conn = None
    try:
        conn = psycopg2.connect(
            host=PG_HOST, port=PG_PORT, dbname=PG_DB,
            user=PG_USER, password=PG_PASSWORD,
        )
        conn.autocommit = True
        with conn.cursor() as cur:
            cur.execute(sql)
    except psycopg2.Error as e:
        fail(f"psycopg2 执行失败: {e}")
    finally:
        if conn is not None:
            conn.close()


def main() -> None:
    print(f"[INFO] 目标库: postgresql://{PG_USER}@{PG_HOST}:{PG_PORT}/{PG_DB}")
    # 优先 psql
    if seed_via_psql() is not False:
        print("[OK] 演示数据导入完成（psql）。")
        return
    # 回退 psycopg2
    seed_via_psycopg2()
    print("[OK] 演示数据导入完成（psycopg2）。")


if __name__ == "__main__":
    main()

#!/usr/bin/env bash
#
# scripts/backup.sh
# FinanceRPA 全量备份脚本（PG dump + MinIO 同步）
#
# 备份内容：
#   1. PostgreSQL 逻辑备份（pg_dump --clean --if-exists --create）
#   2. MinIO 对象存储桶同步（mc mirror，全量镜像）
#
# 备份落盘位置：
#   ./backups/postgres/<db>_<timestamp>.sql.gz
#   ./backups/minio/<timestamp>/
# 保留策略：本地保留最近 7 份（older 自动清理）。
#
# 凭据来源：仓库根目录 .env（POSTGRES_* / MINIO_*）。
#
# 依赖（按可用性自动选择）：
#   - PostgreSQL 客户端（pg_dump）或 docker（docker exec 容器内 pg_dump）
#   - MinIO Client（mc）或 AWS CLI（aws s3 sync）
#
# 用法：
#   ./scripts/backup.sh
#   PROFILE=prod ./scripts/backup.sh

set -uo pipefail

PROFILE="${PROFILE:-dev}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
BACKUP_DIR="$ROOT/backups"
PG_BACKUP_DIR="$BACKUP_DIR/postgres"
MINIO_BACKUP_DIR="$BACKUP_DIR/minio"
KEEP=7

mkdir -p "$PG_BACKUP_DIR" "$MINIO_BACKUP_DIR"

# ---- 读取 .env（仅简单 KEY=VALUE）----
if [[ -f "$ENV_FILE" ]]; then
  while IFS='=' read -r k v; do
    [[ -z "$k" || "$k" == \#* || "$v" == "" ]] && continue
    case "$k" in
      POSTGRES_*|MINIO_*|REDIS_*|AI_INTERNAL_TOKEN|VOLCENGINE_API_KEY) export "$k=$v" ;;
    esac
  done < "$ENV_FILE"
fi

PG_HOST="${POSTGRES_HOST:-localhost}"
PG_PORT="${POSTGRES_PORT:-5432}"
PG_DB="${POSTGRES_DB:-finrpa}"
PG_USER="${POSTGRES_USER:-finrpa}"
PG_PASSWORD="${POSTGRES_PASSWORD:-finrpa}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_BUCKET="${MINIO_BUCKET:-finrpa}"

TS="$(date +%Y%m%d_%H%M%S)"

have_cmd() { command -v "$1" >/dev/null 2>&1; }

echo "=================================================="
echo " FinanceRPA 备份 (profile=${PROFILE})  @ ${TS}"
echo "=================================================="

# ---------- 1. PostgreSQL 备份 ----------
echo "==> [1/2] PostgreSQL 备份"
PG_DUMP_OPTS="--clean --if-exists --create --no-owner --no-privileges"
OUT_FILE="$PG_BACKUP_DIR/${PG_DB}_${TS}.sql"
GZ_FILE="${OUT_FILE}.gz"

export PGPASSWORD="$PG_PASSWORD"

pg_dump_local() {
  if ! have_cmd pg_dump; then
    return 1
  fi
  echo "    [INFO] 使用本地 pg_dump 连接 ${PG_HOST}:${PG_PORT}/${PG_DB}"
  pg_dump $PG_DUMP_OPTS -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" \
    | gzip > "$GZ_FILE"
  return $?
}

pg_dump_docker() {
  if ! have_cmd docker; then
    return 1
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx finrpa-postgres; then
    return 1
  fi
  echo "    [INFO] 使用容器内 pg_dump（finrpa-postgres）"
  docker exec finrpa-postgres sh -c \
    "PGPASSWORD='$PG_PASSWORD' pg_dump $PG_DUMP_OPTS -U '$PG_USER' -d '$PG_DB'" \
    | gzip > "$GZ_FILE"
  return $?
}

if pg_dump_local || pg_dump_docker; then
  if [[ -s "$GZ_FILE" ]]; then
    echo "    [OK] PG 备份完成: $GZ_FILE ($(du -h "$GZ_FILE" | cut -f1))"
  else
    echo "    [FAIL] PG 备份文件为空，请检查数据库连接与权限。" >&2
    rm -f "$GZ_FILE"
  fi
else
  echo "    [FAIL] 未找到 pg_dump 且容器 finrpa-postgres 不可用，跳过 PG 备份。" >&2
fi

# ---------- 2. MinIO 备份 ----------
echo "==> [2/2] MinIO 对象存储备份"
DEST="$MINIO_BACKUP_DIR/$TS"
mkdir -p "$DEST"

mc_backup() {
  if ! have_cmd mc; then
    return 1
  fi
  local alias="finrpa-backup"
  echo "    [INFO] 使用 mc mirror 同步桶 ${MINIO_BUCKET}"
  mc alias set "$alias" "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null 2>&1 || return 1
  mc mirror "$alias/$MINIO_BUCKET" "$DEST" >/dev/null 2>&1
  return $?
}

aws_backup() {
  if ! have_cmd aws; then
    return 1
  fi
  echo "    [INFO] 使用 aws s3 sync 同步桶 ${MINIO_BUCKET}"
  AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
  AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
  AWS_ENDPOINT_URL="$MINIO_ENDPOINT" \
  aws s3 sync "s3://${MINIO_BUCKET}" "$DEST" >/dev/null 2>&1
  return $?
}

if mc_backup || aws_backup; then
  echo "    [OK] MinIO 备份完成: $DEST"
else
  echo "    [FAIL] 未找到 mc 或 aws，跳过 MinIO 备份（请安装 minio client 或 aws cli）。" >&2
fi

# ---------- 清理旧备份（保留最近 KEEP 份）----------
echo "==> 清理旧备份（保留最近 ${KEEP} 份）"
(
  cd "$PG_BACKUP_DIR" 2>/dev/null && ls -1t *.sql.gz 2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -f
  cd "$MINIO_BACKUP_DIR" 2>/dev/null && ls -1t 2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -rf
)

echo "--------------------------------------------------"
echo " 备份目录: $BACKUP_DIR"
echo "=================================================="

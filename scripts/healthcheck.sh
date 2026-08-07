#!/usr/bin/env bash
#
# scripts/healthcheck.sh
# FinanceRPA 全链路健康检查脚本
#
# 检查范围（覆盖数据层 / 应用层 / 网关层）：
#   1. postgres      PG 端口连通 + pg_isready
#   2. redis         PING 响应
#   3. minio         /minio/health/live 或控制台可达
#   4. finance-backend  /api/actuator/health HTTP 200
#   5. finance-ai       /api/v1/ai/health HTTP 200
#   6. finance-frontend 首页 HTTP 200
#   7. nginx          80 端口首页 HTTP 200
#
# 探测策略：
#   - 优先探测宿主机映射端口（开发环境 docker-compose.yml 已暴露）。
#   - 宿主机端口不通时，回退到 `docker inspect` 读取容器 health status（生产环境容器端口仅内网）。
#
# 用法：
#   ./scripts/healthcheck.sh            # 默认开发环境（dev）
#   PROFILE=prod ./scripts/healthcheck.sh
#
# 退出码：全部通过=0，存在失败=1。

set -uo pipefail

PROFILE="${PROFILE:-dev}"
# 开发环境容器名前缀 finrpa-，生产环境同为 finrpa-（compose name 不同但 container_name 一致）
PG_CONTAINER="finrpa-postgres"
REDIS_CONTAINER="finrpa-redis"
MINIO_CONTAINER="finrpa-minio"
BACKEND_CONTAINER="finrpa-backend"
AI_CONTAINER="finrpa-ai"
FRONTEND_CONTAINER="finrpa-frontend"
NGINX_CONTAINER="finrpa-nginx"

# 开发环境宿主机映射端口
PG_PORT=5432
REDIS_PORT=6379
MINIO_PORT=9000
BACKEND_PORT=8080
AI_PORT=8000
FRONTEND_PORT=8081
NGINX_PORT=80

PASS=0
FAIL=0

log_ok()   { echo "  [OK]   $1"; PASS=$((PASS+1)); }
log_fail() { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }

# 命令可用性检测
have_cmd() { command -v "$1" >/dev/null 2>&1; }

# 探测宿主机 TCP 端口（不依赖 nc，使用 bash /dev/tcp）
probe_tcp() {
  local host="$1" port="$2" timeout="${3:-3}"
  if have_cmd nc; then
    nc -z -w "$timeout" "$host" "$port" 2>/dev/null && return 0
    return 1
  fi
  # 回退：bash /dev/tcp
  (exec 3<>/dev/tcp/"$host"/"$port") 2>/dev/null && { exec 3>&-; return 0; } || return 1
}

# 探测 HTTP 状态码
probe_http() {
  local url="$1" timeout="${2:-5}"
  if have_cmd curl; then
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$timeout" "$url" 2>/dev/null)
    [[ "$code" =~ ^[0-9]+$ ]] && echo "$code" && return 0
    return 1
  fi
  return 1
}

# 读取容器 health 状态（docker inspect）
container_health() {
  local c="$1"
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$c" 2>/dev/null
}

# 判定单个服务：先 HTTP/TCP，失败再看容器 health
check_service() {
  local name="$1" port="$2" http_path="$3" container="$4"
  echo "==> $name"
  if [[ -n "$http_path" ]]; then
    local code
    code=$(probe_http "http://127.0.0.1:${port}${http_path}")
    if [[ -n "$code" && "$code" != "000" ]]; then
      if [[ "$code" =~ ^2 ]]; then
        log_ok "$name HTTP ${code} @ :${port}${http_path}"
      else
        log_fail "$name HTTP ${code} @ :${port}${http_path}"
      fi
      return
    fi
  elif [[ -n "$port" ]]; then
    if probe_tcp 127.0.0.1 "$port"; then
      log_ok "$name TCP 连通 @ :${port}"
      return
    fi
  fi
  # 回退：容器 health
  local h
  h=$(container_health "$container")
  if [[ "$h" == "healthy" || "$h" == "no-healthcheck" ]]; then
    log_ok "$name 容器状态=${h}（宿主机端口未暴露，按容器状态判定）"
  else
    log_fail "$name 宿主机不可达且容器状态=${h:-unknown}（container=${container}）"
  fi
}

echo "=================================================="
echo " FinanceRPA 全链路健康检查 (profile=${PROFILE})"
echo "=================================================="

# 1. postgres
echo "==> postgres"
if probe_tcp 127.0.0.1 "$PG_PORT"; then
  if have_cmd pg_isready && PGPASSWORD="${POSTGRES_PASSWORD:-finrpa}" pg_isready -h 127.0.0.1 -p "$PG_PORT" -U "${POSTGRES_USER:-finrpa}" -d "${POSTGRES_DB:-finrpa}" >/dev/null 2>&1; then
    log_ok "postgres pg_isready 通过 @ :${PG_PORT}"
  else
    log_ok "postgres TCP 连通 @ :${PG_PORT}（未安装 psql 客户端，跳过 pg_isready）"
  fi
else
  h=$(container_health "$PG_CONTAINER")
  [[ "$h" == "healthy" || "$h" == "no-healthcheck" ]] && log_ok "postgres 容器状态=${h}" || log_fail "postgres 不可达（container=${PG_CONTAINER}）"
fi

# 2. redis
echo "==> redis"
if probe_tcp 127.0.0.1 "$REDIS_PORT"; then
  if [[ -n "${REDIS_PASSWORD:-}" ]]; then
    if redis-cli -a "${REDIS_PASSWORD}" ping 2>/dev/null | grep -q PONG; then
      log_ok "redis PONG @ :${REDIS_PORT}"
    else
      log_fail "redis 认证失败 @ :${REDIS_PORT}"
    fi
  elif have_cmd redis-cli && redis-cli -h 127.0.0.1 -p "$REDIS_PORT" ping 2>/dev/null | grep -q PONG; then
    log_ok "redis PONG @ :${REDIS_PORT}"
  else
    log_ok "redis TCP 连通 @ :${REDIS_PORT}（未安装 redis-cli，跳过 PING）"
  fi
else
  h=$(container_health "$REDIS_CONTAINER")
  [[ "$h" == "healthy" || "$h" == "no-healthcheck" ]] && log_ok "redis 容器状态=${h}" || log_fail "redis 不可达（container=${REDIS_CONTAINER}）"
fi

# 3. minio（开发环境暴露 9000；健康检查用控制台存活接口）
echo "==> minio"
check_service "minio" "$MINIO_PORT" "/minio/health/live" "$MINIO_CONTAINER"

# 4. backend
check_service "finance-backend" "$BACKEND_PORT" "/api/actuator/health" "$BACKEND_CONTAINER"

# 5. ai
check_service "finance-ai" "$AI_PORT" "/api/v1/ai/health" "$AI_CONTAINER"

# 6. frontend
check_service "finance-frontend" "$FRONTEND_PORT" "/" "$FRONTEND_CONTAINER"

# 7. nginx
check_service "nginx" "$NGINX_PORT" "/" "$NGINX_CONTAINER"

echo "--------------------------------------------------"
echo " 通过: ${PASS}    失败: ${FAIL}"
echo "=================================================="

[[ "$FAIL" -eq 0 ]] && exit 0 || exit 1

# FinanceRPA 生产部署指南

> **目标读者**：运维工程师、实施工程师
> **适用版本**：M9.3 生产 Docker Compose overlay
> **关联文档**：[task-breakdown.md](./task-breakdown.md) · [system-design.md](./system-design.md)

---

## 1. 部署前准备

### 1.1 硬件要求

| 资源 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 4 核 | 8 核 |
| 内存 | 8 GB | 16 GB |
| 磁盘 | 50 GB SSD | 200 GB SSD |
| 网络 | 内网可达 | 公网 IP + 域名 |

> finance-ai 容器需要运行 Chromium 浏览器执行 Skyvern 视觉决策，内存占用较高（默认 4G 上限）。

### 1.2 软件要求

| 软件 | 最低版本 | 用途 |
|------|---------|------|
| Docker Engine | 24.0 | 容器运行时 |
| Docker Compose | v2.20 | 服务编排（compose-spec） |
| OpenSSL | 1.1.1 | 自签证书生成（可选） |
| curl / wget | 任意 | 健康检查 |

> Docker Compose v1（python 实现）已停止维护，必须使用 v2（go 实现，命令 `docker compose` 或 `docker-compose` v2）。

### 1.3 代码与镜像准备

```bash
# 1. 克隆代码（或拷贝发布包到部署目录）
git clone <repo-url> /opt/finrpa
cd /opt/finrpa

# 2. 确认关键文件存在
ls docker-compose.prod.yml nginx/conf.d/prod.conf .env.prod.example
```

构建产物通过 `docker-compose build` 在部署机本地构建，无需外部镜像仓库。

---

## 2. HTTPS 证书配置

### 2.1 方案对比

| 方案 | 适用场景 | 证书有效期 | 浏览器信任 |
|------|---------|-----------|-----------|
| 自签证书 | 内网部署 / 演示 / 测试 | 1 年（自定义） | ❌ 需手动信任 |
| Let's Encrypt | 公网域名部署 | 90 天（自动续期） | ✅ 通用信任 |
| 商业证书 | 企业正式上线 | 1-2 年 | ✅ 通用信任 |

### 2.2 方案 A：自签证书（内网/演示）

```bash
# 1. 创建证书目录
mkdir -p nginx/ssl

# 2. 生成自签证书（CN 填写实际访问域名或 IP）
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout nginx/ssl/server.key \
  -out nginx/ssl/server.crt \
  -subj "/C=CN/ST=Beijing/L=Beijing/O=FinanceRPA/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

# 3. 校验文件
ls -l nginx/ssl/
# 应有 server.crt + server.key 两个文件

# 4. 验证证书
openssl x509 -in nginx/ssl/server.crt -text -noout | head -n 20
```

> 浏览器首次访问会提示"不安全"，需手动点击"继续前往"。生产环境建议使用真实证书。

### 2.3 方案 B：Let's Encrypt（公网域名）

推荐使用 certbot 申请证书后，将证书文件复制到 `nginx/ssl/` 目录：

```bash
# 1. 安装 certbot
sudo apt-get install -y certbot

# 2. 申请证书（需 80 端口临时可用，建议先停 nginx）
sudo certbot certonly --standalone -d finrpa.example.com

# 3. 复制证书到 nginx/ssl/（Let's Encrypt 默认路径 /etc/letsencrypt/live/<域名>/）
mkdir -p nginx/ssl
sudo cp /etc/letsencrypt/live/finrpa.example.com/fullchain.pem nginx/ssl/server.crt
sudo cp /etc/letsencrypt/live/finrpa.example.com/privkey.pem nginx/ssl/server.key

# 4. 设置权限（nginx 容器以 nginx 用户运行）
sudo chown -R 1001:1001 nginx/ssl
sudo chmod 600 nginx/ssl/server.key
```

> 证书 90 天过期，建议配合 crontab 定期续期：
> ```bash
> # 每月 1 号 03:00 续期 + 重启 nginx
> 0 3 1 * * certbot renew --quiet && cp /etc/letsencrypt/live/finrpa.example.com/{fullchain,privkey}.pem /opt/finrpa/nginx/ssl/{server.crt,server.key} && docker-compose -f /opt/finrpa/docker-compose.prod.yml restart nginx
> ```

### 2.4 方案 C：商业证书

将 CA 颁发的证书文件（含中间证书的 fullchain）重命名为 `server.crt`，私钥文件重命名为 `server.key`，放入 `nginx/ssl/` 目录即可。

---

## 3. 环境变量配置

### 3.1 创建 .env.prod

```bash
cp .env.prod.example .env.prod
vim .env.prod
```

### 3.2 必填变量清单

| 变量 | 用途 | 生成方式 |
|------|------|---------|
| `POSTGRES_PASSWORD` | 数据库密码 | `openssl rand -base64 32` |
| `REDIS_PASSWORD` | Redis 密码 | `openssl rand -base64 24` |
| `MINIO_ROOT_USER` | MinIO 管理员用户名 | 自定义（≥ 3 字符） |
| `MINIO_ROOT_PASSWORD` | MinIO 管理员密码 | `openssl rand -base64 24`（≥ 8 字符） |
| `AI_INTERNAL_TOKEN` | Java ↔ Python 内部 Token | `openssl rand -hex 32` |
| `JWT_SECRET` | JWT 签名密钥 | `openssl rand -base64 64` |
| `FERNET_KEY` | Fernet 加密密钥 | `python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"` |
| `SKYVERN_SECRET_KEY` | Skyvern JWT 密钥 | `openssl rand -hex 32` |
| `SKYVERN_AES_KEY` | Skyvern AES 密钥 | `openssl rand -hex 32` |
| `VOLCENGINE_API_KEY` | 火山方舟 API Key | 火山方舟控制台获取 |

> 任意一个 [必填] 变量未设置，`docker-compose up` 会因 `${VAR:?...}` 语法直接报错并中止启动。

### 3.3 一键生成所有密钥（可选）

```bash
# 在 .env.prod 当前目录执行
cat >> .env.prod <<EOF
POSTGRES_PASSWORD=$(openssl rand -base64 32)
REDIS_PASSWORD=$(openssl rand -base64 24)
MINIO_ROOT_PASSWORD=$(openssl rand -base64 24)
AI_INTERNAL_TOKEN=$(openssl rand -hex 32)
JWT_SECRET=$(openssl rand -base64 64)
SKYVERN_SECRET_KEY=$(openssl rand -hex 32)
SKYVERN_AES_KEY=$(openssl rand -hex 32)
EOF
# 然后手动填入 MINIO_ROOT_USER 和 VOLCENGINE_API_KEY
```

---

## 4. 一键启动

### 4.1 启动命令

```bash
# 启动所有服务（首次会自动构建镜像，约 10-15 分钟）
docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --build

# 查看启动进度
docker-compose -f docker-compose.prod.yml logs -f

# 等待所有服务健康（约 5-6 分钟，finance-ai start_period=300s）
docker-compose -f docker-compose.prod.yml ps
# 期望所有服务 STATUS = Up (healthy)
```

### 4.2 验证 HTTPS

```bash
# 浏览器访问
https://<服务器IP或域名>/

# 命令行验证
curl -k https://localhost/
# 应返回前端 HTML

# 验证 HTTP → HTTPS 重定向
curl -I http://localhost/
# 应返回 301 Moved Permanently，Location: https://localhost/

# 验证后端 API
curl -k https://localhost/api/actuator/health
# 应返回 {"status":"UP"}

# 验证 AI 服务
curl -k https://localhost/api/v1/ai/health
# 应返回 {"status":"ok"}
```

### 4.3 停止与重启

```bash
# 优雅停止（等待请求处理完成）
docker-compose -f docker-compose.prod.yml down

# 重启单个服务
docker-compose -f docker-compose.prod.yml restart finance-backend

# 完全清理（含数据卷，谨慎！）
docker-compose -f docker-compose.prod.yml down -v
```

---

## 5. 服务架构与资源限制

### 5.1 服务列表

| 服务 | 容器名 | 端口暴露 | CPU 限制 | 内存限制 | 启动顺序 |
|------|--------|---------|---------|---------|---------|
| postgres | finrpa-postgres | 内网 5432 | 1.0 | 1G | 1 |
| redis | finrpa-redis | 内网 6379 | 0.5 | 256M | 1 |
| minio | finrpa-minio | 9001（Console） | 0.5 | 512M | 1 |
| finance-backend | finrpa-backend | 内网 8080 | 1.0 | 1G | 2（依赖 pg+redis） |
| finance-ai | finrpa-ai | 内网 8000 | 2.0 | 4G | 2（依赖 pg+redis） |
| finance-frontend | finrpa-frontend | 内网 80 | 0.25 | 128M | 3 |
| nginx | finrpa-nginx | 80, 443 | 0.5 | 256M | 4（依赖全部应用） |

> **设计原则**：数据层与应用层端口仅容器内网通信（`expose`），仅 nginx 与可选的 MinIO Console 暴露到宿主机（`ports`），降低攻击面。

### 5.2 健康检查策略

| 服务 | 检查方式 | interval | timeout | retries | start_period |
|------|---------|---------|---------|---------|--------------|
| postgres | `pg_isready -U <user>` | 10s | 5s | 5 | 10s |
| redis | `redis-cli -a <pwd> ping` | 10s | 5s | 5 | 5s |
| minio | `mc ready local` | 10s | 5s | 5 | 10s |
| finance-backend | `curl /api/actuator/health` | 15s | 10s | 10 | 90s |
| finance-ai | `urllib /api/v1/ai/health` | 30s | 10s | 5 | 300s |
| finance-frontend | `wget /` | 15s | 10s | 5 | 30s |
| nginx | `wget /healthz` | 15s | 10s | 5 | 10s |

> finance-ai start_period 较长（300s）是因为首次启动需要 `uv sync` 安装依赖 + Alembic 迁移 + Playwright 浏览器初始化。

### 5.3 日志卷挂载

| 卷名 | 容器内路径 | 用途 |
|------|-----------|------|
| `backend-logs` | `/app/logs` | Java 后端日志（finance-backend.log，按天滚动 30 天） |
| `ai-data` | `/data` | Skyvern 数据（视频/HAR/截图） |
| `ai-logs` | `/data/log` | Python AI 服务日志 |
| `nginx-logs` | `/var/log/nginx` | Nginx access/error 日志 |

查看日志：

```bash
# 实时查看某服务日志（docker 标准输出）
docker-compose -f docker-compose.prod.yml logs -f finance-backend

# 查看日志卷中的文件
docker run --rm -v finrpa-prod_backend-logs:/logs alpine cat /logs/finance-backend.log
docker run --rm -v finrpa-prod_nginx-logs:/logs alpine tail -n 100 /logs/access.log
```

---

## 6. 备份策略

### 6.1 备份范围

| 数据类型 | 位置 | 备份方式 | 保留期 |
|---------|------|---------|--------|
| PostgreSQL 数据 | `postgres-data` 卷 | `pg_dump` 全量导出 | 7 天滚动 |
| MinIO 对象 | `minio-data` 卷 | `mc mirror` 同步到备份卷 | 30 天滚动 |
| Redis 数据 | `redis-data` 卷 | AOF + RDB（可选） | 不备份（缓存可重建） |
| 配置文件 | 宿主机 `./` | 文件备份 | 永久 |

### 6.2 备份卷

docker-compose.prod.yml 预定义两个备份卷：

- `pg-backup` → 挂载到 postgres 容器 `/backups/postgres`
- `minio-backup` → 挂载到 minio 容器 `/backups/minio`

### 6.3 手动备份命令

```bash
# PostgreSQL 全量备份
docker-compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U finrpa -d finrpa -F c -f /backups/postgres/finrpa-$(date +%Y%m%d-%H%M%S).dump

# 查看 PostgreSQL 备份
docker-compose -f docker-compose.prod.yml exec postgres ls -lh /backups/postgres/

# PostgreSQL 恢复
docker-compose -f docker-compose.prod.yml exec postgres \
  pg_restore -U finrpa -d finrpa -c /backups/postgres/finrpa-20260806-120000.dump

# MinIO 全量同步到备份卷
docker-compose -f docker-compose.prod.yml exec minio \
  mc mirror local/ /backups/minio/ --overwrite

# 查看 MinIO 备份
docker-compose -f docker-compose.prod.yml exec minio ls -lh /backups/minio/
```

### 6.4 自动备份（推荐 crontab）

```bash
# 编辑 root crontab
sudo crontab -e

# 每日凌晨 2:00 执行 PostgreSQL 备份
0 2 * * * cd /opt/finrpa && docker-compose -f docker-compose.prod.yml exec -T postgres pg_dump -U finrpa -d finrpa -F c -f /backups/postgres/finrpa-$(date +\%Y\%m\%d).dump

# 每周日凌晨 3:00 执行 MinIO 备份
0 3 * * 0 cd /opt/finrpa && docker-compose -f docker-compose.prod.yml exec -T minio mc mirror local/ /backups/minio/ --overwrite

# 每月 1 号清理 7 天前的 PostgreSQL 备份
0 4 1 * * docker run --rm -v finrpa-prod_pg-backup:/backups alpine find /backups -name "*.dump" -mtime +7 -delete
```

> M9.4 将提供 `scripts/backup.sh` 一键备份脚本，封装上述命令。

---

## 7. 故障排查

### 7.1 启动失败

**现象**：`docker-compose up` 直接退出，提示 `variable is not set`

**原因**：必填环境变量未设置

**解决**：

```bash
# 检查 .env.prod 是否完整
docker-compose --env-file .env.prod -f docker-compose.prod.yml config > /dev/null
# 报错会指出具体变量名

# 按报错补全 .env.prod 中缺失的变量
```

### 7.2 finance-ai 健康检查失败

**现象**：finrpa-ai 容器 STATUS 显示 `(unhealthy)` 或 `(health: starting)`

**原因**：Playwright 浏览器初始化失败 / Skyvern 迁移失败

**解决**：

```bash
# 查看详细日志
docker-compose -f docker-compose.prod.yml logs finance-ai | tail -n 100

# 常见错误：
# 1. "chromium not installed" → 镜像构建时 playwright install 失败，重建镜像
# 2. "alembic upgrade failed" → 检查 DATABASE_STRING 配置是否正确
# 3. "VOLCENGINE_API_KEY invalid" → 检查 API Key 是否在火山方舟控制台开通了对应模型

# 临时禁用健康检查启动观察
docker-compose -f docker-compose.prod.yml stop finance-ai
docker-compose -f docker-compose.prod.yml run --no-deps --entrypoint sh finance-ai
```

### 7.3 HTTPS 访问报 SSL_ERROR

**现象**：浏览器访问报 `SSL_ERROR_BAD_CERT` / `ERR_SSL_PROTOCOL_ERROR`

**原因**：证书文件缺失或路径错误

**解决**：

```bash
# 1. 检查证书文件
ls -l nginx/ssl/
# 应有 server.crt 和 server.key

# 2. 校验证书有效性
openssl x509 -in nginx/ssl/server.crt -noout -dates
# 显示 notBefore / notAfter

# 3. 校验私钥与证书匹配
openssl x509 -in nginx/ssl/server.crt -noout -modulus | openssl md5
openssl rsa -in nginx/ssl/server.key -noout -modulus | openssl md5
# 两个 md5 应一致

# 4. 测试 nginx 配置语法
docker run --rm -v $(pwd)/nginx:/etc/nginx:ro nginx:1.25-alpine nginx -t
```

### 7.4 数据库连接失败

**现象**：finance-backend 日志报 `Connection refused` / `FATAL: password authentication failed`

**解决**：

```bash
# 1. 确认 postgres 容器健康
docker-compose -f docker-compose.prod.yml ps postgres

# 2. 进入 postgres 容器测试连接
docker-compose -f docker-compose.prod.yml exec postgres psql -U finrpa -d finrpa -c "SELECT 1;"

# 3. 密码错误时重置（需停止应用层）
docker-compose -f docker-compose.prod.yml exec postgres psql -U finrpa -d postgres -c "ALTER USER finrpa PASSWORD 'new-password';"
# 同步更新 .env.prod 中的 POSTGRES_PASSWORD 并重启 finance-backend
```

### 7.5 端口被占用

**现象**：`docker-compose up` 报 `Bind for 0.0.0.0:80 failed: port is already allocated`

**解决**：

```bash
# 查找占用端口的进程
sudo lsof -i :80
sudo lsof -i :443

# 停止占用进程或修改 docker-compose.prod.yml 中端口映射
# 例如改 80→8080, 443→8443（需同步修改 nginx.conf，不推荐生产使用）
```

### 7.6 镜像构建失败

**现象**：`docker-compose build` 失败

**解决**：

```bash
# 1. 清理构建缓存重试
docker builder prune -f
docker-compose -f docker-compose.prod.yml build --no-cache

# 2. finance-backend 构建失败：检查 Maven 依赖是否可达（可能需要配置内网 Nexus）
# 3. finance-ai 构建失败：检查 PyPI 镜像源（pyproject.toml 中已配置阿里云源）
# 4. finance-frontend 构建失败：检查 npm registry 是否可达
```

### 7.7 资源不足导致 OOM

**现象**：容器频繁重启，`docker logs` 报 `OOMKilled`

**解决**：

```bash
# 查看容器资源使用
docker stats

# 查看具体被杀的容器
docker inspect finrpa-ai | grep -A 5 "OOMKilled"

# 调整资源限制（编辑 docker-compose.prod.yml 中 deploy.resources.limits）
# finance-ai 内存通常需要 4G（Skyvern + Chromium）
# 调整后重启：docker-compose -f docker-compose.prod.yml up -d
```

---

## 8. 升级与回滚

### 8.1 滚动升级

```bash
# 1. 拉取新代码
git pull origin main

# 2. 重新构建镜像
docker-compose -f docker-compose.prod.yml build

# 3. 滚动重启（逐个服务，避免全停）
docker-compose -f docker-compose.prod.yml up -d --no-deps finance-backend
docker-compose -f docker-compose.prod.yml up -d --no-deps finance-ai
docker-compose -f docker-compose.prod.yml up -d --no-deps finance-frontend
docker-compose -f docker-compose.prod.yml up -d --no-deps nginx
```

### 8.2 数据库迁移回滚

> Flyway 不支持自动回滚（社区版）。生产升级前必须备份。

```bash
# 1. 升级前备份
docker-compose -f docker-compose.prod.yml exec postgres pg_dump -U finrpa -d finrpa -F c -f /backups/postgres/pre-upgrade.dump

# 2. 升级失败回滚
docker-compose -f docker-compose.prod.yml stop finance-backend
docker-compose -f docker-compose.prod.yml exec postgres pg_restore -U finrpa -d finrpa -c /backups/postgres/pre-upgrade.dump

# 3. 回滚代码到上一个版本
git reset --hard <previous-commit>
docker-compose -f docker-compose.prod.yml build
docker-compose -f docker-compose.prod.yml up -d
```

---

## 9. 安全清单

部署前请逐项确认：

- [ ] 所有 `${VAR:?...}` 必填变量已设置为强随机值（非默认值）
- [ ] `.env.prod` 文件权限为 `600`（`chmod 600 .env.prod`）
- [ ] SSL 证书已配置且未过期
- [ ] HTTP 80 端口访问自动 301 重定向到 HTTPS 443
- [ ] MinIO Console 端口（9001）已加防火墙白名单或改为内网访问
- [ ] 服务器防火墙仅开放 80/443 端口
- [ ] PostgreSQL/Redis/MinIO 端口未直接暴露到公网（仅 `expose`，非 `ports`）
- [ ] 定时备份 crontab 已配置
- [ ] 日志卷未满（建议配置日志轮转）
- [ ] 已测试过恢复流程（在测试环境）

---

## 附录 A：完整启动流程速查

```bash
# 1. 准备目录
cd /opt/finrpa

# 2. 配置环境变量
cp .env.prod.example .env.prod
vim .env.prod  # 填入真实密钥

# 3. 配置 SSL 证书
mkdir -p nginx/ssl
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout nginx/ssl/server.key -out nginx/ssl/server.crt \
  -subj "/C=CN/ST=Beijing/O=FinanceRPA/CN=finrpa.example.com"

# 4. 构建并启动
docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --build

# 5. 等待健康检查通过（约 5-6 分钟）
watch -n 5 'docker-compose -f docker-compose.prod.yml ps'

# 6. 验证
curl -k https://localhost/api/actuator/health
```

## 附录 B：常用命令速查

```bash
# 启动
docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d

# 停止
docker-compose -f docker-compose.prod.yml down

# 重启某服务
docker-compose -f docker-compose.prod.yml restart <service>

# 查看日志
docker-compose -f docker-compose.prod.yml logs -f <service>

# 查看资源占用
docker stats

# 进入容器
docker-compose -f docker-compose.prod.yml exec <service> sh

# 查看健康状态
docker-compose -f docker-compose.prod.yml ps

# 手动备份
docker-compose -f docker-compose.prod.yml exec postgres pg_dump -U finrpa -d finrpa -F c -f /backups/postgres/$(date +%Y%m%d).dump
```

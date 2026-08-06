-- P3 系统配置种子数据（INT-1 AI 配置 / INT-3 MinIO 配置 / OPS-2 定时任务参数 / OPS-3 系统开关）
-- 复用 V2 已建的 sys_config 表（key-value），不新建表
-- 默认值与 application.yml 保持一致，确保 DB 化前后行为不变
-- 敏感字段（ai.internal_token / minio.secret_key）当前明文存储，VO 脱敏展示；后续如需加密可改用 FernetCryptoService

-- ===== 1. AI 服务配置（INT-1） =====
INSERT INTO sys_config (config_key, config_value, config_type, description)
VALUES
    ('ai.base_url', 'http://localhost:8000', 'STRING', 'Python finance-ai 服务基础地址'),
    ('ai.internal_token', 'finrpa-internal-secret', 'STRING', '服务间共享密钥（X-Internal-Token Header）'),
    ('ai.connect_timeout', '5', 'INTEGER', '连接超时（秒）'),
    ('ai.read_timeout', '60', 'INTEGER', '读取超时（秒，同步调用场景）'),
    ('ai.sse_timeout', '3600', 'INTEGER', 'SSE 长连接超时（秒，默认 1 小时）'),
    ('ai.retry.max_attempts', '3', 'INTEGER', 'AI 调用最大重试次数'),
    ('ai.retry.backoff', '1000', 'INTEGER', 'AI 调用初始退避（毫秒，指数递增）')
ON CONFLICT (config_key) DO NOTHING;

-- ===== 2. MinIO 对象存储配置（INT-3） =====
INSERT INTO sys_config (config_key, config_value, config_type, description)
VALUES
    ('minio.endpoint', 'http://localhost:9000', 'STRING', 'MinIO 服务地址（含协议与端口）'),
    ('minio.access_key', 'minioadmin', 'STRING', 'MinIO 访问密钥'),
    ('minio.secret_key', 'minioadmin', 'STRING', 'MinIO 秘密密钥'),
    ('minio.bucket_prefix', 'finrpa-audit-', 'STRING', 'Bucket 前缀，完整 bucket 名为 {prefix}{org_id}'),
    ('minio.presign_expiry_hours', '1', 'INTEGER', '预签名 URL 有效期（小时）'),
    ('minio.retention_days', '90', 'INTEGER', '截图保留期（天）')
ON CONFLICT (config_key) DO NOTHING;

-- ===== 3. 定时任务参数（OPS-2） =====
-- 注：cron 表达式不 DB 化（Spring @Scheduled 启动时绑定，原生不支持热更新），仅 DB 化启停开关与重试次数
INSERT INTO sys_config (config_key, config_value, config_type, description)
VALUES
    ('scheduler.approval_timeout.enabled', 'true', 'BOOLEAN', '审批超时扫描任务启停（cron 固定每分钟，改 cron 需重启）'),
    ('scheduler.notification_retry.enabled', 'true', 'BOOLEAN', '通知重试扫描任务启停（cron 固定每 5 分钟）'),
    ('scheduler.notification_retry.max_count', '3', 'INTEGER', '通知最大重试次数')
ON CONFLICT (config_key) DO NOTHING;

-- ===== 4. 系统参数开关（OPS-3） =====
INSERT INTO sys_config (config_key, config_value, config_type, description)
VALUES
    ('maintenance.enabled', 'false', 'BOOLEAN', '全局维护模式开关（启用后非白名单请求返回 503）'),
    ('registration.enabled', 'true', 'BOOLEAN', '用户注册开关（关闭时 register 接口返回 403）'),
    ('upload.max_file_size_mb', '10', 'INTEGER', '文件上传大小上限（MB）')
ON CONFLICT (config_key) DO NOTHING;

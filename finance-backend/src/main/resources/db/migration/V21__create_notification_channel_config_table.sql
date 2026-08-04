-- 通知通道 Webhook 配置表（P0-4 settings 页通知配置持久化）
-- 持久化企业微信 / 钉钉群机器人 Webhook URL + 加签密钥 + 启用状态
-- 支持运行时通过 PUT /api/notification/channels/{channel} 在线修改，热生效
-- 注意：该表无 org_id 字段（全局共享配置），已加入 TenantConstant.IGNORED_TABLES
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_notification_channel_config (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    webhook_url VARCHAR(512) NOT NULL DEFAULT '',
    secret VARCHAR(256) NOT NULL DEFAULT '',
    enabled SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (channel)
);

-- 初始化两条记录（wecom / dingtalk），从 application.yml 环境变量初始值同步
-- 若环境变量已配置，则用环境变量值初始化；否则留空
INSERT INTO rpa_notification_channel_config (channel, webhook_url, secret, enabled)
SELECT 'wecom', '', '', 1
WHERE NOT EXISTS (SELECT 1 FROM rpa_notification_channel_config WHERE channel = 'wecom');

INSERT INTO rpa_notification_channel_config (channel, webhook_url, secret, enabled)
SELECT 'dingtalk', '', '', 1
WHERE NOT EXISTS (SELECT 1 FROM rpa_notification_channel_config WHERE channel = 'dingtalk');

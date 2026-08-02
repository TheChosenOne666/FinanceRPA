-- 通知发送尝试记录表（M6.6 扩展 NotificationAttempt 持久化）
-- 记录所有通知发送尝试（成功/失败），用于审计追踪与重试统计
-- 注意：该表无 org_id 字段，由 Java 内部触发流程写入（审批触发 / 重试调度），
-- 已加入 TenantConstant.IGNORED_TABLES 绕过自动租户过滤
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_notification_attempt (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    approval_id BIGINT,
    task_id BIGINT,
    target_user_id BIGINT,
    channel VARCHAR(32) NOT NULL,
    template VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    content TEXT,
    success SMALLINT NOT NULL DEFAULT 0,
    error_message VARCHAR(2048),
    raw_response TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    enqueued_at TIMESTAMP,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (attempt_id)
);

CREATE INDEX idx_rpa_notification_attempt_approval ON rpa_notification_attempt(approval_id);
CREATE INDEX idx_rpa_notification_attempt_task ON rpa_notification_attempt(task_id);
CREATE INDEX idx_rpa_notification_attempt_channel ON rpa_notification_attempt(channel);
CREATE INDEX idx_rpa_notification_attempt_template ON rpa_notification_attempt(template);
CREATE INDEX idx_rpa_notification_attempt_success ON rpa_notification_attempt(success);
CREATE INDEX idx_rpa_notification_attempt_create_time ON rpa_notification_attempt(create_time);

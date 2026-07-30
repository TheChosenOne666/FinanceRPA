-- 审计日志表（Python Executor 回调写入，记录任务执行的操作行为）
-- 用于安全审计与合规追溯，仅追加不修改
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_audit_log (
    id BIGSERIAL PRIMARY KEY,
    audit_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_element VARCHAR(512),
    page_url VARCHAR(1024),
    execution_result VARCHAR(32) NOT NULL DEFAULT 'success',
    error_message TEXT,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (audit_id)
);

CREATE INDEX idx_rpa_audit_log_task ON rpa_audit_log(task_id);
CREATE INDEX idx_rpa_audit_log_org ON rpa_audit_log(org_id);
CREATE INDEX idx_rpa_audit_log_create_time ON rpa_audit_log(create_time);

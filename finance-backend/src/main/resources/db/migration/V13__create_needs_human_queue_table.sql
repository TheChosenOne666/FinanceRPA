-- NEEDS_HUMAN 队列表（Python ResilientCaller 重试耗尽后上报，等待操作员处置）
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_needs_human_queue (
    id BIGSERIAL PRIMARY KEY,
    queue_id BIGINT NOT NULL,
    task_id BIGINT,
    org_id BIGINT,
    subtask_id VARCHAR(128),
    context_name VARCHAR(64) NOT NULL DEFAULT 'unknown',
    screenshot_url VARCHAR(1024),
    llm_raw_output TEXT,
    validation_error TEXT,
    attempts INT DEFAULT 0 NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    resolve_action VARCHAR(32),
    resolved_by BIGINT,
    resolved_at TIMESTAMP,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (queue_id)
);

CREATE INDEX idx_rpa_needs_human_queue_task ON rpa_needs_human_queue(task_id);
CREATE INDEX idx_rpa_needs_human_queue_org ON rpa_needs_human_queue(org_id);
CREATE INDEX idx_rpa_needs_human_queue_status ON rpa_needs_human_queue(status);
CREATE INDEX idx_rpa_needs_human_queue_create_time ON rpa_needs_human_queue(create_time);

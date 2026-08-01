-- 审批请求表（M6.3 审批流路由 + Pub/Sub）
-- 高风险/极高风险任务触发时创建审批单，等待审批通过后再执行
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_approval_request (
    id BIGSERIAL PRIMARY KEY,
    approval_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    org_id BIGINT,
    workflow_id BIGINT,
    user_id BIGINT,
    risk_level VARCHAR(32) NOT NULL,
    approval_route VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    approver_id BIGINT,
    approve_reason VARCHAR(1024),
    reject_reason VARCHAR(1024),
    risk_reasoning VARCHAR(2048),
    request_payload TEXT,
    timeout_minutes INT DEFAULT 30 NOT NULL,
    timeout_at TIMESTAMP,
    approved_at TIMESTAMP,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (approval_id)
);

CREATE INDEX idx_rpa_approval_request_task ON rpa_approval_request(task_id);
CREATE INDEX idx_rpa_approval_request_org ON rpa_approval_request(org_id);
CREATE INDEX idx_rpa_approval_request_status ON rpa_approval_request(status);
CREATE INDEX idx_rpa_approval_request_route ON rpa_approval_request(approval_route);
CREATE INDEX idx_rpa_approval_request_timeout ON rpa_approval_request(timeout_at);
CREATE INDEX idx_rpa_approval_request_create_time ON rpa_approval_request(create_time);

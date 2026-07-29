SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL,
    email VARCHAR(128),
    phone VARCHAR(32),
    real_name VARCHAR(64),
    avatar VARCHAR(256),
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    role_name VARCHAR(64) NOT NULL UNIQUE,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(256),
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (role_id)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    permission_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(32),
    resource_path VARCHAR(256),
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (permission_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS rpa_task (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_name VARCHAR(128) NOT NULL,
    task_code VARCHAR(64) NOT NULL UNIQUE,
    task_type VARCHAR(32) NOT NULL,
    description VARCHAR(512),
    workflow_id BIGINT,
    executor_id BIGINT,
    cron_expression VARCHAR(128),
    priority SMALLINT DEFAULT 0 NOT NULL,
    status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 3,
    timeout_seconds INT DEFAULT 3600,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_run_time TIMESTAMP,
    next_run_time TIMESTAMP,
    UNIQUE (task_id)
);

CREATE TABLE IF NOT EXISTS rpa_workflow (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    workflow_name VARCHAR(128) NOT NULL,
    workflow_code VARCHAR(64) NOT NULL UNIQUE,
    workflow_definition JSONB NOT NULL,
    description VARCHAR(512),
    version VARCHAR(32) DEFAULT '1.0.0' NOT NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (workflow_id)
);

CREATE TABLE IF NOT EXISTS rpa_task_execution (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    workflow_id BIGINT,
    executor_id BIGINT,
    status VARCHAR(32) DEFAULT 'RUNNING' NOT NULL,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    error_message TEXT,
    error_stack TEXT,
    input_params JSONB,
    output_result JSONB,
    UNIQUE (execution_id)
);

CREATE TABLE IF NOT EXISTS rpa_task_log (
    id BIGSERIAL PRIMARY KEY,
    log_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    log_level VARCHAR(16) DEFAULT 'INFO' NOT NULL,
    log_message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (log_id)
);

CREATE TABLE IF NOT EXISTS rpa_browser_session (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    task_id BIGINT,
    execution_id BIGINT,
    browser_type VARCHAR(32) NOT NULL,
    browser_version VARCHAR(32),
    status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error_message TEXT,
    UNIQUE (session_id)
);

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGSERIAL PRIMARY KEY,
    log_id BIGINT NOT NULL,
    user_id BIGINT,
    username VARCHAR(64),
    operation_type VARCHAR(64) NOT NULL,
    operation_module VARCHAR(64) NOT NULL,
    operation_detail TEXT,
    request_url VARCHAR(512),
    request_method VARCHAR(16),
    request_params JSONB,
    response_result JSONB,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    status VARCHAR(16) DEFAULT 'SUCCESS' NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (log_id)
);

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(32) DEFAULT 'STRING',
    description VARCHAR(256),
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_dictionary (
    id BIGSERIAL PRIMARY KEY,
    dict_id BIGINT NOT NULL,
    dict_type VARCHAR(64) NOT NULL,
    dict_code VARCHAR(64) NOT NULL,
    dict_value VARCHAR(256) NOT NULL,
    description VARCHAR(256),
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (dict_id),
    UNIQUE (dict_type, dict_code)
);

CREATE TABLE IF NOT EXISTS rpa_approval (
    id BIGSERIAL PRIMARY KEY,
    approval_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    execution_id BIGINT,
    applicant_id BIGINT NOT NULL,
    applicant_name VARCHAR(64),
    approval_type VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
    reason VARCHAR(512),
    approval_time TIMESTAMP,
    approver_id BIGINT,
    approver_name VARCHAR(64),
    approval_comment VARCHAR(512),
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (approval_id)
);

CREATE INDEX idx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_status ON sys_user(status);
CREATE INDEX idx_sys_role_code ON sys_role(role_code);
CREATE INDEX idx_sys_user_role_user ON sys_user_role(user_id);
CREATE INDEX idx_sys_user_role_role ON sys_user_role(role_id);
CREATE INDEX idx_rpa_task_status ON rpa_task(status);
CREATE INDEX idx_rpa_task_code ON rpa_task(task_code);
CREATE INDEX idx_rpa_task_execution_task ON rpa_task_execution(task_id);
CREATE INDEX idx_rpa_task_execution_status ON rpa_task_execution(status);
CREATE INDEX idx_rpa_task_log_execution ON rpa_task_log(execution_id);
CREATE INDEX idx_rpa_browser_session_task ON rpa_browser_session(task_id);
CREATE INDEX idx_sys_audit_log_user ON sys_audit_log(user_id);
CREATE INDEX idx_sys_audit_log_time ON sys_audit_log(create_time);
CREATE INDEX idx_rpa_approval_task ON rpa_approval(task_id);
CREATE INDEX idx_rpa_approval_status ON rpa_approval(approval_status);

INSERT INTO sys_role (role_id, role_name, role_code, description)
VALUES
    (1, '超级管理员', 'admin', '系统超级管理员，拥有所有权限'),
    (2, '运维管理员', 'ops', '运维管理员，负责任务管理和监控'),
    (3, '业务用户', 'user', '业务用户，负责提交任务和查看结果');

INSERT INTO sys_config (config_key, config_value, config_type, description)
VALUES
    ('system.name', 'FinanceRPA', 'STRING', '系统名称'),
    ('system.version', '0.0.1', 'STRING', '系统版本'),
    ('rpa.default_timeout', '3600', 'INTEGER', '默认任务超时时间（秒）'),
    ('rpa.max_retry', '3', 'INTEGER', '默认最大重试次数'),
    ('security.jwt.expire_hours', '24', 'INTEGER', 'JWT过期时间（小时）');

SET search_path = finrpa;

-- Agent 任务表（任务执行实例，区别于 rpa_task 任务定义表）
CREATE TABLE IF NOT EXISTS rpa_agent_task (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    goal VARCHAR(512) NOT NULL,
    params JSONB,
    workflow_id BIGINT,
    status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
    current_step INT DEFAULT 0 NOT NULL,
    total_steps INT DEFAULT 0 NOT NULL,
    message VARCHAR(512),
    error_message TEXT,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (task_id)
);

-- Agent 子任务表
CREATE TABLE IF NOT EXISTS rpa_agent_subtask (
    id BIGSERIAL PRIMARY KEY,
    subtask_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    subtask_index INT NOT NULL,
    goal VARCHAR(512) NOT NULL,
    completion_condition VARCHAR(512),
    max_retries INT DEFAULT 2,
    failure_strategy VARCHAR(32) DEFAULT 'REPLAN',
    status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
    error_message TEXT,
    result_data JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (subtask_id)
);

-- Agent 协调状态表
CREATE TABLE IF NOT EXISTS rpa_agent_coordination_state (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    navigation_goal VARCHAR(512) NOT NULL,
    current_plan JSONB,
    completed_subtasks JSONB,
    total_replans INT DEFAULT 0,
    max_replans INT DEFAULT 3,
    status VARCHAR(32) DEFAULT 'RUNNING' NOT NULL,
    error_message TEXT,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (task_id)
);

CREATE INDEX idx_rpa_agent_task_org ON rpa_agent_task(org_id);
CREATE INDEX idx_rpa_agent_task_status ON rpa_agent_task(status);
CREATE INDEX idx_rpa_agent_task_user ON rpa_agent_task(user_id);
CREATE INDEX idx_rpa_agent_subtask_task ON rpa_agent_subtask(task_id);
CREATE INDEX idx_rpa_agent_subtask_status ON rpa_agent_subtask(status);
CREATE INDEX idx_rpa_agent_coord_state_task ON rpa_agent_coordination_state(task_id);

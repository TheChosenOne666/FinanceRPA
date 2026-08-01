-- LLM 调用记录表（Python ResilientCaller 回调写入，记录每次 LLM 调用详情）
-- 用于调用统计、成本分析与监控
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_llm_call_log (
    id BIGSERIAL PRIMARY KEY,
    call_id BIGINT NOT NULL,
    task_id BIGINT,
    org_id BIGINT,
    model VARCHAR(128) NOT NULL,
    context_name VARCHAR(64) NOT NULL DEFAULT 'unknown',
    retry_attempt SMALLINT DEFAULT 0 NOT NULL,
    success BOOLEAN DEFAULT FALSE NOT NULL,
    error_message TEXT,
    duration_ms INT DEFAULT 0 NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    cache_hit BOOLEAN DEFAULT FALSE NOT NULL,
    cost DECIMAL(12, 6) DEFAULT 0 NOT NULL,
    call_time TIMESTAMP,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (call_id)
);

CREATE INDEX idx_rpa_llm_call_log_task ON rpa_llm_call_log(task_id);
CREATE INDEX idx_rpa_llm_call_log_org ON rpa_llm_call_log(org_id);
CREATE INDEX idx_rpa_llm_call_log_model ON rpa_llm_call_log(model);
CREATE INDEX idx_rpa_llm_call_log_create_time ON rpa_llm_call_log(create_time);

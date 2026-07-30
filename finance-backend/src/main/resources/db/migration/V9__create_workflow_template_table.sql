-- M3.4 工作流模板表（全局共享，无 org_id 字段）
-- 存储 6 个内置金融场景模板 + 用户自定义模板
-- params/steps 使用 JSONB 存储复杂数据结构
CREATE TABLE IF NOT EXISTS rpa_workflow_template (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    industry VARCHAR(32) NOT NULL,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'medium',
    params JSONB NOT NULL DEFAULT '[]',
    steps JSONB NOT NULL DEFAULT '[]',
    version VARCHAR(16) DEFAULT '1.0.0',
    enabled SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (name)
);

CREATE INDEX idx_rpa_workflow_template_industry ON rpa_workflow_template(industry);
CREATE INDEX idx_rpa_workflow_template_risk_level ON rpa_workflow_template(risk_level);

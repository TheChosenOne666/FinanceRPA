-- 风险关键词库表（M6.1 关键词预筛）
-- 全局共享（无 org_id 字段），存储银行 / 保险 / 证券 三大行业的关键词
-- 由 RiskKeywordInitializer 启动时 upsert 内置关键词，管理员可通过 API 增删改
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_risk_keyword (
    id BIGSERIAL PRIMARY KEY,
    keyword_id BIGINT NOT NULL,
    keyword VARCHAR(128) NOT NULL,
    industry VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL,
    risk_type VARCHAR(16) NOT NULL DEFAULT 'medium',
    description VARCHAR(256),
    enabled SMALLINT DEFAULT 1 NOT NULL,
    builtin SMALLINT DEFAULT 0 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (keyword_id)
);

CREATE INDEX idx_rpa_risk_keyword_industry ON rpa_risk_keyword(industry);
CREATE INDEX idx_rpa_risk_keyword_category ON rpa_risk_keyword(category);
CREATE INDEX idx_rpa_risk_keyword_enabled ON rpa_risk_keyword(enabled);
CREATE INDEX idx_rpa_risk_keyword_builtin ON rpa_risk_keyword(builtin);

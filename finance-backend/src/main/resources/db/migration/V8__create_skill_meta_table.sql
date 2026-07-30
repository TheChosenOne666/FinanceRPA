SET search_path = finrpa;

-- Skill 元数据表（全局共享，不参与租户隔离）
-- 存储 7 个内置 Skill 与用户自定义 Skill 的元数据，供前端展示与工作流模板校验
CREATE TABLE IF NOT EXISTS rpa_skill_meta (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL,                  -- 雪花算法业务 ID
    name VARCHAR(64) NOT NULL,                 -- Skill 唯一标识（对应 Python skill_name）
    description VARCHAR(256) NOT NULL,         -- 用途描述
    category VARCHAR(32) NOT NULL,             -- 分类：auth / interaction / extraction
    param_schema JSONB,                        -- Pydantic params_model 的 JSON Schema
    error_strategy VARCHAR(16) DEFAULT 'RETRY' NOT NULL,  -- 失败策略：RETRY / SKIP / ABORT
    max_retries INT DEFAULT 2 NOT NULL,        -- 最大重试次数
    version VARCHAR(16) DEFAULT '1.0.0' NOT NULL,  -- 版本号
    enabled SMALLINT DEFAULT 1 NOT NULL,       -- 启用状态（0-禁用 1-启用）
    deleted SMALLINT DEFAULT 0 NOT NULL,       -- 逻辑删除标识
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (name)
);

CREATE INDEX idx_rpa_skill_meta_category ON rpa_skill_meta(category);
CREATE INDEX idx_rpa_skill_meta_enabled ON rpa_skill_meta(enabled);

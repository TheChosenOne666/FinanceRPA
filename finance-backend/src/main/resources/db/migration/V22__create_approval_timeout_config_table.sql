-- 审批超时阈值配置表（P1 RSK-1 settings 页风控配置）
-- 持久化 high / critical 风险等级的审批超时分钟数，支持运行时在线修改
-- 替代写死在 ApprovalConstant.HIGH_APPROVAL_TIMEOUT_MINUTES / CRITICAL_APPROVAL_TIMEOUT_MINUTES 的 30 / 60 分钟
-- 注意：该表无 org_id 字段（全局共享配置），已加入 TenantConstant.IGNORED_TABLES
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_approval_timeout_config (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    timeout_minutes INT NOT NULL DEFAULT 30,
    description VARCHAR(256),
    enabled SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (risk_level)
);

COMMENT ON TABLE rpa_approval_timeout_config IS '审批超时阈值配置表（全局共享，按风险等级配置超时分钟数）';
COMMENT ON COLUMN rpa_approval_timeout_config.config_id IS '配置业务 ID（雪花算法）';
COMMENT ON COLUMN rpa_approval_timeout_config.risk_level IS '风险等级：high / critical';
COMMENT ON COLUMN rpa_approval_timeout_config.timeout_minutes IS '超时分钟数（默认 30）';
COMMENT ON COLUMN rpa_approval_timeout_config.description IS '描述说明';
COMMENT ON COLUMN rpa_approval_timeout_config.enabled IS '启用状态：0-禁用 1-启用';
COMMENT ON COLUMN rpa_approval_timeout_config.deleted IS '逻辑删除标识：0-未删除 1-已删除';

-- 初始化两条记录（与 ApprovalConstant 默认值保持一致：high=30, critical=60）
INSERT INTO rpa_approval_timeout_config (config_id, risk_level, timeout_minutes, description, enabled)
SELECT 1751000000000000001, 'high', 30, '高风险任务审批超时阈值（部门审批）', 1
WHERE NOT EXISTS (SELECT 1 FROM rpa_approval_timeout_config WHERE risk_level = 'high');

INSERT INTO rpa_approval_timeout_config (config_id, risk_level, timeout_minutes, description, enabled)
SELECT 1751000000000000002, 'critical', 60, '极高风险任务审批超时阈值（合规审计部审批）', 1
WHERE NOT EXISTS (SELECT 1 FROM rpa_approval_timeout_config WHERE risk_level = 'critical');

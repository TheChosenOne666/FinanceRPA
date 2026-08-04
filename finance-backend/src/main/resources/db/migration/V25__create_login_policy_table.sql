-- 登录安全策略配置表（P2 SEC-2 settings 页安全策略）
-- sys_login_policy：全局共享的单行配置（id=1），无 org_id 字段，已加入 TenantConstant.IGNORED_TABLES
-- 字段：最大失败次数 / 锁定分钟数 / IP 白名单 / IP 黑名单 / 是否允许多端并发登录 / 空闲超时分钟数
SET search_path = finrpa;

-- 1. 登录安全策略配置表（全局单行配置）
CREATE TABLE IF NOT EXISTS sys_login_policy (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    max_login_attempts INT NOT NULL DEFAULT 5,
    lock_minutes INT NOT NULL DEFAULT 30,
    ip_whitelist TEXT,
    ip_blacklist TEXT,
    allow_multi_login SMALLINT NOT NULL DEFAULT 0,
    session_timeout_minutes INT NOT NULL DEFAULT 30,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (policy_id)
);

COMMENT ON TABLE sys_login_policy IS '登录安全策略配置表（全局共享单行配置，id=1）';
COMMENT ON COLUMN sys_login_policy.policy_id IS '策略业务 ID（雪花算法）';
COMMENT ON COLUMN sys_login_policy.max_login_attempts IS '最大连续登录失败次数（超过后锁定账号，默认 5）';
COMMENT ON COLUMN sys_login_policy.lock_minutes IS '账号锁定时长（分钟，默认 30）';
COMMENT ON COLUMN sys_login_policy.ip_whitelist IS 'IP 白名单（逗号分隔，空表示不限制；同时配置时白名单优先）';
COMMENT ON COLUMN sys_login_policy.ip_blacklist IS 'IP 黑名单（逗号分隔，命中即拒绝）';
COMMENT ON COLUMN sys_login_policy.allow_multi_login IS '是否允许多端并发登录：0-不允许 1-允许（默认 0，配合 SEC-3 会话管理）';
COMMENT ON COLUMN sys_login_policy.session_timeout_minutes IS '会话空闲超时分钟数（默认 30，配合 SEC-3 会话管理）';
COMMENT ON COLUMN sys_login_policy.enabled IS '启用状态：0-禁用 1-启用';

-- 初始化单行默认配置
INSERT INTO sys_login_policy (policy_id, max_login_attempts, lock_minutes, ip_whitelist,
    ip_blacklist, allow_multi_login, session_timeout_minutes, enabled)
SELECT 1751000000000000011, 5, 30, NULL, NULL, 0, 30, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_login_policy);

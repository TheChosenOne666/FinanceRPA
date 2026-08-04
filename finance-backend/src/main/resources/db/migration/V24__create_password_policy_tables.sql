-- 密码策略配置表 + 密码历史表 + sys_user 加密码安全字段（P2 SEC-1 settings 页安全策略）
-- sys_password_policy：全局共享的单行配置（id=1），无 org_id 字段，已加入 TenantConstant.IGNORED_TABLES
-- sys_password_history：按 user_id 关联，无 org_id 字段，已加入 TenantConstant.IGNORED_TABLES
-- sys_user 增加 pwd_changed_at 字段，用于密码过期校验
SET search_path = finrpa;

-- 1. 密码策略配置表（全局单行配置）
CREATE TABLE IF NOT EXISTS sys_password_policy (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    min_length INT NOT NULL DEFAULT 8,
    require_uppercase SMALLINT NOT NULL DEFAULT 1,
    require_lowercase SMALLINT NOT NULL DEFAULT 1,
    require_digit SMALLINT NOT NULL DEFAULT 1,
    require_special SMALLINT NOT NULL DEFAULT 1,
    special_chars VARCHAR(32) NOT NULL DEFAULT '!@#$%^&*()_+-=[]{}|;:,.<>?',
    expire_days INT NOT NULL DEFAULT 90,
    history_count INT NOT NULL DEFAULT 5,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (policy_id)
);

COMMENT ON TABLE sys_password_policy IS '密码策略配置表（全局共享单行配置，id=1）';
COMMENT ON COLUMN sys_password_policy.policy_id IS '策略业务 ID（雪花算法）';
COMMENT ON COLUMN sys_password_policy.min_length IS '密码最小长度（默认 8）';
COMMENT ON COLUMN sys_password_policy.require_uppercase IS '是否要求大写字母：0-不要求 1-要求';
COMMENT ON COLUMN sys_password_policy.require_lowercase IS '是否要求小写字母：0-不要求 1-要求';
COMMENT ON COLUMN sys_password_policy.require_digit IS '是否要求数字：0-不要求 1-要求';
COMMENT ON COLUMN sys_password_policy.require_special IS '是否要求特殊字符：0-不要求 1-要求';
COMMENT ON COLUMN sys_password_policy.special_chars IS '允许的特殊字符集';
COMMENT ON COLUMN sys_password_policy.expire_days IS '密码过期天数（0 表示不过期，默认 90）';
COMMENT ON COLUMN sys_password_policy.history_count IS '密码历史记录数（禁止重复使用最近 N 次密码，0 表示不检查，默认 5）';
COMMENT ON COLUMN sys_password_policy.enabled IS '启用状态：0-禁用 1-启用';

-- 初始化单行默认配置
INSERT INTO sys_password_policy (policy_id, min_length, require_uppercase, require_lowercase,
    require_digit, require_special, special_chars, expire_days, history_count, enabled)
SELECT 1751000000000000010, 8, 1, 1, 1, 1, '!@#$%^&*()_+-=[]{}|;:,.<>?', 90, 5, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_password_policy);

-- 2. 密码历史表（记录用户最近 N 次密码哈希，防止重复使用）
CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGSERIAL PRIMARY KEY,
    history_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (history_id)
);

COMMENT ON TABLE sys_password_history IS '密码历史表（记录用户最近 N 次密码哈希，防止重复使用）';
COMMENT ON COLUMN sys_password_history.history_id IS '历史记录业务 ID（雪花算法）';
COMMENT ON COLUMN sys_password_history.user_id IS '用户业务 ID';
COMMENT ON COLUMN sys_password_history.password_hash IS '密码 BCrypt 哈希值';

CREATE INDEX IF NOT EXISTS idx_password_history_user_id ON sys_password_history (user_id, create_time DESC);

-- 3. sys_user 增加密码安全字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN sys_user.pwd_changed_at IS '密码最后修改时间（用于密码过期校验）';

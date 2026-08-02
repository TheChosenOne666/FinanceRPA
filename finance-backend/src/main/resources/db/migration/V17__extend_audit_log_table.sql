-- M7.1：扩展 rpa_audit_log 表为系统设计 6.4.1 完整结构
-- 新增部门/业务线/用户/操作参数(脱敏)/风险信息/时间信息/截图URL/LLM信息等字段
-- 截图 URL 字段在 M7.1 预留，由 M7.2 MinIO 存储集成后填充

SET search_path = finrpa;

-- region 基本信息
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS department_id BIGINT;
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS business_line_id BIGINT;
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS user_id BIGINT;
-- endregion

-- region 操作信息
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS action_params TEXT;
-- endregion

-- region 风险信息
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS risk_level VARCHAR(16);
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS approval_id BIGINT;
-- endregion

-- region 时间信息
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS duration_ms BIGINT;
-- endregion

-- region 截图信息（M7.2 MinIO 预签名 URL，M7.1 预留字段）
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS before_screenshot_url VARCHAR(1024);
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS after_screenshot_url VARCHAR(1024);
-- endregion

-- region LLM 信息
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS llm_model VARCHAR(64);
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS llm_tokens_used INT;
ALTER TABLE rpa_audit_log ADD COLUMN IF NOT EXISTS llm_cost DECIMAL(10, 4);
-- endregion

-- region 字段注释
COMMENT ON COLUMN rpa_audit_log.department_id IS '部门 ID（多维检索用）';
COMMENT ON COLUMN rpa_audit_log.business_line_id IS '业务线 ID（多维检索用）';
COMMENT ON COLUMN rpa_audit_log.user_id IS '触发用户 ID';
COMMENT ON COLUMN rpa_audit_log.action_params IS '操作参数 JSON（经 SanitizeService 脱敏后存储）';
COMMENT ON COLUMN rpa_audit_log.risk_level IS '风险等级：low / medium / high / critical';
COMMENT ON COLUMN rpa_audit_log.approval_id IS '关联审批单 ID（经审批的任务填写）';
COMMENT ON COLUMN rpa_audit_log.started_at IS '操作开始时间';
COMMENT ON COLUMN rpa_audit_log.completed_at IS '操作完成时间';
COMMENT ON COLUMN rpa_audit_log.duration_ms IS '操作耗时（毫秒）';
COMMENT ON COLUMN rpa_audit_log.before_screenshot_url IS '操作前截图 URL（M7.2 MinIO 预签名，有效期 1 小时）';
COMMENT ON COLUMN rpa_audit_log.after_screenshot_url IS '操作后截图 URL（M7.2 MinIO 预签名，有效期 1 小时）';
COMMENT ON COLUMN rpa_audit_log.llm_model IS 'LLM 模型名称（如 gpt-4o / claude-sonnet）';
COMMENT ON COLUMN rpa_audit_log.llm_tokens_used IS 'LLM token 用量';
COMMENT ON COLUMN rpa_audit_log.llm_cost IS 'LLM 调用成本（美元）';
-- endregion

-- region 多维检索索引（M7.4 检索维度：用户/部门/业务线/风险等级/操作类型/时间范围）
CREATE INDEX IF NOT EXISTS idx_rpa_audit_log_user ON rpa_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_rpa_audit_log_dept ON rpa_audit_log(department_id);
CREATE INDEX IF NOT EXISTS idx_rpa_audit_log_biz_line ON rpa_audit_log(business_line_id);
CREATE INDEX IF NOT EXISTS idx_rpa_audit_log_risk_level ON rpa_audit_log(risk_level);
CREATE INDEX IF NOT EXISTS idx_rpa_audit_log_action_type ON rpa_audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_rpa_audit_log_started_at ON rpa_audit_log(started_at);
-- endregion

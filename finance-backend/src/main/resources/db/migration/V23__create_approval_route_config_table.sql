-- 审批人映射配置表（P1 RSK-3 settings 页风控配置）
-- 持久化「风险等级 × 业务线 → 审批人」映射规则，替代写死在 ApprovalRouteService 的「按风险等级路由」逻辑
-- 创建审批单时：先按 (org_id, risk_level, business_line_id) 精确匹配查找审批人；
-- 找不到则按 (org_id, risk_level, business_line_id IS NULL) 默认路由查找；仍找不到则 approver_id 留空（由审批中心手动认领）
-- 注意：该表有 org_id 字段，但已加入 TenantConstant.IGNORED_TABLES（与 sys_user 同列处理，对外 API 在 Service 层手动按 orgId 过滤）
SET search_path = finrpa;

CREATE TABLE IF NOT EXISTS rpa_approval_route_config (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    business_line_id BIGINT,
    approver_user_id BIGINT NOT NULL,
    department_id BIGINT,
    description VARCHAR(256),
    enabled SMALLINT NOT NULL DEFAULT 1,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (org_id, risk_level, business_line_id)
);

COMMENT ON TABLE rpa_approval_route_config IS '审批人映射配置表（按组织 + 风险等级 + 业务线路由审批人）';
COMMENT ON COLUMN rpa_approval_route_config.config_id IS '配置业务 ID（雪花算法）';
COMMENT ON COLUMN rpa_approval_route_config.org_id IS '组织业务 ID（雪花算法 ID）';
COMMENT ON COLUMN rpa_approval_route_config.risk_level IS '风险等级：high / critical';
COMMENT ON COLUMN rpa_approval_route_config.business_line_id IS '业务线业务 ID（NULL 表示该风险等级的默认路由）';
COMMENT ON COLUMN rpa_approval_route_config.approver_user_id IS '审批人用户业务 ID（关联 sys_user.user_id）';
COMMENT ON COLUMN rpa_approval_route_config.department_id IS '审批人所属部门业务 ID（可空，关联 enterprise_department.dept_id）';
COMMENT ON COLUMN rpa_approval_route_config.description IS '描述说明';
COMMENT ON COLUMN rpa_approval_route_config.enabled IS '启用状态：0-禁用 1-启用';
COMMENT ON COLUMN rpa_approval_route_config.deleted IS '逻辑删除标识：0-未删除 1-已删除';

CREATE INDEX IF NOT EXISTS idx_rpa_approval_route_config_org ON rpa_approval_route_config(org_id);
CREATE INDEX IF NOT EXISTS idx_rpa_approval_route_config_risk ON rpa_approval_route_config(risk_level);
CREATE INDEX IF NOT EXISTS idx_rpa_approval_route_config_biz ON rpa_approval_route_config(business_line_id);

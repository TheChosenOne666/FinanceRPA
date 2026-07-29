SET search_path = finrpa;

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS org_id BIGINT;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS org_name VARCHAR(128);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS dept_name VARCHAR(128);

ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS org_id BIGINT;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS is_cross_org_read SMALLINT DEFAULT 0;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS is_cross_org_approve SMALLINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_sys_user_org ON sys_user(org_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_dept ON sys_user(dept_name);

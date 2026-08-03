-- M7.6 三维度 RBAC + 任务业务线字段
-- 1. sys_user_role 表扩展 department_id + business_line_id 列（向后兼容，允许 NULL）
--    实现需求文档 6.2.2 权限解析算法：遍历用户的所有 (部门, 业务线, 角色) 关联
-- 2. rpa_agent_task 表新增 department_id + business_line_id 字段
--    用于任务列表页按业务线筛选 + 元信息展示部门/业务线名称

-- ===== 1. sys_user_role 扩展三维度关联字段 =====
ALTER TABLE finrpa.sys_user_role
    ADD COLUMN IF NOT EXISTS department_id BIGINT;

ALTER TABLE finrpa.sys_user_role
    ADD COLUMN IF NOT EXISTS business_line_id BIGINT;

COMMENT ON COLUMN finrpa.sys_user_role.department_id IS '部门业务 ID（关联 enterprise_department.dept_id，NULL 表示不限部门）';
COMMENT ON COLUMN finrpa.sys_user_role.business_line_id IS '业务线业务 ID（关联 enterprise_business_line.business_line_id，NULL 表示不限业务线）';

-- 1.1 调整唯一约束：原 (user_id, role_id) 扩展为 (user_id, role_id, department_id, business_line_id)
--     允许同一用户在不同部门/业务线持有相同角色
ALTER TABLE finrpa.sys_user_role
    DROP CONSTRAINT IF EXISTS sys_user_role_user_id_role_id_key;

ALTER TABLE finrpa.sys_user_role
    ADD CONSTRAINT sys_user_role_user_role_dept_biz_key
    UNIQUE (user_id, role_id, department_id, business_line_id);

-- 1.2 索引：按部门 / 业务线查询用户角色关联
CREATE INDEX IF NOT EXISTS idx_sys_user_role_dept ON finrpa.sys_user_role(department_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_biz ON finrpa.sys_user_role(business_line_id);

-- ===== 2. rpa_agent_task 表新增部门 + 业务线字段 =====
ALTER TABLE finrpa.rpa_agent_task
    ADD COLUMN IF NOT EXISTS department_id BIGINT;

ALTER TABLE finrpa.rpa_agent_task
    ADD COLUMN IF NOT EXISTS business_line_id BIGINT;

COMMENT ON COLUMN finrpa.rpa_agent_task.department_id IS '部门业务 ID（关联 enterprise_department.dept_id，任务触发时从用户关联中推断）';
COMMENT ON COLUMN finrpa.rpa_agent_task.business_line_id IS '业务线业务 ID（关联 enterprise_business_line.business_line_id，任务触发时从用户关联或请求参数中获取）';

-- 2.1 索引：按业务线 / 部门筛选任务
CREATE INDEX IF NOT EXISTS idx_rpa_agent_task_biz_line ON finrpa.rpa_agent_task(business_line_id);
CREATE INDEX IF NOT EXISTS idx_rpa_agent_task_dept ON finrpa.rpa_agent_task(department_id);

-- M7.5 UI 对齐原型：rpa_workflow_template 新增 create_user_id 字段
-- 用于工作流详情页显示"创建人"字段（关联 sys_user.user_id 获取姓名）
-- 内置模板由系统初始化，create_user_id 为 NULL，前端显示"系统"
ALTER TABLE finrpa.rpa_workflow_template
    ADD COLUMN IF NOT EXISTS create_user_id BIGINT;

COMMENT ON COLUMN finrpa.rpa_workflow_template.create_user_id IS '创建人用户 ID（关联 sys_user.user_id，内置模板为 NULL 表示系统创建）';

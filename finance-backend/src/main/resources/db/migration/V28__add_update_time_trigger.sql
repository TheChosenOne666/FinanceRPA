-- 通用 update_time 自动刷新触发器
--
-- 背景：PostgreSQL 的 `DEFAULT CURRENT_TIMESTAMP` 仅在 INSERT 时生效，
-- UPDATE 时不会自动刷新（不同于 MySQL 的 `ON UPDATE CURRENT_TIMESTAMP`）。
-- 项目未配置 MyBatis-Plus MetaObjectHandler 做 updateFill，导致所有表的
-- update_time 永远等于 create_time，任务管理页耗时全部显示为 0s。
--
-- 方案：创建通用的 BEFORE UPDATE 触发器函数 set_update_timestamp()，
-- 对所有含 update_time 列的业务表挂载同名触发器，UPDATE 时自动刷新 update_time。
--
-- 涉及表（共 25 张，均含 update_time 列）：
--   sys_user / sys_role / sys_permission / sys_config / sys_dictionary / rpa_approval
--   rpa_task / rpa_workflow
--   enterprise_organization / enterprise_department / enterprise_business_line
--   rpa_agent_task / rpa_agent_subtask / rpa_agent_coordination_state
--   rpa_skill_meta / rpa_workflow_template
--   rpa_needs_human_queue / rpa_risk_keyword / rpa_approval_request
--   rpa_notification_attempt / rpa_notification_channel_config
--   rpa_approval_timeout_config / rpa_approval_route_config
--   sys_password_policy / sys_login_policy
SET search_path = finrpa;

-- 1. 触发器函数：仅当 NEW.update_time 未被显式赋值（或与 OLD 相同）时刷新为 NOW()
--    使用 COALESCE + NULLIF 兼容两种情况：显式传入旧值 / 未传入（保持默认）
CREATE OR REPLACE FUNCTION set_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    -- 若 NEW.update_time 与 OLD.update_time 相同（说明应用层未显式更新），刷新为当前时间
    -- 若应用层显式传入了新的 update_time，则保留应用层传入的值
    IF NEW.update_time IS NOT DISTINCT FROM OLD.update_time THEN
        NEW.update_time = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. 为所有含 update_time 列的业务表挂载触发器
--    使用 DROP IF EXISTS + CREATE 保证幂等（迁移可重复执行）

-- region 核心表（V2）
DROP TRIGGER IF EXISTS trg_update_time_sys_user ON sys_user;
CREATE TRIGGER trg_update_time_sys_user
    BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_sys_role ON sys_role;
CREATE TRIGGER trg_update_time_sys_role
    BEFORE UPDATE ON sys_role
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_sys_permission ON sys_permission;
CREATE TRIGGER trg_update_time_sys_permission
    BEFORE UPDATE ON sys_permission
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_sys_config ON sys_config;
CREATE TRIGGER trg_update_time_sys_config
    BEFORE UPDATE ON sys_config
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_sys_dictionary ON sys_dictionary;
CREATE TRIGGER trg_update_time_sys_dictionary
    BEFORE UPDATE ON sys_dictionary
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_approval ON rpa_approval;
CREATE TRIGGER trg_update_time_rpa_approval
    BEFORE UPDATE ON rpa_approval
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_task ON rpa_task;
CREATE TRIGGER trg_update_time_rpa_task
    BEFORE UPDATE ON rpa_task
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_workflow ON rpa_workflow;
CREATE TRIGGER trg_update_time_rpa_workflow
    BEFORE UPDATE ON rpa_workflow
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();
-- endregion

-- region 租户表（V6）
DROP TRIGGER IF EXISTS trg_update_time_enterprise_organization ON enterprise_organization;
CREATE TRIGGER trg_update_time_enterprise_organization
    BEFORE UPDATE ON enterprise_organization
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_enterprise_department ON enterprise_department;
CREATE TRIGGER trg_update_time_enterprise_department
    BEFORE UPDATE ON enterprise_department
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_enterprise_business_line ON enterprise_business_line;
CREATE TRIGGER trg_update_time_enterprise_business_line
    BEFORE UPDATE ON enterprise_business_line
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();
-- endregion

-- region Agent 表（V7）
DROP TRIGGER IF EXISTS trg_update_time_rpa_agent_task ON rpa_agent_task;
CREATE TRIGGER trg_update_time_rpa_agent_task
    BEFORE UPDATE ON rpa_agent_task
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_agent_subtask ON rpa_agent_subtask;
CREATE TRIGGER trg_update_time_rpa_agent_subtask
    BEFORE UPDATE ON rpa_agent_subtask
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_agent_coordination_state ON rpa_agent_coordination_state;
CREATE TRIGGER trg_update_time_rpa_agent_coordination_state
    BEFORE UPDATE ON rpa_agent_coordination_state
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();
-- endregion

-- region Skill / Workflow 模板表（V8 / V9）
DROP TRIGGER IF EXISTS trg_update_time_rpa_skill_meta ON rpa_skill_meta;
CREATE TRIGGER trg_update_time_rpa_skill_meta
    BEFORE UPDATE ON rpa_skill_meta
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_workflow_template ON rpa_workflow_template;
CREATE TRIGGER trg_update_time_rpa_workflow_template
    BEFORE UPDATE ON rpa_workflow_template
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();
-- endregion

-- region 审批 / 通知 / 关键词表（V13-V16 / V21-V23）
DROP TRIGGER IF EXISTS trg_update_time_rpa_needs_human_queue ON rpa_needs_human_queue;
CREATE TRIGGER trg_update_time_rpa_needs_human_queue
    BEFORE UPDATE ON rpa_needs_human_queue
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_risk_keyword ON rpa_risk_keyword;
CREATE TRIGGER trg_update_time_rpa_risk_keyword
    BEFORE UPDATE ON rpa_risk_keyword
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_approval_request ON rpa_approval_request;
CREATE TRIGGER trg_update_time_rpa_approval_request
    BEFORE UPDATE ON rpa_approval_request
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_notification_attempt ON rpa_notification_attempt;
CREATE TRIGGER trg_update_time_rpa_notification_attempt
    BEFORE UPDATE ON rpa_notification_attempt
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_notification_channel_config ON rpa_notification_channel_config;
CREATE TRIGGER trg_update_time_rpa_notification_channel_config
    BEFORE UPDATE ON rpa_notification_channel_config
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_approval_timeout_config ON rpa_approval_timeout_config;
CREATE TRIGGER trg_update_time_rpa_approval_timeout_config
    BEFORE UPDATE ON rpa_approval_timeout_config
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_rpa_approval_route_config ON rpa_approval_route_config;
CREATE TRIGGER trg_update_time_rpa_approval_route_config
    BEFORE UPDATE ON rpa_approval_route_config
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();
-- endregion

-- region 策略表（V24 / V25）
DROP TRIGGER IF EXISTS trg_update_time_sys_password_policy ON sys_password_policy;
CREATE TRIGGER trg_update_time_sys_password_policy
    BEFORE UPDATE ON sys_password_policy
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();

DROP TRIGGER IF EXISTS trg_update_time_sys_login_policy ON sys_login_policy;
CREATE TRIGGER trg_update_time_sys_login_policy
    BEFORE UPDATE ON sys_login_policy
    FOR EACH ROW EXECUTE FUNCTION set_update_timestamp();
-- endregion

-- 3. 历史数据说明：
--    历史已终态任务的 update_time = create_time（触发器缺失期间的数据），
--    无法精确恢复真实结束时间，不做人为篡改。
--    这些历史任务在任务列表中耗时将显示为 0s（前端 formatDuration 对 ms=0 返回 "0s"），
--    属于已知历史数据缺陷，新任务由上述触发器保证 update_time 正确刷新。

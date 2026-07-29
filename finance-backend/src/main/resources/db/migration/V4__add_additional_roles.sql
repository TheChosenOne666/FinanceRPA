SET search_path = finrpa;

INSERT INTO sys_role (role_id, role_name, role_code, description, org_id, is_cross_org_read, is_cross_org_approve)
SELECT 4, '组织管理员', 'org_admin', '组织默认管理员，管理本组织所有资源', NULL, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'org_admin');

INSERT INTO sys_role (role_id, role_name, role_code, description, org_id, is_cross_org_read, is_cross_org_approve)
SELECT 5, '平台管理员', 'super_admin', '平台超级管理员，拥有全平台最高权限', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'super_admin');

INSERT INTO sys_role (role_id, role_name, role_code, description, org_id, is_cross_org_read, is_cross_org_approve)
SELECT 6, '操作员', 'operator', '业务操作员，负责日常业务操作', NULL, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'operator');

INSERT INTO sys_role (role_id, role_name, role_code, description, org_id, is_cross_org_read, is_cross_org_approve)
SELECT 7, '审批员', 'approver', '审批员，负责审批业务申请', NULL, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'approver');

INSERT INTO sys_role (role_id, role_name, role_code, description, org_id, is_cross_org_read, is_cross_org_approve)
SELECT 8, '查看员', 'viewer', '只读用户，仅能查看数据', NULL, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'viewer');

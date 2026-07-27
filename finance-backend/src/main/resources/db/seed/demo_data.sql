SET search_path = finrpa;

INSERT INTO sys_user (user_id, username, password, real_name, org_id, org_name, dept_name, status, deleted)
SELECT uuid_generate_v4(), 'banking_admin', '$2b$10$8XsgDVGRtvGl.9Bj5eoR7.XCAbRDCtDQdy.DU66AV/3IVkpS4qqWu', '平台管理员', 'o_demo_cmb', '招商银行演示', '信息技术部', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'banking_admin');

INSERT INTO sys_user (user_id, username, password, real_name, org_id, org_name, dept_name, status, deleted)
SELECT uuid_generate_v4(), 'credit_operator', '$2b$10$8XsgDVGRtvGl.9Bj5eoR7.XCAbRDCtDQdy.DU66AV/3IVkpS4qqWu', '对公信贷操作员', 'o_demo_cmb', '招商银行演示', '对公信贷部', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'credit_operator');

INSERT INTO sys_user (user_id, username, password, real_name, org_id, org_name, dept_name, status, deleted)
SELECT uuid_generate_v4(), 'credit_approver', '$2b$10$8XsgDVGRtvGl.9Bj5eoR7.XCAbRDCtDQdy.DU66AV/3IVkpS4qqWu', '对公信贷审批员', 'o_demo_cmb', '招商银行演示', '对公信贷部', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'credit_approver');

INSERT INTO sys_user (user_id, username, password, real_name, org_id, org_name, dept_name, status, deleted)
SELECT uuid_generate_v4(), 'risk_viewer', '$2b$10$8XsgDVGRtvGl.9Bj5eoR7.XCAbRDCtDQdy.DU66AV/3IVkpS4qqWu', '风险管理员', 'o_demo_cmb', '招商银行演示', '风险管理部', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'risk_viewer');

INSERT INTO sys_user (user_id, username, password, real_name, org_id, org_name, dept_name, status, deleted)
SELECT uuid_generate_v4(), 'compliance_approver', '$2b$10$8XsgDVGRtvGl.9Bj5eoR7.XCAbRDCtDQdy.DU66AV/3IVkpS4qqWu', '合规审计员', 'o_demo_cmb', '招商银行演示', '合规审计部', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'compliance_approver');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.username = 'banking_admin' AND r.role_code = 'super_admin'
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur 
    WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.username = 'credit_operator' AND r.role_code = 'operator'
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur 
    WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.username = 'credit_approver' AND r.role_code = 'approver'
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur 
    WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.username = 'risk_viewer' AND r.role_code = 'viewer'
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur 
    WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.username = 'compliance_approver' AND r.role_code = 'approver'
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur 
    WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
);
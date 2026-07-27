SET search_path = finrpa;

INSERT INTO sys_user (user_id, username, password, real_name, org_id, org_name, dept_name, status, deleted)
SELECT uuid_generate_v4(), 'admin', '$2b$10$8XsgDVGRtvGl.9Bj5eoR7.XCAbRDCtDQdy.DU66AV/3IVkpS4qqWu', '系统管理员', '小楼金融', '小楼金融', '管理部', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'org_admin'
AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur 
    WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
);
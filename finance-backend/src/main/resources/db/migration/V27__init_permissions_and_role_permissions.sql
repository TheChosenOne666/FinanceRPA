-- P3 USR-3 权限矩阵可视化：初始化权限点 + 内置角色权限关联
-- 复用 V2 已建的 sys_permission + sys_role_permission 表
-- permission_id 使用固定值 1-12，便于内置角色权限关联引用
-- 内置角色 role_id（V2/V4 迁移）：super_admin=5 / org_admin=4 / operator=6 / approver=7 / viewer=8

-- ===== 1. 初始化 12 个权限点 =====
INSERT INTO sys_permission (permission_id, permission_code, permission_name, resource_type, resource_path, parent_id, sort_order, status, deleted)
VALUES
    (1,  '*',                '全部权限',     'all',      NULL,           0, 0,  1, 0),
    (2,  'user:manage',      '用户管理',     'user',     '/api/users',   0, 10, 1, 0),
    (3,  'role:manage',      '角色管理',     'role',     '/api/roles',   0, 20, 1, 0),
    (4,  'org:manage',       '组织管理',     'org',      '/api/tenant',  0, 30, 1, 0),
    (5,  'task:create',      '任务创建',     'task',     '/api/tasks',   0, 40, 1, 0),
    (6,  'task:update',      '任务更新',     'task',     '/api/tasks',   0, 50, 1, 0),
    (7,  'task:delete',      '任务删除',     'task',     '/api/tasks',   0, 60, 1, 0),
    (8,  'task:execute',      '任务执行',     'task',     '/api/tasks',   0, 70, 1, 0),
    (9,  'task:view',        '任务查看',     'task',     '/api/tasks',   0, 80, 1, 0),
    (10, 'task:approve',     '任务审批',     'task',     '/api/approvals',0, 90, 1, 0),
    (11, 'workflow:approve', '工作流审批',   'workflow', '/api/workflows',0, 100, 1, 0),
    (12, 'report:view',      '报表查看',     'report',   '/api/dashboard',0, 110, 1, 0)
ON CONFLICT (permission_id) DO NOTHING;

-- ===== 2. 初始化内置角色权限关联（与 PermissionServiceImpl.getPermissionsByRole 硬编码一致） =====
-- super_admin (role_id=5): 全部权限 (permission_id=1)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 5, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1);

-- org_admin (role_id=4): user:manage(2) / role:manage(3) / org:manage(4)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, perm_id FROM (VALUES (2), (3), (4)) AS t(perm_id)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 4 AND rp.permission_id = t.perm_id
);

-- operator (role_id=6): task:create(5) / task:update(6) / task:delete(7) / task:execute(8)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 6, perm_id FROM (VALUES (5), (6), (7), (8)) AS t(perm_id)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 6 AND rp.permission_id = t.perm_id
);

-- approver (role_id=7): task:approve(10) / workflow:approve(11)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 7, perm_id FROM (VALUES (10), (11)) AS t(perm_id)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 7 AND rp.permission_id = t.perm_id
);

-- viewer (role_id=8): task:view(9) / report:view(12)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 8, perm_id FROM (VALUES (9), (12)) AS t(perm_id)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 8 AND rp.permission_id = t.perm_id
);

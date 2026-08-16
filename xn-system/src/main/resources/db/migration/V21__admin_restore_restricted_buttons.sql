-- 管理员受限模块：把 V20 收回的按钮/表格按钮加回，写接口仍只留给超管。可重复执行。

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'permission-content:create',
    'permission-content:table-edit',
    'permission-content:table-delete',
    'route:create',
    'route:update',
    'route:delete',
    'route:add-child',
    'route:generate',
    'route:table-edit',
    'route:table-delete',
    'role:create',
    'role:update',
    'role:delete',
    'role:assign',
    'role:table-edit',
    'role:table-delete',
    'user:create',
    'user:update',
    'user:delete',
    'user:import',
    'user:export',
    'user:table-edit',
    'user:table-delete',
    'unit:create',
    'unit:update',
    'unit:delete',
    'unit:add-child',
    'unit:assign',
    'unit:table-edit',
    'unit:table-delete',
    'post:create',
    'post:update',
    'post:delete',
    'post:import',
    'post:export',
    'post:table-edit',
    'post:table-delete',
    'system-config:update',
    'remote-storage:create',
    'remote-storage:update',
    'remote-storage:delete',
    'remote-storage:table-edit',
    'remote-storage:table-delete',
    'security-policy:update',
    'security-policy:table-unlock'
)
WHERE r.code = 'ADMIN'
  AND p.type IN ('BUTTON', 'TABLE_BUTTON')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

UPDATE sys_role
SET description = '日常管理；组织/权限/系统设置类模块按钮可见，写接口仅超管'
WHERE code = 'ADMIN';

-- 游客 GUEST 并入普通用户 USER 后删除。可重复执行。

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT u.id, gp.permission_id
FROM sys_role g
JOIN sys_role_permission gp ON gp.role_id = g.id
JOIN sys_role u ON u.code = 'USER'
WHERE g.code = 'GUEST'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission up
      WHERE up.role_id = u.id
        AND up.permission_id = gp.permission_id
  );

INSERT INTO sys_user_role (user_id, role_id)
SELECT ur.user_id, u.id
FROM sys_user_role ur
JOIN sys_role g ON g.id = ur.role_id AND g.code = 'GUEST'
JOIN sys_role u ON u.code = 'USER'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_user_role x
    WHERE x.user_id = ur.user_id
      AND x.role_id = u.id
);

DELETE ur
FROM sys_user_role ur
JOIN sys_role g ON g.id = ur.role_id AND g.code = 'GUEST';

UPDATE sys_user
SET role = 'USER'
WHERE role = 'GUEST';

INSERT INTO sys_unit_role (unit_id, role_id)
SELECT ur.unit_id, u.id
FROM sys_unit_role ur
JOIN sys_role g ON g.id = ur.role_id AND g.code = 'GUEST'
JOIN sys_role u ON u.code = 'USER'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_unit_role x
    WHERE x.unit_id = ur.unit_id
      AND x.role_id = u.id
);

DELETE ur
FROM sys_unit_role ur
JOIN sys_role g ON g.id = ur.role_id AND g.code = 'GUEST';

DELETE rp
FROM sys_role_permission rp
JOIN sys_role g ON g.id = rp.role_id AND g.code = 'GUEST';

DELETE FROM sys_role WHERE code = 'GUEST';

UPDATE sys_role
SET description = '全量菜单与查询权限；不含新增/修改/删除/清空/导入/导出等写操作'
WHERE code = 'USER';

-- 管理员不可改个人信息（资料/头像）；改密接口保留。可重复执行。

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id AND r.code = 'ADMIN'
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.code IN (
    'api:PUT:/api/auth/me',
    'api:POST:/api/auth/me/avatar'
);

-- 管理员不可自助改密。可重复执行。

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id AND r.code = 'ADMIN'
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.code = 'api:PUT:/api/auth/me/password';

-- 内置角色 / 种子账号 / 演示组织树：只在本脚本写入一次，启动 Java 不再回写。
-- 已有库用 WHERE NOT EXISTS，不覆盖名称、密码、单位归属。
-- Flyway 早于 Hibernate，空库需先建表。

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    status INT NOT NULL DEFAULT 1,
    built_in BIT NOT NULL DEFAULT 0,
    data_scope VARCHAR(30) NOT NULL DEFAULT 'UNIT_AND_CHILDREN',
    CONSTRAINT uk_sys_role_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT NULL,
    description VARCHAR(200),
    sort INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 1,
    built_in BIT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_unit_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar VARCHAR(500),
    status INT NOT NULL DEFAULT 1,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    unit_id BIGINT NULL,
    post_id BIGINT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    password_changed_at DATETIME(6),
    pwd_force_change TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_unit_role (
    unit_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (unit_id, role_id)
);

INSERT INTO sys_role (code, name, description, status, built_in, data_scope)
SELECT 'SUPER_ADMIN', '超级管理员', '拥有全部权限，系统兜底角色', 1, 1, 'ALL'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'SUPER_ADMIN');

INSERT INTO sys_role (code, name, description, status, built_in, data_scope)
SELECT 'ADMIN', '管理员', '日常管理，含用户/角色/权限', 1, 1, 'ALL'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'ADMIN');

INSERT INTO sys_role (code, name, description, status, built_in, data_scope)
SELECT 'USER', '普通用户', '工作台与只读权限', 1, 1, 'UNIT_AND_CHILDREN'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'USER');

INSERT INTO sys_role (code, name, description, status, built_in, data_scope)
SELECT 'GUEST', '游客', '全量菜单与查询权限；不含新增/修改/删除/清空/导入/导出等写操作', 1, 1, 'UNIT_AND_CHILDREN'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'GUEST');

INSERT INTO sys_unit (code, name, parent_id, description, sort, status, built_in)
SELECT 'XN', '心念科技', NULL, '心念科技', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE code = 'XN');

INSERT INTO sys_unit (code, name, parent_id, description, sort, status, built_in)
SELECT 'XN_XA', '西安分公司', (SELECT id FROM sys_unit WHERE code = 'XN'), '西安分公司', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE code = 'XN_XA');

INSERT INTO sys_unit (code, name, parent_id, description, sort, status, built_in)
SELECT 'XN_XA_RD', '研发部门', (SELECT id FROM sys_unit WHERE code = 'XN_XA'), '研发部门', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE code = 'XN_XA_RD');

INSERT INTO sys_unit (code, name, parent_id, description, sort, status, built_in)
SELECT 'XN_XA_MKT', '市场部门', (SELECT id FROM sys_unit WHERE code = 'XN_XA'), '市场部门', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE code = 'XN_XA_MKT');

INSERT INTO sys_unit (code, name, parent_id, description, sort, status, built_in)
SELECT 'XN_XA_QA', '测试部门', (SELECT id FROM sys_unit WHERE code = 'XN_XA'), '测试部门', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE code = 'XN_XA_QA');

INSERT INTO sys_unit (code, name, parent_id, description, sort, status, built_in)
SELECT 'XN_XA_FIN', '财务部门', (SELECT id FROM sys_unit WHERE code = 'XN_XA'), '财务部门', 4, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE code = 'XN_XA_FIN');

INSERT INTO sys_unit (code, name, parent_id, description, sort, status, built_in)
SELECT 'XN_XA_OPS', '运维部门', (SELECT id FROM sys_unit WHERE code = 'XN_XA'), '运维部门', 5, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_unit WHERE code = 'XN_XA_OPS');

INSERT INTO sys_unit_role (unit_id, role_id)
SELECT u.id, r.id
FROM sys_unit u
CROSS JOIN sys_role r
WHERE u.code IN ('XN', 'XN_XA', 'XN_XA_RD', 'XN_XA_MKT', 'XN_XA_QA', 'XN_XA_FIN', 'XN_XA_OPS')
  AND r.code = 'USER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_unit_role ur WHERE ur.unit_id = u.id AND ur.role_id = r.id
  );

-- 默认口令：SuperAdmin / admin / guest（与用户名相同）。已有账号不改密码。
INSERT INTO sys_user (username, password, nickname, email, phone, status, role, unit_id, created_at, updated_at, pwd_force_change)
SELECT 'SuperAdmin',
       '$2b$10$HTKGuwPMCIHIQhqJjs5Cl.uxBexzVbQ2e4qMzDh8C0AFeid7JKX7y',
       '超级管理员', NULL, NULL, 1, 'ADMIN',
       (SELECT id FROM sys_unit WHERE code = 'XN'),
       NOW(6), NOW(6), 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user WHERE username IN ('SuperAdmin', 'SUPER_ADMIN', 'superadmin', 'superAdmin', 'SUPERADMIN')
);

INSERT INTO sys_user (username, password, nickname, email, phone, status, role, unit_id, created_at, updated_at, pwd_force_change)
SELECT 'admin',
       '$2b$10$3ja1qepTD.LSCBWimNcCDOFeUQdfg8SscNOTEJPV6q9ojGsn4uPfy',
       '管理员', 'admin@smartadmin.com', '13800000001', 1, 'ADMIN',
       (SELECT id FROM sys_unit WHERE code = 'XN'),
       NOW(6), NOW(6), 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user WHERE username IN ('admin', 'Admin', 'ADMIN', 'sysadmin')
);

INSERT INTO sys_user (username, password, nickname, email, phone, status, role, unit_id, created_at, updated_at, pwd_force_change)
SELECT 'guest',
       '$2b$10$L9AkTe7xsYiNZ7tMgIT1C.bO.AppV2uih3Clh0OB.OundKGOcdajC',
       '游客', NULL, NULL, 1, 'GUEST',
       (SELECT id FROM sys_unit WHERE code = 'XN'),
       NOW(6), NOW(6), 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user WHERE username IN ('guest', 'Guest', 'GUEST')
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'SUPER_ADMIN'
WHERE u.username = 'SuperAdmin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'GUEST'
WHERE u.username = 'guest'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

UPDATE sys_user u
JOIN sys_unit unit ON unit.code = 'XN'
SET u.unit_id = unit.id
WHERE u.username IN ('SuperAdmin', 'admin', 'guest')
  AND u.unit_id IS NULL
  AND u.deleted_at IS NULL;

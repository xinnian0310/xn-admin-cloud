-- 内置菜单路由：只在本脚本写入一次，启动 Java 不再回写标题/图标/排序/父子。
-- 已有库按 path（菜单）或 permission+type=DIR（目录）跳过，不覆盖后台改过的数据。

CREATE TABLE IF NOT EXISTS sys_route (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    path VARCHAR(200),
    view_path VARCHAR(200),
    link_url VARCHAR(500),
    icon VARCHAR(50),
    icon_antd VARCHAR(80),
    permission VARCHAR(100),
    parent_id BIGINT,
    type VARCHAR(20) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 1,
    hidden BIT NOT NULL DEFAULT 0,
    affix BIT NOT NULL DEFAULT 0,
    permission_control BIT NOT NULL DEFAULT 0,
    built_in BIT NOT NULL DEFAULT 0
);

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '首页', '/dashboard', 'dashboard', 'HomeFilled', 'HomeOutlined', 'menu:dashboard', NULL, 'MENU', 1, 1, 0, 1, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/dashboard');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '个人中心', NULL, NULL, 'UserFilled', 'UserOutlined', 'menu:personal', NULL, 'DIR', 2, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:personal');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '系统监控', NULL, NULL, 'Monitor', 'MonitorOutlined', 'menu:monitor', NULL, 'DIR', 3, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:monitor');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '系统管理', NULL, NULL, 'Setting', 'SettingOutlined', 'menu:system', NULL, 'DIR', 4, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '组件演示', NULL, NULL, 'Grid', 'AppstoreOutlined', 'menu:demo', NULL, 'DIR', 5, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:demo');

SET @personal_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:personal' LIMIT 1);
SET @monitor_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:monitor' LIMIT 1);
SET @system_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system' LIMIT 1);
SET @demo_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:demo' LIMIT 1);

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '个人信息', '/profile', 'profile', 'UserFilled', 'UserOutlined', 'menu:profile', @personal_id, 'MENU', 1, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/profile');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '我的消息', '/messages/mine', 'messages/mine', 'ChatDotRound', 'MessageOutlined', 'menu:personal:message', @personal_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/messages/mine');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '在线用户', '/monitor/online', 'monitor/online', 'Connection', 'ApiOutlined', 'menu:monitor:online', @monitor_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/monitor/online');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '服务监控', '/monitor/server', 'monitor/server', 'Cpu', 'CloudServerOutlined', 'menu:monitor:server', @monitor_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/monitor/server');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '缓存监控', '/monitor/redis', 'monitor/redis', 'Coin', 'DatabaseOutlined', 'menu:monitor:redis', @monitor_id, 'MENU', 3, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/monitor/redis');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT 'SQL监控', '/monitor/sql', 'monitor/sql', 'DataLine', 'LineChartOutlined', 'menu:monitor:sql', @monitor_id, 'MENU', 4, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/monitor/sql');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '日志管理', NULL, NULL, 'Document', 'FileTextOutlined', 'menu:monitor:logs', @monitor_id, 'DIR', 5, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:monitor:logs');

SET @logs_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:monitor:logs' LIMIT 1);

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '登录日志', '/system/logs/login', 'system/logs/login', 'Position', 'LoginOutlined', 'menu:system:login-log', @logs_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/logs/login');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '操作日志', '/system/logs/oper', 'system/logs/oper', 'Document', 'FileTextOutlined', 'menu:system:oper-log', @logs_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/logs/oper');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '异常日志', '/system/logs/exception', 'system/logs/exception', 'Warning', 'WarningOutlined', 'menu:system:exception-log', @logs_id, 'MENU', 3, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/logs/exception');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '组织与账号', NULL, NULL, 'OfficeBuilding', 'BankOutlined', 'menu:system:org', @system_id, 'DIR', 1, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:org');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '权限与安全', NULL, NULL, 'Lock', 'LockOutlined', 'menu:system:rbac', @system_id, 'DIR', 2, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:rbac');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '内容运营', NULL, NULL, 'Notebook', 'ReadOutlined', 'menu:system:content', @system_id, 'DIR', 3, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:content');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '基础数据', NULL, NULL, 'Collection', 'AppstoreOutlined', 'menu:system:base', @system_id, 'DIR', 4, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:base');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '系统设置', NULL, NULL, 'Tools', 'ToolOutlined', 'menu:system:settings', @system_id, 'DIR', 5, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:settings');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '系统工具', NULL, NULL, 'Suitcase', 'LaptopOutlined', 'menu:system:tools', @system_id, 'DIR', 6, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:tools');

SET @org_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:org' LIMIT 1);
SET @rbac_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:rbac' LIMIT 1);
SET @content_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:content' LIMIT 1);
SET @base_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:base' LIMIT 1);
SET @settings_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:settings' LIMIT 1);
SET @tools_id := (SELECT id FROM sys_route WHERE type = 'DIR' AND permission = 'menu:system:tools' LIMIT 1);

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '用户管理', '/users', 'users', 'User', 'UserOutlined', 'menu:system:user', @org_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/users');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '单位管理', '/system/units', 'system/units', 'OfficeBuilding', 'BankOutlined', 'menu:system:unit', @org_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/units');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '岗位管理', '/system/posts', 'system/posts', 'Postcard', 'IdcardOutlined', 'menu:system:post', @org_id, 'MENU', 3, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/posts');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '角色列表', '/system/roles', 'system/roles', 'Avatar', 'TeamOutlined', 'menu:system:role', @rbac_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/roles');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '角色权限', '/system/permissions', 'system/permissions', 'SetUp', 'ControlOutlined', 'menu:system:permission', @rbac_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/permissions');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '权限内容', '/system/permissions-content', 'system/permissions-content', 'Key', 'KeyOutlined', 'menu:system:permission-content', @rbac_id, 'MENU', 3, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/permissions-content');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '路由管理', '/system/routes', 'system/routes', 'Guide', 'CompassOutlined', 'menu:system:route', @rbac_id, 'MENU', 4, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/routes');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '公告管理', '/system/notices', 'system/notices', 'Bell', 'BellOutlined', 'menu:system:notice', @content_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/notices');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '站内信', '/system/messages', 'system/messages', 'Message', 'MailOutlined', 'menu:system:message', @content_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/messages');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '字典管理', '/system/dicts', 'system/dicts', 'Collection', 'BookOutlined', 'menu:system:dict', @base_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/dicts');

SET @dict_id := (SELECT id FROM sys_route WHERE path = '/system/dicts' LIMIT 1);

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '字典数据', '/system/dicts/data', 'system/dicts/data', 'Collection', 'BookOutlined', 'menu:system:dict-data', @dict_id, 'MENU', 1, 1, 1, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/dicts/data');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '登录页设置', '/system/login-settings', 'system/login-settings', 'PictureFilled', 'PictureOutlined', 'menu:system:login-page', @settings_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/login-settings');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '系统配置', '/system/config', 'system/config', 'Setting', 'SettingOutlined', 'menu:system:config', @settings_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/config');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '远程连接配置', '/system/remote-storage', 'system/remote-storage', 'Link', 'LinkOutlined', 'menu:system:remote-storage', @settings_id, 'MENU', 3, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/remote-storage');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '安全策略', '/system/security', 'system/security', 'Key', 'SafetyCertificateOutlined', 'menu:system:security', @settings_id, 'MENU', 4, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/security');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '联系与捐赠', '/system/site-contact', 'system/site-contact', 'Phone', 'PhoneOutlined', 'menu:system:site-contact', @settings_id, 'MENU', 5, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/site-contact');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '文件管理', '/system/files', 'system/files', 'FolderOpened', 'FolderOpenOutlined', 'menu:system:file', @tools_id, 'MENU', 1, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/files');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '定时任务', '/system/jobs', 'system/jobs', 'Timer', 'FieldTimeOutlined', 'menu:system:job', @tools_id, 'MENU', 2, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/jobs');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '任务日志', '/system/jobs/logs', 'system/jobs/logs', 'Document', 'FileSearchOutlined', 'menu:system:job-log', @tools_id, 'MENU', 3, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/jobs/logs');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '接口文档', '/system/api-docs', 'system/api-docs', 'Document', 'ApiOutlined', 'menu:system:api-docs', @tools_id, 'MENU', 4, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/api-docs');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '回收站', '/system/recycle', 'system/recycle', 'Delete', 'DeleteOutlined', 'menu:system:recycle', @tools_id, 'MENU', 5, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/recycle');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '代码生成', '/system/codegen', 'system/codegen', 'MagicStick', 'CodeOutlined', 'menu:system:codegen', @tools_id, 'MENU', 6, 1, 0, 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/system/codegen');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '基础组件', '/demos/ui', 'demos/ui', 'Brush', 'BgColorsOutlined', 'menu:demo:ui', @demo_id, 'MENU', 1, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/demos/ui');

INSERT INTO sys_route (title, path, view_path, icon, icon_antd, permission, parent_id, type, sort, status, hidden, affix, permission_control, built_in)
SELECT '系统组件', '/demos/xn', 'demos/xn', 'Box', 'BlockOutlined', 'menu:demo:xn', @demo_id, 'MENU', 2, 1, 0, 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_route WHERE path = '/demos/xn');

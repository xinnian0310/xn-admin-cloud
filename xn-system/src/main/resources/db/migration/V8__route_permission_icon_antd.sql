-- Add optional Ant Design icon field for multi-frontend menu icons.
-- Vue continues using `icon` (Element Plus); React prefers `icon_antd`.

SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_route' AND COLUMN_NAME = 'icon_antd'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE sys_route ADD COLUMN icon_antd VARCHAR(80) NULL COMMENT ''Ant Design / Iconify icon for React frontend'' AFTER icon',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists2 := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_permission' AND COLUMN_NAME = 'icon_antd'
);
SET @sql2 := IF(
  @exists2 = 0,
  'ALTER TABLE sys_permission ADD COLUMN icon_antd VARCHAR(80) NULL COMMENT ''Ant Design / Iconify icon for React frontend'' AFTER icon',
  'SELECT 1'
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

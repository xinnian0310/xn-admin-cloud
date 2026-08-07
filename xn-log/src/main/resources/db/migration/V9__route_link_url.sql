-- External link (iframe) routes: optional link_url on sys_route.
-- Existing DIR/MENU rows are unchanged (link_url stays NULL).

SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_route' AND COLUMN_NAME = 'link_url'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE sys_route ADD COLUMN link_url VARCHAR(500) NULL COMMENT ''External URL for LINK type (iframe)'' AFTER view_path',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

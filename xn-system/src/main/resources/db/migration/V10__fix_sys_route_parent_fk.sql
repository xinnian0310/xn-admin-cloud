-- 库从 smartadmin 迁到 xn_admin 后，sys_route.parent_id 外键仍指向旧库，导致新建目录的子菜单插入失败。
-- 纠正为同库自引用。

SET @fk_name := (
  SELECT CONSTRAINT_NAME
  FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_route'
    AND COLUMN_NAME = 'parent_id'
    AND REFERENCED_TABLE_NAME = 'sys_route'
  LIMIT 1
);

SET @sql := IF(
  @fk_name IS NULL,
  'SELECT 1',
  CONCAT('ALTER TABLE sys_route DROP FOREIGN KEY `', @fk_name, '`')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE sys_route
  ADD CONSTRAINT fk_sys_route_parent
  FOREIGN KEY (parent_id) REFERENCES sys_route (id)
  ON DELETE RESTRICT
  ON UPDATE RESTRICT;

-- 回收站与软删除字段
CREATE TABLE IF NOT EXISTS sys_recycle_bin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type VARCHAR(20) NOT NULL,
    biz_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500),
    snapshot TEXT,
    deleted_by VARCHAR(50),
    deleted_at DATETIME(6)
);

SET @exist_user_del := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'deleted_at'
);
SET @sql_user_del := IF(@exist_user_del = 0,
  'ALTER TABLE sys_user ADD COLUMN deleted_at DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql_user_del; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist_file_del := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'deleted_at'
);
SET @sql_file_del := IF(@exist_file_del = 0,
  'ALTER TABLE sys_file ADD COLUMN deleted_at DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt2 FROM @sql_file_del; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

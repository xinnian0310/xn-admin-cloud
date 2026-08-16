-- 公告 / 站内信附件：仅存展示文件名 + 对象路径（objectKey）
SET @exist_notice_file_name := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice' AND COLUMN_NAME = 'file_name'
);
SET @sql_notice_file_name := IF(@exist_notice_file_name = 0,
  'ALTER TABLE sys_notice ADD COLUMN file_name VARCHAR(255) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql_notice_file_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist_notice_file_path := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice' AND COLUMN_NAME = 'file_path'
);
SET @sql_notice_file_path := IF(@exist_notice_file_path = 0,
  'ALTER TABLE sys_notice ADD COLUMN file_path VARCHAR(500) NULL',
  'SELECT 1');
PREPARE stmt2 FROM @sql_notice_file_path; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

SET @exist_message_file_name := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND COLUMN_NAME = 'file_name'
);
SET @sql_message_file_name := IF(@exist_message_file_name = 0,
  'ALTER TABLE sys_message ADD COLUMN file_name VARCHAR(255) NULL',
  'SELECT 1');
PREPARE stmt3 FROM @sql_message_file_name; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

SET @exist_message_file_path := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND COLUMN_NAME = 'file_path'
);
SET @sql_message_file_path := IF(@exist_message_file_path = 0,
  'ALTER TABLE sys_message ADD COLUMN file_path VARCHAR(500) NULL',
  'SELECT 1');
PREPARE stmt4 FROM @sql_message_file_path; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

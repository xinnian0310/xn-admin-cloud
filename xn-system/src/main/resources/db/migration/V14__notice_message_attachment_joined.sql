-- 多附件：file_name / file_path 用逗号拼接，加长列；补 attachments JSON 列
SET @exist_notice_attachments := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice' AND COLUMN_NAME = 'attachments'
);
SET @sql_notice_attachments := IF(@exist_notice_attachments = 0,
  'ALTER TABLE sys_notice ADD COLUMN attachments LONGTEXT NULL',
  'SELECT 1');
PREPARE stmt_na FROM @sql_notice_attachments; EXECUTE stmt_na; DEALLOCATE PREPARE stmt_na;

SET @exist_message_attachments := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND COLUMN_NAME = 'attachments'
);
SET @sql_message_attachments := IF(@exist_message_attachments = 0,
  'ALTER TABLE sys_message ADD COLUMN attachments LONGTEXT NULL',
  'SELECT 1');
PREPARE stmt_ma FROM @sql_message_attachments; EXECUTE stmt_ma; DEALLOCATE PREPARE stmt_ma;

ALTER TABLE sys_notice MODIFY COLUMN file_name VARCHAR(4000) NULL;
ALTER TABLE sys_notice MODIFY COLUMN file_path VARCHAR(8000) NULL;
ALTER TABLE sys_message MODIFY COLUMN file_name VARCHAR(4000) NULL;
ALTER TABLE sys_message MODIFY COLUMN file_path VARCHAR(8000) NULL;

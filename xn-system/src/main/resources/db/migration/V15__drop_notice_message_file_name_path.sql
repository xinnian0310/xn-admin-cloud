-- 旧 file_name / file_path 迁进 attachments 后删列。
-- V13/V14 会加上这两列；列已不在时跳过 UPDATE / DROP，避免重复执行失败。
SET SESSION group_concat_max_len = 65535;

SET @exist_notice_file_name := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice' AND COLUMN_NAME = 'file_name'
);
SET @exist_notice_file_path := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice' AND COLUMN_NAME = 'file_path'
);
SET @exist_message_file_name := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND COLUMN_NAME = 'file_name'
);
SET @exist_message_file_path := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND COLUMN_NAME = 'file_path'
);

-- 公告：空 attachments 时按逗号拆旧列，最多 10 个
SET @sql_notice_migrate := IF(@exist_notice_file_name > 0 AND @exist_notice_file_path > 0,
  'UPDATE sys_notice n JOIN (
    SELECT n2.id, CONCAT(''['', GROUP_CONCAT(
      CONCAT(
        ''{"name":'', JSON_QUOTE(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(n2.file_name, '','', seq.n), '','', -1))),
        '',"path":'', JSON_QUOTE(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(n2.file_path, '','', seq.n), '','', -1))),
        ''}''
      ) ORDER BY seq.n SEPARATOR '',''
    ), '']'') AS js
    FROM sys_notice n2
    JOIN (
      SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
      UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    ) seq
      ON seq.n <= 1 + LENGTH(n2.file_name) - LENGTH(REPLACE(n2.file_name, '','', ''''))
     AND seq.n <= 1 + LENGTH(n2.file_path) - LENGTH(REPLACE(n2.file_path, '','', ''''))
    WHERE (n2.attachments IS NULL OR TRIM(n2.attachments) IN ('''', ''[]'', ''null''))
      AND NULLIF(TRIM(n2.file_name), '''') IS NOT NULL
      AND NULLIF(TRIM(n2.file_path), '''') IS NOT NULL
    GROUP BY n2.id
  ) x ON n.id = x.id SET n.attachments = x.js',
  'SELECT 1');
PREPARE stmt_nm FROM @sql_notice_migrate; EXECUTE stmt_nm; DEALLOCATE PREPARE stmt_nm;

-- 站内信：同上
SET @sql_message_migrate := IF(@exist_message_file_name > 0 AND @exist_message_file_path > 0,
  'UPDATE sys_message n JOIN (
    SELECT n2.id, CONCAT(''['', GROUP_CONCAT(
      CONCAT(
        ''{"name":'', JSON_QUOTE(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(n2.file_name, '','', seq.n), '','', -1))),
        '',"path":'', JSON_QUOTE(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(n2.file_path, '','', seq.n), '','', -1))),
        ''}''
      ) ORDER BY seq.n SEPARATOR '',''
    ), '']'') AS js
    FROM sys_message n2
    JOIN (
      SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
      UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    ) seq
      ON seq.n <= 1 + LENGTH(n2.file_name) - LENGTH(REPLACE(n2.file_name, '','', ''''))
     AND seq.n <= 1 + LENGTH(n2.file_path) - LENGTH(REPLACE(n2.file_path, '','', ''''))
    WHERE (n2.attachments IS NULL OR TRIM(n2.attachments) IN ('''', ''[]'', ''null''))
      AND NULLIF(TRIM(n2.file_name), '''') IS NOT NULL
      AND NULLIF(TRIM(n2.file_path), '''') IS NOT NULL
    GROUP BY n2.id
  ) x ON n.id = x.id SET n.attachments = x.js',
  'SELECT 1');
PREPARE stmt_mm FROM @sql_message_migrate; EXECUTE stmt_mm; DEALLOCATE PREPARE stmt_mm;

SET @sql_notice_drop := IF(@exist_notice_file_name > 0,
  'ALTER TABLE sys_notice DROP COLUMN file_name, DROP COLUMN file_path',
  'SELECT 1');
PREPARE stmt_nd FROM @sql_notice_drop; EXECUTE stmt_nd; DEALLOCATE PREPARE stmt_nd;

SET @sql_message_drop := IF(@exist_message_file_name > 0,
  'ALTER TABLE sys_message DROP COLUMN file_name, DROP COLUMN file_path',
  'SELECT 1');
PREPARE stmt_md FROM @sql_message_drop; EXECUTE stmt_md; DEALLOCATE PREPARE stmt_md;

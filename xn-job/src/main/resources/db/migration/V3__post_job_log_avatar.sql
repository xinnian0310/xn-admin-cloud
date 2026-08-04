-- 岗位、任务日志、用户扩展字段（生产 validate 时依赖本脚本）
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 1,
    remark VARCHAR(200),
    built_in BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT uk_sys_post_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_job_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT,
    job_name VARCHAR(100),
    job_key VARCHAR(100),
    invoke_target VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    message VARCHAR(500),
    exception_info TEXT,
    start_time DATETIME(6),
    end_time DATETIME(6),
    cost_ms BIGINT
);

-- MySQL 8+ 无 IF NOT EXISTS 的 ADD COLUMN 时用存储过程更稳妥；开发环境多用 ddl-auto:update
-- 以下语句若列已存在会失败，可忽略或手工执行
SET @exist_avatar := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar'
);
SET @sql_avatar := IF(@exist_avatar = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(500) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql_avatar; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist_post := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'post_id'
);
SET @sql_post := IF(@exist_post = 0,
  'ALTER TABLE sys_user ADD COLUMN post_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt2 FROM @sql_post; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

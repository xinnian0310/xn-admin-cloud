-- 密码策略字段（挂在安全策略单例）+ 用户密码状态 + 历史密码

SET @exist_pwd_min := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_security_policy' AND COLUMN_NAME = 'pwd_min_length'
);
SET @sql_pwd_min := IF(@exist_pwd_min = 0,
  'ALTER TABLE sys_security_policy
     ADD COLUMN pwd_min_length INT NOT NULL DEFAULT 6,
     ADD COLUMN pwd_max_length INT NOT NULL DEFAULT 50,
     ADD COLUMN pwd_require_upper TINYINT(1) NOT NULL DEFAULT 0,
     ADD COLUMN pwd_require_lower TINYINT(1) NOT NULL DEFAULT 0,
     ADD COLUMN pwd_require_digit TINYINT(1) NOT NULL DEFAULT 0,
     ADD COLUMN pwd_require_special TINYINT(1) NOT NULL DEFAULT 0,
     ADD COLUMN pwd_expire_days INT NOT NULL DEFAULT 0,
     ADD COLUMN pwd_force_change_first TINYINT(1) NOT NULL DEFAULT 1,
     ADD COLUMN pwd_history_count INT NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt_pwd FROM @sql_pwd_min; EXECUTE stmt_pwd; DEALLOCATE PREPARE stmt_pwd;

SET @exist_user_pwd_at := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password_changed_at'
);
SET @sql_user_pwd_at := IF(@exist_user_pwd_at = 0,
  'ALTER TABLE sys_user
     ADD COLUMN password_changed_at DATETIME(6) NULL,
     ADD COLUMN pwd_force_change TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt_user_pwd FROM @sql_user_pwd_at; EXECUTE stmt_user_pwd; DEALLOCATE PREPARE stmt_user_pwd;

CREATE TABLE IF NOT EXISTS sys_user_password_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    INDEX idx_pwd_hist_user (user_id)
);

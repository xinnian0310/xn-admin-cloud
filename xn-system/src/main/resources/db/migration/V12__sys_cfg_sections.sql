-- 系统配置按分区独立表；对象存储为名字/路径行表
CREATE TABLE IF NOT EXISTS sys_cfg_app (
    id BIGINT NOT NULL PRIMARY KEY,
    config_json TEXT NOT NULL,
    updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_cfg_session (
    id BIGINT NOT NULL PRIMARY KEY,
    config_json TEXT NOT NULL,
    updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_cfg_ui (
    id BIGINT NOT NULL PRIMARY KEY,
    config_json TEXT NOT NULL,
    updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_cfg_log_retention (
    id BIGINT NOT NULL PRIMARY KEY,
    config_json TEXT NOT NULL,
    updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_cfg_sensitive_data (
    id BIGINT NOT NULL PRIMARY KEY,
    config_json TEXT NOT NULL,
    updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_cfg_storage (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    updated_at DATETIME(6) NULL,
    UNIQUE KEY uk_sys_cfg_storage_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

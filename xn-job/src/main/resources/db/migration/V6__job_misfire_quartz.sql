-- 定时任务表（原由 JPA 建表；此处固化并补充 Quartz misfire 策略字段）
CREATE TABLE IF NOT EXISTS sys_job (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    job_key         VARCHAR(100) NOT NULL,
    cron            VARCHAR(100) NOT NULL,
    invoke_target   VARCHAR(500) NOT NULL,
    status          INT          NOT NULL,
    remark          VARCHAR(500) NULL,
    concurrent      BIT(1)       NOT NULL,
    misfire_policy  VARCHAR(8)   NOT NULL DEFAULT '0' COMMENT '0默认 1忽略 2补偿执行 3不触发',
    last_run_at     DATETIME(6)  NULL,
    last_status     VARCHAR(20)  NULL,
    last_message    VARCHAR(500) NULL,
    created_at      DATETIME(6)  NULL,
    updated_at      DATETIME(6)  NULL,
    UNIQUE KEY uk_sys_job_key (job_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已有库补列（幂等）
SET @dbname = DATABASE();
SET @preparedStatement = (
    SELECT IF(
        (
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'sys_job'
              AND COLUMN_NAME = 'misfire_policy'
        ) > 0,
        'SELECT 1',
        'ALTER TABLE sys_job ADD COLUMN misfire_policy VARCHAR(8) NOT NULL DEFAULT ''0'' COMMENT ''misfire策略'' AFTER concurrent'
    )
);
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

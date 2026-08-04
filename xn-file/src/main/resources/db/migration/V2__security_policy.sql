-- 安全策略单例表（管理员可在「安全策略」页调整登录锁定/限流）
CREATE TABLE IF NOT EXISTS sys_security_policy (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    max_failures            INT          NOT NULL DEFAULT 5,
    lock_minutes            INT          NOT NULL DEFAULT 15,
    rate_limit_per_minute   INT          NOT NULL DEFAULT 30,
    captcha_ttl_seconds     INT          NOT NULL DEFAULT 120,
    updated_at              DATETIME(6)  NULL
);

INSERT INTO sys_security_policy (id, max_failures, lock_minutes, rate_limit_per_minute, captcha_ttl_seconds, updated_at)
SELECT 1, 5, 15, 30, 120, NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM sys_security_policy WHERE id = 1);

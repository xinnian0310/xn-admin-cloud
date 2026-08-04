-- 站点联系与捐赠配置（单例 JSON，供管理端首页与官网公开拉取）

CREATE TABLE IF NOT EXISTS sys_site_contact (
    id          BIGINT       NOT NULL PRIMARY KEY,
    config_json TEXT         NOT NULL,
    updated_at  DATETIME(6)  NULL
);

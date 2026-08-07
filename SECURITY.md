# 安全政策

## 支持的版本

当前主版本（`1.0.x`）接受安全修复。

## 报告漏洞

请**不要**在公开 Issue / Discussion 中披露未修复的安全漏洞。

请通过仓库的私密渠道联系维护者（Security Advisory、私信或邮件），并尽量提供：

- 影响的服务 / 接口
- 复现步骤与环境（profile、版本）
- 潜在影响与修复建议（如有）

我们会尽快确认，并协调修复与披露时机。

## 部署安全建议

- 生产使用 `SPRING_PROFILES_ACTIVE=prod,cloud`（或 `prod`），**不要**带 `dev`
- 更换 `JWT_SECRET`、数据库、Redis、MinIO、Nacos 等全部默认密钥
- 首次登录后立即修改种子账号密码；生产勿长期使用演示账号
- 参考各服务 `env.example` 配置环境变量，**勿**将真实密钥提交到 Git
- 勿对公网暴露 MySQL / Redis / Nacos / MinIO / 业务服务直连端口；仅暴露网关或反向代理

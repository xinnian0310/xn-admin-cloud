# xn-system

微服务核心业务服务（端口 **8081**）：登录鉴权、RBAC、组织岗位、字典、配置、公告、站内信、监控、回收站、代码生成等。经网关 [`xn-gateway`](../xn-gateway/)（8088）对外提供 `/api/**`（除 files / logs / jobs）。

属于本仓库 [`xn-admin-cloud`](../README.md)；配套前端为独立仓库 **xn-admin-vue3-ts** 等。

## 技术栈

| 类别 | 技术 |
|------|------|
| 运行时 | Java 21、Spring Boot 4.1 |
| Web / 安全 | Spring Web MVC、Spring Security、JJWT |
| 数据 | Spring Data JPA、MySQL、Flyway |
| 发现 / 配置 | Nacos（`dev,cloud` profile） |
| 缓存 / KV | Lettuce + `AppKvStore` |
| 其他 | Validation、AspectJ、WebSocket、Actuator、Lombok |

## 快速启动

要求：JDK 21、MySQL（库 `xn_admin`）、Nacos / Redis / MinIO 已就绪。建议按 [`env.example`](./env.example) 与仓库根 README 配置。

```bash
# Windows（推荐 IDEA Start All Cloud）
set SPRING_PROFILES_ACTIVE=dev,cloud
.\mvnw.cmd spring-boot:run
```

本服务：http://localhost:8081  
统一入口（网关）：http://127.0.0.1:8088

## 关键配置要点

| 配置项 | 说明 |
|--------|------|
| `server.port` | 默认 `8081`（可用 `SERVER_PORT` 覆盖） |
| `spring.datasource.*` | MySQL（库名 `xn_admin`） |
| `app.jwt.secret` / `expiration` | JWT 密钥与有效期 |
| `app.api-guard.enforce` | 生产建议 `true` |
| `app.redis.*` | Redis |

**无需登录即可访问：** `/api/auth/login`、验证码相关、`/api/login-page-configs/active`、`/api/system-config/public`、`/uploads/**`、`/ws/**`、`OPTIONS /**`。

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| SuperAdmin | SuperAdmin | SUPER_ADMIN |
| admin | admin | ADMIN |

## API 分组

经网关访问时路径不变。本服务主要承载：

| 前缀 | 说明 |
|------|------|
| `/api/auth` | 登录、注册、刷新、当前用户、改密、头像、菜单、API 注册表、验证码 |
| `/api/users` | 用户 CRUD、导入导出、状态、批量删除 |
| `/api/roles` | 角色 CRUD、分配权限、数据权限范围 |
| `/api/permissions` | 权限树 / 分组 CRUD |
| `/api/routes` | 路由树 CRUD、按路由代码生成 |
| `/api/units` | 组织单位树、单位默认角色 |
| `/api/posts` | 岗位 CRUD、导入导出、启停 |
| `/api/dict-types` / `/api/dict-data` | 字典类型与数据 |
| `/api/notices` | 公告管理、发布/撤回、我的公告、已读 |
| `/api/messages` | 站内信管理、发送、我的消息 |
| `/api/login-page-configs` | 登录页配置（含公开 `/active`） |
| `/api/system-config` | 系统配置（品牌、会话、UI、存储、日志保留、脱敏） |
| `/api/security-policy` | 登录锁定 / 限流 / 密码策略 / 解锁 |
| `/api/site-contact` | 联系与捐赠（含公开接口） |
| `/api/site-ui-shots` | 站点界面截图（公开） |
| `/api/user-ui-config` | 登录用户个人布局 / 字号 |
| `/api/page-ui` | 当前路由的搜索/按钮 UI |
| `/api/table-columns` | 用户表格列偏好 |
| `/api/attachments` | 附件上传 |
| `/api/codegen` | 按表预览 / 生成代码 |
| `/api/recycle` | 回收站：列表、恢复、彻底删除、清空 |
| `/api/dashboard` | 工作台统计 |
| `/api/monitor` | 在线用户、踢下线、服务 / Redis / SQL 监控 |
| `/ws/notices` | 公告 WebSocket |
| `/uploads/**` | 上传静态资源 |

由其他服务承载：

| 前缀 | 服务 |
|------|------|
| `/api/files` | `xn-file` |
| `/api/logs/**` | `xn-log` |
| `/api/jobs` | `xn-job` |

统一响应：`{ code, message, data }`（`code === 200` 表示成功）。

更完整说明见 [xn-admin-cloud README](../README.md)。

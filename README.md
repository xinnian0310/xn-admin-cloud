# xn-admin-cloud

心念后台 **微服务后端**（Spring Boot 4 + Spring Cloud Gateway + Nacos）。

技术栈：Java 21、Maven 多模块、Flyway、JPA、Redis、MinIO、Quartz、OpenAPI。

版本：`1.0.0` · 许可证：[Apache-2.0](./LICENSE) · Copyright 2026 心念

> 本仓库独立开源。配套四套管理端为**其它独立仓库**（见下方「相关仓库」），不随本仓库一并发布。

## 服务一览

| 服务 | 端口 | 职责 |
|------|------|------|
| **xn-gateway** | 8088 | 统一入口；经 Nacos `lb://` 转发 |
| **xn-system** | 8081 | 登录 / RBAC / 组织岗位 / 字典 / 配置 / 公告站内信 / 监控 / 回收站 / 代码生成… |
| **xn-file** | 8082 | 文件 / 分片上传 / MinIO / 预览 |
| **xn-log** | 8083 | 登录 / 操作 / 异常 / 任务日志（查询、详情、删除、清空、导出） |
| **xn-job** | 8084 | Quartz 定时任务 CRUD / 启停 / 立即执行 |

中间件：MySQL、Redis、MinIO、Nacos（默认库名 `xn_admin`，可按配置修改）。

## 相关仓库

开源范围仅下列仓库（不含官网等其它工程）：

| 仓库 | Gitee | GitHub | 说明 |
|------|-------|--------|------|
| `xn-admin-cloud` | [Gitee](https://gitee.com/jenning/xn-admin-cloud) | [GitHub](https://github.com/xinnian0310/xn-admin-cloud) | 本仓库 |
| `xn-admin-vue3-ts` | [Gitee](https://gitee.com/jenning/xn-admin-vue3-ts) | [GitHub](https://github.com/xinnian0310/xn-admin-vue3-ts) | 基准管理端（Vue 3 + TypeScript + Element Plus） |
| `xn-admin-vue3-js` | [Gitee](https://gitee.com/jenning/xn-admin-vue3-js) | [GitHub](https://github.com/xinnian0310/xn-admin-vue3-js) | Vue 3 + JavaScript（Composition） |
| `xn-admin-vue2-js` | [Gitee](https://gitee.com/jenning/xn-admin-vue2-js) | [GitHub](https://github.com/xinnian0310/xn-admin-vue2-js) | Vue 3 + JavaScript（Options API） |
| `xn-admin-react-ts` | [Gitee](https://gitee.com/jenning/xn-admin-react-ts) | [GitHub](https://github.com/xinnian0310/xn-admin-react-ts) | React 19 + TypeScript + Ant Design |

各前端开发代理默认指向本仓库网关 `http://127.0.0.1:8088`。

## 功能概览

接口与菜单种子已齐；管理端页面由配套前端仓库承接。

| 域 | 后端能力 | 服务 |
| --- | --- | --- |
| 认证会话 | 登录 / 登出 / 注册 / 验证码（图形、滑块）/ JWT 续期 / 改密 / 头像 / 菜单 / API 注册表 | xn-system |
| 组织账号 | 用户（CRUD、导入导出、启停）/ 单位树与默认角色 / 岗位 | xn-system |
| 权限安全 | 角色与数据权限（全部 / 本单位及下级 / 本单位 / 本人）/ 权限内容 / 动态路由 / 安全策略（锁定、限流、密码规则） | xn-system |
| 基础数据 | 字典类型与数据 | xn-system |
| 系统设置 | 系统配置 / 登录页 / 远程存储 / 联系与捐赠 / 用户 UI 偏好 / 表格列偏好 / page-ui | xn-system |
| 内容运营 | 公告（发布、撤回、已读、WebSocket）/ 站内信 | xn-system |
| 监控 | 在线用户与强制下线 / 服务 / Redis / SQL | xn-system |
| 日志 | 登录 / 操作 / 异常 / 任务日志：查询、详情、删除、批量删除、清空、导出 | xn-log |
| 文件 | 浏览、上传、建目录、删除、分片上传 | xn-file |
| 任务 | Quartz CRUD、启停、立即执行 | xn-job |
| 工具 | 回收站 / 代码生成 / 路由一键生成 | xn-system |

**与前端对齐：** 四套管理端已闭环上述绝大多数页面。登录 / 操作 / 异常 / 任务日志的**接口、菜单、page-ui 已种子**，前端页面尚未落地（点菜单会 404）。详见各前端仓库 README。

## 网关路由

| 路径 | Nacos 服务名 |
|------|----------------|
| `/api/files/**` | `xn-file` |
| `/api/logs/**` | `xn-log` |
| `/api/jobs/**` | `xn-job` |
| `/api/**`、`/uploads/**`、`/ws/**` | `xn-system` |

- 注册中心默认：`127.0.0.1:8849`，账号以 Nacos 配置为准（示例环境常见 `nacos/nacos`，生产务必修改）
- 健康检查：http://127.0.0.1:8088/actuator/health
- 常用前端开发端口（其它仓库）：react-ts `1800`、vue2-js `1801`、vue3-js `1802`、vue3-ts `1803`

## 默认账号

首次启动（`dev`）会初始化种子账号（**仅新建时**写入默认密码，之后改密不会被重启覆盖）：

| 用户名 | 初始密码 | 角色 |
|--------|----------|------|
| `SuperAdmin` | `SuperAdmin` | 超级管理员 |
| `admin` | `admin` | 管理员 |

**仅用于本地开发。** 登录后请尽快修改密码。生产环境务必使用 `prod`（或 `prod,cloud`）profile，且勿使用示例密钥。详见 [SECURITY.md](./SECURITY.md)。

## Profile 说明

| Profile | 用途 |
|---------|------|
| `dev` | 本地开发：宽松配置、演示数据初始化、可开 Swagger / infra 重启 |
| `cloud` | 多服务：Nacos 发现与独立端口 |
| `prod` | 正式部署：校验库表、关闭演示清理与危险开关 |

- **本机默认**：`dev,cloud`（见 `application.yml`）
- **正式部署**：设置 `SPRING_PROFILES_ACTIVE=prod,cloud`（或仅 `prod`），**不要带 `dev`**

## 快速启动

### 前提

1. **JDK 21**、Maven 3.9+（本仓库自带 `mvnw`，可不装全局 Maven）
2. **MySQL**、**Redis**、**Nacos**、**MinIO** 已就绪，且库 / 桶 / 账号与配置一致  
   （可用 Docker Compose 或本机安装；端口与账号以各服务 `application-*.yml` / `env.example` 为准）

### Maven（推荐，可复现）

在仓库根目录分别启动（需 5 个终端，或自行写脚本并行）：

```bat
set SPRING_PROFILES_ACTIVE=dev,cloud
mvnw -pl xn-system spring-boot:run
mvnw -pl xn-file spring-boot:run
mvnw -pl xn-log spring-boot:run
mvnw -pl xn-job spring-boot:run
mvnw -pl xn-gateway spring-boot:run
```

Linux / macOS 将 `set` 换为 `export`，并用 `./mvnw`。

各模块目录内也有独立 `mvnw`，可在模块目录执行 `mvnw spring-boot:run`。

启动成功后访问网关：http://127.0.0.1:8088

### IDEA

1. 打开本仓库根目录，等待 Maven 导入完成  
2. 为 `xn-system` / `xn-file` / `xn-log` / `xn-job` / `xn-gateway` 各建一个 Spring Boot 运行配置  
3. Active profiles 填：`dev,cloud`  
4. 五个服务全部启动后访问 http://127.0.0.1:8088  

也可在 IDEA 中自建 Compound 配置一次拉起全部服务。

### 配置与密钥

- 各服务有 `env.example`，可复制为环境变量（**生产勿使用示例密钥**）
- 关键变量示例：`JWT_SECRET`、`DB_USERNAME`、`DB_PASSWORD`、`CORS_ALLOWED_ORIGINS`、`MINIO_*`

生产启动示例：

```bat
set SPRING_PROFILES_ACTIVE=prod,cloud
set JWT_SECRET=请换成至少32字符的随机串
set DB_PASSWORD=请换成强密码
mvnw -pl xn-system spring-boot:run
```

## 工程规范

根目录为统一父 POM + Maven Wrapper（JDK 21）。

| 能力 | 说明 |
|------|------|
| Spotless | Google Java Format（AOSP） |
| SpotBugs | High 级别门禁 |
| JaCoCo | 覆盖率报告（`*/target/site/jacoco/`） |
| Enforcer | 强制 JDK 21、Maven ≥ 3.9 |
| Git Hooks | Conventional Commits + 提交前 Spotless |
| CI | GitHub Actions / Gitee Go |

常用命令：

```bat
mvnw -B spotless:apply
mvnw -B verify
```

- `spotless:apply`：格式化代码  
- `verify`：编译 + 单测 + Spotless check + SpotBugs + JaCoCo  

安装 Hooks（执行过 `mvnw` 也会自动配置）：

```bat
scripts\install-hooks.bat
```

提交格式示例：`feat(system): 增加密码策略`。完整约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。

CI 文件：

- `.github/workflows/ci.yml`（GitHub Actions）
- `.gitee/workflows/ci.yml`（Gitee Go，需在控制台启用流水线）

## Docker

在**仓库根目录**构建（依赖根 `pom.xml` + `mvnw`）：

```bat
docker build -f xn-gateway/Dockerfile .
docker build -f xn-system/Dockerfile .
docker build -f xn-file/Dockerfile .
docker build -f xn-log/Dockerfile .
docker build -f xn-job/Dockerfile .
```

镜像运行时请设置 `SPRING_PROFILES_ACTIVE=prod,cloud` 及数据库 / JWT / MinIO 等环境变量。

## 生产部署（摘要）

- Profile：`prod,cloud`（不要带 `dev`）
- 经 Nginx / 网关对外提供 HTTPS；数据库与中间件仅内网可达
- 镜像构建见上文 Docker；运行时注入数据库 / JWT / MinIO / Nacos 等环境变量
- 完整安全要求见 [SECURITY.md](./SECURITY.md)

## 现状与演进

- 当前为「共享代码 + 按控制器拆进程」：降低拆分风险，业务服务裁剪各自 Controller。  
- 鉴权在各业务服务内完成（JWT）；网关负责路由与发现，不作统一鉴权过滤器。  
- **共享代码同步**：以 `xn-system` 为规范源，改完安全/公共类后执行  
  `scripts/sync-shared-from-system.ps1`（或 `.sh`）；CI 可用 `-Check` / `--check` 防漂移。  
  后续可再抽独立 `xn-common` 模块。  
- 新业务：新增 module（如 `xn-order`），并在 gateway 增加路由即可。

## 支持捐赠

如果这个项目对你有帮助，欢迎请作者喝杯咖啡 ☕

<p align="center">
  <img src="./docs/donation/donate.png" alt="支持捐赠（微信支付 / 支付宝）" width="480" />
</p>

## 许可证

[Apache License 2.0](./LICENSE)

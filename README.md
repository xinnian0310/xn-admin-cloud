# xn-admin-cloud

心念后台 **微服务后端**（Spring Boot 4 + Spring Cloud Gateway + Nacos）。

技术栈：Java 21、Maven 多模块、Flyway、JPA、Redis、MinIO、Quartz、OpenAPI。

版本：`1.0.0` · 许可证：[Apache-2.0](./LICENSE) · Copyright 2026 心念

## 服务一览

| 服务 | 端口 | 职责 |
|------|------|------|
| **xn-gateway** | 8088 | 统一入口；经 Nacos `lb://` 转发 |
| **xn-system** | 8081 | 登录 / 权限 / 组织 / 字典 / 配置 / 公告 / 站内信 / 监控… |
| **xn-file** | 8082 | 文件 / MinIO / 预览 |
| **xn-log** | 8083 | 登录 / 操作 / 异常 / 任务日志 |
| **xn-job** | 8084 | Quartz 定时任务 |

中间件：MySQL、Redis、MinIO、Nacos（库名 `smartadmin`）。

配套前端：与本仓库同级的 [`xn-admin-vue3-ts`](../xn-admin-vue3-ts/)（开发代理指向网关 `8088`）。

## 网关路由

| 路径 | Nacos 服务名 |
|------|----------------|
| `/api/files/**` | `xn-file` |
| `/api/logs/**` | `xn-log` |
| `/api/jobs/**` | `xn-job` |
| `/api/**`、`/uploads/**`、`/ws/**` | `xn-system` |

- 注册中心：`127.0.0.1:8849`（与上层目录 `tool/nacos3` 一致），默认账号 `nacos/nacos`
- 前端开发代理指向网关 `8088`；常用前端端口：react-ts `1800`、vue2-js `1801`、vue3-js `1802`、vue3-ts `1803`、xn-home `8888`
- 健康检查：http://127.0.0.1:8088/actuator/health

## 默认账号

首次启动（`dev`）会初始化种子账号（**仅新建时**写入默认密码，之后改密不会被重启覆盖）：

| 用户名 | 初始密码 | 角色 |
|--------|----------|------|
| `SuperAdmin` | `SuperAdmin` | 超级管理员 |
| `admin` | `admin` | 管理员 |

请尽快修改密码。生产环境务必使用 `prod`（或 `prod,cloud`）profile，且勿使用示例密钥。

## Profile 说明

| Profile | 用途 |
|---------|------|
| `dev` | 本地开发：宽松配置、演示数据初始化、可开 Swagger / infra 重启 |
| `cloud` | 多服务：Nacos 发现与独立端口 |
| `prod` | 正式部署：校验库表、关闭演示清理与危险开关 |

- **本机默认**：`dev,cloud`（见 `application.yml`）
- **正式部署**：设置 `SPRING_PROFILES_ACTIVE=prod,cloud`（或仅 `prod`），**不要带 `dev`**

## 快速启动

前提：MySQL / Redis / Nacos 已就绪（若仓库位于上层 monorepo，可用上层 `启动-tool.bat`）。

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
- 中间件启停：上层 `启动-tool.bat` / `停止-tool.bat`；前端：`启动-前端.bat` / `停止-前端.bat`

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

## 现状与演进

- 当前为「共享代码 + 按控制器拆进程」：降低拆分风险，业务服务裁剪各自 Controller。  
- 鉴权在各业务服务内完成（JWT）；网关负责路由与发现，不作统一鉴权过滤器。  
- 后续可再抽 `xn-common`、Feign/MQ、按服务拆库。  
- 新业务：新增 module（如 `xn-order`），并在 gateway 增加路由即可。

# xn-admin-cloud

心念后台 **微服务后端**（Spring Boot 4 + Spring Cloud Gateway + Nacos）。

技术栈：Java 21、Maven 多模块、Flyway、JPA、Redis、MinIO、Quartz、OpenAPI。

## 服务一览

| 服务 | 端口 | 职责 |
|------|------|------|
| **xn-gateway** | 8088 | 统一入口；经 Nacos `lb://` 转发 |
| **xn-system** | 8081 | 登录 / 权限 / 组织 / 字典 / 配置 / 公告 / 站内信 / 监控… |
| **xn-file** | 8082 | 文件 / MinIO / 预览 |
| **xn-log** | 8083 | 登录 / 操作 / 异常 / 任务日志 |
| **xn-job** | 8084 | Quartz 定时任务 |

中间件：MySQL、Redis、MinIO、Nacos（库名 `smartadmin`）。

## 网关路由

| 路径 | Nacos 服务名 |
|------|----------------|
| `/api/files/**` | `xn-file` |
| `/api/logs/**` | `xn-log` |
| `/api/jobs/**` | `xn-job` |
| `/api/**`、`/uploads/**`、`/ws/**` | `xn-system` |

- 注册中心：`127.0.0.1:8849`（与上层目录 `tool/nacos3` 一致），默认账号 `nacos/nacos`
- 前端开发代理指向网关 `8088`（`xn-admin-vue3-ts/vite.config.ts`），前端端口 `5173`
- 健康检查：http://127.0.0.1:8088/actuator/health

## 快速启动

前提：MySQL / Redis / Nacos 已就绪（上层目录可用 `启动-tool.bat`）。

### IDEA（推荐）

1. 打开本仓库根目录，等待 Maven 导入完成  
2. 运行配置选择 **`Start All Cloud`** → 启动  
3. 会同时拉起 system / file / log / job / gateway  
4. 访问网关：http://127.0.0.1:8088  

若没有该配置：确认已加载 `.idea/runConfigurations/`，或 Reload Maven 后再试。  
停止：Run 窗口对 Compound 点 Stop。

### Maven（单服务）

在仓库根目录：

```bat
set SPRING_PROFILES_ACTIVE=dev,cloud
mvnw -pl xn-system spring-boot:run
```

其它服务把 `-pl` 换成 `xn-file` / `xn-log` / `xn-job` / `xn-gateway`。  
各模块也有独立 `mvnw`，可在模块目录内直接 `mvnw spring-boot:run`。

### 配置与密钥

- Profile：`dev`（本地默认）、`prod`、`cloud`（Nacos 发现）
- 各服务有 `env.example`，可复制为环境变量（生产勿使用示例密钥）
- 中间件启停：上层 `启动-tool.bat` / `停止-tool.bat`；前端：`启动-前端.bat` / `停止-前端.bat`

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

## 现状与演进

- 当前为「共享代码 + 按控制器拆进程」：降低拆分风险，业务服务裁剪各自 Controller。  
- 后续可再抽 `xn-common`、Feign/MQ、按服务拆库。  
- 新业务：新增 module（如 `xn-order`），并在 gateway 增加路由即可。

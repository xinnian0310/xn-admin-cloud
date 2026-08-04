# xn-admin-cloud

心念后台 **微服务后端**（Spring Boot + Spring Cloud Gateway + Nacos）。

## 服务一览（5 个可部署单元）

| 服务 | 端口 | 职责 |
|------|------|------|
| **xn-gateway** | 8088 | 统一入口；`lb://` 经 Nacos 发现转发 |
| **xn-system** | 8081 | 登录/权限/组织/字典/配置/公告/站内信/监控… |
| **xn-file** | 8082 | 文件 / MinIO / 预览 |
| **xn-log** | 8083 | 登录 / 操作 / 异常 / 任务日志 |
| **xn-job** | 8084 | Quartz 定时任务 |

中间件：MySQL、Redis、MinIO、Nacos（库名 `smartadmin`）。

## 网关路由（Nacos 服务发现）

| 路径 | 服务名（Nacos） |
|------|-----------------|
| `/api/files/**` | `xn-file` |
| `/api/logs/**` | `xn-log` |
| `/api/jobs/**` | `xn-job` |
| `/api/**`、`/uploads/**`、`/ws/**` | `xn-system` |

注册中心：`127.0.0.1:8849`（与 `tool/nacos3` 一致），账号默认 `nacos/nacos`。  
启动前请先打开本机 Nacos；控制台可看到 `xn-system` / `xn-file` / `xn-log` / `xn-job` / `xn-gateway`。

前端代理指向网关 `8088`（`xn-admin-vue3-ts/vite.config.ts`），开发端口 `5173`。

## 启动

前提：MySQL / Redis / Nacos 已就绪（可用仓库根目录 `启动-tool.bat`）。

### IDEA 一键启动（推荐）

1. 打开 `xn-admin-cloud` 根目录，等 Maven 导入完成  
2. 右上角运行配置选 **`Start All Cloud`** → 点绿色三角  
3. 会同时拉起 system / file / log / job / gateway（5 个）  
4. 网关：http://127.0.0.1:8088  

若列表里没有该配置：`Run` → `Edit Configurations` → 看是否已加载 `.idea/runConfigurations/`；没有则 Reload Maven 后再看一眼。

停止：Run 窗口对 Compound 点 Stop（或分别停 5 个）。

### 手动 / Maven 启动（可选）

```bat
cd xn-admin-cloud\xn-system && set SPRING_PROFILES_ACTIVE=dev,cloud && mvnw spring-boot:run
```

健康检查：http://127.0.0.1:8088/actuator/health  

中间件用 `启动-tool.bat` / `停止-tool.bat`，前端用 `启动-前端.bat` / `停止-前端.bat`；后端用 IDEA。

## 现状说明

- 当前为「共享库 + 按控制器拆进程」：各业务服务裁剪 Controller，降低拆分风险。  
- 后续可再抽 `xn-common`、Feign/MQ、按服务拆库。  
- 新业务：在本仓库新增 module（如 `xn-order`），并在 gateway 增加路由即可。  

# xn-log

日志服务（端口 **8083**）：登录 / 操作 / 异常 / 任务日志查询与导出。网关路由：`/api/logs/**` → `lb://xn-log`。

启动方式与中间件依赖见 [xn-admin-cloud README](../README.md)。本地：

```bash
set SPRING_PROFILES_ACTIVE=dev,cloud
.\mvnw.cmd spring-boot:run
```

配置参考 [`env.example`](./env.example)。

## API

| 前缀 | 能力 |
|------|------|
| `/api/logs/login` | 登录日志：列表、删除、批量删除、清空、导出 |
| `/api/logs/oper` | 操作日志：列表、详情、删除、批量删除、清空、导出 |
| `/api/logs/exception` | 异常日志：列表、详情、删除、批量删除、清空、导出 |
| `/api/logs/job` | 任务日志：列表、详情、删除、批量删除、清空、导出 |

菜单与 page-ui 由 `xn-system` 种子（`/system/logs/login` · `oper` · `exception`，`/system/jobs/logs`）。四套管理端**接口模块已接、页面尚未落地**，点菜单会 404。

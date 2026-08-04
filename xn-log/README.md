# xn-log

日志服务（端口 **8083**）：登录 / 操作 / 异常 / 任务日志查询与导出。网关路由：`/api/logs/**` → `lb://xn-log`。

启动方式与中间件依赖见 [xn-admin-cloud README](../README.md)。本地：

```bash
set SPRING_PROFILES_ACTIVE=dev,cloud
.\mvnw.cmd spring-boot:run
```

配置参考 [`env.example`](./env.example)。

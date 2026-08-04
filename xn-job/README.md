# xn-job

定时任务服务（端口 **8084**）：Quartz 任务 CRUD、启停、立即执行。网关路由：`/api/jobs/**` → `lb://xn-job`。

启动方式与中间件依赖见 [xn-admin-cloud README](../README.md)。本地：

```bash
set SPRING_PROFILES_ACTIVE=dev,cloud
.\mvnw.cmd spring-boot:run
```

配置参考 [`env.example`](./env.example)。

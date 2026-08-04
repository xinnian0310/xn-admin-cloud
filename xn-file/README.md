# xn-file

文件服务（端口 **8082**）：上传 / 浏览 / 删除、MinIO、预览。网关路由：`/api/files/**` → `lb://xn-file`。

启动方式与中间件依赖见 [xn-admin-cloud README](../README.md)。本地：

```bash
set SPRING_PROFILES_ACTIVE=dev,cloud
.\mvnw.cmd spring-boot:run
```

配置参考 [`env.example`](./env.example)。

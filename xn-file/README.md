# xn-file

文件服务（端口 **8082**）：上传 / 浏览 / 删除、分片上传、MinIO、预览。网关路由：`/api/files/**` → `lb://xn-file`。

启动方式与中间件依赖见 [xn-admin-cloud README](../README.md)。本地：

```bash
set SPRING_PROFILES_ACTIVE=dev,cloud
.\mvnw.cmd spring-boot:run
```

配置参考 [`env.example`](./env.example)。

## API

| 前缀 | 能力 |
|------|------|
| `/api/files` | 列表、浏览、目录树、上传、建目录、删除 |
| `/api/files/chunk` | 分片：检查、初始化、状态、上传分片、完成、取消 |

管理端「文件管理」页已闭环（目录树、上传、删除、预览）。

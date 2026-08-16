# xn-job

定时任务服务（端口 **8084**）：Quartz 任务 CRUD、启停、立即执行。网关路由：`/api/jobs/**` → `lb://xn-job`。

启动方式与中间件依赖见 [xn-admin-cloud README](../README.md)。本地：

```bash
set SPRING_PROFILES_ACTIVE=dev,cloud
.\mvnw.cmd spring-boot:run
```

配置参考 [`env.example`](./env.example)。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/jobs` | 任务列表 |
| GET | `/api/jobs/{id}` | 任务详情 |
| POST | `/api/jobs` | 新增 |
| PUT | `/api/jobs/{id}` | 修改 |
| DELETE | `/api/jobs/{id}` | 删除 |
| POST | `/api/jobs/batch-delete` | 批量删除 |
| PUT | `/api/jobs/{id}/status` | 启停 |
| POST | `/api/jobs/{id}/run` | 立即执行 |

任务日志在 **xn-log**（`/api/logs/job`），不在本服务。前端定时任务页已闭环；任务日志页尚未落地。

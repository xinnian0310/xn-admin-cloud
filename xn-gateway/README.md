# xn-gateway

API 网关（端口 **8088**）：Spring Cloud Gateway，经 Nacos 以 `lb://` 转发到各业务服务。鉴权在各业务服务内完成（JWT），本服务不作统一鉴权过滤器。

| 路径 | Nacos 服务名 |
|------|----------------|
| `/api/files/**` | `xn-file` |
| `/api/logs/**` | `xn-log` |
| `/api/jobs/**` | `xn-job` |
| `/api/**`、`/uploads/**`、`/ws/**` | `xn-system` |

路由表与启动方式见 [xn-admin-cloud README](../README.md)。

# 演示库备份

| 文件 | 说明 |
|------|------|
| [`xn_admin.nb3`](./xn_admin.nb3) | Navicat 备份，库名 `xn_admin` |

## 还原

1. 安装 [Navicat](https://www.navicat.com/)，连接本机 MySQL
2. 若尚无库，先创建 `xn_admin`
3. 右键该库 → **还原备份** → 选择本目录 `xn_admin.nb3`

不必还原本备份也可以跑起来：先建空库，启动服务后由 **Flyway** 建表；`dev` 会写入种子账号 `SuperAdmin` / `admin`。

详见仓库根 [README.md](../../README.md)「数据库」。

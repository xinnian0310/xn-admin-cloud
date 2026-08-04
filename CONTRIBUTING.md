# 工程规范

## 本地命令

```bat
mvnw -B spotless:apply
mvnw -B verify
```

- `spotless:apply`：按 Google Java Format（AOSP）格式化
- `verify`：编译 + 单测 + Spotless check + SpotBugs（High）+ JaCoCo 报告

## Git Hooks

首次在本仓库执行任意 `mvnw` 目标后，会自动设置 `core.hooksPath=.githooks`。

也可手动：

```bat
git config core.hooksPath .githooks
```

| Hook | 作用 |
|------|------|
| `commit-msg` | Conventional Commits（如 `feat: xxx`） |
| `pre-commit` | `spotless:check` |

## 提交信息

```
<type>(optional-scope): <subject>

feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert
```

示例：`feat(system): 增加密码策略`

## CI

- GitHub Actions：`.github/workflows/ci.yml`
- Gitee Go：`.gitee/workflows/ci.yml`（需在 Gitee 控制台启用流水线）

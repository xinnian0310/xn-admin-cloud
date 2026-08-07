# 贡献指南

感谢关注本仓库。本工程为独立开源项目；配套前端见相关仓库（如 `xn-admin-vue3-ts`）。

## 如何贡献

1. Fork 本仓库并创建功能分支
2. 本地按下方命令完成检查
3. 提交信息遵循 Conventional Commits
4. 发起 Pull Request，说明动机与验证方式

安全相关问题请优先阅读 [SECURITY.md](./SECURITY.md)，勿在公开 Issue 中披露未修复漏洞。

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

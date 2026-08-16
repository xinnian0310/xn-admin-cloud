#!/usr/bin/env bash
# 以 xn-system 为规范源，将共享包同步到 xn-file / xn-log / xn-job。
# 用法（仓库根目录）:
#   bash scripts/sync-shared-from-system.sh          # 复制并覆盖
#   bash scripts/sync-shared-from-system.sh --check  # 仅检查漂移，有差异则 exit 1
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/xn-system/src/main/java/com/smartadmin"
TARGETS=(xn-file xn-log xn-job)

# 安全与基础设施相关包（按目录同步；控制器仍由各模块自行裁剪）
PACKAGES=(
  common
  config
  security
  service
  util
  websocket
  monitor
  scheduler
  entity
  dto
  repository
)

CHECK=0
if [[ "${1:-}" == "--check" ]]; then
  CHECK=1
fi

diff_count=0
copy_count=0

sync_dir() {
  local pkg="$1"
  local from="$SRC/$pkg"
  [[ -d "$from" ]] || return 0
  for t in "${TARGETS[@]}"; do
    local to="$ROOT/$t/src/main/java/com/smartadmin/$pkg"
    mkdir -p "$to"
    while IFS= read -r -d '' f; do
      local rel="${f#$from/}"
      local dest="$to/$rel"
      mkdir -p "$(dirname "$dest")"
      if [[ ! -f "$dest" ]]; then
        if [[ "$CHECK" -eq 1 ]]; then
          echo "MISSING $t: $pkg/$rel"
          diff_count=$((diff_count + 1))
        else
          cp "$f" "$dest"
          copy_count=$((copy_count + 1))
          echo "ADD $t: $pkg/$rel"
        fi
        continue
      fi
      if ! cmp -s "$f" "$dest"; then
        if [[ "$CHECK" -eq 1 ]]; then
          echo "DRIFT $t: $pkg/$rel"
          diff_count=$((diff_count + 1))
        else
          cp "$f" "$dest"
          copy_count=$((copy_count + 1))
          echo "SYNC $t: $pkg/$rel"
        fi
      fi
    done < <(find "$from" -type f -name '*.java' -print0)
  done
}

echo "source: xn-system"
for pkg in "${PACKAGES[@]}"; do
  sync_dir "$pkg"
done

# 配置文件中与上传/MinIO 相关的公共片段由各模块 application.yml 手维；
# 关键：安全类请只改 xn-system，再跑本脚本。

if [[ "$CHECK" -eq 1 ]]; then
  if [[ "$diff_count" -gt 0 ]]; then
    echo "FAILED: $diff_count drifted/missing file(s). Run: bash scripts/sync-shared-from-system.sh"
    exit 1
  fi
  echo "OK: no drift"
else
  echo "DONE: synced $copy_count file(s)"
fi

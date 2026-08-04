@echo off
REM 安装 Git hooks（也可由 mvnw 自动设置 core.hooksPath）
cd /d "%~dp0"
git config core.hooksPath .githooks
echo Git hooks installed: core.hooksPath=.githooks
git config --get core.hooksPath

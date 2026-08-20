@echo off
chcp 65001 >nul
cd /d "%~dp0.."

if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dev,cloud"
if not defined UPLOAD_DIR set "UPLOAD_DIR=%CD%\uploads"
if not exist "%UPLOAD_DIR%" mkdir "%UPLOAD_DIR%"
if not exist "%CD%\logs" mkdir "%CD%\logs"

echo SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%
echo UPLOAD_DIR=%UPLOAD_DIR%
echo Starting xn-system / xn-file / xn-log / xn-job / xn-gateway ...
echo Gateway will be http://127.0.0.1:8088
echo.

start "xn-system" cmd /k "set SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%&& set UPLOAD_DIR=%UPLOAD_DIR%&& mvnw -pl xn-system spring-boot:run"
start "xn-file" cmd /k "set SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%&& set UPLOAD_DIR=%UPLOAD_DIR%&& mvnw -pl xn-file spring-boot:run"
start "xn-log" cmd /k "set SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%&& set UPLOAD_DIR=%UPLOAD_DIR%&& mvnw -pl xn-log spring-boot:run"
start "xn-job" cmd /k "set SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%&& set UPLOAD_DIR=%UPLOAD_DIR%&& mvnw -pl xn-job spring-boot:run"
start "xn-gateway" cmd /k "set SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%&& set UPLOAD_DIR=%UPLOAD_DIR%&& mvnw -pl xn-gateway spring-boot:run"

echo Five services launched in new windows. Close those windows to stop them.

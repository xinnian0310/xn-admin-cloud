#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev,cloud}"
export UPLOAD_DIR="${UPLOAD_DIR:-$PWD/uploads}"
mkdir -p "$UPLOAD_DIR" logs

echo "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
echo "UPLOAD_DIR=$UPLOAD_DIR"
echo "Starting xn-system / xn-file / xn-log / xn-job / xn-gateway ..."
echo "Gateway will be http://127.0.0.1:8088"

./mvnw -pl xn-system spring-boot:run >logs/xn-system.log 2>&1 &
./mvnw -pl xn-file spring-boot:run >logs/xn-file.log 2>&1 &
./mvnw -pl xn-log spring-boot:run >logs/xn-log.log 2>&1 &
./mvnw -pl xn-job spring-boot:run >logs/xn-job.log 2>&1 &
./mvnw -pl xn-gateway spring-boot:run >logs/xn-gateway.log 2>&1 &

echo "Logs: $PWD/logs/*.log"
echo "Stop: kill the mvnw/java processes for these modules."
wait

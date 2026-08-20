# xn-admin-cloud

[English](README.en.md) | [简体中文](README.md)

**Microservice backend** for XinNian Admin (Spring Boot 4 + Spring Cloud Gateway + Nacos).

xn-admin-cloud is the open-source backend of XinNian Admin. It covers typical mid-office needs: JWT login and sessions, RBAC with data scope, organizations / posts / dictionaries, notices and inbox messages, files with chunked upload, scheduled jobs, monitoring, logs, recycle bin, and code generation. The project is split into a gateway plus system / file / log / job services, and ships with four independent admin frontends. Licensed under Apache License 2.0 — **free for personal and commercial use**.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)
[![Open Source](https://img.shields.io/badge/Open%20Source-Free-success.svg)](./LICENSE)
[![Commercial](https://img.shields.io/badge/Commercial-Allowed-brightgreen.svg)](./LICENSE)
[![Personal](https://img.shields.io/badge/Personal-Allowed-brightgreen.svg)](./LICENSE)

Stack: Java 21, Maven multi-module, Flyway, JPA, Redis, MinIO, Quartz, OpenAPI.

Version: `1.0.0` · License: [Apache-2.0](./LICENSE) · **Commercial / personal use allowed** · Copyright 2026 XinNian

> This repository is published independently under Apache License 2.0. The four admin frontends live in **separate repositories** (see Related repositories) and are not bundled here.

## Live demos

- Website: https://xinniankeji.vip
- Vue 3 + TypeScript: https://vue3-ts.xinniankeji.vip
- Vue 3 + JavaScript: https://vue3-js.xinniankeji.vip
- Vue 3 Options API: https://vue2.xinniankeji.vip
- React: https://react.xinniankeji.vip

## Services

| Service | Port | Role |
|------|------|------|
| **xn-gateway** | 8088 | Unified entry; forwards via Nacos `lb://` |
| **xn-system** | 8081 | Login / RBAC / org & posts / dict / config / notices / monitor / recycle / codegen… |
| **xn-file** | 8082 | Files / chunked upload / MinIO / preview |
| **xn-log** | 8083 | Login / operation / exception / job logs |
| **xn-job** | 8084 | Quartz job CRUD / start-stop / run now |

Middleware: MySQL, Redis, MinIO, Nacos (default database `xn_admin`). Local: `docker compose up -d` at the repo root. Demo dump: [`docs/sql/xn_admin.nb3`](./docs/sql/xn_admin.nb3).

## Related repositories

Open-source scope is backend + four admin frontends:

| Repository | Gitee | GitHub | Notes |
|------|-------|--------|------|
| `xn-admin-cloud` | [Gitee](https://gitee.com/jenning/xn-admin-cloud) | [GitHub](https://github.com/xinnian0310/xn-admin-cloud) | This repo |
| `xn-admin-vue3-ts` | [Gitee](https://gitee.com/jenning/xn-admin-vue3-ts) | [GitHub](https://github.com/xinnian0310/xn-admin-vue3-ts) | Baseline admin (Vue 3 + TypeScript + Element Plus) |
| `xn-admin-vue3-js` | [Gitee](https://gitee.com/jenning/xn-admin-vue3-js) | [GitHub](https://github.com/xinnian0310/xn-admin-vue3-js) | Vue 3 + JavaScript (Composition) |
| `xn-admin-vue2-js` | [Gitee](https://gitee.com/jenning/xn-admin-vue2-js) | [GitHub](https://github.com/xinnian0310/xn-admin-vue2-js) | Vue 3 + JavaScript (Options API; the folder name is not Vue 2) |
| `xn-admin-react-ts` | [Gitee](https://gitee.com/jenning/xn-admin-react-ts) | [GitHub](https://github.com/xinnian0310/xn-admin-react-ts) | React 19 + TypeScript + Ant Design |

Dev proxies on all frontends point to this gateway at `http://127.0.0.1:8088`.

## Feature overview

APIs and menu seeds are in place; pages are implemented by the frontend repositories.

| Domain | Backend capabilities | Service |
| --- | --- | --- |
| Auth | Login / logout / register / captcha / JWT refresh / password / avatar / menus / API registry | xn-system |
| Organization | Users (CRUD, import/export, enable) / unit tree / posts | xn-system |
| Security | Roles and data scope (all / unit+children / unit / self) / permissions / dynamic routes / lock, rate limit, password rules | xn-system |
| Master data | Dictionary types and items | xn-system |
| Settings | System config / login page / remote storage / contact & donation / UI prefs / column prefs / page-ui | xn-system |
| Content | Notices (publish, recall, read, WebSocket) / inbox | xn-system |
| Monitor | Online users / services / Redis / SQL | xn-system |
| Logs | Login / operation / exception / job logs: query, detail, delete, clear, export | xn-log |
| Files | Browse, upload, mkdir, delete, chunked upload | xn-file |
| Jobs | Quartz CRUD, start/stop, run now | xn-job |
| Tools | Recycle bin / codegen / one-click route generation | xn-system |

**Aligned with frontends:** all four admin UIs include the pages above, including login / operation / exception / job logs.

## Gateway routes

| Path | Nacos service |
|------|----------------|
| `/api/files/**` | `xn-file` |
| `/api/logs/**` | `xn-log` |
| `/api/jobs/**` | `xn-job` |
| `/api/**`, `/uploads/**`, `/ws/**` | `xn-system` |

- Registry default: `127.0.0.1:8849` (change sample credentials in production)
- Health: http://127.0.0.1:8088/actuator/health
- Frontend dev ports: react-ts `1800`, vue2-js `1801`, vue3-js `1802`, vue3-ts `1803`

## Default accounts

Seeded on first `dev` start (written only when created; later password changes survive restart):

| Username | Initial password | Role |
|----------|------------------|------|
| `SuperAdmin` | `SuperAdmin` | Super admin |
| `admin` | `admin` | Admin |

**Local development only.** Change passwords immediately. Production must use `prod` (or `prod,cloud`) and must not reuse sample secrets. See [SECURITY.md](./SECURITY.md).

## Profiles

| Profile | Use |
|---------|------|
| `dev` | Local: relaxed config, demo seed, Swagger / infra restart |
| `cloud` | Multi-service: Nacos discovery and dedicated ports |
| `prod` | Production: schema checks, demo cleanup and dangerous switches off |

- **Local default**: `dev,cloud`
- **Production**: `SPRING_PROFILES_ACTIVE=prod,cloud` (do **not** include `dev`)

## Quick start (3 steps)

You need Docker and JDK 21. Maven can be the bundled `mvnw`.

### 1. Start middleware

```bash
docker compose up -d
```

This starts MySQL `3306` (database `xn_admin` / password `root`), Redis `6379`, Nacos `8849` (console `8850`), and MinIO `9000` (console `9001`). Credentials match `application-dev.yml` / `env.example` — **local only**. Nacos takes about 30–60s on first boot.

An empty database is enough: Flyway and seeds create tables plus SuperAdmin / admin. You can also restore [`docs/sql/xn_admin.nb3`](./docs/sql/xn_admin.nb3) in Navicat.

### 2. Start the five backend services

Windows:

```bat
scripts\run-dev.bat
```

Linux / macOS:

```bash
chmod +x mvnw scripts/run-dev.sh
./scripts/run-dev.sh
```

When they are up: http://127.0.0.1:8088  
Health: http://127.0.0.1:8088/actuator/health

IntelliJ: one Spring Boot run config per module, Active profiles `dev,cloud`, or a Compound.

### 3. Start any admin frontend

Clone a frontend repo (see Related repositories), for example the baseline:

```bash
cd xn-admin-vue3-ts
npm install
npm run dev
```

Open http://localhost:1803. Default accounts: `SuperAdmin` / `SuperAdmin` or `admin` / `admin` (dev only — change passwords after login).

Manual equivalent of step 2:

```bat
set SPRING_PROFILES_ACTIVE=dev,cloud
mvnw -pl xn-system spring-boot:run
mvnw -pl xn-file spring-boot:run
mvnw -pl xn-log spring-boot:run
mvnw -pl xn-job spring-boot:run
mvnw -pl xn-gateway spring-boot:run
```

On Linux / macOS use `export` and `./mvnw`.

### Config and secrets

- Each service has `env.example` (**never use sample secrets in production**)
- Key variables: `JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `MINIO_*`

```bat
set SPRING_PROFILES_ACTIVE=prod,cloud
set JWT_SECRET=replace-with-at-least-32-random-chars
set DB_PASSWORD=replace-with-a-strong-password
mvnw -pl xn-system spring-boot:run
```

## Engineering

Root POM + Maven Wrapper, JDK 21.

| Tool | Notes |
|------|------|
| Spotless | Google Java Format (AOSP) |
| SpotBugs | High-severity gate |
| JaCoCo | Coverage under `*/target/site/jacoco/` |
| Enforcer | JDK 21, Maven ≥ 3.9 |
| Git Hooks | Conventional Commits + Spotless before commit |
| CI | GitHub Actions / Gitee Go |

```bat
mvnw -B spotless:apply
mvnw -B verify
```

Install hooks:

```bat
scripts\install-hooks.bat
```

Commit example: `feat(system): add password policy`. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Docker

**Local middleware** is step 1 in Quick start: `docker compose up -d` (`docker-compose.yml`).

**Service images** are built from the **repo root**:

```bat
docker build -f xn-gateway/Dockerfile .
docker build -f xn-system/Dockerfile .
docker build -f xn-file/Dockerfile .
docker build -f xn-log/Dockerfile .
docker build -f xn-job/Dockerfile .
```

Runtime: `SPRING_PROFILES_ACTIVE=prod,cloud` plus database / JWT / MinIO env vars.

## Production (summary)

- Profile: `prod,cloud` (no `dev`)
- HTTPS via Nginx / gateway; DB and middleware on private network only
- See [SECURITY.md](./SECURITY.md)

## Current architecture

- Shared code + process split by controller
- JWT is validated inside each business service; the gateway routes and discovers, and is not a unified auth filter
- Shared sources live in `xn-system`; after edits run `scripts/sync-shared-from-system.ps1` (or `.sh`)
- New business modules (for example `xn-order`) need a gateway route

## Support

If this project helps you, a coffee is welcome ☕

<p align="center">
  <img src="./docs/donation/donate.png" alt="Donate (WeChat Pay / Alipay)" width="480" />
</p>

## License

[Apache License 2.0](./LICENSE). Personal, commercial, closed-source, and redistribution are allowed if you keep copyright, license, and NOTICE, and mark modified files. Software is provided “as is”, without warranty.

Donations are voluntary and are not a commercial license or paid support.

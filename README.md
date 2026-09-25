# Removerr

> Self-hosted web app that unifies **media deletion** across a Plex + Radarr + Sonarr + Seerr stack.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Angular](https://img.shields.io/badge/Angular-21-DD0031)
![Docker](https://img.shields.io/badge/Docker-multi--stage-2496ED)
![License](https://img.shields.io/badge/License-MIT-blue)

Removerr gives a Plex admin a single place to review their library — enriched with **who requested**
and **who watched** each title — and to send movies, shows or individual seasons to a **trash with a
delay** before permanent deletion. It is a full-stack application (Spring Boot API + Angular SPA)
shipped as a single Docker container.

> **Note:** the UI is in French for now — kept single-language for simplicity. The app isn't
> internationalized, though runtime strings (toasts, errors) are centralized in
> `frontend/src/app/core/messages.ts` should that ever change.

## Highlights

- **Trash is a *delay*, not a quarantine.** Trashing moves and deletes **nothing** — the media stays
  fully streamable in Plex until the scheduled purge runs (`DELETE … deleteFiles=true`). Restoring
  simply drops the trash entry. This keeps the source of truth in Radarr/Sonarr/Plex at all times.
- **"Watched by a user" computed across services** — scrobble history from Plex is cross-referenced
  with Radarr/Sonarr metadata (last-episode rules, `totalEpisodeCount`, season visibility).
- **Secrets encrypted at rest** — third-party API keys are stored in the database encrypted with
  AES-GCM; boot secrets (JWT, encryption key) come from the environment only.
- **Schema owned by Liquibase** — Hibernate runs with `ddl-auto: none`; migrations are versioned XML.
- **Stateless auth** — Plex PIN login, then a JWT in an `HttpOnly`, `SameSite=Strict` cookie.

## Tech stack

| Layer | Choice |
|-------|--------|
| Backend | Spring Boot 3.5, Java 21 |
| Database | SQLite (WAL, foreign keys on, single-writer Hikari pool) |
| Migrations | Liquibase (XML changelogs) |
| ORM | Hibernate 6 + community SQLite dialect |
| Cache | Caffeine (5 min global TTL) |
| Security | Spring Security + JWT in an HttpOnly / SameSite=Strict cookie |
| HTTP client | Spring WebClient (Reactor), 5 s connect / 10 s read timeouts |
| Frontend | Angular 21 standalone components, signals, Tailwind CSS v4 |
| Packaging | Maven + Angular CLI in a multi-stage Dockerfile (single container) |

## Architecture

```
backend/  (Spring Boot, com.removerr)
├── auth/         Plex PIN flow, JwtService, JwtAuthFilter
├── plexuser/     Plex account sync
├── library/      MediaAggregationService (Radarr + Sonarr + Plex + Seerr)
├── trash/        TrashService, PurgeService, PurgeScheduler
├── setting/      key/value app settings (API keys AES-GCM encrypted)
├── integration/  ArrBaseClient + Radarr / Sonarr / Plex / Seerr clients
├── crypto/       AesGcmEncryptor
├── common/       GlobalExceptionHandler, SPA controller
└── config/       SecurityConfig, DataSourceConfig

frontend/ (Angular)
├── core/      API services, auth, layout
├── shared/    reusable components
├── features/  library, trash, settings, audit, login
└── layout/    desktop sidebar + mobile bottom nav
```

Errors are returned consistently as `{ code, message }` via a `@RestControllerAdvice`
(`SERVICE_NOT_CONFIGURED` → 503, `INTEGRATION_ERROR` → 502, `VALIDATION_FAILED` → 400, …).

## Getting started

### Prerequisites

- Docker & Docker Compose
- A reachable Plex server, plus Radarr / Sonarr / Seerr (configured from the in-app Settings page)

### Run with Docker

```bash
# 1. Create your environment file
cp .env.example .env
# Generate the two required secrets and paste them into .env:
openssl rand -base64 32   # JWT_SECRET
openssl rand -base64 32   # REMOVERR_ENC_KEY

# 2. Start
docker compose up -d --build
```

The app is then available on `http://localhost:5055` (configurable via `PORT`). Log in with Plex,
then add your service URLs and API keys from **Settings**.

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | *(required)* | ≥ 32 random bytes, signs the auth JWT |
| `REMOVERR_ENC_KEY` | *(required)* | 32-byte base64 key, encrypts stored API keys |
| `TZ` | `Europe/Paris` | Container timezone (drives `PURGE_HOUR`) |
| `PURGE_HOUR` | `3` | Hour (0–23) of the daily auto-purge |
| `PORT` | `5055` | Host port |
| `CONFIG_PATH` | `./config` | Host path mounted to `/config` (holds the SQLite DB) |
| `LOG_LEVEL` | `INFO` | `DEBUG` / `INFO` / `WARN` |

> Service URLs, API keys and the trash retention delay are managed at runtime from the **Settings**
> page (stored in the database), not via environment variables.

### Local development

```bash
# Backend (dev profile: safe default secrets, local SQLite DB)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test

# Frontend (dev server with proxy to the backend on :8080)
cd frontend
npm install
ng serve
```

## API overview

```
POST   /api/auth/plex/pin            # public — start Plex PIN login
GET    /api/auth/plex/pin/{id}       # public — poll PIN status
POST   /api/auth/logout              # public

GET    /api/me                       # current admin
GET    /api/library                  # movies
GET    /api/library/shows            # shows

GET    /api/trash
POST   /api/trash/movies             # { radarrId }
POST   /api/trash/shows              # { sonarrId }
POST   /api/trash/seasons            # { sonarrId, seasonNumber }
POST   /api/trash/{id}/restore
POST   /api/admin/purge              # manual purge

GET    /api/settings
PUT    /api/settings
POST   /api/settings/test            # same body as PUT, null fields fall back to saved values
GET    /api/users
PATCH  /api/users/{id}               # toggle whether a user is counted
GET    /api/audit
```

Every route outside `/api/auth/*` requires a valid `ROLE_ADMIN` JWT.

## License

[MIT](LICENSE)

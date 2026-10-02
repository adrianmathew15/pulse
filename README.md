# Pulse

Pulse is a real-time infrastructure monitoring platform. Phase 1 is designed as a small, understandable vertical slice: register services, simulate and persist CPU/memory metrics, evaluate CPU thresholds, and update a dashboard through WebSockets.

> **Current status:** Service Management, Simulated Metric Persistence, Alert Persistence, and real-time WebSocket delivery are implemented. Services have configurable CPU and memory thresholds. Each threshold breach creates one active alert episode, repeated violations are suppressed, and recovery resolves the episode. Committed metric and alert changes update the dashboard live, while REST remains the initial-load and reconnect-recovery source.

## Phase 1 features

The completed Phase 1 will provide service registration and removal, stored metric history, live CPU/memory charts, threshold-based CPU alerts with duplicate suppression, recent alerts, useful error handling, and Docker Compose startup. Features explicitly excluded from this phase are listed in [the architecture document](docs/architecture.md).

## Architecture

The application uses three containers on one Docker Compose network:

- React, TypeScript, Vite, Tailwind CSS, Recharts, and Axios, built and served by Nginx.
- One Spring Boot backend containing REST, simulation, alerting, persistence, and STOMP/WebSocket concerns.
- PostgreSQL as the durable store for services, metrics, and alerts.

Nginx proxies `/api` and `/ws` to the backend, keeping browser traffic same-origin. See [docs/architecture.md](docs/architecture.md) for the component diagram, proposed schema, API contract, event flows, and configuration decisions.

## Technology baseline

- Java 25 LTS, Spring Boot 4.1.1, Maven 3.9.11
- Node.js 24 LTS, React 19.3, TypeScript 5.9, Vite 7.3, Tailwind CSS 4.3
- PostgreSQL 18 (verified with 18.6)
- Docker and Docker Compose
- JUnit 5 and Spring Boot Test

## Run with Docker Compose

Docker Desktop (or Docker Engine with Compose v2) is required.

```bash
cp .env.example .env
docker compose up --build
```

On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp`. Compose requires the database name, user, and password plus the authentication values described below. The example database password is for local development only; replace it with a strong secret for any non-local environment.

Open <http://localhost:3000>. Backend health is available at <http://localhost:8080/actuator/health>. Stop the application with:

```bash
docker compose down
```

Add `--volumes` only when you intentionally want to delete the local PostgreSQL data volume.

PostgreSQL and Spring Boot are published only on the host loopback interface for local tools and diagnostics. Containers still reach PostgreSQL at `postgres:5432`. Configure browser-to-backend addresses and the allowed browser origin with:

| Variable | Local default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | Public API base URL embedded in the frontend build. Set an absolute HTTPS URL for a separately hosted frontend. |
| `VITE_WS_URL` | Same-origin `/ws` | Public STOMP WebSocket URL embedded in the frontend build. Set an absolute WSS URL for a separately hosted frontend. |
| `CORS_ALLOWED_ORIGIN` | `http://localhost:3000` in Compose | Exact browser origin accepted by the backend REST and WebSocket endpoints. Wildcard origins are not used. |

`VITE_*` values are public browser configuration and are visible in the built frontend. Never put passwords, tokens, or other secrets in them. Leaving the two Vite variables empty preserves local Nginx/Vite proxy behavior.

## Production Docker Compose

`docker-compose.production.yml` prepares the server-side stack for a future Oracle VM while
leaving the local `docker-compose.yml` workflow unchanged. It contains PostgreSQL, Spring Boot,
and a dedicated Nginx reverse proxy; the React frontend is intentionally excluded because it
will be hosted separately.

```bash
docker compose -f docker-compose.production.yml config --quiet
docker compose -f docker-compose.production.yml up --build -d --wait
```

Only the reverse proxy publishes a host port (`PROXY_HTTP_PORT`, default `80`). PostgreSQL remains
available only as `postgres:5432` on the internal data network, while Spring Boot remains available
only as `backend:8080` to other containers. The proxy forwards `/api`, `/ws`, and exactly
`/actuator/health`; its `/ws` route preserves WebSocket upgrade headers. Port 80 is an HTTP-only
placeholder for local validation and must not be exposed to the internet until TLS is configured.

Production requires `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `PULSE_AUTH_USERNAME`,
`PULSE_AUTH_PASSWORD_HASH`, `PULSE_JWT_SECRET`, and an exact `CORS_ALLOWED_ORIGIN`. Telegram
credentials are required only when `PULSE_TELEGRAM_ENABLED=true`. The remaining tuning variables
retain their documented defaults. Supply secrets through the VM's protected environment or an
uncommitted `.env` file; never commit the populated values.

## Authentication and endpoint safety

Pulse uses one environment-configured application user. `POST /api/auth/login` accepts its
username and password and returns a short-lived HMAC-SHA-256 JWT. All other `/api/**` requests
require `Authorization: Bearer <token>`. STOMP clients send the same header in their CONNECT
frame. `/actuator/health` remains public for container health checks; no other actuator endpoint
is exposed.

Configure these server-only values in `.env` before starting Compose:

| Variable | Default | Purpose |
|---|---:|---|
| `PULSE_AUTH_USERNAME` | required | Initial application username. |
| `PULSE_AUTH_PASSWORD_HASH` | required | BCrypt hash of the application password. Store the hash, never the plaintext password. In a Compose `.env` file, single-quote the hash so its `$` characters remain literal. |
| `PULSE_JWT_SECRET` | required | Base64-encoded random key of at least 32 bytes. Generate it with a cryptographically secure local tool, for example `openssl rand -base64 32`. |
| `PULSE_JWT_EXPIRATION_MS` | `900000` | Access-token lifetime in milliseconds (15 minutes by default). |

Do not put any of these values in `VITE_*` variables or commit a populated `.env`. The frontend
keeps the access token in `sessionStorage`, adds it to REST requests and STOMP CONNECT, and returns
to the login page when it expires or the backend rejects it.

Registered monitor endpoints are restricted to absolute HTTP/HTTPS URLs whose resolved addresses
are public. Loopback, private, link-local, multicast, cloud-metadata, and obvious internal/Docker
destinations are rejected before persistence and again before each request. The HTTP client pins
validation to the DNS addresses it actually uses and does not follow redirects; a redirect target
is validated and recorded as a non-UP response without being requested.

## Telegram incident notifications

Pulse can send incident-created and incident-resolved notifications through the official
Telegram Bot API `sendMessage` method. The channel is disabled by default, and Pulse starts
normally without any Telegram credentials.

To enable delivery, create a `.env` file from `.env.example` and configure:

| Variable | Default | Purpose |
|---|---|---|
| `PULSE_TELEGRAM_ENABLED` | `false` | Enables the Telegram notification channel. |
| `PULSE_TELEGRAM_BOT_TOKEN` | empty | Bot token issued by BotFather. |
| `PULSE_TELEGRAM_CHAT_ID` | empty | Target user, group, or channel chat ID. |
| `PULSE_TELEGRAM_API_BASE_URL` | `https://api.telegram.org` | Telegram Bot API base URL. Override primarily for testing. |
| `PULSE_TELEGRAM_TIMEOUT_MS` | `5000` | Connect and response timeout in milliseconds. |

Create a bot by messaging [BotFather](https://t.me/BotFather) in Telegram and copy the bot
token into `PULSE_TELEGRAM_BOT_TOKEN`. Start a direct chat with the bot, or add it to the
target group/channel and send a message there, so the bot can send messages to that chat.
Obtain the target chat ID (for example, from the Bot API `getUpdates` response), set
`PULSE_TELEGRAM_CHAT_ID`, and then set `PULSE_TELEGRAM_ENABLED=true`. Never commit a real bot
token or chat ID.

Telegram is a second implementation of the existing `NotificationChannel` interface. Incident
transitions remain provider-independent, delivery runs only after the incident transaction
commits, and a Telegram API failure is logged without interrupting in-app STOMP delivery.

## Run applications locally

Start only PostgreSQL with `docker compose up postgres`, then run:

Before launching Spring Boot directly, export `PULSE_AUTH_USERNAME`,
`PULSE_AUTH_PASSWORD_HASH`, and `PULSE_JWT_SECRET` in that terminal. These are mandatory server
settings; Compose reads them from `.env`, but a direct Maven process does not.

```bash
cd backend
mvn test
mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Local backend development requires Java 25 and Maven 3.6.3 or newer; Node.js 24 is recommended for the frontend. Vite serves at <http://localhost:5173> and proxies API/WebSocket traffic to port 8080.

## API

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Exchange the configured username/password for an access token |
| `POST` | `/api/services` | Register a service |
| `GET` | `/api/services` | List services |
| `GET` | `/api/services/{id}` | Get a service |
| `DELETE` | `/api/services/{id}` | Delete a service |
| `PATCH` | `/api/services/{id}/thresholds` | Update CPU and memory warning thresholds |
| `GET` | `/api/services/{id}/metrics?limit=100` | Get recent metrics in chronological order (maximum 500) |
| `GET` | `/api/services/{id}/alerts?status=ACTIVE&limit=100` | Get bounded alert history, optionally filtered by status |
| `GET` | `/api/alerts/active?limit=500` | Get active alerts across services |

The backend generates one simulated sample per registered service every five seconds by default and evaluates it against that service's thresholds. CPU and memory episodes are independent. Thresholds default to 80%, must be greater than zero and at most 100%, and can be set during registration or edited in service details. The STOMP endpoint `/ws` publishes committed metrics to `/topic/services/{serviceId}/metrics` and alert transitions to `/topic/services/{serviceId}/alerts` plus the dashboard-wide `/topic/alerts` stream.

Metric simulation and retrieval can be tuned without rebuilding:

| Variable | Default | Purpose |
|---|---:|---|
| `PULSE_METRICS_ENABLED` | `true` | Enable scheduled sample generation |
| `PULSE_METRICS_INTERVAL_MS` | `5000` | Delay between completed generation runs |
| `PULSE_METRICS_DEFAULT_LIMIT` | `100` | History rows returned when `limit` is omitted |
| `PULSE_METRICS_MAX_LIMIT` | `500` | Server-side upper bound for history requests |
| `PULSE_ALERTS_DEFAULT_LIMIT` | `100` | Default alert-history result size |
| `PULSE_ALERTS_MAX_LIMIT` | `500` | Server-side upper bound for alert requests |

Metric values are simulations; Pulse does not contact a service endpoint to collect them. Automatic retention and aggregation are future concerns, so long-running installations should manage database growth operationally for now.

## Verification

```bash
cd backend
mvn test
mvn package

cd ../frontend
npm ci
npm test
npm run build

cd ..
docker compose config
docker compose up --build --wait
```

The integration tests use `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` and exercise a real PostgreSQL database. The backend suite also covers authentication and authorization, SSRF defenses, WebSocket authorization and payload publication, after-commit configuration, and persistence-failure suppression. Frontend tests cover session handling plus duplicate metric and alert event handling. The backend container build can be used when Maven or Java 25 is not installed on the host.

## Future improvements

Only after the current vertical slice is complete, later phases may consider a telemetry pipeline, caching, advanced user management, real agents, distributed processing, observability, CI/CD, and cloud deployment. They are deliberately absent now.

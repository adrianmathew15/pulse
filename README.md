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

On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp`. The example password is for local development; change it in `.env` before first startup.

Open <http://localhost:3000>. Backend health is available at <http://localhost:8080/actuator/health>. Stop the application with:

```bash
docker compose down
```

Add `--volumes` only when you intentionally want to delete the local PostgreSQL data volume.

## Run applications locally

Start only PostgreSQL with `docker compose up postgres`, then run:

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

The integration tests use `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` and exercise a real PostgreSQL database. The 37-test backend suite also covers WebSocket payload publication, after-commit configuration, and persistence-failure suppression. Frontend tests cover duplicate metric and alert event handling. The backend container build can be used when Maven or Java 25 is not installed on the host.

## Future improvements

Only after the Phase 1 vertical slice is complete, later phases may consider a telemetry pipeline, caching, authentication/authorization, real agents, distributed processing, observability, CI/CD, and cloud deployment. They are deliberately absent now.

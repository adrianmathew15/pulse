Pulse — Phase 2: Real Service Monitoring & Notifications
1. Phase 2 Objective

Transform Pulse from a monitoring platform using simulated telemetry into a system capable of monitoring real external services, detecting availability changes, maintaining incident state, and notifying users through external channels.

Phase 2 should preserve everything that works in Phase 1.

Phase 2 should allow Pulse to:
Register a real service endpoint.
Periodically check whether the endpoint is reachable.
Determine whether the service is UP or DOWN.
Record health-check results.
Detect UP → DOWN transitions.
Detect DOWN → UP recovery transitions.
Avoid repeatedly notifying while a service remains DOWN.
Display service availability in the dashboard.
Display response time and HTTP status.
Send external notifications for important state changes.
Continue supporting the existing CPU/memory simulation and threshold alerts.
Run everything through Docker Compose.
2. Phase 2 Architecture

The architecture would evolve from:

Service
   ↓
Metric Simulator
   ↓
PostgreSQL
   ↓
WebSocket
   ↓
React

to:

                         ┌─────────────────────┐
                         │   External Service  │
                         └──────────┬──────────┘
                                    │
                              HTTP Health Check
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Pulse Monitor     │
                         │   Scheduler         │
                         └──────────┬──────────┘
                                    │
                              Health Result
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │  State Engine       │
                         │                     │
                         │ UP / DOWN           │
                         │ State transitions   │
                         └──────────┬──────────┘
                                    │
                             State changed?
                              /           \
                            YES            NO
                             │              │
                             ▼              └─── ignore
                      ┌─────────────┐
                      │ Alert Engine│
                      └──────┬──────┘
                             │
                    ┌────────┴─────────┐
                    ▼                  ▼
              PostgreSQL          Notification
                    │                  │
                    ▼             ┌────┴────┐
               WebSocket           │         │
                    │           Telegram   Future
                    ▼
              React Dashboard

The existing simulated CPU/memory pipeline remains:

Metric Simulator
       ↓
PostgreSQL
       ↓
Alert Engine
       ↓
WebSocket
       ↓
Dashboard

So Phase 2 adds real monitoring rather than replacing Phase 1.

3. Phase 2 Iterations

I would divide Phase 2 into four major iterations.

Iteration 2.1 — Real Health Monitoring
Goal

Make Pulse actually contact registered service endpoints.

For every registered service:

Every N seconds
       ↓
HTTP request
       ↓
Response
       ↓
Health result

Record:

UP/DOWN
HTTP status
response time
timestamp
failure reason when applicable

Example:

Payment API

Status: UP
HTTP: 200
Response: 142 ms
Last checked: 20:45:12

For a failure:

Payment API

Status: DOWN
HTTP: 503
Response: 821 ms
Last checked: 20:45:22
Reason: HTTP 503 Service Unavailable
Important

Don't immediately build notifications.

First make sure real health checking works reliably.

4. Iteration 2.2 — Availability State & Incidents

This is where Pulse becomes much more interesting.

Instead of treating every failed health check as a new alert:

DOWN
DOWN
DOWN
DOWN

Pulse should recognize that these belong to one incident.

Example:

20:40:00 UP
20:40:10 UP
20:40:20 DOWN ← Incident begins
20:40:30 DOWN
20:40:40 DOWN
20:40:50 DOWN
20:41:00 UP   ← Incident recovered

Pulse records:

Incident
---------
Service: Payment API
Started: 20:40:20
Recovered: 20:41:00
Duration: 40 seconds
Status: RESOLVED
State machine
        ┌─────────┐
        │   UP    │
        └────┬────┘
             │ failure
             ▼
        ┌─────────┐
        │  DOWN   │
        └────┬────┘
             │ success
             ▼
        ┌─────────┐
        │   UP    │
        └─────────┘

This also prevents notification spam.

5. Iteration 2.3 — Notification System

Once state transitions are reliable, introduce a notification layer.

Rather than putting Telegram code directly into the health checker:

Health Monitor
      ↓
Alert Engine
      ↓
NotificationService
      ↓
Telegram

This abstraction is important because later you could add:

NotificationService
       ├── Telegram
       ├── Email
       ├── Slack
       └── Discord

without changing the monitoring engine.

Notifications

When:

UP → DOWN

send:

🔴 Pulse Alert
Payment API is DOWN
HTTP 503
Detected: 20:40:20

When:

DOWN → UP

send:

🟢 Pulse Recovery
Payment API is UP
Downtime: 40 seconds
Recovered: 20:41:00

Notification rule

Only notify on state transitions.

Not:

DOWN → DOWN → DOWN → DOWN

but:

UP → DOWN     🔴 notify
DOWN → DOWN   —
DOWN → DOWN   —
DOWN → UP     🟢 notify
6. Iteration 2.4 — Telegram Integration

Only after the notification architecture works with a test/mock implementation should we connect Telegram.

The flow becomes:

Real Health Check
       ↓
State Transition
       ↓
Alert
       ↓
Notification Service
       ↓
Telegram Bot API
       ↓
Your phone

Credentials should be supplied through environment variables.

Never put Telegram/API credentials in GitHub.

7. Dashboard Evolution

The existing dashboard should gain an availability section.

For each service:

┌──────────────────────────────────────┐
│ Payment API                          │
│                                      │
│ ● UP                                 │
│ HTTP 200                             │
│ Response: 142 ms                     │
│ Last checked: 20:45:12               │
│                                      │
│ Uptime: 99.8%                        │
└──────────────────────────────────────┘

For a failed service:

┌──────────────────────────────────────┐
│ Payment API                          │
│                                      │
│ ● DOWN                               │
│ HTTP 503                             │
│ Down since: 20:40:20                 │
│                                      │
│ Incident ACTIVE                      │
└──────────────────────────────────────┘

The dashboard should continue receiving updates through the existing WebSocket infrastructure.

8. Database Evolution

Phase 1 currently has:

services
metrics
alerts

Phase 2 could introduce:

health_checks
incidents

Conceptually:

Service
 ├── Metrics
 ├── Alerts
 ├── Health Checks
 └── Incidents
health_checks

Potential fields:

id
service_id
status
http_status
response_time_ms
checked_at
failure_reason
incidents

Potential fields:

id
service_id
started_at
resolved_at
status
failure_reason
duration

We should let the implementation determine the exact schema rather than locking ourselves into unnecessary fields now.

9. Testing Strategy

Phase 2 should have considerably stronger testing because we're now interacting with unreliable external systems.

Health checks

Test:

HTTP 200 → UP
HTTP 201/204 where appropriate
HTTP 4xx
HTTP 5xx
timeout
connection refused
malformed/unreachable endpoint
slow response
State transitions

Test:

UP → UP
UP → DOWN
DOWN → DOWN
DOWN → UP
Notifications

Test:

UP → DOWN → one notification

DOWN → DOWN → no notification

DOWN → UP → recovery notification
Failure handling

Test:

Health check fails
       ↓
Alert still persisted
       ↓
Notification fails
       ↓
Monitoring system continues running

A notification provider going down should not take down Pulse.

10. Docker Architecture

Phase 1:

frontend
backend
postgres

Phase 2 initially remains:

frontend
backend
postgres

The health checker can live inside the existing Spring Boot backend.

Don't create a separate microservice yet.

If we eventually need Kafka, then we can introduce:

frontend
backend
postgres
kafka

But I would not add Kafka just because Phase 2 exists. The real health-monitoring capability is more valuable to implement first.

11. What We Are NOT Doing Yet

To keep Pulse understandable, Phase 2 should initially exclude:

Kubernetes
microservices
Redis
Prometheus
Grafana
cloud deployment
authentication
complex alert rules
multi-tenancy
mobile application
AI/LLM features
dozens of notification providers

The original plan already treats these as later evolution rather than Phase 1 requirements.

12. Phase 2 Definition of Done

I'd consider Phase 2 complete when this workflow works:

1. Register real service
        ↓
2. Pulse periodically checks endpoint
        ↓
3. Service is shown as UP
        ↓
4. Service becomes unavailable
        ↓
5. Pulse detects DOWN
        ↓
6. Incident is created
        ↓
7. Dashboard updates in real time
        ↓
8. External notification is sent
        ↓
9. Service becomes available again
        ↓
10. Pulse detects recovery
        ↓
11. Incident is resolved
        ↓
12. Recovery notification is sent
        ↓
13. No duplicate notifications occur
        ↓
14. All data survives restart
        ↓
15. Tests + Docker verification pass

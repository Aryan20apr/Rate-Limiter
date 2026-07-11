# Distributed Rate Limiter

A distributed HTTP rate-limiting layer for Spring Boot applications. Counter state is centralized in Redis and evaluated atomically via Lua scripts, enabling horizontally scaled deployments to enforce a single, consistent quota behind a load balancer. Rule definitions are persisted in Redis and can be changed at runtime through a secured Admin REST API, with pub/sub propagation to every replica within seconds.

**Java 21** · **Spring Boot 4** · **Redis 7** · **nginx** · **Docker Compose**

---

## Overview

Rate limiting is enforced at the servlet filter boundary, prior to controller dispatch. Each request is classified by client identity (user ID, IP address, or API key), matched against a configurable rule set, and evaluated against shared Redis-backed counters. Permitted requests proceed with standard `X-RateLimit-*` response headers; rejected requests receive HTTP 429 with `Retry-After` and a structured JSON error payload.

Distributed correctness is achieved through atomic read-modify-write operations in Redis Lua scripts, eliminating race conditions under concurrent load and ensuring counter consistency across application replicas.

When `rate-limit.store=redis`, rules live in a Redis hash and are cached in a thread-safe `RuleCatalog` on each instance. Operators change limits via the Admin API without redeploying; every replica hot-reloads through the `rl:config:changed` pub/sub channel.

```mermaid
flowchart LR
    Client --> nginx
    nginx --> App1[Instance 1]
    nginx --> App2[Instance 2]
    App1 --> Redis[(Redis)]
    App2 --> Redis
    Admin[Operator] -->|Admin API| App1
    App1 -->|pub/sub| App2
```



Each replica executes an identical enforcement pipeline. The load balancer distributes ingress traffic and propagates `X-Forwarded-For` for accurate client IP resolution.

## Quick start

**Requirements:** Docker and Docker Compose

```bash
cd core
docker compose up --build -d

# wait for the stack
until curl -sf -o /dev/null http://localhost/test; do sleep 2; done

# send a request
curl -si -H "X-User-Id: demo-user" http://localhost/test
```


| Service      | Port            | Role                                                             |
| ------------ | --------------- | ---------------------------------------------------------------- |
| nginx        | 80              | Load balancer → [http://localhost](http://localhost)             |
| app-1, app-2 | 8080 (internal) | Spring Boot replicas                                             |
| redis        | 6379            | Shared counter + rule config state                               |
| redis-ui     | 8081            | Redis Commander → [http://localhost:8081](http://localhost:8081) |


```bash
# stop
docker compose down

# reset counters only (preserves rl:config:* rule definitions)
docker compose exec -T redis redis-cli EVAL \
  "local n=0 for _,k in ipairs(redis.call('KEYS','rl:*')) do if not string.match(k,'^rl:config:') then redis.call('DEL',k) n=n+1 end end return n" 0
```

---



## How it works

```mermaid
flowchart TD
    REQ[HTTP request] --> FILTER[RateLimitFilter]
    FILTER --> ID[ClientIdentityResolver]
    ID --> MGR[RateLimitManager]
    MGR --> CATALOG[RuleCatalog]
    CATALOG --> RULES[RuleMatcher]
    RULES --> STORE[RedisRateLimitStore]
    STORE --> LUA[Lua script]
    LUA --> OK{Allowed?}
    OK -->|yes| API[Your controller]
    OK -->|no| DENY[429 + X-RateLimit-* + JSON]
    API --> RES[200 + X-RateLimit-*]
```



1. **Identify** the client from headers (`X-User-Id`, `X-API-Key`, `X-Forwarded-For`, or JWT `sub`).
2. **Match** rules from the in-memory `RuleCatalog` by scope, endpoint pattern, and `enabled` flag.
3. **Check** each matching rule against Redis (token bucket or sliding-window counter).
4. **Respond** with quota headers on success, or a full 429 contract on denial.

All matching rules must pass. On denial, the first failing rule in **priority order** (lower number first, then alphabetical `name`) determines the response. On success, headers reflect the tightest remaining quota.

### Dynamic configuration (Redis store)

On first startup, if the Redis config hash is empty, rules from `application.yaml` are seeded into Redis. After that, YAML is ignored for enforcement — Redis is the source of truth.

```mermaid
sequenceDiagram
    participant Operator
    participant AdminAPI as Admin API
    participant Redis
    participant Inst1 as Instance 1
    participant Inst2 as Instance 2

    Operator->>AdminAPI: PUT /admin/rules/user-limit
    AdminAPI->>Redis: HSET rl:config:rules
    AdminAPI->>Redis: PUBLISH rl:config:changed
    Redis-->>Inst1: user-limit
    Redis-->>Inst2: user-limit
    Inst1->>Redis: reload rule
    Inst2->>Redis: reload rule
    Note over Inst1, Inst2: RuleCatalog updated. Next request uses new limit
```



Changing a rule's `maxRequests` or `capacity` does **not** reset existing counter keys. Flush counter keys separately if a hard reset is required.

### Redis keys

**Counter state**

```
rl:{ruleName}:{scopeKey}

rl:user-limit:user:alice
rl:ip-limit:ip:203.0.113.5
rl:endpoint-limit:api-key:sk-demo-key
rl:global-limit:global
```

**Rule configuration** :


| Key / channel       | Type    | Purpose                                        |
| ------------------- | ------- | ---------------------------------------------- |
| `rl:config:rules`   | Hash    | Field = rule name, value = JSON rule (includes `priority`) |
| `rl:config:meta`    | Hash    | Bootstrap metadata (`bootstrapped`, `version`) |
| `rl:config:changed` | Pub/Sub | Notifies all instances to reload               |


---



## Features


|                                                   |                                                                        |
| ------------------------------------------------- | ---------------------------------------------------------------------- |
| **Distributed state**                             | Redis + atomic Lua scripts                                             |
| **Dynamic rules**                                 | Admin REST API, Redis persistence, pub/sub hot reload                  |
| **Rule priority**                                 | Lower number evaluated first; tie-break by rule name                   |
| **Algorithms (Redis)**                            | Token bucket, sliding-window counter                                   |
| **Algorithms (in-memory)**                        | Fixed window, sliding log, sliding counter, token bucket, leaky bucket |
| **Scopes**                                        | Global, per-user, per-IP, per-API-key, per-endpoint, per-user-endpoint |
| **Client identity**                               | `X-User-Id`, `X-API-Key`, `X-Forwarded-For`, Bearer JWT `sub`          |
| **Endpoint rules**                                | Ant-style patterns (`/api/`**)                                         |
| **HTTP contract**                                 | `X-RateLimit-Limit`, `Remaining`, `Reset`; `Retry-After` + JSON on 429 |
| **Enforcement**                                   | Servlet filter; optional `@RateLimited` AOP                            |
| **Failure handling**                              | Fail-closed (503) or fail-open when Redis is down                      |
| **Admin auth**                                  | Bearer token guard on `/admin/**`                                      |
| **Path exclusions**                               | `/admin/`** and `/actuator/`** bypass rate-limit filter                |




### Default rules (Docker profile)


| Rule             | Priority | Scope   | Limit                                   |
| ---------------- | -------- | ------- | --------------------------------------- |
| `user-limit`     | 10       | User    | 50 req / min                            |
| `endpoint-limit` | 20       | API key | 10 burst, 1/sec refill — `/api/**` only |
| `ip-limit`       | 30       | IP      | 30 req / min                            |
| `global-limit`   | 40       | Global  | 100 burst, 10/sec refill                |


Defined in `core/src/main/resources/application-docker.yaml`. Seeded into Redis on first start; thereafter managed via the Admin API or direct Redis edits.

---



## Try it



### Successful request

```bash
curl -si -H "X-User-Id: demo-user" http://localhost/test
```

```http
HTTP/1.1 200
X-RateLimit-Limit: 50
X-RateLimit-Remaining: 49
X-RateLimit-Reset: 1719494460
```



### Hit the limit

```bash
docker compose exec -T redis redis-cli EVAL \
  "local n=0 for _,k in ipairs(redis.call('KEYS','rl:*')) do if not string.match(k,'^rl:config:') then redis.call('DEL',k) n=n+1 end end return n" 0

for i in $(seq 1 55); do
  curl -s -o /dev/null -w "hit %{http_code} remaining=%{header:X-RateLimit-Remaining}\n" \
    -H "X-User-Id: demo-user" http://localhost/test
done
```

```http
HTTP/1.1 429
X-RateLimit-Limit: 50
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1719494502
Retry-After: 42
Content-Type: application/json

{"error":"rate_limit_exceeded","message":"Rate limit exceeded for rule 'user-limit'.","retryAfterSeconds":42}
```

`user-limit` (priority 10) is evaluated before `global-limit` (priority 40), so the denial names the user rule.



### API key (scoped to `/api/**`)

```bash
curl -si -H "X-API-Key: sk-demo-key" http://localhost/api/data
```



### Per-IP limiting

```bash
docker compose exec -T redis redis-cli EVAL \
  "local n=0 for _,k in ipairs(redis.call('KEYS','rl:*')) do if not string.match(k,'^rl:config:') then redis.call('DEL',k) n=n+1 end end return n" 0

for i in $(seq 1 35); do curl -s -o /dev/null http://localhost/test; done
curl -si http://localhost/test | head -15
```



### Change a limit at runtime (Admin API)

The Docker Compose profile sets `RATE_LIMIT_ADMIN_TOKEN` to `rl-admin-7f3a9c2e8b1d4f6a9e0c3b5d7f2a8e1`. All admin requests require `Authorization: Bearer <token>`.

Rules are evaluated in ascending `priority` order (10 before 20). `GET /admin/rules` returns rules in that order.

```bash
# list rules (sorted by priority, then name)
curl -s -H "Authorization: Bearer rl-admin-7f3a9c2e8b1d4f6a9e0c3b5d7f2a8e1" \
  http://localhost/admin/rules | jq '.[] | {name, priority, scope, maxRequests, capacity}'

# tighten user-limit to 5 requests per minute (priority 10 preserved)
curl -s -X PUT \
  -H "Authorization: Bearer rl-admin-7f3a9c2e8b1d4f6a9e0c3b5d7f2a8e1" \
  -H "Content-Type: application/json" \
  -d '{"scope":"USER","algorithm":"sliding-counter","maxRequests":5,"windowMillis":60000,"enabled":true,"priority":10}' \
  http://localhost/admin/rules/user-limit | jq

# verify enforcement picks up the new limit (both replicas via nginx)
docker compose exec -T redis redis-cli EVAL \
  "local n=0 for _,k in ipairs(redis.call('KEYS','rl:*')) do if not string.match(k,'^rl:config:') then redis.call('DEL',k) n=n+1 end end return n" 0
for i in $(seq 1 7); do
  curl -s -o /dev/null -w "hit %{http_code}\n" -H "X-User-Id: demo-user" http://localhost/test
done
# Expect 200 for hits 1–5, then 429 from user-limit (priority 10, evaluated before global-limit)
```

### Change rule priority

Lower `priority` values are evaluated first. Omit `priority` or send `0` on update to keep the existing value; on create, `0` auto-assigns the next slot (`max + 10`).

```bash
# move ip-limit ahead of user-limit (5 before 10)
curl -s -X PUT \
  -H "Authorization: Bearer rl-admin-7f3a9c2e8b1d4f6a9e0c3b5d7f2a8e1" \
  -H "Content-Type: application/json" \
  -d '{"scope":"IP","algorithm":"sliding-counter","maxRequests":30,"windowMillis":60000,"enabled":true,"priority":5}' \
  http://localhost/admin/rules/ip-limit | jq '.priority'
# Expect: 5

# create a stricter user rule that runs before user-limit
curl -s -X POST \
  -H "Authorization: Bearer rl-admin-7f3a9c2e8b1d4f6a9e0c3b5d7f2a8e1" \
  -H "Content-Type: application/json" \
  -d '{"name":"strict-user","scope":"USER","algorithm":"sliding-counter","maxRequests":3,"windowMillis":60000,"enabled":true,"priority":5}' \
  http://localhost/admin/rules | jq '{name, priority, maxRequests}'

docker compose exec -T redis redis-cli EVAL \
  "local n=0 for _,k in ipairs(redis.call('KEYS','rl:*')) do if not string.match(k,'^rl:config:') then redis.call('DEL',k) n=n+1 end end return n" 0
for i in $(seq 1 5); do
  curl -s -o /dev/null -w "hit %{http_code}\n" -H "X-User-Id: demo-user" http://localhost/test
done
# Expect 200 for hits 1–3, then 429 citing strict-user (priority 5, before user-limit at 10)
```

Admin API surface:

```
GET    /admin/rules              List all rules
GET    /admin/rules/{name}       Get single rule
POST   /admin/rules              Create rule
PUT    /admin/rules/{name}       Update rule
DELETE /admin/rules/{name}       Delete rule
```


| Status                | When                                             |
| --------------------- | ------------------------------------------------ |
| `200` / `201` / `204` | Success (get/update, create, delete)             |
| `400`                 | Validation failure with structured `fieldErrors` |
| `401`                 | Missing or invalid admin token                   |
| `404`                 | Rule not found                                   |
| `409`                 | Duplicate name on create                         |




### Inspect Redis

```bash
# counter keys
docker compose exec -T redis redis-cli KEYS 'rl:*'

# persisted rule config
docker compose exec -T redis redis-cli HGETALL rl:config:rules
```

---



## Configuration

```yaml
rate-limit:
  store: redis              # redis | memory
  failure-mode: closed      # closed → 503 when Redis unavailable | open → allow through
  admin:
    enabled: true           # default true when store=redis; false for YAML-only mode
    token: ${RATE_LIMIT_ADMIN_TOKEN}   # required Bearer token for /admin/**
  redis:
    host: redis
    port: 6379
    timeout-millis: 50
  rules:
    - name: user-limit
      priority: 10          # lower number = evaluated first
      scope: USER
      algorithm: sliding-counter
      maxRequests: 50
      windowMillis: 60000
      enabled: true
      endpointPattern: /api/**   # optional
```

**Rule loading behaviour:**


| `store`  | Rule source                                                | Runtime changes                           |
| -------- | ---------------------------------------------------------- | ----------------------------------------- |
| `redis`  | Redis (`rl:config:rules`); YAML seeds on first empty start | Admin API or Redis edits + pub/sub reload |
| `memory` | YAML at startup only                                       | Requires restart                          |


Dynamic configuration (Admin API, `RuleCatalog`, pub/sub) is active only when `store=redis` and `admin.enabled=true`.


| Algorithm         | Required fields               |
| ----------------- | ----------------------------- |
| `token`           | `capacity`, `refillPerSecond` |
| `sliding-counter` | `maxRequests`, `windowMillis` |


---



## What's next

 The next iteration adds **operational visibility** — Prometheus metrics export, Grafana dashboards for allow/reject rates and Redis latency, and load-test harnesses to validate enforcement under concurrent traffic.

---


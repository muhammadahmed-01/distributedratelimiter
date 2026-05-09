# Distributed Rate Limiter

A spring boot service showcasing IP-based rate limiting, JWT authentication, and account-level rate limiting using a sliding-window log approach with Redis+Lua for atomic operations.

---

## 🚀 Features

- IP Rate Limiting (first line of defense)  
- JWT Authentication Filter (parses and validates tokens)  
- Account Rate Limiting (applies after successful auth)  
- Deterministic Redis+Lua scripts for atomic counters (zero race conditions)  
- Clear filter ordering to minimize wasted work
- **Prometheus Metrics**: Exported via `/actuator/prometheus` tracking allowed/blocked rates
- **Correlation IDs**: MDC-powered request tracking via `X-Correlation-Id`
- **Structured Error Responses**: Standard JSON formats for `401` and `429` status codes

---

## 🧭 How it works (one-liner)

Requests pass through a cheap IP filter first, then JWT authentication, then account-level rate checks — minimizing work for blocked requests.

---

## 🧪 Sample Logs (illustrative)

```
[IpRateLimit] Request from 203.0.113.10
[IpRateLimit] count=1/3, ttl=60s
[JwtAuth] Authorization header found
[AccountRateLimit] account=123, count=1/60s

[IpRateLimit] Request from 203.0.113.10
[IpRateLimit] count=2/3, ttl=60s
[JwtAuth] Authorization header found
[AccountRateLimit] account=123, count=2/60s

[IpRateLimit] Request from 203.0.113.10
[IpRateLimit] count=3/3, ttl=60s
[JwtAuth] Authorization header found
[AccountRateLimit] account=123, count=3/60s

[IpRateLimit] Request from 203.0.113.10
[IpRateLimit] BLOCKED (count=4/3)
```

---

## 📦 Project Structure

```
config/
    AwsConfig
    RedisConfig
    WebFilterConfig
    JwtConfig
controller/
    DemoController
filter/
    IpRateLimitFilter
    JwtAuthFilter
    AccountRateLimitFilter
rateLimit/
    RateLimitResponse
    SlidingWindowLogRateLimiter
security/
    JwtGen
    JwtSecretService
    SecurityConfig
resources/
    rate_limiter.lua
```

---

## ⚙️ Quick Start

### Option 1: Local Development (Maven)

1. Start Redis: `docker run -p 6379:6379 --name redis -d redis:alpine`
2. Run the app: `mvn spring-boot:run`
3. Test endpoint: `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/hello`

### Option 2: Full Observability Stack (Docker Compose)

Spins up Redis, the Spring Boot application, Prometheus, and Grafana in one command:

```bash
docker compose up -d
```

- **App**: `http://localhost:8080`
- **Prometheus**: `http://localhost:9090`
- **Grafana**: `http://localhost:3000` (The dashboard is pre-provisioned!)

---

## 📈 Observability & Grafana

The application records metric counters for both `allowed` and `blocked` requests, tagged by rate limit `type` (IP or Account) and `status`.

`![Grafana Dashboard](docs/grafana-dashboard.png)`

---

## 🔥 Load Testing with k6

A comprehensive `k6` test suite is provided in the `load-tests/` directory to prove the system works correctly under heavy concurrent load.

It tests four scenarios:

1. **IP Limit**: Valid requests until the limit is exhausted, followed by `429`s.
2. **JWT Auth**: Rejects invalid tokens with `401 Unauthorized`.
3. **Account Limit**: Rejects requests that exceed the account's quota.
4. **Distributed Race Condition Test**: 50 Virtual Users simultaneously bombarding the same account using different spoofed IPs to prove the atomic Lua script perfectly prevents concurrency bugs.

**Run the tests:**

```bash
k6 run load-tests/rate_limit_test.js
```

---

## 📄 More Details

See [Design.md](Design.md) for architecture rationale, trade-offs, and domain separation notes.

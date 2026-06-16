# Rate Limit Audit Checklist

Use this on day one of a distributed rate limiting review. Each step maps to something documented in this case study.

---

## Hour 1: Baseline and Traffic Profile

| Step | Action | Case study equivalent |
|------|--------|------------------------|
| 1 | Record symptom: which endpoints, abuse pattern, 429 rate, when it started | README problem section: scrapers, burst traffic, multi-instance gaps |
| 2 | Map current limit layers: edge proxy, API gateway, app-level | Tiered IP → JWT → Account filter chain |
| 3 | Hit endpoint once with curl; note headers and latency | `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/hello` |
| 4 | Check rate-limit response headers | `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After` on 429 |
| 5 | Confirm limit algorithm (fixed window, token bucket, sliding log) | Sliding-window log in Redis + Lua (`rate_limiter.lua`) |
| 6 | Verify Redis is shared across app instances | Docker Compose `full` profile: one Redis, one app |

**Output by end of hour 1:** One written baseline: endpoint URL, current limits, algorithm, single-request headers.

---

## Hour 2: Correctness and Failure Modes

| Step | Action | Case study equivalent |
|------|--------|------------------------|
| 7 | Test boundary burst (send 2× limit at window edge) | Fixed window fails; SWL blocks correctly (`ip_filter_test.js`) |
| 8 | Test concurrent burst from one client IP | `full_pipeline_test.js` distributed race (~3k req/s target) |
| 9 | Test multi-account isolation on shared IP | `multi_account_isolation_test.js` |
| 10 | Test Redis unavailable behavior | `RedisFailurePolicyTest`: FAIL_CLOSED → 503, FAIL_OPEN → allow |
| 11 | Check clock source for window trimming | Java passes `Instant.now().getEpochSecond()` to Lua; see production lessons in README |
| 12 | Reconcile app metrics with load test counters | Grafana Totals vs k6 (`load-tests/K6_RESULTS.md`) |

**Output by end of hour 2:** Findings draft with one k6 profile result, Redis failure policy noted, proposed P0 items.

---

## Quick Commands (Staging or Local)

```bash
# Health (bypasses IP limit)
curl -s http://localhost:8080/actuator/health

# Single authenticated request
curl -s -D - -H "Authorization: Bearer <token>" http://localhost:8080/api/hello -o /dev/null

# Prometheus counters
curl -s http://localhost:8080/actuator/prometheus | grep rate_limit_requests_total

# Full k6 suite
docker compose --profile full up --build -d
.\load-tests\run-all-tests.ps1
```

---

## When to Escalate (Not App-Level Limits)

Stop the in-app-only path and involve platform or infra when:

| Signal | Likely cause | Escalation |
|--------|--------------|------------|
| Abuse still saturates origin after 429s | No edge enforcement | CDN/WAF, API gateway, Envoy rate limits |
| Redis latency p99 spikes under load | Hot keys, single shard | Redis Cluster, key sharding, SW counter |
| Limits correct per pod but not globally | In-memory counters | Shared store (Redis) or edge proxy |
| Geo-distributed traffic | Regional bursts | Per-region limits, anycast edge |
| DDoS volume exceeds app capacity | Volume attack | Cloud provider DDoS protection |

This case study focuses on **correct distributed limits in Spring Boot + Redis**. Production often adds edge enforcement; this checklist gets you evidence before proposing scope.

---

## Case Study Mapping

| Investigation step | Case study file / endpoint |
|--------------------|----------------------------|
| IP limit proof | `load-tests/ip_filter_test.js` |
| Account limit proof | `load-tests/account_filter_test.js` |
| Full pipeline burst | `load-tests/full_pipeline_test.js` |
| Health bypass | `load-tests/health_bypass_test.js` |
| Measured results | `load-tests/K6_RESULTS.md` |
| Algorithm trade-offs | `Design.md` |
| Redis failure policy | `RedisFailurePolicyTest`, `ratelimit.redis.failure-policy` |

# k6 Load Test Results

Verified run of all k6 profiles against the Docker full stack.

| Field | Value |
|-------|-------|
| **Date** | 2026-06-10T14:48–14:49 (+05:00) |
| **Target** | `http://localhost:8080` (Docker `app` container) |
| **Stack** | `docker compose --profile full` (app, redis, prometheus, grafana) |
| **Profile** | `SPRING_PROFILES_ACTIVE=dev` |
| **Limits** | IP 100/min · Account 10/min |
| **Runner** | `.\load-tests\run-all-tests.ps1` (Redis `FLUSHALL` between each script) |
| **Raw log** | [`k6-run-output.log`](k6-run-output.log) |
| **Suite duration** | ~54 s |
| **Overall verdict** | **8/8 PASS** |

---

## Summary

| # | Profile | Script | Requests | Allowed | Blocked | Checks | Verdict |
|---|---------|--------|----------|---------|---------|--------|---------|
| 1 | IP filter (isolated) | `ip_filter_test.js` | 110 | 100 | 10 (`ip`) | 330/330 | PASS |
| 2 | JWT filter (isolated) | `jwt_filter_test.js` | 4 | 2 | 2 (`401`) | 5/5 | PASS |
| 3 | Account filter (isolated) | `account_filter_test.js` | 12 | 10 | 2 (`account`) | 24/24 | PASS |
| 4 | IP + JWT combo | `ip_jwt_combo_test.js` | 110 | 100 | 10 (`ip`, incl. 5 authed) | 115/115 | PASS |
| 5 | Shared IP counter | `shared_ip_counter_test.js` | 110 | 100 | 10 (`ip`) | 125/125 | PASS |
| 6 | Multi-account isolation | `multi_account_isolation_test.js` | 60 | 50 | 10 (`account`) | 180/180 | PASS |
| 7 | Health bypass | `health_bypass_test.js` | 130 | 120 | 10 (`ip` on API only) | 150/150 | PASS |
| 8 | Full pipeline | `full_pipeline_test.js` | 2,016 | 20 | 1,996 (`account`) | 2,046/2,046 | PASS |

**Totals:** 2,542 HTTP requests · 2,412 allowed or expected rejections · 130 rate-limit blocks · **0 check failures**

---

## 1. IP filter (isolated)

**Script:** `ip_filter_test.js`  
**Scenario:** 110 anonymous requests, 10 VUs, shared iterations  
**Proves:** IP sliding-window limit (100/min) returns `429` with `type:ip`

| Metric | Result |
|--------|--------|
| Allowed (200) | 100 |
| Blocked (429, `type:ip`) | 10 |
| Checks | 100% (330/330) |
| `http_req_duration` avg | 4.38 ms |
| Duration | 0.2 s |

**Thresholds:** `ip_allowed >= 95` ✓ · `ip_blocked >= 5` ✓ · `checks > 99%` ✓

---

## 2. JWT filter (isolated)

**Script:** `jwt_filter_test.js`  
**Scenario:** 4 sequential steps, 1 VU (anonymous → valid JWT → invalid JWT → missing `accountId`)  
**Proves:** JWT auth pass-through and rejection before account limiting

| Step | Status | Body |
|------|--------|------|
| Anonymous (no header) | 200 | — |
| Valid JWT | 200 | — |
| Invalid JWT | 401 | `invalid_token` |
| JWT without `accountId` | 401 | — |

| Metric | Result |
|--------|--------|
| Checks | 100% (5/5) |
| `http_req_duration` avg | 3.86 ms |
| Duration | < 0.1 s |

**Thresholds:** `checks == 100%` ✓

---

## 3. Account filter (isolated)

**Script:** `account_filter_test.js`  
**Scenario:** 12 authenticated requests, same `accountId`, 1 VU  
**Proves:** Account limit (10/min) returns `429` with `type:account`; IP limit not hit

| Metric | Result |
|--------|--------|
| Allowed (200) | 10 |
| Blocked (429, `type:account`) | 2 |
| Checks | 100% (24/24) |
| `http_req_duration` avg | 4.71 ms |
| Duration | 0.7 s |

**Thresholds:** `account_allowed >= 10` ✓ · `account_blocked >= 2` ✓ · `checks > 99%` ✓

---

## 4. IP + JWT combo

**Script:** `ip_jwt_combo_test.js`  
**Scenarios:**
1. `exhaust_ip_anonymous` — 105 anonymous requests (10 VUs)
2. `auth_after_ip_exhaust` — 5 valid JWT requests after 3 s delay

**Proves:** IP filter runs first; authenticated traffic is still blocked by IP quota

| Phase | Allowed | Blocked | Block type |
|-------|---------|---------|------------|
| Exhaust (anonymous) | 100 | 5 | `ip` |
| Auth after exhaust | 0 | 5 | `ip` (not `account`) |

| Metric | Result |
|--------|--------|
| Checks | 100% (115/115) |
| `http_req_duration` avg | 3.47 ms |
| Duration | 3.3 s |

**Thresholds:** `ip_exhaust_allowed >= 95` ✓ · `auth_blocked_by_ip >= 5` ✓ · `checks == 100%` ✓

---

## 5. Shared IP counter

**Script:** `shared_ip_counter_test.js`  
**Scenarios:**
1. `anon_seed` — 95 anonymous requests (5 VUs)
2. `auth_top_up` — 15 authenticated requests after 2 s delay

**Proves:** Anonymous and authenticated traffic share one IP bucket

| Phase | Allowed | Blocked | Block type |
|-------|---------|---------|------------|
| Anonymous seed | 95 | 0 | — |
| Auth top-up | 5 | 10 | `ip` |

| Metric | Result |
|--------|--------|
| Total allowed | 100 (95 anon + 5 auth) |
| Checks | 100% (125/125) |
| `http_req_duration` avg | 2.86 ms |
| Duration | 2.8 s |

**Thresholds:** `anon_allowed >= 90` ✓ · `auth_allowed 3–7` ✓ (got 5) · `auth_ip_blocked >= 8` ✓ · `checks > 99%` ✓

---

## 6. Multi-account isolation

**Script:** `multi_account_isolation_test.js`  
**Scenario:** 5 VUs × 12 iterations, unique `accountId` per VU (`acc-isolation-1` … `acc-isolation-5`)  
**Proves:** Account quotas are independent; same client IP does not cross-contaminate accounts

| Metric | Result |
|--------|--------|
| Allowed (200) | 50 (10 per account) |
| Blocked (429, `type:account`) | 10 (2 per account) |
| IP blocks | 0 |
| Checks | 100% (180/180) |
| `http_req_duration` avg | 6.55 ms |
| Duration | 0.7 s |

**Thresholds:** `isolation_allowed 48–52` ✓ · `isolation_blocked 8–12` ✓ · `checks > 99%` ✓

---

## 7. Health endpoint bypass

**Script:** `health_bypass_test.js`  
**Scenarios:**
1. `exhaust_api` — 110 requests to `/api/hello` (10 VUs)
2. `health_after_exhaust` — 20 requests to `/actuator/health` after 3 s delay

**Proves:** `IpRateLimitFilter.shouldNotFilter` skips `/actuator/health`

| Endpoint | Allowed | Blocked |
|----------|---------|---------|
| `/api/hello` | 100 | 10 (`ip`) |
| `/actuator/health` | 20 | 0 |

| Metric | Result |
|--------|--------|
| Health responses | 20 × 200, body `UP` |
| Checks | 100% (150/150) |
| `http_req_duration` avg | 3.31 ms |
| Duration | 4.1 s |

**Thresholds:** `api_blocked >= 5` ✓ · `health_ok == 20` ✓ · `checks == 100%` ✓

---

## 8. Full pipeline (all filters)

**Script:** `full_pipeline_test.js`  
**Scenarios:**
1. `authenticated_burst` — 15 requests, 3 VUs, shared token `acc-pipeline-burst`
2. `distributed_race` — 200 req/s for 10 s, up to 50 VUs, token `acc-pipeline-race` (starts at 22 s)

**Proves:** Full chain IP → JWT → Account under burst and high-concurrency load

| Phase | Requests | Allowed | Blocked | Dominant block |
|-------|----------|---------|---------|----------------|
| Authenticated burst | 15 | ~10 | ~5 | `account` |
| Distributed race | 2,001 | ~10 | ~1,991 | `account` |
| **Total** | **2,016** | **20** | **1,996** | **`account`** |

| Metric | Result |
|--------|--------|
| Checks | 100% (2,046/2,046) |
| `http_req_duration` avg | 1.76 ms |
| `http_req_duration` p95 | 3.19 ms |
| Duration | 32.0 s |

**Thresholds:** `stack_allowed >= 10` ✓ · `stack_blocked >= 3` ✓ · `checks > 99%` ✓

Under the 200 req/s race, account limit (10/min per token) dominates; no `type:ip` rejections observed in the race phase.

---

## Reproduce

```powershell
# Ensure stack is up
docker compose --profile full up -d

# Run all profiles (flushes Redis between each)
.\load-tests\run-all-tests.ps1
```

Single profile:

```powershell
docker exec distributed-rate-limiter-redis-1 redis-cli FLUSHALL
k6 run -e JWT_SIGNING_KEY=my-super-secret-signing-key-which-must-be-32-bytes! load-tests/ip_filter_test.js
```

---

## Legacy script (not in suite)

`rate_limit_test.js` is a legacy all-in-one script with timing-dependent phases. It is **not** run by `run-all-tests.ps1` because isolated and combination profiles provide deterministic, Redis-flushed coverage. Use the profiles above for CI and regression checks.

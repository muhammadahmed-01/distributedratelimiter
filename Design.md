# Distributed Rate Limiter – Design Document

> **For reviewers:** This is the "why behind the how." The README shows what runs and what was tested; this document explains the trade-offs you'd discuss in a system design interview or architecture review.

---

## 1. Architectural philosophy

**The goal isn't to block traffic — it's to protect the system while keeping legitimate users moving.**

Every request hits three checkpoints before business logic:

**IP → JWT → Account → Controller**

Each layer is deliberately small, fast, and independent. Cheap checks run first so you never parse a JWT for traffic you've already decided to reject. Expensive identity work only happens when the request has earned it.

This is the same instinct behind physical security: **guard the gate before you guard the treasury.** A scraper shouldn't consume your auth pipeline. A stolen token shouldn't get unlimited retries because you only rate-limited by IP.

The tiered model also maps cleanly to how teams operate:

- **Ops** cares about IP floods and Redis health.
- **Security** cares about token validity and abuse patterns.
- **Product** cares that paying users aren't throttled because someone else shared their NAT.

One pipeline, three audiences, one set of metrics to reconcile.

---

## 2. Rate limiting algorithm comparison

Choosing an algorithm is choosing what you're willing to get wrong. Fixed windows are easy until someone sends 2× traffic at the boundary. Token buckets are forgiving until you need hard caps. This project picked **sliding-window log** because correctness at the limit matters more than saving a few Redis bytes on a security boundary.

| Algorithm                  | Accuracy | Memory-Efficiency | Burst Handling              | Complexity | Best For                                      |
|----------------------------|----------|-------------------|-----------------------------|------------|-----------------------------------------------|
| **Fixed Window**           | ⭐⭐       | ⭐⭐⭐⭐⭐             | Allows 2× burst at boundary | ⭐          | Simple internal APIs                          |
| **Sliding Window Log** ✅   | ⭐⭐⭐⭐⭐    | ⭐⭐                | No burst exploitation       | ⭐⭐⭐        | Security-critical, user-facing APIs           |
| **Sliding Window Counter** | ⭐⭐⭐⭐     | ⭐⭐⭐⭐⭐             | ~5% error at boundary       | ⭐⭐         | High-traffic, memory-constrained              |
| **Token Bucket**           | ⭐⭐⭐⭐     | ⭐⭐⭐⭐              | Allows controlled bursts    | ⭐⭐         | APIs where burst is acceptable (e.g. uploads) |
| **Leaky Bucket**           | ⭐⭐⭐      | ⭐⭐⭐⭐              | Smooths all bursts          | ⭐⭐         | Queue-based systems, traffic shaping          |

**Why Sliding Window Log was chosen here:**
This project enforces strict per-user limits where boundary-burst exploits would
allow a malicious client to send 2× the intended limit. SWL eliminates this
entirely at the cost of higher memory, which is an acceptable tradeoff for a security
layer where correctness beats memory efficiency.

---

## 3. Why Redis + Lua?

**The hard part of rate limiting isn't counting — it's counting correctly when 3,000 requests arrive in the same second from three app instances.**

Redis alone gives you speed. Lua inside Redis gives you **atomicity**: read, decide, and write as one indivisible step. No race where two pods both think there's one slot left.

### Benefits of Lua in Redis

- **Atomic execution** – Redis guarantees a Lua script runs as one operation.
- **ZERO race conditions** – No concurrent clients can interrupt.
- **High performance** – Evaluated server‑side; no round trip cost.
- **Deterministic logic** – Every node behaves the same.

### Why not use Redis transactions?

- More verbose.
- Still allow other ops between MULTI/EXEC unless carefully managed.
- Harder to reason about under concurrency.

**Lua keeps the logic simple, fast, and bulletproof.**

---

## 4. Multi‑Layer Rate Limiting Strategy

### 4.1 IP Rate Limiting – The Outer Firewall

Purpose:

- Block scrapers, bots, and abusive IPs.
- Let legitimate traffic through cheaply.

Reasoning:

- Cheapest layer (no JWT parsing).
- Prevents denial-of-service early.

### 4.2 JWT Authentication \- Identity Establishment

Purpose:

- Validate client identity and extract claims required for per\-account rules.

Reasoning:

- Account\-level limits require a stable identity (user id, tenant id).
- JWT parsing and signature verification are more expensive than IP checks, but they are necessary to enforce fair, user-specific limits.
- Keeping JWT after IP still preserves the cheap early protection while enabling correct account enforcement.

### 4.3 Account/User Rate Limiting \- Identity\-Level Control (After JWT)

After JWT passes, the **AccountRateLimitFilter** applies rules like:

- 100 req/min per user
- 300 req/min per tenant
- Stricter rules for new accounts

Benefits:

- Stops compromised accounts from spamming across proxies.
- Ensures fair usage across tenants.
- Accurate enforcement because the user identity is known and trusted.

---

## 5. Component Breakdown

### Web Layer Filters (Execution Order)

1. **IpRateLimitFilter**  
2. **JwtAuthFilter**  
3. **AccountRateLimitFilter**

This order ensures optimal cost/performance and matches the sequence diagram below: IP checks are cheap and block early; JWT parsing runs only for allowed IPs; account checks run only for authenticated requests.

---

## 6. Sequence Diagram

(The flow below is the canonical execution path used in the project.)

```
Client
  │
  ▼
┌──────────┐
│ IPFilter │─── Block? → End
└──────────┘
        │
        ▼
┌───────────┐
│ JWTFilter │── Block? → End
└───────────┘
        │
        ▼
┌────────────────────┐
│ AccountStatusFilter│── Block? → End
└────────────────────┘
        │
        ▼
 Controller
```

---

## 7. Domain Separation Rationale

This section maps logical domains to the project's folder structure to keep concerns isolated and make the code easy to review.

### config/

Infra and Spring bean configuration:

- RedisConfig
- WebFilterConfig
- SecurityFilterConfig

Purpose: environment wiring, beans, and external integrations. Keeps deployment details out of business logic.

### controller/

Web/API surface:

- DemoController

Purpose: HTTP endpoints and request/response shaping only. No rate-limit or security enforcement logic here.

### filter/

Request policing and pipeline guards:

- CorrelationIdFilter
- IpRateLimitFilter
- JwtAuthFilter
- AccountRateLimitFilter

Purpose: Ordering, cheap-first checks, and blocking decisions. Filters orchestrate rate-limit primitives.

### rateLimit/

Core rate-limit algorithms and models:

- SlidingWindowLogRateLimiter
- RateLimitResponse

Purpose: Algorithmic logic for counting and decisions. Framework-agnostic for easy unit testing and reuse.

### security/

Authentication and secrets:

- JwtGen
- SecurityConfig

Purpose: JWT creation/verification and related security utilities. Separated from rate-limiting rules.

### metrics/

Observability:

- RateLimitMetrics

Purpose: Micrometer counters and timers for rate-limit decisions and Redis latency.

### util/

Shared helpers:

- JsonErrorWriter

Purpose: Safe JSON serialization for error responses.

### resources/

Static runtime assets:

- rate_limiter.lua

Purpose: Lua scripts and other non-code assets used by Redis or the runtime environment.

This structure matches the README and emphasizes separation of concerns: configuration, API surface, traffic policing, algorithmic core, security, and resources each have a focused place. That makes the codebase easier to review, test, and extend.

---

## 8. Trade-offs summary

No architecture is free. This one optimizes for **correctness under burst** and **operational clarity** — the things that matter when someone is paging you at 2 a.m.

### What we optimized for

- Production resilience under bursts.
- Operational simplicity.
- Clear debugging signals.
- Ability to scale horizontally.

### What we sacrificed

- Some memory overhead from SWL.
- Slight complexity in Lua scripts.
- Extra initial work structuring filters & configs.

**But the payoff is a system that’s simple to operate and very hard to break.**

---

## 9. Final verdict

If you remember one sentence from this document:

**Fail fast, fail cheap, fail early.**

That's not pessimism — it's respect for your downstream services and your on-call engineer. Every millisecond spent on a request you've already decided to block is a millisecond stolen from a real user.

What this architecture buys you:

- **Predictable load** on controllers and databases  
- **Protection from noisy neighbors** without manual firewall rules  
- **Fair quotas** once identity is established  
- **Room to grow** — add tenant-level or endpoint-level limits without rewriting the pipeline  

It's conservative on purpose. Production systems rarely fail because someone chose the clever algorithm. They fail because nobody agreed on what "limited" means under concurrency.

## 10. What I'd Do Differently at Scale

### Redis is a Single Point of Failure

The current implementation uses a single Redis instance. Under failure, all rate limiting
stops. Production fix:

- **Redis Sentinel** for automatic failover (primary + 2 replicas)
- **Redis Cluster** for horizontal sharding if key space becomes large
- **Fail-open vs fail-closed decision**: configurable via `ratelimit.redis.failure-policy`.
  Default is **fail-closed** (`503 Service Unavailable`) because a down Redis means
  rate limiting is broken. Set `FAIL_OPEN` for availability-first projects where
  unprotected traffic is acceptable during outages.

### Sliding Window Log Memory Grows with Traffic

SWL stores one timestamped entry per request in Redis. At high traffic:

- 10,000 req/min per key = 10,000 entries in the sorted set
- Across 1M active users = significant Redis memory pressure

Production fix: **Sliding Window Counter** — stores only 2 integers (current and
previous window counts) instead of one entry per request. Trades ~5% accuracy
at window boundaries for O(1) memory per key regardless of traffic volume.

### JWT Parsing Happens Per Request

Currently each filter instance parses and verifies the JWT signature independently.
At scale this is CPU-expensive and doesn't support token revocation.

Production fix:

- Cache validated token claims in Redis with TTL matching token expiry
- Maintain a distributed token blacklist in Redis for revocation
- This converts per-request cryptographic verification into a Redis lookup

### Rate Limit Headers

The service returns `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`,
and `Retry-After` on blocked responses. Clients should honor `Retry-After` with
exponential backoff to avoid retry storms.

### Production Secret Management

JWT signing keys must not be committed to source control. This project loads
`JWT_SIGNING_KEY` from environment variables (see `.env.example`).

In production, load secrets from a managed store:

- **AWS Secrets Manager** / **Parameter Store**
- **HashiCorp Vault**
- **Kubernetes Secrets**

Example pattern (not wired in this project — requires a paid AWS account):

```java
// SecretsManagerClient client = ...;
// String key = client.getSecretValue(...).secretString();
```

The project intentionally uses env vars for portability and zero cloud cost.

### The Algorithm Tradeoff at Netflix/Stripe Scale

At millions of requests/second on shared infrastructure, even Redis Lua becomes
a bottleneck. Escalation path:

- **Token Bucket in application memory** (per-instance, no Redis for high-frequency
  endpoints) — trades perfect distributed accuracy for speed
- **Envoy/Nginx rate limiting at the proxy layer** — moves enforcement out of
  application code entirely
- **Account sharding** — partition users across rate limit shards to reduce
  hot-key contention on popular accounts

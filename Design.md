# Distributed Rate Limiter – DESIGN.md

## 1. Architectural Philosophy
The system follows a tiered‑defense model: IP → JWT Authentication → Account → Business Logic.  
Each layer is intentionally simple, fast, and independent so cheaper checks run first and protect more expensive work downstream.

This mirrors traditional, time‑tested security architecture: guard the gate before you guard the treasury.

---

## 2. Why Sliding Window Log (SWL) and Not Fixed Window?

### 🟢 Sliding Window Log – Chosen
SWL gives:
- **High accuracy** – no burstiness at window boundaries.
- **Fair usage distribution** – load spreads evenly.
- **Predictable protection** – malicious clients cannot abuse the classic fixed-window "boundary reset loophole."

### 🔴 Fixed Window – Not Chosen
Fixed Window is simpler, but:
- Requests can double-burst across window edges.
- Attackers can time their calls to bypass early limits.
- It’s unfair to well‑behaved clients during resets.

### ⚖️ Trade‑off Summary
| Aspect | Sliding Window Log | Fixed Window |
|-------|--------------------|--------------|
| Accuracy | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| Complexity | ⭐⭐⭐ | ⭐ |
| Predictability | ⭐⭐⭐⭐ | ⭐⭐ |
| Memory Use | Higher | Lower |
| Boundary Burst Exploit | No | Yes |

**Verdict:** Sliding Window Log gives 80% benefit for slightly more complexity — worth it.

---

## 3. Why Use Redis + Lua Scripts?

### ⚡ Atomicity & Speed
Redis by itself is fast, but the moment you need:
- read → modify → write  
- with guaranteed **atomic** behavior  
- at **scale**  

…then Lua is the old-school warrior you want guarding your gates.

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
- AwsConfig
- RedisConfig
- WebFilterConfig
- JwtConfig

Purpose: environment wiring, beans, and external integrations. Keeps deployment details out of business logic.

### controller/
Web/API surface:
- DemoController

Purpose: HTTP endpoints and request/response shaping only. No rate-limit or security enforcement logic here.

### filter/
Request policing and pipeline guards:
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
- JwtSecretService
- SecurityConfig

Purpose: JWT creation/verification and related security utilities. Separated from rate-limiting rules.

### resources/
Static runtime assets:
- rate_limiter.lua

Purpose: Lua scripts and other non-code assets used by Redis or the runtime environment.

This structure matches the README and emphasizes separation of concerns: configuration, API surface, traffic policing, algorithmic core, security, and resources each have a focused place. That makes the codebase easier to review, test, and extend.

---

## 8. Trade-Offs Summary

### What We Optimized For
- Production resilience under bursts.
- Operational simplicity.
- Clear debugging signals.
- Ability to scale horizontally.

### What We Sacrificed
- Some memory overhead from SWL.
- Slight complexity in Lua scripts.
- Extra initial work structuring filters & configs.

**But the payoff is a system that’s simple to operate and very hard to break.**

---

## 9. Final Verdict
The architecture follows a conservative, time-tested philosophy:  
**Fail fast, fail cheap, fail early.**

The tiered approach ensures:
- Minimal load on downstream services  
- Strong protection against noisy neighbors  
- Fair usage for honest users  
- Extensibility for future rate‑limit types

---

## How to present this in a portfolio
- Start with the problem: why rate limiting matters and boundary-burst issues with naive windows.
- Explain the cheap-first filter ordering and show logs that demonstrate early blocking.
- Highlight the Redis+Lua atomicity argument and show the Lua script (resources/rate_limiter.lua).
- Walk through one request in the sequence diagram, pointing out where identity is extracted and account rules applied.
- Mention testability: core algorithms live in rateLimit/ and are framework-agnostic.

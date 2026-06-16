# Upwork Portfolio Blurb

## Short version (paste into proposal)

Your API needs rate limits that stay correct under burst traffic and across multiple instances, not a counter that breaks at the second pod. I build Redis-backed sliding-window limiters with tiered filters (IP, JWT, account), load-test them with k6, and reconcile metrics in Grafana.

This reference implementation passed **8/8 k6 profiles** over **~77,000 requests** (full-pipeline burst: **~60,900** requests, **58,763** IP blocks at **~1,100 req/s** peak). Design trade-offs and measured numbers are in the README.

**GitHub (verify locally):** https://github.com/muhammadahmed-01/DistributedRateLimiter

---

## Medium version (portfolio description)

Distributed rate limiting is a concurrency problem, not a configuration toggle. Fixed windows double-count at boundaries; in-memory counters diverge across pods; parsing JWTs before rejecting abusive IPs wastes CPU on traffic you already know is hostile.

Muhammad Ahmed built a Spring Boot reference service with atomic sliding-window logs in Redis + Lua, a three-layer filter chain, and an eight-profile k6 suite that exercises isolated filters, combo scenarios, and a **3,000 req/s × 20s** full-pipeline race. Measured on Docker (2026-06-13): **8/8 PASS**, **~77,000** HTTP requests, Grafana totals within **±1%** of k6 counters. Includes fail-closed/fail-open Redis policy, Prometheus metrics, and production failure-mode notes (Redis down, clock skew, hot keys).

**GitHub:** https://github.com/muhammadahmed-01/DistributedRateLimiter

---

## Proposal hooks (pick one)

**Pain + proof:**
"Built and load-tested Redis-backed rate limiter; metrics and design tradeoffs in README: https://github.com/muhammadahmed-01/DistributedRateLimiter"

**Evidence over promises:**
"8/8 k6 profiles, ~77k requests, full-pipeline burst with ~1,100 req/s IP blocks observed. Run `docker compose --profile full up --build` and compare Grafana Totals to the README: https://github.com/muhammadahmed-01/DistributedRateLimiter"

**Architecture depth:**
"Sliding-window log in Redis + Lua, tiered IP/JWT/account filters, fail-closed default. Load test artifacts and Grafana alignment in repo: https://github.com/muhammadahmed-01/DistributedRateLimiter"

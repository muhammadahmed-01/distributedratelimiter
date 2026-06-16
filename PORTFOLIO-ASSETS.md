# Portfolio Assets Pack

Upload these when pitching backend/API work on Upwork or pinning on GitHub. Every number maps to [load-tests/K6_RESULTS.md](load-tests/K6_RESULTS.md) (2026-06-13 scaled run).

---

## Upwork Portfolio Title

**Redis Rate Limiter: 77k k6 Requests, 8/8 PASS | Sliding Window + Grafana Proof**

Alternative (shorter):

**Distributed Rate Limiting: 8/8 k6 PASS, Grafana Totals Match Prometheus**

---

## Upwork Portfolio Description

API abuse and burst traffic break naive counters: fixed windows double at boundaries, in-memory limits fail across pods, and JWT parsing before IP rejection wastes CPU.

This portfolio piece documents a Spring Boot + Redis sliding-window limiter with tiered filters, fail-closed Redis policy, and measured load tests: **8/8 k6 profiles PASS**, **~77,000** requests, full-pipeline burst **~60,900** requests with **58,763** IP blocks, Grafana **Totals** within **±1%** of k6. Includes design doc, production failure modes, and one-command Docker repro.

---

## Images to Upload (Order)

| Order | File | Caption for Upwork |
|-------|------|-------------------|
| 1 | `docs/images/grafana-dashboard.png` | Grafana Totals after k6 suite: IP/account allowed vs blocked align with k6 counters (±1%) |
| 2 | `load-tests/K6_RESULTS.md` (rendered summary table) | Summary: 8/8 k6 PASS, ~77k requests, tiered filter chain |
| 3 | README results table (browser screenshot) | Eight-profile breakdown: isolated filters through 3k req/s full pipeline |

---

## GitHub About Text

Redis-backed distributed rate limiter. Sliding-window log + Lua, tiered IP/JWT/account filters. 8/8 k6 PASS, ~77k requests. Grafana + Prometheus metrics.

**Repository:** https://github.com/muhammadahmed-01/DistributedRateLimiter

---

## LinkedIn Post Snippet

Most rate limiter demos stop at an in-memory counter. Under burst traffic and multiple instances, that is when fixed windows and race conditions show up.

I documented a Redis + Lua sliding-window limiter with k6 proof: **8/8 profiles PASS**, **~77,000** requests, Grafana totals matching Prometheus. Tiered IP/JWT/account filters, fail-closed default, production failure modes in the README.

Reproducible in Docker. Link in comments.

---

## Related Files

| File | Use |
|------|-----|
| `UPWORK-BLURB.md` | Proposal paste block |
| `load-tests/K6_RESULTS.md` | Measured k6 evidence |
| `Design.md` | Architecture interview prep |
| `docs/RATE-LIMIT-AUDIT-CHECKLIST.md` | Discovery call process |
| `docs/images/grafana-dashboard.png` | Primary Upwork screenshot |

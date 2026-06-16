# Phase 1 Rate Limit Audit: Statement of Work

**Service:** Distributed API rate limiting review (Spring Boot + Redis)  
**Prepared by:** Muhammad Ahmed  
**Version:** Sample SOW (June 2026)

---

## Overview

This is a fixed-scope audit of **one public API path** where abuse, burst traffic, or missing limits is suspected. The goal is a prioritized findings report with **measured evidence**: baseline traffic, limit policy review, Redis/Lua correctness check, k6 regression results, and a clear P0/P1/P2 action list.

The investigation sequence matches the tiered IP → identity → account model documented in this reference implementation (~77k k6 requests, 8/8 profiles PASS).

---

## Scope (Included)

| Item | Detail |
|------|--------|
| **One hot endpoint** | Single read or write path (e.g. login, search, checkout) |
| **Traffic baseline** | Current RPS, 429 rate, abuse pattern, recent incidents |
| **Limit policy review** | Per-IP, per-user, per-tenant quotas and window sizes |
| **Algorithm check** | Fixed vs sliding window; race conditions under concurrency |
| **Redis/Lua review** | Atomicity, key design, TTL, failure policy (fail-open vs fail-closed) |
| **Observability** | Prometheus/Grafana or equivalent counter reconciliation |
| **k6 regression** | At least two profiles: isolated limit + burst scenario |
| **Findings report** | P0 / P1 / P2 priority matrix with evidence links |
| **Optional fix PR** | Minimal patch (limit tuning, Lua fix, filter ordering) |
| **Handoff call** | 30-minute walkthrough of findings and next steps |

**Typical timeline:** 3 to 5 business days from access grant to final report delivery.

---

## Out of Scope

Phase 1 is an audit, not a platform rewrite.

- Full API gateway or CDN migration
- WAF rule authoring and DDoS mitigation at provider level
- Multi-region Redis cluster design and failover
- Greenfield development or new feature work
- SOC 2, PCI, or compliance audit work
- Mobile app throttling (client-side only)

Follow-on work covers edge proxy limits, Redis Sentinel/Cluster, tenant-level quotas, and CI k6 gates. Scoped separately after Phase 1 findings.

---

## Deliverables

1. **Baseline snapshot** (RPS, 429 rate, current limits, sample headers)
2. **Audit report** (executive summary, findings table, P0/P1/P2 matrix)
3. **k6 regression output** (at least burst + isolation profiles)
4. **Reproduction steps** (curl/k6 commands or staging URL)
5. **Optional:** GitHub PR with limit tuning or Lua/filter fix
6. **30-minute handoff** (screen share or recorded walkthrough)

---

## Acceptance Criteria

| Criterion | Met when |
|-----------|----------|
| Baseline documented | Written traffic profile with endpoint URL and limit headers |
| Algorithm assessed | Fixed vs sliding window trade-offs stated with evidence |
| Failure mode documented | Redis down behavior matches stated policy |
| k6 evidence | At least one burst profile with pass/fail thresholds |
| Findings prioritized | P0/P1/P2 matrix with owner and effort estimate |

---

## Reference Implementation

Reproducible proof in this repository:

```bash
docker compose --profile full up --build -d
.\load-tests\run-all-tests.ps1
```

Measured results: `load-tests/K6_RESULTS.md`  
Process checklist: `docs/RATE-LIMIT-AUDIT-CHECKLIST.md`

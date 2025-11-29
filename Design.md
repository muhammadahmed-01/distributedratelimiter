# Architecture & Design Overview

## Goal
Provide a clean, extensible security pipeline with three request filters:
1. **IPFilter**
2. **JWTFilter**
3. **AccountStatusFilter**

Requests follow a strict short‑circuiting flow:  
If a filter blocks, the rest are never invoked.

---

## High‑Level Flow

```
Client → IPFilter → JWTFilter → AccountStatusFilter → Controller
```

- **IPFilter first** — cheapest computation, eliminates bad traffic early.
- **JWTFilter second** — validates identity.
- **AccountStatusFilter last** — enforces business rules.

---

## IP Filter

### Responsibilities
- Validate request IP against allowed and blocked lists.
- Short‑circuit immediately with `403` on violation.
- Should produce **zero noise** in logs unless blocking.

### Config
- CIDR support (e.g., `10.0.0.0/8`, `192.168.1.0/24`)
- Single IP literals (`203.0.113.15`)

### Log Requirements
- Allowed IPs → **no logs**
- Blocked IPs → concise warn:
```
[IPFilter] Blocked IP: <ip>
```

---

## JWT Filter

### Responsibilities
- Validate JWT signature.
- Verify required claims (`sub`, `iat`, `exp`, etc.)
- Attach authenticated user to request context.

### Failure Short‑Circuit
- Missing token → `401`
- Invalid signature → `401`
- Expired token → `401`

### Log Requirements
- Only warn on failures, no verbose debug.

---

## Account Status Filter

### Responsibilities
- Ensure user account is active, not locked, not disabled.
- Fetch user metadata from persistence/identity provider.

### Failure Short‑Circuit
- Inactive → `403`
- Locked → `403`

### Log Requirements
- Only one clear warning line per block.

---

## Design Principles

### 1. **Fail Fast**
Each filter returns quickly on failure, minimizing computation.

### 2. **Single Responsibility Per Filter**
No filter knows about another.

### 3. **Minimal Logging**
Only log when needed:
- A request was blocked.
- A token was invalid.
- An account was disallowed.

### 4. **Testability First**
Every filter supports:
- happy path
- block path
- ensures next filter is not invoked if it blocks

### 5. **Predictable Structure**
All filters follow the same skeleton:
- Extract context
- Validate
- Short‑circuit
- Pass to next

---

## Sequence Diagram

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

## Testing Matrix

| Scenario | IPFilter | JWT | Account | Expected |
|---------|----------|-----|---------|----------|
| Blocked IP | ❌ | — | — | 403 |
| Missing JWT | ✅ | ❌ | — | 401 |
| Invalid JWT | ✅ | ❌ | — | 401 |
| Expired JWT | ✅ | ❌ | — | 401 |
| Inactive Account | ✅ | ✅ | ❌ | 403 |
| Fully Valid | ✅ | ✅ | ✅ | 200 |

---


## Summary

This design provides:
- A clean security pipeline.
- Predictable fail-fast behavior.
- Minimal but high‑signal logs.
- A structure that’s easy to extend without rewriting everything.

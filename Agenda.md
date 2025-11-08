
---

## 🎯 Final Project Task List: Distributed Rate Limiter API

**Goal:** Implement a production-grade, tiered rate limiting service using Spring Boot and Redis, proving mastery of concurrency and cloud-native security principles.

### Phase 1: High-Value Core Logic (40% Effort / 80% Resume Value)

Focus on the toughest challenges: Atomicity (A3) and Security Filtering (B1).

| Priority | Task ID | Task Description | Key Implementation Skill |
| :---: | :---: | :--- | :--- |
| **P1** | **A3** | **Implement the Sliding Window Log (SWL) Algorithm:** Create and execute the **Redis Lua script** for the Account ID limit. This ensures **global atomicity** and concurrency safety. | Distributed Concurrency, Lua Scripting, Redis ZSET |
| **P1** | **B1** | **Implement the IP Address Filter (Fixed Window):** Create the first **Spring Web Filter** to extract the IP (via `X-Forwarded-For`). Use simple `INCR`/`EXPIRE` Redis commands for a **low-cost, high-performance** DDoS defense. | HTTP/Network Headers, Fixed Window Algorithm, Fail-Fast Optimization |
| **P2** | **B2** | **Implement JWT/Security Filter:** Integrate Spring Security and create a filter to **validate the JWT**. Successfully extract the **Account ID** and **User ID** from the payload for rate-limiting keys. | Spring Security, JWT Validation, Claims Extraction |

---

### Phase 2: Resilience and Architectural Integrity (30% Effort / High SAA Value)

Focus on anticipating failure, credential security, and clean separation of concerns.

| Priority | Task ID | Task Description | Key Implementation Skill |
| :---: | :--- | :--- | :--- |
| **P2** | **C1** | **Integrate Resilience4J (Circuit Breaker):** Wrap the Redis calls in the **`RateLimitService`** with a Circuit Breaker. Implement a **fallback method** (e.g., allow a few requests) to maintain service integrity when Redis is down. | Resilience, Fault Tolerance, Hystrix Replacement |
| **P2** | **B3** | **Secure Credential Management:** Use the **AWS SDK (or document the integration)** to fetch the JWT signing key from a secure location (e.g., AWS Secrets Manager/Parameter Store) instead of hardcoding. | Cloud Security, Principle of Least Privilege |
| **P3** | **A1** | **Setup & Containerization:** Finalize the Spring Boot setup. Create the **`Dockerfile`** and **`docker-compose.yml`** to run the App and Redis simultaneously. | DevOps Hygiene, Infrastructure-as-Code (IaC) |

---

### Phase 3: Polish, Documentation, and Optimization (30% Effort / Low-Cost)

Focus on making the project easily reviewed and understood by the Hiring Manager.

| Priority | Task ID | Task Description | Key Implementation Skill |
| :---: | :--- | :--- | :--- |
| **P3** | **D1** | **Write `DESIGN.md` (The Architect's Document):** Document the **trade-offs** for choosing SWL vs. Fixed Window, why Lua was used, and the tiered Account/User/IP system. | Architectural Judgment, Technical Communication |
| **P4** | **A2** | **Finalize Service Abstraction:** Refactor the **`RateLimitService`** interface and clean up the Java code that calls the Redis script for better readability. | Clean Code, API Design |
| **P4** | **C2** | **Observability:** Enable **Spring Boot Actuator** and configure structured logging (JSON format) to track rate limit violations and service health. | Monitoring, Production Readiness |
| **P4** | **D2** | **README/API Examples:** Write a clean **`README.md`** with an architecture diagram and simple `curl` examples showing a successful request and a rejected (429) request. | Presentation Skills, Usability |

---

### ✅ Success Criteria

Upon completion, you will have a project that allows you to confidently answer interview questions about:

1.  **Concurrency:** (Task A3)
2.  **Resilience:** (Task C1)
3.  **Security Filtering:** (Task B1, B2)
4.  **Architectural Trade-offs:** (Task D1)
5.  **Cloud Security:** (Task B3)
# Rate Limiting & JWT Auth Demo

A compact Spring Boot demo showcasing IP-based rate limiting, JWT authentication, and account-level rate limiting using a sliding-window log approach with Redis+Lua for atomic operations. Designed to be easy to read, run, and demonstrate in a portfolio.

---

## 🚀 Features

- IP Rate Limiting (first line of defense)  
- JWT Authentication Filter (parses and validates tokens)  
- Account Rate Limiting (applies after successful auth)  
- Deterministic Redis+Lua scripts for atomic counters  
- Clear filter ordering to minimize wasted work

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

1. Start Redis (Docker recommended):  
   `docker run -p 6379:6379 --name redis -d redis:alpine`

2. Build & run the app:  
   - Using Maven: `mvn spring-boot:run`  
   - Or build and run the generated jar: `mvn clean package && java -jar target/*.jar`

3. Generate or obtain a JWT (use JwtGen or any JWT tool with the configured secret).

4. Test the demo endpoint:  
   `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/hello`

---

## 📄 More Details

See Design.md for architecture rationale, trade-offs, and domain separation notes.

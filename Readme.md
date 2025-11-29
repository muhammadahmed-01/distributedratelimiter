# Rate Limiting & JWT Auth Demo

A clean and minimal Spring Boot setup demonstrating **IP-based rate limiting**, **JWT authentication**, and **account-level rate limiting**, following an 80/20 approach.

---

## 🚀 Features

* **IP Rate Limiting** (first line of defense)
* **JWT Authentication Filter** (parses and validates tokens)
* **Account Rate Limiting** (applies only after authenticated access)
* **Proper Filter Ordering** so that expensive work never occurs unnecessarily
* **Clean logs** for debugging and observability

---

## 🧪 Sample Logs (cleaned)

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

These logs show exactly when requests are allowed vs blocked, and confirm downstream filters stop executing once IP throttling triggers.

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

## ⚙️ Running the Project

1. Download redis docker image and run it
2. Generate a JWT Token using `JwtGen` file or any JWT tool with matching secret key
3. Run the application
4. Test this URL: http://localhost:8080/api/hello with curl or Postman

---

## 🧭 Filter Ordering Overview

1. **IpRateLimitFilter** runs first — blocks early.
2. **JwtAuthFilter** parses token only if IP allowed.
3. **AccountRateLimitFilter** applies user/account limits.

This ensures optimal performance, security, and predictable behavior.

---

## 📄 More Details

See `design.md` for architecture and flow diagrams.

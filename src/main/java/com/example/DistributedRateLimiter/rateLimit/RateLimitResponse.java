package com.example.DistributedRateLimiter.rateLimit;

public record RateLimitResponse(
        boolean allowed,
        long remaining,
        long resetTimeSeconds
) {}
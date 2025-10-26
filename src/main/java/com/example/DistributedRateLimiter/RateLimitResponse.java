package com.example.DistributedRateLimiter;

public record RateLimitResponse(
        boolean allowed,
        long remaining,
        long resetTimeSeconds
) {}
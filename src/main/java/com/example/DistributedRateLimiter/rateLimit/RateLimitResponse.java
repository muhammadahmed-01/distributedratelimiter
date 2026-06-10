package com.example.DistributedRateLimiter.rateLimit;

public record RateLimitResponse(
        boolean allowed,
        long remaining,
        long resetTimeSeconds,
        boolean redisUnavailable
) {
    public RateLimitResponse(boolean allowed, long remaining, long resetTimeSeconds) {
        this(allowed, remaining, resetTimeSeconds, false);
    }

    public static RateLimitResponse redisUnavailable(boolean allowThrough) {
        return new RateLimitResponse(allowThrough, allowThrough ? Long.MAX_VALUE : 0, 0, true);
    }
}

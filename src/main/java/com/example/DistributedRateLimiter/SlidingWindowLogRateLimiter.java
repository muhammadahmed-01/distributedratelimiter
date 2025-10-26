package com.example.DistributedRateLimiter;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class SlidingWindowLogRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimiterScript;

    public SlidingWindowLogRateLimiter(RedisTemplate<String, String> redisTemplate, DefaultRedisScript<Long> rateLimiterScript) {
        this.redisTemplate = redisTemplate;
        this.rateLimiterScript = rateLimiterScript;
    }

    public RateLimitResponse checkRateLimit(String accountId, long limit, long windowSeconds) {
        String key = "rate_limit:account:" + accountId;
        long now = Instant.now().getEpochSecond();

        List<String> keys = List.of(key);
        List<Object> args = Arrays.asList(
                String.valueOf(limit),
                String.valueOf(windowSeconds),
                String.valueOf(now)
        );

        Long requestsAfter = redisTemplate.execute(rateLimiterScript, keys, args.toArray());

        // 4. Interpret the result
        if (requestsAfter == null) {
            // Should not happen, but indicates a Redis error or script failure
            throw new RuntimeException("Redis script execution failed.");
        }

        boolean allowed = requestsAfter != -1;

        long remaining = allowed ? limit - requestsAfter : 0;

        // Simple estimation of reset time (improving this is a Phase 3 optimization)
        long resetTime = now + windowSeconds;
        return new RateLimitResponse(allowed, remaining, resetTime);
    }
}

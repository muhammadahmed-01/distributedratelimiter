package com.example.DistributedRateLimiter.rateLimit;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
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
    private final RateLimitMetrics metrics;
    private final RedisFailurePolicy failurePolicy;

    public SlidingWindowLogRateLimiter(RedisTemplate<String, String> redisTemplate,
                                       DefaultRedisScript<Long> rateLimiterScript,
                                       RateLimitMetrics metrics,
                                       @Value("${ratelimit.redis.failure-policy:FAIL_CLOSED}") RedisFailurePolicy failurePolicy) {
        this.redisTemplate = redisTemplate;
        this.rateLimiterScript = rateLimiterScript;
        this.metrics = metrics;
        this.failurePolicy = failurePolicy;
    }

    public RateLimitResponse checkRateLimit(String key, long limit, long windowSeconds, String layer) {
        return metrics.recordRedisLatency(layer, () -> doCheckRateLimit(key, limit, windowSeconds));
    }

    private RateLimitResponse doCheckRateLimit(String key, long limit, long windowSeconds) {
        try {
            long now = Instant.now().getEpochSecond();

            List<String> keys = List.of(key);
            List<Object> args = Arrays.asList(
                    String.valueOf(limit),
                    String.valueOf(windowSeconds),
                    String.valueOf(now)
            );

            Long requestsAfter = redisTemplate.execute(rateLimiterScript, keys, args.toArray());

            if (requestsAfter == null) {
                return handleRedisFailure();
            }

            boolean allowed = requestsAfter != -1;
            long remaining = allowed ? Math.max(0, limit - requestsAfter) : 0;
            long resetTime = now + windowSeconds;
            return new RateLimitResponse(allowed, remaining, resetTime);
        } catch (RedisConnectionFailureException e) {
            return handleRedisFailure();
        } catch (RuntimeException e) {
            if (isRedisFailure(e)) {
                return handleRedisFailure();
            }
            throw e;
        }
    }

    private RateLimitResponse handleRedisFailure() {
        metrics.recordRedisError();
        boolean allowThrough = failurePolicy == RedisFailurePolicy.FAIL_OPEN;
        return RateLimitResponse.redisUnavailable(allowThrough);
    }

    private boolean isRedisFailure(RuntimeException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RedisConnectionFailureException) {
                return true;
            }
            cause = cause.getCause();
        }
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("redis");
    }
}

package com.example.DistributedRateLimiter.rateLimit;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFailurePolicyTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private DefaultRedisScript<Long> rateLimiterScript;

    private RateLimitMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new RateLimitMetrics(new SimpleMeterRegistry());
        when(redisTemplate.execute(any(), any(List.class), any()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
    }

    @Test
    void whenFailClosed_thenDeniesRequest() {
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(
                redisTemplate, rateLimiterScript, metrics, RedisFailurePolicy.FAIL_CLOSED);

        RateLimitResponse response = limiter.checkRateLimit("rate_limit:test", 10, 60, "ip");

        assertThat(response.redisUnavailable()).isTrue();
        assertThat(response.allowed()).isFalse();
    }

    @Test
    void whenFailOpen_thenAllowsRequest() {
        SlidingWindowLogRateLimiter limiter = new SlidingWindowLogRateLimiter(
                redisTemplate, rateLimiterScript, metrics, RedisFailurePolicy.FAIL_OPEN);

        RateLimitResponse response = limiter.checkRateLimit("rate_limit:test", 10, 60, "account");

        assertThat(response.redisUnavailable()).isTrue();
        assertThat(response.allowed()).isTrue();
    }
}

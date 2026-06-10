package com.example.DistributedRateLimiter.rateLimit;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SlidingWindowLogRateLimiterRedisTest {

    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    private SlidingWindowLogRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("rate_limiter.lua"));
        script.setResultType(Long.class);

        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        com.example.DistributedRateLimiter.metrics.RateLimitMetrics metrics =
                new com.example.DistributedRateLimiter.metrics.RateLimitMetrics(registry);

        rateLimiter = new SlidingWindowLogRateLimiter(redisTemplate, script, metrics, RedisFailurePolicy.FAIL_CLOSED);
    }

    @Test
    void whenConcurrentRequests_thenEnforcesLimitAtomically() throws Exception {
        String key = "rate_limit:it:concurrent";
        int limit = 10;
        int totalRequests = 50;
        int threads = 25;

        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    RateLimitResponse response = rateLimiter.checkRateLimit(key, limit, 60, "ip");
                    if (response.allowed()) {
                        allowed.incrementAndGet();
                    } else {
                        blocked.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        executor.shutdown();

        assertThat(allowed.get()).isEqualTo(limit);
        assertThat(blocked.get()).isEqualTo(totalRequests - limit);
    }
}

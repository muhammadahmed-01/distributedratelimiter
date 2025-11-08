package com.example.DistributedRateLimiter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

@Component
public class RateLimiterResilienceTester implements CommandLineRunner {

    private final SlidingWindowLogRateLimiter rateLimiter;
    private final RedisTemplate<String, String> redisTemplate;

    public RateLimiterResilienceTester(SlidingWindowLogRateLimiter rateLimiter,
                                       RedisTemplate<String, String> redisTemplate) {
        this.rateLimiter = rateLimiter;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        String accountId = "test-user-resilience";
        long limit = 10;
        long window = 30; // seconds

        System.out.println("=== Redis Resilience Test ===");
        System.out.println("Phase A: Redis healthy");

        // Phase A: normal operation
        for (int i = 1; i <= 5; i++) {
            RateLimitResponse r = rateLimiter.checkRateLimit(accountId, limit, window);
            System.out.printf("#%d -> Allowed: %s, Remaining: %d%n", i, r.allowed(), r.remaining());
            Thread.sleep(200);
        }

        System.out.println("\nPhase B: Simulate Redis down (Circuit Breaker)");

        // Phase B: simulate Redis failure by shutting down Redis or mocking the template
        // Option 1: clear the connection (for demo only)
        redisTemplate.getConnectionFactory().getConnection().close();

        for (int i = 1; i <= 5; i++) {
            try {
                RateLimitResponse r = rateLimiter.checkRateLimit(accountId, limit, window);
                System.out.printf("Fallback #%d -> Allowed: %s, Remaining: %d%n", i, r.allowed(), r.remaining());
            } catch (Exception ex) {
                System.err.println("Unexpected exception: " + ex.getMessage());
            }
            Thread.sleep(200);
        }

        System.out.println("\nTest complete.");
    }
}

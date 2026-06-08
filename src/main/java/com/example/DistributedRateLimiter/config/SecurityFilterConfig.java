package com.example.DistributedRateLimiter.config;

import com.example.DistributedRateLimiter.filter.AccountRateLimitFilter;
import com.example.DistributedRateLimiter.filter.JwtAuthFilter;
import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityFilterConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(RateLimitMetrics metrics,
                                       @Value("${jwt.signingKey}") String signingKey) {
        return new JwtAuthFilter(metrics, signingKey);
    }

    @Bean
    public AccountRateLimitFilter accountRateLimitFilter(SlidingWindowLogRateLimiter rateLimiter,
                                                         RateLimitMetrics metrics,
                                                         @Value("${ratelimit.account.limit:10}") int limit,
                                                         @Value("${ratelimit.account.windowSeconds:60}") int windowSeconds) {
        return new AccountRateLimitFilter(rateLimiter, metrics, limit, windowSeconds);
    }
}

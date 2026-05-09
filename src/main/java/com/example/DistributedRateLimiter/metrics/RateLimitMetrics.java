package com.example.DistributedRateLimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitMetrics {
    private final Counter ipAllowed, ipBlocked, ipInvalid;
    private final Counter accountAllowed, accountBlocked;
    private final Counter jwtInvalid;
    private final Timer redisLatency;

    private final MeterRegistry registry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Eagerly register ALL combinations so they appear in Grafana from the start
        ipAllowed = build(registry, "ip", "allowed");
        ipBlocked = build(registry, "ip", "blocked");
        ipInvalid = build(registry, "ip", "invalid");
        accountAllowed = build(registry, "account", "allowed");
        accountBlocked = build(registry, "account", "blocked");
        jwtInvalid = build(registry, "jwt", "invalid");

        redisLatency = Timer.builder("rate_limit_redis_latency_seconds")
                .description("Latency of Redis Lua script execution per filter type")
                .tag("type", "ip") // or "account"
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    private Counter build(MeterRegistry r, String type, String status) {
        Counter counter = Counter.builder("rate_limit_requests")
                .tag("type", type)
                .tag("status", status)
                .description("Rate limit decisions by filter layer and outcome")
                .register(r);
        counterCache.put(type + ":" + status, counter);
        return counter;
    }

    public void recordAllowed(String type) {
        get(type, "allowed").increment();
    }

    public void recordBlocked(String type) {
        get(type, "blocked").increment();
    }

    public void recordInvalid(String type) {
        get(type, "invalid").increment();
    }

    private Counter get(String type, String status) {
        if (type == null || status == null) {
            throw new IllegalArgumentException("type and status must not be null");
        }
        String key = type + ":" + status;
        Counter cached = counterCache.get(key);
        if (cached != null) {
            return cached;
        }
        // Build and cache new counter on the fly for unexpected type/status
        // combinations
        return build(registry, type, status);
    }
}
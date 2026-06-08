package com.example.DistributedRateLimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class RateLimitMetrics {
    private final MeterRegistry registry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;

        build(registry, "ip", "allowed");
        build(registry, "ip", "blocked");
        build(registry, "account", "allowed");
        build(registry, "account", "blocked");
        build(registry, "jwt", "invalid");
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

    public void recordRedisError() {
        registry.counter("rate_limit_redis_errors_total",
                "description", "Redis failures encountered during rate limit checks").increment();
    }

    public <T> T recordRedisLatency(String type, Supplier<T> operation) {
        return registry.timer("rate_limit_redis_latency_seconds", "type", type).record(operation);
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
        return build(registry, type, status);
    }
}

package com.example.DistributedRateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    private final int limit;
    private final int windowSeconds;

    public IpRateLimitFilter(StringRedisTemplate redis,
                             @Value("${ratelimit.ip.limit:100}") int limit,
                             @Value("${ratelimit.ip.windowSeconds:60}") int windowSeconds) {
        this.redis = redis;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = extractClientIp(request);
        String key = "ip:" + ip;

        Long current = redis.opsForValue().increment(key);
        if (current != null && current == 1L) {
            // first time — set TTL
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        long remaining = Math.max(0, limit - (current == null ? 0 : current));

        // Set helpful headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if (current != null && current > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}

package com.example.DistributedRateLimiter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.rateLimit.RateLimitResponse;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class IpRateLimitFilter extends OncePerRequestFilter {

    private final SlidingWindowLogRateLimiter rateLimiter;
    private final RateLimitMetrics metrics;
    private final int limit;
    private final int windowSeconds;

    public IpRateLimitFilter(SlidingWindowLogRateLimiter rateLimiter,
                             RateLimitMetrics metrics,
                             @Value("${ratelimit.ip.limit:100}") int limit,
                             @Value("${ratelimit.ip.windowSeconds:60}") int windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = extractClientIp(request);
        String key = "rate_limit:ip:" + ip;

        RateLimitResponse r = rateLimiter.checkRateLimit(key, limit, windowSeconds);

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(r.remaining()));

        if (!r.allowed()) {
            metrics.recordBlocked("ip");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
            response.getWriter().write(String.format("{\"error\":\"rate_limited\", \"type\":\"ip\", \"correlationId\":\"%s\"}", correlationId));
            return;
        }

        metrics.recordAllowed("ip");
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

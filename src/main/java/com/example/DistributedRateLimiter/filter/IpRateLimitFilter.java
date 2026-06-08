package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.rateLimit.RateLimitResponse;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import com.example.DistributedRateLimiter.util.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitFilter.class);

    private final SlidingWindowLogRateLimiter rateLimiter;
    private final RateLimitMetrics metrics;
    private final int limit;
    private final int windowSeconds;
    private final String trustedProxies;

    public IpRateLimitFilter(SlidingWindowLogRateLimiter rateLimiter,
                             RateLimitMetrics metrics,
                             int limit,
                             int windowSeconds,
                             String trustedProxies) {
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.trustedProxies = trustedProxies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = extractClientIp(request);
        String key = "rate_limit:ip:" + ip;

        RateLimitResponse r = rateLimiter.checkRateLimit(key, limit, windowSeconds);

        if (r.redisUnavailable() && !r.allowed()) {
            handleServiceUnavailable(response);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(r.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(r.resetTimeSeconds()));

        if (!r.allowed()) {
            metrics.recordBlocked("ip");
            log.info("IP rate limit blocked ip={}", ip);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
            JsonErrorWriter.writeRateLimited(response, "ip", correlationId);
            return;
        }

        metrics.recordAllowed("ip");
        filterChain.doFilter(request, response);
    }

    private void handleServiceUnavailable(HttpServletResponse response) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
        response.setHeader("Retry-After", String.valueOf(TimeUnit.SECONDS.toSeconds(30)));
        JsonErrorWriter.writeError(response, HttpStatus.SERVICE_UNAVAILABLE.value(), "rate_limit_unavailable", correlationId);
    }

    String extractClientIp(HttpServletRequest req) {
        if (isTrustedProxy(req.getRemoteAddr())) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (trustedProxies == null || trustedProxies.isBlank()) {
            return false;
        }
        return Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(remoteAddr::equals);
    }
}

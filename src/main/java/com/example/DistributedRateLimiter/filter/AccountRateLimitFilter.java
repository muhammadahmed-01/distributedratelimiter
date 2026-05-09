package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.rateLimit.RateLimitResponse;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AccountRateLimitFilter extends OncePerRequestFilter {
    private final SlidingWindowLogRateLimiter service;
    private final RateLimitMetrics metrics;

    public AccountRateLimitFilter(SlidingWindowLogRateLimiter service, RateLimitMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
//        System.out.println("[AccountRateLimitFilter] Checking rate limit for account");
        String accountId = (String) req.getAttribute("accountId");
        if (accountId == null) {
            chain.doFilter(req, res); // anonymous endpoints may be allowed, or apply IP limits only
            return;
        }

        RateLimitResponse r = service.checkRateLimit("rate_limit:account:" + accountId, /*limit*/10, /*window*/60);
        res.setHeader("X-RateLimit-Limit", "10");
        res.setHeader("X-RateLimit-Remaining", String.valueOf(r.remaining()));
        if (!r.allowed()) {
            metrics.recordBlocked("account");
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
            res.getWriter().write(String.format("{\"error\":\"rate_limited\", \"type\":\"account\", \"correlationId\":\"%s\"}", correlationId));
            return;
        }
        metrics.recordAllowed("account");
        chain.doFilter(req, res);
    }
}

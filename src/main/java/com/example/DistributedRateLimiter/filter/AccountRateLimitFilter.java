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
import java.util.concurrent.TimeUnit;

public class AccountRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccountRateLimitFilter.class);

    private final SlidingWindowLogRateLimiter service;
    private final RateLimitMetrics metrics;
    private final int limit;
    private final int windowSeconds;

    public AccountRateLimitFilter(SlidingWindowLogRateLimiter service,
                                  RateLimitMetrics metrics,
                                  int limit,
                                  int windowSeconds) {
        this.service = service;
        this.metrics = metrics;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String accountId = (String) req.getAttribute("accountId");
        if (accountId == null) {
            chain.doFilter(req, res);
            return;
        }

        RateLimitResponse r = service.checkRateLimit("rate_limit:account:" + accountId, limit, windowSeconds);

        if (r.redisUnavailable() && !r.allowed()) {
            handleServiceUnavailable(res);
            return;
        }

        res.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(r.remaining()));
        res.setHeader("X-RateLimit-Reset", String.valueOf(r.resetTimeSeconds()));

        if (!r.allowed()) {
            metrics.recordBlocked("account");
            log.info("Account rate limit blocked accountId={}", accountId);
            res.setHeader("Retry-After", String.valueOf(windowSeconds));
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
            JsonErrorWriter.writeRateLimited(res, "account", correlationId);
            return;
        }

        metrics.recordAllowed("account");
        chain.doFilter(req, res);
    }

    private void handleServiceUnavailable(HttpServletResponse res) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_VAR);
        res.setHeader("Retry-After", String.valueOf(TimeUnit.SECONDS.toSeconds(30)));
        JsonErrorWriter.writeError(res, HttpStatus.SERVICE_UNAVAILABLE.value(), "rate_limit_unavailable", correlationId);
    }
}

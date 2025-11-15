package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.rateLimit.RateLimitResponse;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AccountRateLimitFilter extends OncePerRequestFilter {
    private final SlidingWindowLogRateLimiter service;
    public AccountRateLimitFilter(SlidingWindowLogRateLimiter service) { this.service = service; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        System.out.println("[AccountRateLimitFilter] Checking rate limit for account");
        String accountId = (String) req.getAttribute("accountId");
        if (accountId == null) {
            chain.doFilter(req, res); // anonymous endpoints may be allowed, or apply IP limits only
            return;
        }

        RateLimitResponse r = service.checkRateLimit("account:" + accountId, /*limit*/10, /*window*/60);
        res.setHeader("X-RateLimit-Limit", "10");
        res.setHeader("X-RateLimit-Remaining", String.valueOf(r.remaining()));
        if (!r.allowed()) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.getWriter().write("{\"error\":\"rate_limited\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}

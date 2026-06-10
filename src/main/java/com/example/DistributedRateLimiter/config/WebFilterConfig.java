package com.example.DistributedRateLimiter.config;

import com.example.DistributedRateLimiter.filter.CorrelationIdFilter;
import com.example.DistributedRateLimiter.filter.IpRateLimitFilter;
import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Servlet-layer filters run before Spring Security: correlation ID first, then IP limiting.
 * JWT and account filters are registered in {@link SecurityFilterConfig} instead.
 */
@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> reg = new FilterRegistrationBean<>(new CorrelationIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        reg.setName("correlationIdFilter");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<IpRateLimitFilter> ipRateLimitFilterRegistration(IpRateLimitFilter filter) {
        FilterRegistrationBean<IpRateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        reg.addUrlPatterns("/*");
        reg.setName("ipRateLimitFilter");
        return reg;
    }

    @Bean
    public IpRateLimitFilter ipRateLimitFilter(SlidingWindowLogRateLimiter rateLimiter,
                                               RateLimitMetrics metrics,
                                               @Value("${ratelimit.ip.limit:100}") int limit,
                                               @Value("${ratelimit.ip.windowSeconds:60}") int windowSeconds,
                                               @Value("${ratelimit.trusted-proxies:}") String trustedProxies) {
        return new IpRateLimitFilter(rateLimiter, metrics, limit, windowSeconds, trustedProxies);
    }
}

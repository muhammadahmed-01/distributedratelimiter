package com.example.DistributedRateLimiter.config;

import com.example.DistributedRateLimiter.IpRateLimitFilter;
import com.example.DistributedRateLimiter.SlidingWindowLogRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<IpRateLimitFilter> ipRateLimitFilterRegistration(IpRateLimitFilter filter) {
        FilterRegistrationBean<IpRateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE); // MUST be here
        reg.addUrlPatterns("/*");
        reg.setName("ipRateLimitFilter");
        return reg;
    }

    @Bean
    public IpRateLimitFilter ipRateLimitFilter(StringRedisTemplate redisTemplate, @Value("${ratelimit.ip.limit:100}") int limit,
                                               @Value("${ratelimit.ip.windowSeconds:60}") int windowSeconds) {
        return new IpRateLimitFilter(redisTemplate, limit, windowSeconds);
    }
}

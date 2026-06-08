package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.rateLimit.RateLimitResponse;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountRateLimitFilterTest {

    private SlidingWindowLogRateLimiter rateLimiter;
    private RateLimitMetrics metrics;
    private AccountRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(SlidingWindowLogRateLimiter.class);
        metrics = mock(RateLimitMetrics.class);
        filter = new AccountRateLimitFilter(rateLimiter, metrics, 3, 60);
    }

    @Test
    void whenAnonymous_thenSkipsAccountLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimiter);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void whenUnderLimit_thenAllowsAndSetsHeaders() throws Exception {
        when(rateLimiter.checkRateLimit(eq("rate_limit:account:acc-1"), eq(3L), eq(60L)))
                .thenReturn(new RateLimitResponse(true, 2L, 1_700_000_000L));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("accountId", "acc-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("2");
        verify(metrics).recordAllowed("account");
    }

    @Test
    void whenOverLimit_thenReturns429() throws Exception {
        when(rateLimiter.checkRateLimit(anyString(), anyLong(), anyLong()))
                .thenReturn(new RateLimitResponse(false, 0L, 0L));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("accountId", "acc-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("rate_limited");
        verify(metrics).recordBlocked("account");
    }

    @Test
    void whenRedisUnavailable_thenReturns503() throws Exception {
        when(rateLimiter.checkRateLimit(anyString(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.redisUnavailable(false));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("accountId", "acc-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
    }
}

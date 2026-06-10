package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.support.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthFilterTest {

    private JwtAuthFilter filter;
    private RateLimitMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = mock(RateLimitMetrics.class);
        filter = new JwtAuthFilter(metrics, JwtTestSupport.TEST_SIGNING_KEY);
    }

    @Test
    void whenNoAuthorizationHeader_thenPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute("accountId")).isNull();
    }

    @Test
    void whenValidToken_thenSetsAccountAttributes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JwtTestSupport.tokenFor("acc-42", "u-42"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("accountId")).isEqualTo("acc-42");
        assertThat(request.getAttribute("userId")).isEqualTo("u-42");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void whenTokenMissingAccountId_thenReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JwtTestSupport.tokenWithoutAccountId());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_token");
        assertThat(request.getAttribute("accountId")).isNull();
        verify(metrics).recordInvalid("jwt");
    }

    @Test
    void whenInvalidToken_thenReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-valid-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_token");
        verify(metrics).recordInvalid("jwt");
    }
}

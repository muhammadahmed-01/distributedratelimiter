package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.rateLimit.RateLimitResponse;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import com.example.DistributedRateLimiter.support.JwtTestSupport;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "jwt.signingKey=" + JwtTestSupport.TEST_SIGNING_KEY,
        "ratelimit.account.limit=10",
        "ratelimit.account.windowSeconds=60"
})
class FilterChainIntegrationTest {

    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlidingWindowLogRateLimiter rateLimiter;

    @Test
    void authenticatedRequest_checksAccountLimitExactlyOnce() throws Exception {
        when(rateLimiter.checkRateLimit(contains("rate_limit:ip:"), anyLong(), anyLong()))
                .thenReturn(new RateLimitResponse(true, 99L, 0L));
        when(rateLimiter.checkRateLimit(eq("rate_limit:account:acc-001"), anyLong(), anyLong()))
                .thenReturn(new RateLimitResponse(true, 9L, 0L));

        String token = JwtTestSupport.tokenFor("acc-001", "u-123");

        mockMvc.perform(get("/api/hello").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(rateLimiter, times(1)).checkRateLimit(eq("rate_limit:account:acc-001"), anyLong(), anyLong());
    }
}

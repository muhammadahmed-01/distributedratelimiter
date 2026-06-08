package com.example.DistributedRateLimiter.filter;

import com.example.DistributedRateLimiter.metrics.RateLimitMetrics;
import com.example.DistributedRateLimiter.rateLimit.RateLimitResponse;
import com.example.DistributedRateLimiter.rateLimit.SlidingWindowLogRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ContextConfiguration(classes = {IpRateLimitFilterTest.TestConfig.class, IpRateLimitFilterTest.DummyController.class})
@TestPropertySource(properties = {
        "ratelimit.ip.limit=5",
        "ratelimit.ip.windowSeconds=10",
        "ratelimit.trusted-proxies=192.168.1.100"
})
class IpRateLimitFilterTest {

    @Configuration
    static class TestConfig {
        @Bean
        IpRateLimitFilter ipRateLimitFilter(SlidingWindowLogRateLimiter rateLimiter, RateLimitMetrics metrics) {
            return new IpRateLimitFilter(rateLimiter, metrics, 5, 10, "192.168.1.100");
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private IpRateLimitFilter ipRateLimitFilter;

    private MockMvc mockMvc;

    @MockitoBean
    private SlidingWindowLogRateLimiter rateLimiter;

    @MockitoBean
    private RateLimitMetrics metrics;

    @RestController
    static class DummyController {
        @GetMapping("/api/test")
        public String dummyEndpoint() {
            return "OK";
        }
    }

    private static final int TEST_LIMIT = 5;
    private static final int TEST_WINDOW_SECONDS = 10;
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_KEY = "rate_limit:ip:" + TEST_IP;
    private static final String DUMMY_API_PATH = "/api/test";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilter(ipRateLimitFilter)
                .build();
    }

    @Test
    void whenLimitNotExceeded_thenAllowRequestAndSetHeaders() throws Exception {
        long currentRemaining = 3L;
        when(rateLimiter.checkRateLimit(eq(TEST_KEY), eq((long) TEST_LIMIT), eq((long) TEST_WINDOW_SECONDS)))
                .thenReturn(new RateLimitResponse(true, currentRemaining, 0L));

        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", String.valueOf(TEST_LIMIT)))
                .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(currentRemaining)));

        verify(metrics, times(1)).recordAllowed("ip");
    }

    @Test
    void whenLimitExceeded_thenReturn429AndSetHeaders() throws Exception {
        when(rateLimiter.checkRateLimit(eq(TEST_KEY), eq((long) TEST_LIMIT), eq((long) TEST_WINDOW_SECONDS)))
                .thenReturn(new RateLimitResponse(false, 0L, 0L));

        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", String.valueOf(TEST_LIMIT)))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("Retry-After", String.valueOf(TEST_WINDOW_SECONDS)));

        verify(metrics, times(1)).recordBlocked("ip");
    }

    @Test
    void whenUsingXForwardedForFromTrustedProxy_thenParsesCorrectIp() throws Exception {
        String proxiedIp = "10.0.0.1";
        String proxiedKey = "rate_limit:ip:" + proxiedIp;

        when(rateLimiter.checkRateLimit(eq(proxiedKey), anyLong(), anyLong()))
                .thenReturn(new RateLimitResponse(true, 4L, 0L));

        mockMvc.perform(get(DUMMY_API_PATH)
                        .with(request -> {
                            request.setRemoteAddr("192.168.1.100");
                            return request;
                        })
                        .header("X-Forwarded-For", "10.0.0.1, 172.21.0.5"))
                .andExpect(status().isOk());

        verify(rateLimiter, times(1)).checkRateLimit(eq(proxiedKey), anyLong(), anyLong());
    }

    @Test
    void whenUsingXForwardedForFromUntrustedClient_thenIgnoresHeader() throws Exception {
        String untrustedClientIp = "10.0.0.99";
        String untrustedKey = "rate_limit:ip:" + untrustedClientIp;

        when(rateLimiter.checkRateLimit(eq(untrustedKey), anyLong(), anyLong()))
                .thenReturn(new RateLimitResponse(true, 4L, 0L));

        mockMvc.perform(get(DUMMY_API_PATH)
                        .with(request -> {
                            request.setRemoteAddr(untrustedClientIp);
                            return request;
                        })
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk());

        verify(rateLimiter, times(1)).checkRateLimit(eq(untrustedKey), anyLong(), anyLong());
    }

    @Test
    void whenRedisUnavailableAndFailClosed_thenReturn503() throws Exception {
        when(rateLimiter.checkRateLimit(any(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.redisUnavailable(false));

        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void whenManyConcurrentRequests_thenObeysLimitAtomically() throws Exception {
        int totalRequests = 50;
        int concurrentThreads = 25;
        String concurrentIp = "10.10.10.10";
        String concurrentKey = "rate_limit:ip:" + concurrentIp;

        final AtomicInteger currentCalls = new AtomicInteger(0);

        when(rateLimiter.checkRateLimit(eq(concurrentKey), anyLong(), anyLong())).thenAnswer((Answer<RateLimitResponse>) invocation -> {
            int count = currentCalls.incrementAndGet();
            if (count <= TEST_LIMIT) {
                return new RateLimitResponse(true, TEST_LIMIT - count, 0L);
            } else {
                return new RateLimitResponse(false, 0L, 0L);
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();

                    MockHttpServletRequest request = new MockHttpServletRequest("GET", DUMMY_API_PATH);
                    request.setRemoteAddr(concurrentIp);
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    MockFilterChain filterChain = new MockFilterChain();

                    ipRateLimitFilter.doFilterInternal(request, response, filterChain);

                    if (response.getStatus() == HttpStatus.OK.value()) {
                        successCount.incrementAndGet();
                    } else if (response.getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finishedInTime = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finishedInTime).isTrue();
        assertThat(successCount.get()).isEqualTo(TEST_LIMIT);
        assertThat(failCount.get()).isEqualTo(totalRequests - TEST_LIMIT);
    }
}

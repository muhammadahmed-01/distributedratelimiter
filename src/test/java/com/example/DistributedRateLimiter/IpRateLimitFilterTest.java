package com.example.DistributedRateLimiter;


import com.example.DistributedRateLimiter.filter.IpRateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Functional test for the IpRateLimitFilter.
 *
 * This test uses @WebMvcTest to load only the web layer (including our filter)
 * and mocks the StringRedisTemplate dependency.
 */
@WebMvcTest(excludeAutoConfiguration = SecurityAutoConfiguration.class) // Disables default security
// We explicitly tell Spring to load our Filter and a dummy Controller for testing
@ContextConfiguration(classes = {IpRateLimitFilter.class, IpRateLimitFilterTest.DummyController.class})
@TestPropertySource(properties = { // Injects test values into @Value
        "ratelimit.ip.limit=5",
        "ratelimit.ip.windowSeconds=10"
})
class IpRateLimitFilterTest {

    @Autowired
    private WebApplicationContext context;

    // --- 1. Autowire the filter bean itself ---
    @Autowired
    private IpRateLimitFilter ipRateLimitFilter;

    private MockMvc mockMvc;

    // Mock the Redis dependency
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    // We also need to mock the ValueOperations that StringRedisTemplate returns
    @MockBean
    private ValueOperations<String, String> valueOperations;

    // Dummy controller to be the target of our filtered requests
    @RestController
    static class DummyController {
        @GetMapping("/api/test")
        public String dummyEndpoint() {
            return "OK";
        }
    }

    // Configurable test parameters (now match the injected properties)
    private static final int TEST_LIMIT = 5;
    private static final int TEST_WINDOW_SECONDS = 10;
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_KEY = "ip:" + TEST_IP;
    private static final String DUMMY_API_PATH = "/api/test";

    @BeforeEach
    void setUp() {
        // Mock the behavior of stringRedisTemplate.opsForValue()
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // We must re-apply the filter to the MockMvc setup before each test
        // This ensures the @Value properties in the filter are correctly injected
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                // --- 2. Manually add the filter to the MockMvc chain ---
                .addFilter(ipRateLimitFilter)
                .build();
    }

    @Test
    void whenLimitNotExceeded_thenAllowRequestAndSetHeaders() throws Exception {
        // GIVEN: The count for our IP is 2 (which is < 5)
        long currentCount = 2L;
        when(valueOperations.increment(TEST_KEY)).thenReturn(currentCount);

        // WHEN: We make a request
        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                // THEN: The request is allowed (200 OK)
                .andExpect(status().isOk())
                // AND: The correct headers are set
                .andExpect(header().string("X-RateLimit-Limit", String.valueOf(TEST_LIMIT)))
                // Use variables for robustness
                .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(TEST_LIMIT - currentCount)));
    }

    @Test
    void whenLimitExceeded_thenReturn429AndSetHeaders() throws Exception {
        // GIVEN: The count for our IP is 6 (which is > 5)
        long currentCount = TEST_LIMIT + 1; // 6
        when(valueOperations.increment(TEST_KEY)).thenReturn(currentCount);

        // WHEN: We make a request
        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                // THEN: The request is rejected (429)
                .andExpect(status().isTooManyRequests())
                // AND: The body contains the error
                .andExpect(content().json("{\"error\":\"Too many requests\"}"))
                // AND: The headers are set correctly for a denied request
                .andExpect(header().string("X-RateLimit-Limit", String.valueOf(TEST_LIMIT)))
                .andExpect(header().string("X-RateLimit-Remaining", "0")) // Remaining is 0
                .andExpect(header().string("Retry-After", String.valueOf(TEST_WINDOW_SECONDS)));
    }

    @Test
    void whenFirstRequest_thenSetExpire() throws Exception {
        // GIVEN: This is the first request, so increment returns 1
        long currentCount = 1L;
        when(valueOperations.increment(TEST_KEY)).thenReturn(currentCount);

        // WHEN: We make the request
        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                // THEN: The request is allowed
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(TEST_LIMIT - currentCount)));

        // AND: The EXPIRE command was called exactly once with the correct window
        verify(stringRedisTemplate, times(1)).expire(TEST_KEY, TEST_WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void whenSecondRequest_thenDoNotSetExpire() throws Exception {
        // GWEEN: This is the second request, so increment returns 2
        when(valueOperations.increment(TEST_KEY)).thenReturn(2L);
        when(stringRedisTemplate.getExpire(TEST_KEY, TimeUnit.SECONDS))
                .thenReturn(10L); // it's not the first request so TTL is a positive value

        // WHEN: We make the request
        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                // THEN: The request is allowed
                .andExpect(status().isOk());

        // AND: The EXPIRE command was *never* called (it was only called on the first request)
        verify(stringRedisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void whenUsingXForwardedFor_thenParsesCorrectIp() throws Exception {
        String proxiedIp = "10.0.0.1";
        String proxiedKey = "ip:" + proxiedIp;

        // GIVEN: The increment call will use the *proxied* key
        when(valueOperations.increment(proxiedKey)).thenReturn(1L);

        // WHEN: We make a request with the X-Forwarded-For header
        mockMvc.perform(get(DUMMY_API_PATH)
                        .with(request -> {
                            request.setRemoteAddr("192.168.1.100"); // This is the load balancer IP
                            return request;
                        })
                        .header("X-Forwarded-For", "10.0.0.1, 172.21.0.5")) // This is the real client IP
                // THEN: The request is allowed
                .andExpect(status().isOk());

        // AND: The increment was called on the *correct* proxied IP key
        verify(valueOperations, times(1)).increment(proxiedKey);
        // AND: The EXPIRE was set on the *correct* proxied IP key
        verify(stringRedisTemplate, times(1)).expire(proxiedKey, TEST_WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void whenNoXForwardedFor_thenUsesRemoteAddr() throws Exception {
        // GWEEN: The increment call will use the *remote address* key
        when(valueOperations.increment(TEST_KEY)).thenReturn(1L);

        // WHEN: We make a request with no X-Forwarded-For header
        mockMvc.perform(get(DUMMY_API_PATH).with(request -> {
                    request.setRemoteAddr(TEST_IP);
                    return request;
                }))
                // THEN: The request is allowed
                .andExpect(status().isOk());

        // AND: The increment was called on the correct remote address key
        verify(valueOperations, times(1)).increment(TEST_KEY);
    }

    // --- THIS IS THE NEW "MULTI-MACHINE" TEST ---

    @Test
    void whenManyConcurrentRequests_thenObeysLimitAtomically() throws Exception {
        // GIVEN: A high number of concurrent requests all at once
        int totalRequests = 50;
        int concurrentThreads = 25;
        // NOTE: We use a *different IP* to avoid interfering with other tests
        String concurrentIp = "10.10.10.10";
        String concurrentKey = "ip:" + concurrentIp;

        // We must simulate the atomic nature of Redis increment
        // We create an AtomicLong to act as our "Redis" counter
        final AtomicLong redisCounter = new AtomicLong(0);

        // When valueOperations.increment(key) is called, return the *new* value
        when(valueOperations.increment(concurrentKey)).thenAnswer((Answer<Long>) invocation ->
                redisCounter.incrementAndGet()
        );
        when(stringRedisTemplate.getExpire(concurrentKey, TimeUnit.SECONDS))
                .thenReturn(-1L) // first call, no TTL
                .thenReturn(10L); // subsequent calls, TTL is set

        // We use latches to make all threads start at the same moment
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalRequests);

        // We use atomic counters to track success/failure across threads
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // WHEN: We submit all 50 requests to the thread pool
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    // 1. All threads wait here until the startGate is opened
                    startGate.await();

                    // 2. Create fresh mock objects for this thread
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", DUMMY_API_PATH);
                    request.setRemoteAddr(concurrentIp);
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    MockFilterChain filterChain = new MockFilterChain();

                    // 3. Execute the filter logic
                    ipRateLimitFilter.doFilterInternal(request, response, filterChain);

                    // 4. Record the result
                    if (response.getStatus() == HttpStatus.OK.value()) {
                        successCount.incrementAndGet();
                    } else if (response.getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                        failCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 5. Signal that this thread has finished
                    endGate.countDown();
                }
            });
        }

        // 3... 2... 1... GO! All 50 requests hit the filter simultaneously.
        startGate.countDown();

        // Wait for all 50 requests to complete
        boolean finishedInTime = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // THEN: We verify the results
        assertThat(finishedInTime).isTrue(); // Ensure the test didn't time out

        // Exactly 5 requests (TEST_LIMIT) should have been successful
        assertThat(successCount.get()).isEqualTo(TEST_LIMIT);

        // Exactly 45 requests (totalRequests - TEST_LIMIT) should have failed
        assertThat(failCount.get()).isEqualTo(totalRequests - TEST_LIMIT);

        // Verify EXPIRE was called exactly once (for the first request)
        verify(stringRedisTemplate, times(1)).expire(concurrentKey, TEST_WINDOW_SECONDS, TimeUnit.SECONDS);
    }
}
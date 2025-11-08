//package com.example.DistributedRateLimiter;
//
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.*;
//
//@SpringBootApplication
//@EnableRedisRepositories // Ensure Redis configuration is enabled
//public class RateLimitFunctionalTester implements CommandLineRunner {
//
//    private final SlidingWindowLogRateLimiter rateLimitService;
//    private final RedisTemplate<String, String> redisTemplate;
//
//    // Inject the service and template
//    public RateLimitFunctionalTester(SlidingWindowLogRateLimiter rateLimitService, RedisTemplate<String, String> redisTemplate) {
//        this.rateLimitService = rateLimitService;
//        this.redisTemplate = redisTemplate;
//    }
//
//    public static void main(String[] args) {
//        // Run the Spring Boot app, which executes the run() method below
//        SpringApplication.run(RateLimitFunctionalTester.class, args);
//    }
//
//    @Override
//    public void run(String... args) throws Exception {
//        System.out.println("--- Starting Functional Verification (Task A3) ---");
//
//        // Clear Redis key for a clean test environment (Optional but Recommended)
//        redisTemplate.delete("rate_limit:test-user-A");
//        redisTemplate.delete("rate_limit:test-user-B");
//
//        testSimpleLimit();
//        testKeyIsolation();
//        testWindowReset();
//
//        System.out.println("--- Functional Verification Complete ---");
//
//        System.out.println("--- Starting Distributed Simulation (Task A4) ---");
//        testDistributedSimulation();
//        testDistributedIsolation();
//        System.out.println("--- Distributed Simulation Complete ---");
//    }
//
//    private void testSimpleLimit() throws InterruptedException {
//        String key = "test-user-A";
//        long limit = 3;
//        long window = 5; // seconds
//
//        System.out.println("\n[TEST 1: Simple Limit Check (Limit: 3/5s)]");
//
//        // 1. Allowed Requests (1, 2, 3)
//        for (int i = 1; i <= limit; i++) {
//            RateLimitResponse response = rateLimitService.checkRateLimit(key, limit, window);
//            System.out.printf("Request %d: Allowed: %s, Remaining: %d\n", i, response.allowed(), response.remaining());
//            assert response.allowed() : "Request should be allowed.";
//        }
//
//        // 2. Denied Request (4)
//        RateLimitResponse deniedResponse = rateLimitService.checkRateLimit(key, limit, window);
//        System.out.printf("Request 4: Allowed: %s, Remaining: %d (DENIED)\n", deniedResponse.allowed(), deniedResponse.remaining());
//        assert !deniedResponse.allowed() : "Request should be denied.";
//    }
//
//    private void testKeyIsolation() {
//        String keyA = "test-user-A-isolation";
//        String keyB = "test-user-B-isolation";
//        long limit = 2;
//        long window = 10;
//
//        System.out.println("\n[TEST 2: Key Isolation Check (Limit: 2/10s for each)]");
//
//        // User A hits limit
//        rateLimitService.checkRateLimit(keyA, limit, window);
//        rateLimitService.checkRateLimit(keyA, limit, window);
//        RateLimitResponse responseA = rateLimitService.checkRateLimit(keyA, limit, window);
//
//        // User B is still fine
//        RateLimitResponse responseB = rateLimitService.checkRateLimit(keyB, limit, window);
//
//        System.out.printf("User A (Over Limit): Allowed: %s, Remaining: %d\n", responseA.allowed(), responseA.remaining());
//        System.out.printf("User B (Under Limit): Allowed: %s, Remaining: %d\n", responseB.allowed(), responseB.remaining());
//
//        assert !responseA.allowed() : "User A should be denied.";
//        assert responseB.allowed() : "User B should be allowed.";
//    }
//
//    private void testWindowReset() throws InterruptedException {
//        String key = "test-user-C-reset";
//        long limit = 1;
//        long window = 2; // short window for testing
//
//        System.out.println("\n[TEST 3: Sliding Window Reset Check]");
//
//        // 1. Initial hit and denial
//        rateLimitService.checkRateLimit(key, limit, window);
//        RateLimitResponse denied = rateLimitService.checkRateLimit(key, limit, window);
//        System.out.printf("Hit limit: Allowed: %s\n", denied.allowed());
//
//        // 2. Wait for the window to expire (2 seconds + 1 second buffer)
//        System.out.printf("Waiting %d seconds for window reset...\n", window + 1);
//        Thread.sleep((window + 1) * 1000L);
//
//        // 3. New request should be allowed as the old timestamp is cleaned up by the script
//        RateLimitResponse allowed = rateLimitService.checkRateLimit(key, limit, window);
//        System.out.printf("After reset: Allowed: %s\n", allowed.allowed());
//
//        assert allowed.allowed() : "Request should be allowed after the window reset.";
//    }
//
//    private void testDistributedSimulation() throws InterruptedException, ExecutionException {
//        String key = "test-user-D-distributed";
//        long limit = 10;
//        long window = 5; // seconds
//
//        System.out.println("\n[TEST 4: Distributed Simulation (Multi-Machine Check)]");
//
//        // Clean start
//        redisTemplate.delete("rate_limit:" + key);
//
//        int concurrentClients = 5; // pretend we have 5 machines
//        int requestsPerClient = 5; // each tries 5 requests
//        ExecutorService executor = Executors.newFixedThreadPool(concurrentClients);
//        List<Future<RateLimitResponse>> futures = new ArrayList<>();
//
//        CountDownLatch latch = new CountDownLatch(1); // so all start simultaneously
//
//        for (int i = 0; i < concurrentClients; i++) {
//            int clientId = i + 1;
//            futures.add(executor.submit(() -> {
//                latch.await(); // wait for the "go" signal
//                RateLimitResponse lastResponse = null;
//                for (int j = 1; j <= requestsPerClient; j++) {
//                    lastResponse = rateLimitService.checkRateLimit(key, limit, window);
//                    System.out.printf("Client %d -> Request %d: Allowed=%s, Remaining=%d\n",
//                            clientId, j, lastResponse.allowed(), lastResponse.remaining());
//                    Thread.sleep(50); // small delay between hits
//                }
//                return lastResponse;
//            }));
//        }
//
//        // Fire the starting gun
//        latch.countDown();
//
//        // Wait for all threads to complete
//        executor.shutdown();
//        executor.awaitTermination(10, TimeUnit.SECONDS);
//
//        // Gather all responses
//        List<RateLimitResponse> allResponses = new ArrayList<>();
//        for (Future<RateLimitResponse> f : futures) {
//            allResponses.add(f.get());
//        }
//
//        // Count how many requests were allowed in total
//        long totalAllowed = allResponses.stream()
//                .filter(RateLimitResponse::allowed)
//                .count();
//
//        System.out.printf("\nTotal allowed requests across all clients: %d (limit=%d)\n", totalAllowed, limit);
//        assert totalAllowed <= limit : "Total allowed requests exceeded limit!";
//    }
//
//    private void testDistributedIsolation() throws InterruptedException {
//        System.out.println("\n[TEST 5: Multi-Machine Isolation Test]");
//        String[] users = {"userX", "userY"};
//        long limit = 3;
//        long window = 5;
//
//        ExecutorService executor = Executors.newFixedThreadPool(users.length);
//        for (String user : users) {
//            executor.submit(() -> {
//                for (int i = 1; i <= limit + 1; i++) {
//                    RateLimitResponse r = rateLimitService.checkRateLimit(user, limit, window);
//                    System.out.printf("User %s -> Request %d: Allowed=%s, Remaining=%d\n",
//                            user, i, r.allowed(), r.remaining());
//                }
//            });
//        }
//        executor.shutdown();
//        executor.awaitTermination(10, TimeUnit.SECONDS);
//    }
//
//}
//

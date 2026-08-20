package com.ecommerce.integration;

import com.ecommerce.ratelimit.ClientIpResolver;
import com.ecommerce.ratelimit.RateLimitBucketRegistry;
import com.ecommerce.ratelimit.RateLimitFilter;
import com.ecommerce.ratelimit.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for RateLimitFilter.
 *
 * Uses standalone MockMvc — no Spring context, no DB required.
 * Filter beans are instantiated directly with test-friendly low limits
 * (e.g. capacity=2) so tests run fast without real timing waits.
 *
 * Each test gets a fresh RateLimitBucketRegistry (fresh Caffeine cache)
 * so tests are fully isolated from each other.
 */
class RateLimitFilterTest {

    private MockMvc mockMvc;
    private RateLimitProperties properties;

    // -------------------------------------------------------------------------
    // Dummy controller — returns 200 for all tested paths
    // -------------------------------------------------------------------------

    @RestController
    static class TestController {
        @PostMapping("/api/v1/auth/login")    public String login()    { return "ok"; }
        @PostMapping("/api/v1/auth/register") public String register() { return "ok"; }
        @PostMapping("/api/v1/auth/refresh")  public String refresh()  { return "ok"; }
        @PostMapping("/api/v1/auth/logout")   public String logout()   { return "ok"; }
        @GetMapping("/api/v1/products")       public String products() { return "ok"; }
        @PostMapping("/api/v1/payments/momo/ipn") public String momoIpn() { return "ok"; }
        @PostMapping("/api/v1/checkout")      public String checkout() { return "ok"; }
        @PostMapping("/api/v1/carts/add")     public String cartAdd()  { return "ok"; }
    }

    // -------------------------------------------------------------------------
    // Helper — build MockMvc with fresh filter for each test
    // -------------------------------------------------------------------------

    /**
     * Creates a MockMvc with the RateLimitFilter configured using the
     * given properties. Each call returns a completely fresh bucket registry
     * so tests don't affect each other.
     */
    private MockMvc buildMockMvc(RateLimitProperties props) {
        RateLimitBucketRegistry registry = new RateLimitBucketRegistry();
        ClientIpResolver ipResolver      = new ClientIpResolver(props);
        ObjectMapper objectMapper        = new ObjectMapper();
        RateLimitFilter filter           = new RateLimitFilter(props, registry, ipResolver, objectMapper);

        return MockMvcBuilders
                .standaloneSetup(new TestController())
                .addFilters(filter)
                .build();
    }

    /** Returns properties with very low limits (capacity=2) for fast testing. */
    private RateLimitProperties tightProperties() {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(true);
        p.setTrustProxy(false);

        // Set auth.login capacity = 2 so 3rd request triggers 429
        p.getAuth().setLogin(new RateLimitProperties.GroupLimit(2, 2, 60));
        p.getAuth().setRegister(new RateLimitProperties.GroupLimit(2, 2, 60));
        p.getAuth().setRefresh(new RateLimitProperties.GroupLimit(2, 2, 60));
        p.getAuth().setLogout(new RateLimitProperties.GroupLimit(2, 2, 60));

        // Public — capacity 3 so 4th request triggers 429
        p.setPub(new RateLimitProperties.GroupLimit(3, 3, 60));

        // Sensitive/checkout — capacity 2
        p.setSensitive(new RateLimitProperties.GroupLimit(2, 2, 60));
        p.setCheckout(new RateLimitProperties.GroupLimit(2, 2, 60));
        p.setMockPayment(new RateLimitProperties.GroupLimit(2, 2, 60));
        p.setOrderRead(new RateLimitProperties.GroupLimit(2, 2, 60));

        return p;
    }

    @BeforeEach
    void setUp() {
        properties = tightProperties();
        mockMvc = buildMockMvc(properties);
    }

    // =========================================================================
    // TEST CASES
    // =========================================================================

    // --- Test 1: Login below limit → 200 ------------------------------------

    @Test
    @DisplayName("1. Login below limit → 200 OK")
    void login_belowLimit_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // --- Test 2: Login over limit → 429 -------------------------------------

    @Test
    @DisplayName("2. Login over limit → 429 Too Many Requests")
    void login_overLimit_returns429() throws Exception {
        // capacity=2: first 2 succeed, 3rd should be 429
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    // --- Test 3: Different IP → independent bucket --------------------------

    @Test
    @DisplayName("3. Login from different IP → not affected by other IP's limit")
    void login_differentIp_hasIndependentBucket() throws Exception {
        // Exhaust bucket for IP1 (127.0.0.1 — default MockMvc remote addr)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                .andExpect(status().isTooManyRequests());

        // Build a second MockMvc that simulates a different client IP via X-Forwarded-For
        // with trustProxy=true so the header is respected
        RateLimitProperties trustProps = tightProperties();
        trustProps.setTrustProxy(true);
        MockMvc mockMvcOtherIp = buildMockMvc(trustProps);

        // Different IP — should still have full bucket
        mockMvcOtherIp.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.99")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // --- Test 4: Register over limit → 429 ----------------------------------

    @Test
    @DisplayName("4. Register over limit → 429")
    void register_overLimit_returns429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/register").content("{}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/register").content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    // --- Test 5: Public GET products below limit → 200 ----------------------

    @Test
    @DisplayName("5. Public GET /api/v1/products below limit → 200")
    void publicGetProducts_belowLimit_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    // --- Test 6: Public GET products over limit → 429 -----------------------

    @Test
    @DisplayName("6. Public GET /api/v1/products over limit → 429")
    void publicGetProducts_overLimit_returns429() throws Exception {
        // pub capacity=3: first 3 succeed, 4th is 429
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isTooManyRequests());
    }

    // --- Test 7: OPTIONS request → never rate limited -----------------------

    @Test
    @DisplayName("7. OPTIONS (CORS preflight) → always 200, never rate limited")
    void optionsRequest_neverRateLimited() throws Exception {
        // Even after exhausting login bucket, OPTIONS should pass
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                    .andExpect(status().isOk());
        }

        // OPTIONS should always go through
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/api/v1/auth/login"))
                    .andExpect(status().isOk());
        }
    }

    // --- Test 8: MoMo IPN → bypass, never rate limited ----------------------

    @Test
    @DisplayName("8. MoMo IPN endpoint → bypass, never rate limited")
    void momoIpn_isBypassEndpoint_neverRateLimited() throws Exception {
        // Send many requests — should never 429
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/payments/momo/ipn")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
        }
    }

    // --- Test 9: rate-limit.enabled=false → all pass -----------------------

    @Test
    @DisplayName("9. rate-limit.enabled=false → all requests pass normally")
    void rateLimitDisabled_allRequestsPass() throws Exception {
        RateLimitProperties disabledProps = tightProperties();
        disabledProps.setEnabled(false);
        MockMvc disabledMockMvc = buildMockMvc(disabledProps);

        // Even more than capacity — should all pass
        for (int i = 0; i < 10; i++) {
            disabledMockMvc.perform(post("/api/v1/auth/login").content("{}"))
                    .andExpect(status().isOk());
        }
    }

    // --- Test 10: 429 response has correct JSON format ----------------------

    @Test
    @DisplayName("10. 429 response body matches GlobalExceptionHandler format")
    void rateLimitExceeded_responseBody_matchesErrorFormat() throws Exception {
        // Exhaust the bucket
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").content("{}")).andReturn();
        }

        mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // --- Test 11: Retry-After header present in 429 -------------------------

    @Test
    @DisplayName("11. 429 response includes Retry-After header")
    void rateLimitExceeded_retryAfterHeader_isPresent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").content("{}")).andReturn();
        }

        mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    // --- Test 12: Concurrent requests — thread-safe counter -----------------

    @Test
    @DisplayName("12. Concurrent requests — bucket counter is thread-safe")
    void concurrentRequests_bucketIsThreadSafe() throws Exception {
        // capacity=2: exactly 2 requests should succeed, rest should get 429
        int totalRequests = 10;
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Runnable> tasks = java.util.stream.IntStream.range(0, totalRequests)
                .mapToObj(i -> (Runnable) () -> {
                    try {
                        latch.await(); // all threads start at the same time
                        int status = mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                        if (status == 200) successCount.incrementAndGet();
                        else if (status == 429) rejectedCount.incrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        tasks.forEach(executor::submit);
        latch.countDown(); // release all threads simultaneously

        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly 2 should have passed (bucket capacity=2), rest rejected
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(rejectedCount.get()).isEqualTo(8);
    }

    // --- Test 13: Checkout endpoint → rate limited separately ---------------

    @Test
    @DisplayName("13. Checkout has its own independent bucket (not shared with login)")
    void checkout_hasIndependentBucket_fromLogin() throws Exception {
        // Exhaust login bucket
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/login").content("{}"))
                .andExpect(status().isTooManyRequests());

        // Checkout bucket is independent — should still pass
        mockMvc.perform(post("/api/v1/checkout").content("{}"))
                .andExpect(status().isOk());
    }

    // --- Test 14: trustProxy=false ignores X-Forwarded-For ------------------

    @Test
    @DisplayName("14. trustProxy=false → X-Forwarded-For header is ignored (spoofing prevented)")
    void trustProxyFalse_ignoresXForwardedFor() throws Exception {
        // Exhaust bucket using default IP (127.0.0.1)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").content("{}")).andReturn();
        }

        // Attacker tries to spoof a different IP via header — should still be 429
        // because trustProxy=false, so X-Forwarded-For is ignored
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "9.9.9.9")
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    // --- Test 15: trustProxy=true respects X-Forwarded-For ------------------

    @Test
    @DisplayName("15. trustProxy=true → X-Forwarded-For is used as key (production proxy mode)")
    void trustProxyTrue_usesXForwardedForAsKey() throws Exception {
        RateLimitProperties trustProps = tightProperties();
        trustProps.setTrustProxy(true);
        MockMvc proxyMockMvc = buildMockMvc(trustProps);

        // Exhaust bucket for IP1
        for (int i = 0; i < 2; i++) {
            proxyMockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "1.2.3.4")
                            .content("{}"))
                    .andExpect(status().isOk());
        }
        proxyMockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .content("{}"))
                .andExpect(status().isTooManyRequests());

        // Different IP → independent bucket, should still pass
        proxyMockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "5.6.7.8")
                        .content("{}"))
                .andExpect(status().isOk());
    }
}

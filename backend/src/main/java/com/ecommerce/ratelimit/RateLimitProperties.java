package com.ecommerce.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for API rate limiting.
 *
 * Bound from the {@code rate-limit} prefix in application.yaml.
 * Each endpoint group has its own capacity and refill settings.
 *
 * Interview notes:
 *   capacity                - max tokens in the bucket (burst allowance)
 *   refill-tokens           - how many tokens are added per refill period
 *   refill-duration-seconds - length of one refill period
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** Master switch -- set to false in tests to disable rate limiting entirely. */
    private boolean enabled = true;

    /**
     * Whether to trust the X-Forwarded-For header when resolving client IP.
     * Set to false in local dev to prevent IP spoofing via header manipulation.
     * Set to true only when the app is behind a trusted reverse proxy (Nginx, Railway, Render).
     */
    private boolean trustProxy = false;

    /** Limits for authentication endpoints (/api/v1/auth/**). */
    private AuthLimits auth = new AuthLimits();

    /** Limits for public read endpoints (GET /api/v1/products/**, GET /api/v1/categories/**). */
    private GroupLimit pub = new GroupLimit(100, 100, 60);

    /** Limits for authenticated mutation endpoints (cart add/update/remove). */
    private GroupLimit sensitive = new GroupLimit(30, 30, 60);

    /** Limits for checkout endpoint (POST /api/v1/checkout). */
    private GroupLimit checkout = new GroupLimit(10, 10, 60);

    /** Limits for authenticated order/payment read endpoints. */
    private GroupLimit orderRead = new GroupLimit(60, 60, 60);

    /** Limits for mock payment endpoint (DEV only, @ConditionalOnProperty). */
    private GroupLimit mockPayment = new GroupLimit(10, 10, 60);

    // -----------------------------------------------------------------------
    // Nested config classes
    // -----------------------------------------------------------------------

    @Getter
    @Setter
    public static class AuthLimits {
        private GroupLimit login    = new GroupLimit(5,  5,  60);
        private GroupLimit register = new GroupLimit(3,  3,  60);
        private GroupLimit refresh  = new GroupLimit(10, 10, 60);
        private GroupLimit logout   = new GroupLimit(20, 20, 60);
    }

    @Getter
    @Setter
    public static class GroupLimit {
        /** Max tokens in the bucket (controls burst). */
        private long capacity;

        /** Tokens added per refill period. */
        private long refillTokens;

        /** Duration of one refill period, in seconds. */
        private long refillDurationSeconds;

        public GroupLimit() {}

        public GroupLimit(long capacity, long refillTokens, long refillDurationSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillDurationSeconds = refillDurationSeconds;
        }
    }
}
package com.ecommerce.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Servlet filter that enforces per-endpoint rate limiting using the Token Bucket algorithm.
 *
 * <p>Filter order (runs BEFORE JwtAuthenticationFilter):
 * <pre>
 *   Request → RateLimitFilter → JwtAuthenticationFilter → SecurityFilters → Controller
 * </pre>
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Runs before JWT filter so auth endpoints are protected before any token parsing.</li>
 *   <li>OPTIONS (CORS preflight) requests are always skipped — rate limiting them would break CORS.</li>
 *   <li>MoMo IPN endpoint is bypassed — it is a server-to-server callback with no fixed IP.</li>
 *   <li>Authenticated endpoints use userId (email) as the key to avoid NAT IP collisions.</li>
 *   <li>Auth endpoints (login/register/refresh) use IP because the user is not yet authenticated.</li>
 *   <li>Response format matches GlobalExceptionHandler.buildError() — same JSON structure.</li>
 * </ul>
 *
 * <p>Multi-instance note: this is in-memory rate limiting. In a multi-instance deployment,
 * each instance maintains its own buckets. For globally consistent limits, Redis-backed
 * Bucket4j (bucket4j-redis) would be needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final RateLimitBucketRegistry registry;
    private final ClientIpResolver ipResolver;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Endpoint path constants
    // -------------------------------------------------------------------------
    private static final String AUTH_LOGIN    = "/api/v1/auth/login";
    private static final String AUTH_REGISTER = "/api/v1/auth/register";
    private static final String AUTH_REFRESH  = "/api/v1/auth/refresh";
    private static final String AUTH_LOGOUT   = "/api/v1/auth/logout";

    private static final String MOMO_IPN     = "/api/v1/payments/momo/ipn";
    private static final String MOMO_RETURN  = "/api/v1/payments/momo/return";

    // -------------------------------------------------------------------------
    // Filter logic
    // -------------------------------------------------------------------------

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1. Master switch — skip entirely if rate limiting is disabled
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Skip OPTIONS (CORS preflight) — never rate limit preflight requests
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // 3. Skip bypass endpoints (MoMo server-to-server callbacks, Swagger)
        if (isBypassEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Determine which limit group applies and resolve key + config
        RateLimitContext ctx = resolveContext(request, path);
        if (ctx == null) {
            // No rule matched — pass through (e.g. Swagger, actuator, unknown paths)
            filterChain.doFilter(request, response);
            return;
        }

        // 5. Get (or create) the bucket for this key
        Bucket bucket = registry.resolveBucket(ctx.key(), ctx.config());

        // 6. Try to consume 1 token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Token available — continue to next filter
            filterChain.doFilter(request, response);
        } else {
            // No token left — reject with 429
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            log.warn("Rate limit exceeded — key={}, group={}, path={}, method={}",
                    ctx.key(), ctx.group(), path, request.getMethod());
            writeRateLimitResponse(response, retryAfterSeconds);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Determines which rate-limit group applies to this request and returns
     * the (key, config, group) context, or null if the request should not be limited.
     */
    private RateLimitContext resolveContext(HttpServletRequest request, String path) {
        String ip = ipResolver.resolve(request);

        // --- Authentication group (IP-based, user is not yet authenticated) ---
        if (path.equals(AUTH_LOGIN)) {
            return new RateLimitContext("auth:login:" + ip, properties.getAuth().getLogin(), "AUTH_LOGIN");
        }
        if (path.equals(AUTH_REGISTER)) {
            return new RateLimitContext("auth:register:" + ip, properties.getAuth().getRegister(), "AUTH_REGISTER");
        }
        if (path.equals(AUTH_REFRESH)) {
            return new RateLimitContext("auth:refresh:" + ip, properties.getAuth().getRefresh(), "AUTH_REFRESH");
        }
        if (path.equals(AUTH_LOGOUT)) {
            return new RateLimitContext("auth:logout:" + ip, properties.getAuth().getLogout(), "AUTH_LOGOUT");
        }

        // --- Public read APIs (IP-based, no auth required) ---
        if (isPublicReadEndpoint(request, path)) {
            return new RateLimitContext("pub:" + ip, properties.getPub(), "PUBLIC");
        }

        // --- Checkout (userId-based — already authenticated, idempotency key also present) ---
        if (path.startsWith("/api/v1/checkout")) {
            String key = resolveUserKey("checkout", ip);
            return new RateLimitContext(key, properties.getCheckout(), "CHECKOUT");
        }

        // --- Order / Payment read (userId-based — polling after checkout) ---
        if (path.startsWith("/api/v1/orders") || path.startsWith("/api/v1/payments/order")) {
            String key = resolveUserKey("order-read", ip);
            return new RateLimitContext(key, properties.getOrderRead(), "ORDER_READ");
        }

        // --- Mock payment (userId-based — DEV tool, ConditionalOnProperty) ---
        if (path.startsWith("/api/v1/mock-payments")) {
            String key = resolveUserKey("mock-payment", ip);
            return new RateLimitContext(key, properties.getMockPayment(), "MOCK_PAYMENT");
        }

        // --- Authenticated cart mutations (userId-based) ---
        if (path.startsWith("/api/v1/carts")) {
            String key = resolveUserKey("sensitive", ip);
            return new RateLimitContext(key, properties.getSensitive(), "SENSITIVE");
        }

        // --- Admin endpoints (products POST/PUT/DELETE, categories write) — not rate limited
        //     Admin accounts are controlled, low-traffic writes — no limit needed
        return null;
    }

    /**
     * Resolves rate-limit key for authenticated endpoints.
     * Uses the authenticated user's email (userId) when available,
     * falls back to IP for unauthenticated requests to the same path.
     *
     * <p>Interview: userId key is fairer than IP in office/shared-network scenarios.
     * An attacker with many accounts is still limited per account.
     */
    private String resolveUserKey(String group, String fallbackIp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return group + ":user:" + userDetails.getUsername();
        }
        return group + ":ip:" + fallbackIp;
    }

    /**
     * Returns true for endpoints that should never be rate-limited.
     *
     * <p>MoMo IPN: server-to-server callback from MoMo's servers.
     * MoMo's IP range is not fixed and blocking it would break payment processing.
     * Security is provided by HMAC signature verification inside MomoIpnService.
     */
    private boolean isBypassEndpoint(String path) {
        return path.equals(MOMO_IPN)
        || path.equals(MOMO_RETURN)
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs");
    }

    /** Returns true for public, unauthenticated read-only endpoints. */
    private boolean isPublicReadEndpoint(HttpServletRequest request, String path) {
        boolean isGet = HttpMethod.GET.matches(request.getMethod());
        return isGet && (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/categories"));
    }

    /**
     * Writes the 429 Too Many Requests response.
     * JSON format matches GlobalExceptionHandler.buildError() to keep a consistent error contract.
     */
    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", "Too many requests. Please try again later.");

        objectMapper.writeValue(response.getWriter(), body);
    }

    // -------------------------------------------------------------------------
    // Inner record — groups key + config + group name together
    // -------------------------------------------------------------------------

    /**
     * Lightweight context object grouping the rate-limit key, config, and group label.
     * Using a Java record (Java 16+) keeps this concise and immutable.
     */
    private record RateLimitContext(
            String key,
            RateLimitProperties.GroupLimit config,
            String group) {}
}

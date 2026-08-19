package com.ecommerce.ratelimit;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the real client IP address from an HTTP request.
 *
 * <p>Interview talking points:
 * <ul>
 *   <li>X-Forwarded-For is set by reverse proxies (Nginx, Railway, Render) and contains the
 *       original client IP. It can be spoofed if the app is not behind a trusted proxy.</li>
 *   <li>In local dev (trust-proxy=false), we ignore X-Forwarded-For and use
 *       {@code request.getRemoteAddr()} directly to avoid IP spoofing via header injection.</li>
 *   <li>In production behind a trusted reverse proxy (trust-proxy=true), the first value in
 *       X-Forwarded-For is the real client IP.</li>
 * </ul>
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final RateLimitProperties properties;

    /**
     * Returns the client IP to use as the rate-limit key.
     *
     * @param request the incoming HTTP request
     * @return client IP string, never null
     */

    public String resolve(HttpServletRequest request){
        if(properties.isTrustProxy()) {
            String fowarded = request.getHeader("X-Forwarded-For");
            if(fowarded != null && !fowarded.isBlank()){
                // X-Forwarded-For may contain multiple IPs: "clientIp, proxy1, proxy2"
                // The first one is the original client IP added by the first proxy.
                String clientIp = fowarded.split(",")[0].trim();
                if(!clientIp.isEmpty()){
                    return clientIp;
                }
            }
        }
        return request.getRemoteAddr();
    }
}

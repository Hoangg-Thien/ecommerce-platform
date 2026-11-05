package com.ecommerce.payment.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.net.URI;

@Configuration
@Getter
public class VnpayConfig {
    public static final String SANDBOX_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    @Value("${payment.vnpay.enabled:false}") private boolean enabled;
    @Value("${payment.vnpay.tmn-code:}") private String tmnCode;
    @Value("${payment.vnpay.hash-secret:}") private String hashSecret;
    @Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") private String payUrl;
    @Value("${payment.vnpay.return-url:}") private String returnUrl;
    @Value("${payment.vnpay.frontend-origin:http://localhost:5173}") private String frontendOrigin;

    @PostConstruct
    public void validate() {
        if (!enabled) return;
        if (!tmnCode.matches("[A-Za-z0-9]{8}") || hashSecret.isBlank())
            throw new IllegalStateException("VNPay Sandbox credentials are required");
        if (!SANDBOX_PAY_URL.equals(payUrl)) throw new IllegalStateException("Only VNPay Sandbox is supported");
        try {
            URI callback = URI.create(returnUrl);
            URI frontend = URI.create(frontendOrigin);
            if (!"https".equals(callback.getScheme()) || callback.getHost() == null || callback.getUserInfo() != null
                    || callback.getQuery() != null || callback.getFragment() != null
                    || !("https".equals(frontend.getScheme()) || ("http".equals(frontend.getScheme())
                        && ("localhost".equals(frontend.getHost()) || "127.0.0.1".equals(frontend.getHost()))))
                    || frontend.getHost() == null || frontend.getUserInfo() != null || frontend.getQuery() != null
                    || frontend.getFragment() != null || !(frontend.getPath().isEmpty() || "/".equals(frontend.getPath())))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("VNPay requires HTTPS Return URL and a fixed frontend origin");
        }
    }
}

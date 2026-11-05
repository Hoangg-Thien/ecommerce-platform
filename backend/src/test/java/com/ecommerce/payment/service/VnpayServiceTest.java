package com.ecommerce.payment.service;

import com.ecommerce.payment.config.VnpayConfig;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.order.entity.Order;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VnpayServiceTest {
    final Instant now = Instant.parse("2026-11-05T18:00:00Z");
    final VnpayConfig config = mock(VnpayConfig.class);
    final VnpayService service = new VnpayService(config, Clock.fixed(now, ZoneOffset.UTC));
    void configure() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getTmnCode()).thenReturn("TESTCODE");
        when(config.getHashSecret()).thenReturn("test-only-key");
        when(config.getPayUrl()).thenReturn(VnpayConfig.SANDBOX_PAY_URL);
        when(config.getReturnUrl()).thenReturn("https://merchant.test/return");
    }
    @Test void canonicalSignatureGoldenVector() {
        when(config.getHashSecret()).thenReturn("test-only-key");
        var fields = Map.of("vnp_OrderInfo","Thanh toan 123", "vnp_Amount","500000",
                "vnp_ReturnUrl","https://merchant.test/return?a=1&b=2");
        assertThat(service.canonical(fields)).isEqualTo("vnp_Amount=500000&vnp_OrderInfo=Thanh+toan+123&vnp_ReturnUrl=https%3A%2F%2Fmerchant.test%2Freturn%3Fa%3D1%26b%3D2");
        assertThat(service.sign(fields)).isEqualTo("cf03e12a89faaad6424e36113490ae3d9ef26b3a636eea0a314b3b8572adf5945c37c35a4b8695bce27442d4309efc49525dc216ed2f54f165944ec091ed98b5");
    }
    @Test void amountTimezoneReferenceAndFixedExpiry() {
        configure();
        Payment p = new Payment(); var order = new Order(); order.setId(7L); p.setOrder(order); p.setAmount(new BigDecimal("5000"));
        service.createUrl(p, "127.0.0.1");
        assertThat(p.getPaymentUrl()).startsWith(VnpayConfig.SANDBOX_PAY_URL + "?")
                .contains("vnp_Amount=500000", "vnp_CreateDate=20261106010000", "vnp_ExpireDate=20261106011500");
        assertThat(p.getProviderTxnRef()).matches("[A-Za-z0-9]{32}");
        assertThat(p.getExpiresAt()).isEqualTo(now.plusSeconds(900));
        p.setPaymentMethod(com.ecommerce.payment.enums.PaymentMethod.VNPAY);
        p.setPaymentStatus(com.ecommerce.payment.enums.PaymentStatus.PENDING);
        order.setStatus(com.ecommerce.order.enums.OrderStatus.AWAITING_PAYMENT);
        assertThat(service.retryUrl(p)).isEqualTo(p.getPaymentUrl());
        p.setExpiresAt(now);
        assertThatThrownBy(() -> service.retryUrl(p)).isInstanceOf(IllegalStateException.class);
    }
    @Test void refusesBelowMinimumAndFractionalOrOverflowingAmounts() {
        for (String amount : List.of("0","4999","5000.01","10000000000")) {
            assertThatThrownBy(() -> VnpayService.minorAmount(new BigDecimal(amount))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(VnpayService.minorAmount(new BigDecimal("5000.00"))).isEqualTo("500000");
    }
}

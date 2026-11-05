package com.ecommerce.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class VnpayConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(VnpayConfig.class);
    @Test void disabledByDefault() { context.run(c -> assertThat(c).hasNotFailed()); }
    @Test void enabledWithoutConfigFailsClosed() {
        context.withPropertyValues("payment.vnpay.enabled=true").run(c -> assertThat(c).hasFailed());
    }
    @Test void ignoresRetiredMockFlagAndRejectsProductionGateway() {
        configured().withPropertyValues("payment.momo.mock-enabled=true").run(c -> assertThat(c).hasNotFailed());
        configured().withPropertyValues("payment.vnpay.pay-url=https://pay.vnpay.vn").run(c -> assertThat(c).hasFailed());
    }
    @Test void validSandboxConfigStarts() { configured().run(c -> assertThat(c).hasNotFailed()); }
    private ApplicationContextRunner configured() {
        return context.withPropertyValues("payment.vnpay.enabled=true", "payment.momo.mock-enabled=false",
                "payment.vnpay.tmn-code=TESTCODE", "payment.vnpay.hash-secret=test-only-key",
                "payment.vnpay.return-url=https://merchant.test/api/v1/payments/vnpay/return");
    }
}

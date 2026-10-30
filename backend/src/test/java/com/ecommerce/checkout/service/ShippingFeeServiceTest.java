package com.ecommerce.checkout.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingFeeServiceTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ShippingFeeService.class);

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.5"})
    void rejectsInvalidFeeAtStartup(String fee) {
        contextRunner.withPropertyValues("app.shipping.flat-fee=" + fee).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void defaultsToThirtyThousand() {
        contextRunner.run(context -> assertThat(context.getBean(ShippingFeeService.class).getFlatFee())
                .isEqualByComparingTo("30000"));
    }

    @Test
    void supportsZeroShipping() {
        contextRunner.withPropertyValues("app.shipping.flat-fee=0").run(context ->
                assertThat(context.getBean(ShippingFeeService.class).getFlatFee()).isEqualTo(BigDecimal.ZERO));
    }
}

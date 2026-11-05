package com.ecommerce.payment.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.strategy.*;

@ExtendWith(MockitoExtension.class)
class PaymentStrategyFactoryTest {

    @Mock private CodPaymentStrategy codPaymentStrategy;
    @Mock private VnpayPaymentStrategy vnpayPaymentStrategy;

    @InjectMocks
    private PaymentStrategyFactory factory;

    @Test
    void getPaymentStrategy_WithCod_ShouldReturnCodStrategy() {
        PaymentStrategy strategy = factory.getPaymentStrategy(PaymentMethod.COD);

        assertThat(strategy).isInstanceOf(CodPaymentStrategy.class);
    }

    @Test
    void getPaymentStrategy_WithMomo_ShouldRejectHistoricalMethod() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.getPaymentStrategy(PaymentMethod.MOMO))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void vnpayIsTheOnlyOnlineStrategy() {
        assertThat(factory.getPaymentStrategy(PaymentMethod.VNPAY)).isSameAs(vnpayPaymentStrategy);
    }
}

package com.ecommerce.unit.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.service.payment.*;

@ExtendWith(MockitoExtension.class)
class PaymentStrategyFactoryTest {

    @Mock private CodPaymentStrategy codPaymentStrategy;
    @Mock private MomoPaymentStrategy momoPaymentStrategy;

    @InjectMocks
    private PaymentStrategyFactory factory;

    @Test
    void getPaymentStrategy_WithCod_ShouldReturnCodStrategy() {
        PaymentStrategy strategy = factory.getPaymentStrategy(PaymentMethod.COD);

        assertThat(strategy).isInstanceOf(CodPaymentStrategy.class);
    }

    @Test
    void getPaymentStrategy_WithMomo_ShouldReturnMomoStrategy() {
        PaymentStrategy strategy = factory.getPaymentStrategy(PaymentMethod.MOMO);

        assertThat(strategy).isInstanceOf(MomoPaymentStrategy.class);
    }
}

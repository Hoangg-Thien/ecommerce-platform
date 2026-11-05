package com.ecommerce.payment.strategy;

import org.springframework.stereotype.Component;

import com.ecommerce.payment.enums.PaymentMethod;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final CodPaymentStrategy codPaymentStrategy;
    private final VnpayPaymentStrategy vnpayPaymentStrategy;

    public PaymentStrategy getPaymentStrategy(PaymentMethod paymentMethod){
        return switch(paymentMethod){
            case COD -> codPaymentStrategy;
            case MOMO -> throw new IllegalArgumentException("MOMO is a historical payment method only");
            case VNPAY -> vnpayPaymentStrategy;
        };
    }
}

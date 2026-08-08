package com.ecommerce.service.payment;

import org.springframework.stereotype.Component;

import com.ecommerce.enums.PaymentMethod;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final CodPaymentStrategy codPaymentStrategy;
    private final MomoPaymentStrategy momoPaymentStrategy;

    public PaymentStrategy getPaymentStrategy(PaymentMethod paymentMethod){
        return switch(paymentMethod){
            case COD -> codPaymentStrategy;
            case MOMO -> momoPaymentStrategy;
        };
    }
}

package com.ecommerce.checkout.service;

import com.ecommerce.promotion.pricing.PricingCalculator.Result;
import lombok.Getter;

@Getter
public class CheckoutPricingException extends IllegalArgumentException {
    private final String code;
    private final int status;
    private final Result breakdown;
    public CheckoutPricingException(String code, int status, String message, Result breakdown) {
        super(message);
        this.code = code;
        this.status = status;
        this.breakdown = breakdown;
    }
}

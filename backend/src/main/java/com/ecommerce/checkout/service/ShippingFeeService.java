package com.ecommerce.checkout.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ShippingFeeService {
    private final BigDecimal flatFee;

    public ShippingFeeService(@Value("${app.shipping.flat-fee:30000}") BigDecimal flatFee) {
        if (flatFee == null || flatFee.signum() < 0 || flatFee.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("app.shipping.flat-fee must be a non-negative integer in VND");
        }
        this.flatFee = flatFee.setScale(0);
    }

    public BigDecimal getFlatFee() {
        return flatFee;
    }
}

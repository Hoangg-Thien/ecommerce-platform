package com.ecommerce.promotion.service;

/** Mapped to HTTP 409 by the checkout boundary in the checkout integration phase. */
public class VoucherSoldOutException extends RuntimeException {
    public VoucherSoldOutException() {
        super("VOUCHER_SOLD_OUT");
    }
}

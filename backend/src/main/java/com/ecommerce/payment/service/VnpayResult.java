package com.ecommerce.payment.service;

public enum VnpayResult {
    SUCCESS, FAILURE, UNKNOWN;

    public static VnpayResult classify(String responseCode, String transactionStatus) {
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) return SUCCESS;
        // No definitive-failure pair has merchant evidence yet; 24/02, 11/02 and 51/02 remain UNKNOWN.
        return UNKNOWN;
    }
}

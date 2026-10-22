package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.request.MomoIpnRequest;
import com.ecommerce.payment.entity.Payment;

public interface MomoService {
    
    // Gửi request tạo payment lên MoMo, nhận về URL để redirect user
    // Đồng thời set momoOrderId và momoRequestId vào payment object
    String createPaymentUrl(Payment payment);

    // Verify chữ ký HMAC-SHA256 từ IPN callback
    boolean verifySignature(MomoIpnRequest request);

    void refundPayment(Payment payment);
}

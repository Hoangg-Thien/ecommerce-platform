package com.ecommerce.payment.mapper;

import org.springframework.stereotype.Component;

import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.entity.Payment;

@Component
public class PaymentMapper {
    public PaymentResponse toPaymentResponse (Payment payment){
        if(payment == null){
            return null;
        }
        return PaymentResponse.builder()
        .id(payment.getId())
        .orderId(payment.getOrder().getId())
        .paymentMethod(payment.getPaymentMethod())
        .paymentStatus(payment.getPaymentStatus())
        .amount(payment.getAmount())
        .transactionId(payment.getTransactionId())
        .createdAt(payment.getCreatedAt())
        .updatedAt(payment.getUpdatedAt())
        .build();
    }
}

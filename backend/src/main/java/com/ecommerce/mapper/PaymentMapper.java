package com.ecommerce.mapper;

import org.springframework.stereotype.Component;

import com.ecommerce.dto.response.PaymentResponse;
import com.ecommerce.entity.Payment;

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

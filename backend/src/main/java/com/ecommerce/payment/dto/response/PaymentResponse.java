package com.ecommerce.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private com.ecommerce.order.enums.OrderStatus orderStatus;
    private com.ecommerce.payment.enums.RefundStatus refundStatus;
    private String reason;
    private java.time.Instant expiresAt;
    private BigDecimal amount;
    private String transactionId; // null if is COD
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

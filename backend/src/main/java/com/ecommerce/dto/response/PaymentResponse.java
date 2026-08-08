package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;

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
    private BigDecimal amount;
    private String transactionId; // null if is COD
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

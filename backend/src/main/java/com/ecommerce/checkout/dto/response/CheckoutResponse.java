package com.ecommerce.checkout.dto.response;

import com.ecommerce.payment.dto.response.PaymentResponse;

import java.math.BigDecimal;

import com.ecommerce.order.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private Long orderId;
    private OrderStatus orderStatus;
    private BigDecimal totalPrice;
    private PaymentResponse paymentResponse; // information payment just created
    @lombok.ToString.Exclude
    private String paymentUrl; // null for COD
}

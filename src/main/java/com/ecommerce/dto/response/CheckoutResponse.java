package com.ecommerce.dto.response;

import java.math.BigDecimal;

import com.ecommerce.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private Long id;
    private OrderStatus orderStatus;
    private BigDecimal totalPrice;
    private PaymentResponse paymentResponse; // information payment just created
    private String paymentUrl; // null if is COD, just MOMO
}

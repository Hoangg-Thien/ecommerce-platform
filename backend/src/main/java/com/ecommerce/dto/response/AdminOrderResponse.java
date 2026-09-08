package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO dùng cho Admin — bổ sung thêm thông tin khách hàng so với OrderResponse thường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private OrderStatus status;
    private BigDecimal totalPrice;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
}

package com.ecommerce.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.promotion.enums.OrderDiscountSource;

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
    private BigDecimal subtotalPrice;
    private BigDecimal discountAmount;
    private OrderDiscountSource orderDiscountSource;
    private BigDecimal orderDiscountPercent;
    private String voucherCode;
    private BigDecimal shippingFee;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private com.ecommerce.payment.enums.RefundStatus refundStatus;
    private String reason;
    private java.time.Instant expiresAt;
    private java.time.Instant paidAt;
    private String providerTxnRef;
    private String providerTransactionNo;
    private String providerEnvironment;
    private String responseCode;
    private String transactionStatus;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
}

package com.ecommerce.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ecommerce.dto.response.AdminOrderResponse;
import com.ecommerce.dto.response.OrderItemResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.enums.PaymentStatus;

@Component
public class OrderMapper {

    public OrderItemResponse tOrderItemResponse(OrderItem orderItem) {
        if (orderItem == null) return null;

        BigDecimal subTotal = orderItem.getPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity()));

        Long variantId = null;
        if (orderItem.getProduct() != null && orderItem.getProduct().getVariants() != null) {
            variantId = orderItem.getProduct().getVariants().stream()
                .filter(v -> v.getSize().equals(orderItem.getSize()))
                .map(com.ecommerce.entity.ProductVariant::getId)
                .findFirst()
                .orElse(null);
        }

        return OrderItemResponse.builder()
            .id(orderItem.getId())
            .productId(orderItem.getProduct().getId())
            .variantId(variantId)
            .productName(orderItem.getProduct().getName())
            .imageUrl(orderItem.getProduct().getImageUrl())
            .size(orderItem.getSize())
            .quantity(orderItem.getQuantity())
            .price(orderItem.getPrice())
            .subTotal(subTotal)
            .build();
    }

    public OrderResponse tOrderResponse(Order order) {
        if (order == null) return null;

        List<OrderItemResponse> itemsResponses = order.getItems().stream()
            .map(this::tOrderItemResponse)
            .toList();

        return OrderResponse.builder()
            .id(order.getId())
            .userId(order.getUser().getId())
            .status(order.getStatus())
            .totalPrice(order.getTotalPrice())
            .paymentMethod(order.getPaymentMethod())
            .createdAt(order.getCreateAt())
            .items(itemsResponses)
            .build();
    }

    /** Dùng cho Admin — bổ sung userEmail + paymentStatus */
    public AdminOrderResponse toAdminOrderResponse(Order order) {
        if (order == null) return null;

        List<OrderItemResponse> itemsResponses = order.getItems() != null
            ? order.getItems().stream().map(this::tOrderItemResponse).toList()
            : List.of();

        PaymentStatus paymentStatus = null;
        if (order.getPayment() != null) {
            paymentStatus = order.getPayment().getPaymentStatus();
        }

        Long userId = order.getUser() != null ? order.getUser().getId() : null;
        String userEmail = order.getUser() != null ? order.getUser().getEmail() : null;

        return AdminOrderResponse.builder()
            .id(order.getId())
            .userId(userId)
            .userEmail(userEmail)
            .status(order.getStatus())
            .totalPrice(order.getTotalPrice())
            .paymentMethod(order.getPaymentMethod())
            .paymentStatus(paymentStatus)
            .createdAt(order.getCreateAt())
            .items(itemsResponses)
            .build();
    }
}

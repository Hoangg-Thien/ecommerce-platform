package com.ecommerce.mapper;

import com.ecommerce.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ecommerce.dto.response.OrderItemResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;

@Component
public class OrderMapper {

    public OrderItemResponse tOrderItemResponse(OrderItem orderItem){
        if(orderItem == null){
            return null;
        }

        BigDecimal subTotal = orderItem.getPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity()));

        return OrderItemResponse.builder()
        .id(orderItem.getId())
        .productId(orderItem.getProduct().getId())
        .productName(orderItem.getProduct().getName())
        .quantity(orderItem.getQuantity())
        .price(orderItem.getPrice())
        .subTotal(subTotal)
        .build();
    }

    public OrderResponse tOrderResponse(Order order){
        if(order == null){
            return null;
        }

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
}

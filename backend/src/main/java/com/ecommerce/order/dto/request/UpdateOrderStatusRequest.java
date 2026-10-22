package com.ecommerce.order.dto.request;

import com.ecommerce.order.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {
    @NotNull(message = "Status is required")
    private OrderStatus orderStatus;
}
